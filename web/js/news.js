// Jongrai - script for /news.html (the list at /news and one article at /news/<slug>). Runs after /app.js and /kit.js.
// Articles are text files in data/news/; see data/news/_TEMPLATE.txt for how to write one.
const TYPES=[['patch','Patch'],['skin','Skin'],['event','Event'],['esports','Esports'],['news','News']];
let list=[],heroes=[],heroLower={},filter='',tag='';
/** A tag's label: Khmer from km.json news.tag.<slug> when there is one, else the tag as written. */
const tagName=g=>t('news.tag.'+g.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,''),g);
const tagChips=a=>(a.tags||[]).map(g=>`<span class="tg" data-tag="${esc(g)}">${esc(tagName(g))}</span>`).join('');
const typeName=ty=>{const e=TYPES.find(x=>x[0]===ty);return t('news.type.'+ty,e?e[1]:ty)};
const pick=(a,en,km)=>LANG==='km'&&a[km]?a[km]:a[en];
const media=f=>'/news-media/'+encodeURIComponent(f);
const daysLeft=iso=>{if(!iso)return null;const end=new Date(iso+'T23:59:59'),now=new Date();return Math.ceil((end-now)/864e5)};
function countdown(a){
 const d=daysLeft(a.ends);if(d===null)return'';
 if(d<1&&new Date(a.ends+'T23:59:59')<new Date())return`<span class="cd over">${t('news.ended','Ended')}</span>`;
 return`<span class="cd">${d<=1?t('news.endsToday','Ends today'):t('news.endsIn','Ends in {n} days',{n:d})}</span>`;
}
const heroChip=n=>{const h=heroLower[n.toLowerCase()];if(!h)return`<span class="hchip">${esc(n)}</span>`;
 return`<a class="hchip" href="/heroes/${esc(h.slug)}"><i>${h.icon?`<img src="/icons/${esc(h.icon)}" alt="">`:''}</i>${esc(h.name)}</a>`};

/** The story text: blank line = new paragraph, "## " sub-heading, "- " bullet, "[image: file | caption]",
 *  **bold** and [[Hero]] links. Everything is escaped first, so a story can never inject HTML. */
