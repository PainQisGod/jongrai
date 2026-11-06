// Jongrai - script for /draft.html. Runs after /app.js and /kit.js.
const TOURNAMENT=[...'BRBRBR'].map(s=>['ban',s,0]).concat([...'BRRBBR'].map(s=>['pick',s,1]),[...'RBRB'].map(s=>['ban',s,2]),[...'RBBR'].map(s=>['pick',s,3]));
// With bans off the board is the engine's own 10-pick snake, like the original page.
const PICKS_ONLY=[...'BRRBBR'].map(s=>['pick',s,1]).concat([...'RBBR'].map(s=>['pick',s,3]));
let bansOn=store.get('jongrai-bans')!=='off';
const order=()=>bansOn?TOURNAMENT:PICKS_ONLY;
const PHASES=['Ban phase 1','Pick phase 1','Ban phase 2','Pick phase 2'];
const SIDE={B:'blue',R:'red'},LANES=['EXP','JUNGLE','MID','GOLD','ROAM'];
const ROLE_ICON={TANK:'Emblem_Tank.png',FIGHTER:'Emblem_Fighter.png',ASSASSIN:'Emblem_Assassin.png',MAGE:'Emblem_Mage.png',MARKSMAN:'Emblem_Marksman.png',SUPPORT:'Emblem_Support.png'};
const TIER_ORDER='SABCD';
const SORT='<select class="sortsel" id="sort" aria-label="Sort heroes"><option value="tier">Sort: tier</option><option value="win">Sort: win rate</option><option value="ban">Sort: ban rate</option><option value="name">Sort: A–Z</option></select>';
let heroes=[],byName={},state={version:0,blue:{picks:[],bans:[]},red:{picks:[],bans:[]}};
let sortBy='tier',follow=true,manual={side:'blue',kind:'pick'},role='',lane='',undoStack=[],recs=[],fly=null,seen=new Set();
const other=s=>s==='blue'?'red':'blue';
const cap=s=>s[0].toUpperCase()+s.slice(1);
const pickNames=s=>state[s].picks.map(p=>p.name);
const key=n=>n.toLowerCase();

/** The first step of the 20-step order a side has not filled yet. Works even when the board was filled out of order. */
function currentStep(){
 const need={blue:{pick:0,ban:0},red:{pick:0,ban:0}};
 for(let i=0;i<order().length;i++){const[k,s]=order()[i],side=SIDE[s];need[side][k]++;
  if(state[side][k+'s'].length<need[side][k])return i}
 return order().length;
}
function acting(){const i=currentStep();if(!follow||i>=order().length)return manual;const[k,s]=order()[i];return{side:SIDE[s],kind:k}}

