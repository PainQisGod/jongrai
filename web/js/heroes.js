// Jongrai - script for /hero-statistics.html. Runs after /app.js and /kit.js.
const ROLE_ICON={TANK:'Emblem_Tank.png',FIGHTER:'Emblem_Fighter.png',ASSASSIN:'Emblem_Assassin.png',MAGE:'Emblem_Mage.png',MARKSMAN:'Emblem_Marksman.png',SUPPORT:'Emblem_Support.png'};
const TIER_LABEL={S:'top',A:'strong',B:'solid',C:'average',D:'weak'};
const SPIKES=['EARLY','MID','LATE'];
let heroes=[],byKey={},role='',lastFocus=null;
const cap=s=>s[0].toUpperCase()+s.slice(1).toLowerCase();
const key=n=>n.toLowerCase();

/** Places each tier's heroes along the win-rate axis, nudging them up or down until no two faces overlap. */
function drawRiver(){
 const el=$('#river'),W=el.clientWidth-112,D=32,lo=Math.floor(Math.min(...heroes.map(h=>h.winRate))),hi=Math.ceil(Math.max(...heroes.map(h=>h.winRate)));
 const x=w=>(w-lo)/(hi-lo)*W;
 let html='';
 for(const t of'SABCD'){
  const placed=[];
  for(const h of heroes.filter(h=>h.tier===t).sort((a,b)=>a.winRate-b.winRate)){
   const px=x(h.winRate);let k=0,py=0;
   do{py=(k%2?1:-1)*Math.ceil(k/2)*D*.52;k++}while(placed.some(p=>Math.hypot(p.x-px,p.y-py)<D));
   placed.push({x:px,y:py,h});
  }
  const half=Math.max(...placed.map(p=>Math.abs(p.y)))+D*.6;
  html+=`<div class="band" style="height:${Math.max(60,half*2)}px"><div class="band-l"><span class="tier ${t}">${t}</span><small>${TIER_LABEL[t]} · ${placed.length}</small></div>
   ${placed.map((p,i)=>`<button class="dot in${p.h.metaBan?' banned':''}" style="left:${p.x}px;top:calc(50% + ${p.y}px);animation-delay:${(p.x/W*.5+i*.006).toFixed(3)}s" data-h="${esc(p.h.slug)}"
    aria-label="${esc(p.h.name)}, tier ${t}, ${p.h.winRate.toFixed(2)}% win${', '+p.h.banRate.toFixed(1)+'% ban'}"><img src="${icon(p.h.icon)}" alt=""><span class="lbl">${esc(p.h.name)}<b>${p.h.winRate.toFixed(1)}%</b></span></button>`).join('')}</div>`;
 }
 const ticks=[];for(let w=lo;w<=hi;w++)ticks.push(`<i class="${w===50?'half':''}" style="left:${x(w)}px"></i><span class="${w===50?'half':''}" style="left:${x(w)}px">${w}${w===50?'%':''}</span>`);
 el.innerHTML=html+`<div class="axis" aria-hidden="true">${ticks.join('')}<span class="flip" style="left:${x(50)}px">COIN FLIP</span><span class="cap">WIN RATE →</span></div>`;
 applyFilter();
}
function applyFilter(){
 const q=$('#q').value.trim().toLowerCase();let n=0;
 document.querySelectorAll('.dot').forEach(d=>{const h=byKey[d.dataset.h],ok=(!q||h.name.toLowerCase().includes(q))&&(!role||h.roles.includes(role));
  d.classList.toggle('dim',!ok);d.classList.toggle('hit',ok&&!!q);d.tabIndex=ok?0:-1;n+=ok});
 $('#match').textContent=q||role?`${n} of ${heroes.length} match`:`${heroes.length} heroes`;
}
function drawRoles(){$('#roles').innerHTML=Object.entries(ROLE_ICON).map(([r,f])=>`<button class="chip" data-role="${r}" aria-pressed="${role===r}"><img src="${icon(f)}" alt="">${cap(r)}</button>`).join('')}

