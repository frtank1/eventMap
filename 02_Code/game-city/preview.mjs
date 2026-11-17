// Loopback-only verification server. Never packaged into the mobile app or backend.
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
export function createPreviewServer({root=path.dirname(fileURLToPath(import.meta.url))}={}){
let release='pilot-1';
return createServer(async(req,res)=>{
  try {
    const url=new URL(req.url,'http://127.0.0.1');let file, type='application/json';
    if(url.pathname==='/__qa/release'&&req.method==='POST') {
      const next=url.searchParams.get('value');if(!['pilot-1','pilot-2','offline','incompatible'].includes(next)){res.writeHead(400).end();return;}
      release=next;res.end(JSON.stringify({release}));return;
    }
    if(req.method!=='GET'){res.writeHead(405).end();return;}
    if(url.pathname==='/world/manifest.json') {
      if(release==='offline'){res.writeHead(503).end('{}');return;}
      if(release==='incompatible'){res.end(JSON.stringify({schemaVersion:1,engineVersion:99}));return;}
      file=path.join(root,'content',release==='pilot-2'?'releases/pilot-2.json':'manifest.json');
    } else if(url.pathname==='/world/places-manifest.json') {
      if(release==='offline'){res.writeHead(503).end('{}');return;}
      if(release==='incompatible'){res.end(JSON.stringify({schemaVersion:1,catalogVersion:99}));return;}
      file=path.join(root,'content/places-manifest.json');
    } else if(/^\/world\/assets\/[a-f0-9]{64}\.json$/.test(url.pathname)) file=path.join(root,'content',url.pathname.replace('/world/',''));
    else if(url.pathname==='/test/nsdi-quarter.json')file=path.join(root,'test/nsdi-local/quarter.json');
    else if(url.pathname==='/demo.mp4'){file=path.join(root,'../androidApp/src/debug/res/raw/preview_reel.mp4');type='video/mp4';}
    else if(url.pathname==='/qa.js'){file=path.join(root,'test/qa.js');type='text/javascript';}
    else if(url.pathname==='/stage-one-qa.js'){file=path.join(root,'test/stage-one-qa.js');type='text/javascript';}
    else if(['/test/visual-performance-qa.js','/runtime/frame-governor.js','/test/festival-qa.js','/test/festival-fixtures.js'].includes(url.pathname)){file=path.join(root,url.pathname.slice(1));type='text/javascript';}
    else if(/^\/event-covers\/(sport|music|market|discovery|route|quest|community)\.svg$/.test(url.pathname)){file=path.join(root,'test',url.pathname.slice(1));type='image/svg+xml';}
    else if(['/','/index.html','/mobile.js','/mobile.css','/addresses.json','/places.json','/overview.json'].includes(url.pathname)) {
      file=path.join(root,'preview-dist',url.pathname==='/'?'index.html':url.pathname.slice(1));
      type=file.endsWith('.html')?'text/html':file.endsWith('.js')?'text/javascript':file.endsWith('.json')?'application/json':'text/css';
    } else {res.writeHead(404).end();return;}
    let bytes=await readFile(file);if(type==='text/html')bytes=Buffer.from(bytes.toString().replace('</body>',`<script src="qa.js"></script>${url.searchParams.get('festival')==='1'?'<script type="module" src="/test/festival-qa.js"></script>':'<script src="stage-one-qa.js"></script>'}${url.searchParams.get('visualperf')==='1'?'<script type="module" src="/test/visual-performance-qa.js"></script>':''}</body>`));
    res.writeHead(200,{'Content-Type':type,'Cache-Control':'no-store','Access-Control-Allow-Origin':'*'});res.end(bytes);
  } catch {res.writeHead(404).end();}
});
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  createPreviewServer().listen(8784,'127.0.0.1',()=>console.log('Local: http://127.0.0.1:8784'));
}
