// Jongrai - shared shell for the redesigned pages. Loaded after /app.js (esc, call, store, laneIcon, meta).

const PAGES = [['/', 'Home', 'nav.home'], ['/news', 'News', 'nav.news'], ['/heroes', 'Heroes', 'nav.heroes'], ['/items', 'Items', 'nav.items'], ['/league', 'Pro League', 'nav.league'], ['/draft', 'Draft', 'nav.draft'], ['/about', 'About', 'nav.about']];
/** The nav entry for this address: /home counts as /, /heroes/<slug> as /heroes and /news/<slug> as /news. */
const navKey = p => p === '/home' ? '/' : p.startsWith('/heroes/') ? '/heroes' : p.startsWith('/news/') ? '/news' : (p === '/elo' || p.startsWith('/league/')) ? '/league' : p;
const icon = f => '/icons/' + encodeURIComponent(f);

/* Small round flags for the language switch, drawn inline (no image files).
   EN: split diagonally, United States top-left and United Kingdom bottom-right. ខ្មែរ: Cambodia. */
const FLAG_EN = `<svg class="lang-flag" viewBox="0 0 20 20" aria-hidden="true"><defs><clipPath id="fl-c"><circle cx="10" cy="10" r="10"/></clipPath>
<clipPath id="fl-us"><path d="M0 0H20L0 20Z"/></clipPath><clipPath id="fl-uk"><path d="M20 0V20H0Z"/></clipPath></defs><g clip-path="url(#fl-c)">
<g clip-path="url(#fl-us)"><rect width="20" height="20" fill="#fff"/>${[0,2,4,6,8,10,12].map(i => `<rect y="${i * 20 / 13}" width="20" height="${20 / 13}" fill="#b22234"/>`).join('')}<rect width="10" height="${7 * 20 / 13}" fill="#3c3b6e"/></g>
<g clip-path="url(#fl-uk)"><rect width="20" height="20" fill="#012169"/><path d="M0 0L20 20M20 0L0 20" stroke="#fff" stroke-width="4"/><path d="M0 0L20 20M20 0L0 20" stroke="#c8102e" stroke-width="1.4"/>
<path d="M10 0V20M0 10H20" stroke="#fff" stroke-width="5"/><path d="M10 0V20M0 10H20" stroke="#c8102e" stroke-width="3"/></g>
<path d="M0 20L20 0" stroke="rgba(0,0,0,.25)" stroke-width=".6"/></g></svg>`;
const FLAG_KM = `<svg class="lang-flag" viewBox="0 0 20 20" aria-hidden="true"><defs><clipPath id="fl-k"><circle cx="10" cy="10" r="10"/></clipPath></defs><g clip-path="url(#fl-k)">
<rect width="20" height="20" fill="#032ea1"/><rect y="5" width="20" height="10" fill="#e00025"/>
<path fill="#fff" d="M4.6 13.6h10.8v-.9h-.7v-1.3h-.6V10h-.5V8.7h-.4l-.2-.9-.2.9h-.4V10h-.4V7.6h-.5l-.3-1.5-.3 1.5H9.9V10h-.4V8.7h-.4l-.2-.9-.2.9h-.4V10h-.5v1.4h-.6v1.3h-.7z"/></g></svg>`;

