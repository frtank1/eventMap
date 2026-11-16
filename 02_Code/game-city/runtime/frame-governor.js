// Deterministic, browser/Three-free policy. All durations are MILLISECONDS.
// Feed uncapped intervals between rendered frames, not the animation dt (seconds)
// and not every RAF callback: main.js deliberately renders at at most 30 Hz.
export const PIXEL_RATIO_LIMITS = Object.freeze({low:1, balanced:1.35});
export const FRAME_GOVERNOR_DEFAULTS = Object.freeze({
  windowSize:120, minSamples:45, warmupMs:3000, resumeWarmupMs:1000,
  emaTimeConstantMs:1000, slowEmaMs:42, slowP95Ms:45,
  slowFrameMs:42, slowRatio:.5, sustainedSlowMs:3000, cooldownMs:8000,
});

const positive = n => Number.isFinite(n) && n > 0;
export const isFrameInterval = n => positive(n) && n <= 60000;
const dprValue = n => positive(n) ? n : 1;
const qualityValue = q => {
  if (!Object.hasOwn(PIXEL_RATIO_LIMITS,q)) throw new RangeError('Unknown quality');
  return q;
};
const modeValue = m => m === 'auto' ? m : qualityValue(m);

export function frameExclusion({active=true, blocked=false, warmingUp=false,
  hidden=false, reducedMotion=false}={}) {
  return !active ? 'inactive' : hidden ? 'hidden' : blocked ? 'blocked' :
    reducedMotion ? 'reduced-motion' : warmingUp ? 'warming-up' : null;
}

// Null, not an invented 0/30/60 FPS, denotes an empty measurement.
export function summarizeFrames(values) {
  const sorted = values.filter(isFrameInterval).sort((a,b) => a-b);
  const durationMs = sorted.reduce((sum,n) => sum+n,0), samples = sorted.length;
  return {samples, durationMs, fps:samples ? samples*1000/durationMs : null,
    meanMs:samples ? durationMs/samples : null,
    p95Ms:samples ? sorted[Math.ceil(samples*.95)-1] : null,
    slowFrames:sorted.filter(n => n>50).length};
}

