// Jongrai mockups - shared shell. Loaded after the real /app.js (esc, call, store, laneIcon, meta).

const PAGES = [['index.html', 'Proposal'], ['home.html', 'Home'], ['draft.html', 'Draft'], ['heroes.html', 'Heroes']];
const icon = f => '/icons/' + encodeURIComponent(f);

/** Top bar with the Khmer wordmark, nav, patch ticket and a live dot that reflects /api/meta. */
async function shell(current) {
  document.body.insertAdjacentHTML('afterbegin', `<div class="grain"></div><canvas id="embers" aria-hidden="true"></canvas>
  <header class="bar page"><a class="mark" href="home.html" aria-label="Jongrai home"><span class="kh" lang="km">ចង្រៃ</span><span class="lat">JONGRAI</span></a>
  <nav class="nav">${PAGES.map(([href, label]) => `<a href="${href}"${href === current ? ' aria-current="page"' : ''}>${label}</a>`).join('')}</nav>
  <span class="sp"></span><span class="live" id="live"><i></i><span>ENGINE</span></span><span class="ticket">PATCH <span data-patch>-</span></span></header>`);
  embers();
  const m = await meta();
  const live = document.getElementById('live');
  live.classList.add(m.error ? 'off' : 'on');
  live.title = m.error || `Engine live: ${m.heroes} heroes, ${m.items} items`;
  return m;
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
