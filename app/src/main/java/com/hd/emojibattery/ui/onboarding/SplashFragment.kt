package com.hd.emojibattery.ui.onboarding

import android.os.Handler
import android.os.Looper
import android.view.View
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.databinding.FragmentSplashBinding
import com.hd.emojibattery.ui.navigation.ScreenIds
import hoang.dqm.codebase.ui.features.splash.BaseSplashFragment

open class SplashFragment<VM : SplashViewModel> : BaseSplashFragment<FragmentSplashBinding, VM>() {
    private val fallback = Handler(Looper.getMainLooper())
    private var didNavigate = false
    private val fallbackNavigation = Runnable { continueToFirstScreen() }

    override fun initView() {
        didNavigate = false
        super.initView()
        fallback.postDelayed(fallbackNavigation, 2200L)
    }

    override fun initListener() = Unit
    override fun initData() = Unit
    override fun isInternetConnected(isInternet: Boolean) = Unit
    override fun onFetchConfigSuccess() = Unit

    override fun openHome() = continueToFirstScreen()

    private fun continueToFirstScreen() {
        if (didNavigate || !isAdded) return
        didNavigate = true
        fallback.removeCallbacks(fallbackNavigation)
        val prefs = requireContext().getSharedPreferences("emoji_battery", 0)
        val destination = if (prefs.getBoolean("intro_complete", false)) ScreenIds.HOME else ScreenIds.INTRO_1
        (requireActivity() as MainActivity).openScreen(destination, false)
    }

    override fun onDestroyView() {
        fallback.removeCallbacks(fallbackNavigation)
        super.onDestroyView()
    }
}
