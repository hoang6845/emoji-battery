package com.hd.emojibattery.ui.onboarding

import androidx.core.view.isVisible
import androidx.core.view.doOnLayout
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.R
import com.hd.emojibattery.databinding.FragmentIntroBinding
import com.hd.emojibattery.ui.navigation.ScreenIds
import hoang.dqm.codebase.base.activity.BaseFragment

open class IntroFragment<VM : IntroViewModel> : BaseFragment<FragmentIntroBinding, VM>() {
    private val images = intArrayOf(R.drawable.intro_pet_1, R.drawable.intro_pet_2, R.drawable.intro_pet_3, R.drawable.intro_pet_5)
    private val titles by lazy { resources.getStringArray(R.array.onboarding_titles) }
    private val descriptions by lazy { resources.getStringArray(R.array.onboarding_descriptions) }

    override fun initView() {
        val route = arguments?.getString("screen_id")
        val index = when (route) { ScreenIds.INTRO_2 -> 1; ScreenIds.INTRO_3 -> 2; ScreenIds.INTRO_5 -> 3; else -> 0 }
        viewModel.showPage(index)
        renderPage(index)
    }

    override fun initListener() {
        binding.introInlineNext.setOnClickListener { binding.buttonNext.performClick() }
        binding.buttonNext.setOnClickListener {
            val current = viewModel.page.value ?: 0
            if (current < images.lastIndex) {
                val next = current + 1
                viewModel.showPage(next)
                renderPage(next)
                val route = listOf(ScreenIds.INTRO_1, ScreenIds.INTRO_2, ScreenIds.INTRO_3, ScreenIds.INTRO_5)[next]
                (requireActivity() as MainActivity).openScreen(route, false)
            } else {
                requireContext().getSharedPreferences("emoji_battery", 0).edit().putBoolean("intro_complete", true).apply()
                (requireActivity() as MainActivity).openScreen(ScreenIds.HOME, false)
            }
        }
        binding.buttonSkip.setOnClickListener {
            requireContext().getSharedPreferences("emoji_battery", 0).edit().putBoolean("intro_complete", true).apply()
            (requireActivity() as MainActivity).openScreen(ScreenIds.HOME, false)
        }
    }

    override fun initData() = Unit

    private fun renderPage(index: Int) {
        binding.imageIntro.setImageResource(images[index])
        binding.textTitle.text = titles[index]
        binding.textDescription.text = descriptions[index]
        binding.buttonNext.text = if (index == images.lastIndex) getString(R.string.intro_get_started) else getString(R.string.intro_next)
        binding.buttonSkip.isVisible = false
        val adPage = index == 1 || index == 2
        binding.introInlineNext.isVisible = adPage
        binding.introAdSpace.setBackgroundColor(if (adPage) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT)
        if (adPage) binding.buttonNext.text = "Open"
        binding.textDescription.textSize = if (adPage) 16f else 18f
        binding.root.doOnLayout {
            val scale = binding.root.height / 916f
            binding.imageIntro.layoutParams = binding.imageIntro.layoutParams.apply { height = (732 * scale).toInt() }
            binding.introPanelScroll.layoutParams = binding.introPanelScroll.layoutParams.apply { height = ((if (index == 1 || index == 2) 444 else 256) * scale).toInt() }
        }
        listOf(binding.indicator1, binding.indicator2, binding.indicator3, binding.indicator4).forEachIndexed { i, indicator ->
            indicator.layoutParams = indicator.layoutParams.apply { width = ((if (i == index) 17 else 10) * resources.displayMetrics.density).toInt() }
        }
        binding.indicator1.isSelected = index == 0
        binding.indicator2.isSelected = index == 1
        binding.indicator3.isSelected = index == 2
        binding.indicator4.isSelected = index == 3
    }
}
