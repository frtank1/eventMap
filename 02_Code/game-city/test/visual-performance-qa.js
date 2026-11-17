// Preview-only ES module; never bundle into mobile.js. Integration for main:
//   immediately AFTER renderer.render(scene,camera), once per rendered frame:
//   window.EventMapPerformanceQA?.sampleFrame({dtMs:interval,
//     drawCalls:renderer.info.render.calls, triangles:renderer.info.render.triangles,
//     quality, pixelRatio:renderer.getPixelRatio(), active, blocked,
//     hidden:document.hidden, reducedMotion:reducedMotion.matches, warmingUp});
// Pass raw milliseconds, NOT capped animation dt. Call .pause() on visibility,
// active=false, context loss or a clock discontinuity if no frames are emitted.
// Preview owner: serve this at /test/visual-performance-qa.js and the dependency
// at /runtime/frame-governor.js; inject type="module" only for ?visualperf=1.
import {frameExclusion, isFrameInterval, summarizeFrames} from '../runtime/frame-governor.js';

export const PERFORMANCE_SCENARIOS = Object.freeze([
  Object.freeze({id:'baiterek-day', title:'Байтерек · день', focus:'baiterek', night:false}),
  Object.freeze({id:'baiterek-night', title:'Байтерек · ночь', focus:'baiterek', night:true}),
  Object.freeze({id:'arena-day', title:'Арена · день', focus:'arena', night:false}),
  Object.freeze({id:'city-day', title:'Обзор города · день', focus:'city', night:false}),
  Object.freeze({id:'city-night', title:'Обзор города · ночь', focus:'city', night:true}),
  Object.freeze({id:'bridges-night', title:'Мосты у Ак Орды · ночь', focus:'akorda', night:true}),
]);

const counter = value => Number.isSafeInteger(value) && value>=0 ? value : null;
const ratio = value => Number.isFinite(value) && value>0 ? value : null;
function distribution(values) {
  const sorted=values.filter(n => n!==null).sort((a,b) => a-b);
  return {samples:sorted.length, mean:sorted.length ? sorted.reduce((a,b) => a+b,0)/sorted.length : null,
    p95:sorted.length ? sorted[Math.ceil(sorted.length*.95)-1] : null,
    max:sorted.length ? sorted.at(-1) : null};
}

// Pure collector. Tests supply synthetic intervals; only the browser render hook
// can provide device measurements. Never estimate rendered FPS from QA RAF/timers
// or repeatedly sample EventMapMobile.stats().fps (that would double-count frames).
export function createPerformanceRun({warmupMs=3000, durationMs=12000, maxSamples=10000}={}) {
  if (!Number.isFinite(warmupMs) || warmupMs<0 || !Number.isFinite(durationMs) || durationMs<=0 ||
      !Number.isSafeInteger(maxSamples) || maxSamples<1 || maxSamples>100000)
    throw new RangeError('Invalid benchmark bounds');
  const frames=[], excluded=Object.create(null);
  let warmup=warmupMs, duration=0, resumePending=false, status='running';
  function exclude(reason) { excluded[reason]=(excluded[reason]||0)+1; }
  function progress() { return {status, samples:frames.length, durationMs:duration, warmupRemainingMs:warmup}; }
  function pause(reason='paused') {
    if (status==='running') { exclude(reason); resumePending=true; }
    return progress();
  }
  function sample(frame={}) {
    if (status!=='running') return progress();
    const exclusion=frameExclusion(frame);
    if (exclusion || !isFrameInterval(frame.dtMs)) return pause(exclusion || 'invalid-interval');
    if (resumePending) {
      resumePending=false; warmup=warmupMs; exclude('resume-boundary'); return progress();
    }
    if (warmup>0) {
      warmup=Math.max(0,warmup-Math.min(frame.dtMs,100)); exclude('warmup'); return progress();
    }
    frames.push({dtMs:frame.dtMs, drawCalls:counter(frame.drawCalls), triangles:counter(frame.triangles),
      quality:['low','balanced'].includes(frame.quality) ? frame.quality : 'unknown',
      pixelRatio:ratio(frame.pixelRatio)});
    duration+=frame.dtMs;
    if (duration>=durationMs) status='complete';
    else if (frames.length>=maxSamples) status='sample-limit';
    return progress();
  }
  function report() {
    const qualitySamples={}; for (const f of frames) qualitySamples[f.quality]=(qualitySamples[f.quality]||0)+1;
    const ratios=frames.map(f => f.pixelRatio).filter(n => n!==null);
    return {schemaVersion:1, measurement:'rendered-frame-intervals', status,
      warmupMs, targetDurationMs:durationMs, excluded:{...excluded},
      frameTiming:summarizeFrames(frames.map(f => f.dtMs)),
      drawCalls:distribution(frames.map(f => f.drawCalls)), triangles:distribution(frames.map(f => f.triangles)),
      qualitySamples, pixelRatio:{samples:ratios.length,
        min:ratios.length ? ratios.reduce((a,b) => Math.min(a,b)) : null,
        max:ratios.length ? ratios.reduce((a,b) => Math.max(a,b)) : null}};
  }
  return {sample, pause, progress, report, stop() { if (status==='running') status='cancelled'; return report(); }};
}

