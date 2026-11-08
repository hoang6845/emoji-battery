package com.hd.emojibattery.ui.onboarding

import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.databinding.FragmentLanguagePickerBinding
import com.hd.emojibattery.ui.navigation.ScreenIds
import com.hd.emojibattery.ui.routes.LanguageViewModel
import hoang.dqm.codebase.base.activity.BaseFragment

class LanguagePickerFragment : BaseFragment<FragmentLanguagePickerBinding, LanguageViewModel>() {
    private val preferences by lazy { requireContext().getSharedPreferences("emoji_battery", 0) }
    override fun initView() {
        val flags = intArrayOf(com.hd.emojibattery.R.drawable.flag_spain, com.hd.emojibattery.R.drawable.flag_united_states, com.hd.emojibattery.R.drawable.flag_portugal, com.hd.emojibattery.R.drawable.flag_vietnam, com.hd.emojibattery.R.drawable.flag_japan, com.hd.emojibattery.R.drawable.flag_china, com.hd.emojibattery.R.drawable.flag_germany, com.hd.emojibattery.R.drawable.flag_indonesia, com.hd.emojibattery.R.drawable.flag_russia, com.hd.emojibattery.R.drawable.flag_france, com.hd.emojibattery.R.drawable.flag_thailand, com.hd.emojibattery.R.drawable.flag_south_korea, com.hd.emojibattery.R.drawable.flag_united_arab_emirates, com.hd.emojibattery.R.drawable.flag_india, com.hd.emojibattery.R.drawable.flag_poland, com.hd.emojibattery.R.drawable.flag_malaysia)
        flags.forEachIndexed { index, art ->
            val row = binding.languageOptions.getChildAt(index) as android.widget.RadioButton
            row.text = row.text.toString().substringAfter("   ")
            val flag = androidx.core.content.ContextCompat.getDrawable(requireContext(), art)!!
            val size = (40 * resources.displayMetrics.density).toInt()
            flag.setBounds(0, 0, size, size)
            row.setCompoundDrawables(flag, null, null, null)
            row.compoundDrawablePadding = (10 * resources.displayMetrics.density).toInt()
            androidx.core.widget.CompoundButtonCompat.setButtonTintList(row, android.content.res.ColorStateList.valueOf(0xffa27cff.toInt()))
        }
        val selected = preferences.getInt("language_index", 1)
        if (selected in 0 until binding.languageOptions.childCount)
            binding.languageOptions.check(binding.languageOptions.getChildAt(selected).id)
    }
    override fun initData() = Unit
    override fun initListener() {
        binding.languageBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.languageContinue.setOnClickListener {
            val selected = binding.languageOptions.indexOfChild(binding.languageOptions.findViewById(binding.languageOptions.checkedRadioButtonId))
            preferences.edit().putInt("language_index", selected).putBoolean("language_selected", true).apply()
            if (preferences.getBoolean("intro_complete", false) && parentFragmentManager.backStackEntryCount > 0) requireActivity().onBackPressedDispatcher.onBackPressed()
            else (requireActivity() as MainActivity).openScreen(ScreenIds.INTRO_1, false)
        }
    }
}
