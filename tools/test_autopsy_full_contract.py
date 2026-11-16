#!/usr/bin/env python3
"""v7.36 Part 1: runs tools/autopsy_full.py inside the normal contract run. Real gaps (bridge call without Kotlin method, callback the page lacks,
manifest class missing, missing resource) fail the build step; informational lists never do."""
import os, subprocess, sys
here = os.path.dirname(os.path.abspath(__file__))
r = subprocess.run([sys.executable, '-I', os.path.join(here, 'autopsy_full.py')], capture_output=True, text=True)
print(r.stdout[-1800:])
sys.exit(r.returncode)
