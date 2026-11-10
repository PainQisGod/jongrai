// Jongrai - script for /draft.html. Runs after /app.js and /kit.js.
// Translation: tr() is app.js's t() under another name, because many functions here use t for a team side.
const tr=(...a)=>t(...a);
const sideTxt=s=>tr('dr.side.'+s,cap(s));           // Blue / Red
const kindTxt=(k,plural)=>tr('dr.kind.'+k+(plural?'s':''),plural?k+'s':k);  // pick(s) / ban(s)
const timingTxt=s=>tr('dr.timing.'+String(s).toLowerCase(),String(s).toLowerCase());
/** The engine sends English sentences (analysis details, headlines, suggestion reasons); in Khmer they are rebuilt from these patterns. */
const EN_PATTERNS=[
 [/^The draft is even$/,()=>tr('dr.h.even','The draft is even')],
 [/^(Blue|Red) has a (commanding|clear|slight) draft edge$/,(m)=>tr('dr.h.edge.'+m[2],'{side} has a '+m[2]+' draft edge',{side:sideTxt(m[1].toLowerCase())})],
 [/^Counter matchups are even \((\d+) vs (\d+)\)$/,(m)=>tr('dr.d.counterEven','Counter matchups are even ({a} vs {b})',{a:m[1],b:m[2]})],
 [/^(Blue|Red) leads the counter matchups \((\d+) vs (\d+)\)$/,(m)=>tr('dr.d.counterLead','{side} leads the counter matchups ({a} vs {b})',{side:sideTxt(m[1].toLowerCase()),a:m[2],b:m[3]})],
 [/^Avg win rate ([\d.]+)% vs ([\d.]+)%$/,(m)=>tr('dr.d.meta','Avg win rate {a}% vs {b}%',{a:m[1],b:m[2]})],
 [/^(\d+) combo pairs vs (\d+)$/,(m)=>tr('dr.d.synergy','{a} combo pairs vs {b}',{a:m[1],b:m[2]})],
 [/^Heavy-control heroes (\d+) vs (\d+)$/,(m)=>tr('dr.d.lockdown','Heavy-control heroes {a} vs {b}',{a:m[1],b:m[2]})],
 [/^Coordination (\d+)\/100 vs (\d+)\/100$/,(m)=>tr('dr.d.coord','Coordination {a}/100 vs {b}/100',{a:m[1],b:m[2]})],
 [/^counters your (.+)$/,(m)=>tr('dr.r.countersYour','counters your {h}',{h:m[1]})],
 [/^counters (.+)$/,(m)=>tr('dr.r.counters','counters {h}',{h:m[1]})],
 [/^weak into (.+)$/,(m)=>tr('dr.r.weak','weak into {h}',{h:m[1]})],
 [/^synergy with their (.+)$/,(m)=>tr('dr.r.synergyTheir','synergy with their {h}',{h:m[1]})],
 [/^synergy with (.+)$/,(m)=>tr('dr.r.synergy','synergy with {h}',{h:m[1]})],
 [/^your team has no tank$/,()=>tr('dr.r.noTank','your team has no tank')],
 [/^fills open lane$/,()=>tr('dr.r.openLane','fills open lane')],
 [/^lane already covered$/,()=>tr('dr.r.laneCovered','lane already covered')],
 [/^already have 2\+ (\w+)$/,(m)=>tr('dr.r.dupRole','already have 2+ {r}',{r:cap(m[1].toLowerCase())})],
 [/^(high|low) win rate ([\d.]+)%$/,(m)=>tr('dr.r.wr.'+m[1],m[1]+' win rate {v}%',{v:m[2]})],
 [/^ban rate ([\d.]+)%$/,(m)=>tr('dr.r.banRate','ban rate {v}%',{v:m[1]})],
 [/^(\d+)% win on (.+) \((\d+) pro picks\)$/,(m)=>tr('dr.r.mapWin','{p}% win on {map} ({n} pro picks)',{p:m[1],map:m[2],n:m[3]})],
 [/^contested on (.+) \((\d+)% of games\)$/,(m)=>tr('dr.r.mapContested','contested on {map} ({p}% of games)',{map:m[1],p:m[2]})],
 // map mechanic: "<map>: <reason>", Khmer under dr.fx.<id> (ids come with /api/maps)
 [/^([A-Z][A-Za-z ]+): (.+)$/,(m)=>FX_ID[m[2]]?m[1]+': '+tr('dr.fx.'+FX_ID[m[2]],m[2]):m[0]]];
let FX_ID={};
const trEn=s=>{if(LANG!=='km')return s;for(const[re,f]of EN_PATTERNS){const m=re.exec(s);if(m)return f(m)}return s};
const CAT_KEY={'COUNTERS':'counters','META POWER':'meta','SYNERGY':'synergy','LOCKDOWN':'lockdown','TEAM COORDINATION':'coord'};
const catTxt=n=>CAT_KEY[n]?tr('dr.cat.'+CAT_KEY[n],n):n;
const axisTxt=n=>tr('dr.axis.'+n.toLowerCase(),n);
const TOURNAMENT=[...'BRBRBR'].map(s=>['ban',s,0]).concat([...'BRRBBR'].map(s=>['pick',s,1]),[...'RBRB'].map(s=>['ban',s,2]),[...'RBBR'].map(s=>['pick',s,3]));
// With bans off the board is the engine's own 10-pick snake, like the original page.
const PICKS_ONLY=[...'BRRBBR'].map(s=>['pick',s,1]).concat([...'RBBR'].map(s=>['pick',s,3]));
let bansOn=store.get('jongrai-bans')!=='off';
const order=()=>bansOn?TOURNAMENT:PICKS_ONLY;
const PHASES=['Ban phase 1','Pick phase 1','Ban phase 2','Pick phase 2'];
const phaseTxt=i=>tr('dr.phase.'+i,PHASES[i]);
const SIDE={B:'blue',R:'red'},LANES=['EXP','JUNGLE','MID','GOLD','ROAM'];
const ROLE_ICON={TANK:'Emblem_Tank.png',FIGHTER:'Emblem_Fighter.png',ASSASSIN:'Emblem_Assassin.png',MAGE:'Emblem_Mage.png',MARKSMAN:'Emblem_Marksman.png',SUPPORT:'Emblem_Support.png'};
const TIER_ORDER='SABCD';
const SORT=()=>`<select class="sortsel" id="sort" aria-label="${esc(tr('dr.sort.label','Sort heroes'))}"><option value="tier">${tr('dr.sort.tier','Sort: tier')}</option><option value="win">${tr('dr.sort.win','Sort: win rate')}</option><option value="ban">${tr('dr.sort.ban','Sort: ban rate')}</option><option value="name">${tr('dr.sort.name','Sort: A–Z')}</option></select>`;
let heroes=[],byName={},state={version:0,blue:{picks:[],bans:[]},red:{picks:[],bans:[]}};
let sortBy='tier',follow=true,manual={side:'blue',kind:'pick'},role='',lane='',undoStack=[],recs=[],fly=null,seen=new Set();
const other=s=>s==='blue'?'red':'blue';
const cap=s=>s[0].toUpperCase()+s.slice(1);
const pickNames=s=>state[s].picks.map(p=>p.name);
const key=n=>n.toLowerCase();

