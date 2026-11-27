const boards={
 regular:[["ONIC","9","2","27"],["RRQ HOSHI","8","3","24"],["BLACKLIST","7","4","21"],["ALTER EGO","6","5","18"],["TEAM LIQUID","5","6","15"]],
 playoffs:[["ONIC","3","0","—"],["RRQ HOSHI","2","1","—"],["BLACKLIST","1","2","—"],["ALTER EGO","0","3","—"]],
 elo:[["ONIC","—","—","1,684"],["RRQ HOSHI","—","—","1,642"],["BLACKLIST","—","—","1,617"],["TEAM LIQUID","—","—","1,590"],["ALTER EGO","—","—","1,574"]]
};
const rows=document.querySelector('#leader-rows');
function renderBoard(key){rows.innerHTML=boards[key].map((r,i)=>'<div class="leader-row"><span><b>'+String(i+1).padStart(2,'0')+'</b>'+r[0]+'</span><span>'+r[1]+'</span><span>'+r[2]+'</span><strong>'+r[3]+'</strong></div>').join('');}
renderBoard('regular');
document.querySelectorAll('[data-tab]').forEach(btn=>btn.addEventListener('click',()=>{document.querySelectorAll('[data-tab]').forEach(b=>b.classList.toggle('selected',b===btn));renderBoard(btn.dataset.tab)}));
const toggle=document.querySelector('.menu-toggle'),nav=document.querySelector('.nav');
toggle.addEventListener('click',()=>{const open=nav.classList.toggle('open');toggle.setAttribute('aria-expanded',String(open));});
nav.querySelectorAll('a').forEach(a=>a.addEventListener('click',()=>{nav.classList.remove('open');toggle.setAttribute('aria-expanded','false')}));
