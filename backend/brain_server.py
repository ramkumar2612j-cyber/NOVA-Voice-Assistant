#!/usr/bin/env python3
"""JARVIS CENTRAL CLOUD BRAIN (v7.217). Owner ka apna server — phone + laptop + website sab isi se jude hain.

Design rules (project ke purane invariants):
  * Pure Python stdlib (jaise tools/jarvis_server/server.py) — koi paid dependency nahi, kahin bhi free me chalega.
  * SQLite storage (backend/state/brain.db) — ek file, backup = copy.
  * Secrets DB me HASH hokar jaate hain: device tokens aur admin session tokens ka sirf SHA-256. DB churi ho to tokens bekaar.
  * Admin password PBKDF2-SHA256 (200k iterations) + per-install random salt.
  * Body size caps, parameterized SQL, rate limit, logs me koi secret nahi.
  * HTTPS deployer ki zimmedari (docs/DEPLOY.md) — phone client https-only hai (localhost exception jaise JarvisVoiceApi).
  * E2E mode: client (phone/desktop) memory/tasks ka TEXT apne family key se AES-256-GCM me seal karke bhejta hai
    (LaptopLink wala hi scheme). Server usko opaque blob ki tarah store karta hai — merge sirf metadata (ver/ts/device)
    se hota hai. Isliye server ko content nahi dikhta, phir bhi sync + conflict-resolution chalta hai.

Pairing (owner ke liye simple):
  1. Admin panel me login karo (ADMIN_PASSWORD — pehli baar auto-generate hokar state/admin_password.txt me)
  2. "Naya device jodo" -> one-time code  JARVIS-CLOUD:<base64url json>  (15 min valid)
  3. Phone app / laptop me wahi code paste -> device hamesha ke liye registered

Endpoints:
  POST /api/register   {code, name, platform}          -> {device_id, device_token, family_key}  (one-time code)
  POST /api/status     {bat, net, run}                 -> {ok, now}          (heartbeat, Bearer device token)
  POST /api/tasks/push {id, title, target, due, done, ver, ts}   -> {ok, task}
  POST /api/tasks/pull {since}                          -> {tasks: [...]}
  POST /api/tasks/claim {id}                            -> {ok}
  POST /api/tasks/done  {id, result}                    -> {ok}
  POST /api/memory/push {id, text, scope(all|private), ver, ts} -> {ok, fact}
  POST /api/memory/pull {since}                         -> {facts: [...]}   (private sirf banane wale device ko)
  POST /api/sync        {items: [{key, val, ver, ts}]}  -> {items: [...], conflicts: [...]}  (LWW merge)
  GET  /api/status                                        -> PUBLIC: {ok, version, devices_online, uptime_s} (naam nahi)
  POST /admin/login     {password}                       -> {token}  (session 12h)
  GET  /admin/overview                                    -> devices, health, counts (E2E mode me sirf counts)
  GET  /admin/versions  / POST /admin/changelog/publish  -> website ke liye
  GET  /admin/reports   / POST /admin/report/daily       -> daily health reports

Run:  python3 brain_server.py          (JARVIS_BRAIN_PORT=8787, JARVIS_BRAIN_HOST=0.0.0.0)
"""
import base64, hashlib, hmac, json, os, secrets, sqlite3, sys, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

VERSION = "7.217"
HERE = Path(__file__).resolve().parent
STATE = Path(os.environ.get("JARVIS_BRAIN_STATE", HERE / "state"))
DB = STATE / "brain.db"
MAX_BODY = 64_000            # 64 KB — payload caps (phone client bhi 8 KB tak bhejta hai)
RATE_LIMIT = 60              # per minute per IP for API
ADMIN_SESSION_S = 12 * 3600
PAIR_CODE_TTL_S = 15 * 60
DEVICE_OFFLINE_S = 15 * 60   # heartbeat itne der me na aaye to "offline"

os.makedirs(STATE, exist_ok=True)
_lock = threading.Lock()     # one writer at a time (SQLite serialized)
_rate = {}                   # (ip, bucket) -> [window_start, count]
_rate_lock = threading.Lock()


def _b64u(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).decode().rstrip("=")


