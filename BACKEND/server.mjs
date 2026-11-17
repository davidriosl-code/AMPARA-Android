/**
 * EcoResilia / proxy educativo de IA para pruebas locales.
 * Node >= 22. No utiliza npm ni guarda preguntas ni API keys en disco.
 * Escucha SOLO en 127.0.0.1 para que el emulador Android acceda via 10.0.2.2.
 */
import http from 'node:http';

const port = 8787;
const hostname = '127.0.0.1';
const model = process.env.NVIDIA_MODEL || 'meta/llama-3.3-70b-instruct';
const key = process.env.NVIDIA_API_KEY || '';
const systemPrompt = `Eres EcoResilia, un asistente educativo de prevención de desastres para Perú. Responde en español claro, con empatía y pasos prácticos. Solo orientas sobre preparación familiar, huaicos, inundaciones, sismos, mochilas, mascotas, drenajes, comunicación y fuentes institucionales. NO inventes datos meteorológicos recientes, mapas, avisos, ubicaciones, llamadas a servicios o rutas de evacuación; no puedes inspeccionar una vivienda ni verificar peligros de un lugar. Los reportes ciudadanos y la cartografía histórica no son alertas oficiales. Ante peligro inmediato o personas atrapadas, prioriza alejarse del peligro si es posible y llamar a emergencias (Bomberos 116, Policía 105 en Perú), Defensa Civil local; no cruces cauces o aguas de inundación. Nunca garantices seguridad, evita consejos técnicos temerarios. Si falta información para una decisión crítica, dilo. No solicites datos personales. No afirmes tener acceso en tiempo real a ninguna fuente.`;

function json(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(body);
}
const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') return json(res, 200, { status: key ? 'ready' : 'needs_key', aiConfigured: Boolean(key) });
  if (req.method !== 'POST' || req.url !== '/api/assistant') return json(res, 404, { error: 'not_found' });
  if (!key) return json(res, 503, { error: 'Set NVIDIA_API_KEY in the server environment' });
  let raw = '';
  try {
    for await (const chunk of req) {
      raw += chunk.toString();
      if (raw.length > 13000) return json(res, 413, { error: 'request_too_large' });
    }
    const body = JSON.parse(raw);
    if (!Array.isArray(body.messages) || body.messages.length === 0) return json(res, 400, { error: 'messages_required' });
    const conversation = body.messages.slice(-10).map(x => ({
      role: x?.role === 'assistant' ? 'assistant' : 'user',
      content: String(x?.content ?? '').slice(0, 1000)
    })).filter(x => x.content.trim());
    if (conversation.at(-1)?.role !== 'user') return json(res, 400, { error: 'last_message_must_be_user' });
    const place = typeof body.place === 'string' ? body.place.slice(0, 80) : 'Perú';
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 25000);
    try {
      const result = await fetch('https://integrate.api.nvidia.com/v1/chat/completions', {
        method: 'POST', signal: controller.signal,
        headers: { 'Authorization': `Bearer ${key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model, temperature: 0.2, max_tokens: 650, stream: false,
          messages: [ { role: 'system', content: systemPrompt + ` La ubicación seleccionada en la app (NO verificada) es: ${place}.` }, ...conversation ]
        })
      });
      if (!result.ok) return json(res, 502, { error: 'provider_unavailable', status: result.status });
      const data = await result.json();
      const answer = String(data?.choices?.[0]?.message?.content ?? '').trim();
      if (!answer) return json(res, 502, { error: 'empty_answer' });
      return json(res, 200, { answer: answer.slice(0, 2400), model });
    } finally { clearTimeout(timeout); }
  } catch (error) {
    return json(res, 502, { error: 'proxy_failure', type: error?.name ?? 'unknown' });
  }
});
server.listen(port, hostname, () => {
  process.stdout.write(`EcoResilia AI proxy listening at http://${hostname}:${port}\n`);
  if (!key) process.stdout.write('NVIDIA_API_KEY not set. Offline assistant in Android is still available.\n');
});
