// OpenMapTiles writes render_height = 5 m for every building that has no
// height or levels tag in OpenStreetMap, which is most of Astana. Drawn
// literally, blocks of flats become one-storey sheds. For exactly that default
// we estimate a plausible height from the footprint shape.
// This is a PRESENTATION ESTIMATE, not building data: a tagged height or level
// count is never changed, and nothing here is exposed as a fact to the user.
export const OMT_DEFAULT_HEIGHT=5;
export const ESTIMATE=Object.freeze({shed:4,lowRise:9,midRise:16,highRise:27});

function hull(points){
  const pts=[...points].sort((a,b)=>a[0]-b[0]||a[1]-b[1]);if(pts.length<3)return pts;
  const cross=(o,a,b)=>(a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0]),lower=[],upper=[];
  for(const p of pts){while(lower.length>1&&cross(lower.at(-2),lower.at(-1),p)<=0)lower.pop();lower.push(p);}
  for(let i=pts.length-1;i>=0;i--){const p=pts[i];while(upper.length>1&&cross(upper.at(-2),upper.at(-1),p)<=0)upper.pop();upper.push(p);}
  return lower.slice(0,-1).concat(upper.slice(0,-1));
}
// Long and short side of the minimum-area enclosing rectangle, plus area, in metres.
export function footprintSize(ring,xScale=1,zScale=1){
  const h=hull(ring.map(([x,z])=>[x*xScale,z*zScale]));let area=0,long=0,short=0,best=Infinity;
  for(let i=0;i<h.length;i++){const a=h[i],b=h[(i+1)%h.length];area+=a[0]*b[1]-b[0]*a[1];}
  for(let i=0;i<h.length;i++){
    const a=h[i],b=h[(i+1)%h.length],len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<.01)continue;
    const c=(b[0]-a[0])/len,s=(b[1]-a[1])/len;let u0=Infinity,u1=-Infinity,v0=Infinity,v1=-Infinity;
    for(const [x,z]of h){const u=x*c+z*s,v=-x*s+z*c;u0=Math.min(u0,u);u1=Math.max(u1,u);v0=Math.min(v0,v);v1=Math.max(v1,v);}
    const w=u1-u0,d=v1-v0;if(w*d<best){best=w*d;long=Math.max(w,d);short=Math.min(w,d);}
  }
  return {long,short,area:Math.abs(area)/2};
}
// Within this many landmark radii a building keeps its source height.
export const LANDMARK_CLEARANCE=1.25;
export function nearLandmark(x,z,landmarks){return landmarks.some(l=>Math.hypot(x-l.x,z-l.z)<l.radius*LANDMARK_CLEARANCE);}
export function estimatedTop(top,base,ring,xScale=1,zScale=1,keep=false){
  if(keep||top!==OMT_DEFAULT_HEIGHT||base>0||!Array.isArray(ring)||ring.length<3)return top;
  const {long,short,area}=footprintSize(ring,xScale,zScale);
  if(!(area>0))return top;
  if(area<120)return ESTIMATE.shed;                                   // garages, kiosks, cottages
  if(short>=9&&short<=22&&long>=60)return ESTIMATE.highRise;           // long slab of flats
  if(short>=9&&short<=22&&long>=24)return ESTIMATE.midRise;            // shorter block of flats
  return ESTIMATE.lowRise;                                             // halls, malls, irregular shapes
}