def _unb64u(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def _sha(s: str) -> str:
    return hashlib.sha256(s.encode()).hexdigest()


def rate_ok(ip: str, bucket: str, limit: int) -> bool:
    now = int(time.time())
    with _rate_lock:
        key = (ip, bucket)
        w, n = _rate.get(key, (now, 0))
        if now - w >= 60:
            _rate[key] = (now, 1)
            return True
        if n >= limit:
            return False
        _rate[key] = (w, n + 1)
        return True


# ---------------------------------------------------------------- storage

def db() -> sqlite3.Connection:
    c = sqlite3.connect(DB, timeout=15)
    c.execute("PRAGMA journal_mode=WAL")
    c.row_factory = sqlite3.Row
    return c


def init_db() -> None:
    with _lock, db() as c:
        c.executescript("""
        CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT);
        CREATE TABLE IF NOT EXISTS pair_codes(token TEXT PRIMARY KEY, family_key TEXT, created REAL, used INTEGER DEFAULT 0);
        CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, name TEXT, platform TEXT, token_hash TEXT,
                                           family_key TEXT, created REAL, last_seen REAL, bat INTEGER, net TEXT, run INTEGER DEFAULT 0);
        CREATE TABLE IF NOT EXISTS tasks(id TEXT PRIMARY KEY, title TEXT, target TEXT, due INTEGER, state TEXT,
                                         claimed_by TEXT, result TEXT, ver INTEGER, ts INTEGER, device TEXT, updated INTEGER);
        CREATE TABLE IF NOT EXISTS memory(id TEXT PRIMARY KEY, text TEXT, scope TEXT, device TEXT, ver INTEGER, ts INTEGER, updated INTEGER);
        CREATE TABLE IF NOT EXISTS sync(key TEXT PRIMARY KEY, val TEXT, ver INTEGER, ts INTEGER, device TEXT, updated INTEGER);
        CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY, created REAL);
        CREATE TABLE IF NOT EXISTS versions(v TEXT PRIMARY KEY, date TEXT, note TEXT, published INTEGER DEFAULT 0);
        CREATE TABLE IF NOT EXISTS reports(day TEXT PRIMARY KEY, json TEXT, created REAL);
        """)


# ---------------------------------------------------------------- admin password

def admin_password() -> str:
    """ADMIN_PASSWORD env ya pehli baar auto-generate. Password KABHI console/log par print nahi hota
    (log capture ho sakta hai) — sirf state/admin_password.txt (chmod 600) me jaata hai; owner wahi se padhta hai."""
    env = os.environ.get("ADMIN_PASSWORD", "")
    if env:
        return env
    f = STATE / "admin_password.txt"
    if f.is_file():
        return f.read_text().strip()
    pw = "jarvis-" + _b64u(secrets.token_bytes(9)).lower()
    f.write_text(pw + "\n")
    os.chmod(f, 0o600)
    print("ADMIN PASSWORD banaya gaya: state/admin_password.txt me hai (ye file hi kholke padho, kabhi share mat karo).", file=sys.stderr)
    return pw


def admin_pw_record() -> tuple:
    """(salt, pbkdf2_hex) — ek hi baar banta hai, phir meta me."""
    with _lock, db() as c:
        row = c.execute("SELECT v FROM meta WHERE k='admin_salt'").fetchone()
        if row:
            salt, want = row[0].split(":")
            return salt, want
    salt = _b64u(secrets.token_bytes(16))
    want = hashlib.pbkdf2_hmac("sha256", admin_password().encode(), salt.encode(), 200_000).hex()
    with _lock, db() as c:
        c.execute("INSERT OR IGNORE INTO meta(k,v) VALUES('admin_salt',?)", (salt + ":" + want,))
        row = c.execute("SELECT v FROM meta WHERE k='admin_salt'").fetchone()
    salt, want = row[0].split(":")
    return salt, want


def admin_check(pw: str) -> bool:
    salt, want = admin_pw_record()
    got = hashlib.pbkdf2_hmac("sha256", pw.encode(), salt.encode(), 200_000).hex()
    return hmac.compare_digest(got, want)


# ---------------------------------------------------------------- helpers

def device_by_token(auth: str):
    if not auth.startswith("Bearer "):
        return None
    h = _sha(auth[7:])
    with _lock, db() as c:
        row = c.execute("SELECT * FROM devices WHERE token_hash=?", (h,)).fetchone()
    return dict(row) if row else None


def new_pair_code() -> dict:
    """One-time pairing code + family key. Admin hi bana sakta hai."""
    token = _b64u(secrets.token_bytes(24))
    family = _b64u(secrets.token_bytes(32))           # E2E seal/unseal key (devices ke paas rehti hai)
    with _lock, db() as c:
        c.execute("DELETE FROM pair_codes WHERE created < ?", (time.time() - PAIR_CODE_TTL_S,))
        c.execute("INSERT INTO pair_codes(token, family_key, created) VALUES(?,?,?)", (token, family, time.time()))
    addr = os.environ.get("JARVIS_BRAIN_PUBLIC_ADDR", "")
    code = "JARVIS-CLOUD:" + _b64u(json.dumps({"a": addr, "p": token}, separators=(",", ":")).encode())
    return {"code": code, "expires_in": PAIR_CODE_TTL_S}


def use_pair_code(token: str):
    with _lock, db() as c:
        row = c.execute("SELECT * FROM pair_codes WHERE token=? AND used=0", (token,)).fetchone()
        if not row or time.time() - row["created"] > PAIR_CODE_TTL_S:
            return None
        c.execute("UPDATE pair_codes SET used=1 WHERE token=?", (token,))
    return {"family_key": row["family_key"]}


def now_i() -> int:
    return int(time.time())


def _int(v, d: int) -> int:
    try:
        return int(str(v))
    except Exception:
        return d


# ---------------------------------------------------------------- HTTP handler

class Handler(BaseHTTPRequestHandler):
    server_version = "JarvisBrain/" + VERSION
    _srv_start = time.time()

    def _ip(self) -> str:
        return self.client_address[0]

    def _send(self, code: int, obj) -> None:
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _body(self) -> dict:
        n = int(self.headers.get("Content-Length", "0"))
        if n <= 0 or n > MAX_BODY:
            raise ValueError("body size")
        raw = self.rfile.read(n)
        try:
            v = json.loads(raw.decode("utf-8"))
            return v if isinstance(v, dict) else {}
        except Exception:
            return {}

    def _auth_device(self):
        d = device_by_token(self.headers.get("Authorization", ""))
        if not d:
            self._send(401, {"detail": "bad device token"})
            return None
        return d

    def _auth_admin(self):
        h = self.headers.get("Authorization", "")
        if not h.startswith("Bearer "):
            return False
        with _lock, db() as c:
            row = c.execute("SELECT * FROM sessions WHERE token_hash=?", (_sha(h[7:]),)).fetchone()
        if not row or time.time() - row["created"] > ADMIN_SESSION_S:
            return False
        return True

    def log_message(self, fmt, *args):   # kabhi bhi token/password log nahi
        sys.stderr.write("%s %s\n" % (self._ip(), self.path.split("?")[0]))

    # ---- GET ----
    def do_GET(self):
        p = self.path.split("?")[0]
        if p == "/api/status":
            with _lock, db() as c:
                n = c.execute("SELECT COUNT(*) FROM devices WHERE last_seen > ?",
                              (time.time() - DEVICE_OFFLINE_S,)).fetchone()[0]
            return self._send(200, {"ok": True, "version": VERSION, "devices_online": n,
                                    "uptime_s": int(time.time() - self._srv_start)})
        if p == "/admin/overview":
            if not self._auth_admin():
                return self._send(401, {"detail": "admin only"})
            with _lock, db() as c:
                devs = [dict(r) for r in c.execute("SELECT id,name,platform,created,last_seen,bat,net,run FROM devices ORDER BY last_seen DESC")]
                counts = {t: c.execute("SELECT COUNT(*) FROM " + t).fetchone()[0] for t in ("tasks", "memory", "sync", "pair_codes")}
                reps = [dict(r) for r in c.execute("SELECT day, created FROM reports ORDER BY day DESC LIMIT 30")]
            for d in devs:
                d["online"] = time.time() - (d["last_seen"] or 0) < DEVICE_OFFLINE_S
                d.pop("last_seen", None)
            return self._send(200, {"ok": True, "devices": devs, "counts": counts, "reports": reps,
                                    "version": VERSION, "e2e": True})
        if p == "/admin/versions":
            if not self._auth_admin():
                return self._send(401, {"detail": "admin only"})
            with _lock, db() as c:
                vs = [dict(r) for r in c.execute("SELECT v, date, note, published FROM versions ORDER BY v DESC")]
            return self._send(200, {"ok": True, "versions": vs})
        if p == "/admin/reports":
            if not self._auth_admin():
                return self._send(401, {"detail": "admin only"})
            with _lock, db() as c:
                reps = [dict(r) for r in c.execute("SELECT day, json, created FROM reports ORDER BY day DESC LIMIT 30")]
            return self._send(200, {"ok": True, "reports": reps})
        self._send(404, {"detail": "not found"})

    # ---- POST ----
    def do_POST(self):
        p = self.path.split("?")[0]
        try:
            if p == "/api/register":
                return self.h_register()
            if p == "/admin/login":
                return self.h_admin_login()
            if p == "/admin/paircode":
                if not self._auth_admin():
                    return self._send(401, {"detail": "admin only"})
                return self._send(200, new_pair_code())
            if p == "/admin/changelog/publish":
                return self.h_changelog()
            if p == "/admin/report/daily":
                return self.h_report()
            # --- device endpoints (rate limited) ---
            if not rate_ok(self._ip(), "api", RATE_LIMIT):
                return self._send(429, {"detail": "slow down"})
            if p == "/api/status":
                return self.h_status()
            if p == "/api/tasks/push":
                return self.h_tasks_push()
            if p == "/api/tasks/pull":
                return self.h_tasks_pull()
            if p == "/api/tasks/claim":
                return self.h_tasks_claim()
            if p == "/api/tasks/done":
                return self.h_tasks_done()
            if p == "/api/memory/push":
                return self.h_memory_push()
            if p == "/api/memory/pull":
                return self.h_memory_pull()
            if p == "/api/sync":
                return self.h_sync()
            self._send(404, {"detail": "not found"})
        except ValueError:
            self._send(413, {"detail": "body size or bad json"})
        except Exception as e:   # internals kabhi response me nahi jaate
            sys.stderr.write("ERR %s %s\n" % (p, e.__class__.__name__))
            self._send(500, {"detail": "server error"})

    # ---- endpoint bodies ----
    def h_register(self):
        if not rate_ok(self._ip(), "register", 10):
            return self._send(429, {"detail": "slow down"})
        b = self._body()
        code = str(b.get("code", ""))[:200].strip()
        # code format: JARVIS-CLOUD:<base64url({"a":addr,"p":token})>  (prefix optional — sirf b64 bhi chalega)
        raw = code.split(":", 1)[1] if ":" in code else code
        token = ""
        try:
            payload = json.loads(_unb64u(raw).decode("utf-8"))
            token = str(payload.get("p", "")) if isinstance(payload, dict) else ""
        except Exception:
            token = raw   # shayad seedha token hi bheja hai
        info = use_pair_code(token.strip())
        if not info:
            return self._send(401, {"detail": "pairing code galat ya expire"})
        name = str(b.get("name", ""))[:40].strip() or "device"
        platform = str(b.get("platform", ""))[:20].strip() or "unknown"
        dev_id = "d-" + _b64u(secrets.token_bytes(6))
        dev_token = _b64u(secrets.token_bytes(24))
        with _lock, db() as c:
            c.execute("INSERT INTO devices(id,name,platform,token_hash,family_key,created,last_seen) VALUES(?,?,?,?,?,?,?)",
                      (dev_id, name, platform, _sha(dev_token), info["family_key"], time.time(), time.time()))
        return self._send(200, {"ok": True, "device_id": dev_id, "device_token": dev_token,
                                "family_key": info["family_key"], "version": VERSION})

    def h_status(self):
        d = self._auth_device()
        if not d:
            return
        b = self._body()
        with _lock, db() as c:
            c.execute("UPDATE devices SET last_seen=?, bat=?, net=?, run=? WHERE id=?",
                      (time.time(), _int(b.get("bat"), -1), str(b.get("net", ""))[:10], 1 if b.get("run") else 0, d["id"]))
        return self._send(200, {"ok": True, "now": now_i()})

    def h_tasks_push(self):
        d = self._auth_device()
        if not d:
            return
        b = self._body()
        tid = str(b.get("id", ""))[:64].strip()
        title = str(b.get("title", ""))[:800]
        if not tid or not title:
            return self._send(422, {"detail": "id and title required"})
        target = str(b.get("target", ""))[:20].strip()
        due = _int(b.get("due"), 0)
        ver = _int(b.get("ver"), 1)
        ts = _int(b.get("ts"), now_i())
        state = "done" if b.get("done") else "open"
        with _lock, db() as c:
            row = c.execute("SELECT ver, ts, device FROM tasks WHERE id=?", (tid,)).fetchone()
            if row:   # LWW: newer (ver, ts) jeetta hai; tie -> device id
                if (ver, ts, d["id"]) <= (row["ver"], row["ts"], row["device"]):
                    return self._send(409, {"detail": "stale", "ver": row["ver"]})
                c.execute("""UPDATE tasks SET title=?, target=?, due=?, state=?, ver=?, ts=?, device=?, updated=? WHERE id=?""",
                          (title, target, due, state, ver, ts, d["id"], now_i(), tid))
            else:
                c.execute("""INSERT INTO tasks(id,title,target,due,state,claimed_by,result,ver,ts,device,updated)
                             VALUES(?,?,?,?,?,'','',?,?,?,?)""",
                          (tid, title, target, due, state, ver, ts, d["id"], now_i()))
        return self._send(200, {"ok": True})

    def h_tasks_pull(self):
        d = self._auth_device()
        if not d:
            return
        b = self._body()
        since = _int(b.get("since"), 0)
        with _lock, db() as c:
            rows = [dict(r) for r in c.execute(
                "SELECT id,title,target,due,state,claimed_by,ver,ts,device FROM tasks WHERE updated > ? ORDER BY updated LIMIT 200", (since,))]
        mine = [t for t in rows if not t["target"] or t["target"] in (d["platform"], d["name"]) or t["device"] == d["id"]]   # banane wale ko hamesha apne task ka haal dikhta hai
        return self._send(200, {"ok": True, "tasks": mine, "now": now_i()})

    def h_tasks_claim(self):
        d = self._auth_device()
        if not d:
            return
        b = self._body()
        tid = str(b.get("id", ""))[:64]
        with _lock, db() as c:
            row = c.execute("SELECT state, claimed_by FROM tasks WHERE id=?", (tid,)).fetchone()
            if not row:
                return self._send(404, {"detail": "no such task"})
            if row["state"] != "open":
                return self._send(409, {"detail": "not open", "claimed_by": row["claimed_by"]})
            c.execute("UPDATE tasks SET state='claimed', claimed_by=?, updated=? WHERE id=?", (d["id"], now_i(), tid))
        return self._send(200, {"ok": True})

    def h_tasks_done(self):
        d = self._auth_device()
        if not d:
            return
        b = self._body()
        tid = str(b.get("id", ""))[:64]
        result = str(b.get("result", ""))[:800]
        with _lock, db() as c:
            row = c.execute("SELECT claimed_by FROM tasks WHERE id=?", (tid,)).fetchone()
            if not row:
                return self._send(404, {"detail": "no such task"})
            if row["claimed_by"] not in ("", d["id"]):
                return self._send(409, {"detail": "claimed by another device"})
            c.execute("UPDATE tasks SET state='done', result=?, claimed_by=?, updated=? WHERE id=?", (result, d["id"], now_i(), tid))
        return self._send(200, {"ok": True})

    def h_memory_push(self):
        d = self._auth_device()
        if not d:
            return
        b = self._body()
        fid = str(b.get("id", ""))[:64].strip()
        text = str(b.get("text", ""))[:800]
        scope = "private" if b.get("scope") == "private" else "all"
        if not fid or not text:
            return self._send(422, {"detail": "id and text required"})
        ver = _int(b.get("ver"), 1)
        ts = _int(b.get("ts"), now_i())
        with _lock, db() as c:
            row = c.execute("SELECT ver, ts, device FROM memory WHERE id=?", (fid,)).fetchone()
            if row:
                if (ver, ts, d["id"]) <= (row["ver"], row["ts"], row["device"]):
                    return self._send(409, {"detail": "stale", "ver": row["ver"]})
                c.execute("UPDATE memory SET text=?, scope=?, ver=?, ts=?, device=?, updated=? WHERE id=?",
                          (text, scope, ver, ts, d["id"], now_i(), fid))
            else:
                c.execute("INSERT INTO memory(id,text,scope,device,ver,ts,updated) VALUES(?,?,?,?,?,?,?)",
                          (fid, text, scope, d["id"], ver, ts, now_i()))
        return self._send(200, {"ok": True})

    def h_memory_pull(self):
        d = self._auth_device()
        if not d:
            return
        b = self._body()
        since = _int(b.get("since"), 0)
        with _lock, db() as c:
            rows = [dict(r) for r in c.execute(
                "SELECT id,text,scope,device,ver,ts FROM memory WHERE updated > ? ORDER BY updated LIMIT 300", (since,))]
        # privacy control: private fact sirf banane wale device ko jaata hai
        out = [f for f in rows if f["scope"] != "private" or f["device"] == d["id"]]
        return self._send(200, {"ok": True, "facts": out, "now": now_i()})

    def h_sync(self):
        """Generic LWW KV sync: jeeta hua version wapas + jo hamara purana tha aur haar gaya wo conflict me."""
        d = self._auth_device()
        if not d:
            return
        b = self._body()
        items = b.get("items")
        if not isinstance(items, list) or len(items) > 50:
            return self._send(422, {"detail": "items list (max 50) required"})
        out, conflicts = [], []
        with _lock, db() as c:
            for it in items[:50]:
                key = str(it.get("key", ""))[:64].strip()
                val = str(it.get("val", ""))[:4000]
                ver = _int(it.get("ver"), 1)
                ts = _int(it.get("ts"), now_i())
                if not key:
                    continue
                row = c.execute("SELECT ver, ts, device, val FROM sync WHERE key=?", (key,)).fetchone()
                if not row:
                    c.execute("INSERT INTO sync(key,val,ver,ts,device,updated) VALUES(?,?,?,?,?,?)",
                              (key, val, ver, ts, d["id"], now_i()))
                    out.append({"key": key, "val": val, "ver": ver, "ts": ts})
                elif (ver, ts, d["id"]) > (row["ver"], row["ts"], row["device"]):
                    c.execute("UPDATE sync SET val=?, ver=?, ts=?, device=?, updated=? WHERE key=?",
                              (val, ver, ts, d["id"], now_i(), key))
                    out.append({"key": key, "val": val, "ver": ver, "ts": ts})
                else:
                    conflicts.append({"key": key, "val": row["val"], "ver": row["ver"], "ts": row["ts"]})
        return self._send(200, {"ok": True, "items": out, "conflicts": conflicts, "now": now_i()})

    def h_admin_login(self):
        if not rate_ok(self._ip(), "admin_login", 20):
            return self._send(429, {"detail": "slow down"})
        b = self._body()
        pw = str(b.get("password", ""))[:200]
        if not admin_check(pw):
            return self._send(401, {"detail": "galat password"})
        tok = _b64u(secrets.token_bytes(24))
        with _lock, db() as c:
            c.execute("INSERT INTO sessions(token_hash, created) VALUES(?,?)", (_sha(tok), time.time()))
            c.execute("DELETE FROM sessions WHERE created < ?", (time.time() - ADMIN_SESSION_S,))
        return self._send(200, {"ok": True, "token": tok, "expires_in": ADMIN_SESSION_S})

    def h_changelog(self):
        if not self._auth_admin():
            return self._send(401, {"detail": "admin only"})
        b = self._body()
        v = str(b.get("v", ""))[:20].strip()
        date = str(b.get("date", ""))[:20].strip()
        note = str(b.get("note", ""))[:2000]
        if not v:
            return self._send(422, {"detail": "v required"})
        with _lock, db() as c:
            c.execute("INSERT INTO versions(v,date,note,published) VALUES(?,?,?,1) ON CONFLICT(v) DO UPDATE SET note=excluded.note, published=1",
                      (v, date, note))
        return self._send(200, {"ok": True})

    def h_report(self):
        if not self._auth_admin():
            return self._send(401, {"detail": "admin only"})
        b = self._body()
        day = str(b.get("day", ""))[:10] or time.strftime("%Y-%m-%d")
        payload = json.dumps(b.get("report", {}), ensure_ascii=False)[:20000]
        with _lock, db() as c:
            c.execute("INSERT INTO reports(day,json,created) VALUES(?,?,?) ON CONFLICT(day) DO UPDATE SET json=excluded.json, created=excluded.created",
                      (day, payload, time.time()))
        return self._send(200, {"ok": True})


def main() -> None:
    init_db()
    admin_pw_record()   # pehli baar password ban jaaye + print ho
    port = int(os.environ.get("JARVIS_BRAIN_PORT", "8787"))
    host = os.environ.get("JARVIS_BRAIN_HOST", "0.0.0.0")
    srv = ThreadingHTTPServer((host, port), Handler)
    srv.daemon_threads = True   # ek atka handler poori shutdown nahi rok sakta
    print("JARVIS BRAIN %s ready on %s:%d (state: %s)" % (VERSION, host, port, STATE), file=sys.stderr)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
