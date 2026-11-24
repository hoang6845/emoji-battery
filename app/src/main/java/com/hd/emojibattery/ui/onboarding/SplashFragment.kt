package com.hd.emojibattery.ui.onboarding

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.databinding.FragmentSplashBinding
import com.hd.emojibattery.ui.navigation.ScreenIds
import hoang.dqm.codebase.base.activity.BaseFragment
import kotlinx.coroutines.launch

open class SplashFragment<VM : SplashViewModel> : BaseFragment<FragmentSplashBinding, VM>() {
    private val handler = Handler(Looper.getMainLooper())
    private var navigated = false
    private var startedAds = false
    private val offlineFallback = Runnable {
        val owner = viewLifecycleOwnerLiveData.value ?: return@Runnable
        owner.lifecycleScope.launch {
            owner.lifecycle.withResumed { if (!startedAds) continueToFirstScreen() }
        }
    }

    override fun initView() {
        navigated = false
        startedAds = false
        val owner = viewLifecycleOwner
        val host = requireActivity() as MainActivity
        // SDK/consent initialization has its own budget, before the interstitial load wait.
        handler.postDelayed(offlineFallback, 30_000L)
        host.batteryAds.initialize {
            if (navigated || !isAdded || viewLifecycleOwnerLiveData.value !== owner) return@initialize
            owner.lifecycleScope.launch {
                owner.lifecycle.withResumed {
                    if (!navigated && !startedAds) {
                        startedAds = true
                        handler.removeCallbacks(offlineFallback)
                        val returning = requireContext().getSharedPreferences("emoji_battery", 0).getBoolean("intro_complete", false)
                        host.batteryAds.prepare(if (returning) ScreenIds.HOME else ScreenIds.LANGUAGE)
                        host.batteryAds.awaitSplash(this@SplashFragment, returning, ::continueToFirstScreen)
                    }
                }
            }
        }
    }
    override fun initListener() = Unit
    override fun initData() = Unit
    private fun continueToFirstScreen() {
        if (navigated || !isAdded || parentFragmentManager.isStateSaved) return
        navigated = true
        handler.removeCallbacks(offlineFallback)
        val returning = requireContext().getSharedPreferences("emoji_battery", 0).getBoolean("intro_complete", false)
        (requireActivity() as MainActivity).openScreen(if (returning) { if (tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed()) ScreenIds.HOME else ScreenIds.PAYWALL } else ScreenIds.LANGUAGE, false)
    }
    override fun onDestroyView() {
        navigated = true
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}
