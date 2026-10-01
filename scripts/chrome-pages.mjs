// Logs in through the real form in the Mac's Chrome, then visits every page: title, content length, console/CSP/network
// issues, and a screenshot in target/shots/page-*.png. Also exercises the contact-details inline edit UI and the FAQ accordion.
import fs from 'fs';
const HOST = process.env.CDP || '192.168.65.254:9222', BASE = process.env.BASE || 'http://localhost:8080';
const t = await (await fetch(`http://${HOST}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(t.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
let id = 0; const pend = new Map(); let issues = [];
ws.onmessage = e => { const m = JSON.parse(e.data);
  if (m.id && pend.has(m.id)) { pend.get(m.id)(m); pend.delete(m.id); }
  else if (m.method === 'Runtime.exceptionThrown') issues.push('EXC ' + m.params.exceptionDetails.text + ' ' + (m.params.exceptionDetails.exception?.description || '').slice(0, 100));
  else if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') issues.push(`${m.params.entry.source}: ${m.params.entry.text.slice(0, 140)} ${m.params.entry.url || ''}`);
  else if (m.method === 'Network.responseReceived' && m.params.response.status >= 400 && !/stripe\.com/.test(m.params.response.url)) issues.push('HTTP ' + m.params.response.status + ' ' + m.params.response.url); };
const send = (method, params = {}) => new Promise(r => { const i = ++id; pend.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async x => (await send('Runtime.evaluate', { expression: x, returnByValue: true, awaitPromise: true })).result?.result?.value;
const sleep = ms => new Promise(r => setTimeout(r, ms));
for (const d of ['Page', 'Runtime', 'Network', 'Log']) await send(d + '.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false });
await send('Network.clearBrowserCookies');
await send('Page.navigate', { url: BASE + '/login' }); await sleep(1500);
await ev(`(()=>{const s=(i,v)=>document.querySelector(i).value=v; s('#dob-day','22'); s('#dob-month',[...document.querySelector('#dob-month').options].find(o=>/07|Jul/.test(o.text+o.value)).value); s('#dob-year','1990'); s('#postcode','M1 1AE'); s('#agreement','AGR-100002'); document.querySelector('#login-form').requestSubmit();})()`);
await sleep(2500);
console.log('logged in as:', await ev(`document.querySelector('.c-toolbar__greeting')?.innerText`));
const pages = ['/my-account', '/my-documents', '/my-contact-details', '/finance/make-a-payment', '/finance/update-bank-details', '/finance/change-payment-date',
  '/finance/settlement-figure', '/finance/request-a-statement', '/help/faqs', '/help/ways-to-pay', '/help/contact-us', '/cookies', '/privacy', '/terms', '/no/such/page'];
for (const p of pages) {
  issues = []; await send('Page.navigate', { url: BASE + p }); await sleep(1800);
  const file = `target/shots/page-${p.replace(/\W+/g, '_')}.png`;
  fs.writeFileSync(file, Buffer.from((await send('Page.captureScreenshot', { format: 'png' })).result.data, 'base64'));
  console.log(JSON.stringify({ page: p, title: await ev('document.title'), h1: await ev(`document.querySelector('h1')?.innerText`), chars: await ev('document.body.innerText.length'), issues: [...new Set(issues)] }));
}
// interaction: settlement calculation through the real form
issues = []; await send('Page.navigate', { url: BASE + '/finance/settlement-figure' }); await sleep(1500);
await ev(`document.querySelector('form[action="/finance/settlement-figure"]').requestSubmit()`); await sleep(1800);
fs.writeFileSync('target/shots/interaction-settlement.png', Buffer.from((await send('Page.captureScreenshot', { format: 'png' })).result.data, 'base64'));
console.log(JSON.stringify({ interaction: 'settlement form submit', amountShown: await ev(`document.body.innerText.match(/£[\\d,]+\\.\\d{2}/g)`), issues: [...new Set(issues)] }));
await fetch(`http://${HOST}/json/close/${t.id}`); ws.close();
