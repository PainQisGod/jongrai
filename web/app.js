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

let META = null;
/** Fills every [data-patch], [data-hero-count] and [data-item-count] element from the loaded meta. */
function fillMeta() {
  if (!META) return;
  document.querySelectorAll('[data-patch]').forEach(e => e.textContent = META.patch);
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
