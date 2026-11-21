package com.hd.emojibattery

import com.hd.emojibattery.ui.routes.HomeFrameFragment
import hoang.dqm.codebase.base.application.BaseApplication
import hoang.dqm.codebase.data.AppInfo
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.common.premium.PremiumManager

class EmojiBatteryApplication : BaseApplication() {
    override fun onCreate() {
        super.onCreate()
        AdsManager.initialize(this, PremiumManager.INSTANCE)
        val ads = AdsManager.getInstance()
        ads.configure(
            com.hd.emojibattery.ads.BatteryAdPlacements,
            isAllowShowOpenAd = true,
            isFirstSession = !getSharedPreferences("emoji_battery", 0).getBoolean("intro_complete", false)
        )
        // Local test config is usable even when Firebase is unavailable/offline.
        val config = assets.open("ads_config.json").bufferedReader().use {
            com.google.gson.Gson().fromJson(it, tpt.dev.monetization.ads.remote.RemoteAdsConfig::class.java)
        }
        ads.updateRemoteAdsConfig(config)
        ads.setGlobalAdsEnabled(!BuildConfig.UI_TEST_MODE)
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
