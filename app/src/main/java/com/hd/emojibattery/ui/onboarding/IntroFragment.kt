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
        binding.root.onSwipe = { forward ->
            val current = viewModel.page.value ?: 0
            val pages = listOf(ScreenIds.INTRO_1, ScreenIds.INTRO_2, ScreenIds.INTRO_3, ScreenIds.INTRO_5)
            val target = current + if (forward) 1 else -1
            if (target in pages.indices) (requireActivity() as MainActivity).openScreen(pages[target], false)
        }
        binding.introInlineNext.setOnClickListener { binding.buttonNext.performClick() }
        binding.buttonNext.setOnClickListener {
            val current = viewModel.page.value ?: 0
            if (current < images.lastIndex) {
                val next = current + 1
                val route = listOf(ScreenIds.INTRO_1, ScreenIds.INTRO_2, ScreenIds.INTRO_3, ScreenIds.INTRO_5)[next]
                (requireActivity() as MainActivity).openScreen(route, false)
            } else {
                requireContext().getSharedPreferences("emoji_battery", 0).edit().putBoolean("intro_complete", true).apply()
                (requireActivity() as MainActivity).openScreen(if (tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed()) ScreenIds.HOME else ScreenIds.PAYWALL, false)
            }
        }
        binding.buttonSkip.setOnClickListener {
            requireContext().getSharedPreferences("emoji_battery", 0).edit().putBoolean("intro_complete", true).apply()
            (requireActivity() as MainActivity).openScreen(if (tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed()) ScreenIds.HOME else ScreenIds.PAYWALL, false)
        }
    }

    override fun initData() = Unit

    private fun renderPage(index: Int) {
        binding.imageIntro.setImageResource(images[index])
        binding.textTitle.text = titles[index]
        binding.textDescription.text = descriptions[index]
        binding.buttonNext.text = if (index == images.lastIndex) getString(R.string.intro_get_started) else getString(R.string.intro_next)
        binding.introInlineNext.text = binding.buttonNext.text
        binding.buttonSkip.isVisible = false
        val adPage = index == 1 || index == 3
        binding.introInlineNext.isVisible = adPage
        binding.buttonNext.isVisible = !adPage
        binding.textDescription.textSize = if (adPage) 16f else if (index == 3) 20f else 18f
        fun layoutPage() {
            // Landscape has a flow layout with a pinned CTA, rather than portrait coordinates.
            if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
                binding.root.width == 0 || binding.root.height == 0) return
            val sx = binding.root.width / 412f
            val sy = binding.root.height / 916f
            binding.textTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 26f * sx)
            binding.textDescription.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, (if (adPage) 16f else if (index == 3) 20f else 18f) * sx)
            fun place(view: android.view.View, x: Float, y: Float, width: Float, height: Float?) {
                view.layoutParams = (view.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
                    leftMargin = (x * sx).toInt()
                    val verticalScale = if (adPage && view.parent === binding.introPanelScroll) sx else sy
                    topMargin = (y * verticalScale).toInt()
                    this.width = (width * sx).toInt()
                    if (height != null) this.height = (height * verticalScale).toInt()
                }
            }
            place(binding.imageIntro, 0f, if (index == 2) -22f else 0f, 412f, 732f)
            val fadeY = if (index == 0) 558f else if (index == 2) 520f else 608f
            place(binding.introArtFade, 0f, fadeY, 412f, if (index == 0) 174f else if (index == 2) 267f else 124f)

        }
        binding.root.doOnLayout { layoutPage() }
        binding.root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            // Ads arrive asynchronously, so recompute after the reserved space changes.
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) layoutPage()
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
