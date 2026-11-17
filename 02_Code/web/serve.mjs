// Local check of ./dist only. Production hosting is any static file host.
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.join(path.dirname(fileURLToPath(import.meta.url)),'dist');
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.json':'application/json','.jpg':'image/jpeg','.webp':'image/webp','.png':'image/png','.svg':'image/svg+xml','.txt':'text/plain; charset=utf-8'};
const port=Number(process.env.PORT)||8790;
createServer(async(req,res)=>{
  try{
    const pathname=decodeURIComponent(new URL(req.url,'http://x').pathname),file=path.join(root,pathname.endsWith('/')?pathname+'index.html':pathname);
    if(req.method!=='GET'||!file.startsWith(root+path.sep)){res.writeHead(404).end();return;}
    const bytes=await readFile(file);res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store'});res.end(bytes);
  }catch{res.writeHead(404).end();}
}).listen(port,'127.0.0.1',()=>console.log(`Web version: http://127.0.0.1:${port}/  (demo data: add ?demo=1)`));
