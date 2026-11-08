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
