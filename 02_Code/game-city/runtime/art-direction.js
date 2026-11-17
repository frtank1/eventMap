// EventMap «City of meetings». Shared art constants, not map/attendance data.
// Every LOD and the bundled fallback use the same materials and world scale.
export const ART_VERSION='city-day-diorama-2';
// Day: warm stone, blue-slate roofs, jade parks and a deep river. Keep the
// same palette at every distance; night values are intentionally unchanged.
export const CITY_PALETTE=Object.freeze({
  ivory:[0xe8e0d4,0x91a4b6], sand:[0xcaa48e,0x9d8f9f], sage:[0xb6c5bf,0x77989a],
  glass:[0x426e85,0x234e6e], roof:[0x657f94,0x415c75], trim:[0xf4ecde,0xbdccda],
  land:[0xd0d7ce,0x20394a], urban:[0xd9d2c5,0x304653], grass:[0x83a98c,0x29534f],
  water:[0x398b9d,0x18536d], pavement:[0xe2d7c4,0x586d85], road:[0x8b9398,0x2a415a],
  kerb:[0xede5d7,0x879db4], trunk:[0x816246,0x4b5e71], leaf:[0x4d846e,0x2b655e],
  gold:[0xe1b956,0xc5ab73], dark:[0x294e60,0x223a56], wood:[0xc58b5e,0x8592a6],
  // Exported landmark colors are remapped at presentation time, not regenerated.
  // Neutral ground is not lawn: only mapped green areas become green.
  amber:[0xc69e59,0x7a6a52], rooftop:[0x657f94,0x415c75], canopy:[0x5b9477,0x2b655e],
});
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
    sky:night?0x1a3157:winter?0xdbe8ee:0xd9e7e9,
    zenith:night?0x07122a:winter?0x9fbcd6:0x80b2cf,
    cloud:night?0x2a3f63:0xffffff,
    land:winter?(night?0x647e93:0xe2e9e6):CITY_PALETTE.land[night?1:0],
    skyLight:night?0x91b7e7:0xd5e4f2,groundLight:night?0x25334b:0x879a99,
    sun:night?0x9fbeec:0xffeed9,ambient:night?1.08:.85,direct:night?.8:2.35,
    exposure:night?1.08:1.12,
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
