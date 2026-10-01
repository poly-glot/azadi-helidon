// Drives the Chrome on the Mac host over CDP: loads pages, records console/network errors, saves screenshots.
import fs from 'fs';
const HOST = process.env.CDP || '192.168.65.254:9222', BASE = process.env.BASE || 'http://localhost:8080';
const pages = process.argv.slice(2), out = 'target/shots';
const t = await (await fetch(`http://${HOST}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(t.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
let id = 0; const pend = new Map(); let issues = [];
ws.onmessage = e => { const m = JSON.parse(e.data);
  if (m.id && pend.has(m.id)) { pend.get(m.id)(m); pend.delete(m.id); }
  else if (m.method === 'Runtime.exceptionThrown') issues.push('EXC ' + m.params.exceptionDetails.text);
  else if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') issues.push('log ' + m.params.entry.text + ' ' + (m.params.entry.url || ''));
  else if (m.method === 'Network.responseReceived' && m.params.response.status >= 400) issues.push('HTTP ' + m.params.response.status + ' ' + m.params.response.url);
  else if (m.method === 'Network.loadingFailed') issues.push('FAILED ' + m.params.errorText + ' ' + (m.params.requestId)); };
const send = (method, params = {}) => new Promise(r => { const i = ++id; pend.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async x => (await send('Runtime.evaluate', { expression: x, returnByValue: true })).result?.result?.value;
const sleep = ms => new Promise(r => setTimeout(r, ms));
for (const d of ['Page', 'Runtime', 'Network', 'Log']) await send(d + '.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false });
for (const p of pages) {
  issues = [];
  await send('Page.navigate', { url: BASE + p }); await sleep(2500);
  const shot = await send('Page.captureScreenshot', { format: 'png' });
  const file = `${out}/${p.replace(/\W+/g, '_') || 'root'}.png`; fs.writeFileSync(file, Buffer.from(shot.result.data, 'base64'));
  console.log(JSON.stringify({ page: p, title: await ev('document.title'), textLength: await ev('document.body.innerText.length'),
    cssLoaded: await ev(`[...document.styleSheets].some(s=>{try{return s.cssRules.length>50}catch(e){return true}})`), issues: [...new Set(issues)], screenshot: file }));
}
await fetch(`http://${HOST}/json/close/${t.id}`); ws.close();
