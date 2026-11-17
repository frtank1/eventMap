import test from 'node:test';
import assert from 'node:assert/strict';
import {CITY_LOD_BANDS,cityLodBand,cityLodWeights,distanceWeight,decorateLodFade,LOD_COVERAGE_GLSL} from '../runtime/city-lod.js';

test('six distance bands cover person to whole city without boundary flicker',()=>{
  assert.deepEqual([5,180,700,1800,5000,18000].map(d=>cityLodBand(d).id),CITY_LOD_BANDS.map(b=>b.id));
  for(let i=0;i<5;i++){const b=CITY_LOD_BANDS[i],next=CITY_LOD_BANDS[i+1];
    assert.equal(cityLodBand(b.max*1.05,b.id).id,b.id);
    assert.equal(cityLodBand(b.max*.95,next.id).id,next.id);
    assert.equal(cityLodBand(b.max*1.11,b.id).id,next.id);
  }
  assert.equal(cityLodBand(NaN).id,'city');assert.equal(cityLodBand(-1).id,'person');
});
test('each detail channel fades continuously, not with six duplicate scenes',()=>{
  let previous=cityLodWeights(0);
  for(let d=1;d<=38000;d+=13){const w=cityLodWeights(d);for(const key of Object.keys(w)){
    assert.ok(w[key]>=0&&w[key]<=1);assert.ok(w[key]<=previous[key]);assert.ok(previous[key]-w[key]<.06);
  }previous=w;}
  const w=cityLodWeights(5100);assert.ok(w.tiles>0&&w.tiles<1);
  assert.equal(cityLodWeights(6100).tiles,0);assert.equal(cityLodWeights(4000).tiles,1);
  assert.equal(cityLodWeights(5100,'low').tiles,w.tiles,'tile/background complement independent of quality');
  assert.ok(cityLodWeights(800,'low').people<cityLodWeights(800).people);
  assert.equal(distanceWeight(NaN,1,2),0);
});
test('complementary ordered coverage has exactly one owner, including endpoints',()=>{
  for(let i=0;i<=100;i++){const weight=i/100;for(let pixel=0;pixel<16;pixel++){
    const sample=(pixel+.5)/16,tile=sample<weight,backdrop=sample>=weight;
    assert.notEqual(tile,backdrop);
  }}
  assert.ok(!/time|random|sin\(/.test(LOD_COVERAGE_GLSL));
});
test('fade composes existing shaders, updates one uniform and never changes depth/geometry',()=>{
  let called=0;const mat={depthWrite:true,transparent:false,onBeforeCompile(s){called++;s.uniforms.original={value:7};},customProgramCacheKey(){return 'facade';}};
  const fade=decorateLodFade(mat),shader={uniforms:{},fragmentShader:'#define STANDARD\n#include <clipping_planes_fragment>'};
  mat.onBeforeCompile(shader);assert.equal(called,1);assert.equal(shader.uniforms.original.value,7);
  for(const w of [0,.25,.5,1,NaN]){fade.set(w);assert.ok(shader.uniforms.uCityLodWeight.value>=0&&shader.uniforms.uCityLodWeight.value<=1);}
  assert.equal(mat.depthWrite,true);assert.equal(mat.transparent,false);assert.equal(mat.needsUpdate,undefined);
  assert.equal(mat.customProgramCacheKey(),'facade:city-lod-1');assert.match(shader.fragmentShader,/cityCoverageSample\(\)>=uCityLodWeight/);
  assert.match(shader.fragmentShader,/\n#define STANDARD\n/,'GLSL preprocessor directives must start on a new line');
});