/** Top bar with the Khmer wordmark, nav, patch ticket and a live dot that reflects /api/meta. */
async function shell(current) {
  await i18nReady();
  document.body.insertAdjacentHTML('afterbegin', `<div class="grain"></div><canvas id="embers" aria-hidden="true"></canvas>
  <header class="bar page"><a class="mark" href="/" aria-label="${esc(t('nav.homeLabel', 'Jongrai home'))}"><span class="kh" lang="km">ចង្រៃ</span><span class="lat">JONGRAI</span></a>
  <div class="nav-panel" id="navPanel"><nav class="nav">${PAGES.map(([href, label, key]) => `<a href="${href}"${href === navKey(current) ? ' aria-current="page"' : ''}>${esc(t(key, label))}</a>`).join('')}</nav></div>
  <span class="sp"></span><span class="live" id="live"><i></i><span>${esc(t('shell.engine', 'ENGINE'))}</span></span><span class="ticket" title="${esc(t('shell.patchTitle', 'Hero stats from the official rank page'))}">${esc(t('shell.patch', 'PATCH'))} <span data-patch>-</span><span class="asof">${esc(t('shell.asOf', 'as of'))} <span data-updated>-</span></span></span>
  <span class="langsw" role="group" aria-label="Language"><button type="button" data-l="en" aria-pressed="${LANG === 'en'}">${FLAG_EN}EN</button><button type="button" data-l="km" lang="km" aria-pressed="${LANG === 'km'}">${FLAG_KM}ខ្មែរ</button></span>
  <button type="button" class="nav-toggle" id="navToggle" aria-expanded="false" aria-controls="navPanel" aria-label="${esc(t('nav.menu', 'Menu'))}"><span></span><span></span><span></span></button></header>`);
  navMenu();
  if (current === '/league' || current === '/elo' || current === '/league/elo') {
    const active = current === '/league' ? 'league' : 'elo';
    document.querySelector('.bar').insertAdjacentHTML('afterend', `
      <div class="section-nav page" aria-label="Pro League sections">
        <nav class="section-nav-inner">
          <a href="/league" ${active === 'league' ? 'aria-current="page"' : ''}>${esc(t('pl.subnav.professional', 'Professional League'))}</a>
          <a href="/league/elo" ${active === 'elo' ? 'aria-current="page"' : ''}>${esc(t('pl.subnav.elo', 'ELO Ranking'))}</a>
        </nav>
      </div>`);
  }
  document.querySelectorAll('.langsw button').forEach(b => b.onclick = () => { if (b.dataset.l !== LANG) setLang(b.dataset.l); });
  embers();
  const m = await meta();
  const live = document.getElementById('live');
  live.classList.add(m.error ? 'off' : 'on');
  live.title = m.error || t('shell.engineLive', 'Engine live: {heroes} heroes, {items} items', {heroes: m.heroes, items: m.items});
  return m;
}

/** Phone menu (750px and narrower): the three-stripe button opens the nav in a dropdown. Esc, a tap outside or a link closes it. */
function navMenu() {
  const bar = document.querySelector('.bar'), btn = document.getElementById('navToggle');
  const set = open => {
    bar.classList.toggle('menu-open', open);
    btn.setAttribute('aria-expanded', String(open));
    btn.setAttribute('aria-label', t(open ? 'nav.menuClose' : 'nav.menu', open ? 'Close menu' : 'Menu'));
  };
  btn.onclick = () => set(!bar.classList.contains('menu-open'));
  addEventListener('keydown', e => { if (e.key === 'Escape' && bar.classList.contains('menu-open')) { set(false); btn.focus(); } });
  addEventListener('click', e => { if (bar.classList.contains('menu-open') && !bar.contains(e.target)) set(false); });
  document.querySelectorAll('#navPanel .nav a').forEach(a => a.addEventListener('click', () => set(false)));
  matchMedia('(min-width:751px)').addEventListener('change', e => { if (e.matches) set(false); });
}

/** A few slow sparks rising from the bottom edge. Off for reduced motion and hidden tabs. */
function embers() {
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const c = document.getElementById('embers'), g = c.getContext('2d');
  let w, h;
  const size = () => { w = c.width = innerWidth; h = c.height = innerHeight; };
  size(); addEventListener('resize', size);
  const spark = () => ({x: Math.random() * w, y: h + Math.random() * h, r: .6 + Math.random() * 1.6,
    v: .15 + Math.random() * .45, sway: Math.random() * 6.28, a: .25 + Math.random() * .5});
  const ps = Array.from({length: 46}, spark);
  (function tick() {
    if (!document.hidden) {
      g.clearRect(0, 0, w, h);
      for (const p of ps) {
        p.y -= p.v; p.sway += .01; p.x += Math.sin(p.sway) * .25;
        if (p.y < -10) Object.assign(p, spark(), {y: h + 10});
        const fade = Math.min(1, p.y / h * 1.6);
        g.beginPath(); g.arc(p.x, p.y, p.r, 0, 6.283);
        g.fillStyle = `rgba(246,${170 + (p.r * 30 | 0)},90,${p.a * fade})`;
        g.shadowColor = 'rgba(246,190,90,.8)'; g.shadowBlur = 8; g.fill();
      }
    }
    requestAnimationFrame(tick);
  })();
}

let toastTimer;
function toast(text, color) {
  let t = document.querySelector('.toast');
  if (!t) { t = document.createElement('div'); t.className = 'toast'; t.setAttribute('role', 'status'); document.body.append(t); }
  t.textContent = text; t.style.borderColor = color || '';
  t.classList.add('show'); clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 3200);
}

/** Counts a number element from its current value to `to` (keeps the decimals of `to`). */
function countTo(el, to, ms = 700) {
  const from = parseFloat(el.textContent) || 0, dp = (String(to).split('.')[1] || '').length, t0 = performance.now();
  const step = now => {
    const k = Math.min(1, (now - t0) / ms), e = 1 - Math.pow(1 - k, 3);
    el.textContent = (from + (to - from) * e).toFixed(dp);
    if (k < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}