function story(text){
 const inline=s=>esc(s).replace(/(\d+-\d+)/g,'<span class="nb">$1</span>').replace(/\*\*(.+?)\*\*/g,'<b>$1</b>')
  .replace(/\[\[([^\]]+)\]\]/g,(m,n)=>{const h=heroLower[n.trim().toLowerCase()];return h?`<a href="/heroes/${esc(h.slug)}">${esc(h.name)}</a>`:esc(n)});
 const out=[];let para=[],ul=[];
 const flush=()=>{if(para.length){out.push(`<p>${para.map(inline).join(' ')}</p>`);para=[]}if(ul.length){out.push(`<ul>${ul.map(x=>`<li>${inline(x)}</li>`).join('')}</ul>`);ul=[]}};
 for(const raw of text.split('\n')){
  const line=raw.trim();
  if(!line){flush();continue}
  let m;
  if((m=/^\[image:\s*([^|\]]+?)\s*(?:\|\s*([^\]]*))?\]$/i.exec(line))){flush();
   out.push(`<figure><img src="${media(m[1])}" alt="${esc(m[2]||'')}" loading="lazy">${m[2]?`<figcaption>${inline(m[2])}</figcaption>`:''}</figure>`);continue}
  if(line.startsWith('## ')){flush();out.push(`<h3>${inline(line.slice(3))}</h3>`);continue}
  if(/^[-•]\s+/.test(line)){if(para.length){const u=ul;ul=[];flush();ul=u}ul.push(line.replace(/^[-•]\s+/,''));continue}
  if(ul.length)flush();
  para.push(line);
 }
 flush();return out.join('');
}
function card(a){
 return`<a class="ncard" href="/news/${esc(a.slug)}" data-slug="${esc(a.slug)}">
  <div class="cover ${esc(a.type)}">${a.image?`<img src="${media(a.image)}" alt="" loading="lazy">`:`<span>${esc(typeName(a.type))}</span>`}</div>
  <div class="txt"><div class="meta"><span class="ty ${esc(a.type)}">${esc(typeName(a.type))}</span><time>${esc(fmtDate(a.date))}</time>${countdown(a)}</div>
  ${a.tags&&a.tags.length?`<div class="tgs">${tagChips(a)}</div>`:''}
  <h3>${esc(pick(a,'title','titleKm'))}</h3>${a.summary?`<p>${esc(pick(a,'summary','summaryKm'))}</p>`:''}</div></a>`;
}
function drawList(){
 const shown=list.filter(a=>(!filter||a.type===filter)&&(!tag||(a.tags||[]).includes(tag)));
 const counts=Object.fromEntries(TYPES.map(([k])=>[k,list.filter(a=>a.type===k).length]));
 $('#types').innerHTML=[['',t('news.all','All')],...TYPES.filter(([k])=>counts[k]).map(([k])=>[k,typeName(k)])]
  .map(([k,l])=>`<a href="${k?'/news?type='+k:'/news'}" data-type="${k}" class="${k===filter&&(k||!tag)?'on':''}">${esc(l)}</a>`).join('');
 // topic filters: every tag in use, most used first
 const tc={};list.forEach(a=>(a.tags||[]).forEach(g=>tc[g]=(tc[g]||0)+1));
 const tl=Object.keys(tc).sort((x,y)=>tc[y]-tc[x]||x.localeCompare(y));
 $('#tags').hidden=!tl.length;
 $('#tags').innerHTML=tl.map(g=>`<a href="/news?tag=${encodeURIComponent(g)}" data-tag="${esc(g)}" class="${g===tag?'on':''}">${esc(tagName(g))}<i>${tc[g]}</i></a>`).join('');
 $('#feed').innerHTML=!list.length?`<p class="l">${t('news.empty','No news yet.')}</p>`
  :!shown.length?`<p class="l">${t('news.none','No news of this type yet.')}</p>`
  :`<div class="lead-card">${card(shown[0])}</div>${shown.length>1?`<div class="grid">${shown.slice(1).map(card).join('')}</div>`:''}`;
}
async function showArticle(slug,push){
 const a=await call('/api/news?slug='+encodeURIComponent(slug));
 if(a.error){location.href='/news';return}
 if(push)history.pushState({},'','/news/'+slug);
 const body=LANG==='km'&&a.bodyKm?a.bodyKm:a.body;
 document.title=pick(a,'title','titleKm')+' - Jongrai';
 $('#story').innerHTML=`<div class="meta"><span class="ty ${esc(a.type)}">${esc(typeName(a.type))}</span><time>${esc(fmtDate(a.date))}</time>${tagChips(a)}
   ${a.author?`<span>${t('news.by','by {a}',{a:esc(a.author)})}</span>`:''}${countdown(a)}</div>
  <h1>${esc(pick(a,'title','titleKm'))}</h1>
  ${a.summary?`<p class="dek">${esc(pick(a,'summary','summaryKm'))}</p>`:''}
  ${a.image?`<figure class="cover-fig"><img class="hero-img" src="${media(a.image)}" alt="">${a.credit?`<figcaption>${t('news.credit','Photo: {c}',{c:esc(a.credit)})}</figcaption>`:''}</figure>`:''}
  ${a.heroes.length?`<div class="heroes"><span>${t('news.heroes','Heroes in this story')}</span>${a.heroes.map(heroChip).join('')}</div>`:''}
  <div class="body">${story(body)}</div>`;
 $('#list').hidden=true;$('#article').hidden=false;window.scrollTo(0,0);
}
function showList(push){
 if(push){const q=new URLSearchParams();if(filter)q.set('type',filter);if(tag)q.set('tag',tag);history.pushState({},'','/news'+(q.toString()?'?'+q:''))}
 document.title=t('news.title','Latest news')+' - Jongrai';
 $('#article').hidden=true;$('#list').hidden=false;drawList();
}
function route(){
 const m=/^\/news\/([^/]+)$/.exec(location.pathname);
 const q=new URLSearchParams(location.search);filter=q.get('type')||'';tag=q.get('tag')||'';
 if(m)showArticle(decodeURIComponent(m[1]),false);else showList(false);
}
document.addEventListener('click',e=>{
 if(e.ctrlKey||e.metaKey||e.shiftKey||e.button)return;
 const tg=e.target.closest('[data-tag]');if(tg){e.preventDefault();tag=tg.dataset.tag===tag&&tg.closest('#tags')?'':tg.dataset.tag;showList(true);return}  // a tag anywhere filters by it; clicking the lit filter again clears it
 const ty=e.target.closest('[data-type]');if(ty){e.preventDefault();filter=ty.dataset.type;if(!filter)tag='';showList(true);return}
 const c=e.target.closest('.ncard');if(c){e.preventDefault();showArticle(c.dataset.slug,true);return}
 const b=e.target.closest('.back');if(b){e.preventDefault();filter='';tag='';showList(true)}
});
window.addEventListener('popstate',route);
(async()=>{
 shell('/news');
 try{
  await i18nReady();
  [list,heroes]=await Promise.all([call('/api/news'),call('/api/heroes')]);
  if(list.error)throw new Error(list.error);
  if(!heroes.error)heroes.forEach(h=>heroLower[h.name.toLowerCase()]=h);
  route();
 }catch(e){$('#feed').innerHTML=`<p class="l" style="color:var(--ember)">${t('news.err','News could not be loaded. Restart the server after updating.')}</p>`}
})();
