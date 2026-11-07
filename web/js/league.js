const cap=t=>String(t||'').replace(/\b\w/g,c=>c.toUpperCase());
// Jongrai - script for /pro-league.html. Runs after /app.js and /kit.js.
const $$=s=>document.querySelectorAll(s);
const STATUS_CLS={finished:'completed',completed:'completed',scheduled:'scheduled',live:'live',postponed:'postponed',cancelled:'cancelled'};
let leagues=[], currentLeague=null, currentSeason=null, teamsById={}, heroIcons={};
const keyOf=l=>l.id+'|'+l.season;   // one league can run several seasons

async function loadLeagues(){
 try{
  leagues=await call('/api/leagues');
  if(leagues.error)throw new Error(leagues.error);
  renderLeagueBar();
  if(leagues.length) selectLeague(keyOf(leagues[0]));
 }catch(e){$('#leagueBar').innerHTML='<span style="color:var(--mut)">Failed to load leagues</span>';}
}
function renderLeagueBar(){
 const bar=$('#leagueBar');
 bar.innerHTML=leagues.map(l=>`<button type="button" class="league-btn${keyOf(l)===selected?' active':''}" data-key="${esc(keyOf(l))}" title="${esc(l.region)} - ${esc(l.season)}">${esc(l.name)} · ${esc(l.season)}</button>`).join('');
 bar.querySelectorAll('.league-btn').forEach(btn=>btn.onclick=()=>selectLeague(btn.dataset.key));
}
let selected='';
async function selectLeague(key){
 selected=key;
 const league=leagues.find(l=>keyOf(l)===key);
 currentLeague=league?.id||'';
 currentSeason=league?.season||'';
 renderLeagueBar();
 await loadData();
}
function setTab(tab){
 $$('#tabs .tab').forEach(t=>t.classList.toggle('active',t.dataset.tab===tab));
 $$('#standingsPanel,#matchesPanel,#teamsPanel,#draftsPanel').forEach(p=>p.hidden=true);
 $('#'+tab+'Panel').hidden=false;
}
$$('#tabs .tab').forEach(t=>t.onclick=()=>setTab(t.dataset.tab));

