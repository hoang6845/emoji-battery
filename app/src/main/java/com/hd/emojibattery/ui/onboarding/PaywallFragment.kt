package com.hd.emojibattery.ui.onboarding

import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.R
import com.hd.emojibattery.databinding.FragmentPaywallBinding
import com.hd.emojibattery.ui.navigation.ScreenIds
import hoang.dqm.codebase.base.activity.BaseFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tpt.dev.monetization.common.premium.PremiumManager
import tpt.dev.monetization.subs.manager.BillingManager
import tpt.dev.monetization.subs.model.IAPProduct
import tpt.dev.monetization.subs.extensions.biggestSubscriptionOfferDetailsToken
import java.text.NumberFormat
import java.util.Currency
import java.math.BigDecimal
import java.math.RoundingMode

class PaywallFragment : BaseFragment<FragmentPaywallBinding, PaywallViewModel>() {
    private val billing get() = BillingManager.getInstance()
    private var closeAt = 0L
    private var leaving = false
    private var pulse: AnimatorSet? = null
    private var entrance: AnimatorSet? = null
    private var purchasing = false
    private val billingListener = object : tpt.dev.monetization.subs.listener.BillingClientListener {
        override fun onConnected(isConnected: Boolean, responseCode: Int) = Unit
        override fun onQueryProductDetailComplete(products: List<IAPProduct>) = Unit
        override fun onLaunchPurchaseComplete(isSuccess: Boolean) {
            purchasing = false
            if (view == null) return
            updateSelection()
            if (!isSuccess) Toast.makeText(requireContext(), R.string.paywall_billing_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    override fun initView() {
        leaving = false
        closeAt = SystemClock.elapsedRealtime() + 3000L
        binding.buttonClose.visibility = View.INVISIBLE
        updateSelection()
        binding.priceWeekly.text = "…"
        binding.priceYearly.text = "…"
        animateEntrance()
        viewLifecycleOwner.lifecycleScope.launch {
            delay(3000)
            binding.buttonClose.isVisible = true
            binding.buttonClose.alpha = 0f
            binding.buttonClose.animate().alpha(1f).setDuration(250).start()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { billing.iapProductsFlow.collect { renderPrices(it) } }
                launch { PremiumManager.INSTANCE.getSubscribedStateFlow().collect { if (it) leave() } }
            }
        }
    }
    private fun animateEntrance() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val density = resources.displayMetrics.density
        val animations = mutableListOf<android.animation.Animator>()
        fun reveal(target: View, delay: Long, distance: Float, fade: Boolean = true) {
            if (fade) {
                target.alpha = 0f
                animations += ObjectAnimator.ofFloat(target, View.ALPHA, 0f, 1f).apply {
                    startDelay = delay
                    duration = 440L
                }
            }
            target.translationY = distance * density
            animations += ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, distance * density, 0f).apply {
                startDelay = delay
                duration = 580L
            }
        }
        reveal(binding.paywallArtwork, 0L, 0f)
        reveal(binding.paywallHeading, 60L, 16f)
        reveal(binding.paywallBenefits, 120L, 20f)
        reveal(binding.paywallPlans, 200L, 24f)
        // Keep CTA alpha owned by Billing readiness; entrance animates its position.
        reveal(binding.buttonContinue, 260L, 28f, fade = false)
        entrance = AnimatorSet().apply {
            playTogether(animations)
            interpolator = android.view.animation.PathInterpolator(0.16f, 1f, 0.3f, 1f)
            start()
        }
    }

