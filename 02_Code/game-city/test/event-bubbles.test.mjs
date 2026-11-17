import test from 'node:test';
import assert from 'node:assert/strict';
import {safeBubbleCover,bubbleRect,nativeBubbleCovers,setLabelVisible,positionLabel,setLabelClass,setLabelLevel} from '../runtime/event-bubbles.js';
import {visibleEvents} from '../runtime/social-state.js';
test('native covers require current event ID and cover revision, never demo or remote URLs',()=>{
  const data='data:image/jpeg;base64,/9j/',events=visibleEvents([{id:'e',lat:51.13,lng:71.43,coverRef:'new'}]);
  assert.equal(events[0].coverRef,'new');
  assert.equal(nativeBubbleCovers([{id:'e',ref:'new',data}],events).get('e'),data);
  for(const item of [{id:'other',ref:'new',data},{id:'e',ref:'old',data},{id:'e',ref:'new',data:'event-covers/sport.svg'},{id:'e',ref:'new',data:'https://cdn.example/a.jpg'}])assert.equal(nativeBubbleCovers([item],events).size,0);
  assert.equal(nativeBubbleCovers([{id:'e',ref:'new',data}],visibleEvents([{id:'e',lat:51.13,lng:71.43,coverRef:'new',visibility:'private'}])).size,0);
});
test('preview covers accept only bundled fixture paths or bounded raster data',()=>{
  assert.equal(safeBubbleCover('event-covers/sport.svg'),'event-covers/sport.svg');
  assert.equal(safeBubbleCover('data:image/jpeg;base64,YWJj'),'data:image/jpeg;base64,YWJj');
  for(const value of [null,{},'javascript:alert(1)','https://tracker.test/a.jpg','//tracker.test/a','../a.png','event-covers/../private.svg','data:image/svg+xml;base64,YWJj','data:text/html;base64,YWJj','data:image/png;base64,'+'A'.repeat(180001)])assert.equal(safeBubbleCover(value),null);
});
test('balloon tether stays at geographic anchor and avoids viewport chrome',()=>{
  const viewport={width:390,height:844,top:90,small:true};
  assert.deepEqual(bubbleRect(195,400,viewport),{x:143,y:262,w:104,h:138});
  for(const [x,y]of [[20,400],[380,400],[195,100],[195,800],[NaN,400]])assert.equal(bubbleRect(x,y,viewport),null);
  assert.equal(bubbleRect(200,400,viewport).x+52,200);
});
test('idle labels do not rewrite visibility or position every render frame',()=>{
  let hidden=false,transform='',visibilityWrites=0,positionWrites=0,classWrites=0,levelWrites=0,domReads=0;
  const label={get hidden(){domReads++;return hidden;},set hidden(v){hidden=v;visibilityWrites++;},style:{get transform(){domReads++;return transform;},set transform(v){transform=v;positionWrites++;}},classList:{toggle(){classWrites++;}},dataset:{set level(v){levelWrites++;}}};
  for(let i=0;i<300;i++){setLabelVisible(label,false);positionLabel(label,195.001,400.001);setLabelClass(label,'balloon-moving',false);setLabelLevel(label,'city');}
  assert.equal(visibilityWrites,1);assert.equal(positionWrites,1);
  assert.equal(classWrites,1);assert.equal(levelWrites,1);assert.equal(domReads,1,'unchanged labels do not read DOM state every frame either');
  setLabelVisible(label,true);positionLabel(label,195.026,400.026);
  assert.equal(visibilityWrites,2);assert.equal(positionWrites,1);
  positionLabel(label,196,401);assert.equal(positionWrites,2);
  assert.equal(transform,'translate(196px,401px) translate(-50%,-100%)');
  setLabelClass(label,'balloon-moving',true);setLabelLevel(label,'street');assert.equal(classWrites,2);assert.equal(levelWrites,2);
});
