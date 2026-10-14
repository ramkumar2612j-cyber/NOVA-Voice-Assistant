#version 300 es
precision highp float;
// NOVA orb: glass sphere + glowing neon "N" inside + 2 chrome gyro rings + satellite.
// uRes: render target size in px. uFull/uTile: used by the still renderer to draw a big image in tiles.
// uMood: x=listening y=thinking z=speaking w=level(0..1)   uJit: sub-pixel jitter for supersampled stills
uniform vec2 uRes;
uniform vec2 uFull;
uniform vec2 uTile;
uniform float uTime;
uniform vec4 uMood;
uniform vec2 uJit;      // sub-pixel offset (stills accumulate several jittered passes)
uniform float uBg;      // 1 = opaque graphite background (stills for splash/poster), 0 = transparent
out vec4 outc;

#define PI 3.14159265
mat2 rot(float a){float c=cos(a),s=sin(a);return mat2(c,-s,s,c);}
float sdTorus(vec3 p, vec2 t){vec2 q=vec2(length(p.xz)-t.x,p.y);return length(q)-t.y;}
float sdSeg(vec3 p, vec3 a, vec3 b, float r){vec3 pa=p-a,ba=b-a;float h=clamp(dot(pa,ba)/dot(ba,ba),0.,1.);return length(pa-ba*h)-r;}
float sdN(vec3 p){
  float t=.062;
  float a=sdSeg(p,vec3(-.27,-.40,0.),vec3(-.27,.40,0.),t);
  float b=sdSeg(p,vec3(.27,-.40,0.),vec3(.27,.40,0.),t);
  float c=sdSeg(p,vec3(-.27,.40,0.),vec3(.27,-.40,0.),t);
  return min(min(a,b),c);
}
vec3 palA(){return vec3(.21,.95,.76);}   // teal  #35F2C2
vec3 palB(){return vec3(.49,.36,1.);}    // violet #7C5CFF
vec3 palC(){return vec3(1.,.71,.28);}    // amber #FFB547

