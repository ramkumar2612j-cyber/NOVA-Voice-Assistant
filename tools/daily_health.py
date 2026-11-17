#!/usr/bin/env python3
"""v7.221 DAILY HEALTH CHECK (Part 5). Rozana ka sach-much health report:

  python3 tools/daily_health.py            # local run (jo checks us machine par ho sakte hain)
  python3 tools/daily_health.py --ci       # GitHub Actions me (sab checks)
  python3 tools/daily_health.py --push URL PASSWORD   # report backend ke admin panel me bhejo

Checks (jo available hai wahi chalta hai, har ek ka status likha jaata hai):
  1. Python contract suite (tools/run_all_checks.sh)
  2. Desktop node tests (desktop/)
  3. Website contract + backend tests
  4. Security scan (tools/security_scan.py)
  5. Version pins (build.gradle versionName)

Report: DAILY_REPORT_<date>.md — FAIL hone par bhi file banti hai (sach likhta hai)."""
import json, os, re, subprocess, sys, time, urllib.request, urllib.error
from datetime import datetime, timezone, timedelta

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
IST = timezone(timedelta(hours=5, minutes=30))


def run(cmd, cwd=ROOT, timeout=1500):
    """(ok, output) — timeout bhi capture."""
    try:
        r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=timeout)
        return r.returncode == 0, (r.stdout + r.stderr)[-4000:]
    except subprocess.TimeoutExpired:
        return False, "TIMEOUT after %ss" % timeout
    except Exception as e:
        return False, "%s: %s" % (e.__class__.__name__, e)


def main():
    ci = "--ci" in sys.argv
    results = []   # (name, status, detail)

    # 1) contract suite
    ok, out = run(["bash", "tools/run_all_checks.sh"])
    m = re.search(r"contract scripts: (\d+) passed, (\d+) failed", out)
    detail = m.group(0) if m else out.strip().splitlines()[-1] if out.strip() else "no output"
    results.append(("Python contract suite (196 scripts)", "PASS" if ok else "FAIL", detail))

    # 2) desktop tests
    if not os.path.isdir(os.path.join(ROOT, "desktop", "node_modules")):
        ok2, o2 = run(["npm", "install", "--no-audit", "--no-fund"], cwd=os.path.join(ROOT, "desktop"), timeout=600)
    ok2, o2 = run(["npm", "test"], cwd=os.path.join(ROOT, "desktop"), timeout=900)
    m2 = re.search(r"# (?:pass|passing) (\d+)", o2) or re.search(r"ℹ pass (\d+)", o2)
    results.append(("Desktop tests (node --test)", "PASS" if ok2 else "FAIL", (m2.group(0) if m2 else o2.strip().splitlines()[-1] if o2.strip() else "?")))

    # 3) website + backend
    okw, _w = run(["python3", "tools/test_website_contract.py"], timeout=300)
    results.append(("Website contract", "PASS" if okw else "FAIL", ""))
    okb, _b = run(["python3", "backend/test_brain_server.py"], timeout=600)
    results.append(("Backend brain tests", "PASS" if okb else "FAIL", ""))

    # 4) security scan
    oks, so = run(["python3", "tools/security_scan.py"], timeout=600)
    results.append(("Security scan", "PASS" if oks else "WARN", so.strip().splitlines()[-1] if so.strip() else ""))

    # 5) version pins
    g = open(os.path.join(ROOT, "app/build.gradle.kts"), encoding="utf-8").read()
    vm = re.search(r'versionName = "([^"]+)"', g)
    results.append(("versionName", "PASS" if vm else "FAIL", vm.group(1) if vm else "missing"))

    n_pass = sum(1 for _, s, _ in results if s == "PASS")
    overall = "PASS" if n_pass == len(results) else ("PARTIAL" if n_pass else "FAIL")

    day = datetime.now(IST).strftime("%Y-%m-%d")
    lines = [
        "# JARVIS DAILY REPORT — %s" % day,
        "",
        "Overall: **%s** (%d/%d checks pass)" % (overall, n_pass, len(results)),
        "",
        "| Check | Status | Detail |",
        "|---|---|---|",
    ]
    for name, st, det in results:
        lines.append("| %s | %s | %s |" % (name, st, (det or "").replace("|", "/")[:120]))
    lines += [
        "",
        "## Agla kadam (agar kuch FAIL ho)",
        "- Contract FAIL: FAIL wale test ka naam dekho, `python3 tools/<test>.py` chala ke detail dekho.",
        "- Desktop FAIL: `cd desktop && npm test` se kaunsa test phata dekho.",
        "- Website/Backend FAIL: deploy steps docs/DEPLOY.md me hain.",
        "- Security WARN: security_scan ka output dekho — asli secret mila to TURANT rotate karo.",
        "",
        "_Ye report automation ne banayi (tools/daily_health.py). Jhooth nahi likhta: jo chala uska hi result hai._",
    ]
    report = "\n".join(lines)
    path = os.path.join(ROOT, "DAILY_REPORT_%s.md" % day)
    with open(path, "w", encoding="utf-8") as f:
        f.write(report)
    print(report)
    print("\nReport saved: %s" % path)

    # --push URL PASSWORD
    if "--push" in sys.argv:
        i = sys.argv.index("--push")
        url, pw = sys.argv[i + 1], sys.argv[i + 2]
        try:
            req = urllib.request.Request(url.rstrip("/") + "/admin/login", method="POST",
                                         data=json.dumps({"password": pw}).encode(),
                                         headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=15) as r:
                tok = json.loads(r.read().decode())["token"]
            payload = {"day": day, "report": {"overall": overall, "checks": [{"name": n, "status": s, "detail": d} for n, s, d in results]}}
            req2 = urllib.request.Request(url.rstrip("/") + "/admin/report/daily", method="POST",
                                          data=json.dumps(payload).encode(),
                                          headers={"Content-Type": "application/json", "Authorization": "Bearer " + tok})
            urllib.request.urlopen(req2, timeout=15).read()
            print("Report pushed to the brain server.")
        except Exception as e:
            print("Push failed: %s (server down? url galat?)" % e.__class__.__name__)

    return 0 if overall == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
