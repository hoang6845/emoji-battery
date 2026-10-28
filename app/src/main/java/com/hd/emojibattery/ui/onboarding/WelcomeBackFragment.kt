package com.hd.emojibattery.ui.onboarding

import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.ui.navigation.ScreenIds
import com.hd.emojibattery.databinding.FragmentWelcomeBackBinding
import hoang.dqm.codebase.base.activity.BaseFragment

class WelcomeBackFragment : BaseFragment<FragmentWelcomeBackBinding, WelcomeBackViewModel>() {
    override fun initView() = Unit
    override fun initListener() {
        binding.buttonWelcomeHome.setOnClickListener { (requireActivity() as MainActivity).openScreen(ScreenIds.HOME, false) }
    }
    override fun initData() = Unit
}
