/**
 * EcoResilia RC4.3 · Backend HTTPS para asistente de prevención.
 * Node >=22. Autentica Firebase ID tokens con claves públicas de Google.
 * Sin claves incrustadas; límite de solicitudes conservador para demostraciones.
 * Proveedores compatibles: Gemini, NVIDIA y OpenRouter.
 */
import http from 'node:http';
import { createVerify } from 'node:crypto';

const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'ecoresilia';
const PORT = Number(process.env.PORT || 8787);
const AI_PROVIDER = (process.env.AI_PROVIDER || 'gemini').toLowerCase();
const MODEL = process.env.AI_MODEL || (AI_PROVIDER === 'gemini' ? 'gemini-2.5-flash' : AI_PROVIDER === 'nvidia' ? 'meta/llama-3.3-70b-instruct' : '');
const key = AI_PROVIDER === 'gemini' ? process.env.GEMINI_API_KEY : AI_PROVIDER === 'nvidia' ? process.env.NVIDIA_API_KEY : process.env.OPENROUTER_API_KEY;
const allowedProviders = new Set(['gemini','nvidia','openrouter']);
const publicKeysEndpoint = 'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';
const userCalls = new Map();
let publicKeys = {}, publicKeysExpireAt = 0;
let activeCalls = 0;
const SYSTEM = `Eres EcoResilia, asistente educativo para la prevención de desastres en Perú. Responde de manera personalizada y clara, NO con plantillas fijas. Conversa fluidamente en español. Ámbitos: preparación familiar, mochila de emergencia, niños, adultos mayores, mascotas, huaicos, sismos, inundaciones y consulta de fuentes SENAMHI, INDECI y Defensa Civil. No tienes acceso en tiempo real a reportes oficiales salvo que el backend explícitamente te proporcione datos verificados (no ocurre en esta versión). No inventes avisos, pronósticos recientes, riesgos locales, rutas seguras ni ubicaciones de refugios. No ordenes evacuaciones concretas sin información oficial. Si la persona describe peligro inmediato, recomienda ponerse a salvo sin cruzar cauces ni corrientes, y contactar Bomberos 116, Policía 105 o Defensa Civil local. No pidas datos personales ni detalles clínicos. No diagnostiques edificios o personas. Distingue entre simulacro, reporte ciudadano y comunicado oficial. Evita respuestas demasiado largas y pregunta solo cuando una precisión ayude.`;

