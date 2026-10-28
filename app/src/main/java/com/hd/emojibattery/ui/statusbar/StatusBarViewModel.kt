package com.hd.emojibattery.ui.statusbar

import androidx.lifecycle.MutableLiveData
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

open class StatusBarViewModel : BaseViewModel() {
    val selectedTab = MutableLiveData("Background")
    fun selectTab(value: String) { selectedTab.value = value }
}
