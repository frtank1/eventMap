import {frameExclusion,isFrameInterval,summarizeFrames} from './frame-governor.js';

// Opt-in debug camera sweep, not a synthetic touch-gesture test. Only actual
// renderer intervals enter the report. Never packaged fixture data or API calls.
export const ZOOM_PHASES=Object.freeze([
  {id:'warmup',ms:3000},{id:'city',ms:3000},{id:'zoom-in',ms:8000},{id:'near',ms:4000},{id:'zoom-out',ms:8000},
]);
const duration=ZOOM_PHASES.reduce((n,p)=>n+p.ms,0);
const ease=t=>t*t*(3-2*t);
export function zoomPose(elapsed){
  let start=0;
  for(const phase of ZOOM_PHASES){if(elapsed<start+phase.ms||phase===ZOOM_PHASES.at(-1)){
    const t=Math.min(1,Math.max(0,(elapsed-start)/phase.ms));
    const approach=phase.id==='zoom-in'?ease(t):phase.id==='near'?1:phase.id==='zoom-out'?1-ease(t):0;
    return {phase:phase.id,distance:Math.exp(Math.log(18000)*(1-approach)+Math.log(180)*approach),targetZ:-1000*(1-approach),targetY:38*approach,azimuth:.28+.3*approach,elevation:.72-.17*approach};
  }start+=phase.ms;}
}
export function createZoomBenchmark(){
  let elapsed=0,status='running',reason='',lastPhase='warmup';const frames=[];
  return {
    get running(){return status==='running';},get progress(){return Math.min(1,elapsed/duration);},pose:()=>zoomPose(elapsed),
    cancel(value='cancelled'){if(status==='running'){status='cancelled';reason=value;}},
    sample(frame={}){
      if(status!=='running')return;
      const exclusion=frameExclusion(frame);if(exclusion){status='cancelled';reason=exclusion;return;}
      if(!isFrameInterval(frame.dtMs))return;
      lastPhase=zoomPose(elapsed).phase;elapsed+=lastPhase==='warmup'?Math.min(frame.dtMs,100):frame.dtMs;
      if(lastPhase!=='warmup')frames.push({phase:lastPhase,dtMs:frame.dtMs,drawCalls:frame.drawCalls,triangles:frame.triangles,quality:frame.quality,pixelRatio:frame.pixelRatio});
      if(elapsed>=duration){status=ZOOM_PHASES.filter(p=>p.id!=='warmup').every(p=>frames.some(f=>f.phase===p.id))?'complete':'incomplete';if(status==='incomplete')reason='skipped-phase';}
      if(frames.length>=3000){status='cancelled';reason='sample-limit';}
    },
    report(){const perPhase=Object.fromEntries(ZOOM_PHASES.filter(p=>p.id!=='warmup').map(p=>[p.id,summarizeFrames(frames.filter(f=>f.phase===p.id).map(f=>f.dtMs))]));
      const valid=key=>frames.map(f=>f[key]).filter(Number.isFinite);
      return {status,reason,measurement:'rendered-frame-intervals',elapsedMs:elapsed,frameTiming:summarizeFrames(frames.map(f=>f.dtMs)),phases:perPhase,
        maxDrawCalls:Math.max(0,...valid('drawCalls')),maxTriangles:Math.max(0,...valid('triangles')),
        quality:[...new Set(frames.map(f=>f.quality))],pixelRatio:[...new Set(valid('pixelRatio'))],note:'Camera sweep; gesture and tap checks are separate.'};
    },
  };
}
