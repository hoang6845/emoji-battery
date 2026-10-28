package com.hd.emojibattery.ui.customize

import androidx.lifecycle.MutableLiveData
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

open class CustomizeViewModel : BaseViewModel() {
    val activeGesture = MutableLiveData("Single-tap action")
    fun selectGesture(value: String) { activeGesture.value = value }
}
