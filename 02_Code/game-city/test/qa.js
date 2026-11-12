const panel=document.createElement('div');panel.id='qa';document.body.append(panel);
const style=document.createElement('link');style.rel='stylesheet';style.href='mobile.css'; // existing stylesheet includes QA-only selectors
const line=document.createElement('output');panel.append(line);
function button(text,click){const b=document.createElement('button');b.textContent=text;b.onclick=click;panel.append(b);}
const send=m=>window.EventMapMobile.receive({v:1,...m});
button('Байтерек',()=>send({type:'focus',id:'baiterek'}));button('Ак Орда',()=>send({type:'focus',id:'akorda'}));button('Арена',()=>send({type:'focus',id:'arena'}));
let night=false;button('День / ночь',()=>{night=!night;send({type:'appearance',night,theme:'default'})});
for(const r of ['pilot-1','pilot-2','offline','incompatible'])button(r,async()=>{await fetch('/__qa/release?value='+r,{method:'POST'});send({type:'update'});});
addEventListener('eventmap-message',e=>{line.textContent=JSON.stringify(e.detail);});
setInterval(()=>{const s=window.EventMapMobile?.stats();panel.title=JSON.stringify(s);},2000);
