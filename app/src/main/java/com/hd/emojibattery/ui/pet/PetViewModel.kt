package com.hd.emojibattery.ui.pet

import androidx.lifecycle.MutableLiveData
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

open class PetViewModel : BaseViewModel() {
    val selectedPetId = MutableLiveData<String?>()
    fun selectPet(id: String) { selectedPetId.value = id }
}
