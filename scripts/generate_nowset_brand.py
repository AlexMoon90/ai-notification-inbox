"""Reconstructed vector master from supplied NOWSET board; no board pixels are shipped.
Requires fonttools. Render SVG exports with sharp; Android uses these same outlined paths.
"""
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
R=Path(__file__).resolve().parents[1]; OUT=R/'assets/branding'; RES=R/'app/src/main/res'
N=['M168 72 C168 30 230 30 230 72 L230 177 C230 213 201 224 180 204 L168 192 Z',
'M26 111 C26 92 40 91 54 101 L83 127 L83 182 C83 222 26 222 26 182 Z',
'M26 111 L26 80 C26 50 57 32 80 52 L217 170 C233 184 232 202 218 211 C205 221 190 215 177 204 L54 101 C43 92 30 95 26 111 Z']
COORDS=[(200,44,200,205),(54,102,54,213),(54,54,219,211)]
STOPS=[[(0,'#2F7BFF'),(.4,'#0964ED'),(1,'#0B1F44')],[(0,'#0B1F44'),(.5,'#1267EA'),(1,'#0879FF')],[(0,'#2F7BFF'),(.3,'#69AFFF'),(.6,'#1974FF'),(1,'#0B1F44')]]
DEFS='<defs>'+''.join(f'<linearGradient id="g{i}" gradientUnits="userSpaceOnUse" x1="{COORDS[i][0]}" y1="{COORDS[i][1]}" x2="{COORDS[i][2]}" y2="{COORDS[i][3]}">'+''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o,c in stops)+'</linearGradient>' for i,stops in enumerate(STOPS))+'</defs>'
DEFS_LIGHT=DEFS.replace('#0B1F44','#2459A7')
def symbol(mono=None):return ''.join(f'<path d="{p}" fill="{mono or f"url(#g{i})"}"/>' for i,p in enumerate(N))
def text_paths(text,width,x,y,bold=True,tracking=0):
 f=TTFont(R/f'app/src/main/res/font/archivo_{800 if bold else 400}.ttf');gs=f.getGlyphSet();cm=f.getBestCmap();ad=f['hmtx'].metrics
 advance=sum(ad[cm[ord(c)]][0] for c in text)+tracking*(len(text)-1);sc=width/advance;cursor=x;paths=[]
 for c in text:
  name=cm[ord(c)];pen=SVGPathPen(gs);gs[name].draw(TransformPen(pen,(sc,0,0,-sc,cursor,y)));d=pen.getCommands()
  if d:paths.append(d)
  cursor+=(ad[name][0]+tracking)*sc
 return paths
def texts(paths,color):return ''.join(f'<path d="{p}" fill="{color}"/>' for p in paths)
def save(name,w,h,body,light=False):
 (OUT/(name+'.svg')).write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{DEFS_LIGHT if light else DEFS}{body}</svg>')
word=text_paths('NOWSET',304,28,277);tag=text_paths('Set your now.',224,68,321,False,150)
save('nowset_symbol',256,256,symbol());save('nowset_symbol_light',256,256,symbol(),True);save('nowset_symbol_monochrome',256,256,symbol('#0B1F44'))
for suffix,color in [('', '#0B1F44'),('_light','#FFFFFF')]:
 save('nowset_logo_stacked'+suffix,360,344,'<g transform="translate(42 -25) scale(1.08)">'+symbol()+'</g>'+texts(word,color)+texts(tag,'#647895' if not suffix else '#B9CBEB'),bool(suffix))
 save('nowset_logo_horizontal'+suffix,560,140,'<g transform="translate(0 -6) scale(.6)">'+symbol()+'</g>'+texts(text_paths('NOWSET',350,178,92),color))
save('nowset_app_icon',512,512,'<rect width="512" height="512" rx="112" fill="#0B1F44"/><g transform="translate(64 64) scale(1.5)">'+symbol()+'</g>',True)
def vector(name,w,h,body):
 (RES/'drawable'/f'{name}.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="{w}dp" android:height="{h}dp" android:viewportWidth="{w}" android:viewportHeight="{h}">{body}</vector>')
def vpaths(mono=False,light=False):
 out=''
 for i,p in enumerate(N):
  if mono:out+=f'<path android:fillColor="#FFFFFF" android:pathData="{p}"/>';continue
  out+=f'<path android:pathData="{p}"><aapt:attr name="android:fillColor"><gradient android:startX="{COORDS[i][0]}" android:startY="{COORDS[i][1]}" android:endX="{COORDS[i][2]}" android:endY="{COORDS[i][3]}" android:type="linear">'+''.join(f'<item android:offset="{o}" android:color="{c}"/>' for o,c in [(o, '#2459A7' if light and c=='#0B1F44' else c) for o,c in STOPS[i]])+'</gradient></aapt:attr></path>'
 return out
vector('nowset_symbol',256,256,vpaths())
vector('nowset_symbol_light',256,256,vpaths(light=True))
vector('nowset_foreground',108,108,'<group android:translateX="22" android:translateY="22" android:scaleX="0.25" android:scaleY="0.25">'+vpaths(light=True)+'</group>')
vector('nowset_monochrome',108,108,'<group android:translateX="22" android:translateY="22" android:scaleX="0.25" android:scaleY="0.25">'+vpaths(True)+'</group>')
vector('ic_nowset_notification',24,24,'<group android:scaleX="0.09375" android:scaleY="0.09375">'+vpaths(True)+'</group>')
for suffix,col in [('', '#0B1F44'),('_light','#FFFFFF')]:
 body=''.join(f'<path android:fillColor="{col}" android:pathData="{p}"/>' for p in text_paths('NOWSET',184,8,34)+text_paths('Set your now.',126,37,60,False,150))
 vector('nowset_wordmark'+suffix,200,80,body)
