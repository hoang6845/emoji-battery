from pathlib import Path
root=Path('app/src/main')
def edit(p, old, new):
 p=Path(p); s=p.read_text(encoding='utf-8'); assert old in s, (p,old); p.write_text(s.replace(old,new),encoding='utf-8')
base=root/'java/com/hd/emojibattery'
edit(base/'EmojiBatteryApplication.kt','        AdsManager.initialize', '''        tpt.dev.monetization.subs.manager.BillingManager.getInstance().configure(listOf(
            tpt.dev.monetization.subs.model.IAPProduct(tpt.dev.monetization.subs.model.IAPProductType.Subscription, "weekly_premium"),
            tpt.dev.monetization.subs.model.IAPProduct(tpt.dev.monetization.subs.model.IAPProductType.Subscription, "yearly_premium")
        ))
        AdsManager.initialize''')
edit(base/'ui/onboarding/SplashFragment.kt','if (returning) ScreenIds.HOME else ScreenIds.LANGUAGE, false','if (returning) { if (tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed()) ScreenIds.HOME else ScreenIds.PAYWALL } else ScreenIds.LANGUAGE, false')
edit(base/'ui/onboarding/IntroFragment.kt','openScreen(ScreenIds.HOME, false)','openScreen(if (tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed()) ScreenIds.HOME else ScreenIds.PAYWALL, false)')
edit(base/'ui/common/BaseFeatureFragment.kt','// Temporarily hide premium shortcuts on every feature screen.','// Premium shortcuts are available until the user subscribes.')
edit(base/'ui/common/BaseFeatureFragment.kt','R.id.settings_premium_shortcut).forEach','R.id.settings_premium_shortcut, R.id.settings_premium).forEach')
edit(base/'ui/common/BaseFeatureFragment.kt','binding.root.findViewById<View>(id)?.isVisible = false','binding.root.findViewById<View>(id)?.isVisible = !tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed()')
edit(base/'ui/customize/CustomizeFragment.kt','panel.findViewById<View>(R.id.customize_premium).isVisible = false','panel.findViewById<View>(R.id.customize_premium).isVisible = !tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed()')
edit(base/'ads/BatteryAds.kt','if (contains(id)) return false','if (PremiumManager.INSTANCE.isSubscribed() || contains(id)) return false')
# Remove the extra trial UI and use exported drawable artwork for every icon.
p=root/'res/layout/fragment_paywall.xml'; s=p.read_text()
start=s.index('            <LinearLayout android:layout_width="match_parent" android:layout_height="68dp" android:layout_marginTop="30dp"')
end=s.index('<RadioGroup', start)
s=s[:start]+s[end:]
s=s.replace('<RadioGroup android:layout_width=', '<RadioGroup android:layout_marginTop="30dp" android:layout_width=')
start=s.index('        <TextView android:id="@+id/button_close"'); end=s.index('/>',start)+2
s=s[:start]+'''        <ImageButton android:id="@+id/button_close" android:layout_width="48dp" android:layout_height="48dp" android:layout_gravity="top|end" android:layout_marginTop="20dp" android:layout_marginEnd="6dp" android:padding="9dp" android:background="@android:color/transparent" android:src="@drawable/paywall_close" android:contentDescription="Close" android:visibility="invisible" />'''+s[end:]
s=s.replace('android:layout_width="292dp" android:layout_height="49dp"','android:layout_width="320dp" android:layout_height="72dp"').replace('android:layout_height="287dp"','android:layout_height="299dp"').replace('android:layout_marginTop="16dp" android:layout_gravity="center_horizontal"','android:layout_marginTop="4dp" android:layout_gravity="center_horizontal"')
s=s.replace('<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/paywall_weekly_price"','<TextView android:id="@+id/price_weekly" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="…"')
s=s.replace('<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/paywall_yearly_price"','<TextView android:id="@+id/price_yearly" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="…"')
s=s.replace('android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="…"','android:layout_width="wrap_content" android:layout_height="wrap_content" android:maxWidth="140dp" android:gravity="end"')
s=s.replace('android:background="@drawable/bg_primary_button"','android:background="@drawable/bg_paywall_button"').replace('android:textSize="18sp" android:background="@drawable/bg_paywall_button"','android:textSize="22sp" android:background="@drawable/bg_paywall_button"')
s=s.replace('android:layout_marginTop="8dp" android:text="@string/paywall_cancel"','android:layout_marginTop="12dp" android:text="@string/paywall_cancel"').replace('android:textColor="#27205F" android:textSize="12sp"','android:textColor="#27205F" android:textSize="14sp"')
s=s.replace('<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:layout_marginTop="12dp" android:text="@string/paywall_legal"', '''<LinearLayout android:layout_width="match_parent" android:layout_height="48dp" android:orientation="horizontal">
<TextView android:id="@+id/button_terms" android:layout_width="0dp" android:layout_weight="1" android:layout_height="match_parent" android:gravity="start|center_vertical" android:text="Terms of Use" android:textColor="#9E9E9E" android:textSize="12sp"/>
<TextView android:id="@+id/button_restore" android:layout_width="wrap_content" android:layout_height="match_parent" android:gravity="center" android:text="Restore" android:paddingHorizontal="16dp" android:textColor="#9E9E9E" android:textSize="12sp"/>
<TextView android:id="@+id/button_privacy" android:layout_width="0dp" android:layout_weight="1" android:layout_height="match_parent" android:gravity="end|center_vertical" android:text="Privacy Policy" android:textColor="#9E9E9E" android:textSize="12sp"/>
</LinearLayout>
<TextView android:id="@+id/text_legal" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:layout_marginTop="4dp" android:text="@string/paywall_legal"''').replace('android:textSize="10sp"','android:textSize="12sp"')
p.write_text(s)
