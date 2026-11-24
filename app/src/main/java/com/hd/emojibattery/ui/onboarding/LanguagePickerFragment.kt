package com.hd.emojibattery.ui.onboarding


import com.hd.emojibattery.databinding.FragmentLanguagePickerBinding
import com.hd.emojibattery.ui.navigation.ScreenIds
import com.hd.emojibattery.ui.routes.LanguageViewModel
import hoang.dqm.codebase.base.activity.BaseFragment
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LanguagePickerFragment : BaseFragment<FragmentLanguagePickerBinding, LanguageViewModel>() {
    private var hasSelected = false
    private var loadingJob: Job? = null
    private var loadingDialog: android.app.Dialog? = null
    private val preferences by lazy { requireContext().getSharedPreferences("emoji_battery", 0) }
    override fun initView() {
        (requireActivity() as LanguageActivity).batteryAds.beginLanguage()
        hasSelected = false
        binding.languageBack.visibility = if ((requireActivity() as LanguageActivity).fromOnboarding) View.INVISIBLE else View.VISIBLE
        binding.languageContinue.visibility = View.INVISIBLE
        binding.languageContinue.isEnabled = false
        val flags = intArrayOf(com.hd.emojibattery.R.drawable.flag_spain, com.hd.emojibattery.R.drawable.flag_united_states, com.hd.emojibattery.R.drawable.flag_portugal, com.hd.emojibattery.R.drawable.flag_vietnam, com.hd.emojibattery.R.drawable.flag_japan, com.hd.emojibattery.R.drawable.flag_china, com.hd.emojibattery.R.drawable.flag_germany, com.hd.emojibattery.R.drawable.flag_indonesia, com.hd.emojibattery.R.drawable.flag_russia, com.hd.emojibattery.R.drawable.flag_france, com.hd.emojibattery.R.drawable.flag_thailand, com.hd.emojibattery.R.drawable.flag_south_korea, com.hd.emojibattery.R.drawable.flag_united_arab_emirates, com.hd.emojibattery.R.drawable.flag_india, com.hd.emojibattery.R.drawable.flag_poland, com.hd.emojibattery.R.drawable.flag_malaysia)
        flags.forEachIndexed { index, art ->
            val row = binding.languageOptions.getChildAt(index) as android.widget.RadioButton
            row.text = row.text.toString().substringAfter("   ")
            val flag = androidx.core.content.ContextCompat.getDrawable(requireContext(), art)!!
            val size = (40 * resources.displayMetrics.density).toInt()
            flag.setBounds(0, 0, size, size)
            row.setCompoundDrawables(flag, null, null, null)
            row.compoundDrawablePadding = (10 * resources.displayMetrics.density).toInt()
            // CompoundButton anchors its RTL indicator to the edge independently of view padding.
            androidx.core.widget.CompoundButtonCompat.getButtonDrawable(row)?.let { indicator ->
                val inset = (8 * resources.displayMetrics.density).toInt()
                row.buttonDrawable = android.graphics.drawable.InsetDrawable(indicator.mutate(), 0, 0, inset, 0)
            }
            androidx.core.widget.CompoundButtonCompat.setButtonTintList(row, android.content.res.ColorStateList.valueOf(0xffa27cff.toInt()))
        }
        binding.languageOptions.clearCheck()
        showLanguageLoading(false)
    }
    override fun initData() = Unit
    override fun initListener() {
        binding.languageOptions.setOnCheckedChangeListener { _, _ ->
            if (!hasSelected) {
                hasSelected = true
                (requireActivity() as LanguageActivity).batteryAds.showLanguageReload(this)
                showLanguageLoading(true)
            }
        }
        binding.languageBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.languageContinue.setOnClickListener {
            if (!hasSelected || !binding.languageContinue.isEnabled) return@setOnClickListener
            val selected = binding.languageOptions.indexOfChild(binding.languageOptions.findViewById(binding.languageOptions.checkedRadioButtonId))
            com.hd.emojibattery.runtime.LanguagePreference.save(preferences, selected)
            binding.languageContinue.isEnabled = false
            (requireActivity() as LanguageActivity).completeSelection()
        }
    }

    private fun showLanguageLoading(showDone: Boolean) {
        loadingJob?.cancel()
        loadingDialog?.dismiss()
        loadingDialog = android.app.Dialog(requireContext(), tpt.dev.monetization.R.style.PrepareLoadingAdsDialogTheme).also {
            it.setContentView(com.hd.emojibattery.R.layout.dialog_language_loading)
            it.setCancelable(false)
            it.window?.setBackgroundDrawableResource(android.R.color.transparent)
            it.window?.setDimAmount(0f)
            it.show()
            it.window?.setLayout(-1, -1)
        }
        loadingJob = viewLifecycleOwner.lifecycleScope.launch {
            // Match IPTV: keep loading visible even on cache hits or load failure.
            delay(1500L)
            loadingDialog?.dismiss()
            loadingDialog = null
            if (showDone) {
                delay(200L)
                binding.languageContinue.visibility = View.VISIBLE
                binding.languageContinue.isEnabled = true
                binding.languageContinue.apply {
                    alpha = 0f
                    scaleX = 0.75f
                    scaleY = 0.75f
                    translationY = 6f * resources.displayMetrics.density
                    animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                        .setDuration(300L)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.languageContinue.animate().cancel()
        loadingJob?.cancel()
        loadingDialog?.dismiss()
        loadingDialog = null
        super.onDestroyView()
    }
}
