"""Tiled supersampled still of the orb shader. Usage: python3 render_master.py <full_px> <out.png> [tile=1024] [passes=4] [time=2.4] [bg=0]
Renders tile by tile (SwiftShader/software GL here), accumulates jittered passes with numpy, writes a straight-alpha PNG.
Evidence: renders here only. '8K' = 8192x8192 raster of the shader, not phone-verified."""
import sys, os, io, base64, json, time
import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright
full = int(sys.argv[1]); out = sys.argv[2]
tile = int(sys.argv[3]) if len(sys.argv) > 3 else 1024
passes = int(sys.argv[4]) if len(sys.argv) > 4 else 4
tm = float(sys.argv[5]) if len(sys.argv) > 5 else 2.4
bg = float(sys.argv[6]) if len(sys.argv) > 6 else 0
frag = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'orb.frag')).read()
J = [(-.25, -.125), (.125, -.25), (.25, .125), (-.125, .25), (0, 0), (.375, -.375), (-.375, .375), (-.4, -.4)][:passes]
html = """<!doctype html><body style='margin:0'><canvas id=c width=%d height=%d></canvas><script>
const FS=%s;
const c=document.getElementById('c');const gl=c.getContext('webgl2',{premultipliedAlpha:true,preserveDrawingBuffer:true,alpha:true});
function sh(t,s){const x=gl.createShader(t);gl.shaderSource(x,s);gl.compileShader(x);if(!gl.getShaderParameter(x,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(x));return x}
const pr=gl.createProgram();gl.attachShader(pr,sh(gl.VERTEX_SHADER,'#version 300 es\\nin vec2 p;void main(){gl_Position=vec4(p,0.,1.);}'));gl.attachShader(pr,sh(gl.FRAGMENT_SHADER,FS));gl.linkProgram(pr);
if(!gl.getProgramParameter(pr,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(pr));gl.useProgram(pr);
const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,3,-1,-1,3]),gl.STATIC_DRAW);
const l=gl.getAttribLocation(pr,'p');gl.enableVertexAttribArray(l);gl.vertexAttribPointer(l,2,gl.FLOAT,false,0,0);
const T=%d,FULL=%d;
function L(n){return gl.getUniformLocation(pr,n)}
window.draw=function(tx,ty,jx,jy){gl.uniform2f(L('uRes'),T,T);gl.uniform2f(L('uFull'),FULL,FULL);gl.uniform2f(L('uTile'),tx,ty);gl.uniform2f(L('uJit'),jx,jy);
 gl.uniform1f(L('uTime'),%f);gl.uniform4f(L('uMood'),0,0,0,0);gl.uniform1f(L('uBg'),%f);gl.viewport(0,0,T,T);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);gl.drawArrays(gl.TRIANGLES,0,3);gl.finish();
 return c.toDataURL('image/png')};
window.__ok=1;
</script>""" % (tile, tile, json.dumps(frag), tile, full, tm, bg)
res = np.zeros((full, full, 4), np.float32)
with sync_playwright() as pw:
    br = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
    pg = br.new_page(viewport={'width': 256, 'height': 256})
    pg.set_default_timeout(600000)
    pg.set_content(html); pg.wait_for_function('window.__ok===1')
    n = full // tile; t0 = time.time()
    for ty in range(n):
        for tx in range(n):
            acc = np.zeros((tile, tile, 4), np.float32)
            for (jx, jy) in J:
                d = pg.evaluate('draw(%d,%d,%f,%f)' % (tx * tile, ty * tile, jx, jy))
                a = np.asarray(Image.open(io.BytesIO(base64.b64decode(d.split(',')[1]))).convert('RGBA'), np.float32)
                # canvas readback is premultiplied->unpremultiplied by toDataURL; re-premultiply before averaging
                a[..., :3] *= a[..., 3:4] / 255.0
                acc += a
            acc /= len(J)
            # GL origin is bottom-left: tile row ty counted from the bottom, so flip into image rows
            y0 = full - (ty + 1) * tile
            res[y0:y0 + tile, tx * tile:(tx + 1) * tile] = acc
            print('tile', tx, ty, '%.0fs' % (time.time() - t0), flush=True)
    br.close()
al = res[..., 3:4]
rgb = np.where(al > 0, res[..., :3] * 255.0 / np.maximum(al, 1e-3), 0)
img = np.concatenate([np.clip(rgb, 0, 255), al], axis=2).astype(np.uint8)
Image.fromarray(img, 'RGBA').save(out, optimize=False)
print('wrote', out)
