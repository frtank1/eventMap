import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {placeMessage} from '../runtime/place-addresses.js';

const qaSource=readFileSync(new URL('./stage-one-qa.js',import.meta.url),'utf8');
const mainSource=readFileSync(new URL('../runtime/main.js',import.meta.url),'utf8');
const bridgeStart=mainSource.indexOf('const native = data => {');
const bridgeEnd=mainSource.indexOf('\nconst info =',bridgeStart);
assert.ok(bridgeStart>=0&&bridgeEnd>bridgeStart,'Locate the real runtime native() adapter; do not copy its implementation into the test');
const bridgeSource=mainSource.slice(bridgeStart,bridgeEnd);

// Minimal DOM for event wiring and form state only. No browser, WebGL, network,
// generated assets or layout assertions. The actual QA script runs unchanged.
class Element {
  constructor(tag){this.tag=tag;this.children=[];this.attributes={};this.value='';this.textContent='';}
  append(...items){
    for(const item of items){
      item.parent=this;this.children.push(item);
      if(this.tag==='select'&&this.children.length===1)this.value=item.value;
    }
  }
  replaceChildren(...items){this.children=[];this.append(...items);}
  setAttribute(key,value){this.attributes[key]=value;}
  remove(){this.parent.children=this.parent.children.filter(n=>n!==this);}
  insertBefore(item,before){item.parent=this;this.children.splice(this.children.indexOf(before),0,item);}
  querySelector(tag){return walk(this).find(n=>n.tag===tag)??null;}
  pause(){}
}
function walk(root){return [root,...root.children.flatMap(walk)];}
function setup(transport='browser'){
  const body=new Element('body'),panel=new Element('div'),listeners=new Map();body.append(panel);
  const sent=[],received=[],nativeMessages=[];
  const window={
    EventMapMobile:{receive:m=>sent.push(structuredClone(m))},
    dispatchEvent(event){received.push(structuredClone(event.detail));listeners.get(event.type)?.(event);return true;},
  };
  const postMessage=raw=>nativeMessages.push(JSON.parse(raw));
  if(transport==='android')window.EventMapNative={postMessage};
  if(transport==='webkit')window.webkit={messageHandlers:{EventMapNative:{postMessage}}};
  const context=vm.createContext({
    location:{search:'?stage1'},URLSearchParams,window,
    document:{body,createElement:tag=>new Element(tag),querySelector:selector=>selector==='#qa'?panel:null},
    addEventListener:(type,listener)=>listeners.set(type,listener),
    CustomEvent:class {constructor(type,{detail}){this.type=type;this.detail=detail;}},
  });
  vm.runInContext(qaSource,context,{filename:'stage-one-qa.js'});
  vm.runInContext(`let placeRequest=0;\n${bridgeSource}\nglobalThis.emitRuntimeMessage=native;`,context,{filename:'main.js:native'});
  const button=title=>walk(body).find(n=>n.tag==='button'&&n.textContent===title);
  const entranceButtons=()=>walk(body).filter(n=>n.attributes['aria-pressed']!==undefined);
  const dialog=()=>walk(body).find(n=>n.attributes.role==='dialog');
  const submit=()=>{
    const form=walk(body).find(n=>n.tag==='form');assert.ok(form,'Demo creation form is open');
    walk(form).find(n=>n.name==='title').value='Тестовая встреча';
    form.onsubmit({preventDefault(){}});
    return sent.at(-1).events.at(-1);
  };
  return {context,body,window,sent,received,nativeMessages,button,entranceButtons,dialog,submit,emit:context.emitRuntimeMessage};
}

const place={id:'building',title:'Библиотека',lat:51.128,lng:71.43,geometryKind:'footprint'};
const entrance={id:'node/123',lat:51.1281,lng:71.4302,kind:'main',wheelchair:'unknown'};
const details={address:'улица Достык, 11',addressStatus:'source',addressSource:'OpenStreetMap',sourceRef:'osm:way/100',entranceStatus:'mapped',
  entrances:[entrance,{...entrance,id:'node/456',lat:51.1283,kind:'home',wheelchair:'no'}]};
const message=()=>placeMessage(place,structuredClone(details));

test('real runtime native() opens a browser QA place card without a caller-supplied v',()=>{
  const h=setup(),raw=message();assert.equal(Object.hasOwn(raw,'v'),false);
  h.emit(raw);
  assert.equal(h.received[0].v,1,'CustomEvent must carry the same versioned envelope as native postMessage');
  assert.equal(h.dialog()?.attributes['aria-label'],place.title);
  assert.equal(h.entranceButtons().length,2);
  assert.ok(h.entranceButtons().every(b=>b.attributes['aria-pressed']==='false'));
  assert.ok(walk(h.body).some(n=>n.textContent==='Источник объекта: osm:way/100'));
  assert.ok(walk(h.body).some(n=>n.textContent.includes('не официальные')));
  assert.equal(Object.hasOwn(raw,'v'),false,'The transport must not mutate its caller');
});

test('browser, Android and WebKit transport the same place envelope',()=>{
  const raw=message(),browser=setup();browser.emit(raw);
  for(const transport of ['android','webkit']){
    const h=setup(transport);h.emit(raw);
    assert.deepEqual(h.nativeMessages,[browser.received[0]],transport);
    assert.equal(h.received.length,0,'Native transport must not also dispatch the browser demo');
  }
});

