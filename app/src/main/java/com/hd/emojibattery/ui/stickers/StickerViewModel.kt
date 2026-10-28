package com.hd.emojibattery.ui.stickers

import androidx.lifecycle.MutableLiveData
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

open class StickerViewModel : BaseViewModel() {
    val query = MutableLiveData("")
    fun setQuery(value: String) { query.value = value }
}
