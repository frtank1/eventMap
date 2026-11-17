// Local check of ./dist only. Production hosting is any static file host.
import {createServer} from 'node:http';
import {readFile,stat} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {networkInterfaces} from 'node:os';
import {gzipSync} from 'node:zlib';
const zipped=new Map();
const root=path.join(path.dirname(fileURLToPath(import.meta.url)),'dist');
const types={'.webmanifest':'application/manifest+json','.glb':'model/gltf-binary','.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.json':'application/json','.jpg':'image/jpeg','.webp':'image/webp','.png':'image/png','.svg':'image/svg+xml','.txt':'text/plain; charset=utf-8'};
const port=Number(process.env.PORT)||8790,host=process.env.HOST==='0.0.0.0'?'0.0.0.0':'127.0.0.1';
createServer(async(req,res)=>{
  try{
    const pathname=decodeURIComponent(new URL(req.url,'http://x').pathname),file=path.join(root,pathname.endsWith('/')?pathname+'index.html':pathname);
    if(req.method!=='GET'||!file.startsWith(root+path.sep)){res.writeHead(404).end();return;}
    const bytes=await readFile(file),type=types[path.extname(file)]||'application/octet-stream';
    // gzip like a real static host would (overview.json 7 MB → ~2 MB), so phones on Wi-Fi load in time.
    if(/json|javascript|css|html|gltf|text/.test(type)&&/\bgzip\b/.test(req.headers['accept-encoding']||'')){
      // Keyed by modification time too: an edit that keeps the size must not serve stale bytes.
      const key=`${file}:${bytes.length}:${(await stat(file)).mtimeMs}`;let z=zipped.get(key);if(!z){z=gzipSync(bytes,{level:6});zipped.set(key,z);}
      res.writeHead(200,{'Content-Type':type,'Content-Encoding':'gzip','Vary':'Accept-Encoding','Cache-Control':'no-store'});res.end(z);return;}
    res.writeHead(200,{'Content-Type':type,'Cache-Control':'no-store'});res.end(bytes);
  }catch{res.writeHead(404).end();}
}).listen(port,host,()=>{console.log(`Web version: http://127.0.0.1:${port}/  (demo data: add ?demo=1)`);
  // HOST=0.0.0.0 lets a phone on the same Wi-Fi open it: http://<this computer's IP>:<port>/
  if(host==='0.0.0.0')for(const list of Object.values(networkInterfaces()))for(const a of list||[])if(a.family==='IPv4'&&!a.internal)console.log(`On the phone (same Wi-Fi): http://${a.address}:${port}/`);});
