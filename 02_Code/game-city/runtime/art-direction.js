// EventMap «City of meetings». Shared art constants, not map/attendance data.
// Every LOD and the bundled fallback use the same materials and world scale.
export const ART_VERSION='city-seasons-1';
// Day «golden postcard» (owner's choice 17.11): saturated greens and water, warm
// stone, coloured roofs, lit by a low golden sun under a pink-blue sky.
// Keep the same palette at every distance; night values are intentionally unchanged.
export const CITY_PALETTE=Object.freeze({
  ivory:[0xf4e9d8,0x91a4b6], sand:[0xe6b08a,0x9d8f9f], sage:[0xbcdcc8,0x77989a],
  glass:[0x3a8db4,0x234e6e], roof:[0xc76a4e,0x415c75], trim:[0xfff4e4,0xbdccda],
  land:[0xdcdcc9,0x20394a], urban:[0xe6ddcc,0x304653], grass:[0x67bd6c,0x29534f],
  water:[0x23a3d8,0x18536d], pavement:[0xeee1cc,0x586d85], road:[0x7d8792,0x2a415a],
  kerb:[0xf6ecdd,0x879db4], trunk:[0x8b6446,0x4b5e71], leaf:[0x3d9b5a,0x2b655e],
  gold:[0xe1b956,0xc5ab73], dark:[0x285874,0x223a56], wood:[0xd38c5a,0x8592a6],
  // Exported landmark colors are remapped at presentation time, not regenerated.
  // Neutral ground is not lawn: only mapped green areas become green.
  amber:[0xdba650,0x7a6a52], rooftop:[0xc76a4e,0x415c75], canopy:[0x4cb064,0x2b655e],
  // Green glass of the Emerald Quarter towers.
  emerald:[0x2fb07c,0x23705f],
});
// Coloured roofs, picked per building (backdrop and detailed streamed buildings).
export const ROOF_COLORS=Object.freeze({
  roofTerracotta:[0xc76a4e,0x415c75], roofTeal:[0x3b958c,0x415c75],
  roofSlate:[0x5b80aa,0x415c75], roofSage:[0x79a868,0x415c75],
});
export const ROOF_KEYS=Object.freeze(Object.keys(ROOF_COLORS));
export function roofKeyAt(x,z){const h=Math.sin(x*12.9898+z*78.233)*43758.5453;return ROOF_KEYS[Math.floor((h-Math.floor(h))*ROOF_KEYS.length)];}
// Keep old exported swatches identifiable when only presentation colors change.
// In particular a daytime palette edit must not break the existing night mapping.
const shippedSwatches={ivory:[0xe6cba5,0xf2dfbf],sand:[0xda9b78],sage:[0xb6c8b0],
  glass:[0x4d9190,0x367d90],roof:[0x8da89d,0x618d91],trim:[0xfff0d5],land:[0xd4dbbe],
  urban:[0xe0d3b4],grass:[0x86bd5a],water:[0x35b3c2],pavement:[0xecd3b2],road:[0xa39a8d],
  kerb:[0xf6e4c9],leaf:[0x49885b],amber:[0xdba648],canopy:[0x6aa843]};
export function landmarkSwatch(color){
  return Object.keys(shippedSwatches).find(k=>shippedSwatches[k].includes(color))||
    Object.keys(CITY_PALETTE).find(k=>CITY_PALETTE[k][0]===color);
}
export function landmarkColor(color,night=false){
  const swatch=landmarkSwatch(color);return swatch?CITY_PALETTE[swatch][night?1:0]:color;
}
export function sceneMood(night=false,theme='default'){
  const winter=theme!=='default';
  return {
    // `sky` is the horizon/haze colour; `zenith` is the top of the gradient dome.
    // Day: golden hour — peach-pink horizon, clear blue zenith, warm low sun.
    sky:night?0x1a3157:winter?0xdbe8ee:0xf6cdb6,
    zenith:night?0x07122a:winter?0x9fbcd6:0x4b8cd6,
    cloud:night?0x2a3f63:winter?0xffffff:0xffe3d2,
    land:winter?(night?0x647e93:0xe2e9e6):CITY_PALETTE.land[night?1:0],
    skyLight:night?0x91b7e7:0xc7dbff,groundLight:night?0x25334b:0xa58a6c,
    sun:night?0x9fbeec:0xffbd78,ambient:night?1.08:.66,direct:night?.8:3.1,
    exposure:night?1.08:1.08,
  };
}
// Presentation LOD is independent from tile LOD. Far views never retain giant
// photo balloons; photos remain available in the list at every zoom.
export function eventDisplayLevel(distance,previous='district'){
  if(!Number.isFinite(distance))return 'city';
  if(previous==='city'&&distance>2400)return 'city';
  if(distance>3000)return 'city';
  if(previous==='street'&&distance<550)return 'street';
  if(distance<420)return 'street';
  return 'district';
}

