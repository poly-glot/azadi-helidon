// Local stand-ins so the flows can be tested end to end without real Stripe / GCP:
//   :12111  fake Stripe API  (POST /v1/payment_intents)   -> requests logged to target/fake-stripe.log
//   :12113  fake Resend API (POST /emails)               -> requests logged to target/fake-resend.log
//   :12112  fake GCE metadata server (ADC token endpoint) -> requests logged to target/fake-metadata.log
import http from 'http';
import fs from 'fs';
import path from 'path';
const logDir = path.join(import.meta.dirname, '..', 'target');   // same place the check scripts read, wherever this is started from
fs.mkdirSync(logDir, { recursive: true });
const log = (file, line) => fs.appendFileSync(path.join(logDir, path.basename(file)), line + '\n');
let n = 0;
http.createServer((req, res) => {
  let body = ''; req.on('data', c => body += c);
  req.on('end', () => {
    log('target/fake-stripe.log', `${req.method} ${req.url} auth=${req.headers.authorization} body=${body}`);
    const id = `pi_fake_${++n}`;
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ id, object: 'payment_intent', amount: Number(new URLSearchParams(body).get('amount')), currency: 'gbp',
      client_secret: `${id}_secret_fake`, status: 'requires_payment_method', livemode: false }));
  });
}).listen(12111);
http.createServer((req, res) => {
  log('target/fake-metadata.log', `${req.method} ${req.url} flavor=${req.headers['metadata-flavor']}`);
  res.setHeader('Metadata-Flavor', 'Google');
  if (req.url.includes('/token')) { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ access_token: 'fake-token', expires_in: 3600, token_type: 'Bearer' })); }
  else if (req.url.includes('/email')) res.end('fake@demo-azadi.iam.gserviceaccount.com');
  else res.end('');
}).listen(12112);
http.createServer((req, res) => {
  let body = ''; req.on('data', c => body += c);
  req.on('end', () => { const j = JSON.parse(body || '{}'); log('target/fake-resend.log', `${req.method} ${req.url} auth=${req.headers.authorization} to=${j.to} subject=${j.subject}`);
    res.writeHead(200, { 'Content-Type': 'application/json' }); res.end('{"id":"email_fake"}'); });
}).listen(12113);
console.log('fakes listening on 12111 (stripe) and 12112 (metadata)');
