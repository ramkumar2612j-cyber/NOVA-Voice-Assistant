#!/usr/bin/env python3
"""Starts server.py in FAKE mode and checks the phone contract: health, 401 without / with a wrong token, 422 empty, 413 long, 200 RIFF wav with the token."""
import json, os, subprocess, sys, time, urllib.request, urllib.error, pathlib
here = pathlib.Path(__file__).resolve().parent
env = dict(os.environ, JARVIS_FAKE="1", JARVIS_TOKEN="secret-token-123", JARVIS_PORT="18765", JARVIS_HOST="127.0.0.1")
p = subprocess.Popen([sys.executable, "-I", str(here / "server.py")], env=env, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
bad = []
def need(c, m):
    if not c: bad.append(m)
def call(method, path, body=None, token=None):
    req = urllib.request.Request("http://127.0.0.1:18765" + path, data=None if body is None else json.dumps(body).encode(), method=method)
    if body is not None: req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as r: return r.status, r.read(), r.headers.get("Content-Type")
    except urllib.error.HTTPError as e: return e.code, e.read(), None
try:
    for _ in range(50):
        try: call("GET", "/health"); break
        except Exception: time.sleep(0.1)
    s, b, _ = call("GET", "/health"); need(s == 200 and json.loads(b)["ok"] is True and json.loads(b)["engine"] == "fake", "health")
    need(call("POST", "/tts", {"text": "hi"})[0] == 401, "no token -> 401")
    need(call("POST", "/tts", {"text": "hi"}, "wrong")[0] == 401, "wrong token -> 401")
    need(call("POST", "/tts", {"text": "  "}, "secret-token-123")[0] == 422, "empty -> 422")
    need(call("POST", "/tts", {"text": "a" * 801}, "secret-token-123")[0] == 413, "long -> 413")
    s, b, ct = call("POST", "/tts", {"text": "The workshop is ready."}, "secret-token-123")
    need(s == 200 and ct == "audio/wav" and b[:4] == b"RIFF" and b[8:12] == b"WAVE" and len(b) > 1000, "wav ok (%s, %s bytes)" % (s, len(b)))
    need(call("GET", "/nope")[0] == 404, "404")
finally:
    p.terminate(); p.wait(timeout=5)
if bad: print("FAIL:", *bad, sep="\n  "); sys.exit(1)
print("PASS jarvis server (fake engine)")