// ---- Time of day, seasons and weather (owner request 17.11) ----------------
// Phases: sunrise · day · sunset · night. Seasons recolour lawns and foliage
// (winter keeps the snow theme). Weather: clear · rain · snow (particles + mood).
export const PHASES=Object.freeze(['sunrise','day','sunset','night']);
export const SEASONS=Object.freeze(['spring','summer','autumn','winter']);
export const WEATHERS=Object.freeze(['clear','cloudy','rain','snow']);
export const SEASON_FOLIAGE=Object.freeze({
  spring:{grass:0x8fd170,leaf:0x62c46b,canopy:0x86d47c},
  summer:{grass:CITY_PALETTE.grass[0],leaf:CITY_PALETTE.leaf[0],canopy:CITY_PALETTE.canopy[0]},
  autumn:{grass:0xb9b067,leaf:0xd97a2c,canopy:0xe3a93b},
});
// Approximate Astana sunrise/sunset, local time (UTC+5), hours per month.
const ASTANA_SUN=[[9.0,17.0],[8.5,17.8],[7.6,18.7],[6.5,19.5],[5.5,20.3],[5.0,21.0],[5.2,20.9],[6.0,20.0],[6.8,18.9],[7.6,17.8],[8.5,17.0],[9.1,16.8]];
function astanaClock(nowMs){const d=new Date(nowMs+5*3600e3);return {month:d.getUTCMonth(),hour:d.getUTCHours()+d.getUTCMinutes()/60};}
export function autoPhase(nowMs=Date.now()){
  const {month,hour}=astanaClock(nowMs),[rise,set]=ASTANA_SUN[month];
  if(hour<rise-.5||hour>=set+.5)return 'night';
  if(hour<rise+1)return 'sunrise';
  if(hour>=set-1)return 'sunset';
  return 'day';
}
export function autoSeason(nowMs=Date.now()){const m=astanaClock(nowMs).month;return m<2||m===11?'winter':m<5?'spring':m<8?'summer':'autumn';}
const mixHex=(a,b,t)=>{const c=(x,s)=>(x>>s)&255,m=s=>Math.round(c(a,s)+(c(b,s)-c(a,s))*t);return (m(16)<<16)|(m(8)<<8)|m(0);};
// Sun direction in scene space (x east, z south, y up) and the light/sky for a phase.
export function atmosphere(phase='day',season='summer',weather='clear'){
  const winter=season==='winter',theme=winter?'winter':'default';
  const base=phase==='night'?{...sceneMood(true,theme),sunOffset:[-380,360,300],glow:0x5f7fb5,envGlow:0x6f8fc5}:
    phase==='sunrise'?{sky:0xf9c6b0,zenith:0x7fa3dc,cloud:0xffe0d4,skyLight:0xe9d6e6,groundLight:0xb59a86,sun:0xffc29a,ambient:.82,direct:2.7,exposure:1.08,sunOffset:[430,200,180],glow:0xffa98a,envGlow:0xffc4a8}:
    phase==='sunset'?{sky:0xf7b98f,zenith:0x5f7ccb,cloud:0xffd2b4,skyLight:0xf0cdb8,groundLight:0xb88a66,sun:0xffa660,ambient:.78,direct:3.0,exposure:1.08,sunOffset:[-420,200,330],glow:0xff9a62,envGlow:0xffb680}:
    {sky:0xcfe6f6,zenith:0x3f8ddf,cloud:0xffffff,skyLight:0xd4e6ff,groundLight:0x93a38a,sun:0xfff0d6,ambient:.74,direct:3.0,exposure:1.08,sunOffset:[-300,520,260],glow:0xfff0c8,envGlow:0xffe8c0};
  const mood={...base,land:winter?(phase==='night'?0x647e93:0xe2e9e6):CITY_PALETTE.land[phase==='night'?1:0]};
  if(winter&&phase!=='night'){mood.sky=mixHex(mood.sky,0xe4edf2,.5);mood.cloud=mixHex(mood.cloud,0xffffff,.5);}
  if(weather!=='clear'){
    // Overcast: grey sky, soft sun, more ambient — rain darker than snow, cloudy lightest.
    const cloudy=weather==='cloudy',grey=weather==='rain'?0x8e9aa7:cloudy?0xb9c3cc:0xc9d3dc,t=phase==='night'?.35:cloudy?.55:.7;
    mood.sky=mixHex(mood.sky,grey,t);mood.zenith=mixHex(mood.zenith,weather==='rain'?0x6b7887:cloudy?0x8fa2b6:0xaab8c6,t);
    mood.cloud=mixHex(mood.cloud,weather==='rain'?0x8d97a3:cloudy?0xd5dbe1:0xe6ebf0,t);mood.glow=mixHex(mood.glow,grey,t);mood.envGlow=mixHex(mood.envGlow,grey,t);
    mood.direct*=phase==='night'?.8:cloudy?.55:.35;mood.ambient*=phase==='night'?1:weather==='rain'?1.08:1.25;mood.exposure*=weather==='rain'?.88:1.02;
  }
  return mood;
}