/* ---------- build footer: lane, emblem with its three talents, battle spell, all as icons ---------- */
const file=s=>String(s).trim().replace(/ /g,'_')+'.png';
/** "Tank with Vitality, Bargain Hunter, Concussive Blast" -> {main:'Tank', talents:[...]} */
function parseEmblem(e){const m=/^\s*(.+?)\s+with\s+(.+)$/.exec(e||'');return m?{main:m[1],talents:m[2].split(',').map(t=>t.trim()).filter(Boolean)}:{main:e||'',talents:[]}}
const emblemIcon=main=>main.toLowerCase()==='basic common'?'Basic_Common_Emblem.png':'Emblem_'+file(main);
function buildFoot(b){
 const out=[];
 const li=laneIcon(b.lane);
 if(b.lane)out.push(`<span class="bf-chip" title="Lane: ${esc(cap(b.lane))}">${li?`<img src="/icons/${li}" alt="">`:''}<b>${esc(cap(b.lane))}</b></span>`);
 if(b.emblem){const e=parseEmblem(b.emblem);
  out.push(`<span class="bf-chip" title="Emblem: ${esc(b.emblem)}"><img src="${icon(emblemIcon(e.main))}" alt="" onerror="this.remove()"><b>${esc(e.main)}</b>${e.talents.map(t=>`<img class="bf-talent" src="${icon('Talent_'+file(t))}" alt="${esc(t)}" title="${esc(t)}" onerror="this.replaceWith(Object.assign(document.createElement('small'),{textContent:this.alt}))">`).join('')}</span>`)}
 if(b.battleSpell)out.push(`<span class="bf-chip" title="Battle spell: ${esc(b.battleSpell)}"><img src="${icon(file(b.battleSpell))}" alt="" onerror="this.remove()"><b>${esc(b.battleSpell)}</b></span>`);
 return out.join('');
}

