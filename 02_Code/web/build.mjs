// Builds the public web version into ./dist: the 3D engine bundle exactly as
// built by ../game-city, the hash-checked city content, and this shell.
// Nothing here edits the engine; it is deployed as static files.
import {mkdir,readFile,writeFile,readdir,rm,copyFile,cp} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {normalizeEvents,CATEGORIES} from './lib.js';

const here=path.dirname(fileURLToPath(import.meta.url));
const engine=path.resolve(here,process.env.EVENTMAP_ENGINE_DIST||'../game-city/preview-dist');
const content=path.resolve(here,process.env.EVENTMAP_CONTENT||'../game-city/content');
const out=path.join(here,'dist');
const attr=v=>String(v).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));

await rm(out,{recursive:true,force:true});await mkdir(path.join(out,'world/assets'),{recursive:true});await mkdir(path.join(out,'covers'),{recursive:true});
const engineFiles=await readdir(engine);
for(const required of ['index.html','mobile.js','mobile.css'])if(!engineFiles.includes(required))throw new Error(`Engine build is missing ${required}. Run "npm run build -- --preview-only" in ../game-city first.`);
for(const name of engineFiles)if(name!=='index.html')await cp(path.join(engine,name),path.join(out,name),{recursive:true});

// City content the engine verifies by SHA-256: only files the manifests name.
let assets=0;
for(const name of ['manifest.json','places-manifest.json']){
  const text=await readFile(path.join(content,name),'utf8');await writeFile(path.join(out,'world',name),text);
  for(const [,file] of text.matchAll(/assets\/([a-f0-9]{64}\.json)/g)){await copyFile(path.join(content,'assets',file),path.join(out,'world/assets',file));assets++;}
}

const config=JSON.parse(await readFile(path.join(here,'config.json'),'utf8'));
config.covers={};
for(const name of await readdir(path.join(here,'covers')).catch(()=>[])){
  const match=/^([a-z]+)\.(jpg|webp|png)$/.exec(name);if(!match||!CATEGORIES[match[1]])continue;
  await copyFile(path.join(here,'covers',name),path.join(out,'covers',name));config.covers[match[1]]=`covers/${name}`;
}
await writeFile(path.join(out,'config.json'),JSON.stringify(config));

// A malformed entry fails the build instead of silently vanishing from the map.
const raw=JSON.parse(await readFile(path.join(here,'events.json'),'utf8'));
const events=normalizeEvents(raw);
if(!Array.isArray(raw)||events.length!==raw.length){
  const kept=new Set(events.map(e=>e.id));
  throw new Error(`events.json: ${Array.isArray(raw)?raw.length-events.length:'all'} invalid or duplicate entries: ${(Array.isArray(raw)?raw:[]).filter(e=>!kept.has(e?.id)).map(e=>e?.id??'(no id)').join(', ')}`);
}
await writeFile(path.join(out,'events.json'),JSON.stringify(raw));
if(config.demoEvents===true)await copyFile(path.join(here,'events.sample.json'),path.join(out,'events.sample.json'));
for(const name of ['shell.js','lib.js','shell.css'])await copyFile(path.join(here,name),path.join(out,name));
// Installable on phones (Add to Home Screen): manifest + icons, no service worker yet.
await cp(path.join(here,'icons'),path.join(out,'icons'),{recursive:true});
// Event scene configs (3D scenes of events; the demo one is clearly marked as such).
await cp(path.join(here,'scenes'),path.join(out,'scenes'),{recursive:true}).catch(()=>{});
await writeFile(path.join(out,'manifest.webmanifest'),JSON.stringify({name:`${config.title||'EventMap'} · ${config.city||'Астана'}`,short_name:config.title||'EventMap',
  description:config.description||'',lang:'ru',start_url:'./',scope:'./',display:'standalone',orientation:'portrait',background_color:'#17263a',theme_color:'#17263a',
  icons:[{src:'icons/icon-192.png',sizes:'192x192',type:'image/png'},{src:'icons/icon-512.png',sizes:'512x512',type:'image/png'},{src:'icons/icon-maskable-512.png',sizes:'512x512',type:'image/png',purpose:'maskable'}]}));
let image='';try{await copyFile(path.join(here,'og.jpg'),path.join(out,'og.jpg'));image='og.jpg';}catch{}

let html=await readFile(path.join(engine,'index.html'),'utf8');
for(const marker of ['</head>','</body>','<title>'])if(!html.includes(marker))throw new Error(`Engine index.html has no ${marker}`);
const title=`${config.title||'EventMap'} · ${config.city||'Астана'}`,site=config.siteUrl?config.siteUrl.replace(/\/?$/,'/'):'';
const head=[`<meta name="description" content="${attr(config.description||'')}">`,`<meta name="theme-color" content="#17263a">`,
  `<meta property="og:type" content="website">`,`<meta property="og:title" content="${attr(title)}">`,`<meta property="og:description" content="${attr(config.description||'')}">`,
  site?`<meta property="og:url" content="${attr(site)}">`:'',site&&image?`<meta property="og:image" content="${attr(site+image)}">`:'',
  `<link rel="manifest" href="manifest.webmanifest">`,`<link rel="apple-touch-icon" href="icons/apple-touch-icon.png">`,`<link rel="icon" href="icons/icon-192.png">`,
  `<meta name="mobile-web-app-capable" content="yes">`,`<meta name="apple-mobile-web-app-capable" content="yes">`,`<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">`,
  `<meta name="apple-mobile-web-app-title" content="${attr(config.title||'EventMap')}">`,
  `<link rel="stylesheet" href="shell.css">`].filter(Boolean).join('');
html=html.replace(/<title>[^<]*<\/title>/,`<title>${attr(title)}</title>`).replace('</head>',`${head}</head>`).replace('</body>',`<script type="module" src="shell.js"></script></body>`);
if(config.apiEvents&&/^https:\/\//.test(config.apiEvents)){
  const origin=new URL(config.apiEvents).origin;
  if(!html.includes(origin)){if(!html.includes("connect-src 'self'"))throw new Error('Engine CSP has no connect-src to extend');html=html.replace("connect-src 'self'",`connect-src 'self' ${origin}`);}
}
// Live weather for «Авто» (Open-Meteo by default): only that origin, and only over HTTPS.
if(config.weatherUrl&&/^https:\/\//.test(config.weatherUrl)){
  const origin=new URL(config.weatherUrl).origin;
  if(!html.includes(origin)){if(!html.includes("connect-src 'self'"))throw new Error('Engine CSP has no connect-src to extend');html=html.replace("connect-src 'self'",`connect-src 'self' ${origin}`);}
}
// The engine CSP is default-src 'none': the web build allows its own manifest only.
if(!html.includes("default-src 'none';"))throw new Error('Engine CSP changed: update the manifest-src patch');
html=html.replace("default-src 'none';","default-src 'none'; manifest-src 'self';");
await writeFile(path.join(out,'index.html'),html);
console.log(`Web build ready: ${events.length} events, ${Object.keys(config.covers).length} category covers, ${assets} city content files → ${path.relative(process.cwd(),out)||'dist'}`);
