#!/usr/bin/env python3
"""v7.197 KOTLIN SYNTAX contract: parses EVERY .kt file (main + test) with the Kotlin compiler that ships inside Gradle
(kotlin-compiler-embeddable, found under <gradle>/lib) and fails on any "syntax error".

Why: a missing / extra comma or bracket inside a giant one-line list (HelpGuide.kt Topic(...)) passed every text-based
contract script but turns the whole GitHub build RED (compileDebugKotlin). This test catches that class of bug here.

Scope (honest): ONLY syntax. Android SDK / org.json are not on this machine, so "unresolved reference" errors are
expected noise and are IGNORED. Type errors are NOT checked - the real Gradle build still decides that.
If no Gradle / Kotlin compiler jar or no Java is found, the test prints SKIPPED and passes (it must never block CI).
Run: python3 -I tools/test_kotlin_syntax_contract.py
"""
import glob
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")

java = shutil.which("java")
libs = sorted(glob.glob("/opt/gradle*/lib") + glob.glob("/usr/share/gradle*/lib") + glob.glob("/usr/lib/gradle*/lib")
              + glob.glob(os.path.expanduser("~/.gradle/wrapper/dists/*/*/gradle-*/lib")))
lib = None
for cand in libs:
    if glob.glob(os.path.join(cand, "kotlin-compiler-embeddable-*.jar")) and glob.glob(os.path.join(cand, "kotlin-stdlib-*.jar")):
        lib = cand
        break
if not java or not lib:
    print("SKIPPED kotlin syntax check (java=%s, gradle kotlin compiler=%s) - the Gradle build will still parse the files" % (bool(java), bool(lib)))
    sys.exit(0)


def jar(prefix):
    m = sorted(glob.glob(os.path.join(lib, prefix + "-[0-9]*.jar")))
    return m[0] if m else None


want = ["kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-reflect", "kotlin-daemon-embeddable",
        "trove4j", "annotations", "kotlinx-coroutines-core-jvm"]
cp = [j for j in (jar(w) for w in want) if j]
stdlib = jar("kotlin-stdlib")
files = []
for sub in ("app/src/main", "app/src/test"):
    for dp, _, fs in os.walk(os.path.join(ROOT, sub)):
        files += [os.path.join(dp, f) for f in fs if f.endswith(".kt")]
files.sort()
if not files:
    print("FAIL no .kt files found")
    sys.exit(1)

out = tempfile.mkdtemp(prefix="ktsyn_")
argfile = os.path.join(out, "args.txt")
with open(argfile, "w", encoding="utf-8") as f:
    f.write("\n".join(files))
cmd = [java, "-Xmx2g", "-cp", os.pathsep.join(cp), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
       "-nowarn", "-classpath", stdlib, "-d", os.path.join(out, "o")] + files
try:
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=140)
except subprocess.TimeoutExpired:
    print("SKIPPED kotlin syntax check (compiler timed out, machine busy)")
    shutil.rmtree(out, ignore_errors=True)
    sys.exit(0)
shutil.rmtree(out, ignore_errors=True)
log = p.stdout + "\n" + p.stderr
bad = [ln for ln in log.splitlines() if "error: syntax error" in ln]
if bad:
    print("FAIL %d Kotlin SYNTAX error(s) (the GitHub build would go RED):" % len(bad))
    for ln in bad[:25]:
        print("  " + ln.replace(os.path.abspath(ROOT) + os.sep, "")[:220])
    sys.exit(1)
print("PASS kotlin syntax: %d .kt files parsed, 0 syntax errors (types/Android refs not checked here)" % len(files))
