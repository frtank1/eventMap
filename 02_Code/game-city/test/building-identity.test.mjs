import test from 'node:test';
import assert from 'node:assert/strict';
import {buildingIdentity,placeMessage} from '../runtime/place-addresses.js';
test('building IDs are stable identities, never local tile IDs, address nodes or ground points',()=>{
  assert.equal(buildingIdentity({id:'mega',geometryKind:'stylized'}),'landmark:mega');
  assert.equal(buildingIdentity({id:'tile-42',geometryKind:'footprint',sourceRef:'osm:way/123'}),'osm:way:123');
  assert.equal(buildingIdentity({id:'tile-42',geometryKind:'footprint',sourceRef:'osm:relation/321'}),'osm:relation:321');
  for(const p of [{id:'tile-42',geometryKind:'footprint'},{id:'mega',geometryKind:'point'},
    {geometryKind:'footprint',sourceRef:'osm:node/123'},{id:'../../x',geometryKind:'stylized'}])assert.equal(buildingIdentity(p),null);
  assert.equal(placeMessage({id:'tile-42',geometryKind:'footprint'}, {buildingId:'landmark:mega'}).buildingId,'landmark:mega');
});
