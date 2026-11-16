// Contract: 05_Docs/API_CONTRACT.md, geography section. No production writes.
const http=require('http'),crypto=require('crypto');
const ring=[[71.43,51.128],[71.4304,51.128],[71.4304,51.1283],[71.43,51.1283],[71.43,51.128]];
function pack(version){
  const payload={addresses:{schemaVersion:1,records:[['way/1',[71.43,51.128,71.4304,51.1283],`Simulation street, ${version}`,'']]},
    places:{schemaVersion:1,source:'OpenStreetMap',license:'ODbL-1.0',timestamp:'2026-11-16T00:00:00Z',places:[{id:'test',sourceRefs:['way/1'],footprints:[{sourceRef:'way/1',rings:[ring]}],entrances:[{id:'node/1',lng:71.43,lat:51.1281,kind:'main',access:'yes',wheelchair:'unknown'}],review:{status:'matched',notes:'Synthetic test fixture, not real geography',sourceUrls:[]}}]}};
  const manifest={schemaVersion:1,catalogVersion:1,release:`simulation-${version}`},files={};
  for(const kind of ['addresses','places']){const bytes=Buffer.from(JSON.stringify(payload[kind])),sha256=crypto.createHash('sha256').update(bytes).digest('hex');files[sha256]=bytes;manifest[kind]={file:`assets/${sha256}.json`,sha256,bytes:bytes.length};}
  return {manifest,files};
}
http.createServer(async(req,res)=>{
  res.setHeader('Content-Type','application/json');
  if(req.method==='POST'&&req.url==='/__control'){
    const body=typeof req.body==='string'?JSON.parse(req.body):req.body;
    if(!['v1','v2','offline','hash','incompatible','slow'].includes(body?.mode)){res.writeHead(400);res.end('{}');return;}
    await pm.state.set('geography.mode',body.mode);res.end(JSON.stringify({mode:body.mode}));return;
  }
  const mode=await pm.state.get('geography.mode')||'v1';
  if(req.method!=='GET'||!(/^\/world\/(places-manifest\.json|assets\/[a-f0-9]{64}\.json)$/.test(req.url))){res.writeHead(501);res.end('{"error":"not_modeled"}');return;}
  if(mode==='offline'){res.writeHead(503);res.end('{"error":"simulated_outage"}');return;}
  if(mode==='slow')await new Promise(resolve=>setTimeout(resolve,1800));
  const p=pack(['v2','hash'].includes(mode)?2:1);
  if(req.url==='/world/places-manifest.json'){
    res.setHeader('Cache-Control','no-cache');if(mode==='incompatible')p.manifest.catalogVersion=99;
    res.end(JSON.stringify(p.manifest));return;
  }
  const sha=req.url.split('/').pop().replace('.json',''),bytes=p.files[sha];
  if(!bytes){res.writeHead(404);res.end('{}');return;}
  res.setHeader('Cache-Control','public, max-age=31536000, immutable');
  const out=Buffer.from(bytes);if(mode==='hash'&&sha===p.manifest.addresses.sha256)out[0]=0;
  res.end(out);
}).listen(process.env.PORT);
