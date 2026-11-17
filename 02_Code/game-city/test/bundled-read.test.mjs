import test from 'node:test';
import assert from 'node:assert/strict';
import {readBundledJSON} from '../runtime/place-addresses.js';

test('large local overview gets an explicit bounded deadline; ordinary reads default to 90 s (phones)',async t=>{
  const timers=[];
  t.mock.method(globalThis,'setTimeout',(_,ms)=>{timers.push(ms);return 1;});
  t.mock.method(globalThis,'clearTimeout',()=>{});
  t.mock.method(globalThis,'fetch',async()=>new Response('{"ok":true}'));
  assert.deepEqual(await readBundledJSON('overview.json',16*1024*1024,{timeoutMs:150000}),{ok:true});
  assert.deepEqual(await readBundledJSON('places.json'),{ok:true});
  assert.deepEqual(timers,[150000,90000]);
  for(const timeoutMs of [0,NaN,180001,Infinity])await assert.rejects(readBundledJSON('overview.json',100,{timeoutMs}),/timeout/);
});
test('longer overview deadline retains size, HTTP and JSON validation',async t=>{
  t.mock.method(globalThis,'fetch',async()=>new Response('123456789'));
  await assert.rejects(readBundledJSON('overview.json',4,{timeoutMs:30000}),/budget/);
  globalThis.fetch=async()=>new Response('',{status:404});
  await assert.rejects(readBundledJSON('overview.json',100,{timeoutMs:30000}),/unavailable/);
  globalThis.fetch=async()=>new Response('bad-json');
  await assert.rejects(readBundledJSON('overview.json',100,{timeoutMs:30000}),SyntaxError);
});
