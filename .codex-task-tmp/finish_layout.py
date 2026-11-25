from pathlib import Path
p=Path('app/src/main/res/layout/fragment_paywall.xml');s=p.read_text()
s=s.replace('<View android:layout_width="match_parent" android:layout_height="363dp"','<ImageView android:layout_width="match_parent" android:layout_height="174dp" android:layout_marginTop="518dp" android:src="@drawable/paywall_hero_fade" android:scaleType="fitXY" android:importantForAccessibility="no"/>\n<View android:layout_width="match_parent" android:layout_height="363dp"')
s=s.replace('<LinearLayout android:id="@+id/row_yearly"','<ImageView android:layout_width="125dp" android:layout_height="42dp" android:layout_gravity="center_horizontal" android:layout_marginBottom="-42dp" android:elevation="3dp" android:src="@drawable/paywall_best_offer" android:scaleType="fitCenter" android:importantForAccessibility="no"/>\n<LinearLayout android:id="@+id/row_yearly"')
s=s.replace('android:text="@string/paywall_cancel" android:textColor="#27205F" android:textSize="18sp"','android:text="@string/paywall_cancel" android:textColor="#27205F" android:textSize="14sp"')
p.write_text(s)
