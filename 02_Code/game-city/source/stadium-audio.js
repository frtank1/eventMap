// Original demo soundscape. No microphone, tracking, third-party audio or autoplay.
const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
export function stadiumMix(distance){
  if(!Number.isFinite(distance))return {gain:0,cutoff:650};
  const d=Math.max(0,distance),x=clamp((d-1200)/600,0,1),fade=1-x*x*(3-2*x);
  const gain=fade/(1+Math.pow(Math.max(0,d-80)/240,1.65));
  return {gain,cutoff:650+5850*Math.sqrt(gain)};
}
function random(seed){return()=>{seed=(seed*1664525+1013904223)>>>0;return seed/4294967296}}
export function makeChorus(samples,sampleRate){
  const out=new Float32Array(Math.ceil((samples.length/sampleRate/.66+1.3)*sampleRate)),rand=random(193);
  // Slightly staggered lower voices form a stylized, non-identifiable crowd.
  for(let voice=0;voice<20;voice++){
    const rate=.68+rand()*.35,offset=Math.floor((.035+rand()*.11)*sampleRate),amp=(.75+rand()*.5)/13;
    for(let n=0;n<(samples.length-1)/rate;n++){
      const source=n*rate,i=Math.floor(source),v=(samples[i]*(1-source+i)+samples[i+1]*(source-i))*amp;
      if(n+offset<out.length)out[n+offset]+=v;
    }
  }
  const dry=out.slice();
  for(const [seconds,amount] of [[.17,.22],[.34,.14],[.59,.08]]){
    const delay=Math.floor(seconds*sampleRate);for(let n=delay;n<out.length;n++)out[n]+=dry[n-delay]*amount;
  }
  let peak=0;for(const v of out)peak=Math.max(peak,Math.abs(v));
  if(peak>0)for(let n=0;n<out.length;n++)out[n]=out[n]/peak*.78;
  return out;
}
export function makeCrowd(sampleRate,seconds=16){
  const out=new Float32Array(sampleRate*seconds),rand=random(8177);let low=0,body=0;
  for(let i=0;i<out.length;i++){
    const t=i/sampleRate,white=rand()*2-1;low=.985*low+.015*white;body=.78*body+.22*white;
    const swell=.64+.19*Math.sin(t*Math.PI/4)+.17*Math.sin(t*Math.PI/2);
    const beat=t%.5,clap=Math.exp(-beat*42)*Math.min(1,beat*400);
    const drum=Math.exp(-beat*16)*Math.sin(2*Math.PI*(68*beat-22*beat*beat));
    const calls=(Math.sin(t*2.2)+1)*.5;
    out[i]=((body-low)*.32+low*.45)*swell+white*clap*.12+drum*.1+Math.sin(t*740+Math.sin(t*17)*7)*calls*.004;
    const edge=Math.min(1,i/(sampleRate*.06),(out.length-1-i)/(sampleRate*.06));out[i]*=edge;
  }
  return out;
}