/* ---------- rail ---------- */
function drawRail(){
 const i=currentStep(),a=acting(),done=i>=order().length;
 const need={blue:{pick:0,ban:0},red:{pick:0,ban:0}};
 $('#notches').innerHTML=order().map(([k,s,ph],n)=>{const side=SIDE[s],idx=need[side][k]++,list=state[side][k+'s'],hit=list[idx];
  const name=hit?(hit.name||hit):'';
  return(n&&ph!==order()[n-1][2]?'<li class="gap" aria-hidden="true"></li>':'')
   +`<li class="${k} ${s}${hit?' done':''}${n===i?' now':''}" title="${n+1}. ${cap(side)} ${k}${name?': '+esc(name):''}"></li>`}).join('');
 $('#phase').textContent=done?'Draft complete':PHASES[order()[i][2]];
 $('#turnLine').innerHTML=done?'Both sides <b>locked</b>':`<b class="${a.side}">${cap(a.side)}</b> ${a.kind}s${follow?'':' <small style="font-size:12px;color:var(--ink-3);letter-spacing:.1em">· FREE MODE</small>'}`;
 const total=order().length;$('#stepNo').textContent=done?`${total} / ${total}`:`STEP ${String(i+1).padStart(2,'0')} / ${total}`;
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
 if(!p)return[`${code}|${next}`,`<div class="door empty${next?' next':''}" data-slot="${code}"><span class="code">${code}</span>${next?'<span class="cue">Picking</span>':''}</div>`];
 const h=byName[p.name]||{lanes:[]},v=h.video,li=laneIcon(p.lane); // pick animation from web/video, listed by the server
 const opts=[...h.lanes,...LANES.filter(l=>!h.lanes.includes(l))].map(l=>`<option value="${l}"${l===p.lane?' selected':''}>${l}${h.lanes.includes(l)?'':' · flex'}</option>`).join('');
 const art=v?`<video src="/video/${encodeURIComponent(v)}" autoplay muted loop playsinline preload="metadata" aria-hidden="true"></video>`
  :`<img class="aura" src="${face(p.name)}" alt=""><img class="face" src="${face(p.name)}" alt="">`;
 return[`${p.name}|${p.lane}`,`<div class="door filled" data-hero="${esc(p.name)}">${art}
  <span class="code small">${code}</span>${h.tier?`<span class="tier ${esc(h.tier)}">${esc(h.tier)}</span>`:''}<span class="flag"></span>
  <div class="plate"><b class="nm">${esc(p.name)}</b><label class="lanesel">${li?`<img src="/icons/${li}" alt="">`:''}<span class="sr">Lane for ${esc(p.name)}</span>
  <select data-lane data-t="${t}" data-hero="${esc(p.name)}">${opts}</select></label></div>
  <button class="x" data-rm data-t="${t}" data-hero="${esc(p.name)}" aria-label="Remove ${esc(p.name)}">✕</button></div>`];
}
function seal(t,n,i,next){
 if(!n)return[`|${next}`,`<div class="seal empty${next?' next':''}">${i+1}</div>`];
 return[n,`<div class="seal" data-hero="${esc(n)}" title="${esc(n)} banned"><img src="${face(n)}" alt="${esc(n)} (banned)">
  <svg viewBox="0 0 56 56" aria-hidden="true"><path d="M12 44 Q 26 30 45 11"/></svg>
  <button class="x" data-rm data-t="${t}" data-hero="${esc(n)}" aria-label="Lift ban on ${esc(n)}">✕</button></div>`];
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
   const saved=store.get('jongrai-name-'+t)||(t==='blue'?'Blue side':'Red side');
   el.innerHTML=`<div class="side-head"><input class="tname" data-team="${t}" value="${esc(saved)}" aria-label="${cap(t)} team name" spellcheck="false"><div class="pips" data-pips></div></div>
    <div class="doors"></div><div class="seals"></div><div class="bans-lbl"><span>BANS</span><span class="num" data-bc></span></div>`;
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
  $('#side-'+t+' [data-pips]').innerHTML=LANES.map(l=>`<img src="/icons/${laneIcon(l)}" alt="${l}${miss.includes(l)?' open':' covered'}" title="${l}: ${miss.includes(l)?'open':'covered'}" class="${miss.includes(l)?'':'on'}">`).join('');
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
 $('#count').textContent=`${list.filter(h=>!used[h.name]).length} available · ${list.length} shown`;
 $('#grid').innerHTML=list.length?list.map(h=>{const u=used[h.name],r=relations(h,a.side),rank=recs.indexOf(h.name);
  const wr=Math.max(0,Math.min(100,(h.winRate-40)*5));
  return`<button class="tile${u?' used':''}${rank>=0&&!u?' rec':''}" data-h="${esc(h.name)}"${u?' aria-disabled="true"':''}
   aria-label="${esc(h.name)}, tier ${esc(h.tier)}, ${h.winRate.toFixed(1)}% win${u?', '+(u==='X'?'banned':'picked'):''}">
   <span class="hint" aria-hidden="true">${a.kind.toUpperCase()}</span>
   ${rank>=0&&!u?`<span class="rank num" title="Engine pick #${rank+1}">#${rank+1}</span>`:''}
   ${!u&&(r.strong.length||r.weak.length)?`<span class="marks">${r.strong.length?`<b class="up" title="Strong against ${esc(r.strong.join(', '))}">▲${r.strong.length}</b>`:''}${r.weak.length?`<b class="dn" title="Weak against ${esc(r.weak.join(', '))}">▼${r.weak.length}</b>`:''}</span>`:''}
   <span class="pt"><img src="${icon(h.icon)}" alt=""><span class="tier ${esc(h.tier)}">${esc(h.tier)}</span></span>
   ${u?`<span class="tag ${u[0]}">${u==='X'?'BAN':u}</span>`:''}
   <span class="tn">${hl(h.name)}</span><span class="wr ${h.winRate>=51?'up':h.winRate<49?'down':''}"><i style="width:${wr}%"></i></span></button>`}).join('')
  :`<div class="empty-pool"><b>No hero matches “${esc($('#q').value)}”</b>Try part of the name, or clear the role and lane filters.<br><br><button class="chip" id="clearAll">Clear filters</button></div>`;
 lens(null);
}
function lens(h){
 const keys=`<span class="keys"><span><kbd>/</kbd> search</span><span><kbd>↵</kbd> ${acting().kind} first</span><span><kbd>Ctrl</kbd><kbd>Z</kbd> undo</span></span>`;
 document.querySelectorAll('.door[class*=rel-]').forEach(d=>d.classList.remove('rel-strong','rel-weak','rel-combo'));
 if(!h){$('#lens').innerHTML=`<span>Hover a hero to see how it fits this board.</span>${keys}`;return}
 const a=acting(),r=relations(h,a.side);
 const mark=(names,cls,label)=>names.forEach(n=>{const d=document.querySelector(`.door[data-hero="${CSS.escape(n)}"]`);if(d){d.classList.add(cls);d.querySelector('.flag').textContent=label}});
 mark(r.strong,'rel-strong','STRONG VS');mark(r.weak,'rel-weak','THREAT');mark(r.combo,'rel-combo','COMBO');
 const rel=(names,c,l)=>names.length?`<span class="rel"><i style="background:${c}"></i>${l} ${esc(names.join(', '))}</span>`:'';
 $('#lens').innerHTML=`<img src="${icon(h.icon)}" alt=""><div><b>${esc(h.name)}</b> · ${esc(h.roles.join(' / ').toLowerCase())} · ${esc(h.lanes.join(' / ').toLowerCase())} · <span class="num">${h.winRate.toFixed(2)}%</span> win${h.banRate?` · <span class="num">${h.banRate.toFixed(1)}%</span> ban`:''}<br>
  ${rel(r.strong,'var(--jade)','strong vs')}${rel(r.weak,'var(--ember)','weak vs')}${rel(r.combo,'var(--gold)','combos with')}${!r.strong.length&&!r.weak.length&&!r.combo.length?`<span style="color:var(--ink-3)">${esc(h.tip||'No direct relation to the heroes on the board yet.')}</span>`:''}</div>${keys}`;
}

/* ---------- council ---------- */
async function drawNext(){
 const a=acting(),done=currentStep()>=order().length&&follow;
 if(done){recs=[];$('#next').innerHTML=`<h2>Next move <small>draft complete</small></h2><p style="margin:0;color:var(--ink-2)">All twenty steps are in. Ask Jongrai for the read below, or tap a slot to change it.</p>`;return}
 const list=await call(`/api/${a.kind==='pick'?'suggest':'bans'}?side=${a.side}`);
 if(list.error||!Array.isArray(list)){$('#next').innerHTML=`<h2>Next move</h2><p style="margin:0;color:var(--ember)">${esc(list.error||'No suggestions')}</p>`;return}
 recs=list.map(s=>s.hero);
 const [top,...rest]=list,tag=`<small>${cap(a.side)} ${a.kind} · engine ranked</small>`;
 $('#next').innerHTML=top?`<h2>${a.kind==='pick'?'Best pick':'Best ban'} ${tag}</h2>
  <button class="feature" data-sg="${esc(top.hero)}"><img src="${face(top.hero)}" alt=""><span><b>${esc(top.hero)}</b><span class="reasons">${top.reasons.map(r=>`<span>${esc(r)}</span>`).join('')}</span></span><span class="score-pill num">${esc(top.score)}</span></button>
  <ul class="alts">${rest.map(s=>`<li><button data-sg="${esc(s.hero)}"><img src="${face(s.hero)}" alt=""><span><b>${esc(s.hero)}</b><small>${esc(s.reasons.join(' · ')||'—')}</small></span><span class="num">${esc(s.score)}</span></button></li>`).join('')}</ul>`
  :`<h2>Next move ${tag}</h2><p style="margin:0;color:var(--ink-2)">Nothing left to suggest for this step.</p>`;
 drawGrid();
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
 $('#lead').textContent=a.lead==='even'?'EVEN':a.lead.toUpperCase()+' EDGE';$('#lead').className='lead '+a.lead;
 $('#headline').textContent=a.headline;
 const locked=state.blue.picks.length===5&&state.red.picks.length===5;
 $('#scale').classList.toggle('locked',locked);
 $('#edge').innerHTML=`<h2>Edge breakdown <small>blue share per category</small></h2>`+a.categories.map(c=>{const s=Math.round(c.blueShare);
  return`<div class="tug"><div class="tug-h"><span class="num b">${s}</span><span>${esc(c.name)}</span><span class="num r">${100-s}</span></div>
  <div class="tug-bar" role="img" aria-label="${esc(c.name)}: blue ${s}, red ${100-s}"><i style="width:${c.blueShare}%"></i><em style="left:${c.blueShare}%"></em></div><small>${esc(c.detail)}</small></div>`}).join('');
 const n=a.profile.length;
 const rings=[25,50,75].map(p=>`<circle cx="100" cy="100" r="${14+p*.7}" fill="none" stroke="var(--hair)" stroke-dasharray="2 3"/>`).join('');
 const labels=a.profile.map((x,i)=>{const g=(-90+i*360/n)*Math.PI/180;return`<text x="${(100+Math.cos(g)*96).toFixed(1)}" y="${(103+Math.sin(g)*96).toFixed(1)}" text-anchor="middle">${esc(x.name)}</text>`}).join('');
 const petals=(side,col,shift)=>a.profile.map((x,i)=>`<path class="petal" d="${petal(i,n,x[side],shift)}" fill="${col}" fill-opacity=".28" stroke="${col}" stroke-width="1.2"><title>${esc(x.name)} · ${cap(side)} ${x[side]}</title></path>`).join('');
 $('#profile').innerHTML=`<h2>Lotus profile <small>nine axes, 0–10</small></h2><div class="lotus"><svg viewBox="-38 -8 276 216" role="img" aria-label="Tactical profile, blue against red">${rings}${petals('blue','#4d8dff',-7)}${petals('red','#ff5062',7)}<circle cx="100" cy="100" r="5" fill="var(--gold)"/>${labels}</svg>
  <div class="legend"><div class="row"><span></span><b style="color:var(--blue)">BLU</b><b style="color:var(--red)">RED</b></div>${a.profile.map(x=>`<div class="row"><span>${esc(x.name)}</span><b class="num">${x.blue}</b><b class="num">${x.red}</b></div>`).join('')}</div></div>`;
 const B=a.coordination.blue,R=a.coordination.red,yn=v=>v?'<span class="yes">✓ yes</span>':'<span class="no">✕ no</span>';
 const row=(k,bv,rv)=>`<span class="v">${bv}</span><span class="k">${k}</span><span class="v r">${rv}</span>`;
 $('#coord').innerHTML=`<h2>Coordination <small><span style="color:var(--blue)">${Math.round(B.score)}</span> / <span style="color:var(--red)">${Math.round(R.score)}</span> of 100</small></h2><div class="coord">
  ${row('Lanes',B.lanesCovered+' / 5',R.lanesCovered+' / 5')}${row('Frontline',yn(B.hasTank),yn(R.hasTank))}${row('Carry',yn(B.hasMarksmanOrMage),yn(R.hasMarksmanOrMage))}
  ${row('Synergies',B.synergyPairs,R.synergyPairs)}${row('Lockdown',B.heavyControl,R.heavyControl)}${row('Spike',B.spikeTiming.toLowerCase(),R.spikeTiming.toLowerCase())}
  <div class="needs"><div>${B.missingLanes.length?`needs <span>${esc(B.missingLanes.join(', ').toLowerCase())}</span>`:'<span class="yes">every lane covered</span>'}</div><div style="text-align:right">${R.missingLanes.length?`needs <span>${esc(R.missingLanes.join(', ').toLowerCase())}</span>`:'<span class="yes">every lane covered</span>'}</div></div></div>`;
}
let coachKey='',lastA=null;
function drawCoach(){
 const b=state.blue.picks.length,r=state.red.picks.length,sig=JSON.stringify([state.blue,state.red]);
 if(coachKey&&coachKey!==sig)coachKey='';
 if(coachKey)return;
 const ready=b===5&&r===5;
 $('#coach').innerHTML=`<div class="coach-h"><h2>Jongrai's wisdom</h2><small>AI coach · patch <span data-patch></span></small>
  <p>${ready?'Ten heroes are locked. Jongrai can read the whole draft now.':`Reads the board once all ten picks are in: <span class="num" style="color:var(--ink)">${b} : ${r}</span> so far.`}</p>
  <button class="ask${ready?'':' quiet'}" id="ask">${ready?'Ask Jongrai':'Read it early'}</button></div>`;
 fillMeta();
}
async function askCoach(refresh){
 const sig=JSON.stringify([state.blue,state.red]);coachKey=sig;
 $('#coach').innerHTML=`<div class="coach-h"><h2>Jongrai's wisdom</h2><p>Reading the draft<span class="ink"><i></i><i></i><i></i></span></p></div>`;
 const done=state.blue.picks.length===5&&state.red.picks.length===5;
 const w=await call('/api/wisdom?'+(done?'':'force=1&')+(refresh?'refresh=1':''),{post:true});
 if(coachKey!==sig)return;
 if(w.error){$('#coach').innerHTML=`<div class="coach-h"><h2>Jongrai's wisdom</h2><small>coach off</small><p style="color:var(--ember)">${esc(w.error)}</p></div>`;return}
 if(!w.ready){coachKey='';drawCoach();return}
 const [lead,rest]=firstSentence(w.verdict);
 const read=(t,txt,i)=>{if(!txt)return'';const[a,b]=firstSentence(txt);
  return`<article class="read ${t} say" style="animation-delay:${.2+i*.15}s"><h3>${esc(teamName(t))}</h3><p><b>${esc(a)}</b>${esc(b)}</p></article>`};
 $('#coach').innerHTML=`<div class="coach-h"><h2>Jongrai's wisdom</h2><small>verdict · patch <span data-patch></span></small><button class="ask quiet" id="reread">Re-read the draft</button></div>
  <div class="top-read"><div class="verdict say"><p class="lead">${esc(lead)}</p>${rest?`<p class="rest">${esc(rest)}</p>`:''}</div>${tally()}</div>
  <div class="reads">${read('blue',w.blue,0)}${read('red',w.red,1)}</div>
  ${w.closing?`<div class="watch say" style="animation-delay:.55s"><span>Key to watch</span><p>${esc(w.closing)}</p></div>`:''}`;
 fillMeta();
}
/** The engine's own score beside the coach's opinion, so the two can be compared at a glance. */
const tally=()=>lastA?`<aside class="tally say" style="animation-delay:.1s"><small>Engine edge</small><div class="pair"><span class="b">${lastA.blueEdge}</span><span class="r">${lastA.redEdge}</span></div>
  <div class="meter"><i style="width:${lastA.blueEdge}%"></i></div><div class="names"><span>${esc(teamName('blue'))}</span><span>${esc(teamName('red'))}</span></div><p>${esc(lastA.headline)}</p></aside>`:'';
/** Splits off the first sentence so each read opens with one line you can scan. */
const firstSentence=t=>{const m=String(t||'').match(/^(.+?[.!?])\s+(\S[\s\S]*)$/);return m?[m[1],m[2]]:[String(t||''),'']};
const teamName=t=>(document.querySelector(`.tname[data-team=${t}]`)||{}).value||cap(t);

/* ---------- actions ---------- */
function refresh(fresh){drawRail();drawSides(fresh);drawGrid();drawNext();drawAnalysis();drawCoach();
 for(const t of['blue','red']){state[t].picks.forEach(p=>seen.add(p.name));state[t].bans.forEach(n=>seen.add(n))}
 if(fly)flyIn();}
function adopt(j){
 const before=JSON.stringify([state.blue,state.red]);
 if(j.state)state=j.state;else if(!j.error)state=j;
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
$('#bansOn').onclick=()=>{bansOn=!bansOn;store.set('jongrai-bans',bansOn?'on':'off');$('#bansOn').setAttribute('aria-checked',bansOn);
 if(!bansOn)manual.kind='pick';refresh(false);
 toast(bansOn?'Bans on: tournament order, 20 steps.':'Bans off: picks only, 10 steps in snake order.')};
$('#follow').onclick=()=>{const now=acting();follow=!follow;$('#follow').setAttribute('aria-checked',follow);if(!follow)manual=now;refresh(false)};
function freeMode(patch){if(follow){manual=acting();follow=false;$('#follow').setAttribute('aria-checked',false);toast('Free mode: you choose the side and action. Turn “Follow draft order” back on any time.')}
 Object.assign(manual,patch);drawRail();drawSides(false);drawGrid();drawNext()}
$('#sideSeg').onclick=e=>{const b=e.target.closest('button');if(b)freeMode({side:b.dataset.s})};
$('#kindSeg').onclick=e=>{const b=e.target.closest('button');if(b)freeMode({kind:b.dataset.k})};
$('#undo').onclick=()=>{if(busy)return;const u=undoStack.pop();$('#undo').disabled=!undoStack.length;if(u)act('remove',u.side,u.name)};
// Reset needs a 0.7 s hold (or two presses from the keyboard), so a stray click never wipes a finished draft.
let holdT,armed=false;
const doReset=async()=>{if(busy)return;busy=true;
 try{undoStack=[];$('#undo').disabled=true;adopt(await call('/api/reset?v='+state.version,{post:true}));toast('Board cleared')}finally{busy=false}};
$('#reset').onpointerdown=()=>{$('#reset').classList.add('holding');holdT=setTimeout(()=>{$('#reset').classList.remove('holding');doReset()},700)};
['pointerup','pointerleave'].forEach(ev=>$('#reset').addEventListener(ev,()=>{clearTimeout(holdT);$('#reset').classList.remove('holding')}));
$('#reset').onkeydown=e=>{if(e.key!=='Enter'&&e.key!==' ')return;e.preventDefault();
 if(armed){armed=false;$('#resetLbl').textContent='Hold to reset';doReset()}else{armed=true;$('#resetLbl').textContent='Press again to reset';setTimeout(()=>{armed=false;$('#resetLbl').textContent='Hold to reset'},2500)}};
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
 $('#lanes').innerHTML=LANES.map(l=>`<button class="chip" data-lane-f="${l}" aria-pressed="${lane===l}"><img src="/icons/${laneIcon(l)}" alt="">${cap(l.toLowerCase())}</button>`).join('')+'<span class="count num" id="count"></span>'+SORT.replace(`value="${sortBy}"`,`value="${sortBy}" selected`);
}
$('#roles').onclick=e=>{const b=e.target.closest('[data-role]');if(!b)return;role=role===b.dataset.role?'':b.dataset.role;drawFilters();drawGrid()};
$('#lanes').onclick=e=>{const b=e.target.closest('[data-lane-f]');if(!b)return;lane=lane===b.dataset.laneF?'':b.dataset.laneF;drawFilters();drawGrid()};
$('#q').oninput=drawGrid;document.addEventListener('change',e=>{if(e.target.id==='sort'){sortBy=e.target.value;drawGrid()}});
$('#ticks').innerHTML=Array.from({length:40},(_,i)=>{const g=i*9*Math.PI/180,r1=i%10?96:93;return`<line x1="${100+Math.sin(g)*r1}" y1="${100-Math.cos(g)*r1}" x2="${100+Math.sin(g)*99}" y2="${100-Math.cos(g)*99}"/>`}).join('');

(async()=>{
 shell('/draft');
 const hs=await call('/api/heroes');if(hs.error){toast(hs.error,'var(--ember)');return}
 heroes=hs;heroes.forEach(h=>byName[h.name]=h);
 drawFilters();
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
function musicLabel(){const b=$('#musicToggle');b.textContent=musicMuted?'Muted':'Playing';b.setAttribute('aria-pressed',String(!musicMuted))}
function setMusic(choice){const id=musicTracks[choice],frame=$('#draftMusic');musicMuted=true;musicLabel();$('#musicToggle').disabled=!id;
 musicCommand('stopVideo');frame.removeAttribute('src');store.set('jongrai-draft-music',choice);loadMusic(true)}
$('#musicChoice').onchange=e=>setMusic(e.target.value);
$('#musicToggle').onclick=()=>{
 if(!$('#draftMusic').hasAttribute('src')){musicMuted=false;loadMusic(false);musicLabel();return}
 musicMuted=!musicMuted;musicCommand(musicMuted?'mute':'unMute');if(!musicMuted)musicCommand('playVideo');musicLabel()};
// Restore the saved track in the menu only; the player itself waits for a click.
{const saved=store.get('jongrai-draft-music');if(saved&&musicTracks[saved]){$('#musicChoice').value=saved;$('#musicToggle').disabled=false}}
