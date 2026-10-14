# Independent numpy reference of the Kaldi fbank used by Fbank.kt. Usage: python3 fbank_reference.py  (writes t.pcm + ref.txt)
# Then compile Fbank.kt + FbankMain.kt with a Kotlin compiler (kc.sh.txt shows how, using the kotlin jars inside Gradle's lib folder)
# and compare kt.txt with ref.txt. Last run: 398 frames, max abs diff 1.1e-06.
import numpy as np
rng=np.random.default_rng(1)
n=16000*4
t=np.arange(n)/16000
x=(3000*np.sin(2*np.pi*220*t)*np.sin(2*np.pi*1.5*t)+1500*np.sin(2*np.pi*1800*t)+400*rng.standard_normal(n)+200).astype(np.int16)
x.tofile('t.pcm')
def mel(f): return 1127.0*np.log(1+f/700.0)
x=x.astype(np.float64)
N=400;S=160;NF=512
T=1+(len(x)-N)//S
win=np.power(0.5-0.5*np.cos(2*np.pi*np.arange(N)/(N-1)),0.85)
low=mel(20.0);high=mel(8000.0);d=(high-low)/81
fb=np.zeros((80,256))
for b in range(80):
    l=low+b*d;c=low+(b+1)*d;r=low+(b+2)*d
    for k in range(256):
        m=mel(16000.0/512*k)
        if l<m<r: fb[b,k]=(m-l)/(c-l) if m<=c else (r-m)/(r-c)
out=np.zeros((T,80))
for f in range(T):
    fr=x[f*S:f*S+N].copy(); fr-=fr.mean()
    fr2=fr.copy(); fr2[1:]=fr[1:]-0.97*fr[:-1]; fr2[0]=fr[0]-0.97*fr[0]
    fr2*=win
    p=(np.abs(np.fft.rfft(fr2,NF))**2)[:256]
    out[f]=np.log(np.maximum(fb@p,1.1920929e-07))
out-=out.mean(0)
np.savetxt('ref.txt',out.reshape(-1),fmt='%.9g')
