import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';

const base = process.argv.find(arg => arg.startsWith('--base='))?.slice(7) || 'http://localhost:8765';
const basic = (email, password) => 'Basic ' + Buffer.from(`${email}:${password}`).toString('base64');
const auth = { OWNER: basic('owner@soas.local','Owner123!'), ADMIN: basic('admin@soas.local','Admin123!'), USER: basic('user@soas.local','User123!') };
const results = [];
async function api(path, role, method = 'GET', body, expected = 200) {
  const response = await fetch(base + path, { method, headers: { ...(role ? { Authorization: auth[role] || role } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(45000) });
  const text = await response.text(); const value = text ? JSON.parse(text) : null;
  assert.equal(response.status, expected, `${method} ${path}: ${response.status} ${text}`);
  if (value && !Array.isArray(value)) assert.equal('trace' in value || 'stackTrace' in value, false);
  return value;
}
async function test(name, action) { try { await action(); results.push({ name, passed: true }); console.log(`PASS ${name}`); } catch (error) { results.push({ name, passed: false, message: error.message }); console.error(`FAIL ${name}: ${error.message}`); throw error; } }
const email = `smoke-${Date.now()}@soas.local`; let user, currentEmail=email;
try {
  await test('Public fiat and crypto lookup', async () => { for (const path of ['/currency-exchange?from=EUR&to=RSD','/crypto-exchange?from=BTC&to=ETH']) assert(Number((await api(path)).rate) > 0); });
  await test('Provider-backed fiat catalog and HTTP statuses', async () => {
    const currencies = await api('/currency-exchange/assets');
    assert(currencies.includes('NZD'));
    assert(!currencies.includes('BTC') && !currencies.includes('XAU'));
    assert(Number((await api('/currency-exchange?from=EUR&to=NZD')).rate) > 0);
    await api('/currency-exchange?from=EUR&to=USD', null, 'POST', null, 405);
    await api('/currency-exchange?from=ZZZ&to=USD', null, 'GET', null, 400);
    await api('/users', 'OWNER', 'POST', null, 400);
    await api('/bank-accounts/holdings', 'ADMIN', 'POST',
      {email:'missing-user@soas.local',currency:'NZD',amount:'100'}, 404);
  });
  await test('Basic Authentication', async () => { await api('/users',null,'GET',null,401); await api('/auth/me',basic('user@soas.local','wrong'),'GET',null,401); assert.equal((await api('/auth/me','USER')).role,'USER'); });
  await test('Forbidden role matrix', async () => { for (const role of ['OWNER','ADMIN']) for (const path of ['/currency-conversion?from=EUR&to=RSD&quantity=1','/trade-service?from=EUR&to=BTC&quantity=1']) await api(path,role,'GET',null,403); await api('/users','USER','GET',null,403); await api('/bank-accounts','OWNER','GET',null,403); await api('/crypto-wallets','OWNER','GET',null,403); });
  await test('USER lifecycle provisions zero defaults', async () => { user=await api('/users','OWNER','POST',{email,password:'SmokeTest123!',role:'USER'},201); const bank=await api(`/bank-accounts/${email}`,'ADMIN'),wallet=await api(`/crypto-wallets/${email}`,'ADMIN'); assert.equal(bank.holdings[0].currency,'EUR'); assert.equal(Number(bank.holdings[0].amount),0); assert.equal(wallet.holdings[0].currency,'ETH'); assert.equal(Number(wallet.holdings[0].amount),0); });
  const own=basic(email,'SmokeTest123!');
  await test('Management restrictions and ownership', async () => { await api('/users','ADMIN','POST',{email:'invalid@soas.local',password:'SmokeTest123!',role:'ADMIN'},403); await api(`/users/${user.id}`,'ADMIN','DELETE',null,403); await api(`/bank-accounts/${email}`,'USER','GET',null,403); await api('/bank-accounts/holdings',own,'PUT',{email,currency:'EUR',amount:'900'},403); });
  await test('ADMIN funds test account', async () => { await api('/bank-accounts/holdings','ADMIN','PUT',{email,currency:'EUR',amount:'10000'}); await api('/crypto-wallets/holdings','ADMIN','PUT',{email,currency:'ETH',amount:'2'}); for (const currency of ['USD','RSD']) await api('/bank-accounts/holdings','ADMIN','POST',{email,currency,amount:'10000'}); });
  await test('Fiat conversion updates bank holdings', async () => { const tx=await api('/currency-conversion?from=EUR&to=RSD&quantity=10',own); assert(Number(tx.received)>0); assert(tx.bankAccount.holdings.some(h=>h.currency==='EUR'&&Number(h.amount)===9990)); });
  await test('Provider fiat works in holdings, conversion and both crypto bridges', async () => {
    await api('/bank-accounts/holdings', 'ADMIN', 'POST', {email,currency:'NZD',amount:'1000'});
    for (const [service, from, to, quantity] of [
      ['currency-conversion','EUR','NZD','10'],
      ['trade-service','NZD','BTC','10'],
      ['trade-service','ETH','NZD','.01']
    ]) {
      const tx = await api(`/${service}?${new URLSearchParams({from,to,quantity})}`, own);
      assert(Number(tx.received) > 0);
      const destination = tx.wallet || tx.bankAccount;
      assert(destination.holdings.some(h => h.currency === to && Number(h.amount) > 0));
      assert(tx.report.includes(from) && tx.report.includes(to));
    }
  });
  await test('All crypto trade modes including fiat bridges', async () => { for (const [from,to,quantity] of [['ETH','BTC','.01'],['EUR','BTC','10'],['USD','ETH','10'],['RSD','BTC','100'],['ETH','EUR','.01'],['ETH','USD','.01'],['ETH','RSD','.01']]) { const tx=await api(`/trade-service?${new URLSearchParams({from,to,quantity})}`,own); assert(Number(tx.received)>0); assert(tx.report.includes(from)&&tx.report.includes(to)); } });
  await test('Validation and insufficient funds return clean errors', async () => { await api('/currency-conversion?from=EUR&to=RSD&quantity=0',own,'GET',null,400); await api('/trade-service?from=ETH&to=USD&quantity=99999999',own,'GET',null,400); await api('/currency-exchange?from=INVALID&to=RSD',null,'GET',null,400); });
  await test('Email update moves bank and wallet ownership', async () => { currentEmail=`renamed-${email}`; await api(`/users/${user.id}`,'OWNER','PUT',{email:currentEmail,password:null,role:'USER'}); assert((await api(`/bank-accounts/${currentEmail}`,'ADMIN')).holdings.length>0); assert.equal((await api(`/bank-accounts/${email}`,'ADMIN')).holdings.length,0); });
  await test('Role changes remove and reprovision holdings', async () => { await api(`/users/${user.id}`,'OWNER','PUT',{email:currentEmail,password:null,role:'ADMIN'}); assert.equal((await api(`/bank-accounts/${currentEmail}`,'ADMIN')).holdings.length,0); await api(`/users/${user.id}`,'OWNER','PUT',{email:currentEmail,password:null,role:'USER'}); const wallet=await api(`/crypto-wallets/${currentEmail}`,'ADMIN'); assert.equal(wallet.holdings[0].currency,'ETH'); assert.equal(Number(wallet.holdings[0].amount),0); });
  await test('USER deletion cleans both services', async () => { await api(`/users/${user.id}`,'OWNER','DELETE',null,204); user=null; for (const path of ['bank-accounts','crypto-wallets']) assert.equal((await api(`/${path}/${currentEmail}`,'ADMIN')).holdings.length,0); });
} catch { process.exitCode=1; }
finally {
  if (user) { try { await api(`/users/${user.id}`,'OWNER','DELETE',null,204); } catch (error) { console.error('Test-user cleanup failed:',error.message); } }
  mkdirSync('.runtime',{recursive:true});
  writeFileSync('.runtime/smoke-results.json',JSON.stringify({at:new Date().toISOString(),base,results},null,2));
  console.log(`${results.filter(r=>r.passed).length}/${results.length} smoke checks passed.`);
}
