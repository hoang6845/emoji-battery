from pathlib import Path
p=Path('app/src/main/res/layout/fragment_paywall.xml')
s=p.read_text()
for asset,name in [('paywall_hero','paywall_artwork'),('paywall_title_art','paywall_heading')]:
 marker='android:src="@drawable/'+asset+'"'
 assert marker in s
 s=s.replace(marker,'android:id="@+id/'+name+'" '+marker,1)
s=s.replace('<LinearLayout android:layout_width="292dp" android:layout_height="146dp"','<LinearLayout android:id="@+id/paywall_benefits" android:layout_width="292dp" android:layout_height="146dp"',1)
s=s.replace('<RadioGroup android:layout_marginTop="30dp"','<RadioGroup android:id="@+id/paywall_plans" android:layout_marginTop="30dp"',1)
p.write_text(s)
