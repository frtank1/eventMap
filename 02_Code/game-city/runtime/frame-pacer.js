// Accumulate fractional refresh intervals instead of snapping to every third
// 75 Hz refresh (25 FPS). No catch-up render bursts after pauses/backgrounding.
export function createFramePacer(fps=30){
  const period=1000/fps;let deadline=null;
  return {due(t){if(!Number.isFinite(t))return false;if(deadline!==null&&t<deadline-.4)return false;deadline=deadline===null||t-deadline>period*2?t+period:deadline+period;return true;}};
}
