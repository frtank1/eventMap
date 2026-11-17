// The dense presentation is an explicitly local visual load test, not a way to
// inject fixtures into production or enlarge the native event contract.
export function debugSceneAllowed(message,{hostname='',native=false,debugPreview=false}={}){
  return message?.preview===true&&((!native&&['localhost','127.0.0.1','[::1]','::1'].includes(hostname))||
    (native&&debugPreview===true&&hostname==='appassets.androidplatform.net'));
}
export function festivalAllowed(message,context={}){
  return message?.density==='festival'&&debugSceneAllowed(message,context);
}
export function eventLabelBudget({festival=false,small=false,quality='balanced'}={}){
  return festival?(quality==='low'?(small?16:32):(small?28:64)):(quality==='low'?5:8);
}
export function eventGroupCell(distance,festival=false){
  return festival?(distance>12000?260:distance>3000?120:distance>1600?75:distance>650?50:25):
    distance>12000?1500:distance>3000?700:distance>1600?500:distance>650?140:35;
}
const thresholds=[650,1600,3000,12000];
export function eventGroupBucket(distance,previous=-1){
  if(!Number.isFinite(distance))return previous>=0&&previous<=4?previous:0;
  if(!Number.isInteger(previous)||previous<0||previous>4)return thresholds.filter(t=>distance>t).length;
  let bucket=previous;
  while(bucket<4&&distance>thresholds[bucket]*1.12)bucket++;
  while(bucket>0&&distance<thresholds[bucket-1]*.88)bucket--;
  return bucket;
}
export const groupDistanceForBucket=bucket=>[0,1000,2000,6000,18000][bucket]??0;
