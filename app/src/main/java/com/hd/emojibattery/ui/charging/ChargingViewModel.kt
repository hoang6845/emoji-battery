package com.hd.emojibattery.ui.charging

import androidx.lifecycle.MutableLiveData
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

open class ChargingViewModel : BaseViewModel() {
    val selectedAnimation = MutableLiveData("Pulse")
    fun selectAnimation(value: String) { selectedAnimation.value = value }
}