export function createFrameGovernor(options={}) {
  const config = {...FRAME_GOVERNOR_DEFAULTS};
  for (const key of Object.keys(config)) if (options[key] !== undefined) config[key]=options[key];
  for (const [key,value] of Object.entries(config)) {
    if (!Number.isFinite(value) || value<0) throw new RangeError(`Invalid ${key}`);
  }
  if (!Number.isInteger(config.windowSize) || config.windowSize<1 || config.windowSize>3600 ||
      !Number.isInteger(config.minSamples) || config.minSamples<1 || config.minSamples>config.windowSize ||
      !positive(config.emaTimeConstantMs) || !positive(config.slowEmaMs) ||
      !positive(config.slowP95Ms) || !positive(config.slowFrameMs) ||
      !positive(config.sustainedSlowMs) || config.slowRatio<=0 || config.slowRatio>1)
    throw new RangeError('Invalid frame governor policy');

  let mode=modeValue(options.mode ?? 'auto');
  let quality=mode==='auto' ? qualityValue(options.initialQuality ?? 'balanced') : mode;
  let deviceDpr=dprValue(options.devicePixelRatio);
  let ring=new Array(config.windowSize), sorted=[], cursor=0, total=0, ema=null;
  let slowCount=0, over50=0, elapsed=0, slowMs=0, lastChange=0;
  let warmup=config.warmupMs, resumePending=false;
  let accepted=false, changed=false, reason='reset';
  const pixelRatio = () => Math.min(deviceDpr,PIXEL_RATIO_LIMITS[quality]);
  const indexOf = value => {
    let lo=0, hi=sorted.length;
    while (lo<hi) { const mid=(lo+hi)>>>1; if (sorted[mid]<value) lo=mid+1; else hi=mid; }
    return lo;
  };
  function clearMeasurements() {
    ring=new Array(config.windowSize); sorted=[]; cursor=0; total=0; ema=null;
    slowCount=0; over50=0; slowMs=0;
  }
  function snapshot() {
    const samples=sorted.length;
    return {mode, quality, pixelRatio:pixelRatio(), accepted, changed, reason,
      warmingUp:warmup>0 || resumePending,
      cooldownRemainingMs:Math.max(0,config.cooldownMs-(elapsed-lastChange)),
      slowDurationMs:slowMs,
      frameTiming:{samples, durationMs:total, fps:samples ? samples*1000/total : null,
        meanMs:samples ? total/samples : null, emaMs:ema,
        p95Ms:samples ? sorted[Math.ceil(samples*.95)-1] : null,
        slowFrames:over50, slowRatio:samples ? slowCount/samples : 0}};
  }
  function sample(dtMs, flags={}) {
    accepted=false; changed=false;
    const exclusion=frameExclusion(flags);
    if (exclusion || !isFrameInterval(dtMs)) {
      reason=exclusion || 'invalid-interval'; slowMs=0; resumePending=true;
      return snapshot();
    }
    if (resumePending) {
      // The first interval after a pause can span the entire hidden/background gap.
      resumePending=false; clearMeasurements(); warmup=Math.max(warmup,config.resumeWarmupMs);
      reason='resume-boundary'; return snapshot();
    }
    // A lone stall never burns seconds of warmup, cooldown or sustained evidence.
    // Its full duration is still retained in telemetry, once out of warmup.
    const evidenceMs=Math.min(dtMs,100); elapsed+=evidenceMs;
    if (warmup>0) { warmup=Math.max(0,warmup-evidenceMs); reason='warming-up'; return snapshot(); }
    if (sorted.length===config.windowSize) {
      const old=ring[cursor]; sorted.splice(indexOf(old),1); total-=old;
      if (old>config.slowFrameMs) slowCount--; if (old>50) over50--;
    }
    ring[cursor]=dtMs; cursor=(cursor+1)%config.windowSize;
    sorted.splice(indexOf(dtMs),0,dtMs); total+=dtMs;
    if (dtMs>config.slowFrameMs) slowCount++; if (dtMs>50) over50++;
    const alpha=1-Math.exp(-evidenceMs/config.emaTimeConstantMs);
    ema=ema===null ? dtMs : ema+alpha*(dtMs-ema);
    accepted=true; reason='sampled';
    const enough=sorted.length>=config.minSamples;
    const slow=enough && ema>config.slowEmaMs &&
      sorted[Math.ceil(sorted.length*.95)-1]>config.slowP95Ms &&
      slowCount/sorted.length>=config.slowRatio;
    slowMs=slow ? slowMs+evidenceMs : 0;
    if (mode==='auto' && quality==='balanced' && slowMs>=config.sustainedSlowMs &&
        elapsed-lastChange>=config.cooldownMs) {
      quality='low'; lastChange=elapsed; slowMs=0; changed=true; reason='sustained-slow';
      // Deliberately latched. A cheaper scene being fast is not proof balanced
      // would be fast. Only explicit reset/selection can raise quality again.
    }
    return snapshot();
  }
  function reset(next={}) {
    // Validate first so an invalid request cannot partially mutate the policy.
    const nextMode=modeValue(next.mode ?? mode);
    const nextQuality=qualityValue(next.quality ?? quality);
    mode=nextMode; quality=mode==='auto' ? nextQuality : mode;
    if (next.devicePixelRatio!==undefined) deviceDpr=dprValue(next.devicePixelRatio);
    clearMeasurements(); elapsed=0; lastChange=0; warmup=config.warmupMs;
    resumePending=false; accepted=false; changed=false; reason='reset';
    return snapshot();
  }
  return {
    sample, reset, snapshot,
    setMode:nextMode => reset({mode:nextMode}),
    setDevicePixelRatio(value) {
      const previous=pixelRatio(); deviceDpr=dprValue(value);
      accepted=false; changed=previous!==pixelRatio(); reason='pixel-ratio'; return snapshot();
    },
    get quality() { return quality; },
    get pixelRatio() { return pixelRatio(); },
    get mode() { return mode; },
  };
}
