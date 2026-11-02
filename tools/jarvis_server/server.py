#!/usr/bin/env python3
"""NOVA Jarvis voice server (v7.136).  Wraps FuturePresentLabs/tts-jarvis (VoxCPM2 + LoRA adapter) behind the tiny API the NOVA app calls:

    POST /tts     header  Authorization: Bearer <JARVIS_TOKEN>   body {"text": "..."}   ->  200 audio/wav (16 bit mono)
    GET  /health  ->  {"ok": true, "engine": "voxcpm2" | "fake"}

Pure Python standard library for the web part (no FastAPI needed). The real engine needs the model's own packages (see README_HINGLISH.md).

Environment:
    JARVIS_TOKEN       secret token. Strongly recommended. If empty the server is OPEN to anyone who finds the address.
    JARVIS_REFERENCE   path of YOUR reference.wav (a clean voice recording you are authorised to use). Optional (v7.137): without it the release's own reference is used if it has one.
    JARVIS_FAKE=1      no model: returns a short test tone as a wav. Only for testing the phone <-> server link.
    JARVIS_PORT        default 8000.   JARVIS_HOST default 0.0.0.0.   JARVIS_MAX_CHARS default 800.
    JARVIS_CFG / JARVIS_STEPS / JARVIS_SEED   model settings (defaults 2.4 / 6 / 42 = the model card's selected recipe).

Run:   python3 server.py
The real engine follows the model card's "Usage" example. NOT TESTED with the real model in the sandbox that wrote this (no GPU, no weights).
"""
import hmac, io, json, math, os, struct, sys, threading, wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

REVISION = "v4-interface-2026-09-06"          # release tag named in the model card's usage example
REPO = "FuturePresentLabs/tts-jarvis"
MAX_CHARS = int(os.environ.get("JARVIS_MAX_CHARS", "800"))
MAX_BODY = 20_000
FAKE = os.environ.get("JARVIS_FAKE", "") == "1"
TOKEN = os.environ.get("JARVIS_TOKEN", "")

_lock = threading.Lock()            # one synthesis at a time (one GPU)
_engine = {"model": None, "sr": 24000, "ref": None, "name": "fake" if FAKE else "voxcpm2"}


def fake_wav(text: str) -> bytes:
    """A short test tone (length follows the text). Only for checking the phone <-> server link."""
    sr = 24000
    n = int(sr * min(6.0, 0.25 + 0.06 * len(text)))
    pcm = b"".join(struct.pack("<h", int(8000 * math.sin(2 * math.pi * 220 * i / sr) * min(1.0, i / 800, (n - i) / 800))) for i in range(n))
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm)
    return buf.getvalue()


def load_real() -> None:
    """Follows the model card's Usage example. Heavy: runs once at start-up."""
    import soundfile  # noqa: F401  (fail early if missing)
    from huggingface_hub import snapshot_download
    from voxcpm.core import VoxCPM
    from voxcpm.model.voxcpm2 import LoRAConfig

    release = Path(snapshot_download(REPO, revision=REVISION))
    ref = os.environ.get("JARVIS_REFERENCE", "")
    if ref and not Path(ref).is_file():
        sys.exit("JARVIS_REFERENCE file nahi mili: %s" % ref)
    if not ref:                                       # v7.137: no upload needed. Use a reference shipped inside the release if there is one, else none.
        found = sorted(p for p in release.rglob("*.wav") if "ref" in p.name.lower() or "ref" in p.parent.name.lower())
        ref = str(found[0]) if found else ""
    settings = json.loads((release / "inference_config.json").read_text())
    base = snapshot_download(settings["base_model"], revision=settings.get("base_revision"))
    adapter = release / "adapter"
    raw = json.loads((adapter / "lora_config.json").read_text())
    lora_cfg = raw.get("lora_config", raw) if isinstance(raw, dict) else raw
    ckpt = adapter / "step_0000031"                     # the card names this checkpoint; fall back to the adapter folder
    weights = ckpt if ckpt.exists() else adapter
    model = VoxCPM.from_pretrained(hf_model_id=base, load_denoiser=False, optimize=False,
                                   lora_config=LoRAConfig(**lora_cfg), lora_weights_path=str(weights))
    _engine.update(model=model, sr=int(model.tts_model.sample_rate), ref=ref)


def real_wav(text: str) -> bytes:
    import soundfile as sf
    audio = _engine["model"].generate(
        text=text, reference_wav_path=(_engine["ref"] or None),
        cfg_value=float(os.environ.get("JARVIS_CFG", "2.4")), inference_timesteps=int(os.environ.get("JARVIS_STEPS", "6")),
        seed=int(os.environ.get("JARVIS_SEED", "42")), normalize=True, denoise=False, max_len=600)
    buf = io.BytesIO()
    sf.write(buf, audio, _engine["sr"], format="WAV", subtype="PCM_16")
    return buf.getvalue()


def token_ok(header: str) -> bool:
    if not TOKEN:
        return True
    return hmac.compare_digest((header or "").encode(), ("Bearer " + TOKEN).encode())


class Handler(BaseHTTPRequestHandler):
    server_version = "NovaJarvis/1"

    def _send(self, code: int, body: bytes, ctype: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code: int, obj) -> None:
        self._send(code, json.dumps(obj).encode(), "application/json")

    def do_GET(self):
        if self.path.split("?")[0] == "/health":
            return self._json(200, {"ok": True, "engine": _engine["name"]})
        self._json(404, {"detail": "not found"})

    def do_POST(self):
        if self.path.split("?")[0] != "/tts":
            return self._json(404, {"detail": "not found"})
        if not token_ok(self.headers.get("Authorization", "")):
            return self._json(401, {"detail": "bad token"})
        try:
            n = int(self.headers.get("Content-Length", "0"))
            if n <= 0 or n > MAX_BODY:
                return self._json(413, {"detail": "body size"})
            text = str(json.loads(self.rfile.read(n).decode("utf-8")).get("text", "")).strip()
        except Exception:
            return self._json(422, {"detail": "bad json"})
        if not text:
            return self._json(422, {"detail": "empty text"})
        if len(text) > MAX_CHARS:
            return self._json(413, {"detail": "text too long"})
        try:
            with _lock:
                data = fake_wav(text) if FAKE else real_wav(text)
        except Exception as e:   # never leak internals to the phone
            print("synthesis error:", repr(e), file=sys.stderr)
            return self._json(500, {"detail": "synthesis failed"})
        self._send(200, data, "audio/wav")

    def log_message(self, fmt, *args):   # no request text in the logs
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args if args else fmt))


def main() -> None:
    if not TOKEN:
        print("WARNING: JARVIS_TOKEN is empty - anyone who knows this address can use the server.", file=sys.stderr)
    if not FAKE:
        load_real()
    srv = ThreadingHTTPServer((os.environ.get("JARVIS_HOST", "0.0.0.0"), int(os.environ.get("JARVIS_PORT", "8000"))), Handler)
    print("Jarvis server ready (engine=%s) on port %d" % (_engine["name"], srv.server_address[1]))
    srv.serve_forever()


if __name__ == "__main__":
    main()
