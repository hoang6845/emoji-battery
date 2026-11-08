"""Build the offline reference gallery from exports and captured emulator screens."""
from pathlib import Path
import re, html, json, hashlib
from PIL import Image, ImageOps, ImageDraw
out=Path('docs/ui-comparison')
inventory=Path('docs/FIGMA_SCREEN_INVENTORY.md').read_text(encoding='utf-8')
names=dict(re.findall(r'\| `(870:\d+)` \| ([^|]+) \|',inventory))
refs=[p for p in Path('docs/figma-reference').glob('870-*.png') if p.stem.replace('-',':') in names]
refs.sort(key=lambda p:int(p.stem.split('-')[1]))
cards=[]
compat_path=out/'capture-compatibility.json'
compat=json.loads(compat_path.read_text()) if compat_path.exists() else {}
apk=Path('app/build/outputs/apk/debug/app-debug.apk')
apk_hash=hashlib.sha256(apk.read_bytes()).hexdigest() if apk.exists() else None
captures={r['route']:r for r in json.loads((out/'capture-results.json').read_text())} if (out/'capture-results.json').exists() else {}
for ref in refs:
    route=ref.stem.replace('-',':'); name=names[route].strip()
    if 'Splash' == name or name == 'Welcome Back': continue
    app=out/ref.name
    images=f'<figure><figcaption>Figma</figcaption><a href="../figma-reference/{ref.name}"><img loading="lazy" src="../figma-reference/{ref.name}"></a></figure>'
    if app.exists():
        source_hash=captures.get(route,{}).get('apk_sha256')
        compatible=compat.get('final_apk_sha256') == apk_hash and (compat.get('unchanged_route_hashes',{}).get(route) == source_hash if 'unchanged_route_hashes' in compat else source_hash == compat.get('capture_apk_sha256') and route not in compat.get('changed_routes',[]))
        current=(source_hash == apk_hash or compatible) and captures.get(route,{}).get('screenshot')
        label='Android · UI_TEST_MODE · bản mới' if current else 'Android · ảnh cũ, chưa xác minh bản mới'
        if compatible and current: label='Android · UI_TEST_MODE · bố cục không đổi ở APK cuối'
        images+=f'<figure><figcaption>{label}</figcaption><a href="{app.name}"><img loading="lazy" src="{app.name}"></a></figure>'
        comparison=Image.new('RGB',(824,948),'#f2efff'); d=ImageDraw.Draw(comparison)
        d.text((12,8),'Figma '+route,fill='#27205f'); d.text((424,8),'Android test mode',fill='#27205f')
        comparison.paste(ImageOps.contain(Image.open(ref).convert('RGB'),(412,916)),(0,32))
        comparison.paste(ImageOps.contain(Image.open(app).convert('RGB'),(412,916)),(412,32))
        comparison.save(out/('compare-'+ref.name))
    else: images+='<p class="note">Layer / nội dung tham chiếu, không phải màn độc lập.</p>'
    cards.append(f'<section data-name="{html.escape(route+" "+name)}" id="{ref.stem}"><h2>{route} · {html.escape(name)}</h2><div class="pair">{images}</div></section>')
page='''<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Emoji Battery · Figma / Android</title>
<style>*{box-sizing:border-box}body{margin:0;background:#f5f2ff;color:#27205f;font:16px system-ui}header{position:sticky;top:0;z-index:2;background:#fff;padding:18px 24px;border-bottom:1px solid #ddd}h1{font-size:22px;margin:0 0 10px}input{width:min(550px,100%);padding:12px;border:1px solid #bcb0e5;border-radius:12px}main{padding:20px;display:grid;grid-template-columns:repeat(auto-fit,minmax(520px,1fr));gap:24px}section{padding:16px;background:white;border-radius:18px;min-width:0}h2{font-size:16px}.pair{display:flex;gap:12px;align-items:flex-start}figure{margin:0;width:50%;min-width:0}img{width:100%;height:auto;border:1px solid #eee;border-radius:8px}figcaption{padding:8px 0;font-size:13px}.note{font-size:13px;color:#787080}nav{margin:10px 0}a{color:#7652d0}@media(max-width:560px){main{display:block;padding:8px}section{margin-bottom:16px;padding:8px}}</style>
<header><h1>Emoji Battery — Figma / Android</h1><input placeholder="Tìm ID hoặc tên màn hình…" oninput="document.querySelectorAll('section').forEach(s=>s.hidden=!s.dataset.name.toLowerCase().includes(this.value.toLowerCase()))"><nav><a href="#870-9122">Language</a> · <a href="#870-9073">Intro</a> · <a href="#870-8989">Paywall</a> · <a href="#870-548">Home</a> · <a href="#870-4308">Customize</a> · <a href="#870-4615">Status Bar</a></nav><div class="note">Ảnh máy ảo có thanh hệ thống và thông số pin thật. Item nội dung để trống ở mode test. Bấm ảnh để mở kích thước gốc. Splash không nằm trong phạm vi chỉnh sửa.</div></header><main>'''+''.join(cards)+'</main></html>'
(out/'index.html').write_text(page,encoding='utf-8')
print(f'Created {len(cards)} reference sections and comparison images')
