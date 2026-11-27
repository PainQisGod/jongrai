// Jongrai - script for /hero-statistics.html. Runs after /app.js and /kit.js.
const ROLE_ICON={TANK:'Emblem_Tank.png',FIGHTER:'Emblem_Fighter.png',ASSASSIN:'Emblem_Assassin.png',MAGE:'Emblem_Mage.png',MARKSMAN:'Emblem_Marksman.png',SUPPORT:'Emblem_Support.png'};
const TIER_LABEL={S:'top',A:'strong',B:'solid',C:'average',D:'weak'};
const tr=(...a)=>t(...a);   // app.js's t() under another name: drawRiver loops over tiers as t
const SPIKES=['EARLY','MID','LATE'];
let heroes=[],byKey={},role='',lastFocus=null;
const cap=s=>s[0].toUpperCase()+s.slice(1).toLowerCase();
const key=n=>n.toLowerCase();

/** Responsive tier board plus a complete sortable hero statistics table. */
let sortBy='winRate';
function visibleHeroes(){
 const q=$('#q').value.trim().toLowerCase();
 return heroes.filter(h=>(!q||h.name.toLowerCase().includes(q))&&(!role||h.roles.includes(role)));
}
function heroButton(h,compact=false){
 return `<button class="hero-card${h.metaBan?' banned':''}" data-h="${esc(h.slug)}" aria-label="${esc(h.name)}, tier ${h.tier}, win rate ${h.winRate.toFixed(2)} percent">
  <span class="hero-face"><img src="${icon(h.icon)}" alt="" loading="lazy"></span>
  <span class="hero-card-name">${esc(h.name)}</span>
  <span class="hero-card-rate">${h.winRate.toFixed(1)}%</span>
 </button>`;
}
function drawTierBoard(){
 const host=$('#tier-board'),list=visibleHeroes();
 host.innerHTML='SABCD'.split('').map(tier=>{
  const members=list.filter(h=>h.tier===tier).sort((a,b)=>b.winRate-a.winRate);
  return `<section class="tier-row tier-${tier}">
   <div class="tier-label"><span class="tier ${tier}">${tier}</span><div><b>${tr('he.tier.'+tier,TIER_LABEL[tier])}</b><small>${members.length} ${tr('he.heroes','heroes')}</small></div></div>
   <div class="tier-heroes">${members.length?members.map(h=>heroButton(h)).join(''):`<p class="tier-empty">${tr('he.noTierMatches','No heroes match these filters.')}</p>`}</div>
  </section>`;
 }).join('');
}
function drawStatsTable(){
 const list=visibleHeroes().slice().sort((a,b)=>sortBy==='name'?a.name.localeCompare(b.name):b[sortBy]-a[sortBy]);
 $('#hero-stat-rows').innerHTML=list.map(h=>`<tr>
  <td><button class="stat-hero" data-h="${esc(h.slug)}"><span class="stat-face${h.metaBan?' banned':''}"><img src="${icon(h.icon)}" alt="" loading="lazy"></span><b>${esc(h.name)}</b></button></td>
  <td>${esc(h.roles.map(cap).join(' / '))}</td>
  <td><span class="tier ${h.tier}">${h.tier}</span></td>
  <td class="num">${h.winRate.toFixed(2)}%</td><td class="num">${h.pickRate.toFixed(2)}%</td><td class="num ${h.metaBan?'meta-ban':''}">${h.banRate.toFixed(1)}%</td>
 </tr>`).join('');
}
function applyFilter(){
 const list=visibleHeroes();
 $('#match').textContent=$('#q').value.trim()||role?tr('he.matchN','{n} of {total} match',{n:list.length,total:heroes.length}):tr('he.count','{n} heroes',{n:heroes.length});
 drawTierBoard();drawStatsTable();
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
 if(b.lane)out.push(`<span class="bf-chip" title="${esc(tr('he.lane','Lane'))}: ${esc(cap(b.lane))}">${li?`<img src="/icons/${li}" alt="">`:''}<b>${esc(cap(b.lane))}</b></span>`);
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
 $('#drawer').innerHTML=`<button class="close" id="close" aria-label="${esc(tr('he.close','Close profile'))}">✕</button>
  <div class="banner"><img class="aura" src="${icon(h.icon)}" alt=""><img class="face" src="${icon(h.icon)}" alt="">
   <div><h2 id="dname">${esc(h.name)}</h2><div class="meta"><span class="tier ${esc(h.tier)}">${esc(h.tier)}</span>
   ${h.roles.map(r=>`<span class="pill">${ROLE_ICON[r]?`<img src="${icon(ROLE_ICON[r])}" alt="">`:''}${esc(cap(r))}</span>`).join('')}
   ${h.lanes.map(l=>`<span class="pill"><img src="/icons/${laneIcon(l)}" alt="">${esc(cap(l))}</span>`).join('')}</div></div></div>
  <div class="body">
   <div class="sect"><div class="stats">
    <div class="stat"><b class="num">${h.winRate.toFixed(2)}%</b><small>${tr('he.wr','win rate')} · <span class="${d>=0?'up':'down'}">${tr('he.vsFlip','{v} vs coin flip',{v:(d>=0?'+':'')+d.toFixed(1)})}</span></small></div>
    <div class="stat"><b class="num">${h.pickRate.toFixed(2)}%</b><small>${tr('he.pr','pick rate')}</small></div>
    <div class="stat"><b class="num" style="color:${h.metaBan?'var(--red-hi)':'var(--ink)'}">${h.banRate.toFixed(1)}%</b><small>${h.metaBan?tr('he.br','ban rate')+' · '+tr('he.metaBan','meta ban'):tr('he.br','ban rate')}</small></div>
    <div class="stat"><b class="num">#${rank}</b><small>${esc(tr('he.ofTier','of {n} in tier {t}',{n:tier.length,t:h.tier}))}</small></div></div></div>
   <div class="sect"><h3>${tr('he.ratings','In-game ratings')}</h3>${rating(tr('he.r.durability','Durability'),h.durability)}${rating(tr('he.r.offense','Offense'),h.offense)}${rating(tr('he.r.control','Control'),h.control)}</div>
   <div class="sect"><h3>${tr('he.spike','Power spike')}</h3><div class="spike">${SPIKES.map(s=>`<div class="${h.spike===s||h.spike==='ALL'?'on':''}">${tr('dr.timing.'+s.toLowerCase(),s)}</div>`).join('')}</div></div>
   <div class="sect"><h3>${tr('he.tip','Jongrai tip')}</h3>${h.tip?`<div class="tip">${tipSections(h.tip).map(s=>`<p>${s.label?`<b>${esc(s.label)}</b>`:''}${esc(s.text)}</p>`).join('')}</div>`:`<p class="tip">${tr('he.noTip','No tip recorded for this hero yet.')}</p>`}<div class="tags">${h.tags.map(t=>`<span class="pill">${esc(t)}</span>`).join('')}</div></div>
   <div class="sect"><h3>${tr('he.matchups','Matchups')}</h3>${rel(tr('he.strong','Strong vs'),'var(--jade)',strong,tr('he.noStrong','No hard counters recorded.'))}${rel(tr('he.weak','Weak vs'),'var(--ember)',weak,tr('he.noWeak','Nobody counters this hero on record.'))}${rel(tr('he.works','Works with'),'var(--gold)',combo,tr('he.noWorks','No recorded combos.'))}</div>
   <div class="sect"><h3>${tr('he.builds','Builds')}</h3><div id="builds"><div class="skel"></div></div></div>
  </div>`;
 $('#drawer').classList.add('open');$('#scrim').classList.add('open');
 document.querySelectorAll('.hero-card.on,.stat-hero.on').forEach(x=>x.classList.remove('on'));
 const trigger=document.querySelector(`[data-h="${CSS.escape(slug)}"]`);if(trigger)trigger.classList.add('on');
 $('#drawer').scrollTop=0;$('#drawer').focus({preventScroll:true});
 const bs=await call('/api/builds?hero='+encodeURIComponent(h.name));
 if(byKey[slug]!==h||!$('#builds'))return;
 $('#builds').innerHTML=bs.error?`<p class="none">${esc(bs.error)}</p>`:!bs.length?`<p class="none">${tr('he.noBuilds','No curated builds for this hero yet.')}</p>`
  :bs.map(b=>`<div class="build"><div class="build-h"><b>${esc(b.name)}</b><span class="num">${b.totalCost.toLocaleString()}g</span></div>
   <div class="items">${b.items.map(it=>`<figure>${it.slug?`<a href="/items/${esc(it.slug)}" style="display:contents">`:''}<img src="${icon(it.icon)}" alt="${esc(it.name)}" loading="lazy"><figcaption>${esc(it.name)}</figcaption>${it.slug?'</a>':''}</figure>`).join('')}</div>
   <div class="build-f">${buildFoot(b)}</div></div>`).join('');
}
function close(){
 $('#drawer').classList.remove('open');$('#scrim').classList.remove('open');history.replaceState(null,'','/heroes');
 document.querySelectorAll('.hero-card.on,.stat-hero.on').forEach(x=>x.classList.remove('on'));
 if(lastFocus)lastFocus.focus({preventScroll:true});
}
document.addEventListener('click',e=>{
 const b=e.target.closest('[data-h]'),r=e.target.closest('[data-role]');
 if(b){lastFocus=b;open(b.dataset.h)}
 else if(r){role=role===r.dataset.role?'':r.dataset.role;drawRoles();applyFilter()}
 else if(e.target.id==='close'||e.target.id==='scrim')close();
});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&$('#drawer').classList.contains('open'))close()});
$('#q').oninput=applyFilter;
$('#q').onkeydown=e=>{if(e.key==='Enter'){const d=visibleHeroes()[0];if(d){lastFocus=document.querySelector('[data-h="'+CSS.escape(d.slug)+'"]');open(d.slug)}}};
$('#hero-sort').onchange=e=>{sortBy=e.target.value;drawStatsTable()};
addEventListener('hashchange',()=>location.hash?open(location.hash.slice(1),false):close());

(async()=>{
 await shell('/heroes');
 const hs=await call('/api/heroes');if(hs.error){toast(hs.error,'var(--ember)');return}
 heroes=await localizeTips(hs);heroes.forEach(h=>byKey[h.slug]=h);
 drawRoles();applyFilter();
 const path=location.pathname.match(/^\/heroes\/([^/]+)$/);
 if(path)open(decodeURIComponent(path[1]),false);
 else if(location.hash)open(location.hash.slice(1),false); // old #slug links
})();
