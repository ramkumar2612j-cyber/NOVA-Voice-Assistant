import sys, base64, json, io
from playwright.sync_api import sync_playwright
frag=open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'orb.frag')).read()
size=int(sys.argv[1]); out=sys.argv[2]; t=float(sys.argv[3]) if len(sys.argv)>3 else 3.0
mood=json.loads(sys.argv[4]) if len(sys.argv)>4 else [0,0,0,0]
bg=float(sys.argv[5]) if len(sys.argv)>5 else 0
aa=float(sys.argv[6]) if len(sys.argv)>6 else 2
html="""<!doctype html><body style='margin:0;background:#0b0d12'><canvas id=c width=%d height=%d></canvas><script>
const VS=`#version 300 es
in vec2 p;void main(){gl_Position=vec4(p,0.,1.);}`;
const FS=%s;window.addEventListener("error",e=>{window.__done=1;document.title="ERR "+e.message});
const c=document.getElementById('c');const gl=c.getContext('webgl2',{premultipliedAlpha:true,preserveDrawingBuffer:true,alpha:true});
function sh(t,s){const x=gl.createShader(t);gl.shaderSource(x,s);gl.compileShader(x);if(!gl.getShaderParameter(x,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(x));return x}
const pr=gl.createProgram();gl.attachShader(pr,sh(gl.VERTEX_SHADER,VS));gl.attachShader(pr,sh(gl.FRAGMENT_SHADER,FS));gl.linkProgram(pr);
if(!gl.getProgramParameter(pr,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(pr));gl.useProgram(pr);
const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,3,-1,-1,3]),gl.STATIC_DRAW);
const l=gl.getAttribLocation(pr,'p');gl.enableVertexAttribArray(l);gl.vertexAttribPointer(l,2,gl.FLOAT,false,0,0);
const S=%d;
function u(n,...v){const loc=gl.getUniformLocation(pr,n);if(v.length==1)gl.uniform1f(loc,v[0]);else if(v.length==2)gl.uniform2f(loc,...v);else gl.uniform4f(loc,...v)}
u('uRes',S,S);u('uFull',S,S);u('uTile',0,0);u('uTime',%f);u('uMood',...%s);u('uJit',0,0);u('uBg',%f);
gl.viewport(0,0,S,S);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);gl.drawArrays(gl.TRIANGLES,0,3);gl.finish();
window.__done=1;
</script>"""%(size,size,json.dumps(frag),size,t,json.dumps(mood),bg)
with sync_playwright() as pw:
    b=pw.chromium.launch(args=['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'])
    pg=b.new_page(viewport={'width':size,'height':size})
    msgs=[];pg.on('console',lambda m:msgs.append(m.text));pg.on('pageerror',lambda e:msgs.append(str(e)))
    pg.set_content(html)
    pg.wait_for_function('window.__done===1',timeout=120000)
    data=pg.evaluate("document.getElementById('c').toDataURL('image/png')")
    open(out,'wb').write(base64.b64decode(data.split(',')[1]))
    print('ok',msgs)
    b.close()
