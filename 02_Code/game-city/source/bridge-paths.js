// Source-line ownership and joining, not inferred bridges at water crossings.
const key=p=>`${Math.round(p[0]*2)},${Math.round(p[1]*2)}`;
export function clipBridgeLine(line,rect){
  const out=[];let current=[];
  for(let i=1;i<line.length;i++){
    const a=line[i-1],b=line[i],dx=b[0]-a[0],dz=b[1]-a[1];let lo=0,hi=1,valid=true;
    for(const [p,q]of [[-dx,a[0]-rect[0]],[dx,rect[2]-a[0]],[-dz,a[1]-rect[1]],[dz,rect[3]-a[1]]]){
      if(p===0){if(q<0)valid=false;continue;}const t=q/p;if(p<0)lo=Math.max(lo,t);else hi=Math.min(hi,t);
    }
    if(!valid||lo>=hi){if(current.length>1)out.push(current);current=[];continue;}
    const start=[a[0]+lo*dx,a[1]+lo*dz].map(x=>Math.round(x*2)/2),end=[a[0]+hi*dx,a[1]+hi*dz].map(x=>Math.round(x*2)/2);
    if(key(start)===key(end))continue;
    if(current.length&&key(current.at(-1))!==key(start)){if(current.length>1)out.push(current);current=[];}
    if(!current.length)current.push(start);current.push(end);
  }
  if(current.length>1)out.push(current);return out;
}
export function joinBridgePaths(rows){
  const unique=new Map();
  for(const r of rows){const f=r.points.map(key).join(';'),b=[...r.points].reverse().map(key).join(';'),id=`${r.kind}:${r.layer}:${r.width}:${f<b?f:b}`;unique.set(id,r);}
  const input=[...unique.values()],ends=new Map();
  input.forEach((r,i)=>{for(const p of [r.points[0],r.points.at(-1)]){const k=key(p);if(!ends.has(k))ends.set(k,[]);ends.get(k).push(i);}});
  const used=new Set(),result=[];
  for(let i=0;i<input.length;i++){
    if(used.has(i))continue;used.add(i);const original=input[i],points=original.points.map(p=>[...p]);
    for(let side=0;side<2;side++){
      if(side)points.reverse();
      while(points.length<4096){
        const at=key(points.at(-1)),near=ends.get(at);if(near?.length!==2)break;
        const next=near.find(j=>!used.has(j));if(next===undefined)break;
        const n=input[next];if(n.kind!==original.kind||n.layer!==original.layer||n.width!==original.width)break;
        const line=key(n.points[0])===at?n.points:[...n.points].reverse();
        if(points.length+line.length>4096)break;used.add(next);points.push(...line.slice(1));
      }
    }
    result.push({...original,points,ramps:[ends.get(key(points[0]))?.length===1,ends.get(key(points.at(-1)))?.length===1]});
  }
  return result;
}
