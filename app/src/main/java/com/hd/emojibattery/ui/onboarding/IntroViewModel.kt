package com.hd.emojibattery.ui.onboarding

import androidx.lifecycle.MutableLiveData
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

open class IntroViewModel : BaseViewModel() {
    val page = MutableLiveData(0)
    fun showPage(value: Int) { page.value = value.coerceIn(0, 3) }
}
