// Jongrai - script for /home.html. Runs after /app.js and /kit.js.
let heroes=[],byName={};
const cap=s=>s[0].toUpperCase()+s.slice(1).toLowerCase();
async function drawBoard(){
 const [s,a]=await Promise.all([call('/api/state'),call('/api/analysis')]);
 const el=$('#board');
 if(s.error){el.innerHTML=`<h2>Live board</h2><p style="color:var(--ember)">${esc(s.error)}</p>`;return}
 const bansOn=store.get('jongrai-bans')!=='off',total=bansOn?20:10;  // same switch as the draft board
 const n=s.blue.picks.length+s.red.picks.length+(bansOn?s.blue.bans.length+s.red.bans.length:0);
 const five=(t,list)=>`<div class="five ${t}">${Array.from({length:5},(_,i)=>{const p=list[i],h=p&&byName[p.name];return h?`<span class="f" title="${esc(h.name)}"><img src="${icon(h.icon)}" alt="${esc(h.name)}"></span>`:'<span></span>'}).join('')}</div>`;
 
 el.innerHTML=`<h2>Live board <small><span class="live on"><i></i></span>${n?`${n} of ${total} steps in`:bansOn?'waiting for the first ban':'waiting for the first pick'}</small></h2>
  <div class="vs">${five('blue',s.blue.picks)}<div class="edge"><span class="b">${a.blueEdge??50}</span><i>:</i><span class="r">${a.redEdge??50}</span><small>EDGE</small></div>${five('red',s.red.picks)}</div>
  <div class="meter"><i style="width:${a.blueEdge??50}%"></i></div>
  <p>${n?esc(a.headline||''):(bansOn?'The board is clear. Blue bans first, then the snake order takes over.':'The board is clear. Blue picks first, then the snake order takes over.')} ${n?`<span style="color:var(--ink-3)">· ${s.blue.bans.length+s.red.bans.length} bans placed</span>`:''}</p>
  <a class="go" href="/draft">${n?'Continue this draft →':'Start drafting →'}</a>`;
}
function drawPulse(){
 const tops=[...heroes].sort((x,y)=>y.winRate-x.winRate).slice(0,3);
 $('#tops').innerHTML=tops.map((h,i)=>`<a class="top" href="/heroes/${esc(h.slug)}"><img class="aura" src="${icon(h.icon)}" alt=""><span class="rk num">0${i+1}</span>
  <img class="face" src="${icon(h.icon)}" alt=""><b>${esc(h.name)}</b><small>${esc(h.roles.map(cap).join(' / '))} · tier ${esc(h.tier)}</small>
  <small style="margin-top:10px;color:var(--ink-3)">${esc(h.lanes.map(cap).join(' / '))} · ${esc(cap(h.spike))} spike</small><div class="wrn">${h.winRate.toFixed(2)}%<span>WIN</span></div></a>`).join('');
 const banned=heroes.filter(h=>h.metaBan).sort((x,y)=>y.banRate-x.banRate).slice(0,7),max=banned[0]?banned[0].banRate:1;
 $('#bans').innerHTML='<h3>Most banned</h3>'+banned.map((h,i)=>`<div class="brow"><img src="${icon(h.icon)}" alt=""><div class="bar"><i style="width:${h.banRate/max*100}%;animation-delay:${i*.07}s"></i><span>${esc(h.name)}</span></div><span class="num">${h.banRate.toFixed(1)}%</span></div>`).join('');
}
/** One hero tip at a time, rotating every 7 s; hover pauses it. */
function tips(){
 const pool=heroes.filter(h=>h.tip).map(h=>{const s=tipSections(h.tip);return{...h,tipPart:s[Math.floor(Math.random()*s.length)]}}).sort(()=>Math.random()-.5).slice(0,6);let i=0,paused=false,t0=Date.now();
 const el=$('#tip');el.onmouseenter=()=>paused=true;el.onmouseleave=()=>{paused=false;t0=Date.now()};
 const show=()=>{const h=pool[i];el.classList.add('out');setTimeout(()=>{
  el.innerHTML=`<img class="face swap" src="${icon(h.icon)}" alt=""><div class="swap"><q>${esc(h.tipPart.text)}</q><cite>${esc(h.name)} · Jongrai tip${h.tipPart.label?' · '+esc(h.tipPart.label):''}</cite><div class="dots">${pool.map((_,j)=>`<i class="${j===i?'on':''}"></i>`).join('')}</div></div>`;
  el.classList.remove('out')},el.innerHTML?300:0)};
 show();
 setInterval(()=>{if(paused||document.hidden||Date.now()-t0<7000)return;t0=Date.now();i=(i+1)%pool.length;show()},250);
}
(async()=>{
 const m=await shell(location.pathname);
 const hs=await call('/api/heroes');if(hs.error){toast(hs.error,'var(--ember)');return}
 heroes=hs;heroes.forEach(h=>byName[h.name]=h);
 const vals={hero:m.heroes||heroes.length,item:m.items||0,banned:heroes.filter(h=>h.metaBan).length};
 const strip=document.querySelector('.strip');
 new IntersectionObserver((es,o)=>{if(!es[0].isIntersecting)return;o.disconnect();
  strip.querySelectorAll('[data-to]').forEach(b=>countTo(b,vals[b.dataset.to]??+b.dataset.to,1100))}).observe(strip);
 drawPulse();tips();drawBoard();
 addEventListener('focus',drawBoard);
})();
