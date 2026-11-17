const http=require('http');
http.createServer(async(req,res)=>{
  if(req.method==='POST'&&req.url==='/__control'){
    const body=typeof req.body==='string'?JSON.parse(req.body):req.body;
    if(!['retry','healthy','offline','malformed'].includes(body?.mode)){res.writeHead(400).end();return;}
    await pm.state.set('mode',body.mode);await pm.state.set('epoch',String(Date.now()));res.end('{}');return;
  }
  if(req.method!=='GET'||!(/^\/planet\/(latest|simulation\/\d+\/\d+\/\d+\.pbf)$/.test(req.url))){res.writeHead(501).end();return;}
  const mode=await pm.state.get('mode')||'retry',epoch=await pm.state.get('epoch')||'initial',key=`attempt:${epoch}:${req.url}`;
  const n=(await pm.state.get(key)||0)+1;await pm.state.set(key,n);
  if(mode==='offline'||mode==='retry'&&n===1){res.writeHead(503).end('{}');return;}
  if(req.url==='/planet/latest'){
    res.setHeader('Content-Type','application/json');res.end(JSON.stringify({tiles:[`https://${mode==='malformed'?'invalid.example':'tiles.openfreemap.org'}/planet/simulation/{z}/{x}/{y}.pbf`]}));return;
  }
  res.setHeader('Content-Type','application/vnd.mapbox-vector-tile');res.end(Buffer.alloc(0));
}).listen(process.env.PORT);
