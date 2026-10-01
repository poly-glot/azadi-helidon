// Real user journey in the Chrome on the Mac (CDP): login form -> account -> payment attempt -> logout.
// Reports HTTP status of each navigation/POST, console/CSP errors, and saves screenshots to target/shots.
import fs from 'fs';
const HOST = process.env.CDP || '192.168.65.254:9222', BASE = process.env.BASE || 'http://localhost:8080';
const t = await (await fetch(`http://${HOST}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(t.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
let id = 0; const pend = new Map(); let issues = [], posts = [];
ws.onmessage = e => { const m = JSON.parse(e.data);
  if (m.id && pend.has(m.id)) { pend.get(m.id)(m); pend.delete(m.id); }
  else if (m.method === 'Runtime.exceptionThrown') issues.push('EXC ' + m.params.exceptionDetails.text + ' ' + (m.params.exceptionDetails.exception?.description || '').slice(0, 120));
  else if (m.method === 'Log.entryAdded' && ['error'].includes(m.params.entry.level)) issues.push(`${m.params.entry.source}: ${m.params.entry.text.slice(0, 160)} ${m.params.entry.url || ''}`);
  else if (m.method === 'Network.responseReceived') { const r = m.params.response; if (r.status >= 400 && !/stripe\.com/.test(r.url)) issues.push('HTTP ' + r.status + ' ' + r.url); if (m.params.type === 'Fetch' || m.params.type === 'XHR') posts.push(r.status + ' ' + r.url); }
  else if (m.method === 'Network.loadingFailed' && !/stripe/.test(m.params.errorText)) issues.push('FAILED ' + m.params.errorText); };
const send = (method, params = {}) => new Promise(r => { const i = ++id; pend.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async x => (await send('Runtime.evaluate', { expression: x, returnByValue: true, awaitPromise: true })).result?.result?.value;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const shot = async n => fs.writeFileSync(`target/shots/journey-${n}.png`, Buffer.from((await send('Page.captureScreenshot', { format: 'png' })).result.data, 'base64'));
const step = async (name, fn) => { issues = []; posts = []; const out = await fn(); console.log(JSON.stringify({ step: name, ...out, fetch: posts, issues: [...new Set(issues)] })); };
for (const d of ['Page', 'Runtime', 'Network', 'Log']) await send(d + '.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false });
await send('Network.clearBrowserCookies');

await step('open /login', async () => { await send('Page.navigate', { url: BASE + '/login' }); await sleep(1800); await shot('1-login');
  return { title: await ev('document.title'), path: await ev('location.pathname') }; });
await step('submit login form', async () => {
  await ev(`(()=>{const s=(i,v)=>document.querySelector(i).value=v; s('#dob-day','15'); s('#dob-month',[...document.querySelector('#dob-month').options].find(o=>/03|Mar/.test(o.text+o.value)).value); s('#dob-year','1985'); s('#postcode','SW1A 1AA'); s('#agreement','AGR-100001'); document.querySelector('#login-form').requestSubmit();})()`);
  await sleep(2500); await shot('2-after-login');
  return { title: await ev('document.title'), path: await ev('location.pathname'), greeting: await ev(`document.querySelector('.c-toolbar__greeting')?.innerText`), cookiesVisibleToJs: await ev('document.cookie.split(";").map(c=>c.split("=")[0].trim()).join(",")') }; });
await step('open make-a-payment', async () => { await send('Page.navigate', { url: BASE + '/finance/make-a-payment' }); await sleep(3500); await shot('3-payment');
  return { title: await ev('document.title'), stripeLoaded: await ev('typeof Stripe'), cardElementMounted: await ev('document.querySelectorAll("#card-element iframe").length > 0'), agreementOptions: await ev('document.querySelector("#agreementId")?.options.length') }; });
await step('click Pay (JS -> our POST -> fake Stripe -> Stripe.js confirm)', async () => {
  await ev(`document.querySelector('#amount').value='10.00'`); await ev(`document.querySelector('#payment-form').requestSubmit()`); await sleep(5000); await shot('4-after-pay');
  return { cardErrors: await ev('document.querySelector("#card-errors")?.innerText') }; });
await step('logout', async () => { await send('Page.navigate', { url: BASE + '/my-account' }); await sleep(1500);
  await ev(`document.querySelector('.c-site-header__logout-form').requestSubmit()`); await sleep(2000);
  const afterLogout = await ev('location.pathname'); await send('Page.navigate', { url: BASE + '/my-account' }); await sleep(1500);
  return { afterLogout, revisitProtectedPage: await ev('location.pathname') }; });
await fetch(`http://${HOST}/json/close/${t.id}`); ws.close();
