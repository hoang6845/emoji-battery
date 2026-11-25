from pathlib import Path
p=Path('app/src/main/res/layout/fragment_paywall.xml');s=p.read_text()
s=s.replace('android:buttonTint="#8B69FB"','android:button="@null" android:buttonTint="@null" android:background="@drawable/paywall_radio" android:minWidth="0dp" android:minHeight="0dp" android:layout_marginEnd="12dp"')
for name in ['radio_weekly','radio_yearly']:
 start=s.index('<RadioButton android:id="@+id/'+name+'"'); end=s.index('/>',start)
 part=s[start:end].replace('android:layout_width="wrap_content" android:layout_height="wrap_content"','android:layout_width="22dp" android:layout_height="22dp"')
 s=s[:start]+part+s[end:]
p.write_text(s)
p=Path('.codex-task-tmp/check_premium_static.py');s=p.read_text().replace("r'binding\\.(\\w+)'", "r'(?<![\\w.])binding\\.(\\w+)'");p.write_text(s)
