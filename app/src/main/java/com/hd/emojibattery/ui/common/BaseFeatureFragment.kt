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

    protected var screen: String = ScreenIds.HOME
        private set
    protected val prefs by lazy { requireContext().getSharedPreferences("emoji_battery", Context.MODE_PRIVATE) }
    protected fun catalog() = AppContentRepository.get(requireContext())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = arguments?.getString("screen_id") ?: ScreenIds.HOME
    }

    override fun initView() {
        val home = screen == ScreenIds.HOME || screen == ScreenIds.THEME_CATEGORY
        val mainTab = home || screen == ScreenIds.CUSTOMIZE || screen == ScreenIds.SETTINGS
        val fullBleedPreview = screen == ScreenIds.CHARGE_PREVIEW || screen == ScreenIds.BATTERY_PREVIEW
        binding.featureScroll.isVisible = !fullBleedPreview
        binding.root.findViewById<View>(R.id.charging_full_screen).isVisible = screen == ScreenIds.CHARGE_PREVIEW
        binding.root.findViewById<View>(R.id.battery_preview_screen).isVisible = screen == ScreenIds.BATTERY_PREVIEW
        binding.root.findViewById<View>(R.id.pet_permission_overlay).isVisible = screen == ScreenIds.PET_STATE_2
        binding.root.findViewById<View>(R.id.pet_flow_modal).isVisible = screen in setOf(ScreenIds.PET_APPLYING, ScreenIds.PET_RATING, ScreenIds.PET_SORT, ScreenIds.PET_EXIT)
        val pet = ScreenRouter.isPet(screen)
        val immersiveEditor = screen == ScreenIds.GESTURE || screen == ScreenIds.GESTURE_STATE_2 || screen == ScreenIds.NOTCH || screen == ScreenIds.NOTCH_EXIT || screen == ScreenIds.BATTERY_TROLL || screen == ScreenIds.BATTERY_TROLL_SETUP || screen == ScreenIds.TROLL_SETUP_2 || screen == ScreenIds.TROLL_PERCENT || screen == ScreenIds.TROLL_EXIT
        binding.featureToolbar.isVisible = !mainTab && !pet && !fullBleedPreview && !immersiveEditor
        binding.featureBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.featureTitle.text = titleFor(screen)
        binding.root.findViewById<View>(R.id.feature_home).isVisible = home
        binding.root.findViewById<View>(R.id.feature_pet).isVisible = ScreenRouter.isPet(screen)
        binding.root.findViewById<View>(R.id.feature_customize).isVisible = ScreenRouter.isCustomize(screen)
        binding.root.findViewById<View>(R.id.feature_stickers).isVisible = ScreenRouter.isSticker(screen) || screen == ScreenIds.SORT
        binding.root.findViewById<View>(R.id.feature_charging).isVisible = ScreenRouter.isCharging(screen)
        binding.root.findViewById<View>(R.id.feature_status_bar).isVisible = ScreenRouter.isStatus(screen)
        binding.root.findViewById<View>(R.id.feature_settings).isVisible = ScreenRouter.isSettings(screen) && screen != ScreenIds.PREMIUM
        bindFeature()
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
        id == ScreenIds.BATTERY_TROLL || id == ScreenIds.BATTERY_TROLL_SETUP || id == ScreenIds.TROLL_SETUP_2 || id == ScreenIds.TROLL_PERCENT || id == ScreenIds.TROLL_EXIT -> "Battery Troll Setup"
        id == ScreenIds.CHARGE_PREVIEW || id == ScreenIds.BATTERY_PREVIEW -> "Battery Preview"
        id == ScreenIds.ANIMATION || id == ScreenIds.ANIMATION_MORE -> "Animation"
        id == ScreenIds.WALLPAPER || id == ScreenIds.WALLPAPER_MORE -> "Wallpaper"
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
}
