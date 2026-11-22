package com.hd.emojibattery.ui.onboarding

import androidx.core.view.isVisible
import androidx.core.view.doOnLayout
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.R
import com.hd.emojibattery.databinding.FragmentIntroBinding
import com.hd.emojibattery.ui.navigation.ScreenIds
import hoang.dqm.codebase.base.activity.BaseFragment

open class IntroFragment<VM : IntroViewModel> : BaseFragment<FragmentIntroBinding, VM>() {
    private val images = intArrayOf(R.drawable.intro_figma_1, R.drawable.intro_figma_2, R.drawable.intro_figma_3, R.drawable.intro_figma_4)
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
        binding.buttonNext.isVisible = !adPage
        binding.introAdSpace.isVisible = adPage
        binding.textDescription.textSize = if (adPage) 16f else if (index == 3) 20f else 18f
        binding.root.doOnLayout {
            val sx = binding.root.width / 412f
            val sy = binding.root.height / 916f
            binding.textTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 26f * sx)
            binding.textDescription.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, (if (adPage) 16f else if (index == 3) 20f else 18f) * sx)
            fun place(view: android.view.View, x: Float, y: Float, width: Float, height: Float) {
                view.layoutParams = (view.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
                    leftMargin = (x * sx).toInt()
                    topMargin = (y * sy).toInt()
                    this.width = (width * sx).toInt()
                    this.height = (height * sy).toInt()
                }
            }
            place(binding.imageIntro, 0f, if (index == 2) -22f else 0f, 412f, 732f)
            val fadeY = if (index == 0) 558f else if (index == 2) 520f else 608f
            place(binding.introArtFade, 0f, fadeY, 412f, if (index == 0) 174f else if (index == 2) 267f else 124f)
            place(binding.introPanelScroll, 0f, if (adPage) 473f else 660f, 412f, if (adPage) 443f else 256f)
            place(binding.textTitle, if (adPage) 15f else if (index == 3) 27f else 20f, if (adPage) 24f else 32f, 380f, 35f)
            place(binding.textDescription, if (adPage) 15f else if (index == 3) 22f else 15f, if (adPage) 64f else 79f, 380f, if (adPage) 40f else 30f)
            place(binding.introIndicators, if (adPage) 16.5f else 167f, if (adPage) 135f else 141f, 79f, 10f)
            place(binding.introInlineNext, 337f, 117f, 64f, 48f)
            place(binding.introAdSpace, 0f, 172f, 412f, 271f)
            place(binding.buttonNext, 26f, 170f, 360f, 57f)
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
