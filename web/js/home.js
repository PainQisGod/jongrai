// Jongrai - production homepage. Keeps live draft, meta, news and hero-tip features.
let heroes=[],byName={};
const cap=s=>String(s||'').charAt(0).toUpperCase()+String(s||'').slice(1).toLowerCase();

async function drawBoard(){
 const [s,a]=await Promise.all([call('/api/state'),call('/api/analysis')]);
 const el=$('#board');
 if(s.error){el.innerHTML=`<h2>${t('home.board.title','Live board')}</h2><p style="color:var(--ember)">${esc(s.error)}</p>`;return}
 const bansOn=store.get('jongrai-bans')!=='off',total=bansOn?20:10;
 const n=s.blue.picks.length+s.red.picks.length+(bansOn?s.blue.bans.length+s.red.bans.length:0);
 const five=(side,list)=>`<div class="five ${side}">${Array.from({length:5},(_,i)=>{const p=list[i],h=p&&byName[p.name];return h?`<span class="f" title="${esc(h.name)}"><img src="${icon(h.icon)}" alt="${esc(h.name)}"></span>`:'<span></span>'}).join('')}</div>`;
 el.innerHTML=`<h2>${t('home.board.title','Live board')} <small><span class="live on"><i></i></span>${n?t('home.board.steps','{n} of {total} steps in',{n,total}):bansOn?t('home.board.waitBan','waiting for the first ban'):t('home.board.waitPick','waiting for the first pick')}</small></h2>
  <div class="vs">${five('blue',s.blue.picks)}<div class="edge"><span class="b">${a.blueEdge??50}</span><i>:</i><span class="r">${a.redEdge??50}</span><small>${t('home.board.edge','EDGE')}</small></div>${five('red',s.red.picks)}</div>
  <div class="meter"><i style="width:${a.blueEdge??50}%"></i></div>
  <p>${n?esc(a.headline||''):(bansOn?t('home.board.clearBan','The board is clear. Blue bans first, then the snake order takes over.'):t('home.board.clearPick','The board is clear. Blue picks first, then the snake order takes over.'))} ${n?`<span style="color:var(--ink-3)">· ${t('home.board.bans','{n} bans placed',{n:s.blue.bans.length+s.red.bans.length})}</span>`:''}</p>
  <a class="go" href="/draft">${n?t('home.board.continue','Continue this draft →'):t('home.board.start','Start drafting →')}</a>`;
}

function drawPulse(){
 const tops=[...heroes].sort((x,y)=>y.winRate-x.winRate).slice(0,3);
 $('#tops').innerHTML=tops.map((h,i)=>`<a class="top" href="/heroes/${esc(h.slug)}"><img class="aura" src="${icon(h.icon)}" alt=""><span class="rk num">0${i+1}</span>
  <img class="face" src="${icon(h.icon)}" alt=""><b>${esc(h.name)}</b><small>${esc(h.roles.map(cap).join(' / '))} · ${t('home.top.tier','tier')} ${esc(h.tier)}</small>
  <small style="margin-top:10px;color:var(--ink-3)">${esc(h.lanes.map(cap).join(' / '))} · ${esc(t('spike.'+h.spike.toLowerCase(),cap(h.spike)+' spike'))}</small><div class="wrn">${h.winRate.toFixed(2)}%<span>${t('home.top.win','WIN')}</span></div></a>`).join('');
 const banned=heroes.filter(h=>h.metaBan).sort((x,y)=>y.banRate-x.banRate).slice(0,7),max=banned[0]?banned[0].banRate:1;
 $('#bans').innerHTML=`<h3>${t('home.bans.title','Most banned')}</h3>`+banned.map((h,i)=>`<div class="brow"><img src="${icon(h.icon)}" alt=""><div class="bar"><i style="width:${h.banRate/max*100}%;animation-delay:${i*.07}s"></i><span>${esc(h.name)}</span></div><span class="num">${h.banRate.toFixed(1)}%</span></div>`).join('');
}

