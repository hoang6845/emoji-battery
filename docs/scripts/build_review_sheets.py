"""Create compact review sheets from captures verified against the built APK."""
from pathlib import Path
import hashlib,json
from PIL import Image,ImageOps,ImageDraw
root=Path('docs/ui-comparison')
sha=hashlib.sha256(Path('app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
compat_file=root/'capture-compatibility.json'
compat=json.loads(compat_file.read_text()) if compat_file.exists() else {}
def current_or_unchanged(row):
    if row.get('apk_sha256')==sha: return True
    if compat.get('final_apk_sha256')!=sha: return False
    if 'unchanged_route_hashes' in compat: return compat['unchanged_route_hashes'].get(row['route'])==row.get('apk_sha256')
    return row.get('apk_sha256')==compat.get('capture_apk_sha256') and row['route'] not in compat.get('changed_routes',[])
rows=[r for r in json.loads((root/'capture-results.json').read_text()) if r['screenshot'] and current_or_unchanged(r)]
for offset in range(0,len(rows),6):
    canvas=Image.new('RGB',(1080,860),'#f0edf8');draw=ImageDraw.Draw(canvas)
    for slot,row in enumerate(rows[offset:offset+6]):
        x=(slot%3)*360;y=(slot//3)*430
        draw.text((x+8,y+5),row['route']+' '+row['name'],fill='#27205f')
        name=row['route'].replace(':','-')+'.png'
        for side,base in enumerate([Path('docs/figma-reference'),root]):
            frame=ImageOps.contain(Image.open(base/name).convert('RGB'),(178,400))
            canvas.paste(frame,(x+side*180,y+25))
    canvas.save(root/('review-sheet-%02d.png'%(offset//6+1)))
print('Verified captures:',len(rows),'Review sheets:',(len(rows)+5)//6)
