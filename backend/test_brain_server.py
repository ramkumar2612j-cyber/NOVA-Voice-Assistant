#!/usr/bin/env python3
"""JARVIS BRAIN server tests (v7.217). Real HTTP calls against a live server on a test port + temp state dir.
Run: python3 -m unittest backend.test_brain_server  (ya seedha: python3 backend/test_brain_server.py)"""
import json, os, shutil, subprocess, sys, tempfile, time, unittest, urllib.request, urllib.error
from pathlib import Path

HERE = Path(__file__).resolve().parent
PORT = 18787
BASE = "http://127.0.0.1:%d" % PORT


def call(method, path, body=None, token=None, timeout=10):
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    if data:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


class ServerFixture(unittest.TestCase):
    proc = None
    state = None
    admin_pw = "test-admin-pw-123"

    @classmethod
    def setUpClass(cls):
        cls.state = Path(tempfile.mkdtemp(prefix="brain_test_"))
        env = dict(os.environ, JARVIS_BRAIN_STATE=str(cls.state), JARVIS_BRAIN_PORT=str(PORT),
                   JARVIS_BRAIN_HOST="127.0.0.1", ADMIN_PASSWORD=cls.admin_pw)
        cls.proc = subprocess.Popen([sys.executable, str(HERE / "brain_server.py")], env=env,
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(80):
            if cls.proc.poll() is not None:
                raise RuntimeError("server process died (port already in use? stale server from an old run?)")
            try:
                s, _b = call("GET", "/api/status")
                if s == 200:
                    return
            except Exception:
                pass
            time.sleep(0.25)
        raise RuntimeError("server did not start")

    @classmethod
    def tearDownClass(cls):
        if cls.proc:
            cls.proc.terminate()
            cls.proc.wait(timeout=10)
        shutil.rmtree(cls.state, ignore_errors=True)


class TestPublicAndAdmin(ServerFixture):
    def test_public_status(self):
        s, b = call("GET", "/api/status")
        self.assertEqual(s, 200)
        self.assertTrue(b["ok"])
        self.assertEqual(b["devices_online"], 0)
        self.assertIn("version", b)

    def test_admin_login_bad(self):
        s, _ = call("POST", "/admin/login", {"password": "galat"})
        self.assertEqual(s, 401)

    def test_admin_login_rate_limit(self):
        for _ in range(22):
            call("POST", "/admin/login", {"password": "x"})
        s, _ = call("POST", "/admin/login", {"password": "x"})
        self.assertEqual(s, 429)

    def test_admin_overview_needs_auth(self):
        s, _ = call("GET", "/admin/overview")
        self.assertEqual(s, 401)

    def test_admin_flow(self):
        s, b = call("POST", "/admin/login", {"password": self.admin_pw})
        self.assertEqual(s, 200)
        tok = b["token"]
        s, b = call("GET", "/admin/overview", token=tok)
        self.assertEqual(s, 200)
        self.assertEqual(b["devices"], [])
        # changelog publish + read
        s, _ = call("POST", "/admin/changelog/publish", {"v": "7.217", "date": "2026-11-17", "note": "cloud brain"}, token=tok)
        self.assertEqual(s, 200)
        s, b = call("GET", "/admin/versions", token=tok)
        self.assertEqual(s, 200)
        self.assertEqual(b["versions"][0]["v"], "7.217")
        # daily report
        s, _ = call("POST", "/admin/report/daily", {"day": "2026-11-17", "report": {"contract": "196/196"}}, token=tok)
        self.assertEqual(s, 200)
        s, b = call("GET", "/admin/reports", token=tok)
        self.assertEqual(s, 200)
        self.assertEqual(b["reports"][0]["day"], "2026-11-17")


class TestDevices(ServerFixture):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        s, b = call("POST", "/admin/login", {"password": cls.admin_pw})
        cls.admin_token = b["token"]

    def register(self, name, platform):
        s, b = call("POST", "/admin/paircode", token=self.admin_token)
        code = b["code"]
        self.assertTrue(code.startswith("JARVIS-CLOUD:"))
        s, b = call("POST", "/api/register", {"code": code, "name": name, "platform": platform})
        return s, b

    def test_register_and_status(self):
        s, _before = call("GET", "/api/status")
        n0 = _before["devices_online"]
        s, b = self.register("mera phone", "android")
        self.assertEqual(s, 200)
        self.assertIn("device_token", b)
        self.assertIn("family_key", b)
        tok = b["device_token"]
        s, b = call("POST", "/api/status", {"bat": 88, "net": "WI-FI", "run": True}, token=tok)
        self.assertEqual(s, 200)
        # public count goes up by exactly the new device
        s, b = call("GET", "/api/status")
        self.assertEqual(b["devices_online"], n0 + 1)

    def test_pair_code_single_use(self):
        s, b = call("POST", "/admin/paircode", token=self.admin_token)
        code = b["code"]
        s, _ = call("POST", "/api/register", {"code": code, "name": "a", "platform": "android"})
        self.assertEqual(s, 200)
        s, _ = call("POST", "/api/register", {"code": code, "name": "b", "platform": "android"})   # dobara = reject
        self.assertEqual(s, 401)

    def test_bad_pair_code(self):
        s, _ = call("POST", "/api/register", {"code": "JARVIS-CLOUD:abcdef", "name": "x", "platform": "android"})
        self.assertEqual(s, 401)

    def test_device_endpoints_need_token(self):
        s, _ = call("POST", "/api/tasks/pull", {"since": 0})
        self.assertEqual(s, 401)
        s, _ = call("POST", "/api/memory/pull", {"since": 0})
        self.assertEqual(s, 401)


class TestTasksAndMemory(ServerFixture):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        s, b = call("POST", "/admin/login", {"password": cls.admin_pw})
        cls.admin_token = b["token"]
        s, b = call("POST", "/admin/paircode", token=cls.admin_token)
        s, b = call("POST", "/api/register", {"code": b["code"], "name": "phone", "platform": "android"})
        cls.phone = b["device_token"]
        s, b = call("POST", "/admin/paircode", token=cls.admin_token)
        s, b = call("POST", "/api/register", {"code": b["code"], "name": "laptop", "platform": "windows"})
        cls.laptop = b["device_token"]

    def test_task_lifecycle(self):
        s, _ = call("POST", "/api/tasks/push", {"id": "t1", "title": "laptop pe chrome kholo", "target": "windows"}, token=self.phone)
        self.assertEqual(s, 200)
        s, b = call("POST", "/api/tasks/pull", {"since": 0}, token=self.laptop)
        self.assertEqual(s, 200)
        ids = [t["id"] for t in b["tasks"]]
        self.assertIn("t1", ids)
        # android-targeted task: target phone ko dikhegi, banane wale laptop ko bhi (creator rule), par TEESRE device ko nahi
        s, b = call("POST", "/api/tasks/push", {"id": "t2", "title": "phone ka kaam", "target": "android"}, token=self.laptop)
        self.assertEqual(s, 200)
        s, b = call("POST", "/admin/paircode", token=self.admin_token)
        s, b = call("POST", "/api/register", {"code": b["code"], "name": "tablet", "platform": "androidtv"})
        tablet = b["device_token"]
        s, b = call("POST", "/api/tasks/pull", {"since": 0}, token=tablet)
        self.assertNotIn("t2", [t["id"] for t in b["tasks"]])   # na target, na creator
        s, b = call("POST", "/api/tasks/pull", {"since": 0}, token=self.laptop)
        self.assertIn("t2", [t["id"] for t in b["tasks"]])      # creator ko apna task dikhta hai
        # claim + done
        s, _ = call("POST", "/api/tasks/claim", {"id": "t1"}, token=self.laptop)
        self.assertEqual(s, 200)
        s, _ = call("POST", "/api/tasks/claim", {"id": "t1"}, token=self.phone)   # doosra claim = conflict
        self.assertEqual(s, 409)
        s, _ = call("POST", "/api/tasks/done", {"id": "t1", "result": "khul gaya"}, token=self.laptop)
        self.assertEqual(s, 200)
        s, b = call("POST", "/api/tasks/pull", {"since": 0}, token=self.phone)
        t1 = [t for t in b["tasks"] if t["id"] == "t1"][0]
        self.assertEqual(t1["state"], "done")

    def test_task_lww_conflict(self):
        call("POST", "/api/tasks/push", {"id": "c1", "title": "v1", "ver": 1, "ts": 1000}, token=self.phone)
        s, _ = call("POST", "/api/tasks/push", {"id": "c1", "title": "purana", "ver": 1, "ts": 900}, token=self.laptop)
        self.assertEqual(s, 409)   # stale reject
        s, _ = call("POST", "/api/tasks/push", {"id": "c1", "title": "naya", "ver": 2, "ts": 2000}, token=self.laptop)
        self.assertEqual(s, 200)

    def test_memory_privacy(self):
        call("POST", "/api/memory/push", {"id": "m1", "text": "sabka fact", "scope": "all"}, token=self.phone)
        call("POST", "/api/memory/push", {"id": "m2", "text": "sirf mera", "scope": "private"}, token=self.phone)
        # laptop ko private fact NAHI milega
        s, b = call("POST", "/api/memory/pull", {"since": 0}, token=self.laptop)
        ids = [f["id"] for f in b["facts"]]
        self.assertIn("m1", ids)
        self.assertNotIn("m2", ids)
        # phone ko apna private fact milega
        s, b = call("POST", "/api/memory/pull", {"since": 0}, token=self.phone)
        self.assertIn("m2", [f["id"] for f in b["facts"]])

    def test_sync_lww(self):
        s, b = call("POST", "/api/sync", {"items": [{"key": "city", "val": "delhi", "ver": 1, "ts": 1000}]}, token=self.phone)
        self.assertEqual(s, 200)
        # laptop ka purana version haar gaya -> conflict list me
        s, b = call("POST", "/api/sync", {"items": [{"key": "city", "val": "mumbai", "ver": 1, "ts": 900}]}, token=self.laptop)
        self.assertEqual(s, 200)
        self.assertEqual(len(b["conflicts"]), 1)
        self.assertEqual(b["conflicts"][0]["val"], "delhi")
        # laptop ka naya version jeet gaya
        s, b = call("POST", "/api/sync", {"items": [{"key": "city", "val": "mumbai", "ver": 2, "ts": 2000}]}, token=self.laptop)
        self.assertEqual(s, 200)
        self.assertEqual(b["items"][0]["val"], "mumbai")

    def test_body_caps(self):
        s, _ = call("POST", "/api/memory/push", {"id": "x" * 500, "text": "y" * 5000}, token=self.phone)
        self.assertIn(s, (200, 422))   # id cap ho ya reject — crash nahi hona chahiye


if __name__ == "__main__":
    unittest.main(verbosity=2)
