// Jongrai - script for /items.html. Runs after /app.js and /kit.js.
// The source writes three tiers: 3 finished equipment, 2 intermediate pieces, 1 components and specials.
const TC={3:'T3',2:'T2',1:'T1',0:'T0'};
let items=[],heroes=[],byItem={},bySlug={},byHeroLower={},f={q:'',tier:'',cat:'',sort:'cost'};
const itemIcon=it=>it.icon?`<img src="/icons/${esc(it.icon)}" alt="" onerror="this.remove()">`:'';
const heroAv=n=>{const h=byHeroLower[n.toLowerCase()];return h&&h.icon?`<img src="/icons/${esc(h.icon)}" alt="" onerror="this.remove()">`:''};
const heroChip=n=>{const h=byHeroLower[n.toLowerCase()];if(!h)return '';
 return`<a class="hchip" href="/heroes/${esc(h.slug)}" title="${esc(h.name)}"><i>${heroAv(h.name)}</i>${esc(h.name)}</a>`};

// The stat block is one string per item; "None" means the item carries no stats at all.
function statChips(it){
 if(!it.stats||/^none$/i.test(it.stats.trim()))return'';
 return`<div class="chips">${it.statList.map(s=>`<span>${esc(s)}</span>`).join('')}</div>`;
}
function filtered(){
 let r=items.filter(it=>
  (!f.q||it.name.toLowerCase().includes(f.q)||it.passives.toLowerCase().includes(f.q)
    ||it.stats.toLowerCase().includes(f.q))&&
  (!f.tier||String(it.tier)===f.tier)&&
  (!f.cat||it.categories.includes(f.cat)));
 const s=f.sort;
 r.sort((a,b)=> s==='name'? a.name.localeCompare(b.name)
  : s==='tierv'? b.tier-a.tier||a.name.localeCompare(b.name)
  : s==='cores'? b.cores.length-a.cores.length||b.cost-a.cost
  : s==='costasc'? a.cost-b.cost||a.name.localeCompare(b.name)
  : b.cost-a.cost||a.name.localeCompare(b.name));
 return r;
}
function card(it){
 return`<button class="it" data-s="${esc(it.slug)}">
  <div class="top"><div class="ii">${itemIcon(it)}</div>
   <div class="nm"><b>${esc(it.name)}</b><small>${esc(it.categories.join(' / '))}</small></div>
   <span class="itier ${TC[it.tier]||'T0'}">T${it.tier}</span></div>
  <div class="chips"><span>${it.cost?it.cost+' gold':'no price recorded'}</span></div>
  ${statChips(it)}
  ${it.passives&&!/^none$/i.test(it.passives.trim())?`<div class="pass">${esc(it.passives)}</div>`:''}
  ${it.cores.length?`<div class="coreby">core item for ${it.cores.length} ${it.cores.length>1?'heroes':'hero'}</div>`:''}
 </button>`;
}
function render(){
 const r=filtered();
 $('#count').textContent=r.length+' of '+items.length+' items';
 $('#grid').innerHTML=r.map(card).join('')||'<p class="l">No item matches those filters.</p>';
}
function showList(push){
 $('#detail').hidden=true;$('#list').hidden=false;
 if(push&&/\/items\/[^/]+/.test(location.pathname))history.pushState({},'','/items');
}
/** Opens whatever the current URL points at: /items or /items/<slug>. */
function route(){
 const m=location.pathname.match(/^\/items\/([a-z0-9-]+)\/?$/i);
 const it=m?byItem[m[1].toLowerCase()]:null;
 if(it)showItem(it.slug,false); else showList(false);
}
function showItem(s,push){
 const it=byItem[s.toLowerCase()];if(!it)return showList(false);
 if(push)history.pushState({item:s},'','/items/'+it.slug);
 const kv=(k,v)=>`<div class="kv"><span>${k}</span><b>${v}</b></div>`;
 const list=(arr,label,empty)=>`<div class="rel"><h4>${label}</h4>${
  arr.length?`<div class="c">${arr.map(heroChip).join('')}</div>`:`<div class="empty">${empty}</div>`}</div>`;
 $('#list').hidden=true;$('#detail').hidden=false;
 $('#detail').innerHTML=`
  <div class="back"><button class="btn ghost" id="backBtn">&larr; All items</button></div>
  <div class="dwrap">
   <div class="dcard"><div class="ii">${itemIcon(it)}</div><h3>${esc(it.name)}</h3>
    <div class="sub">${esc(it.categories.join(' / '))} · /items/${esc(it.slug)}</div>
    ${kv('Tier',it.tier)}
    ${kv('Cost',it.cost?it.cost+' gold':'no price recorded')}
    ${it.availability&&!/^no restriction/i.test(it.availability)?`<div class="note">${esc(it.availability)}</div>`:''}
   </div>
   <div>
    ${statChips(it)?`<div class="rel"><h4>Stats</h4>${statChips(it)}</div>`:''}
    <div class="rel"><h4>Passives and actives</h4>
     ${it.passives&&!/^none$/i.test(it.passives.trim())
      ?`<ul class="passives">${it.passives.split(/(?=\b[A-Z][A-Za-z ]+\((?:passive|active)\))/)
          .map(p=>`<li>${esc(p.trim())}</li>`).join('')}</ul>`
      :'<div class="empty">This item carries no passive.</div>'}</div>
    ${list(it.cores,'Core item for','No hero cores this item yet.')}
    ${list(it.users.filter(u=>!it.cores.includes(u)),'Also built by','No hero builds this item.')}
   </div>
  </div>`;
 $('#backBtn').onclick=()=>showList(true);
 window.scrollTo(0,0);
}
document.addEventListener('click',e=>{
 const b=e.target.closest('[data-s]');if(b){showItem(b.dataset.s,true);return}
 const c=e.target.closest('[data-cat]');
 if(c){e.preventDefault();showList(true);setCat(c.dataset.cat);
  history.replaceState({},'',f.cat?'/items?c='+encodeURIComponent(f.cat):'/items');}
});
/** Selects a category chip; '' is the "All" chip and clears the filter. */
function setCat(cat){f.cat=cat;render();
 document.querySelectorAll('[data-cat]').forEach(a=>a.classList.toggle('on',a.dataset.cat===cat));}
window.addEventListener('popstate',()=>{route();window.scrollTo(0,0);});
['q','tier','sort'].forEach(id=>$('#'+id).addEventListener('input',e=>{f[id]=id==='q'?e.target.value.toLowerCase():e.target.value;render();}));
(async()=>{
 try{
  [items,heroes]=await Promise.all([call('/api/items'),call('/api/heroes')]);
  if(items.error||heroes.error)throw new Error(items.error||heroes.error);
  heroes.forEach(h=>byHeroLower[h.name.toLowerCase()]=h);
  items.forEach(i=>byItem[i.slug.toLowerCase()]=i);
  $('#cats').innerHTML=['',...[...new Set(items.flatMap(i=>i.categories))].sort()]
   .map(c=>`<a href="${c?'/items?c='+encodeURIComponent(c):'/items'}" data-cat="${esc(c)}">${c?esc(c):'All'}</a>`).join('');
  [...new Set(items.map(i=>i.tier))].sort((a,b)=>b-a).forEach(t=>$('#tier').add(new Option('Tier '+t,t)));
  route();shell('/items');
  const c=new URLSearchParams(location.search).get('c');
  setCat(c||'');
 }catch(e){$('#grid').innerHTML='<p class="l">Item catalog unavailable.</p>';}
})();
