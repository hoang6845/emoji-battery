package com.hd.emojibattery.ui.settings

import androidx.lifecycle.MutableLiveData
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

open class SettingsViewModel : BaseViewModel() {
    val feedbackReason = MutableLiveData<String?>()
    fun setFeedbackReason(value: String?) { feedbackReason.value = value }
}
