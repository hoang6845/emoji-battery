"""Generate review-only catalogs. Never writes app sources/assets or publishes remote data."""
import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path(r'C:\Users\ASUS-PR0\Downloads\data_emoji_battery')
OUT = Path(__file__).resolve().parent
PATH_FIELDS = {'src', 'image_url', 'imagePath', 'imagePathThumb'}
MEDIA = {'.png', '.jpg', '.jpeg', '.webp', '.gif'}
MAX_IMAGE_BYTES = 8 * 1024 * 1024  # RemoteImageLoader: stricter than MediaAssets' 48 MiB.

def save(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def url(path):
    return path.relative_to(ROOT).as_posix()

def media(folder):
    return sorted((p for p in folder.rglob('*') if p.is_file() and p.suffix.lower() in MEDIA), key=lambda p: p.relative_to(ROOT).as_posix())

def image_item(path, prefix, locked, category=None):
    item = {'id': prefix + '_' + hashlib.sha256(path.relative_to(ROOT).as_posix().encode()).hexdigest()[:16], 'src': url(path), 'is_lock': locked}
    if category is not None:
        item['category'] = category
    return item

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--locked', choices=['true', 'false'], required=True)
    parser.add_argument('--strip-sticker-suffix', action='store_true')
    parser.add_argument('--allow-unpublished', action='store_true', help='Record local files missing on GitHub instead of claiming remote verification')
    args = parser.parse_args()
    locked = args.locked == 'true'
    catalogs = {}
    sticker_root = ROOT / 'Data Sticker' / 'Data Sticker (DONE)'
    stickers = []
    for p in media(sticker_root):
        if p.parent.name != 'gif':
            continue
        category = p.parent.parent.name
        if args.strip_sticker_suffix:
            category = category.removesuffix('_4_dong_tac')
        stickers.append({'id': 'sticker_' + hashlib.sha256(p.relative_to(ROOT).as_posix().encode()).hexdigest()[:16], 'name': p.stem, 'category': category, 'image_url': url(p), 'free': not locked})
    catalogs['data_sticker'] = stickers
    wall_root = ROOT / 'Data Wallpaper' / 'Data Wallpaper (DONE)'
    catalogs['data_wallpaper_pet_on_screen'] = [{'imagePath': url(p), 'imagePathThumb': url(p), 'category': p.parent.name, 'is_lock': locked} for p in media(wall_root)]
    catalogs['data_background'] = [image_item(p, 'background', locked) for p in media(ROOT / 'Data background status bar' / 'Data Status Bar (DONE)')]
    catalogs['data_emotion'] = [image_item(p, 'emotion', locked) for p in media(ROOT / 'Data Emotion' / 'Data Emotion (DONE)')]
    catalogs['data_animation'] = [image_item(p, 'status_animation', locked) for p in media(ROOT / 'animation' / 'Animation_Status Bar')]
    catalogs['data_charging_animation'] = [image_item(p, 'charging_animation', locked, p.parent.name) for p in media(ROOT / 'animation' / 'Animation_Charging Battery')]
    catalogs['data_battery_v3'] = [image_item(p, 'battery', locked, p.parent.name) for p in media(ROOT / 'battery')]
    catalogs['data_battery_troll'] = [image_item(p, 'troll', locked, p.parent.name) for p in media(ROOT / 'Data Battery Troll' / 'Data Battery Troll (DONE)')]
    excluded = []
    for key, items in catalogs.items():
        accepted = []
        for item in items:
            oversized = []
            for relative in {v for field, v in item.items() if field in PATH_FIELDS}:
                size = (ROOT / relative).stat().st_size
                if size > MAX_IMAGE_BYTES:
                    oversized.append({'path': relative, 'bytes': size})
            if oversized:
                excluded.append({'catalog': key, 'files': oversized})
            else:
                accepted.append(item)
        catalogs[key] = accepted
    remote = json.loads((OUT / 'github-files.json').read_text(encoding='utf-8'))
    tracked = {f['path']: f for f in remote['files']}
    urls = {value for items in catalogs.values() for item in items for field, value in item.items() if field in PATH_FIELDS}
    unpublished = []
    for relative in sorted(urls):
        assert not relative.startswith('/') and '://' not in relative and '..' not in Path(relative).parts
        if relative not in tracked:
            assert args.allow_unpublished, relative
            unpublished.append(relative)
            continue
        content = (ROOT / relative).read_bytes()
        blob_sha = hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()
        assert blob_sha == tracked[relative]['sha'], relative
    for key, items in catalogs.items():
        ids = [item.get('id', item.get('imagePath')) for item in items]
        assert len(ids) == len(set(ids)), key
        save(key + '.json', items)
    # Review map: values stay arrays for readability; not a Firebase template import.
    save('firebase-values.review.json', catalogs)
    save('validation.json', {'github_commit': remote['commit'], 'unique_artwork_urls_verified_against_github_blob_sha': len(urls) - len(unpublished), 'unpublished_local_paths': unpublished, 'counts': {key: len(items) for key, items in catalogs.items()}, 'categories': {key: dict(Counter(item['category'] for item in items if 'category' in item)) for key, items in catalogs.items()}, 'is_lock': locked, 'max_artwork_bytes': MAX_IMAGE_BYTES, 'excluded_oversized': excluded, 'published': False})
    print(json.dumps({'counts': {key: len(items) for key, items in catalogs.items()}, 'verified_artwork_urls': len(urls) - len(unpublished), 'unpublished': len(unpublished)}, indent=2))

if __name__ == '__main__':
    main()
