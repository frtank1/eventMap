// Device-independent budgets, deliberately conservative until physical-device profiling.
export const QUARTER_CENTER = Object.freeze([71.43056, 51.12822]);
// Distinct ground layers prevent coplanar roads, paths and green polygons.
export const GROUND = Object.freeze({grass:-.1,water:.04,contact:.02,plaza:.12,kerb:.22,path:.36,road:.6,lamp:.66});
export function cameraDepth(distance,quarter=true,continuousCity=false){
  const d=Number.isFinite(distance)?Math.max(0,distance):500;
  return {near:Math.max(.12,Math.min(quarter?4:continuousCity?512:32,d/90)),far:continuousCity?80000:quarter?6500:40000};
}
export const QUALITY = Object.freeze({
  low: {dpr:1, snow:320, sparks:160, trees:180},
  balanced: {dpr:1.35, snow:700, sparks:300, trees:320},
});
export function detailLevel(distance, previous='near') {
  if (!Number.isFinite(distance)) return 'far';
  if (previous==='near' && distance<680) return 'near';
  if (previous==='far' && distance>1400) return 'far';
  if (previous==='block' && distance>=520 && distance<=1600) return 'block';
  return distance<600?'near':distance>1500?'far':'block';
}
export function effectState(value={}) {
  return {light:value.light===true,fireworks:value.fireworks===true,snow:value.snow===true};
}
export function effectBudget({quality='balanced',distance=0,visible=true,reduced=false,blocked=false}={}) {
  const q=QUALITY[quality]||QUALITY.balanced;
  const active=visible&&!blocked&&!reduced&&Number.isFinite(distance)&&distance<1800;
  return {snow:active?q.snow:0,sparks:active?q.sparks:0,active};
}
export function frameSummary(values) {
  const samples=values.filter(v=>Number.isFinite(v)&&v>0).sort((a,b)=>a-b);
  if(!samples.length)return {p95Ms:0,slowFrames:0,samples:0};
  return {p95Ms:Math.round(samples[Math.min(samples.length-1,Math.ceil(samples.length*.95)-1)]*10)/10,
    slowFrames:samples.filter(v=>v>50).length,samples:samples.length};
}