/** Rotating tip uses the real hero tips from heroes.csv and preserves the Khmer translation. */
function tips(){
 const pool=heroes.filter(h=>h.tip).map(h=>{const s=tipSections(h.tip);return{...h,tipPart:s[Math.floor(Math.random()*s.length)]}}).sort(()=>Math.random()-.5).slice(0,6);
 if(!pool.length){$('#tip').innerHTML='<p class="leader-empty">'+t('home.tips.none','Hero tips are not available right now.')+'</p>';return}
 let i=0,paused=false,t0=Date.now();const el=$('#tip');
 el.onmouseenter=()=>paused=true;el.onmouseleave=()=>{paused=false;t0=Date.now()};
 const show=()=>{const h=pool[i];el.classList.add('out');setTimeout(()=>{
  el.innerHTML=`<img class="face swap" src="${icon(h.icon)}" alt=""><div class="swap"><q>${esc(h.tipPart.text)}</q><cite>${esc(h.name)} · ${t('home.tip.by','Jongrai tip')}${h.tipPart.label?' · '+esc(h.tipPart.label):''}</cite><div class="dots">${pool.map((_,j)=>`<i class="${j===i?'on':''}"></i>`).join('')}</div></div>`;
  el.classList.remove('out')},el.innerHTML?300:0)};
 show();
 setInterval(()=>{if(paused||document.hidden||Date.now()-t0<7000)return;t0=Date.now();i=(i+1)%pool.length;show()},250);
}

/** The three newest real articles from /api/news; hidden when the newsroom has no articles. */
async function drawNews(){
 const list=await call('/api/news');
 if(!Array.isArray(list)||!list.length)return;
 const TY={patch:'Patch',skin:'Skin',event:'Event',esports:'Esports',news:'News'};
 const km=(a,en,k)=>LANG==='km'&&a[k]?a[k]:a[en];
 const left=a=>{if(!a.ends)return'';const end=new Date(a.ends+'T23:59:59'),d=Math.ceil((end-new Date())/864e5);
  return end<new Date()?'<span class="cd over">'+t('news.ended','Ended')+'</span>':`<span class="cd">${d<=1?t('news.endsToday','Ends today'):t('news.endsIn','Ends in {n} days',{n:d})}</span>`};
 $('#nws').className='nws n'+Math.min(list.length,3);
 $('#nws').innerHTML=list.slice(0,3).map(a=>`<a class="nw" href="/news/${encodeURIComponent(a.slug)}">
   <div class="cv">${a.image?`<img src="/news-media/${encodeURIComponent(a.image)}" alt="" loading="lazy">`:''}</div>
   <div class="tx"><div class="mt"><span class="ty ${esc(a.type)}">${esc(t('news.type.'+a.type,TY[a.type]||a.type))}</span><time>${esc(fmtDate(a.date))}</time>${left(a)}</div>
   <h3>${esc(km(a,'title','titleKm'))}</h3>${a.summary?`<p>${esc(km(a,'summary','summaryKm'))}</p>`:''}</div></a>`).join('');
 $('#news').hidden=false;
}

