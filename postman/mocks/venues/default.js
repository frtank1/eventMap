// Locally owned read-contract, API_CONTRACT.md. Synthetic data, no production writes.
const http=require('http');
const polygon=[{x:0,y:0},{x:20,y:0},{x:20,y:20},{x:0,y:20}];
const floor=id=>({id,name:`Тестовый этаж ${id}`,order:id==='l1'?1:2,plan:{width:100,height:80,outline:[]},rooms:[{id:'atrium',name:'Тестовый атриум',polygon}]});
http.createServer(async(req,res)=>{
  res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-cache');
  const url=new URL(req.url,'http://localhost');
  if(req.method==='POST'&&url.pathname==='/__control'){
    const body=typeof req.body==='string'?JSON.parse(req.body):req.body;
    if(!['empty','v1','v2','offline','slow','bad','oversize','incompatible'].includes(body?.mode)){res.writeHead(400);res.end('{}');return;}
    await pm.state.set('venues.mode',body.mode);res.end(JSON.stringify(body));return;
  }
  if(req.method!=='GET'){res.writeHead(501);res.end('{}');return;}
  const mode=await pm.state.get('venues.mode')||'empty';
  if(url.pathname==='/events'){
    if(url.searchParams.get('buildingId')!=='landmark:mega'){res.writeHead(400);res.end('{}');return;}
    // Deliberately include extra records to test client defence against an old/misconfigured server.
    res.end(JSON.stringify([
      {id:'inside',title:'Synthetic event',venue:{buildingId:'landmark:mega',floorId:'l1',roomId:'atrium'}},
      {id:'building-only',title:'No floor yet',venue:{buildingId:'landmark:mega'}},
      {id:'neighbour',title:'Another building',venue:{buildingId:'landmark:arena'}},
      {id:'private',title:'Not public',visibility:'invite',venue:{buildingId:'landmark:mega'}}
    ]));return;
  }
  if(url.pathname!=='/venues/landmark:mega'){res.writeHead(501);res.end('{}');return;}
  if(mode==='offline'){res.writeHead(503);res.end('{}');return;}
  if(mode==='slow')await new Promise(r=>setTimeout(r,11000));
  if(mode==='bad'){res.end('{bad');return;}
  if(mode==='oversize'){res.end(JSON.stringify({data:'x'.repeat(1024*1024+1)}));return;}
  res.end(JSON.stringify({schemaVersion:mode==='incompatible'?99:1,buildingId:'landmark:mega',revision:mode,name:'Синтетический тест, не план MEGA',
    floors:mode==='empty'?[]:mode==='v2'?[floor('l1'),floor('l2')]:[floor('l1')]}));
}).listen(process.env.PORT);
