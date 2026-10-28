package com.hd.emojibattery

import com.hd.emojibattery.ui.routes.HomeFrameFragment
import hoang.dqm.codebase.base.application.BaseApplication
import hoang.dqm.codebase.data.AppInfo

class EmojiBatteryApplication : BaseApplication() {
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
