#!/usr/bin/env python3
"""v7.41 PART 5 static check: design blocks exist, sit before p5_log, no storage, JS parses (needs node), nothing blocks touch."""
import re,subprocess,sys
s=open('app/src/main/assets/ui.html').read(); bad=[]
log=s.find('<script id="p5_log">')
for i in ('p5_ds','p5_ds2','p5_ds_js','p4b_queue'):
    k=s.find('id="%s"'%i)
    if k<0 or k>log: bad.append('missing or after p5_log: '+i)
blk=''.join(re.findall(r'<(?:style|script) id="p5_ds[^>]*>.*?</(?:style|script)>',s,re.S))
if 'localStorage' in blk or 'sessionStorage' in blk: bad.append('storage used')
if 'pointer-events:none' not in blk: bad.append('boot overlay may block touch')
if s.count('{')<1: bad.append('empty')
js=re.search(r'<script id="p5_ds_js">(.*?)</script>',s,re.S).group(1); open('/tmp/_ds.js','w').write(js)
if subprocess.run(['node','--check','/tmp/_ds.js']).returncode: bad.append('js syntax')
print('PASS' if not bad else 'FAIL '+str(bad)); sys.exit(1 if bad else 0)