async function loadData(){
 if(!currentLeague) return;
 try{
  const ls='league='+encodeURIComponent(currentLeague), season=ls+'&season='+encodeURIComponent(currentSeason);
  const [standings,matches,teams,drafts,heroes]=await Promise.all([
   call('/api/standings?'+season),call('/api/matches?'+season),call('/api/teams?'+season),call('/api/drafts?'+season),call('/api/heroes').catch(()=>[])]);
  const failed=[standings,matches,teams,drafts].find(r=>r.error);
  if(failed)throw new Error(failed.error);

  teamsById={};
  teams.forEach(t=>teamsById[t.id]=t);
  heroIcons={};
  if(Array.isArray(heroes))heroes.forEach(h=>{heroIcons[String(h.name).toLowerCase()]={icon:h.icon||'',slug:h.slug||''}});
  renderStandings(standings);
  renderMatches(matches);
  renderTeams(teams);
  renderDrafts(drafts,matches);
 }catch(e){
  console.error(e);
  $('#standingsEmpty').hidden=false;
  $('#matchesEmpty').hidden=false;
  $('#teamsEmpty').hidden=false;
  $('#draftsEmpty').hidden=false;
 }
}
function renderStandings(data){
 const body=$('#standingsBody'), empty=$('#standingsEmpty');
 if(!data.length){body.innerHTML='';empty.hidden=false;return}
 empty.hidden=true;
 const dim=v=>v===0?' dim':'';
 body.innerHTML=data.map(s=>{
  const t=teamsById[s.teamId]||{name:s.teamId,logo:'',shortName:''};
  return `<tr>
   <td class="c-pos"><span class="rank${s.position<=3?' r'+s.position:''}">${s.position}</span></td>
   <td class="c-team"><div class="team-cell"><img class="team-logo" src="/teams/${esc(t.logo||'')}" alt="" onerror="this.remove()"><div><div class="team-name">${esc(t.name||s.teamId)}</div><div class="team-short">${esc(t.shortName||'')}</div></div></div></td>
   <td>${s.played}</td>
   <td class="win${dim(s.won)}">${s.won}</td>
   <td class="loss${dim(s.lost)}">${s.lost}</td>
   <td class="draw${dim(s.draw)}">${s.draw}</td>
   <td>${s.gf}</td>
   <td>${s.ga}</td>
   <td class="${s.gd>0?'win':s.gd<0?'loss':'dim'}">${s.gd>0?'+':''}${s.gd}</td>
   <td><span class="pts">${s.points}</span></td>
  </tr>`;
 }).join('');
}
function matchTeam(id,state){
 const t=teamsById[id]||{name:id,logo:'',shortName:id};
 const logo=t.logo?`<img src="/teams/${esc(t.logo)}" alt="" loading="lazy" onerror="this.parentElement.classList.add('nologo');this.remove()">`:'';
 return `<div class="match-team ${state}"><div class="match-logo${t.logo?'':' nologo'}" data-s="${esc(t.shortName||t.name||id)}">${logo}</div><span class="match-team-name">${esc(t.name||id)}</span></div>`;
}
function renderMatches(data){
 const list=$('#matchesList'), empty=$('#matchesEmpty');
 if(!data.length){list.innerHTML='';empty.hidden=false;return}
 empty.hidden=true;
 list.innerHTML=data.map(m=>{
  const done=m.status==='completed'||m.status==='finished';
  const ls=m.team1Score, rs=m.team2Score, bo=boOf(m,ls,rs,done);
  const s1=done&&ls<rs?'is-lose':'', s2=done&&rs<ls?'is-lose':'';
  const score=done
   ?`<div class="match-score"><span class="${ls>rs?'w':''}">${ls}</span> : <span class="${rs>ls?'w':''}">${rs}</span>${bo?`<small>(${bo})</small>`:''}</div>`
   :`<div class="match-score vs">vs${bo?`<small>(${bo})</small>`:''}</div>`;
  return `<div class="match-card">
   <div class="match-top"><span class="match-status ${STATUS_CLS[m.status]||''}">${esc(m.status)}</span><span class="match-meta">${m.matchType==='regular'?`Week ${m.week} · Day ${m.day}`:esc(cap(m.matchType))}</span></div>
   ${m.scheduledTime?`<div class="match-when"><span>${esc(fmtWhen(m.scheduledTime))}</span></div>`:''}
   <div class="match-body">${matchTeam(m.team1Id,s1)}${score}${matchTeam(m.team2Id,s2)}</div>
  </div>`;
 }).join('');
}
function renderTeams(data){
 const grid=$('#teamsGrid'), empty=$('#teamsEmpty');
 if(!data.length){grid.innerHTML='';empty.hidden=false;return}
 empty.hidden=true;
 grid.innerHTML=data.map(t=>`<div class="card" style="text-align:center">
  <img src="/teams/${esc(t.logo||'')}" alt="" style="width:64px;height:64px;border-radius:8px;object-fit:cover;margin-bottom:8px" onerror="this.remove()">
  <div style="font-weight:600">${esc(t.name)}</div>
  <div style="color:var(--mut);font-size:12px">${esc(t.shortName)} · ${esc(t.city)}</div>
  <div style="color:var(--mut);font-size:11px;margin-top:4px">Coach: ${esc(t.coach||'Unknown')}</div>
  <div style="margin-top:8px"><span class="badge" style="background:${t.status==='active'?'var(--blue)':'var(--mut)'}">${esc(t.status)}</span></div>
 </div>`).join('');
}
const MONTHS=['January','February','March','April','May','June','July','August','September','October','November','December'];
function fmtWhen(t){const m=/^(\d{4})-(\d{2})-(\d{2})\s*-?\s*(\d{1,2}:\d{2})\s*(\w+)?/.exec(t||'');
 return m?`${MONTHS[+m[2]-1]} ${+m[3]}, ${m[1]} - ${m[4]}${m[5]?' '+m[5]:''}`:(t||'')}
