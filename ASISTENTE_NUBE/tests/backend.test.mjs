import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync, sign} from 'node:crypto';
import {server, verifyFirebaseToken} from '../server.mjs';
function makeJwt(privateKey, extra={}) {
  const now=Math.floor(Date.now()/1000);
  const h={alg:'RS256',kid:'testing',typ:'JWT'};
  const c={aud:'ecoresilia',iss:'https://securetoken.google.com/ecoresilia',sub:'test-user',exp:now+3600,iat:now-10,auth_time:now-10,...extra};
  const body=[Buffer.from(JSON.stringify(h)).toString('base64url'),Buffer.from(JSON.stringify(c)).toString('base64url')].join('.');
  return body+'.'+sign('RSA-SHA256',Buffer.from(body),privateKey).toString('base64url');
}
test('valid token and invalid project/signature',async()=>{
  const {privateKey,publicKey}=generateKeyPairSync('rsa',{modulusLength:2048});
  const token=makeJwt(privateKey);
  assert.equal(await verifyFirebaseToken(token,{keys:{testing:publicKey.export({type:'spki',format:'pem'})}}),'test-user');
  await assert.rejects(verifyFirebaseToken(makeJwt(privateKey,{aud:'wrong'}),{keys:{testing:publicKey.export({type:'spki',format:'pem'})}}));
  await assert.rejects(verifyFirebaseToken(token+'garbage',{keys:{testing:publicKey.export({type:'spki',format:'pem'})}}));
});
test('health exposes only status, no secret; unconfigured fails closed',async()=>{
  await new Promise((resolve)=>server.listen(0,'127.0.0.1',resolve));
  try{
    const url=`http://127.0.0.1:${server.address().port}`;
    const h=await fetch(url+'/health');
    assert.equal(h.status,200);
    const health=await h.json();
    assert.equal(health.status,'needs_key');
    const p=await fetch(url+'/api/assistant',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({messages:[{role:'user',content:'Huaicos'}]})});
    assert.equal(p.status,503);
  } finally {await new Promise(resolve=>server.close(resolve));}
});