/** The first step of the 20-step order a side has not filled yet. Works even when the board was filled out of order. */
// Steps whose timer ran out with nothing chosen: they stay blank and the turn moves on.
const skipped=new Set();
function currentStep(){
 const need={blue:{pick:0,ban:0},red:{pick:0,ban:0}};
 for(let i=0;i<order().length;i++){if(skipped.has(i))continue;const[k,s]=order()[i],side=SIDE[s];need[side][k]++;
  if(state[side][k+'s'].length<need[side][k])return i}
 return order().length;
}
function acting(){const i=currentStep();if(!follow||i>=order().length)return manual;const[k,s]=order()[i];return{side:SIDE[s],kind:k}}

/* ---------- rail ---------- */
function drawRail(){
 const i=currentStep(),a=acting(),done=i>=order().length;
 const need={blue:{pick:0,ban:0},red:{pick:0,ban:0}};
 $('#notches').innerHTML=order().map(([k,s,ph],n)=>{const side=SIDE[s],skip=skipped.has(n),idx=skip?-1:need[side][k]++,list=state[side][k+'s'],hit=skip?null:list[idx];
  const name=hit?(hit.name||hit):'';
  return(n&&ph!==order()[n-1][2]?'<li class="gap" aria-hidden="true"></li>':'')
   +`<li class="${k} ${s}${hit?' done':''}${skip?' skipped':''}${n===i?' now':''}" title="${n+1}. ${cap(side)} ${k}${skip?': skipped (time ran out)':name?': '+esc(name):''}"></li>`}).join('');
 $('#phase').textContent=done?tr('dr.complete','Draft complete'):phaseTxt(order()[i][2]);
 $('#turnLine').innerHTML=done?tr('dr.locked','Both sides <b>locked</b>'):`<b class="${a.side}">${sideTxt(a.side)}</b> ${kindTxt(a.kind,true)}${follow?'':` <small style="font-size:12px;color:var(--ink-3);letter-spacing:.1em">· ${tr('dr.freeMode','FREE MODE')}</small>`}`;
 const total=order().length;$('#stepNo').textContent=done?`${total} / ${total}`:tr('dr.step','STEP {n} / {total}',{n:String(i+1).padStart(2,'0'),total});
 document.querySelectorAll('#sideSeg button').forEach(b=>b.setAttribute('aria-pressed',b.dataset.s===a.side));
 document.querySelectorAll('#kindSeg button').forEach(b=>{b.setAttribute('aria-pressed',b.dataset.k===a.kind);b.disabled=b.dataset.k==='ban'&&!bansOn});
 document.querySelector('main').classList.toggle('no-bans',!bansOn&&!state.blue.bans.length&&!state.red.bans.length);
 document.querySelectorAll('.side').forEach(el=>el.classList.toggle('acting-now',!done&&el.id==='side-'+a.side));
 document.documentElement.style.setProperty('--act',a.side==='blue'?'var(--blue)':'var(--red)');
}

/* ---------- stage ---------- */
const face=n=>{const h=byName[n];return h&&h.icon?icon(h.icon):''};
function door(t,p,i,next){
 const code=(t==='blue'?'B':'R')+(i+1);
 if(!p)return[`${code}|${next}`,`<div class="door empty${next?' next':''}" data-slot="${code}"><span class="code">${code}</span>${next?`<span class="cue">${tr('dr.picking','Picking')}</span>`:''}</div>`];
 const h=byName[p.name]||{lanes:[]},v=h.video,li=laneIcon(p.lane); // pick animation from web/video, listed by the server
 const opts=[...h.lanes,...LANES.filter(l=>!h.lanes.includes(l))].map(l=>`<option value="${l}"${l===p.lane?' selected':''}>${l}${h.lanes.includes(l)?'':' · '+tr('dr.flex','flex')}</option>`).join('');
 const art=v?`<video src="/video/${encodeURIComponent(v)}" autoplay muted loop playsinline preload="metadata" aria-hidden="true"></video>`
  :`<img class="aura" src="${face(p.name)}" alt=""><img class="face" src="${face(p.name)}" alt="">`;
 return[`${p.name}|${p.lane}`,`<div class="door filled" data-hero="${esc(p.name)}">${art}
  <span class="code small">${code}</span>${h.tier?`<span class="tier ${esc(h.tier)}">${esc(h.tier)}</span>`:''}<span class="flag"></span>
  <div class="plate"><b class="nm">${esc(p.name)}</b><label class="lanesel">${li?`<img src="/icons/${li}" alt="">`:''}<span class="sr">${esc(tr('dr.laneFor','Lane for {h}',{h:p.name}))}</span>
  <select data-lane data-t="${t}" data-hero="${esc(p.name)}">${opts}</select></label></div>
  <button class="x" data-rm data-t="${t}" data-hero="${esc(p.name)}" aria-label="${esc(tr('dr.remove','Remove {h}',{h:p.name}))}">✕</button></div>`];
}
function seal(t,n,i,next){
 if(!n)return[`|${next}`,`<div class="seal empty${next?' next':''}">${i+1}</div>`];
 return[n,`<div class="seal" data-hero="${esc(n)}" title="${esc(tr('dr.banned','{h} banned',{h:n}))}"><img src="${face(n)}" alt="${esc(tr('dr.banned','{h} banned',{h:n}))}">
  <svg viewBox="0 0 56 56" aria-hidden="true"><path d="M12 44 Q 26 30 45 11"/></svg>
  <button class="x" data-rm data-t="${t}" data-hero="${esc(n)}" aria-label="${esc(tr('dr.liftBan','Lift ban on {h}',{h:n}))}">✕</button></div>`];
}
/** Replaces only the slots whose content changed, so hero videos keep playing and fresh arrivals can animate. */
function patch(box,parts,fresh){
 if(box.children.length!==parts.length)box.innerHTML=parts.map(()=>'<div></div>').join('');
 parts.forEach(([k,html],i)=>{const old=box.children[i];if(old.dataset.k===k)return;
  const tmp=document.createElement('div');tmp.innerHTML=html;const el=tmp.firstElementChild;el.dataset.k=k;
  const h=el.dataset.hero;if(h&&fresh&&!seen.has(h))el.classList.add('fresh');old.replaceWith(el)});
}
let coordCache=null;
function drawSides(fresh){
 const a=acting(),i=currentStep(),done=i>=order().length;
 for(const t of['blue','red']){
  const el=$('#side-'+t);
  if(!el.firstChild){
   const saved=store.get('jongrai-name-'+t)||(t==='blue'?tr('dr.blueSide','Blue side'):tr('dr.redSide','Red side'));
   el.innerHTML=`<div class="side-head"><input class="tname" data-team="${t}" value="${esc(saved)}" aria-label="${esc(tr('dr.teamName','{side} team name',{side:sideTxt(t)}))}" spellcheck="false"><div class="pips" data-pips></div></div>
    <div class="doors"></div><div class="seals"></div><div class="bans-lbl"><span>${tr('dr.bansLbl','BANS')}</span><span class="num" data-bc></span></div>`;
  }
  const nextPick=!done&&a.side===t&&a.kind==='pick',nextBan=!done&&a.side===t&&a.kind==='ban';
  const picks=Array.from({length:5},(_,j)=>door(t,state[t].picks[j],j,nextPick&&j===state[t].picks.length));
  const bans=Array.from({length:5},(_,j)=>seal(t,state[t].bans[j],j,nextBan&&j===state[t].bans.length));
  patch(el.querySelector('.doors'),picks,fresh);patch(el.querySelector('.seals'),bans,fresh);
  el.querySelector('[data-bc]').textContent=state[t].bans.length+' / 5';
 }
 drawPips();
}
function drawPips(){
 for(const t of['blue','red']){
  const miss=coordCache?coordCache[t].missingLanes:LANES;
  $('#side-'+t+' [data-pips]').innerHTML=LANES.map(l=>`<img src="/icons/${laneIcon(l)}" alt="${l} ${miss.includes(l)?tr('dr.open','open'):tr('dr.covered','covered')}" title="${l}: ${miss.includes(l)?tr('dr.open','open'):tr('dr.covered','covered')}" class="${miss.includes(l)?'':'on'}">`).join('');
 }
}

