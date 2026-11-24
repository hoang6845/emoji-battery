package com.hd.emojibattery.ui.onboarding

import androidx.lifecycle.MutableLiveData
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

class PaywallViewModel : BaseViewModel() {
    val selectedPlan = MutableLiveData(0)
    fun selectPlan(value: Int) { selectedPlan.value = value.coerceIn(0, 1) }
}
