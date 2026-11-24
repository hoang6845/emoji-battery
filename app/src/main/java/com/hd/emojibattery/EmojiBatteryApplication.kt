package com.hd.emojibattery

import com.hd.emojibattery.ui.routes.HomeFrameFragment
import hoang.dqm.codebase.base.application.BaseApplication
import hoang.dqm.codebase.data.AppInfo
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.common.premium.PremiumManager

class EmojiBatteryApplication : BaseApplication() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.hd.emojibattery.runtime.LanguagePreference.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        tpt.dev.monetization.subs.manager.BillingManager.getInstance().configure(listOf(
            tpt.dev.monetization.subs.model.IAPProduct(tpt.dev.monetization.subs.model.IAPProductType.Subscription, "weekly_premium"),
            tpt.dev.monetization.subs.model.IAPProduct(tpt.dev.monetization.subs.model.IAPProductType.Subscription, "yearly_premium")
        ))
        AdsManager.initialize(this, PremiumManager.INSTANCE)
        val ads = AdsManager.getInstance()
        ads.configure(
            com.hd.emojibattery.ads.BatteryAdPlacements,
            isAllowShowOpenAd = true,
            isFirstSession = !getSharedPreferences("emoji_battery", 0).getBoolean("intro_complete", false)
        )
        val config = com.hd.emojibattery.ads.BatteryAdsConfig.load(this)
        ads.updateRemoteAdsConfig(config)
        // Consent + Firebase config must finish before any ad request, including catalog slots.
        ads.setGlobalAdsEnabled(false)
        ads.setAppOpenAdEnabled(false)
    }

    override val appInfo: AppInfo
        get() = AppInfo(
            BuildConfig.APPLICATION_ID,
            getString(R.string.app_name),
            R.mipmap.ic_launcher,
            BuildConfig.DEBUG,
            "", "", "", "",
            HomeFrameFragment::class.java
        )
}
