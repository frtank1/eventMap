// Versioned geographic DATA. No executable assets, geocoder calls or credentials.
import {BrowserCache,hash,manifestUrl,readLimited} from './content-store.js';
import {createAddressIndex} from './place-addresses.js';
import {containsCoordinate,LON_SCALE,METERS} from './world-state.js';
import {distanceSegment} from '../source/city-overview.js';

export const GEO_VERSION=1;
export const GEO_LIMITS=Object.freeze({addresses:12*1024*1024,places:1024*1024});
const demand=(ok,message)=>{if(!ok)throw new Error(message);};
const text=(s,n)=>typeof s==='string'&&s.length<=n&&!/[\u0000-\u001f\u007f]/.test(s);
const ref=s=>typeof s==='string'&&/^(node|way|relation)\/[1-9]\d{0,19}$/.test(s);
const decode=bytes=>JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));
export function validateGeoManifest(m){
  demand(m?.schemaVersion===1&&m.catalogVersion===GEO_VERSION,'geo-version');
  demand(typeof m.release==='string'&&/^[a-zA-Z0-9._-]{1,64}$/.test(m.release),'geo-release');
  for(const kind of ['addresses','places']){
    const d=m[kind];
    demand(d&&/^[a-f0-9]{64}$/.test(d.sha256)&&d.file===`assets/${d.sha256}.json`,'geo-file');
    demand(Number.isInteger(d.bytes)&&d.bytes>0&&d.bytes<=GEO_LIMITS[kind],'geo-size');
  }
  return m;
}
export function validatePlaceCatalog(data){
  demand(data?.schemaVersion===1&&Array.isArray(data.places)&&data.places.length<=128,'place-schema');
  demand(text(data.source,100)&&text(data.license,40)&&text(data.timestamp,40),'place-provenance');
  let vertices=0;const ids=new Set();
  for(const p of data.places){
    demand(p&&/^[a-z][a-z0-9-]{0,63}$/.test(p.id)&&!ids.has(p.id),'place-id');ids.add(p.id);
    demand(Array.isArray(p.sourceRefs)&&p.sourceRefs.length<=40&&p.sourceRefs.every(ref)&&new Set(p.sourceRefs).size===p.sourceRefs.length,'place-refs');
    demand(Array.isArray(p.footprints)&&p.footprints.length<=40,'place-footprints');
    for(const f of p.footprints){
      demand(ref(f.sourceRef)&&p.sourceRefs.includes(f.sourceRef)&&Array.isArray(f.rings)&&f.rings.length>0&&f.rings.length<=32,'place-footprint');
      let count=0;
      for(const ring of f.rings){
        demand(Array.isArray(ring)&&ring.length>=3&&ring.every(c=>Array.isArray(c)&&c.length===2&&containsCoordinate(c[0],c[1])),'place-ring');
        count+=ring.length;vertices+=ring.length;
      }
      demand(count<=512&&vertices<=24000,'place-vertex-budget');
    }
    demand(Array.isArray(p.entrances)&&p.entrances.length<=64,'place-entrances');
    const entrances=new Set();
    for(const e of p.entrances){
      demand(/^node\/[1-9]\d{0,19}$/.test(e.id)&&!entrances.has(e.id)&&containsCoordinate(e.lng,e.lat),'entrance-coordinate');entrances.add(e.id);
      demand(['main','yes','service','exit','emergency','staircase','home','unknown'].includes(e.kind),'entrance-kind');
      demand(['yes','private','customers','unknown','no','permissive'].includes(e.access),'entrance-access');
      demand(['yes','no','limited','unknown'].includes(e.wheelchair),'entrance-wheelchair');
      const point=[e.lng*LON_SCALE,e.lat*METERS],project=([x,y])=>[x*LON_SCALE,y*METERS];
      demand(p.footprints.some(f=>f.rings.some(r=>r.some((a,i)=>distanceSegment(point,project(a),project(r[(i+1)%r.length]))<=2))),'entrance-not-on-building');
    }
    demand(['matched','needs_review','not_found'].includes(p.review?.status)&&text(p.review.notes,1000),'place-review');
    demand(Array.isArray(p.review.sourceUrls)&&p.review.sourceUrls.length<=12&&p.review.sourceUrls.every(u=>text(u,500)&&/^https:\/\//.test(u)),'place-sources');
  }
  return data;
}
export function prepareGeoData(addresses,places){
  validatePlaceCatalog(places);
  const index=createAddressIndex(addresses,places);
  return {addresses,places,index};
}

export class GeoCatalogStore{
  constructor({fallback,cache=new BrowserCache('eventmap-geography-v1'),fetcher=(...a)=>fetch(...a),activate=()=>{},onStatus=()=>{},timeout=20000}){
    Object.assign(this,{fallback,cache,fetcher,activate,onStatus,timeout});this.current=null;this.manifest=null;this.stopped=false;this.busy=false;this.lastCheck=0;
  }
  status(state){this.onStatus({state,release:this.manifest?.release||'bundled'});}
  async ensureFallback(){
    if(!this.fallbackPromise)this.fallbackPromise=(async()=>{
      const p=await this.fallback(),ready=prepareGeoData(p.addresses,p.places);
      if(!this.current&&!this.stopped){await this.activate(ready);this.current=ready;}
    })();
    await this.fallbackPromise;
  }
  async checkPack(pack){
    const m=validateGeoManifest(pack.manifest),data={};
    for(const kind of ['addresses','places']){
      const d=m[kind],bytes=pack.files?.[d.sha256];
      demand(bytes instanceof Uint8Array&&bytes.length===d.bytes,'geo-asset-size');
      demand(await hash(bytes)===d.sha256,'geo-asset-hash');data[kind]=decode(bytes);
    }
    return prepareGeoData(data.addresses,data.places);
  }
  async start(url){
    const next=manifestUrl(url).href;
    demand(!this.url||this.url===next,'geo-origin-change');
    if(this.initializing)return this.initializing;
    this.url=next;
    this.initializing=this.initialize();
    await this.initializing;
    if(!this.stopped)await this.update();
  }
  async initialize(){
    try{await this.ensureFallback();}catch{this.status('fallback-unavailable');}
    try{
      const cached=await this.cache.read(this.url);
      if(cached){const ready=await this.checkPack(cached);if(!this.stopped){await this.activate(ready);this.current=ready;this.manifest=cached.manifest;this.status('cached');}}
    }catch{this.status('cache-unavailable');}
  }
  async lookup(place){
    if(!this.current){try{await this.ensureFallback();}catch{}}
    return this.current?.index.lookup(place)||{address:'',addressStatus:'unavailable',addressSource:'',sourceRef:'',entranceStatus:'unavailable',entrances:[]};
  }
  async update({force=true}={}){
    // Opening, resuming and manual refresh may overlap. A delayed startup cache
    // read must never overwrite a more recent validated network activation.
    if(this.initializing)await this.initializing;
    if(this.busy||!this.url||this.stopped||(!force&&Date.now()-this.lastCheck<300000))return;
    this.lastCheck=Date.now();this.busy=true;this.status('checking');
    const controller=new AbortController();this.controller=controller;
    const timer=setTimeout(()=>controller.abort(),this.timeout);
    const get=async(url,limit)=>readLimited(await this.fetcher(url,{signal:controller.signal,credentials:'omit',redirect:'error',cache:'no-store'}),limit);
    try{
      const manifest=validateGeoManifest(decode(await get(this.url,16384)));
      if(JSON.stringify(manifest)===JSON.stringify(this.manifest)){this.status('current');return;}
      let saved;try{saved=await this.cache.read(this.url);}catch{}
      const pack={manifest,files:{}};
      for(const kind of ['addresses','places']){
        const d=manifest[kind],cached=saved?.files?.[d.sha256];
        pack.files[d.sha256]=cached instanceof Uint8Array&&cached.length===d.bytes&&await hash(cached)===d.sha256?cached:await get(new URL(d.file,this.url).href,d.bytes);
      }
      const ready=await this.checkPack(pack);if(this.stopped)return;
      await this.activate(ready);this.current=ready;this.manifest=manifest;
      try{await this.cache.write(this.url,pack);this.status('updated');}catch{this.status('updated-memory-only');}
    }catch{if(!this.stopped)this.status('kept-working');}
    finally{clearTimeout(timer);this.busy=false;}
  }
  stop(){this.stopped=true;this.controller?.abort();}
}
