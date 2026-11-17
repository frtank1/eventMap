import test from 'node:test';
import assert from 'node:assert/strict';
import {atmosphere,autoPhase,autoSeason,PHASES,SEASONS,WEATHERS,SEASON_FOLIAGE,sceneMood} from '../runtime/art-direction.js';

// Astana local wall-clock → epoch ms (UTC+5).
const astana=(y,m,d,h,min=0)=>Date.UTC(y,m-1,d,h-5,min);

test('auto phase follows Astana sunrise and sunset through the year',()=>{
  assert.equal(autoPhase(astana(2026,11,17,3)),'night');
  assert.equal(autoPhase(astana(2026,11,17,8,40)),'sunrise');
  assert.equal(autoPhase(astana(2026,11,17,13)),'day');
  assert.equal(autoPhase(astana(2026,11,17,17)),'sunset');
  assert.equal(autoPhase(astana(2026,11,17,20)),'night');
  // June: long days, late sunset.
  assert.equal(autoPhase(astana(2026,6,20,19)),'day');
  assert.equal(autoPhase(astana(2026,6,20,21)),'sunset');
});

test('auto season follows the Astana calendar',()=>{
  assert.equal(autoSeason(astana(2026,1,15,12)),'winter');
  assert.equal(autoSeason(astana(2026,4,15,12)),'spring');
  assert.equal(autoSeason(astana(2026,7,15,12)),'summer');
  assert.equal(autoSeason(astana(2026,10,15,12)),'autumn');
  assert.equal(autoSeason(astana(2026,12,15,12)),'winter');
});

test('every phase, season and weather gives a complete, bounded mood',()=>{
  const keys=['sky','zenith','cloud','skyLight','groundLight','sun','land','glow','envGlow'];
  for(const p of PHASES)for(const s of SEASONS)for(const w of WEATHERS){
    const m=atmosphere(p,s,w);
    for(const k of keys)assert.ok(Number.isInteger(m[k])&&m[k]>=0&&m[k]<=0xffffff,`${p}/${s}/${w} ${k}`);
    for(const k of ['ambient','direct','exposure'])assert.ok(m[k]>0&&m[k]<6,`${p}/${s}/${w} ${k}`);
    assert.equal(m.sunOffset.length,3);assert.ok(m.sunOffset[1]>0,'sun stays above the horizon for shadows');
  }
  // Night is the art-directed night mood the city was tuned for.
  assert.equal(atmosphere('night','summer','clear').sky,sceneMood(true,'default').sky);
  // Low sun at sunrise/sunset: long shadows; sunrise in the east, sunset in the west.
  const [rise,set,day]=['sunrise','sunset','day'].map(p=>atmosphere(p,'summer','clear').sunOffset);
  assert.ok(rise[1]<day[1]&&set[1]<day[1]);assert.ok(rise[0]>0&&set[0]<0);
  // Rain dims the direct sun.
  assert.ok(atmosphere('day','summer','rain').direct<atmosphere('day','summer','clear').direct);
  for(const s of ['spring','summer','autumn'])for(const k of ['grass','leaf','canopy'])assert.ok(Number.isInteger(SEASON_FOLIAGE[s][k]));
});
