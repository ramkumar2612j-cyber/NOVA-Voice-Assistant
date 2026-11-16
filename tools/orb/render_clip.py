"""Orb animation clip. Usage: python3 render_clip.py <px> <seconds> <fps> <out.webm> [mood json]. Needs ffmpeg. Renders here only; not a seamless loop."""
import sys, os, io, base64, json, subprocess, tempfile
from playwright.sync_api import sync_playwright
px = int(sys.argv[1]); secs = float(sys.argv[2]); fps = int(sys.argv[3]); out = sys.argv[4]
mood = sys.argv[5] if len(sys.argv) > 5 else '[0,0,0,0]'
frag = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'orb.frag')).read()
html = """<!doctype html><body style='margin:0'><canvas id=c width=%d height=%d></canvas><script>
const FS=%s;const c=document.getElementById('c');const gl=c.getContext('webgl2',{premultipliedAlpha:true,preserveDrawingBuffer:true,alpha:true});
function sh(t,s){const x=gl.createShader(t);gl.shaderSource(x,s);gl.compileShader(x);if(!gl.getShaderParameter(x,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(x));return x}
const pr=gl.createProgram();gl.attachShader(pr,sh(gl.VERTEX_SHADER,'#version 300 es\\nin vec2 p;void main(){gl_Position=vec4(p,0.,1.);}'));gl.attachShader(pr,sh(gl.FRAGMENT_SHADER,FS));gl.linkProgram(pr);gl.useProgram(pr);
const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,3,-1,-1,3]),gl.STATIC_DRAW);
const l=gl.getAttribLocation(pr,'p');gl.enableVertexAttribArray(l);gl.vertexAttribPointer(l,2,gl.FLOAT,false,0,0);
const S=%d;function L(n){return gl.getUniformLocation(pr,n)}
window.draw=function(t){gl.uniform2f(L('uRes'),S,S);gl.uniform2f(L('uFull'),S,S);gl.uniform2f(L('uTile'),0,0);gl.uniform2f(L('uJit'),0,0);gl.uniform1f(L('uTime'),t);gl.uniform4f(L('uMood'),...%s);gl.uniform1f(L('uBg'),1);gl.viewport(0,0,S,S);gl.clear(gl.COLOR_BUFFER_BIT);gl.drawArrays(gl.TRIANGLES,0,3);gl.finish();return c.toDataURL('image/png')};
window.__ok=1;</script>""" % (px, px, json.dumps(frag), px, mood)
d = tempfile.mkdtemp(); n = int(secs * fps)
with sync_playwright() as pw:
    br = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
    pg = br.new_page(); pg.set_default_timeout(600000); pg.set_content(html); pg.wait_for_function('window.__ok===1')
    for i in range(n):
        data = pg.evaluate('draw(%f)' % (i / fps))
        open('%s/f%04d.png' % (d, i), 'wb').write(base64.b64decode(data.split(',')[1]))
    br.close()
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(fps), '-i', d + '/f%04d.png', '-c:v', 'libvpx-vp9', '-b:v', '2M', '-pix_fmt', 'yuv420p', out], check=True)
print('wrote', out, n, 'frames')
