// Public map data only. Never shares an auth, event or user-location cache.
export const TILE_CACHE_LIMITS=Object.freeze({bytes:32*1024*1024,entries:256,item:4*1024*1024,age:7*86400000});
export async function readTileBytes(response,limit=TILE_CACHE_LIMITS.item){
  if(Number(response.headers?.get('content-length'))>limit)throw Error('Tile size budget');
  if(!response.body?.getReader){const bytes=await response.arrayBuffer();if(bytes.byteLength>limit)throw Error('Tile size budget');return bytes;}
  const reader=response.body.getReader(),parts=[];let size=0;
  try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>limit)throw Error('Tile size budget');parts.push(value);}}catch(e){await reader.cancel().catch(()=>{});throw e;}
  const bytes=new Uint8Array(size);let at=0;for(const p of parts){bytes.set(p,at);at+=p.byteLength;}return bytes.buffer;
}
export function createTileCache({indexedDB=globalThis.indexedDB,now=Date.now}={}){
  let opening,disabled=!indexedDB;const metrics={hits:0,misses:0,writes:0,errors:0};
  async function db(){
    if(disabled)return null;
    return opening??=new Promise(resolve=>{
      let settled=false;const done=d=>{if(settled){d?.close();return;}settled=true;clearTimeout(timer);resolve(d);};
      const timer=setTimeout(()=>{disabled=true;done(null);},1500);
      try{const r=indexedDB.open('eventmap-public-tiles-v1',2);r.onupgradeneeded=()=>{if(!r.result.objectStoreNames.contains('tiles'))r.result.createObjectStore('tiles',{keyPath:'key'});const usage=r.result.createObjectStore('usage',{keyPath:'key'}),cursor=r.transaction.objectStore('tiles').openCursor();cursor.onsuccess=()=>{const c=cursor.result;if(c){const v=c.value;usage.put({key:v.key,used:v.used,created:v.created,size:v.bytes.byteLength});c.continue();}};};r.onsuccess=()=>{r.result.onversionchange=()=>r.result.close();done(r.result);};r.onerror=r.onblocked=()=>{disabled=true;metrics.errors++;done(null);};}catch{disabled=true;metrics.errors++;done(null);}
    });
  }
  async function transaction(mode,run){const d=await db();if(!d)return null;return new Promise(resolve=>{let result=null;try{const t=d.transaction(['tiles','usage'],mode);t.oncomplete=()=>resolve(result);t.onerror=t.onabort=()=>{metrics.errors++;resolve(null);};run(t.objectStore('tiles'),v=>result=v,t.objectStore('usage'));}catch{metrics.errors++;resolve(null);}});}
  return {
    async get(key){const value=await transaction('readwrite',(s,set,usage)=>{const r=s.get(key);r.onsuccess=()=>{const v=r.result;if(v&&v.bytes instanceof ArrayBuffer&&v.bytes.byteLength<=TILE_CACHE_LIMITS.item&&now()-v.created<TILE_CACHE_LIMITS.age){usage.put({key,used:now(),created:v.created,size:v.bytes.byteLength});set(v);}else if(v){s.delete(key);usage.delete(key);}};});metrics[value?'hits':'misses']++;return value;},
    async put(key,bytes){
      if(!(bytes instanceof ArrayBuffer)||bytes.byteLength>TILE_CACHE_LIMITS.item)return;
      const written=await transaction('readwrite',(s,set,usage)=>{
        const created=now();s.put({key,bytes,created});usage.put({key,created,used:created,size:bytes.byteLength});
        const r=usage.getAll();r.onsuccess=()=>{
          const rows=r.result.sort((a,b)=>b.used-a.used);let total=0;
          for(let i=0;i<rows.length;i++){
            const v=rows[i];total+=v.size;
            if(i>=TILE_CACHE_LIMITS.entries||total>TILE_CACHE_LIMITS.bytes||now()-v.created>=TILE_CACHE_LIMITS.age){s.delete(v.key);usage.delete(v.key);}
          }
          set(true);
        };
      });if(written)metrics.writes++;
    },
    remove:key=>transaction('readwrite',(s,set,usage)=>{s.delete(key);usage.delete(key);}),stats:()=>({...metrics,available:!disabled}),
  };
}
export function parseTileMetadata(json){
  const template=json?.tiles?.[0];
  if(typeof template!=='string'||template.length>512||!['{z}','{x}','{y}'].every(t=>template.includes(t)))throw Error('TileJSON has no supported XYZ template');
  const url=new URL(template,'https://tiles.openfreemap.org/planet/latest');
  if(url.protocol!=='https:'||url.hostname!=='tiles.openfreemap.org'||url.username||url.password||url.port)throw Error('Untrusted tile source');
  return {template:url.href.replaceAll('%7B','{').replaceAll('%7D','}'),scheme:json.scheme==='tms'?'tms':'xyz',attribution:typeof json.attribution==='string'?json.attribution.slice(0,2000):null};
}
