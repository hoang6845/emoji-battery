"""Tap-through checks for the preview APK; run from the repository root."""
import subprocess, time, re, json, sys
import xml.etree.ElementTree as ET
from pathlib import Path
adb=r'C:/Users/ASUS-PR0/AppData/Local/Android/Sdk/platform-tools/adb.exe'
out=Path('docs/ui-comparison')
results=json.loads((out/'smoke-results.json').read_text()) if len(sys.argv)>1 and (out/'smoke-results.json').exists() else []
def run(*args):
    p=subprocess.run([adb,'-s','emulator-5554',*args],capture_output=True,timeout=45)
    if p.returncode: raise RuntimeError(p.stderr.decode(errors='replace'))
    return p.stdout
def tree():
    run('shell','uiautomator','dump','/sdcard/emoji-flow.xml')
    return ET.fromstring(run('shell','cat','/sdcard/emoji-flow.xml'))
def click(resource=None,text=None):
    for n in tree().iter('node'):
        if (resource and n.get('resource-id','').endswith('/'+resource)) or (text and n.get('text')==text):
            x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
            if x2>x1 and y2>y1:
                run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(1); return
    raise AssertionError(f'Cannot find {resource or text}')
def expect(resource):
    assert any(n.get('resource-id','').endswith('/'+resource) for n in tree().iter('node')),resource
def start(route):
    run('shell','am','start','-W','-f','0x20000000','-n','com.hd.emojibattery/.MainActivity','--es','target_screen',route)
    time.sleep(1)
def scenario(name,fn):
    results[:] = [r for r in results if r['flow'] != name]
    try: fn(); results.append({'flow':name,'passed':True}); print(name,'PASS',flush=True)
    except Exception as e: results.append({'flow':name,'passed':False,'error':str(e)}); print(name,'FAIL',str(e),flush=True)
    (out/('smoke-'+name+'.png')).write_bytes(run('exec-out','screencap','-p'))
    (out/'smoke-results.json').write_text(json.dumps(results,indent=2))
def onboarding():
    start('870:9122'); click('language_continue'); expect('button_next')
    for _ in range(4): click('button_next')
    expect('button_continue'); click('button_continue'); expect('nav_home')
def pet():
    start('870:548'); click('shortcut_pet'); expect('pet_slots')
    click('pet_more'); expect('pet_grid')
    start('870:2117'); click('pet_download_cancel'); expect('pet_rating_rate'); click('pet_rating_later'); expect('pet_slots')
def sticker():
    start('870:3362'); click('sticker_canvas'); expect('adjust_size'); click('adjust_apply'); click('feature_footer_action'); expect('nav_home')
def customize():
    start('870:1536'); expect('notch_vertical'); click('feature_footer_action'); expect('customize_charge')
    click('customize_troll'); expect('troll_catalog')
def settings():
    start('870:4044'); click('settings_language'); expect('language_options'); click('language_continue'); expect('settings_main_content')
    click('settings_restricted'); expect('restricted_apply'); click('restricted_apply'); expect('settings_main_content')
    click('settings_feedback'); click('feedback_choice_0'); click('feedback_send'); expect('feedback_done')
def charging():
    start('870:4387'); click('charging_full_apply'); assert any(n.get('text') == 'Charging animation applied!' for n in tree().iter('node')); run('shell','input','keyevent','4')
def status_icons():
    start('870:5908'); click('icon_data'); click(text='5G'); expect('status_preview_value')
    assert any(n.get('resource-id','').endswith('/status_preview_value') and n.get('text','').endswith('5G') for n in tree().iter('node'))
    click('feature_footer_action'); expect('icon_styles')

def troll():
    start('870:4522'); click('troll_percentage'); expect('percentage_input'); click('percentage_input')
    run('shell','input','keyevent','123','67','67','67'); run('shell','input','text','123')
    click('percentage_apply')
    assert any(n.get('resource-id','').endswith('/troll_value') and n.get('text')=='123%' for n in tree().iter('node'))
    click('feature_footer_action'); expect('customize_charge')

for name,fn in [('onboarding' ,onboarding),('pet',pet),('sticker',sticker),('customize',customize),('settings',settings),('charging',charging),('status_icons',status_icons),('troll',troll)]:
    if len(sys.argv)==1 or name in sys.argv[1:]: scenario(name,fn)