let homeLeague=null,homeTeams={},homeLeagueNames={},homeStandings=[],homeMatches=[],homeAllMatches=[],homeElo=[];
function teamData(id,leagueId){return homeTeams[String(leagueId||'')+'|'+String(id)]||{id,name:id,shortName:id,logo:''}}
function teamMark(team){return team.logo?`<img src="/teams/${encodeURIComponent(team.logo)}" alt="" loading="lazy" onerror="this.remove()">`:'<span class="home-team-mark">'+esc((team.shortName||team.name||'?').slice(0,2))+'</span>'}
function dateTimeLabel(value){
 const m=/^(\d{4})-(\d{2})-(\d{2})\s*-?\s*(\d{1,2}:\d{2})\s*(\w+)?/.exec(value||'');
 if(!m)return value||'Time TBC';
 return `${fmtDate(`${m[1]}-${m[2]}-${m[3]}`)} · ${m[4]}${m[5]?' '+m[5]:''}`;
}
function renderUpcoming(){
 const host=$('#home-matches');
 const upcoming=homeAllMatches.filter(m=>['scheduled','live'].includes(String(m.status).toLowerCase()))
  .sort((a,b)=>String(a.scheduledTime||'').localeCompare(String(b.scheduledTime||''))).slice(0,4);
 const showingResults=!upcoming.length;
 const list=showingResults?homeAllMatches.filter(m=>['completed','finished','forfeit'].includes(String(m.status).toLowerCase()))
  .sort((a,b)=>String(b.scheduledTime||'').localeCompare(String(a.scheduledTime||''))).slice(0,4):upcoming;
 $('#home-matches-title').textContent=showingResults?t('home.competition.latest','Latest Results'):t('home.competition.matches','Upcoming Matches');
 if(!list.length){host.innerHTML='<p class="leader-empty">'+t('home.competition.none','There are no upcoming fixtures recorded for this season yet.')+' <a href="/league">'+t('home.competition.schedule','View the full schedule ↗')+'</a></p>';return}
 host.innerHTML=list.map(m=>{
  const a=teamData(m.team1Id,m.leagueId),b=teamData(m.team2Id,m.leagueId),live=String(m.status).toLowerCase()==='live',done=['completed','finished','forfeit'].includes(String(m.status).toLowerCase());
  return `<a class="home-match" href="/league"><span class="home-match-date">${esc(dateTimeLabel(m.scheduledTime))} · ${esc(homeLeagueNames[m.leagueId]||m.leagueId||'')}</span>
   <span class="home-match-team">${teamMark(a)}<b>${esc(a.shortName||a.name)}</b></span>
   <strong class="home-match-vs">${done||live?esc(m.team1Score+' : '+m.team2Score):'VS'}</strong>
   <span class="home-match-team away">${teamMark(b)}<b>${esc(b.shortName||b.name)}</b></span>
   <span class="home-match-state ${live?'is-live':''}">${live?t('home.competition.live','LIVE'):done?t('home.competition.final','FINAL'):esc(m.bestOf?'BO'+m.bestOf:t('home.competition.upcoming','UPCOMING'))}</span></a>`;
 }).join('');
}
function renderLeaderboard(tab){
 document.querySelectorAll('[data-home-tab]').forEach(b=>{const on=b.dataset.homeTab===tab;b.classList.toggle('selected',on);b.setAttribute('aria-selected',String(on))});
 const body=$('#home-leader-rows'),link=$('#leader-link');
 let rows=[],empty=t('home.leader.noneRegular','No standings are available for this season yet.');
 if(tab==='regular'){
  rows=homeStandings.slice().sort((a,b)=>a.position-b.position).slice(0,6).map(s=>{const tm=teamData(s.teamId);return{rank:s.position,name:tm.name||s.teamId,logo:tm.logo,w:s.won,l:s.lost,value:s.points}});
  link.href='/league';link.textContent=t('home.leader.full','Full standings ↗');
 }else if(tab==='playoffs'){
  const playoff=homeMatches.filter(m=>/play.?off|play.?in|quarter|semi|final|bracket/i.test(m.matchType||'')&&['completed','finished','forfeit'].includes(String(m.status).toLowerCase()));
  const record={};
  playoff.forEach(m=>{[[m.team1Id,m.team1Score,m.team2Score],[m.team2Id,m.team2Score,m.team1Score]].forEach(([id,w,l])=>{if(!record[id])record[id]={w:0,l:0};if(w>l)record[id].w++;else if(w<l)record[id].l++})});
  rows=Object.entries(record).sort((a,b)=>b[1].w-a[1].w||a[1].l-b[1].l).slice(0,6).map(([id,r],i)=>{const tm=teamData(id);return{rank:i+1,name:tm.name||id,logo:tm.logo,w:r.w,l:r.l,value:'—'}});
  empty=t('home.leader.nonePlayoffs','No completed playoff matches are recorded for this season yet.');
  link.href='/league';link.textContent=t('home.leader.playoffLink','View playoff matches ↗');
 }else{
  rows=homeElo.filter(t=>t.qualified).sort((a,b)=>a.rank-b.rank).slice(0,6).map(t=>({rank:t.rank,name:t.name,logo:t.logo,w:t.mapWins,l:t.mapLosses,value:Math.round(t.rating).toLocaleString()}));
  empty=t('home.leader.noneElo','No qualified teams in the global ELO ranking yet.');
  link.href='/league/elo';link.textContent=t('home.leader.eloLink','Full ELO ranking ↗');
 }
 body.innerHTML=rows.length?rows.map(r=>`<div class="leader-row"><span class="leader-team"><b>${String(r.rank).padStart(2,'0')}</b>${r.logo?`<img src="/teams/${encodeURIComponent(r.logo)}" alt="" onerror="this.remove()">`:''}<span>${esc(r.name)}</span></span><span>${r.w}</span><span>${r.l}</span><strong>${esc(r.value)}</strong></div>`).join(''):`<p class="leader-empty">${esc(empty)}</p>`;
}
async function drawCompetition(){
 const host=$('#home-matches');
 const leagues=await call('/api/leagues');
 if(!Array.isArray(leagues)||!leagues.length){host.innerHTML='<p class="leader-empty">'+t('home.competition.unavailable','Pro League data is not available yet.')+' <a href="/league">'+t('home.competition.open','Open Pro League ↗')+'</a></p>';$('#home-leader-rows').innerHTML='<p class="leader-empty">'+t('home.leader.unavailable','Standings are not available.')+'</p>';return}
 // The league file is ordered newest season first. Load each league's newest season for a global match strip.
 const latestByLeague=new Map();
 leagues.forEach(l=>{if(!latestByLeague.has(l.id))latestByLeague.set(l.id,l)});
 const latestLeagues=[...latestByLeague.values()];
 homeLeague=latestLeagues.find(l=>l.status==='active')||latestLeagues[0];
 const packs=await Promise.all(latestLeagues.map(async l=>{
  const qs='league='+encodeURIComponent(l.id)+'&season='+encodeURIComponent(l.season);
  const [teams,standings,matches]=await Promise.all([call('/api/teams?'+qs),call('/api/standings?'+qs),call('/api/matches?'+qs)]);
  return{league:l,teams:Array.isArray(teams)?teams:[],standings:Array.isArray(standings)?standings:[],matches:Array.isArray(matches)?matches:[]};
 }));
 packs.forEach(p=>{homeLeagueNames[p.league.id]=p.league.name;p.teams.forEach(tm=>homeTeams[p.league.id+'|'+tm.id]=tm)});
 const primary=packs.find(p=>p.league.id===homeLeague.id);
 homeStandings=primary?primary.standings:[];
 homeMatches=primary?primary.matches:[];
 homeAllMatches=packs.flatMap(p=>p.matches);
 const elo=await call('/api/elo');
 homeElo=elo&&!elo.error&&Array.isArray(elo.teams)?elo.teams:[];
 $('#match-season').textContent='ALL LEAGUES · LATEST SEASONS';
 $('#leader-season').textContent=homeLeague.season||'CURRENT SEASON';
 $('#home-league-title').textContent=homeLeague.name||'Pro League';
 $('#home-league-meta').textContent=[homeLeague.region,homeLeague.country,homeLeague.season].filter(Boolean).join(' · ')||'Professional MLBB competition';
 renderUpcoming();
 renderLeaderboard('regular');
 document.querySelectorAll('[data-home-tab]').forEach(b=>b.addEventListener('click',()=>renderLeaderboard(b.dataset.homeTab)));
}

/** Page boot: shared shell retains live patch/as-of, language switch, auth and mobile navigation. */
(async()=>{
 const m=await shell(location.pathname);
 drawNews();
 drawCompetition();
 const hs=await call('/api/heroes');if(hs.error){toast(hs.error,'var(--ember)');return}
 heroes=await localizeTips(hs);heroes.forEach(h=>byName[h.name]=h);
 const vals={hero:m.heroes||heroes.length,item:m.items||0,banned:heroes.filter(h=>h.metaBan).length};
 const strip=document.querySelector('.strip');
 new IntersectionObserver((es,o)=>{if(!es[0].isIntersecting)return;o.disconnect();
  strip.querySelectorAll('[data-to]').forEach(b=>countTo(b,vals[b.dataset.to]??+b.dataset.to,1100))}).observe(strip);
 drawPulse();tips();drawBoard();
 addEventListener('focus',drawBoard);
})();
