#!/usr/bin/env python3
"""v7.220 WEBSITE contract (Part 4): official website me zaroori sections, implemented-vs-planned badges
(honest), admin panel login flow, koi fake '100% tested' claim nahi, links valid."""
import os, re, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
bad = []
I = rd('website/index.html')
A = rd('website/admin.html')
JS = rd('website/assets/site.js')
CSS = rd('website/assets/site.css')
W = rd('.github/workflows/website.yml')

# --- 1) zaroori sections (owner ke PART 4 requirements)
for need in ['id="features"', 'id="devices"', 'id="docs"', 'id="privacy"', 'id="contact"', 'id="status"']:
    if need not in I: bad.append('index.html missing section ' + need)
# --- 2) implemented vs planned clearly alag
if I.count('pill imp') < 8: bad.append('IMPLEMENTED badges kam hain (features incomplete?)')
if I.count('pill plan') < 3: bad.append("PLANNED badges kam hain — sab kuch 'implemented' dikhaana jhooth hai")
if 'PLANNED' not in I: bad.append('planned-legend missing')
# --- 3) koi fake claim nahi
for pat in [r'100% (working|tested)', r'fully tested', r'sab (kuch )?(tested|kaam karta)']:
    if re.search(pat, I, re.I): bad.append('fake claim: ' + pat)
# honest note TEST_STATUS ke baare me
if 'TEST_STATUS.md' not in I: bad.append('honest note (TEST_STATUS.md reference) missing')
# TV honest status
if 'test pending' not in I.lower(): bad.append('TV ka honest "test pending" note missing')
# --- 4) admin panel: login + role protection + sections
for need in ['id="login"', 'id="dash"', '/admin/login', 'admin/paircode', 'admin/changelog/publish', 'admin/reports']:
    if need not in A + JS: bad.append('admin panel missing ' + need)
if 'noindex,nofollow' not in A: bad.append('admin page not noindexed')
if 'sessionStorage' not in JS: bad.append('admin token sessionStorage me nahi (localStorage me hota to XSS risk)')
# --- 5) static + zero dependency (free-first)
if re.search(r'<script[^>]+src="https?://', I + A): bad.append('external JS dependency (tracking risk) — sab local hona chahiye')
if 'fetch' in JS and 'api/status' not in JS: bad.append('backend /api/status integration missing')
# --- 6) privacy claims jo code sach me karta hai
for need in ['AES-256-GCM', 'ntfy', 'HARD DENY', 'Keystore']:
    if need not in I: bad.append('privacy section missing tech truth: ' + need)
# --- 7) workflow exists (GitHub Pages free)
if 'actions/deploy-pages' not in W: bad.append('website.yml Pages deploy missing')
if 'test_website_contract.py' not in W: bad.append('workflow website contract check nahi chala raha')

if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS website contract (sections + honest badges + admin + free static)')