test('raw unversioned CustomEvent remains rejected by QA',()=>{
  const h=setup();h.window.dispatchEvent({type:'eventmap-message',detail:message()});
  assert.equal(h.dialog(),undefined);
});

test('runtime place → entrance button → demo form preserves selected coordinates and identity',()=>{
  const h=setup();h.emit(message());h.entranceButtons()[1].onclick();
  assert.equal(h.entranceButtons()[1].attributes['aria-pressed'],'true');
  assert.equal(h.entranceButtons()[0].attributes['aria-pressed'],'false');
  h.button('Создать здесь · демо').onclick();const created=h.submit();
  assert.equal(created.lat,details.entrances[1].lat);assert.equal(created.lng,details.entrances[1].lng);
  assert.equal(created.locationLabel,'улица Достык, 11 · Библиотека · вход OSM node/456');
  assert.equal(h.dialog(),undefined);
});

test('unselected, deselected and reopened runtime places retain their original point',()=>{
  for(const mode of ['unselected','deselected','reopened']){
    const h=setup();h.emit(message());
    if(mode!=='unselected')h.entranceButtons()[0].onclick();
    if(mode==='deselected')h.button('Сбросить выбор входа').onclick();
    if(mode==='reopened')h.emit(message());
    h.button('Создать здесь · демо').onclick();const created=h.submit();
    assert.equal(created.lat,place.lat,mode);assert.equal(created.lng,place.lng,mode);
    assert.equal(created.locationLabel,'улица Достык, 11 · Библиотека',mode);
  }
});

test('legacy v1 defaults to unreviewed and missing states have no fabricated buttons',()=>{
  for(const status of [undefined,'not_mapped','unreviewed','unavailable']){
    const h=setup(),raw={type:'place',...place};
    if(status!==undefined)raw.entranceStatus=status;
    h.emit(raw);assert.ok(h.dialog());assert.equal(h.entranceButtons().length,0);
    assert.equal(h.context.stageOnePlace(h.received[0]).entranceStatus,status??'unreviewed');
    assert.ok(walk(h.body).some(n=>n.textContent.includes('Вход не выбран')));
  }
});

test('QA rejects malformed entrance records, duplicate IDs, invalid bounds and oversized envelopes',()=>{
  const h=setup(),raw={v:1,...message()},parse=h.context.stageOnePlace;
  for(const patch of [{entranceStatus:'official'},{entranceStatus:null},{entranceStatus:'unreviewed'},
    {entrances:[]},{entrances:null},{entrances:{}},{entrances:[null]},{entrances:[entrance,entrance]},
    {title:'x'.repeat(121)},{address:'x'.repeat(203)},{sourceRef:'x'.repeat(81)},{lat:'51.128'}])
    assert.equal(parse({...raw,...patch}),null,JSON.stringify(patch));
  for(const patch of [{id:'way/1'},{id:'node/0'},{id:'node/-1'},{id:'node/1\n'},{id:'node/'+'1'.repeat(96)},
    {id:123},{lat:51.4001},{lat:50.8999},{lng:71.0999},{lng:71.9001},{lat:Infinity},{lat:NaN},
    {lat:'51.1281'},{kind:'official'},{wheelchair:'accessible'}])
    assert.equal(parse({...raw,entrances:[{...entrance,...patch}]}),null,JSON.stringify(patch));
  for(const key of ['id','lat','lng','kind','wheelchair']){
    const e={...entrance};delete e[key];assert.equal(parse({...raw,entrances:[e]}),null,key);
  }
  const serialized=JSON.stringify(raw);
  assert.ok(parse(serialized.padEnd(4096)));assert.equal(parse(serialized.padEnd(4097)),null);
  assert.equal(parse(serialized.slice(0,-1)),null);
  h.emit({...message(),entrances:[{...entrance,lat:0}]});assert.equal(h.dialog(),undefined);
});

test('QA accepts eight entrances, all enum values and inclusive coordinate bounds',()=>{
  const h=setup(),entrances=Array.from({length:8},(_,i)=>({...entrance,id:`node/${i+1}`,
    lat:i%2?50.9:51.4,lng:i%2?71.1:71.9,kind:['main','yes','staircase','home','unknown'][i%5],wheelchair:['yes','no','limited','unknown'][i%4]}));
  h.emit({...message(),entrances});assert.equal(h.entranceButtons().length,8);
  assert.equal(h.context.stageOnePlace({v:1,...message(),entrances:[...entrances,{...entrance,id:'node/9'}]}),null);
});

test('creation helper preserves the original label at the 255-character boundary',()=>{
  const h=setup(),raw=message(),before=JSON.stringify(raw),create=h.context.stageOneCreationPoint;
  assert.equal(create(raw,'node/999').lat,place.lat);
  const long={...raw,address:'a'.repeat(202),title:'t'.repeat(120)};
  assert.equal(create(long,entrance.id).locationLabel,create(long).locationLabel);
  assert.equal(create(long).locationLabel.length,255);
  assert.equal(create(long,entrance.id).lat,entrance.lat);
  const note=' · вход OSM node/123',exact={...long,title:'t'.repeat(255-note.length-205)};
  assert.equal(create(exact,entrance.id).locationLabel,create(exact).locationLabel+note);
  assert.equal(create(exact,entrance.id).locationLabel.length,255);
  assert.equal(JSON.stringify(raw),before);
});
