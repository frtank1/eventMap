// EventMap «City of meetings». Shared art constants, not map/attendance data.
// Every LOD and the bundled fallback use the same materials and world scale.
export const ART_VERSION='nurjol-garden-2';
export const CITY_PALETTE=Object.freeze({
  ivory:[0xf2dfbf,0x91a4b6], sand:[0xda9b78,0x9d8f9f], sage:[0xb6c8b0,0x77989a],
  glass:[0x367d90,0x234e6e], roof:[0x618d91,0x415c75], trim:[0xfff0d5,0xbdccda],
  land:[0xa8ba91,0x20394a], urban:[0xd6ceb2,0x304653], grass:[0x76a469,0x29534f],
  water:[0x30a3ad,0x18536d], pavement:[0xe8ceb1,0x586d85], road:[0x687e82,0x2a415a],
  kerb:[0xf6e4c9,0x879db4], trunk:[0x816246,0x4b5e71], leaf:[0x49885b,0x2b655e],
  gold:[0xe1b956,0xc5ab73], dark:[0x294e60,0x223a56], wood:[0xc58b5e,0x8592a6],
});
export function sceneMood(night=false,theme='default'){
  const winter=theme!=='default';
  return {
    sky:night?0x12243f:winter?0xdbe8ee:0xa7d5e3,
    land:winter?(night?0x647e93:0xe2e9e6):CITY_PALETTE.land[night?1:0],
    skyLight:night?0x91b7e7:0xe0f1ff,groundLight:night?0x25334b:0x748c69,
    sun:night?0x9fbeec:0xffe3b2,ambient:night?1.08:1.16,direct:night?.8:2.1,
    exposure:night?1.08:.98,
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
