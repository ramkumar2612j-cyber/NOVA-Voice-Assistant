#!/usr/bin/env python3
"""v7.136: runs tools/jarvis_server/test_server.py (fake engine) so run_all_checks.sh covers the server contract."""
import subprocess, sys, pathlib
t = pathlib.Path(__file__).resolve().parent / "jarvis_server" / "test_server.py"
r = subprocess.run([sys.executable, "-I", str(t)], capture_output=True, text=True, timeout=120)
print(r.stdout.strip() or r.stderr.strip())
sys.exit(r.returncode)
