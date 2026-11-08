"""Run from the repository root with a booted emulator-5554 and the test APK installed."""
import subprocess, time, re, json, sys
from datetime import datetime, timezone
import hashlib
from pathlib import Path
adb = r'C:/Users/ASUS-PR0/AppData/Local/Android/Sdk/platform-tools/adb.exe'
out = Path('docs/ui-comparison'); out.mkdir(exist_ok=True)
def run(*args):
    try: return subprocess.run([adb,'-s','emulator-5554',*args],capture_output=True,timeout=45)
    except subprocess.TimeoutExpired as error:
        return subprocess.CompletedProcess(args,124,error.stdout or b'',b'ADB command timed out')
if run('shell','getprop','sys.boot_completed').stdout.strip() != b'1':
    raise SystemExit('Android has not completed boot; no screenshots were changed.')
if b'package:' not in run('shell','pm','path','com.hd.emojibattery').stdout:
    raise SystemExit('Preview APK is not installed; no screenshots were changed.')
apk=Path('app/build/outputs/apk/debug/app-debug.apk')
apk_hash=hashlib.sha256(apk.read_bytes()).hexdigest() if apk.exists() else None
routes = re.findall(r'const val (\w+) = "(870:\d+)"', Path('app/src/main/java/com/hd/emojibattery/ui/navigation/ScreenIds.kt').read_text())
routes.sort(key=lambda item: 0 if item[0].startswith(('INTRO','LANGUAGE','PAYWALL')) else 1)
if len(sys.argv) > 1: routes = [item for item in routes if item[1] in sys.argv[1:]]
results=json.loads((out/'capture-results.json').read_text()) if len(sys.argv)>1 and (out/'capture-results.json').exists() else []
modal_routes={'NOTCH_EXIT','GESTURE_ACTION','GESTURE_ACTION_STATE_2','CHARGE_SUCCESS','STICKER_EDIT','FAVORITES_DELETE','RESTRICTED_APPS','FEEDBACK','TROLL_PERCENT','TROLL_EXIT','STICKER_EXIT'}
restart=True
for name, route in routes:
    if name.startswith('SPLASH') or name in ['TEXT_NODE','WELCOME_BACK'] or name.startswith('STATUS_CONTENT'): continue
    # Dialogs are dismissed with the fragment view lifecycle. Use warm navigation;
    # never use Back here (it opens the exit confirmation).
    if restart: run('shell','am','force-stop','com.hd.emojibattery')
    launched=run('shell','am','start','-W','-f','0x20000000','-n','com.hd.emojibattery/.MainActivity','--es','target_screen',route)
    time.sleep(2)
    launch_text=launched.stdout.decode(errors='replace')+launched.stderr.decode(errors='replace')
    # Wait for the Android UI to become idle after fragment inflation and layout.
    tree=''
    for attempt in range(4):
        hierarchy=run('shell','uiautomator','dump','/sdcard/emoji-capture.xml')
        if b'UI hier' in hierarchy.stdout:
            tree=run('shell','cat','/sdcard/emoji-capture.xml').stdout.decode(errors='replace')
            if 'com.hd.emojibattery' in tree: break
        time.sleep(2)
    resumed=run('shell','dumpsys','activity','activities').stdout.decode(errors='replace')
    windows=run('shell','dumpsys','window').stdout.decode(errors='replace')
    focused=bool(re.search(r'mCurrentFocus=.*com\.hd\.emojibattery',windows))
    shot=run('exec-out','screencap','-p')
    valid=shot.stdout.startswith(b'\x89PNG') and launched.returncode == 0 and 'Error' not in launch_text and focused and hierarchy.returncode == 0 and 'com.hd.emojibattery' in tree and bool(re.search(r'(?:mResumedActivity|topResumedActivity).*com\.hd\.emojibattery',resumed))
    if valid:
        (out/(route.replace(':','-')+'.png')).write_bytes(shot.stdout)
        (out/(route.replace(':','-')+'.xml')).write_text(tree, encoding='utf-8')
    results=[r for r in results if r['route'] != route]
    results.append({'name':name,'route':route,'screenshot':valid,'launch':launch_text,'captured_at':datetime.now(timezone.utc).isoformat(),'apk_sha256':apk_hash})
    print(name,route,valid,flush=True)
    (out/'capture-results.json').write_text(json.dumps(results,indent=2))
    restart = not valid