const fmtDur=s=>s>0?`${String(Math.floor(s/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`:'';
function heroFallback(img){const s=document.createElement('span');s.className=img.className+' hero-ic-miss';s.textContent=img.alt.slice(0,2);img.replaceWith(s)}
function heroHtml(name,side){
 const h=heroIcons[String(name).toLowerCase()]||{icon:'',slug:''};
 const pic=h.icon
  ?`<img class="hero-ic ${side}" src="/icons/${esc(h.icon)}" alt="${esc(name)}" loading="lazy" onerror="heroFallback(this)">`
  :`<span class="hero-ic hero-ic-miss ${side}">${esc(String(name).slice(0,2))}</span>`;
 // a hero that is in the roster links to its page: /heroes/<slug>
 return h.slug?`<a class="hero-link" href="/heroes/${esc(h.slug)}" title="${esc(name)}">${pic}</a>`
              :`<span class="hero-link" title="${esc(name)}">${pic}</span>`;
}
function teamBox(id,right){
 const t=teamsById[id]||{name:id,logo:''};
 const full=t.name||id, short=t.shortName||full;
 const logo=t.logo?`<img src="/teams/${esc(t.logo)}" alt="" onerror="this.remove()">`:'';
 // collapsed card shows the short name, an opened card shows the full name (switched in CSS)
 const name=`<span class="nm nm-short" title="${esc(full)}">${esc(short)}</span><span class="nm nm-full">${esc(full)}</span>`;
 return `<div class="mc-team${right?' right':''}">${right?logo+name:name+logo}</div>`;
}
function gameBlock(d,leftId,matchMvp){
 const leftBlue=d.blueTeamId===leftId;
 const lSide=leftBlue?'blue':'red', rSide=leftBlue?'red':'blue';
 const lPicks=leftBlue?d.bluePicks:d.redPicks, rPicks=leftBlue?d.redPicks:d.bluePicks;
 const lBans=leftBlue?d.blueBans:d.redBans, rBans=leftBlue?d.redBans:d.blueBans;
 const res=(won)=>d.winnerTeamId?`<span class="mc-res ${won?'W':'L'}">${won?'W':'L'}</span>`:'<span></span>';
 const lWon=d.winnerTeamId===leftId;
 const dur=fmtDur(d.duration);
 return `<div class="mc-game">
  <div class="mc-row">${res(lWon)}
   <div class="mc-picks">${lPicks.map(h=>heroHtml(h,lSide)).join('')}</div>
   <div class="mc-center"><b>${dur||'-'}</b><small>Game ${d.gameNumber}</small></div>
   <div class="mc-picks right">${rPicks.map(h=>heroHtml(h,rSide)).join('')}</div>
   ${res(!lWon)}</div>
  <div class="mc-row mc-bans"><span></span>
   <div class="mc-picks">${lBans.map(h=>heroHtml(h,lSide)).join('')}</div>
   <div class="mc-center">Bans</div>
   <div class="mc-picks right">${rBans.map(h=>heroHtml(h,rSide)).join('')}</div><span></span></div>
  ${d.map?`<div class="mc-map"><span><i>Map</i> ${esc(d.map)}</span></div>`:''}
  ${d.mvp?`<div class="mc-mvp">MVP: <b>${esc(d.mvp)}</b></div>`:matchMvp?'':'<div class="mc-mvp none">MVP: not recorded</div>'}
 </div>`;
}
function boOf(m,ls,rs,done){
 if(m&&m.bestOf>0)return 'Bo'+m.bestOf;                 // set best_of in pro-matches.csv
 if(!done)return '';
 const hi=Math.max(ls,rs), lo=Math.min(ls,rs);
 if(hi===1&&lo===1)return 'Bo2';
 return lo>0&&hi>lo?'Bo'+(2*hi-1):'';                   // a sweep is Bo2 or Bo3, so it needs best_of
}
function matchCard(m,games){
 const leftId=m?m.team1Id:games[0].blueTeamId;
 const rightId=m?m.team2Id:games[0].redTeamId;
 const done=!!m&&(m.status==='completed'||m.status==='finished');
 let ls,rs;
 if(done){ls=m.team1Score;rs=m.team2Score}
 else{ls=games.filter(g=>g.winnerTeamId===leftId).length;rs=games.filter(g=>g.winnerTeamId===rightId).length}
 const bo=boOf(m,ls,rs,done);
 return `<details class="mc">
  <summary>
   ${m&&m.scheduledTime?`<div class="mc-when"><span>${esc(fmtWhen(m.scheduledTime))}</span></div>`:''}
   <div class="mc-head">${teamBox(leftId,false)}
    <div class="mc-score"><span class="${ls>rs?'w':''}">${ls}</span> : <span class="${rs>ls?'w':''}">${rs}</span>${bo?`<small>(${bo})</small>`:''}</div>
    ${teamBox(rightId,true)}</div>
   <div class="mc-more"></div>
  </summary>
  <div class="mc-body">${games.map(g=>gameBlock(g,leftId,m&&m.mvp)).join('')}
   ${m&&m.mvp?`<div class="mc-mvp match">Match MVP: <b>${esc(m.mvp)}</b></div>`:''}</div>
 </details>`;
}
function renderDrafts(data,matches){
 const list=$('#draftsList'), empty=$('#draftsEmpty');
 if(!data.length){list.innerHTML='';empty.hidden=false;return}
 empty.hidden=true;
 const byId={}; (matches||[]).forEach(m=>byId[m.id]=m);
 const groups=new Map();
 data.forEach(d=>{if(!groups.has(d.matchId))groups.set(d.matchId,[]);groups.get(d.matchId).push(d)});
 list.innerHTML=[...groups].map(([id,g])=>matchCard(byId[id],g.sort((a,b)=>a.gameNumber-b.gameNumber))).join('');
}
shell('/league');loadLeagues();