    override fun initListener() {
        billing.addBillingClientListener(billingListener)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (SystemClock.elapsedRealtime() >= closeAt) leave() }
        })
        binding.buttonClose.setOnClickListener { if (SystemClock.elapsedRealtime() >= closeAt) leave() }
        binding.rowWeekly.setOnClickListener { viewModel.selectPlan(0); updateSelection() }
        binding.rowYearly.setOnClickListener { viewModel.selectPlan(1); updateSelection() }
        binding.buttonContinue.setOnClickListener {
            val product = selectedProduct() ?: return@setOnClickListener
            if (purchasing) return@setOnClickListener
            purchasing = true
            binding.buttonContinue.isEnabled = false
            billing.buyBasePlan(requireActivity(), product)
        }
        binding.buttonRestore.setOnClickListener {
            billing.restorePurchases()
            Toast.makeText(requireContext(), "Checking Google Play subscriptions…", Toast.LENGTH_SHORT).show()
        }
        binding.buttonPrivacy.setOnClickListener { openLink("privacy_policy") }
        binding.buttonTerms.setOnClickListener { openLink("terms") }
    }
    private fun openLink(key: String) {
        val url = if (key == "privacy_policy") "https://sites.google.com/view/emoji-battery-icon-themes/privacy-policy"
        else com.hd.emojibattery.data.AppContentRepository.get(requireContext()).snapshot().optJSONObject("links")?.optString(key).orEmpty()
        if (url.isBlank()) { Toast.makeText(requireContext(), "Link unavailable", Toast.LENGTH_SHORT).show(); return }
        runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    }
    private fun selectedProduct() = billing.getPricedProducts().firstOrNull {
        it.productId == if (viewModel.selectedPlan.value == 0) "weekly_premium" else "yearly_premium"
    }?.takeIf { it.productDetails != null }
    private fun renderPrices(products: List<IAPProduct>) {
        products.forEach { product ->
            val phase = product.productDetails?.biggestSubscriptionOfferDetailsToken()?.pricingPhases?.pricingPhaseList?.lastOrNull() ?: return@forEach
            when (product.productId) {
                "weekly_premium" -> binding.priceWeekly.text = "${phase.formattedPrice}/week"
                "yearly_premium" -> {
                    val formatter = NumberFormat.getCurrencyInstance().apply {
                        currency = Currency.getInstance(phase.priceCurrencyCode)
                        minimumFractionDigits = 0
                        maximumFractionDigits = 0
                        roundingMode = RoundingMode.DOWN
                    }
                    val weekly = BigDecimal.valueOf(phase.priceAmountMicros).divide(BigDecimal(52000000), 6, RoundingMode.HALF_UP)
                    binding.priceYearly.text = "${formatter.format(weekly)}/week"
                    binding.priceYearlyTotal.text = "${phase.formattedPrice}/year"
                }
            }
        }
        updateSelection()
    }
    private fun leave() {
        if (leaving || !isAdded || parentFragmentManager.isStateSaved) return
        if (!com.hd.emojibattery.ui.navigation.ScreenRouter.canNavigate(requireActivity()) ||
            (requireActivity() as MainActivity).batteryAds.busy) return
        leaving = true
        if (arguments?.getString("screen_id") == ScreenIds.PREMIUM && parentFragmentManager.backStackEntryCount > 0) com.hd.emojibattery.ui.navigation.ScreenRouter.pop(requireActivity(), this)
        else (requireActivity() as MainActivity).openScreen(ScreenIds.HOME, false)
    }
    override fun initData() = Unit
    private fun updateSelection() {
        val weekly = viewModel.selectedPlan.value == 0
        binding.rowWeekly.setBackgroundResource(if (weekly) R.drawable.bg_plan_selected else R.drawable.bg_plan)
        binding.rowYearly.setBackgroundResource(if (weekly) R.drawable.bg_plan else R.drawable.bg_plan_selected)
        binding.radioWeekly.isChecked = weekly
        binding.radioYearly.isChecked = !weekly
        val ready = selectedProduct() != null && !purchasing
        binding.buttonContinue.isEnabled = ready
        binding.buttonContinue.alpha = if (ready) 1f else .6f
        binding.textLegal.text = "Payment will be charged to your Google Play account. Your subscription renews automatically ${if (weekly) "every week" else "every year"} unless canceled. Manage or cancel in Google Play subscription settings."
    }
    override fun onResume() {
        super.onResume()
        purchasing = false
        updateSelection()
        if (ValueAnimator.areAnimatorsEnabled()) {
            pulse = AnimatorSet().apply {
                playTogether(*arrayOf("scaleX", "scaleY").map { property ->
                    ObjectAnimator.ofFloat(binding.buttonContinue, property, 1f, 1.025f, 1f).apply {
                        duration = 2000; repeatCount = ValueAnimator.INFINITE
                    }
                }.toTypedArray())
                start()
            }
        }
    }
    override fun onPause() {
        entrance?.end(); entrance = null
        pulse?.cancel(); pulse = null
        binding.buttonContinue.scaleX = 1f; binding.buttonContinue.scaleY = 1f
        super.onPause()
    }
    override fun onDestroyView() {
        entrance?.end(); entrance = null
        billing.removeBillingClientListener(billingListener)
        super.onDestroyView()
    }
}
