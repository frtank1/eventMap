// Product viewport, not an administrative boundary or a claim of complete OSM data.
export const WORLD_BOUNDS=Object.freeze({west:71.10,east:71.85,south:50.95,north:51.40});
export const WORLD_CENTER=Object.freeze([71.43056,51.12822]);
export const METERS=111320;
export const LON_SCALE=METERS*Math.cos(WORLD_CENTER[1]*Math.PI/180);
export const CITY_AREAS=Object.freeze([
  {id:'north-city',title:'Север города',lng:71.43,lat:51.205},
  {id:'east-city',title:'Восток города',lng:71.57,lat:51.145},
  {id:'west-city',title:'Запад города',lng:71.33,lat:51.16},
]);
export function worldPosition(lng,lat){return {x:(lng-WORLD_CENTER[0])*LON_SCALE,z:-(lat-WORLD_CENTER[1])*METERS};}
export function containsCoordinate(lng,lat){return Number.isFinite(lng)&&Number.isFinite(lat)&&lng>=WORLD_BOUNDS.west&&lng<=WORLD_BOUNDS.east&&lat>=WORLD_BOUNDS.south&&lat<=WORLD_BOUNDS.north;}
export function clampTarget(target){
  const a=worldPosition(WORLD_BOUNDS.west,WORLD_BOUNDS.north),b=worldPosition(WORLD_BOUNDS.east,WORLD_BOUNDS.south);
  target.x=Math.max(a.x,Math.min(b.x,target.x));target.z=Math.max(a.z,Math.min(b.z,target.z));return target;
}
export function fallbackDistrict(target,distance,centers,covered){
  if(covered||distance>1400)return null;
  return centers.find(c=>Math.hypot(target.x-c.x,target.z-c.z)<420)?.id||null;
}
// Authored Baiterek remains visible after the network finishes. A hysteresis
// band prevents repeatedly swapping meshes while pinching at the boundary.
// Snapshot radius is 1.2 km; only retain it while the near viewport fits.
export function referenceDistrict(target,distance,previous=false){
  const radius=previous?360:300,zoom=previous?1000:860;
  return Number.isFinite(distance)&&distance<zoom&&Math.hypot(target.x,target.z)<radius?'baiterek':null;
}
