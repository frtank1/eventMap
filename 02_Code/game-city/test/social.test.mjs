import test from 'node:test';
import assert from 'node:assert/strict';
import {defaultLook,validateLook,visibleEvents,groupEvents,labelsOverlap} from '../runtime/social-state.js';
const event=(id,extra={})=>({id,lat:51.128,lng:71.43,title:'Футбол',...extra});
test('appearance is bounded and strips foreign fields',()=>{
  assert.deepEqual(validateLook({...defaultLook,token:'never',lat:51}),defaultLook);
  for(const invalid of [null,{}, {...defaultLook,v:2},{...defaultLook,coat:99},{...defaultLook,skin:-1},{...defaultLook,hair:.5},{...defaultLook,bag:'true'}])assert.equal(validateLook(invalid),null);
  // Looks saved before trousers/shoes/glasses/height/build existed stay valid.
  assert.deepEqual(validateLook({v:1,skin:2,hair:1,coat:3,style:1,bag:false}),{...defaultLook,skin:2,hair:1,coat:3,style:1,bag:false});
  for(const invalid of [{...defaultLook,pants:9},{...defaultLook,glasses:1},{...defaultLook,height:-1}])assert.equal(validateLook(invalid),null);
});
test('only valid, unique and unexpired events enter the city',()=>{
  const now=Date.parse('2026-11-12T10:00:00Z');
  const data=[event('a'),event('a'),event('b',{status:'ended'}),event('c',{endAt:'2026-11-11T10:00:00Z'}),event('d',{expiresAtMs:now-1}),event('e',{lat:NaN}),event('f',{lng:10}),event('g',{status:'cancelled'}),event('future',{endAt:'2026-12-01T10:00:00Z'})];
  assert.deepEqual(visibleEvents(data,now).map(e=>e.id),['a','future']);
  assert.equal(visibleEvents([event('x',{goingCount:-4,title:'x'.repeat(400)})])[0].title.length,120);
  assert.equal(visibleEvents([event('x',{goingCount:-4})])[0].goingCount,0);
  assert.equal(visibleEvents(Array.from({length:500},(_,i)=>event(''+i))).length,150);
});
test('zoom groups preserve all events and split when approaching',()=>{
  const events=visibleEvents([event('a'),event('b',{lng:71.431}),event('c',{lng:71.4315})]);
  const far=groupEvents(events,2000),near=groupEvents(events,100);
  assert(far.length<=near.length);assert.equal(far.flatMap(x=>x.events).length,3);assert.equal(near.flatMap(x=>x.events).length,3);
  assert(groupEvents([],100).length===0);
});
test('label collision includes a readable margin',()=>{
  assert(labelsOverlap({x:0,y:0,w:100,h:40},{x:104,y:0,w:100,h:40}));
  assert(!labelsOverlap({x:0,y:0,w:100,h:40},{x:115,y:0,w:100,h:40}));
});
