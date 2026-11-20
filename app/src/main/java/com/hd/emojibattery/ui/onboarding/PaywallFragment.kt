package com.hd.emojibattery.ui.onboarding

import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.R
import com.hd.emojibattery.databinding.FragmentPaywallBinding
import com.hd.emojibattery.ui.navigation.ScreenIds
import hoang.dqm.codebase.base.activity.BaseFragment

class PaywallFragment : BaseFragment<FragmentPaywallBinding, PaywallViewModel>() {
    override fun initView() {
        binding.switchTrial.isChecked = viewModel.trialEnabled.value == true
        updateSelection()
    }
    override fun initListener() {
        binding.buttonClose.setOnClickListener {
            if (arguments?.getString("screen_id") == ScreenIds.PREMIUM && parentFragmentManager.backStackEntryCount > 0) parentFragmentManager.popBackStack()
            else (requireActivity() as MainActivity).openScreen(ScreenIds.HOME, false)
        }
        binding.rowWeekly.setOnClickListener { viewModel.selectPlan(0); updateSelection() }
        binding.rowYearly.setOnClickListener { viewModel.selectPlan(1); updateSelection() }
        binding.switchTrial.setOnCheckedChangeListener { _, checked -> viewModel.toggleTrial(checked) }
        binding.buttonContinue.setOnClickListener {
            if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) {
                (requireActivity() as MainActivity).openScreen(ScreenIds.HOME, false)
                return@setOnClickListener
            }
            android.widget.Toast.makeText(requireContext(), R.string.paywall_billing_unavailable, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    override fun initData() = Unit
    private fun updateSelection() {
        val weekly = viewModel.selectedPlan.value == 0
        binding.rowWeekly.isSelected = weekly
        binding.rowYearly.isSelected = !weekly
        binding.rowWeekly.setBackgroundResource(if (weekly) com.hd.emojibattery.R.drawable.bg_plan_selected else com.hd.emojibattery.R.drawable.bg_plan)
        binding.rowYearly.setBackgroundResource(if (weekly) com.hd.emojibattery.R.drawable.bg_plan else com.hd.emojibattery.R.drawable.bg_plan_selected)
        binding.radioWeekly.isChecked = weekly
        binding.radioYearly.isChecked = !weekly
    }
}
