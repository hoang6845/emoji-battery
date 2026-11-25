from pathlib import Path
p=Path('app/src/main/res/layout/fragment_paywall.xml');s=p.read_text().replace('android:layout_height="732dp" android:translationY="-40dp"','android:layout_height="692dp"')
s=s.replace('<View android:layout_width="match_parent" android:layout_height="363dp" android:layout_marginHorizontal="15dp" android:layout_marginTop="258dp" android:background="@drawable/bg_paywall_content"/>','<ImageView android:layout_width="match_parent" android:layout_height="363dp" android:layout_marginHorizontal="15dp" android:layout_marginTop="258dp" android:src="@drawable/paywall_content_panel" android:scaleType="fitXY" android:importantForAccessibility="no"/>')
p.write_text(s)