export function createStadiumAudio(){
  let context,master,filter,panner,compressor,analyser,pcm,loading,graphReady=false,disposed=false,resuming=false;
  let enabled=false,active=false,hidden=false,volume=.65,distance=Infinity,status='idle',partial=false;
  const sources=[],nodes=[];let currentMix=stadiumMix(Infinity),lastSync=0;
  const track=n=>(nodes.push(n),n);
  function buffer(samples,rate){const b=context.createBuffer(1,samples.length,rate);b.copyToChannel(samples,0);return b}
  function loop(b,gain,offset=0){const source=track(context.createBufferSource()),g=track(context.createGain());source.buffer=b;source.loop=true;g.gain.value=gain;source.connect(g);g.connect(filter);source.start(context.currentTime+.03,offset);sources.push(source)}
  function silence(){if(!context)return;if(master){master.gain.cancelScheduledValues(context.currentTime);master.gain.setValueAtTime(0,context.currentTime)}if(context.state==='running')context.suspend().catch(()=>{})}
  async function prepare(){
    const sounds=await Promise.allSettled(['astana','tobol'].map(async name=>{
      const response=await fetch(new URL(`./audio/${name}-chant.wav`,import.meta.url),{signal:AbortSignal.timeout(12000)});
      if(!response.ok)throw Error('chant unavailable');
      const audio=await context.decodeAudioData(await response.arrayBuffer());
      return makeChorus(audio.getChannelData(0),audio.sampleRate);
    }));
    if(disposed)return;
    const rate=context.sampleRate,chantLoop=new Float32Array(rate*16);
    sounds.forEach((result,index)=>{if(result.status!=='fulfilled'){partial=true;return}const offset=Math.floor((index?8.2:.6)*rate);const a=result.value;for(let i=0;i<a.length&&i+offset<chantLoop.length;i++)chantLoop[i+offset]+=a[i]});
    loop(buffer(makeCrowd(rate),rate),.7);loop(buffer(chantLoop,rate),.86);
    graphReady=true;status=partial?'partial':'ready';if(!enabled||!active||hidden)silence();
  }
  return {
    async enable(){
      if(disposed)return false;
      enabled=true;
      try{
        if(!context){
          const AudioContext=globalThis.AudioContext||globalThis.webkitAudioContext;if(!AudioContext)throw Error('Web Audio unavailable');
          context=new AudioContext();filter=track(context.createBiquadFilter());filter.type='lowpass';filter.Q.value=.4;
          panner=track(context.createPanner());panner.panningModel='HRTF';panner.distanceModel='inverse';panner.rolloffFactor=0;panner.positionY.value=18;
          master=track(context.createGain());master.gain.value=0;
          compressor=track(context.createDynamicsCompressor());compressor.threshold.value=-12;compressor.knee.value=12;compressor.ratio.value=6;compressor.attack.value=.008;compressor.release.value=.3;
          analyser=track(context.createAnalyser());analyser.fftSize=512;pcm=new Float32Array(analyser.fftSize);
          filter.connect(panner);panner.connect(master);master.connect(compressor);compressor.connect(analyser);analyser.connect(context.destination);
        }
        // Called synchronously from a click before any asset-loading await.
        const resumed=context.resume();
        if(!loading){status='loading';loading=prepare().catch(error=>{loading=null;status='error';enabled=false;silence();throw error})}
        await resumed;await loading;
        if(!enabled||!active||hidden)silence();
        return enabled&&graphReady;
      }catch{status='error';enabled=false;silence();return false}
    },
    mute(){enabled=false;silence()},
    setActive(value){active=Boolean(value);if(!active)silence()},
    setHidden(value){hidden=Boolean(value);if(hidden)silence()},
    setVolume(value){if(Number.isFinite(value))volume=clamp(value,0,1)},
    update(position,forward,up){
      if(disposed)return;
      distance=Math.hypot(position.x,position.y-18,position.z);currentMix=stadiumMix(distance);
      if(!context||!graphReady)return;
      const audible=enabled&&active&&!hidden&&currentMix.gain>0&&volume>0;
      if(!audible){silence();return}
      if(context.state==='suspended'&&!resuming){resuming=true;context.resume().catch(()=>{status='interrupted'}).finally(()=>{resuming=false})}
      if(context.state!=='running')return;
      const t=context.currentTime;if(t-lastSync<.03)return;lastSync=t;
      const listener=context.listener;
      if(listener.positionX){
        for(const [key,value] of [['positionX',position.x],['positionY',position.y],['positionZ',position.z],['forwardX',forward.x],['forwardY',forward.y],['forwardZ',forward.z],['upX',up.x],['upY',up.y],['upZ',up.z]])listener[key].setTargetAtTime(value,t,.06);
      }else{listener.setPosition(position.x,position.y,position.z);listener.setOrientation(forward.x,forward.y,forward.z,up.x,up.y,up.z)}
      master.gain.setTargetAtTime(currentMix.gain*volume*.72,t,.14);filter.frequency.setTargetAtTime(currentMix.cutoff,t,.14);
    },
    getState(){
      let rms=0;if(analyser&&context.state==='running'){analyser.getFloatTimeDomainData(pcm);for(const v of pcm)rms+=v*v;rms=Math.sqrt(rms/pcm.length)}
      return {status,enabled,active,volume,distance,gain:enabled&&active&&!hidden?currentMix.gain*volume:0,proximity:currentMix.gain,rms,context:context?.state||'not-created'};
    },
    dispose(){if(disposed)return;disposed=true;enabled=false;for(const s of sources)try{s.stop()}catch{}for(const n of nodes)n.disconnect();context?.close().catch(()=>{})}
  };
}
