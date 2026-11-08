package com.hd.emojibattery.ui.home

import androidx.lifecycle.MutableLiveData
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

open class HomeViewModel : BaseViewModel() {
    val category = MutableLiveData("All")
    fun selectCategory(value: String) { category.value = value }
}
