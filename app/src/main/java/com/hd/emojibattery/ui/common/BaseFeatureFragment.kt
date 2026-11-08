package com.hd.emojibattery.ui.common

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.R
import com.hd.emojibattery.data.AppContentRepository
import com.hd.emojibattery.databinding.FragmentFeatureBinding
import com.hd.emojibattery.ui.navigation.ScreenIds
import com.hd.emojibattery.ui.navigation.ScreenRouter
import hoang.dqm.codebase.base.activity.BaseFragment
import hoang.dqm.codebase.base.viewmodel.BaseViewModel

abstract class BaseFeatureFragment<VM : BaseViewModel> :
    BaseFragment<FragmentFeatureBinding, VM>() {
    private val featureDialogs = mutableListOf<android.app.Dialog>()
    protected fun trackDialog(dialog: android.app.Dialog) { featureDialogs.add(dialog) }
    override fun onDestroyView() {
        featureDialogs.forEach { it.dismiss() }
        featureDialogs.clear()
        fun release(view: View) {
            (view.background as? android.graphics.drawable.Animatable)?.stop()
            if (view is com.hd.emojibattery.runtime.ChargingMediaView) view.close()
            if (view is android.widget.ImageView) {
                com.hd.emojibattery.data.RemoteImageLoader.clear(view)
                com.hd.emojibattery.runtime.MediaAssets.clear(view)
            }
            view.tag = null
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) release(view.getChildAt(i))
        }
        if (_binding != null) release(binding.root)
        super.onDestroyView()
    }

    protected var screen: String = ScreenIds.HOME
        private set
    protected val prefs by lazy { requireContext().getSharedPreferences(if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) "emoji_battery_preview" else "emoji_battery", Context.MODE_PRIVATE) }
    protected fun catalog() = AppContentRepository.get(requireContext())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = arguments?.getString("screen_id") ?: ScreenIds.HOME
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) {
            val edit = prefs.edit()
            if (screen in setOf(ScreenIds.FAVORITES_STATE_2, ScreenIds.FAVORITES_DELETE) && prefs.all.none { it.key.startsWith("fav_") && it.value == true }) {
                repeat(4) { edit.putBoolean("fav_preview_stickers_$it", true) }
            }
            if (screen in setOf(ScreenIds.ADD_STICKER, ScreenIds.STICKER_EDIT) && prefs.all.none { it.key.startsWith("sticker_selected_") && it.value == true }) {
                edit.putBoolean("sticker_selected_preview_stickers_0", true)
            }
            edit.apply()
        }
    }

    override fun initView() {
        val home = screen == ScreenIds.HOME || screen == ScreenIds.THEME_CATEGORY
        // Inflate only the active feature. Hidden feature trees otherwise create hundreds
        // of unrelated views on every navigation, delaying rendering on slower devices.
        val featureStub = when {
            home -> R.id.stub_home
            ScreenRouter.isPet(screen) -> R.id.stub_pet
            ScreenRouter.isCustomize(screen) -> R.id.stub_customize
            ScreenRouter.isSticker(screen) || screen == ScreenIds.SORT -> R.id.stub_stickers
            ScreenRouter.isCharging(screen) -> R.id.stub_charging
            ScreenRouter.isStatus(screen) -> R.id.stub_status_bar
            else -> R.id.stub_settings
        }
        binding.root.findViewById<android.view.ViewStub>(featureStub)?.inflate()
        val mainTab = home || screen == ScreenIds.CUSTOMIZE || screen == ScreenIds.SETTINGS
        val fullBleedPreview = screen == ScreenIds.CHARGE_PREVIEW || screen == ScreenIds.BATTERY_PREVIEW
        binding.featureScroll.isVisible = !fullBleedPreview && !ScreenRouter.isCharging(screen)
        binding.root.findViewById<View>(R.id.charging_full_screen).isVisible = screen == ScreenIds.CHARGE_PREVIEW
        binding.root.findViewById<View>(R.id.battery_preview_screen).isVisible = screen == ScreenIds.BATTERY_PREVIEW
        binding.root.findViewById<View>(R.id.pet_permission_overlay).isVisible = screen == ScreenIds.PET_STATE_2
        binding.root.findViewById<View>(R.id.pet_flow_modal).isVisible = screen in setOf(ScreenIds.PET_APPLYING, ScreenIds.PET_RATING, ScreenIds.PET_SORT, ScreenIds.PET_EXIT)
        val pet = ScreenRouter.isPet(screen)
        val immersiveEditor = screen == ScreenIds.GESTURE || screen == ScreenIds.GESTURE_STATE_2 || screen == ScreenIds.NOTCH || screen == ScreenIds.NOTCH_EXIT || screen == ScreenIds.BATTERY_TROLL || screen == ScreenIds.BATTERY_TROLL_SETUP || screen == ScreenIds.TROLL_SETUP_2 || screen == ScreenIds.TROLL_PERCENT || screen == ScreenIds.TROLL_EXIT
        binding.featureToolbar.isVisible = !mainTab && !pet && !fullBleedPreview && screen != ScreenIds.GESTURE && screen != ScreenIds.GESTURE_STATE_2 && screen != ScreenIds.GESTURE_ACTION && screen != ScreenIds.GESTURE_ACTION_STATE_2
        if (screen in setOf(ScreenIds.RESTRICTED_APPS, ScreenIds.FEEDBACK)) binding.featureToolbar.isVisible = false
        binding.featureBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.featureTitle.text = titleFor(screen)
        // Temporarily hide premium shortcuts on every feature screen.
        listOf(R.id.home_premium, R.id.customize_premium, R.id.pet_premium, R.id.settings_premium_shortcut).forEach { id ->
            binding.root.findViewById<View>(id)?.isVisible = false
        }
        bindFeature()
        val footerSource = when {
            screen == ScreenIds.HOW_TO_USE -> R.id.help_home
            ScreenRouter.isStatus(screen) && screen !in setOf(ScreenIds.STATUS_MORE_EMOJI, ScreenIds.STATUS_MORE_BATTERY_1, ScreenIds.STATUS_MORE_BATTERY_2, ScreenIds.STATUS_MORE_BATTERY_3, ScreenIds.STATUS_MORE_BATTERY_4) -> R.id.status_apply
            screen in setOf(ScreenIds.ADD_STICKER, ScreenIds.ADD_STICKER_STATE_2, ScreenIds.STICKER_EDIT, ScreenIds.STICKER_DETAIL) -> R.id.sticker_apply
            screen == ScreenIds.NOTCH || screen == ScreenIds.NOTCH_EXIT -> R.id.notch_save
            screen in setOf(ScreenIds.BATTERY_TROLL_SETUP, ScreenIds.TROLL_SETUP_2, ScreenIds.TROLL_PERCENT, ScreenIds.TROLL_EXIT) -> R.id.troll_save
            screen in setOf(ScreenIds.ANIMATION, ScreenIds.WALLPAPER, ScreenIds.CHARGE_SUCCESS) -> R.id.charging_apply
            else -> null
        }
        binding.featureFooter.isVisible = footerSource != null
        val chargingFooter = screen in setOf(ScreenIds.ANIMATION, ScreenIds.WALLPAPER, ScreenIds.CHARGE_SUCCESS)
        binding.featureFooterSecondary.isVisible = chargingFooter
        if (chargingFooter) {
            binding.featureFooter.setBackgroundColor(android.graphics.Color.WHITE)
            binding.featureFooterSecondary.setOnClickListener {
                if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) { navigate(ScreenIds.CHARGE_SUCCESS); return@setOnClickListener }
                if (binding.root.findViewById<View>(R.id.charging_tab_wallpaper).isSelected) com.hd.emojibattery.runtime.FeatureApply.wallpaper(requireActivity())
                else com.hd.emojibattery.runtime.FeatureApply.charging(requireActivity() as MainActivity) { if (isAdded && _binding != null) navigate(ScreenIds.CHARGE_SUCCESS) }
            }
        }
        if (footerSource != null) {
            val source = binding.root.findViewById<TextView>(footerSource)
            source.isVisible = false
            binding.featureFooterAction.text = source.text
            binding.featureFooterAction.setOnClickListener { source.performClick() }
            binding.featureScroll.setPadding(0, 0, 0, ((if (chargingFooter) 144 else 80) * resources.displayMetrics.density).toInt())
        }
    }

    override fun initData() = Unit
    override fun initListener() = Unit
    protected open fun bindFeature() = Unit

    fun getScreenId(): String = screen
    fun refreshContent() { if (_binding != null && isAdded) bindFeature() }

    protected fun navigate(target: String, args: Bundle? = null) {
        (requireActivity() as MainActivity).openScreen(target, true, args)
    }

    protected fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    protected fun text(id: Int) = binding.root.findViewById<TextView>(id)
    protected fun <T : View> view(id: Int): T = binding.root.findViewById(id)

    private fun titleFor(id: String): String = when {
        id == ScreenIds.PET || id == ScreenIds.PET_STATE_2 || id == ScreenIds.PET_STATE_3 -> "Pet on Screen"
        id == ScreenIds.PET_MORE || id == ScreenIds.PET_MORE_SELECTED -> "Choose Your Pet"
        id == ScreenIds.CUSTOMIZE -> "Customize"
        id == ScreenIds.GESTURE || id == ScreenIds.GESTURE_STATE_2 -> "Gesture"
        id == ScreenIds.GESTURE_ACTION || id == ScreenIds.GESTURE_ACTION_STATE_2 -> "Select action"
        id == ScreenIds.NOTCH || id == ScreenIds.NOTCH_EXIT -> "Notch Setup"
        id == ScreenIds.BATTERY_TROLL -> "Battery Troll"
        id == ScreenIds.BATTERY_TROLL_SETUP || id == ScreenIds.TROLL_SETUP_2 || id == ScreenIds.TROLL_PERCENT || id == ScreenIds.TROLL_EXIT -> "Battery Troll Setup"
        id == ScreenIds.CHARGE_PREVIEW || id == ScreenIds.BATTERY_PREVIEW -> "Battery Preview"
        id == ScreenIds.ANIMATION || id == ScreenIds.WALLPAPER || id == ScreenIds.CHARGE_SUCCESS -> "Charging Battery"
        id == ScreenIds.ANIMATION_MORE -> "Animation"
        id == ScreenIds.WALLPAPER || id == ScreenIds.WALLPAPER_MORE -> "Wallpaper"
        id == ScreenIds.DATE_SETTINGS || id == ScreenIds.STATUS_SCROLL_5 -> "Date Settings"
        id == ScreenIds.STATUS_MORE_EMOJI -> "Emoji Icon"
        id == ScreenIds.STATUS_MORE_BATTERY_1 -> "Battery"
        id == ScreenIds.STATUS_MORE_BATTERY_2 -> "Emotion"
        id == ScreenIds.STATUS_MORE_BATTERY_3 -> "Animation"
        id == ScreenIds.STATUS_MORE_BATTERY_4 -> "Background"
        ScreenRouter.isStatus(id) -> "Custom Status Bar"
        id == ScreenIds.SEARCH -> "Search"
        id == ScreenIds.FAVORITES || id == ScreenIds.FAVORITES_STATE_2 || id == ScreenIds.FAVORITES_DELETE -> "My library"
        id == ScreenIds.RESTRICTED_APPS -> "Restricted Apps"
        id == ScreenIds.FEEDBACK -> "Feedback"
        id == ScreenIds.HOW_TO_USE -> "How to use"
        id == ScreenIds.LANGUAGE -> "Language"
        id == ScreenIds.STICKER_MORE || id == ScreenIds.STICKER_EXIT || id == ScreenIds.ADD_STICKER || id == ScreenIds.ADD_STICKER_STATE_2 || id == ScreenIds.STICKER_EDIT -> "Emoji Sticker"
        id == ScreenIds.PET_APPLYING -> "Applying"
        id == ScreenIds.PET_RATING -> "Rate this pet"
        id == ScreenIds.PET_SORT || id == ScreenIds.SORT -> "Sort"
        id == ScreenIds.CHARGE_SUCCESS || id == ScreenIds.APPLY_RESULT -> "Applied"
        id == ScreenIds.THEME_CATEGORY -> "Themes"
        id == ScreenIds.PET_DETAIL -> "Pet details"
        id == ScreenIds.STICKER_DETAIL -> "Sticker"
        else -> "Emoji Battery"
    }
    companion object {
        private const val ACCESSIBILITY_USAGE_ACCEPTED =
            "accessibility_usage_accepted"
    }

    protected fun isAccessibilityUsageAccepted(): Boolean {
        return prefs.getBoolean(
            ACCESSIBILITY_USAGE_ACCEPTED,
            false
        )
    }

    protected fun requireAccessibilityUsageAccepted(
        onAccepted: () -> Unit
    ) {
        if (!isAdded || _binding == null) return

        val activity = requireActivity() as MainActivity

        if (activity.hasAcceptedAccessibilityUsage()) {
            onAccepted()
            return
        }

        activity.requestEmojiEnable {
            if (isAdded && _binding != null) {
                onAccepted()
            }
        }
    }
}
