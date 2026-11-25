import re
import xml.etree.ElementTree as ET
from pathlib import Path
from collections import Counter

root = Path(__file__).resolve().parents[1]
langs = 'es pt vi ja zh de in ru fr th ko ar hi pl ms it nl ro'.split()
checked = 0
for module in ('app', 'codeBase', 'monetization'):
    for source in (root / module / 'src/main/res/values').glob('*.xml'):
        if source.name == 'admob_debug.xml':
            continue
        nodes = [n for n in ET.parse(source).getroot() if n.tag in ('string', 'string-array', 'plurals') and n.get('translatable') != 'false']
        if not nodes:
            continue
        for lang in langs:
            target = source.parent.parent / ('values-' + lang) / source.name
            localized = {(n.tag, n.get('name')): n for n in ET.parse(target).getroot()}
            for node in nodes:
                translated = localized[node.tag, node.get('name')]
                a = [node] if node.tag == 'string' else list(node)
                b = [translated] if translated.tag == 'string' else list(translated)
                assert len(a) == len(b), (target, node.get('name'), 'array size')
                for original, result in zip(a, b):
                    for pattern in (r'%\d*\$?[sdfoxegc]', r'\\[ntr]'):
                        assert Counter(re.findall(pattern, original.text or '')) == Counter(re.findall(pattern, result.text or '')), (target, node.get('name'), pattern)
                    assert not re.search(r'ZXQ\s*\d+\s*QXZ', result.text or ''), (target, node.get('name'))
                    checked += 1
print(f'PASS: {checked} localized entries; full coverage, array sizes, format placeholders and escapes verified.')
