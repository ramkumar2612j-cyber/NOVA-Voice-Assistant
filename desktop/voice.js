'use strict';
// Laptop voice (Step C): the mic recording happens in the HUD, the audio goes to Groq Whisper (same service + guards as the phone app), the text comes back as a normal typed command.
// The key lives only in the main process (never in the page), is sent only in the Authorization header, and is never logged. fetch is injected so tests need no network.
const ENDPOINT = 'https://api.groq.com/openai/v1/audio/transcriptions', MODEL = 'whisper-large-v3';
const PROMPT = 'Hinglish voice command for a laptop assistant called Jarvis, written in English letters.';   // neutral hint, same idea as the phone's ListenGuard prompt
const norm = s => String(s || '').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').replace(/\s+/g, ' ').trim();

function validKey(k) { return typeof k === 'string' && /^[A-Za-z0-9_\-]{20,200}$/.test(k.trim()); }
function failText(code) { return code === 598 || code === 599 ? 'internet nahi hai' : code === 401 || code === 403 ? 'Groq key galat hai' : code === 429 ? 'Groq limit poori ho gayi, thodi der baad try karo' : 'error ' + code; }
function multipart(fields, file) {
  const b = 'jarvisform' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8), parts = [];
  for (const [k, v] of Object.entries(fields)) parts.push(Buffer.from('--' + b + '\r\nContent-Disposition: form-data; name="' + k + '"\r\n\r\n' + v + '\r\n', 'utf8'));
  parts.push(Buffer.from('--' + b + '\r\nContent-Disposition: form-data; name="file"; filename="' + file.name + '"\r\nContent-Type: ' + file.type + '\r\n\r\n', 'utf8'), file.data, Buffer.from('\r\n--' + b + '--\r\n', 'utf8'));
  return { body: Buffer.concat(parts), contentType: 'multipart/form-data; boundary=' + b };
}
// Whisper invents words on noise: drop it when every segment says "no speech", or when it only wrote our own hint back (same rules as the phone).
function cleanText(text, segs) {
  const probs = (segs || []).filter(s => s && typeof s.no_speech_prob === 'number').map(s => s.no_speech_prob);
  if (probs.length && probs.every(p => p > 0.8)) return '';
  const t = String(text || '').trim(), n = norm(t), p = norm(PROMPT);
  if (!n) return '';
  if (n === p || (n.split(' ').length >= 4 && p.includes(n))) return '';
  if (n.includes(p)) return n.replace(p, ' ').replace(/\s+/g, ' ').trim();
  if (['silence', 'no speech', 'inaudible', 'noise', 'blank', 'none', 'you', 'thank you', 'thanks for watching'].includes(n)) return '';   // classic Whisper noise outputs
  return t;
}
async function transcribe(fetchFn, key, audio, mime) {
  if (!validKey(key)) return { ok: false, err: 'Groq key nahi hai. HUD > VOICE me key daalo.' };
  if (!audio || audio.length < 1500) return { ok: false, err: 'Awaaz bahut chhoti thi.' };
  if (audio.length > 8 * 1024 * 1024) return { ok: false, err: 'Awaaz bahut lambi thi.' };
  const ext = /ogg/.test(mime) ? 'ogg' : /mp4|m4a/.test(mime) ? 'm4a' : /wav/.test(mime) ? 'wav' : 'webm';
  const mp = multipart({ model: MODEL, response_format: 'verbose_json', temperature: '0', prompt: PROMPT }, { name: 'cmd.' + ext, type: mime || 'audio/webm', data: Buffer.from(audio) });
  let r;
  try { r = await fetchFn(ENDPOINT, { method: 'POST', headers: { Authorization: 'Bearer ' + key.trim(), 'Content-Type': mp.contentType }, body: mp.body }); }
  catch (e) { return { ok: false, err: failText(599) }; }
  if (!r.ok) return { ok: false, err: failText(r.status) };
  let j; try { j = await r.json(); } catch (e) { return { ok: false, err: 'Groq ka jawab samajh nahi aaya.' }; }
  const text = cleanText(j.text, j.segments);
  return text ? { ok: true, text: text.slice(0, 300) } : { ok: false, err: 'Kuch sunai nahi diya, dobara bolo.' };
}
module.exports = { validKey, failText, multipart, cleanText, transcribe, PROMPT, MODEL, ENDPOINT };