vec3 moodTint(){
  vec3 base=mix(palA(),palB(),.35);
  base=mix(base,vec3(.35,1.,.62),uMood.x*.7);        // listening -> green-teal
  base=mix(base,mix(palB(),palC(),.45),uMood.y*.85);   // thinking -> violet/amber
  base=mix(base,vec3(.45,.95,1.),uMood.z*.6);         // speaking -> cyan
  return base;
}
vec3 env(vec3 d){
  float y=d.y*.5+.5;
  vec3 c=mix(vec3(.008,.010,.022),vec3(.03,.04,.09),y);
  c+=palA()*pow(max(0.,sin(d.x*2.6+d.y*1.7+uTime*.15)),6.)*.9;
  c+=palB()*pow(max(0.,sin(-d.x*2.1+d.z*2.3-uTime*.11+1.7)),6.)*1.0;
  c+=vec3(1.)*smoothstep(.80,.93,dot(d,normalize(vec3(-.55,.72,.55))))*2.4;
  c+=palC()*smoothstep(.86,.97,dot(d,normalize(vec3(.7,-.35,.45))))*1.3;
  c+=vec3(.6,.8,1.)*smoothstep(.9,.99,dot(d,normalize(vec3(.1,.2,-.97))))*1.2;
  return c;
}
mat3 rotX(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,-s,0,s,c);}
mat3 rotY(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
mat3 rotZ(float a){float c=cos(a),s=sin(a);return mat3(c,-s,0,s,c,0,0,0,1);}

mat3 M1,M2;           // world -> ring local
const float R1=1.42, R2=1.20, TR=.045;
float satAng;
vec3 satPos(){ return transpose(M1)*vec3(R1*cos(satAng),0.,R1*sin(satAng)); }
float mapRings(vec3 p, out float id){
  float d1=sdTorus(M1*p,vec2(R1,TR));
  float d2=sdTorus(M2*p,vec2(R2,TR*.85));
  float ds=length(p-satPos())-.075;
  float d=d1; id=1.;
  if(d2<d){d=d2;id=2.;}
  if(ds<d){d=ds;id=3.;}
  return d;
}
vec3 ringNormal(vec3 p){
  float id; vec2 e=vec2(.0015,0.);
  return normalize(vec3(mapRings(p+e.xyy,id)-mapRings(p-e.xyy,id),mapRings(p+e.yxy,id)-mapRings(p-e.yxy,id),mapRings(p+e.yyx,id)-mapRings(p-e.yyx,id)));
}

vec4 shade(vec2 uv){
  // uv: -1..1 in y, aspect-corrected in x
  float spin=1.+uMood.y*2.4+uMood.x*.5;
  float T=uTime;
  M1=rotX(.95)*rotZ(T*.18*spin);
  M1=rotY(T*.30*spin)*M1;
  M2=rotZ(-.55)*rotX(-.35+.12*sin(T*.4));
  M2=rotY(-T*.42*spin)*M2;
  satAng=T*(.9+uMood.y*1.6);
  vec3 ro=vec3(0.,0.,3.0);
  vec3 rd=normalize(vec3(uv*.60,-1.));
  vec3 tint=moodTint();
  float pulse=uMood.z*(.5+.5*uMood.w);

  // background halo (aurora), alpha-weighted
  float r0=length(uv);
  vec3 col=vec3(0.);
  float halo=exp(-r0*r0*1.6);
  col+=tint*halo*(.06+.07*uMood.x+.06*pulse);
  col+=palB()*exp(-pow(length(uv-vec2(.45,.42))*1.4,2.))*.05;
  float alpha=clamp(halo*.22,0.,.25);

  // sphere
  float ts=-1.,tsExit=-1.;
  {vec3 oc=ro;float b=dot(oc,rd),c=dot(oc,oc)-1.;float h=b*b-c;if(h>0.){h=sqrt(h);ts=-b-h;tsExit=-b+h;}}
  // rings: march
  float tr=-1.,rid=0.;
  {
    vec3 oc=ro;float b=dot(oc,rd),c=dot(oc,oc)-2.6*2.6;float h=b*b-c;
    if(h>0.){float t=max(0.,-b-sqrt(h)),tmax=-b+sqrt(h);
      for(int i=0;i<56;i++){float d=mapRings(ro+rd*t,rid);if(d<.0016){tr=t;break;}t+=d*.9;if(t>tmax)break;}}
  }
  vec3 ringCol=vec3(0.);float ringA=0.;
  if(tr>0.){
    vec3 p=ro+rd*tr;vec3 n=ringNormal(p);vec3 rf=reflect(rd,n);
    float id;mapRings(p,id);
    float fr=pow(1.-max(0.,dot(-rd,n)),3.);
    vec3 base=id>2.5?vec3(1.):vec3(.62,.66,.76);
    ringCol=env(rf)*(.35+1.3*fr)*base+tint*.02;
    // glowing groove with an energy runner
    vec3 lp=(id<1.5?M1:M2)*p;float ang=atan(lp.z,lp.x);
    float run=pow(.5+.5*cos(ang-(id<1.5?satAng:-T*1.4*spin)),12.+uMood.y*20.);
    float groove=smoothstep(.55,1.,abs(lp.y)/(TR))*0.0;
    ringCol+=tint*run*(1.1+uMood.y*1.6+pulse);
    if(id>2.5)ringCol=vec3(1.)*(1.4)+tint*.8;
    ringA=1.;
  }
  // sphere shading
  vec3 sphCol=vec3(0.);float sphA=0.;
  if(ts>0.){
    vec3 p=ro+rd*ts;vec3 n=normalize(p);
    // gentle yaw wobble so the N reads in 3D but never turns away
    mat3 Y=rotY(.42*sin(T*.5)+uMood.x*.1*sin(T*3.))*rotX(.12*sin(T*.37));
    float cosi=max(0.,dot(-rd,n));
    float F=.04+.96*pow(1.-cosi,5.);
    vec3 rf=reflect(rd,n);
    vec3 refl=env(rf);
    // refracted ray inside
    vec3 rr=refract(rd,n,1./1.35);
    vec3 q=p;float acc=0.;vec3 glow=vec3(0.);float hit=0.;
    float len=max(0.,2.*dot(-rr,n)*0.95);
    float stp=len/26.;
    vec3 qq=q;
    for(int i=0;i<26;i++){
      qq+=rr*stp;
      vec3 g=Y*qq*1.0;
      float d=sdN(g);
      float k=exp(-d*(26.+6.*uMood.z));
      glow+=tint*k*stp*1.5;
      if(d<.004){hit=1.;break;}
    }
    // inner core glow and soft volume
    float depth=length(p)*0.;
    vec3 inner=vec3(.004,.008,.02)+mix(palB(),palA(),cosi)*.045*(1.-cosi*.5)+glow;
    inner+=mix(tint,vec3(1.),.55)*hit*(1.15+.6*pulse);
    inner+=tint*(.04+.12*pulse)*exp(-pow(length(qq.xy)*2.0,2.));
    // rim light and caustics
    vec3 rim=mix(palA(),palB(),.5+.5*sin(n.x*2.+n.y*1.5+T*.3))*pow(1.-cosi,3.)*.65;
    vec3 Ld=normalize(vec3(-.5,.75,.6));vec3 hv=normalize(Ld-rd);float spec=pow(max(0.,dot(n,hv)),220.)*3.0+pow(max(0.,dot(n,hv)),24.)*.12;
    vec3 hv2=normalize(normalize(vec3(.7,-.4,.5))-rd);spec+=pow(max(0.,dot(n,hv2)),160.)*.7;
    sphCol=inner*(1.-F)+refl*(F*1.1+.02)+rim*.8+spec*mix(vec3(1.),palC(),.0);
    // back-ring seen through the glass
    if(tr>tsExit&&ringA>0.)sphCol=mix(sphCol,ringCol,.22);
    sphA=1.;
  }
  vec3 outcol=col;float oa=alpha;
  if(sphA>0.){outcol=sphCol;oa=1.;}
  if(ringA>0.){
    if(sphA>0.&&tr>ts&&tr<tsExit){/* ring inside glass: blend */outcol=mix(outcol,ringCol,.7);}
    else if(sphA>0.&&tr>tsExit){/* behind: already blended */}
    else {outcol=ringCol;oa=1.;}
  }
  // glow bloom around sphere/rings edge
  float edge=exp(-pow(max(0.,r0-.62)*3.2,2.));
  outcol+=tint*edge*(.035+.06*pulse+.04*uMood.x);
  oa=max(oa,clamp(edge*.22,0.,.22));
  return vec4(outcol,oa);
}
vec3 aces(vec3 x){return clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);}
void main(){
  vec2 fc=gl_FragCoord.xy+uTile+uJit;
  vec2 uv=(fc-.5*uFull)/(.5*min(uFull.x,uFull.y));
  vec4 s=shade(uv);
  vec3 rgb=s.a>0.?s.rgb:vec3(0.);
  rgb=pow(aces(rgb*1.05),vec3(1./2.2));
  float a=s.a;
  if(uBg>.5){
    vec3 bg=mix(vec3(.05,.06,.10),vec3(.02,.025,.04),clamp(length(uv),0.,1.));
    outc=vec4(mix(bg,rgb,a),1.);
  } else {
    outc=vec4(rgb*a,a); // premultiplied
  }
}