/* ---------- pool ---------- */
function usedMap(){const m={};for(const t of['blue','red']){state[t].picks.forEach((p,i)=>m[p.name]=(t==='blue'?'B':'R')+(i+1));state[t].bans.forEach(n=>m[n]='X')}return m}
/** Pool heroes that counter (up) or are countered by (dn) the enemy's current picks. */
function relations(h,side){
 const foe=pickNames(other(side)).map(n=>byName[n]).filter(Boolean),mine=pickNames(side).map(n=>byName[n]).filter(Boolean);
 return{strong:foe.filter(f=>h.counters.includes(key(f.name))).map(f=>f.name),
  weak:foe.filter(f=>f.counters.includes(key(h.name))).map(f=>f.name),
  combo:mine.filter(m=>h.synergies.includes(key(m.name))||m.synergies.includes(key(h.name))).map(m=>m.name)};
}
function drawGrid(){
 const q=$('#q').value.trim().toLowerCase(),used=usedMap(),a=acting(),sort=sortBy;
 const list=heroes.filter(h=>h.name.toLowerCase().includes(q)&&(!role||h.roles.includes(role))&&(!lane||h.lanes.includes(lane)))
  .sort((x,y)=>sort==='win'?y.winRate-x.winRate:sort==='ban'?y.banRate-x.banRate:sort==='name'?x.name.localeCompare(y.name)
   :TIER_ORDER.indexOf(x.tier)-TIER_ORDER.indexOf(y.tier)||y.winRate-x.winRate);
 const hl=n=>{if(!q)return esc(n);const i=n.toLowerCase().indexOf(q);return esc(n.slice(0,i))+'<mark>'+esc(n.slice(i,i+q.length))+'</mark>'+esc(n.slice(i+q.length))};
 $('#count').textContent=tr('dr.count','{a} available · {s} shown',{a:list.filter(h=>!used[h.name]).length,s:list.length});
 $('#grid').innerHTML=list.length?list.map(h=>{const u=used[h.name],r=relations(h,a.side),rank=recs.indexOf(h.name);
  const wr=Math.max(0,Math.min(100,(h.winRate-40)*5));
  return`<button class="tile${u?' used':''}${rank>=0&&!u?' rec':''}" data-h="${esc(h.name)}"${u?' aria-disabled="true"':''}
   aria-label="${esc(h.name)}, ${esc(tr('dr.tierWord','tier'))} ${esc(h.tier)}, ${h.winRate.toFixed(1)}% ${esc(tr('dr.winWord','win'))}${u?', '+(u==='X'?tr('dr.isBanned','banned'):tr('dr.isPicked','picked')):''}">
   <span class="hint" aria-hidden="true">${esc(tr('dr.hint.'+a.kind,a.kind.toUpperCase()))}</span>
   ${rank>=0&&!u?`<span class="rank num" title="${esc(tr('dr.enginePick','Engine pick #{n}',{n:rank+1}))}">#${rank+1}</span>`:''}
   ${!u&&(r.strong.length||r.weak.length)?`<span class="marks">${r.strong.length?`<b class="up" title="${esc(tr('dr.strongAgainst','Strong against {list}',{list:r.strong.join(', ')}))}">▲${r.strong.length}</b>`:''}${r.weak.length?`<b class="dn" title="${esc(tr('dr.weakAgainst','Weak against {list}',{list:r.weak.join(', ')}))}">▼${r.weak.length}</b>`:''}</span>`:''}
   <span class="pt"><img src="${icon(h.icon)}" alt=""><span class="tier ${esc(h.tier)}">${esc(h.tier)}</span></span>
   ${u?`<span class="tag ${u[0]}">${u==='X'?esc(tr('dr.hint.ban','BAN')):u}</span>`:''}
   <span class="tn">${hl(h.name)}</span><span class="wr ${h.winRate>=51?'up':h.winRate<49?'down':''}"><i style="width:${wr}%"></i></span></button>`}).join('')
  :`<div class="empty-pool"><b>${esc(tr('dr.noMatch','No hero matches “{q}”',{q:$('#q').value}))}</b>${tr('dr.noMatchHint','Try part of the name, or clear the role and lane filters.')}<br><br><button class="chip" id="clearAll">${tr('dr.clearFilters','Clear filters')}</button></div>`;
 lens(null);
}
function lens(h){
 const keys=`<span class="keys"><span><kbd>/</kbd> ${tr('dr.key.search','search')}</span><span><kbd>↵</kbd> ${tr('dr.key.first.'+acting().kind,acting().kind+' first')}</span><span><kbd>Ctrl</kbd><kbd>Z</kbd> ${tr('dr.key.undo','undo')}</span></span>`;
 document.querySelectorAll('.door[class*=rel-]').forEach(d=>d.classList.remove('rel-strong','rel-weak','rel-combo'));
 if(!h){$('#lens').innerHTML=`<span>${tr('dr.lens.hover','Hover a hero to see how it fits this board.')}</span>${keys}`;return}
 const a=acting(),r=relations(h,a.side);
 const mark=(names,cls,label)=>names.forEach(n=>{const d=document.querySelector(`.door[data-hero="${CSS.escape(n)}"]`);if(d){d.classList.add(cls);d.querySelector('.flag').textContent=label}});
 mark(r.strong,'rel-strong',tr('dr.flag.strong','STRONG VS'));mark(r.weak,'rel-weak',tr('dr.flag.threat','THREAT'));mark(r.combo,'rel-combo',tr('dr.flag.combo','COMBO'));
 const rel=(names,c,l)=>names.length?`<span class="rel"><i style="background:${c}"></i>${l} ${esc(names.join(', '))}</span>`:'';
 $('#lens').innerHTML=`<img src="${icon(h.icon)}" alt=""><div><b>${esc(h.name)}</b> · ${esc(h.roles.join(' / ').toLowerCase())} · ${esc(h.lanes.join(' / ').toLowerCase())} · ${tr('dr.lens.wr','win rate')} <span class="num">${h.winRate.toFixed(2)}%</span>${h.banRate?` · ${tr('dr.lens.br','ban rate')} <span class="num">${h.banRate.toFixed(1)}%</span>`:''}<br>
  ${rel(r.strong,'var(--jade)',tr('dr.rel.strong','strong vs'))}${rel(r.weak,'var(--ember)',tr('dr.rel.weak','weak vs'))}${rel(r.combo,'var(--gold)',tr('dr.rel.combo','combos with'))}${!r.strong.length&&!r.weak.length&&!r.combo.length?`<span style="color:var(--ink-3)">${esc(h.tip||tr('dr.rel.none','No direct relation to the heroes on the board yet.'))}</span>`:''}</div>${keys}`;
}

/* ---------- council ---------- */
async function drawNext(){
 const a=acting(),done=currentStep()>=order().length&&follow;
 if(done){recs=[];$('#next').innerHTML=`<h2>${tr('dr.next','Next move')} <small>${tr('dr.next.done','draft complete')}</small></h2><p style="margin:0;color:var(--ink-2)">${tr('dr.next.doneText','All twenty steps are in. Ask Jongrai for the read below, or tap a slot to change it.')}</p>`;return}
 const list=await call(`/api/${a.kind==='pick'?'suggest':'bans'}?side=${a.side}${mapQ()}`);
 if(list.error||!Array.isArray(list)){$('#next').innerHTML=`<h2>${tr('dr.next','Next move')}</h2><p style="margin:0;color:var(--ember)">${esc(list.error||tr('dr.next.none','No suggestions'))}</p>`;return}
 recs=list.map(s=>s.hero);
 // the header already names the map, so drop the "<map>: " prefix on map-effect reasons here
 const why=r=>{const t=trEn(r);return mapName&&t.startsWith(mapName+': ')?t.slice(mapName.length+2):t};
 const [top,...rest]=list,tag=`<small>${sideTxt(a.side)} ${kindTxt(a.kind)} · ${tr('dr.next.ranked','engine ranked')}${mapName?' · '+esc(mapName):''}</small>`;
 $('#next').innerHTML=top?`<h2>${a.kind==='pick'?tr('dr.next.bestPick','Best pick'):tr('dr.next.bestBan','Best ban')} ${tag}</h2>
  <button class="feature" data-sg="${esc(top.hero)}"><img src="${face(top.hero)}" alt=""><span><b>${esc(top.hero)}</b><span class="reasons">${top.reasons.map(r=>`<span>${esc(why(r))}</span>`).join('')}</span></span><span class="score-pill num">${esc(top.score)}</span></button>
  <ul class="alts">${rest.map(s=>`<li><button data-sg="${esc(s.hero)}"><img src="${face(s.hero)}" alt=""><span><b>${esc(s.hero)}</b><small>${esc(s.reasons.map(why).join(' · ')||'—')}</small></span><span class="num">${esc(s.score)}</span></button></li>`).join('')}</ul>`
  :`<h2>${tr('dr.next','Next move')} ${tag}</h2><p style="margin:0;color:var(--ink-2)">${tr('dr.next.nothing','Nothing left to suggest for this step.')}</p>`;
 drawGrid();drawMapPlan();
}
/** With a map chosen: what the map does, and the heroes it favours (pick / ban) or hurts (avoid) for the acting side. */
async function drawMapPlan(){
 const box=$('#mapplan'),m=mapList.find(x=>x.name===mapName);
 if(!m){box.hidden=true;box.innerHTML='';return}
 const a=acting(),p=await call(`/api/mapplan?side=${a.side}${mapQ()}`);
 if(!p||p.error||!p.map){box.hidden=true;return}
 const strip=(list,cls,clickable)=>list.length?`<ul class="mp-heroes ${cls}">${list.map(s=>{const why=s.reasons.map(trEn).map(r=>r.replace(mapName+': ','')).join(' · ');
   return`<li>${clickable?`<button data-sg="${esc(s.hero)}"`:'<span'} title="${esc(s.hero+(why?' — '+why:''))}"><img src="${face(s.hero)}" alt=""><b>${esc(s.hero)}</b>${clickable?'</button>':'</span>'}</li>`}).join('')}</ul>`
   :`<p class="mp-none">${tr('dr.plan.none','No hero stands out.')}</p>`;
 const fx=m.effects.map(e=>`<li class="${e.weight<0?'down':'up'}"><span class="num">${e.weight>0?'+':''}${e.weight.toFixed(1)}</span>${esc(tr('dr.fx.'+e.id,e.reason))}</li>`).join('');
 box.hidden=false;
 box.innerHTML=`<h2>${tr('dr.plan','Map plan')} <small>${esc(m.name)} · ${tr('dr.plan.forSide','for {side}',{side:sideTxt(a.side)})}</small></h2>
  <div class="mp-head"><img src="/icons/${encodeURIComponent(m.icon)}" alt=""><p>${esc(tr('dr.mapfx.desc.'+m.slug,m.description))}</p></div>
  ${fx?`<ul class="mp-fx">${fx}</ul>`:''}
  <h3>${tr('dr.plan.pick','Pick for this map')}</h3>${strip(p.pick,'pick',a.kind==='pick')}
  <h3>${tr('dr.plan.ban','Ban for this map')}</h3>${strip(p.ban,'ban',a.kind==='ban')}
  <h3>${tr('dr.plan.avoid','Weak on this map')}</h3>${strip(p.avoid,'avoid',false)}`;
}
function petal(i,n,v,shift){
 const a=(-90+i*360/n+shift)*Math.PI/180,r=14+v/10*70,w=.2,c=100;
 const P=(ang,rad)=>[(c+Math.cos(ang)*rad).toFixed(1),(c+Math.sin(ang)*rad).toFixed(1)].join(' ');
 return`M${c} ${c} Q${P(a-w,r*.8)} ${P(a,r)} Q${P(a+w,r*.8)} ${c} ${c}Z`;
}
async function drawAnalysis(){
 const a=await call('/api/analysis');if(a.error){toast(a.error,'var(--ember)');return}
 lastA=a;coordCache=a.coordination;drawPips();
 const C=527.8,b=a.blueEdge;
 // Blue's arc is centred on the left (Blue's side of the screen); both boundaries move as the edge shifts.
 $('#arc').setAttribute('stroke-dasharray',`${C*b/100} ${C}`);$('#arc').style.transform=`rotate(${180-b*1.8}deg)`;
 $('#needle').style.transform=`rotate(${b*1.8-90}deg)`;$('#needle2').style.transform=`rotate(${-90-b*1.8}deg)`;
 countTo($('#eb'),a.blueEdge);countTo($('#er'),a.redEdge);
 $('#lead').textContent=a.lead==='even'?tr('dr.lead.even','EVEN'):tr('dr.lead.'+a.lead,a.lead.toUpperCase()+' EDGE');$('#lead').className='lead '+a.lead;
 $('#headline').textContent=trEn(a.headline);
 const locked=state.blue.picks.length===5&&state.red.picks.length===5;
 $('#scale').classList.toggle('locked',locked);
 $('#edge').innerHTML=`<h2>${tr('dr.edge','Edge breakdown')} <small>${tr('dr.edge.sub','blue share per category')}</small></h2>`+a.categories.map(c=>{const s=Math.round(c.blueShare);
  return`<div class="tug"><div class="tug-h"><span class="num b">${s}</span><span>${esc(catTxt(c.name))}</span><span class="num r">${100-s}</span></div>
  <div class="tug-bar" role="img" aria-label="${esc(catTxt(c.name))}: ${esc(sideTxt('blue'))} ${s}, ${esc(sideTxt('red'))} ${100-s}"><i style="width:${c.blueShare}%"></i><em style="left:${c.blueShare}%"></em></div><small>${esc(trEn(c.detail))}</small></div>`}).join('');
 const n=a.profile.length;
 const rings=[25,50,75].map(p=>`<circle cx="100" cy="100" r="${14+p*.7}" fill="none" stroke="var(--hair)" stroke-dasharray="2 3"/>`).join('');
 const labels=a.profile.map((x,i)=>{const g=(-90+i*360/n)*Math.PI/180;// Khmer labels are long: anchor side labels outward so neighbours at the bottom don't run into each other
  const anc=LANG==='km'&&Math.abs(Math.cos(g))>.2?(Math.cos(g)>0?'start':'end'):'middle';
  return`<text x="${(100+Math.cos(g)*96).toFixed(1)}" y="${(103+Math.sin(g)*96).toFixed(1)}" text-anchor="${anc}">${esc(axisTxt(x.name))}</text>`}).join('');
 const petals=(side,col,shift)=>a.profile.map((x,i)=>`<path class="petal" d="${petal(i,n,x[side],shift)}" fill="${col}" fill-opacity=".28" stroke="${col}" stroke-width="1.2"><title>${esc(axisTxt(x.name))} · ${sideTxt(side)} ${x[side]}</title></path>`).join('');
 $('#profile').innerHTML=`<h2>${tr('dr.lotus','Lotus profile')} <small>${tr('dr.lotus.sub','nine axes, 0–10')}</small></h2><div class="lotus"><svg viewBox="${LANG==='km'?'-122 -8 386 216':'-38 -8 276 216'}" role="img" aria-label="${esc(tr('dr.lotus.label','Tactical profile, blue against red'))}">${rings}${petals('blue','#4d8dff',-7)}${petals('red','#ff5062',7)}<circle cx="100" cy="100" r="5" fill="var(--gold)"/>${labels}</svg>
  <div class="legend"><div class="row"><span></span><b style="color:var(--blue)">${tr('dr.blu','BLU')}</b><b style="color:var(--red)">${tr('dr.red','RED')}</b></div>${a.profile.map(x=>`<div class="row"><span>${esc(axisTxt(x.name))}</span><b class="num">${x.blue}</b><b class="num">${x.red}</b></div>`).join('')}</div></div>`;
 const B=a.coordination.blue,R=a.coordination.red,yn=v=>v?`<span class="yes">✓ ${tr('dr.yes','yes')}</span>`:`<span class="no">✕ ${tr('dr.no','no')}</span>`;
 const row=(k,bv,rv)=>`<span class="v">${bv}</span><span class="k">${k}</span><span class="v r">${rv}</span>`;
 $('#coord').innerHTML=`<h2>${tr('dr.coord','Coordination')} <small><span style="color:var(--blue)">${Math.round(B.score)}</span> / <span style="color:var(--red)">${Math.round(R.score)}</span> ${tr('dr.coord.of','of 100')}</small></h2><div class="coord">
  ${row(tr('dr.c.lanes','Lanes'),B.lanesCovered+' / 5',R.lanesCovered+' / 5')}${row(tr('dr.c.frontline','Frontline'),yn(B.hasTank),yn(R.hasTank))}${row(tr('dr.c.carry','Carry'),yn(B.hasMarksmanOrMage),yn(R.hasMarksmanOrMage))}
  ${row(tr('dr.c.synergies','Synergies'),B.synergyPairs,R.synergyPairs)}${row(tr('dr.c.lockdown','Lockdown'),B.heavyControl,R.heavyControl)}${row(tr('dr.c.spike','Spike'),timingTxt(B.spikeTiming),timingTxt(R.spikeTiming))}
  <div class="needs"><div>${B.missingLanes.length?`${tr('dr.c.needs','needs')} <span>${esc(B.missingLanes.join(', ').toLowerCase())}</span>`:`<span class="yes">${tr('dr.c.allLanes','every lane covered')}</span>`}</div><div style="text-align:right">${R.missingLanes.length?`${tr('dr.c.needs','needs')} <span>${esc(R.missingLanes.join(', ').toLowerCase())}</span>`:`<span class="yes">${tr('dr.c.allLanes','every lane covered')}</span>`}</div></div></div>`;
}
let coachKey='',lastA=null;
function drawCoach(){
 const b=state.blue.picks.length,r=state.red.picks.length,sig=JSON.stringify([state.blue,state.red,mapName]);
 if(coachKey&&coachKey!==sig)coachKey='';
 if(coachKey)return;
 const ready=b===5&&r===5;
 $('#coach').innerHTML=`<div class="coach-h"><h2>${tr('dr.coach','Jongrai\'s wisdom')}</h2><small>${tr('dr.coach.sub','AI coach · patch')} <span data-patch></span></small>
  <p>${ready?tr('dr.coach.ready','Ten heroes are locked. Jongrai can read the whole draft now.'):tr('dr.coach.wait','Reads the board once all ten picks are in: {b} : {r} so far.',{b:`<span class="num" style="color:var(--ink)">${b}`,r:`${r}</span>`})}</p>
  <button class="ask${ready?'':' quiet'}" id="ask">${ready?tr('dr.coach.ask','Ask Jongrai'):tr('dr.coach.early','Read it early')}</button></div>`;
 fillMeta();
}
async function askCoach(refresh){
 const sig=JSON.stringify([state.blue,state.red,mapName]);coachKey=sig;
 $('#coach').innerHTML=`<div class="coach-h"><h2>${tr('dr.coach','Jongrai\'s wisdom')}</h2><p>${tr('dr.coach.reading','Reading the draft')}<span class="ink"><i></i><i></i><i></i></span></p></div>`;
 const done=state.blue.picks.length===5&&state.red.picks.length===5;
 const w=await call('/api/wisdom?'+(done?'':'force=1&')+(refresh?'refresh=1':'')+mapQ()+(LANG==='km'?'&lang=km':''),{post:true});
 if(coachKey!==sig)return;
 if(w.error){$('#coach').innerHTML=`<div class="coach-h"><h2>${tr('dr.coach','Jongrai\'s wisdom')}</h2><small>${tr('dr.coach.off','coach off')}</small><p style="color:var(--ember)">${esc(w.error)}</p></div>`;return}
 if(!w.ready){coachKey='';drawCoach();return}
 const [lead,rest]=firstSentence(w.verdict);
 const read=(t,txt,i)=>{if(!txt)return'';const[a,b]=firstSentence(txt);
  return`<article class="read ${t} say" style="animation-delay:${.2+i*.15}s"><h3>${esc(teamName(t))}</h3><p><b>${esc(a)}</b>${esc(b)}</p></article>`};
 $('#coach').innerHTML=`<div class="coach-h"><h2>${tr('dr.coach','Jongrai\'s wisdom')}</h2><small>${tr('dr.coach.verdict','verdict · patch')} <span data-patch></span></small><button class="ask quiet" id="reread">${tr('dr.coach.reread','Re-read the draft')}</button></div>
  <div class="top-read"><div class="verdict say"><p class="lead">${esc(lead)}</p>${rest?`<p class="rest">${esc(rest)}</p>`:''}</div>${tally()}</div>
  <div class="reads">${read('blue',w.blue,0)}${read('red',w.red,1)}</div>
  ${w.closing?`<div class="watch say" style="animation-delay:.55s"><span>${tr('dr.coach.watch','Key to watch')}</span><p>${esc(w.closing)}</p></div>`:''}`;
 fillMeta();
}
/** The engine's own score beside the coach's opinion, so the two can be compared at a glance. */
const tally=()=>lastA?`<aside class="tally say" style="animation-delay:.1s"><small>${tr('dr.coach.engineEdge','Engine edge')}</small><div class="pair"><span class="b">${lastA.blueEdge}</span><span class="r">${lastA.redEdge}</span></div>
  <div class="meter"><i style="width:${lastA.blueEdge}%"></i></div><div class="names"><span>${esc(teamName('blue'))}</span><span>${esc(teamName('red'))}</span></div><p>${esc(trEn(lastA.headline))}</p></aside>`:'';
/** Splits off the first sentence so each read opens with one line you can scan. */
const firstSentence=t=>{const m=String(t||'').match(/^(.+?[.!?])\s+(\S[\s\S]*)$/);return m?[m[1],m[2]]:[String(t||''),'']};
const teamName=t=>(document.querySelector(`.tname[data-team=${t}]`)||{}).value||cap(t);

/* ---------- actions ---------- */
function refresh(fresh){drawRail();drawSides(fresh);drawGrid();drawNext();drawAnalysis();drawCoach();restartClock();
 for(const t of['blue','red']){state[t].picks.forEach(p=>seen.add(p.name));state[t].bans.forEach(n=>seen.add(n))}
 if(fly)flyIn();}
function adopt(j){
 const before=JSON.stringify([state.blue,state.red]);
 if(j.state)state=j.state;else if(!j.error)state=j;
 if(!state.blue.picks.length&&!state.red.picks.length&&!state.blue.bans.length&&!state.red.bans.length)skipped.clear();
 if(j.error)toast(j.error,'var(--ember)');
 if(JSON.stringify([state.blue,state.red])!==before)refresh(true);else fly=null;
 const used=usedMap();[...seen].forEach(n=>{if(!used[n])seen.delete(n)});
}
// One board change at a time: a double-click would otherwise send the same version twice and get a 409.
let busy=false;
async function act(path,side,name,extra=''){
 if(busy)return;busy=true;
 try{
  const j=await call(`/api/${path}?side=${side}&hero=${encodeURIComponent(name)}&v=${state.version}${extra}`,{post:true});
  if(!j.error&&(path==='pick'||path==='ban'))undoStack.push({side,name});
  $('#undo').disabled=!undoStack.length;adopt(j);
 }finally{busy=false}
}
function choose(name,el){
 const a=acting();
 const img=el&&el.querySelector('img');
 if(img&&!matchMedia('(prefers-reduced-motion: reduce)').matches)fly={name,rect:img.getBoundingClientRect(),src:img.src};
 act(a.kind,a.side,name);
}
/** The clicked portrait flies from the pool into its new slot. */
function flyIn(){
 const f=fly;fly=null;
 const target=document.querySelector(`.door[data-hero="${CSS.escape(f.name)}"] .face, .door[data-hero="${CSS.escape(f.name)}"] video, .seal[data-hero="${CSS.escape(f.name)}"] img`);
 if(!target)return;
 const to=target.getBoundingClientRect(),ghost=Object.assign(document.createElement('img'),{src:f.src,className:'fly'});
 Object.assign(ghost.style,{left:f.rect.left+'px',top:f.rect.top+'px',width:f.rect.width+'px',height:f.rect.height+'px'});
 document.body.append(ghost);target.style.opacity=0;
 const dx=to.left-f.rect.left,dy=to.top-f.rect.top,s=Math.min(to.width,to.height)/f.rect.width;
 ghost.animate([{transform:'none',opacity:1},{transform:`translate(${dx*.5}px,${dy*.5-60}px) scale(${(1+s)/2})`,opacity:1,offset:.55},
  {transform:`translate(${dx+(to.width-f.rect.width*s)/2}px,${dy+(to.height-f.rect.height*s)/2}px) scale(${s})`,opacity:.9}],
  {duration:620,easing:'cubic-bezier(.5,0,.2,1)'}).onfinish=()=>{ghost.remove();target.style.opacity=''};
}

document.addEventListener('click',e=>{
 const t=e.target;if(t.closest('select'))return;
 const rm=t.closest('[data-rm]'),tile=t.closest('.tile'),sg=t.closest('[data-sg]');
 if(rm){if(!busy){act('remove',rm.dataset.t,rm.dataset.hero);undoStack=undoStack.filter(u=>u.name!==rm.dataset.hero)}return}
 if(tile){if(!tile.classList.contains('used'))choose(tile.dataset.h,tile);return}
 if(sg){const pt=document.querySelector(`.tile[data-h="${CSS.escape(sg.dataset.sg)}"]`);choose(sg.dataset.sg,pt||sg);return}
 if(t.id==='ask')askCoach(false);else if(t.id==='reread'){coachKey='';askCoach(true)}
 else if(t.id==='clearAll'){$('#q').value='';role='';lane='';drawFilters();drawGrid()}
});
document.addEventListener('change',e=>{const s=e.target.closest('[data-lane]');if(s)act('lane',s.dataset.t,s.dataset.hero,'&lane='+encodeURIComponent(s.value))});
document.addEventListener('input',e=>{const n=e.target.closest('.tname');if(n)store.set('jongrai-name-'+n.dataset.team,n.value)});
document.addEventListener('pointerover',e=>{const t=e.target.closest('.tile:not(.used)');if(t)lens(byName[t.dataset.h])});
document.addEventListener('focusin',e=>{const t=e.target.closest('.tile:not(.used)');if(t)lens(byName[t.dataset.h])});
$('#grid').addEventListener('pointerleave',()=>lens(null));
$('#bansOn').setAttribute('aria-checked',bansOn);
$('#bansOn').onclick=()=>{bansOn=!bansOn;skipped.clear();store.set('jongrai-bans',bansOn?'on':'off');$('#bansOn').setAttribute('aria-checked',bansOn);
 if(!bansOn)manual.kind='pick';refresh(false);
 toast(bansOn?tr('dr.toast.bansOn','Bans on: tournament order, 20 steps.'):tr('dr.toast.bansOff','Bans off: picks only, 10 steps in snake order.'))};
$('#follow').onclick=()=>{const now=acting();follow=!follow;$('#follow').setAttribute('aria-checked',follow);if(!follow)manual=now;refresh(false)};

/* ---------- map ---------- */
// The chosen arena nudges the engine by its mechanic (data/map-effects.csv) and by how heroes do on it in recorded pro games.
let mapName=store.get('jongrai-map')||'',mapList=[];
const mapQ=()=>mapName?'&map='+encodeURIComponent(mapName):'';
function drawMapChoice(){
 const m=mapList.find(x=>x.name===mapName);
 $('#mapChoice').innerHTML=`<option value="">${tr('dr.map.any','Map: any')}</option>`+mapList.map(x=>`<option value="${esc(x.name)}"${x.name===mapName?' selected':''}>${esc(x.name)} · ${tr('dr.map.games','{n} pro games',{n:x.games})}</option>`).join('');
 $('#mapIc').hidden=!m;if(m)$('#mapIc').src='/icons/'+encodeURIComponent(m.icon);
}
$('#mapChoice').onchange=e=>{mapName=e.target.value;store.set('jongrai-map',mapName);drawMapChoice();coachKey='';drawNext();drawCoach();
 toast(mapName?tr('dr.toast.map','Map: {map}. Suggestions now weigh its mechanic and how heroes do on it in pro games.',{map:mapName}):tr('dr.toast.mapAny','Map: any. Suggestions ignore the map.'))};

/* ---------- timer ---------- */
// Off, or 50 seconds per pick/ban step like the in-game draft. It restarts whenever the board moves to a new step.
// When it runs out, the step is left blank and the turn passes on.
let timerLen=+(store.get('jongrai-timer')||0),clockEnd=0,clockStep=-1,clockT=null,clockWarned=false;
function restartClock(force){
 const i=currentStep(),done=i>=order().length;
 $('#clock').hidden=!timerLen||done;
 if(!timerLen||done){clearInterval(clockT);clockT=null;clockStep=-1;return}
 if(!force&&i===clockStep&&clockT)return;
 clockStep=i;clockEnd=Date.now()+timerLen*1000;clockWarned=false;
 clearInterval(clockT);clockT=setInterval(tickClock,200);tickClock();
}
function tickClock(){
 const left=Math.max(0,(clockEnd-Date.now())/1000),n=Math.ceil(left),el=$('#clock');
 $('#clockN').textContent=n;el.style.setProperty('--left',(left/timerLen).toFixed(3));
 el.classList.toggle('low',n<=10&&n>0);el.classList.toggle('out',n===0);
 el.setAttribute('aria-label',n?tr('dr.clock.left','{n} seconds left',{n}):tr('dr.clock.up','Time is up'));
 if(n===0&&!clockWarned){clockWarned=true;clearInterval(clockT);clockT=null;skipStep()}
}
/** Time ran out: the step stays blank (no hero) and the turn passes to the next step, like a missed ban in game. */
function skipStep(){
 const i=currentStep();if(i>=order().length)return;
 const a=acting();
 if(!follow){toast(tr('dr.toast.timeUp','Time\'s up for {side} {kind}.',{side:sideTxt(a.side),kind:kindTxt(a.kind)}),'var(--ember)');return}
 skipped.add(i);toast(tr('dr.toast.skipped','Time\'s up: {side}\'s {kind} left blank.',{side:sideTxt(a.side),kind:kindTxt(a.kind)}),'var(--ember)');refresh(false);
}
$('#timerChoice').value=String(timerLen);
$('#timerChoice').onchange=e=>{timerLen=+e.target.value;store.set('jongrai-timer',String(timerLen));restartClock(true)};
function freeMode(patch){if(follow){manual=acting();follow=false;$('#follow').setAttribute('aria-checked',false);toast(tr('dr.toast.free','Free mode: you choose the side and action. Turn “Follow draft order” back on any time.'))}
 Object.assign(manual,patch);drawRail();drawSides(false);drawGrid();drawNext()}
$('#sideSeg').onclick=e=>{const b=e.target.closest('button');if(b)freeMode({side:b.dataset.s})};
$('#kindSeg').onclick=e=>{const b=e.target.closest('button');if(b)freeMode({kind:b.dataset.k})};
$('#undo').onclick=()=>{if(busy)return;const u=undoStack.pop();$('#undo').disabled=!undoStack.length;if(u)act('remove',u.side,u.name)};
// Reset needs a 0.7 s hold (or two presses from the keyboard), so a stray click never wipes a finished draft.
let holdT,armed=false;
const doReset=async()=>{if(busy)return;busy=true;
 try{undoStack=[];skipped.clear();$('#undo').disabled=true;adopt(await call('/api/reset?v='+state.version,{post:true}));toast(tr('dr.toast.cleared','Board cleared'))}finally{busy=false}};
$('#reset').onpointerdown=()=>{$('#reset').classList.add('holding');holdT=setTimeout(()=>{$('#reset').classList.remove('holding');doReset()},700)};
['pointerup','pointerleave'].forEach(ev=>$('#reset').addEventListener(ev,()=>{clearTimeout(holdT);$('#reset').classList.remove('holding')}));
$('#reset').onkeydown=e=>{if(e.key!=='Enter'&&e.key!==' ')return;e.preventDefault();
 if(armed){armed=false;$('#resetLbl').textContent=tr('dr.reset','Hold to reset');doReset()}else{armed=true;$('#resetLbl').textContent=tr('dr.resetAgain','Press again to reset');setTimeout(()=>{armed=false;$('#resetLbl').textContent=tr('dr.reset','Hold to reset')},2500)}};
document.addEventListener('keydown',e=>{
 const typing=/INPUT|SELECT|TEXTAREA/.test(document.activeElement.tagName);
 if(e.key==='/'&&!typing){e.preventDefault();$('#q').focus();$('#q').select()}
 else if((e.ctrlKey||e.metaKey)&&e.key==='z'&&!typing){e.preventDefault();$('#undo').click()}
 else if(document.activeElement===$('#q')&&e.key==='Enter'){const t=document.querySelector('.tile:not(.used)');if(t)choose(t.dataset.h,t)}
 else if(document.activeElement===$('#q')&&e.key==='Escape'){$('#q').value='';drawGrid()}
});
// Another window may have changed the board; catch up when this one comes back.
addEventListener('focus',async()=>{const s=await call('/api/state');if(!s.error&&s.version!==state.version)adopt(s)});
function drawFilters(){
 $('#roles').innerHTML=Object.entries(ROLE_ICON).map(([r,f])=>`<button class="chip" data-role="${r}" aria-pressed="${role===r}"><img src="${icon(f)}" alt="">${cap(r.toLowerCase())}</button>`).join('');
 $('#lanes').innerHTML=LANES.map(l=>`<button class="chip" data-lane-f="${l}" aria-pressed="${lane===l}"><img src="/icons/${laneIcon(l)}" alt="">${cap(l.toLowerCase())}</button>`).join('')+'<span class="count num" id="count"></span>'+SORT().replace(`value="${sortBy}"`,`value="${sortBy}" selected`);
}
$('#roles').onclick=e=>{const b=e.target.closest('[data-role]');if(!b)return;role=role===b.dataset.role?'':b.dataset.role;drawFilters();drawGrid()};
$('#lanes').onclick=e=>{const b=e.target.closest('[data-lane-f]');if(!b)return;lane=lane===b.dataset.laneF?'':b.dataset.laneF;drawFilters();drawGrid()};
$('#q').oninput=drawGrid;document.addEventListener('change',e=>{if(e.target.id==='sort'){sortBy=e.target.value;drawGrid()}});
$('#ticks').innerHTML=Array.from({length:40},(_,i)=>{const g=i*9*Math.PI/180,r1=i%10?96:93;return`<line x1="${100+Math.sin(g)*r1}" y1="${100-Math.cos(g)*r1}" x2="${100+Math.sin(g)*99}" y2="${100-Math.cos(g)*99}"/>`}).join('');

(async()=>{
 await i18nReady();shell('/draft');
 const hs=await call('/api/heroes');if(hs.error){toast(hs.error,'var(--ember)');return}
 heroes=await localizeTips(hs);heroes.forEach(h=>byName[h.name]=h);
 drawFilters();
 const ms=await call('/api/maps');if(Array.isArray(ms)){mapList=ms;FX_ID={};ms.forEach(x=>(x.effects||[]).forEach(e=>FX_ID[e.reason]=e.id));if(mapName&&!ms.some(m=>m.name===mapName))mapName='';drawMapChoice()}
 else{mapName='';$('#mapChoice').innerHTML=`<option value="">${tr('dr.map.unavailable','Map list unavailable: rebuild and restart the server')}</option>`;$('#mapChoice').disabled=true}
 const s=await call('/api/state');if(s.error){toast(s.error,'var(--ember)');return}
 state=s;refresh(false);
})();
// Background music: nothing is loaded from YouTube until the user picks a track or presses the music button.
const musicTracks={music1:'KheFsJvAvsU',music2:'TxTprtLZurY',music3:'kgpqMfTZDTw',music4:'GJl_fn_skOM'};
const MUSIC_ORIGIN='https://www.youtube-nocookie.com';
let musicMuted=true;
function musicCommand(func){const frame=$('#draftMusic');if(frame.hasAttribute('src'))frame.contentWindow.postMessage(JSON.stringify({event:'command',func,args:[]}),MUSIC_ORIGIN)}
function loadMusic(muted){const id=musicTracks[$('#musicChoice').value],frame=$('#draftMusic');if(!id||frame.hasAttribute('src'))return;
 frame.src=`${MUSIC_ORIGIN}/embed/${id}?autoplay=1&mute=${muted?1:0}&loop=1&playlist=${id}&controls=0&enablejsapi=1&playsinline=1&rel=0`}
function musicLabel(){const b=$('#musicToggle');b.textContent=musicMuted?tr('dr.music.muted','Muted'):tr('dr.music.playing','Playing');b.setAttribute('aria-pressed',String(!musicMuted))}
function setMusic(choice){const id=musicTracks[choice],frame=$('#draftMusic');musicMuted=true;musicLabel();$('#musicToggle').disabled=!id;
 musicCommand('stopVideo');frame.removeAttribute('src');store.set('jongrai-draft-music',choice);loadMusic(true)}
$('#musicChoice').onchange=e=>setMusic(e.target.value);
$('#musicToggle').onclick=()=>{
 if(!$('#draftMusic').hasAttribute('src')){musicMuted=false;loadMusic(false);musicLabel();return}
 musicMuted=!musicMuted;musicCommand(musicMuted?'mute':'unMute');if(!musicMuted)musicCommand('playVideo');musicLabel()};
// Restore the saved track in the menu only; the player itself waits for a click.
{const saved=store.get('jongrai-draft-music');if(saved&&musicTracks[saved]){$('#musicChoice').value=saved;$('#musicToggle').disabled=false}}