/* ---------- profile drawer ---------- */
const faceBtn=h=>`<button data-h="${esc(h.slug)}"><img src="${icon(h.icon)}" alt="">${esc(h.name)}</button>`;
function rating(label,v){const on=Math.round(v/10);return`<div class="rating"><span>${label}</span><span class="segs">${Array.from({length:10},(_,i)=>`<i class="${i<on?'on':''}" style="animation-delay:${i*.03}s"></i>`).join('')}</span><span class="num">${v}</span></div>`}
async function open(slug,push=true){
 const h=byKey[slug];if(!h)return;
 if(push)history.replaceState(null,'','/heroes/'+encodeURIComponent(slug));
 const strong=h.counters.map(k=>heroes.find(x=>key(x.name)===k)).filter(Boolean);
 const weak=heroes.filter(x=>x.counters.includes(key(h.name)));
 const combo=heroes.filter(x=>x!==h&&(h.synergies.includes(key(x.name))||x.synergies.includes(key(h.name))));
 const d=h.winRate-50,tier=heroes.filter(x=>x.tier===h.tier),rank=tier.sort((a,b)=>b.winRate-a.winRate).indexOf(h)+1;
 const rel=(label,col,list,empty)=>`<div class="rel"><span style="color:${col}">${label}</span>${list.length?`<div class="faces">${list.map(faceBtn).join('')}</div>`:`<span class="none">${empty}</span>`}</div>`;
 $('#drawer').innerHTML=`<button class="close" id="close" aria-label="Close profile">✕</button>
  <div class="banner"><img class="aura" src="${icon(h.icon)}" alt=""><img class="face" src="${icon(h.icon)}" alt="">
   <div><h2 id="dname">${esc(h.name)}</h2><div class="meta"><span class="tier ${esc(h.tier)}">${esc(h.tier)}</span>
   ${h.roles.map(r=>`<span class="pill">${ROLE_ICON[r]?`<img src="${icon(ROLE_ICON[r])}" alt="">`:''}${esc(cap(r))}</span>`).join('')}
   ${h.lanes.map(l=>`<span class="pill"><img src="/icons/${laneIcon(l)}" alt="">${esc(cap(l))}</span>`).join('')}</div></div></div>
  <div class="body">
   <div class="sect"><div class="stats">
    <div class="stat"><b class="num">${h.winRate.toFixed(2)}%</b><small>win rate · <span class="${d>=0?'up':'down'}">${d>=0?'+':''}${d.toFixed(1)} vs coin flip</span></small></div>
    <div class="stat"><b class="num">${h.pickRate.toFixed(2)}%</b><small>pick rate</small></div>
    <div class="stat"><b class="num" style="color:${h.metaBan?'var(--red-hi)':'var(--ink)'}">${h.banRate.toFixed(1)}%</b><small>${h.metaBan?'ban rate · meta ban':'ban rate'}</small></div>
    <div class="stat"><b class="num">#${rank}</b><small>of ${tier.length} in tier ${esc(h.tier)}</small></div></div></div>
   <div class="sect"><h3>In-game ratings</h3>${rating('Durability',h.durability)}${rating('Offense',h.offense)}${rating('Control',h.control)}</div>
   <div class="sect"><h3>Power spike</h3><div class="spike">${SPIKES.map(s=>`<div class="${h.spike===s||h.spike==='ALL'?'on':''}">${s}</div>`).join('')}</div></div>
   <div class="sect"><h3>Jongrai tip</h3><p class="tip">${esc(h.tip||'No tip recorded for this hero yet.')}</p><div class="tags">${h.tags.map(t=>`<span class="pill">${esc(t)}</span>`).join('')}</div></div>
   <div class="sect"><h3>Matchups</h3>${rel('Strong vs','var(--jade)',strong,'No hard counters recorded.')}${rel('Weak vs','var(--ember)',weak,'Nobody counters this hero on record.')}${rel('Works with','var(--gold)',combo,'No recorded combos.')}</div>
   <div class="sect"><h3>Builds</h3><div id="builds"><div class="skel"></div></div></div>
  </div>`;
 $('#drawer').classList.add('open');$('#scrim').classList.add('open');
 document.querySelectorAll('.dot.on').forEach(x=>x.classList.remove('on'));
 const dot=document.querySelector(`.dot[data-h="${CSS.escape(slug)}"]`);if(dot)dot.classList.add('on');
 $('#drawer').scrollTop=0;$('#drawer').focus({preventScroll:true});
 const bs=await call('/api/builds?hero='+encodeURIComponent(h.name));
 if(byKey[slug]!==h||!$('#builds'))return;
 $('#builds').innerHTML=bs.error?`<p class="none">${esc(bs.error)}</p>`:!bs.length?'<p class="none">No curated builds for this hero yet.</p>'
  :bs.map(b=>`<div class="build"><div class="build-h"><b>${esc(b.name)}</b><span class="num">${b.totalCost.toLocaleString()}g</span></div>
   <div class="items">${b.items.map(it=>`<figure>${it.slug?`<a href="/items/${esc(it.slug)}" style="display:contents">`:''}<img src="${icon(it.icon)}" alt="${esc(it.name)}" loading="lazy"><figcaption>${esc(it.name)}</figcaption>${it.slug?'</a>':''}</figure>`).join('')}</div>
   <div class="build-f">${buildFoot(b)}</div></div>`).join('');
}
function close(){
 $('#drawer').classList.remove('open');$('#scrim').classList.remove('open');history.replaceState(null,'','/heroes');
 document.querySelectorAll('.dot.on').forEach(x=>x.classList.remove('on'));
 if(lastFocus)lastFocus.focus({preventScroll:true});
}
document.addEventListener('click',e=>{
 const b=e.target.closest('[data-h]'),r=e.target.closest('[data-role]');
 if(b){if(b.classList.contains('dot'))lastFocus=b;open(b.dataset.h)}
 else if(r){role=role===r.dataset.role?'':r.dataset.role;drawRoles();applyFilter()}
 else if(e.target.id==='close'||e.target.id==='scrim')close();
});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&$('#drawer').classList.contains('open'))close()});
$('#q').oninput=applyFilter;
$('#q').onkeydown=e=>{if(e.key==='Enter'){const d=document.querySelector('.dot:not(.dim)');if(d){lastFocus=d;open(d.dataset.h)}}};
addEventListener('hashchange',()=>location.hash?open(location.hash.slice(1),false):close());
let rw;addEventListener('resize',()=>{clearTimeout(rw);rw=setTimeout(drawRiver,150)});
(async()=>{
 await shell('/heroes');
 const hs=await call('/api/heroes');if(hs.error){toast(hs.error,'var(--ember)');return}
 heroes=hs;heroes.forEach(h=>byKey[h.slug]=h);
 drawRoles();drawRiver();
 const path=location.pathname.match(/^\/heroes\/([^/]+)$/);
 if(path)open(decodeURIComponent(path[1]),false);
 else if(location.hash)open(location.hash.slice(1),false); // old #slug links
})();
