import concurrent.futures as cf
import copy
import json
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANGS = 'es pt vi ja zh de in ru fr th ko ar hi pl ms it nl ro'.split()
CACHE = ROOT / '.codex-task-tmp/translation_cache.json'
cache = json.loads(CACHE.read_text('utf-8')) if CACHE.exists() else {}
files = []
for module in ('app', 'codeBase', 'monetization'):
    for path in (ROOT / module / 'src/main/res/values').glob('*.xml'):
        if path.name == 'admob_debug.xml':
            continue
        nodes = [n for n in ET.parse(path).getroot() if n.tag in ('string', 'string-array', 'plurals') and n.get('translatable') != 'false']
        if nodes:
            files.append((path, nodes))

def leaves(nodes):
    for node in nodes:
        if node.tag == 'string':
            yield node
        else:
            yield from node

texts = sorted({n.text for _, nodes in files for n in leaves(nodes) if n.text})
pattern = re.compile(r'%\d*\$?[sdfoxegc]|\\[ntr]|\$\d+(?:\.\d+)?|Emoji Battery|Dynamic Island')

def translate_batch(lang, batch):
    originals, protected = [], []
    for source in batch:
        tokens = []
        def mask(match):
            tokens.append(match.group())
            return f'ZXQ{len(tokens)-1}QXZ'
        protected.append(pattern.sub(mask, source.replace("\\'", "'").replace('\\"', '"')))
        originals.append(tokens)
    query = '\n|||\n'.join(protected)
    url = 'https://translate.googleapis.com/translate_a/single?' + urllib.parse.urlencode(dict(client='gtx', sl='en', tl='id' if lang == 'in' else lang, dt='t', q=query))
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=45) as response:
                data = json.load(response)
            result = ''.join(item[0] for item in data[0] if item[0]).split('|||')
            if len(result) != len(batch):
                if len(batch) > 1:
                    return sum((translate_batch(lang, [s]) for s in batch), [])
                raise ValueError('Batch separator lost')
            pairs = []
            for source, value, tokens in zip(batch, result, originals):
                value = value.strip()
                for i, token in enumerate(tokens):
                    marker = f'ZXQ{i}QXZ'
                    if marker not in value:
                        parts = re.split('(' + pattern.pattern + ')', source)
                        translated = []
                        for part in parts:
                            if not part or pattern.fullmatch(part) or not part.strip():
                                translated.append(part)
                            else:
                                translated.append(translate_batch(lang, [part])[0][1])
                        value = ''.join(translated)
                        break
                    value = value.replace(marker, token)
                value = value.replace("'", "\\'").replace('"', '\\"')
                pairs.append((lang + '\0' + source, value))
            return pairs
        except Exception:
            if attempt == 4:
                raise
            time.sleep(1 + attempt * 2)

jobs = []
for lang in LANGS:
    missing = [s for s in texts if lang + '\0' + s not in cache]
    batch = []
    for source in missing:
        if batch and sum(map(len, batch)) + len(source) > 1800:
            jobs.append((lang, batch))
            batch = []
        batch.append(source)
    if batch:
        jobs.append((lang, batch))
print(f'{len(texts)} unique texts, {len(LANGS)} languages, {len(jobs)} batches', flush=True)
with cf.ThreadPoolExecutor(max_workers=6) as pool:
    futures = [pool.submit(translate_batch, *job) for job in jobs]
    for i, future in enumerate(cf.as_completed(futures), 1):
        cache.update(future.result())
        CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=2), 'utf-8')
        if i % 10 == 0:
            print(f'Translated {i}/{len(jobs)} batches', flush=True)

count = 0
for lang in LANGS:
    for source, nodes in files:
        target = source.parent.parent / ('values-' + lang) / source.name
        existing = ET.parse(target).getroot() if target.exists() else ET.Element('resources')
        by_key = {(n.tag, n.get('name')): n for n in existing}
        for node in nodes:
            key = node.tag, node.get('name')
            if key in by_key:
                continue
            node = copy.deepcopy(node)
            for leaf in leaves([node]):
                if leaf.text:
                    leaf.text = cache[lang + '\0' + leaf.text]
            existing.append(node)
        ET.indent(existing, space='    ')
        target.parent.mkdir(parents=True, exist_ok=True)
        ET.ElementTree(existing).write(target, encoding='utf-8', xml_declaration=True)
        count += 1
print(f'Wrote {count} localized resource files', flush=True)