function response(res,status,payload) {
  const raw = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Strict-Transport-Security': 'max-age=31536000'
  });
  res.end(raw);
}
function decodePart(value) {
  if (typeof value !== 'string' || value.length > 5000) throw Error('jwt_format');
  return JSON.parse(Buffer.from(value, 'base64url').toString('utf8'));
}
async function getKeys() {
  if (Date.now() < publicKeysExpireAt && Object.keys(publicKeys).length) return publicKeys;
  const ctrl = new AbortController(); const timer = setTimeout(()=>ctrl.abort(), 7000);
  try {
    const res = await fetch(publicKeysEndpoint, {signal:ctrl.signal});
    if (!res.ok) throw Error('google_keys_unavailable');
    const keys = await res.json();
    if (!keys || typeof keys !== 'object' || Object.keys(keys).length < 1) throw Error('google_keys_invalid');
    const m = res.headers.get('cache-control')?.match(/max-age=(\d+)/i);
    publicKeys = keys;
    publicKeysExpireAt = Date.now() + Math.min(Number(m?.[1] || 1800), 7200) * 1000;
    return publicKeys;
  } finally { clearTimeout(timer); }
}
export async function verifyFirebaseToken(token, options={}) {
  if (typeof token !== 'string' || token.length > 5000 || token.length < 100) throw Error('token_missing');
  const parts=token.split('.');
  if(parts.length !== 3) throw Error('jwt_format');
  const header=decodePart(parts[0]);
  const claims=decodePart(parts[1]);
  const now=Math.floor(Date.now()/1000);
  if (header.alg !== 'RS256' || typeof header.kid !== 'string' || !header.kid) throw Error('invalid_header');
  if (claims.aud !== PROJECT_ID || claims.iss !== `https://securetoken.google.com/${PROJECT_ID}`) throw Error('invalid_project');
  if (typeof claims.sub !== 'string' || claims.sub.length < 1 || claims.sub.length > 128) throw Error('invalid_subject');
  if (!Number.isFinite(claims.exp) || claims.exp <= now || !Number.isFinite(claims.iat) || claims.iat > now || !Number.isFinite(claims.auth_time) || claims.auth_time > now) throw Error('invalid_time');
  const keys=options.keys || await getKeys();
  if (!Object.prototype.hasOwnProperty.call(keys,header.kid)) throw Error('unknown_key');
  const verify=createVerify('RSA-SHA256');
  verify.update(`${parts[0]}.${parts[1]}`);
  verify.end();
  if (!verify.verify(keys[header.kid], Buffer.from(parts[2], 'base64url'))) throw Error('invalid_signature');
  return claims.sub;
}
function checkRate(uid) {
  const hour = Math.floor(Date.now() / 3600000);
  const entry = userCalls.get(uid);
  const current = entry?.hour === hour ? entry.count : 0;
  if (current >= 12) return false;
  userCalls.set(uid, {hour, count:current+1});
  if (userCalls.size > 5000) userCalls.clear();
  return true;
}
function safeHousehold(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const fields = ['adults','children','babies','olderAdults','pets'];
  if (!fields.every(k => Number.isInteger(value[k]) && value[k] >= 0 && value[k] <= 20)) return null;
  const people = value.adults + value.children + value.babies + value.olderAdults;
  if (people < 1 || people > 20) return null;
  return Object.fromEntries(fields.map(k => [k, value[k]]));
}
function sanitize(body) {
  if (!body || !Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > 10) throw Error('invalid_messages');
  const items = body.messages.map((m)=>({
    role: m?.role === 'assistant' ? 'assistant' : 'user',
    content: typeof m?.content === 'string' ? m.content.trim().slice(0,1000) : ''
  })).filter((m)=> m.content.length);
  if (!items.length || items.at(-1).role !== 'user') throw Error('invalid_messages');
  const place = typeof body.place === 'string' ? body.place.trim().slice(0,80) : 'Perú';
  return {messages: items, place, household:safeHousehold(body.household)};
}
async function generate(messages, place, household) {
  const ctrl=new AbortController(); const timer=setTimeout(()=>ctrl.abort(), 50000);
  try {
    const family = household ? ` Composición del hogar declarada por el usuario (solo cantidades, no verificada): ${household.adults} adultos, ${household.children} niños, ${household.babies} bebés, ${household.olderAdults} adultos mayores y ${household.pets} mascotas. Personaliza suministros y recomendaciones a ese perfil cuando sea relevante, sin inventar enfermedades, cantidades oficiales o necesidades médicas.` : '';
    const system = SYSTEM + ` Región elegida en la app (no verificable): ${place}.` + family;
    let endpoint, headers, payload;
    if (AI_PROVIDER === 'gemini') {
      if (!/^[a-zA-Z0-9_.-]+$/.test(MODEL)) throw Error('model_invalid');
      endpoint=`https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`;
      headers={ 'Content-Type':'application/json', 'x-goog-api-key':key };
      payload={systemInstruction:{parts:[{text:system}]}, contents:messages.map(m=>({role:m.role==='assistant'?'model':'user',parts:[{text:m.content}]})), generationConfig:{temperature:0.25,maxOutputTokens:900}};
    } else {
      endpoint=AI_PROVIDER==='nvidia' ? 'https://integrate.api.nvidia.com/v1/chat/completions' : 'https://openrouter.ai/api/v1/chat/completions';
      headers={'Content-Type':'application/json',Authorization:`Bearer ${key}`};
      payload={model:MODEL,messages:[{role:'system',content:system},...messages],temperature:0.25,max_tokens:750,stream:false};
    }
    const res=await fetch(endpoint,{method:'POST',headers,body:JSON.stringify(payload),signal:ctrl.signal});
    if (!res.ok) { console.error('Proveedor IA HTTP',res.status); throw Error('provider_error'); }
    const json=await res.json();
    const answer=AI_PROVIDER==='gemini' ? (json?.candidates?.[0]?.content?.parts || []).map(p=>p?.text || '').join('') : json?.choices?.[0]?.message?.content;
    if (!answer || typeof answer !== 'string') throw Error('provider_empty');
    return answer.trim().slice(0,3500);
  } finally { clearTimeout(timer); }
}
async function requestHandler(req,res) {
  if(req.method==='GET' && req.url==='/health') return response(res,200,{status:key && MODEL && allowedProviders.has(AI_PROVIDER)?'ready':'needs_key',provider:AI_PROVIDER,model:MODEL,auth:'firebase_id_token'});
  if(req.method!=='POST' || req.url!=='/api/assistant') return response(res,404,{error:'not_found'});
  if (!key || !MODEL || !allowedProviders.has(AI_PROVIDER)) return response(res,503,{error:'ai_not_configured'});
  const header=req.headers.authorization || '';
  if (!header.startsWith('Bearer ')) return response(res,401,{error:'firebase_auth_required'});
  let uid;
  try { uid=await verifyFirebaseToken(header.slice(7).trim()); }
  catch { return response(res,401,{error:'invalid_firebase_token'}); }
  if (!checkRate(uid)) return response(res,429,{error:'too_many_requests',message:'Limite de 12 mensajes por hora para esta demostracion'});
  if (activeCalls >= 4) return response(res,503,{error:'server_busy'});
  let payload;
  try {
    let raw='';
    for await (const chunk of req) {
      raw += chunk.toString('utf8');
      if (Buffer.byteLength(raw,'utf8') > 14000) return response(res,413,{error:'request_too_large'});
    }
    payload=sanitize(JSON.parse(raw));
  } catch { return response(res,400,{error:'invalid_request'}); }
  activeCalls++;
  try {
    const answer=await generate(payload.messages, payload.place, payload.household);
    return response(res,200,{answer,source:'ia',model:MODEL});
  } catch(e) {
    console.error('IA:',e?.name || 'Error',e?.message || 'unknown');
    return response(res,502,{error:'provider_unavailable'});
  } finally {activeCalls--;}
}
export const server=http.createServer((req,res)=>{
  Promise.resolve(requestHandler(req,res)).catch(err=>{
    console.error('Backend:',err?.name || 'Error');
    if (!res.headersSent) response(res,500,{error:'internal_error'});
  });
});
if(process.argv[1] && import.meta.url===new URL(`file://${process.argv[1]}`).href){
  server.listen(PORT,'0.0.0.0',()=>console.log(`EcoResilia AI listening on :${PORT}`));
}