// Whitelisted scene metadata only; no events, IDs, addresses, covers or avatar data.
function sceneContext(stats={}) {
  const keys=['artVersion','release','geographyRelease','quality','qualityMode','pixelRatio','quarterMode','night','theme',
    'events','labels','eventModels','eventPresentation','particles','geometries','lod','cityTiles','cityRequests','cityZoom'];
  const result={};
  for (const key of keys) if (['string','boolean','number'].includes(typeof stats[key])) result[key]=stats[key];
  if (stats.effects) result.effects=Object.fromEntries(['light','snow','fireworks'].map(k => [k,stats.effects[k]===true]));
  return result;
}

export function mountVisualPerformanceQA({window:win, document:doc}) {
  if (!['localhost','127.0.0.1','[::1]','::1'].includes(win.location.hostname) ||
      new URLSearchParams(win.location.search).get('visualperf')!=='1' ||
      win.EventMapNative || win.webkit?.messageHandlers?.EventMapNative) return null;
  if (win.EventMapPerformanceQA) return win.EventMapPerformanceQA;
  const panel=doc.createElement('section'); panel.id='visual-performance-qa';
  panel.setAttribute('aria-label','Локальная проверка производительности');
  // Owned inline styles avoid any change to production/quarter stylesheets.
  panel.style.cssText='position:fixed;z-index:10000;right:8px;bottom:8px;max-width:min(350px,calc(100vw - 16px));max-height:45vh;overflow:auto;padding:10px;border:1px solid #627079;border-radius:10px;background:#16212bf2;color:#fff;font:12px/1.4 system-ui;box-sizing:border-box';
  const make=(tag,text) => { const el=doc.createElement(tag); el.textContent=text; panel.append(el); return el; };
  make('strong','ЛОКАЛЬНЫЙ БЕНЧМАРК · без записи в API');
  make('p','После готовности runtime: 3 с прогрева + 12 с отрисованных кадров. Меняет ракурс и день/ночь, закрывает редактор/прогулку. События, эффекты и качество остаются текущими. Не трогайте карту во время замера.');
  const select=make('select',''); select.setAttribute('aria-label','Сценарий');
  for (const scenario of PERFORMANCE_SCENARIOS) {
    const option=doc.createElement('option'); option.value=scenario.id; option.textContent=scenario.title; select.append(option);
  }
  const start=make('button','Запустить'), stop=make('button','Остановить'), save=make('button','Скачать JSON');
  start.type=stop.type=save.type='button'; stop.disabled=save.disabled=true;
  const status=make('p','Не запускался. Измеренных FPS пока нет.'); status.setAttribute('role','status');
  const output=make('pre',''); output.style.cssText='white-space:pre-wrap;margin:4px 0';
  doc.body.append(panel);
  const reduced=win.matchMedia('(prefers-reduced-motion: reduce)');
  let run=null, selected=null, startedAt=null, startContext=null, lastReport=null;
  let restoreAppearance=null, watchdog=null, uiMs=0, receivedFrame=false;
  const readContext=() => sceneContext(win.EventMapMobile?.stats?.() || {});
  const number=(value,digits=1) => value===null ? 'нет данных' : value.toFixed(digits);
  function display(report) {
    const t=report.frameTiming;
    output.textContent=`Кадры: ${t.samples} · ${number(t.durationMs/1000)} с\nFPS: ${number(t.fps)} · p95: ${number(t.p95Ms)} мс\nDraw calls: ${number(report.drawCalls.mean)} ср. / ${number(report.drawCalls.p95,0)} p95\nTriangles: ${number(report.triangles.mean,0)} ср. / ${number(report.triangles.p95,0)} p95\nКачество: ${JSON.stringify(report.qualitySamples)} · DPR: ${number(report.pixelRatio.min,2)}–${number(report.pixelRatio.max,2)}`;
  }
  function finish(cancelled=false) {
    if (!run) return;
    const result=cancelled ? run.stop() : run.report();
    lastReport={...result, scenario:selected.id, startedAt, startContext, endContext:readContext(),
      viewport:{width:win.innerWidth,height:win.innerHeight,devicePixelRatio:win.devicePixelRatio},
      note:'Frame intervals, not GPU timings. Current event/effect load; no injected fixtures. QA panel overhead included. Partial runs are not comparable to completed runs.'};
    display(lastReport); run=null; win.clearTimeout(watchdog);
    status.textContent=result.status==='complete' ? 'Замер завершён. Это данные только этого запуска.' :
      result.status==='sample-limit' ? 'Лимит выборки: замер неполный.' : 'Остановлено: замер неполный.';
    start.disabled=select.disabled=false; stop.disabled=true; save.disabled=false;
    if (restoreAppearance) win.EventMapMobile.receive({v:1,type:'appearance',...restoreAppearance});
    restoreAppearance=null;
  }
  start.onclick=() => {
    if (run) return;
    if (!win.EventMapMobile?.receive) { status.textContent='Runtime ещё не готов.'; return; }
    if (doc.hidden || reduced.matches) { status.textContent='Нужна видимая вкладка без reduced-motion. Настройки доступности не меняются.'; return; }
    selected=PERFORMANCE_SCENARIOS.find(s => s.id===select.value) || PERFORMANCE_SCENARIOS[0];
    const before=readContext();
    restoreAppearance=typeof before.night==='boolean' ? {night:before.night,theme:before.theme || 'default'} : null;
    win.EventMapMobile.receive({v:1,type:'focus',id:selected.focus});
    win.EventMapMobile.receive({v:1,type:'appearance',night:selected.night,theme:'default'});
    startContext=readContext(); startedAt=new Date().toISOString(); run=createPerformanceRun();
    receivedFrame=false; uiMs=0; lastReport=null; output.textContent='';
    start.disabled=select.disabled=save.disabled=true; stop.disabled=false;
    status.textContent='Прогрев. Ожидаем реальные кадры render-hook…';
    watchdog=win.setTimeout(() => {
      if (run && !receivedFrame) status.textContent='Нет render-hook: FPS/p95 не измерены. Подключите sampleFrame после renderer.render; затем перезапустите.';
    },5000);
  };
  stop.onclick=() => finish(true);
  save.onclick=() => {
    if (!lastReport) return;
    const url=win.URL.createObjectURL(new win.Blob([JSON.stringify(lastReport,null,2)],{type:'application/json'}));
    const link=doc.createElement('a'); link.href=url; link.download=`eventmap-${lastReport.scenario}-performance.json`;
    doc.body.append(link); link.click(); link.remove(); win.setTimeout(() => win.URL.revokeObjectURL(url),1000);
  };
  const pause=() => { if (run) { run.pause(); status.textContent='Пауза/смена видимости. Следующий интервал исключён; затем повторный прогрев.'; } };
  doc.addEventListener('visibilitychange',pause); reduced.addEventListener('change',pause);
  const hook={
    sampleFrame(frame={}) {
      if (!run) return;
      receivedFrame=true;
      const flags={...frame, hidden:doc.hidden || frame.hidden===true,
        reducedMotion:reduced.matches || frame.reducedMotion===true};
      const progress=run.sample(flags);
      if (progress.status!=='running') { finish(); return; }
      uiMs+=isFrameInterval(frame.dtMs) ? Math.min(frame.dtMs,100) : 0;
      if (uiMs>=500) {
        uiMs=0;
        const excluded=frameExclusion(flags);
        status.textContent=excluded ? `Пауза: ${excluded}` : progress.warmupRemainingMs>0 ?
          `Прогрев: ${number(progress.warmupRemainingMs/1000)} с` : `Измеряем: ${number(progress.durationMs/1000)} / 12 с`;
        display(run.report());
      }
    },
    pause,
    report:() => lastReport ? structuredClone(lastReport) : null,
    destroy() {
      if (run) finish(true); win.clearTimeout(watchdog);
      doc.removeEventListener('visibilitychange',pause); reduced.removeEventListener('change',pause);
      panel.remove(); if (win.EventMapPerformanceQA===hook) delete win.EventMapPerformanceQA;
    },
  };
  win.EventMapPerformanceQA=hook;
  return hook;
}

if (typeof window!=='undefined' && typeof document!=='undefined') mountVisualPerformanceQA({window,document});
