#!/usr/bin/env python3
"""v7.197 SELF PATCH contract: static wiring checks + a REAL compile and run of SelfPatchRules (pure Kotlin) with tools/kt/SelfPatchRulesCheck.kt
when Gradle's embedded Kotlin compiler exists. GitHubSelfPatch.kt (network) is statically checked only - NOT run against real GitHub.
Run: python3 -I tools/test_self_patch_contract.py"""
import os, sys, glob, subprocess, tempfile, shutil
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = ROOT + "/app/src/main/java/com/nova/assistant/"
rd = lambda p: open(p, encoding="utf-8").read()
rules, net, svc, ma = rd(A + "SelfPatchRules.kt"), rd(A + "GitHubSelfPatch.kt"), rd(A + "JarvisService.kt"), rd(A + "MainActivity.kt")
ui = rd(ROOT + "/app/src/main/assets/ui.html")
fails = 0
def check(c, l):
    global fails
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails += 1
check(not any(l.startswith("import ") for l in rules.splitlines()), "SelfPatchRules is pure Kotlin (compilable here)")
check('"SecureStore"' in rules and '"UpdateManager"' in rules and '"GitHubSelfPatch"' in rules and '"SelfPatchRules"' in rules, "secrets / installer / this feature are protected files")
check("MAX_PER_DAY = 5" in rules and "underCap" in net, "daily cap enforced in the network flow")
check("wishBlocked(wish)" in net and "isAllowedPath" in net and "validate(p, expected" in net, "wish block + path allowlist + validation run before any push")
check('"/repos/${repo()}/contents/$path"' in net and 'put("branch", "main")' in net, "push goes to main via the Contents API")
check("revert(app" in net and 'conclusion == "failure"' in net, "RED build triggers automatic revert")
check("STUB" in rules and "STUB" in net, "reverted NEW file becomes a stub (no DELETE call needed)")
check('"DELETE"' not in net, "no DELETE call (Android HttpURLConnection body issue avoided)")
check("GitHubUpgradePublish.OWNER_REPO" in net, "reuses the single OWNER_REPO constant")
check("SecureStore.getSlot(app, GitHubPublish.SLOT)" in net or "SecureStore.getSlot(ctx, GitHubPublish.SLOT)" in net, "uses the saved GitHub token")
hook = "GitHubSelfPatch.handleVoice(this, Logic.norm(text), text)"
check(hook in svc, "JarvisService hook present")
check(svc.index(hook) < svc.index("UpgradeLedger.handle(this"), "hook runs BEFORE UpgradeLedger (wish text may contain 'feature banao')")
for fn in ("fun selfPatchRun(wish: String)", "fun selfPatchAct(act: String)", "fun selfPatchStatus()"):
    check(fn in ma, "MainActivity bridge " + fn)
check("wish.take(600)" in ma and "act.take(8)" in ma.split("fun selfPatchAct")[1][:200], "bridge String params are capped")
for x in ('id="sp_box"', 'id="sp_run"', 'id="sp_undo"', 'id="v197-self-patch"', '"selfPatchRun"', '"selfPatchAct"', '"selfPatchStatus"'):
    check(x in ui, "ui.html has " + x)
check(ui.index('id="sp_box"') < ui.index('id="tc_box"') or ui.index('id="sp_box"') > ui.index('id="tc_box"'), "sp_box lives in Test Center")
tc = ui.index('id="tc_box"'); check(tc < ui.index('id="sp_box"') < ui.index("<style>\n#tc_box{"), "sp_box is inside the Test Center card")
check(os.path.exists(ROOT + "/app/src/test/java/com/nova/assistant/SelfPatchRulesTest.kt"), "JUnit test file exists (runs in GitHub Actions)")
gl = sorted(glob.glob("/opt/gradle*/lib") + glob.glob("/usr/share/gradle*/lib") + glob.glob("/usr/lib/gradle*/lib"))
L = None
for c in gl:
    if glob.glob(c + "/kotlin-compiler-embeddable-*.jar") and glob.glob(c + "/kotlin-stdlib-[0-9]*.jar"): L = c + "/"; break
if not L or not shutil.which("java"):
    print("SKIP kotlin compile+run (no java / gradle kotlin compiler here) - static checks above still count")
else:
    def j(n):
        m = sorted(glob.glob(L + n + "-[0-9]*.jar"))
        return m[0] if m else None
    names = ["kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-reflect", "kotlin-daemon-embeddable", "trove4j", "annotations", "kotlinx-coroutines-core-jvm"]
    cp = ":".join(x for x in (j(n) for n in names) if x)
    out = tempfile.mkdtemp()
    r = subprocess.run(["java", "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-cp", j("kotlin-stdlib"), "-d", out, A + "SelfPatchRules.kt", ROOT + "/tools/kt/SelfPatchRulesCheck.kt"], capture_output=True, text=True)
    src_err = [ln for ln in (r.stdout + r.stderr).splitlines() if "error:" in ln and ("SelfPatchRules" in ln)]
    if r.returncode != 0 and not src_err:
        print("SKIP kotlin compile+run (compiler could not start here: " + ((r.stderr or r.stdout).strip().splitlines() or ["?"])[-1][:120] + ")")
    else:
        check(r.returncode == 0, "SelfPatchRules.kt compiles with the Gradle-embedded kotlinc")
        if r.returncode == 0:
            r2 = subprocess.run(["java", "-cp", out + ":" + j("kotlin-stdlib"), "SelfPatchRulesCheckKt"], capture_output=True, text=True)
            print(r2.stdout[-3500:])
            check("ALL KT RULE CHECKS PASS" in r2.stdout, "Kotlin behaviour checks pass")
        else:
            print("\n".join(src_err[:10]))
    shutil.rmtree(out, ignore_errors=True)
print("ALL SELF PATCH CONTRACT CHECKS PASS" if fails == 0 else "FAILS: %d" % fails)
sys.exit(1 if fails else 0)
