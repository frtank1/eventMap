import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
// Exercise the actual orchestration with resource stubs: no GPU/DOM substitute
// is claimed as mobile rendering proof. Catch disposal-before-commit regressions.
const source=await readFile(new URL('../runtime/main.js',import.meta.url),'utf8');
const begin=source.indexOf('function activateGeography('),end=source.indexOf('function populatePlaces(',begin);
assert.ok(begin>=0&&end>begin);const activation=source.slice(begin,end);
function fixture(fail){
  const calls=[],resource=name=>({name,group:{name},destroy(){calls.push(`destroy:${name}`);}});
  const oldCity=resource('old-city'),oldQuarter=resource('old-quarter'),newCity=resource('new-city'),newQuarter=resource('new-quarter');
  const context=vm.createContext({calls,geoCatalog:{old:true},geoGeometry:'old-signature',city:oldCity,quarter:oldQuarter,districtId:'baiterek',quarterMode:true,
    districts:new Map([['baiterek',oldQuarter]]),models:[{entry:{id:'baiterek'}}],
    scene:{add(group){calls.push(`add:${group.name}`);}},createWorldStream(){return newCity;},createBaiterekQuarter(){return newQuarter;},
    setQuarterMode(){calls.push('mode');if(fail==='mode')throw Error('mode failure');},appearance(){calls.push('appearance');if(fail==='appearance')throw Error('appearance failure');}});
  vm.runInContext(activation,context);
  return {context,calls,oldCity,oldQuarter,newCity,newQuarter};
}
const ready={places:{places:[{id:'baiterek',review:{status:'matched'},footprints:[]}]}};
test('geography scene activates before old resources are disposed, address-only stays cheap',()=>{
  const x=fixture();x.context.activateGeography(ready);
  assert.equal(x.context.city,x.newCity);assert.equal(x.context.quarter,x.newQuarter);
  assert.ok(x.calls.indexOf('appearance')<x.calls.indexOf('destroy:old-city'));
  const count=x.calls.length;x.context.activateGeography(structuredClone(ready));assert.equal(x.calls.length,count);
});
test('failed geography appearance/mode restores working scene, catalog and resource ownership',()=>{
  for(const fail of ['mode','appearance']){
    const x=fixture(fail);assert.throws(()=>x.context.activateGeography(ready));
    assert.equal(x.context.city,x.oldCity);assert.equal(x.context.quarter,x.oldQuarter);assert.equal(x.context.geoGeometry,'old-signature');
    assert.ok(x.context.geoCatalog.old);assert.equal(x.context.districts.get('baiterek'),x.oldQuarter);
    assert.ok(!x.calls.includes('destroy:old-city')&&!x.calls.includes('destroy:old-quarter'));
    assert.ok(x.calls.includes('destroy:new-city')&&x.calls.includes('destroy:new-quarter'));
  }
});
