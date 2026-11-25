from pathlib import Path
p=Path('app/src/main/res/layout/fragment_paywall.xml'); s=p.read_text()
old='<TextView android:id="@+id/price_yearly" android:layout_width="wrap_content" android:layout_height="wrap_content" android:maxWidth="140dp" android:gravity="end" android:textColor="#27205F" android:textSize="14sp" />'
new='''<LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:gravity="end" android:orientation="vertical">
<TextView android:id="@+id/price_yearly" android:layout_width="wrap_content" android:layout_height="wrap_content" android:maxWidth="160dp" android:gravity="end" android:textColor="#27205F" android:textStyle="bold" android:textSize="18sp"/>
<TextView android:id="@+id/price_yearly_total" android:layout_width="wrap_content" android:layout_height="wrap_content" android:maxWidth="160dp" android:gravity="end" android:textColor="#716A8E" android:textSize="12sp"/>
</LinearLayout>'''
assert old in s; s=s.replace(old,new).replace('android:id="@+id/price_weekly"','android:id="@+id/price_weekly" android:textStyle="bold"').replace('android:textSize="14sp" />','android:textSize="18sp" />')
p.write_text(s)
