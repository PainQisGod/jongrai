/* Project Jongrai - helpers shared by every page. Loaded before each page's own script. */
const $ = s => document.querySelector(s);

/** Escapes text for HTML, including attribute values. Every interpolated piece of data goes through this. */
const esc = s => String(s ?? '').replace(/[&<>"']/g,
  c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

const TIER_COLORS = {S: '#f5c518', A: '#22c55e', B: '#3b82f6', C: '#8b91a3', D: '#ef4444'};

const LANE_ICONS = {
  exp: 'Mobile_Legends_EXP_Lane.png', exp_lane: 'Mobile_Legends_EXP_Lane.png',
  gold: 'Mobile_Legends_Gold_Lane.png', gold_lane: 'Mobile_Legends_Gold_Lane.png',
  mid: 'Mobile_Legends_Mid_Lane.png', mid_lane: 'Mobile_Legends_Mid_Lane.png',
  jungle: 'Mobile_Legends_Jungle.png', roamer: 'Mobile_Legends_Roamer.png', roam: 'Mobile_Legends_Roamer.png'
};
/** File name of a lane's icon in /icons, or '' when there is none. */
const laneIcon = name => name ? LANE_ICONS[name.toLowerCase().replace(/[\s-]+/g, '_')] || '' : '';

/**
 * Fetches JSON and never throws. Failures come back as {error}; a reply that carries its own
 * error (a 400 or 409 body) is returned as it is, so callers can read its other fields.
 * Mutations pass {post:true} and, optionally, {body:{key:value}} (sent as a form body).
 */
async function call(path, {post = false, body} = {}) {
  let r;
  try {
    r = await fetch(path, post ? {method: 'POST', body: body ? new URLSearchParams(body) : undefined} : {});
  } catch (e) {
    return {error: 'Cannot reach the server.'};
  }
  try {
    const j = await r.json();
    return r.ok || (j && j.error) ? j : {error: 'The server answered ' + r.status + '.'};
  } catch (e) {
    return {error: 'The server sent an unreadable reply (' + r.status + ').'};
  }
}

/** localStorage that survives being blocked: reads give null, writes do nothing. */
const store = {
  get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* storage blocked: the setting just won't persist */ } }
};

/* ---------- languages: English (built in) and Khmer (web/lang/km.json) ----------
   English text stays in the HTML and the scripts. Khmer replaces it by key:
   - static text: <span data-i18n="home.cta.draft">Open the draft board</span> (data-i18n-html when the text holds tags)
   - script text: t('home.board.title', 'Live board') or t('key', '{n} of {total} steps in', {n, total})
   A missing Khmer key just shows the English, so pages never break while translation is in progress. */
const LANG = store.get('jongrai-lang') === 'km' ? 'km' : 'en';
let KM = {};
if (LANG === 'km') {
  document.documentElement.classList.add('lang-km', 'i18n-wait');
  setTimeout(() => document.documentElement.classList.remove('i18n-wait'), 2500);  // never leave the page hidden
}
const t = (key, en, vars) => {
  let s = (LANG === 'km' && KM[key]) || en;
  return vars ? s.replace(/\{(\w+)\}/g, (m, k) => vars[k] ?? m) : s;
};
let i18nLoad = null;
/** Loads the Khmer strings (once) and swaps every marked element. Pages await it before drawing script text. */
function i18nReady() {
  if (LANG !== 'km') return Promise.resolve();
  return i18nLoad ??= (async () => {
    try { const r = await fetch('/lang/km.json'); if (r.ok) KM = await r.json(); } catch (e) { /* stay in English */ }
    // An older server build has no /lang route: say so instead of silently staying in English.
    if (!Object.keys(KM).length) setTimeout(() => typeof toast === 'function'
      && toast('Khmer text could not load. Rebuild and restart the server (.\\build.ps1).', 'var(--ember)'), 600);
    document.documentElement.lang = 'km';
    applyI18n(document);
    document.documentElement.classList.remove('i18n-wait');
  })();
}
function applyI18n(root) {
  root.querySelectorAll('[data-i18n]').forEach(el => { const v = KM[el.dataset.i18n]; if (v) el.textContent = v; });
  root.querySelectorAll('[data-i18n-html]').forEach(el => { const v = KM[el.dataset.i18nHtml]; if (v) el.innerHTML = v; });
  root.querySelectorAll('[data-i18n-title]').forEach(el => { const v = KM[el.dataset.i18nTitle]; if (v) el.title = v; });
  root.querySelectorAll('[data-i18n-label]').forEach(el => { const v = KM[el.dataset.i18nLabel]; if (v) el.setAttribute('aria-label', v); });
}
function setLang(l) { store.set('jongrai-lang', l); location.reload(); }

let META = null;
/** "2026-11-06" -> "November 6, 2026", or "6 វិច្ឆិកា 2026" in Khmer (fixed format, independent of the visitor's locale). */
function fmtDate(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
  if (!m) return iso || '';
  if (LANG === 'km') {
    const km = ['មករា','កុម្ភៈ','មីនា','មេសា','ឧសភា','មិថុនា','កក្កដា','សីហា','កញ្ញា','តុលា','វិច្ឆិកា','ធ្នូ'];
    return `${+m[3]} ${km[+m[2] - 1]} ${m[1]}`;
  }
  const months = ['January','February','March','April','May','June','July','August','September','October','November','December'];
  return `${months[+m[2] - 1]} ${+m[3]}, ${m[1]}`;
}
/** Fills every [data-patch], [data-hero-count] and [data-item-count] element from the loaded meta. */
function fillMeta() {
  if (!META) return;
  document.querySelectorAll('[data-patch]').forEach(e => e.textContent = META.patch);
  document.querySelectorAll('[data-updated]').forEach(e => e.textContent = fmtDate(META.updated));
  document.querySelectorAll('[data-hero-count]').forEach(e => e.textContent = META.heroes);
  document.querySelectorAll('[data-item-count]').forEach(e => e.textContent = META.items);
}
/** Loads /api/meta once and fills the page. Call again after rendering new data-* elements. */
async function meta() {
  if (!META) {
    const m = await call('/api/meta');
    if (m.error) return m;
    META = m;
  }
  fillMeta();
  return META;
}

/**
 * Header sign-in: a "Sign in with Google" button, or the signed-in visitor's name with a Sign out button.
 * Does nothing when the server has no sign-in set up, so the site looks the same without it.
 */
async function initAuth() {
  const header = document.querySelector('header');
  if (!header || header.querySelector('.auth')) return;
  const me = await call('/api/me');
  if (me.error || !me.configured) return;
  const box = document.createElement('div');
  box.className = 'auth';
  if (me.loggedIn) {
    const label = String(me.name || me.email || '?');
    const pic = me.picture
      ? `<img class="auth-pic" src="${esc(me.picture)}" alt="" referrerpolicy="no-referrer">`
      : `<span class="auth-pic">${esc(label.trim().charAt(0).toUpperCase())}</span>`;
    box.innerHTML = `${pic}<span class="auth-name" title="${esc(me.email)}">${esc(label)}</span>`
      + `${me.admin ? '<span class="auth-admin">Admin</span>' : ''}<button type="button" class="auth-out">Sign out</button>`;
    box.querySelector('.auth-out').onclick = async () => { await call('/auth/logout', {post: true}); location.reload(); };
  } else {
    box.innerHTML = `<a class="auth-in" href="/auth/login?next=${encodeURIComponent(location.pathname + location.search)}">Sign in with Google</a>`;
  }
  header.appendChild(box);
}
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initAuth); else initAuth();

/**
 * A Jongrai tip as a list of sections. In heroes.csv a tip can hold several sections separated by " | ",
 * each optionally starting with a short label: "Laning Phase: ... | Items: ...".
 */
function tipSections(tip) {
  return String(tip || '').split('|').map(s => s.trim()).filter(Boolean).map(s => {
    const m = /^([^:]{2,24}):\s+(.+)$/s.exec(s);
    return m ? {label: m[1].trim(), text: m[2].trim()} : {label: '', text: s};
  });
}
