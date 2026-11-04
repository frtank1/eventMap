// Only the outbound endpoint EventMap calls; other operations intentionally return 501.
// Based on https://www.mux.com/docs/api-reference/video/direct-uploads
const http = require('http');
http.createServer((req, res) => {
  res.setHeader('content-type', 'application/json');
  if (req.method !== 'POST' || req.url !== '/video/v1/uploads') {
    res.writeHead(501); res.end(JSON.stringify({error: {type: 'not_modeled'}})); return;
  }
  const auth = 'Basic ' + Buffer.from('test-id:test-secret').toString('base64');
  if (req.headers.authorization !== auth) {
    res.writeHead(401); res.end(JSON.stringify({error: {type: 'unauthorized'}})); return;
  }
  const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
  const settings = body?.new_asset_settings;
  if (!settings || settings.playback_policies?.join() !== 'signed' ||
      settings.video_quality !== 'basic' || !settings.passthrough || body.timeout !== 3600) {
    res.writeHead(400); res.end(JSON.stringify({error: {type: 'invalid_parameters'}})); return;
  }
  if (settings.passthrough === 'simulate-503') {
    res.writeHead(503); res.end(JSON.stringify({error: {type: 'service_unavailable'}})); return;
  }
  if (settings.passthrough === 'simulate-html') {
    res.writeHead(502, {'content-type': 'text/html'}); res.end('<h1>Upstream unavailable</h1>'); return;
  }
  res.writeHead(201);
  res.end(JSON.stringify({data: {id: 'sim-upload-id', status: 'waiting', timeout: 3600,
    url: 'https://upload.example.invalid/signed-upload', new_asset_settings: settings}}));
}).listen(process.env.PORT);
