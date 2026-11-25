import re
from pathlib import Path
from PIL import Image
import xml.etree.ElementTree as E
root=Path('app/src/main')
for name in ['fragment_paywall.xml']:
 p=root/'res/layout'/name
 tree=E.parse(p)
 ids={v.split('/')[-1] for e in tree.iter() for k,v in e.attrib.items() if k.endswith('}id')}
 code=(root/'java/com/hd/emojibattery/ui/onboarding/PaywallFragment.kt').read_text(encoding='utf-8-sig')
 def camel(s):
  bits=s.split('_'); return bits[0]+''.join(b.title() for b in bits[1:])
 names={camel(x) for x in ids}|{'root'}
 refs=set(re.findall(r'(?<![\w.])binding\.(\w+)',code))
 assert not refs-names,refs-names
 for match in re.findall(r'@drawable/(\w+)',p.read_text()):
  assert list((root/'res/drawable').glob(match+'.*')),match
for p in [root/'res/drawable/bg_paywall_button.xml']:
 E.parse(p)
for name in ['paywall_title_art','paywall_hero','paywall_close','paywall_best_offer']:
 im=Image.open(root/'res/drawable'/f'{name}.png'); print(name,im.size)
print('PASS: paywall XML, drawable references and view-binding IDs')
