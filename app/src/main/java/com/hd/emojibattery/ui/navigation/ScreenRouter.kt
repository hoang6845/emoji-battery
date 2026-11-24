package com.hd.emojibattery.ui.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.onboarding.PaywallFragment
import com.hd.emojibattery.ui.onboarding.WelcomeBackFragment
import com.hd.emojibattery.ui.routes.*

object ScreenRouter {
    fun fragmentFor(screen: String): Class<out Fragment> = when (screen) {
        ScreenIds.SPLASH -> SplashInitialFragment::class.java
        ScreenIds.SPLASH_LOADING -> SplashLoadingFragment::class.java
        ScreenIds.SPLASH_READY -> SplashReadyFragment::class.java
        ScreenIds.WELCOME_BACK -> WelcomeBackFragment::class.java
        ScreenIds.INTRO_1 -> IntroOneFragment::class.java
        ScreenIds.INTRO_2 -> IntroTwoFragment::class.java
        ScreenIds.INTRO_3 -> IntroThreeFragment::class.java
        ScreenIds.INTRO_5 -> IntroFinalFragment::class.java
        ScreenIds.PAYWALL, ScreenIds.PREMIUM -> PaywallFragment::class.java
        ScreenIds.HOME, ScreenIds.THEME_CATEGORY -> HomeFrameFragment::class.java
        ScreenIds.PET -> PetHomeFragment::class.java
        ScreenIds.PET_STATE_2 -> PetSelectedFragment::class.java
        ScreenIds.PET_STATE_3 -> PetPositionFragment::class.java
        ScreenIds.GESTURE -> GestureSetupFragment::class.java
        ScreenIds.GESTURE_STATE_2 -> GestureSetupStateFragment::class.java
        ScreenIds.NOTCH -> NotchSetupFragment::class.java
        ScreenIds.NOTCH_EXIT -> NotchExitFragment::class.java
        ScreenIds.PET_MORE, ScreenIds.PET_DETAIL -> PetGalleryFragment::class.java
        ScreenIds.PET_MORE_SELECTED -> PetGallerySelectedFragment::class.java
        ScreenIds.PET_APPLYING -> PetApplyingFragment::class.java
        ScreenIds.PET_RATING -> PetRatingFragment::class.java
        ScreenIds.PET_SORT -> PetSortFragment::class.java
        ScreenIds.SORT -> StickerSortFragment::class.java
        ScreenIds.PET_EXIT -> PetExitFragment::class.java
        ScreenIds.GESTURE_ACTION -> GestureActionFragment::class.java
        ScreenIds.GESTURE_ACTION_STATE_2 -> GestureActionSelectedFragment::class.java
        ScreenIds.WALLPAPER_MORE -> WallpaperGalleryFragment::class.java
        ScreenIds.ANIMATION_MORE -> AnimationGalleryFragment::class.java
        ScreenIds.ANIMATION -> AnimationSelectFragment::class.java
        ScreenIds.WALLPAPER -> WallpaperSelectFragment::class.java
        ScreenIds.CHARGE_SUCCESS, ScreenIds.APPLY_RESULT -> ChargingApplyFragment::class.java
        ScreenIds.ADD_STICKER -> StickerAddFragment::class.java
        ScreenIds.ADD_STICKER_STATE_2 -> StickerCategoryFragment::class.java
        ScreenIds.STICKER_EDIT -> StickerEditFragment::class.java
        ScreenIds.STICKER_DETAIL -> StickerDetailFragment::class.java
        ScreenIds.FAVORITES -> FavoritesFragment::class.java
        ScreenIds.SEARCH -> StickerSearchFragment::class.java
        ScreenIds.FAVORITES_STATE_2 -> FavoritesSelectedFragment::class.java
        ScreenIds.HOW_TO_USE -> HelpFragment::class.java
        ScreenIds.FAVORITES_DELETE -> FavoritesDeleteFragment::class.java
        ScreenIds.SETTINGS -> SettingsHomeFragment::class.java
        ScreenIds.RESTRICTED_APPS -> RestrictedAppsFragment::class.java
        ScreenIds.FEEDBACK, ScreenIds.FEEDBACK_SENT -> FeedbackFragment::class.java
        ScreenIds.CUSTOMIZE -> CustomizeHomeFragment::class.java
        ScreenIds.CHARGE_PREVIEW -> ChargingPreviewFragment::class.java
        ScreenIds.BATTERY_TROLL -> BatteryTrollFragment::class.java
        ScreenIds.BATTERY_TROLL_SETUP -> BatteryTrollSetupFragment::class.java
        ScreenIds.STATUS_BAR -> StatusBarHomeFragment::class.java
        ScreenIds.STATUS_SCROLL_1 -> StatusBarScroll1Fragment::class.java
        ScreenIds.STATUS_SCROLL_2 -> StatusBarScroll2Fragment::class.java
        ScreenIds.STATUS_SCROLL_3 -> StatusBarScroll3Fragment::class.java
        ScreenIds.STATUS_SCROLL_4 -> StatusBarScroll4Fragment::class.java
        ScreenIds.STATUS_SCROLL_5 -> StatusBarScroll5Fragment::class.java
        ScreenIds.STATUS_SCROLL_6 -> StatusBarScroll6Fragment::class.java
        ScreenIds.STATUS_CONTENT_1 -> StatusBarContent1Fragment::class.java
        ScreenIds.STATUS_CONTENT_2 -> StatusBarContent2Fragment::class.java
        ScreenIds.STATUS_CONTENT_3 -> StatusBarContent3Fragment::class.java
        ScreenIds.STATUS_CONTENT_4 -> StatusBarContent4Fragment::class.java
        ScreenIds.TROLL_SETUP_2 -> BatteryTrollSetupCompleteFragment::class.java
        ScreenIds.TROLL_PERCENT -> BatteryTrollPercentFragment::class.java
        ScreenIds.TROLL_EXIT -> BatteryTrollExitFragment::class.java
        ScreenIds.STICKER_MORE -> StickerMoreFragment::class.java
        ScreenIds.BATTERY_PREVIEW -> BatteryPreviewFragment::class.java
        ScreenIds.BATTERY_STYLE_PREVIEW -> com.hd.emojibattery.ui.home.BatteryStylePreviewFragment::class.java
        ScreenIds.STICKER_EXIT -> StickerExitFragment::class.java
        ScreenIds.STATUS_MORE_EMOJI -> StatusBarEmojiFragment::class.java
        ScreenIds.STATUS_MORE_BATTERY_1 -> StatusBarBatteryFragment::class.java
        ScreenIds.STATUS_MORE_BATTERY_2 -> StatusBarEmotionFragment::class.java
        ScreenIds.STATUS_MORE_BATTERY_3 -> StatusBarAnimationFragment::class.java
        ScreenIds.STATUS_MORE_BATTERY_4 -> StatusBarBackgroundFragment::class.java
        ScreenIds.LANGUAGE -> com.hd.emojibattery.ui.onboarding.LanguagePickerFragment::class.java
        ScreenIds.DATE_SETTINGS -> StatusBarHomeFragment::class.java
        else -> HomeFrameFragment::class.java
    }

    @JvmStatic fun open(activity: FragmentActivity, container: Int, screen: String, backStack: Boolean) = open(activity, container, screen, null, backStack)
    @JvmStatic fun open(activity: FragmentActivity, container: Int, screen: String, extras: Bundle?, backStack: Boolean) {
        val current = (activity.supportFragmentManager.findFragmentById(container) as? com.hd.emojibattery.ui.common.BaseFeatureFragment<*>)?.getScreenId()
        if (isStatus(screen) && (current == null || !isStatus(current))) {
            androidx.lifecycle.ViewModelProvider(activity)[com.hd.emojibattery.ui.statusbar.StatusEditorViewModel::class.java].reset()
        }
        val currentFragment = activity.supportFragmentManager.findFragmentById(container)
        val stickerReturnIndex = if (current != null && isStatus(current) && isSticker(screen)) {
            activity.supportFragmentManager.backStackEntryCount
        } else if (isSticker(screen)) {
            currentFragment?.arguments?.getInt("sticker_status_return_index", -1) ?: -1
        } else -1
        val fragment = fragmentFor(screen).getDeclaredConstructor().newInstance()
        fragment.arguments = Bundle().apply {
            putString("screen_id", screen)
            extras?.let { putAll(it) }
            if (stickerReturnIndex >= 0) putInt("sticker_status_return_index", stickerReturnIndex)
        }
        activity.supportFragmentManager.beginTransaction().setReorderingAllowed(true).apply {
            val introPages = listOf(ScreenIds.INTRO_1, ScreenIds.INTRO_2, ScreenIds.INTRO_3, ScreenIds.INTRO_5)
            val currentRoute = activity.supportFragmentManager.findFragmentById(container)?.arguments?.getString("screen_id")
            val introFrom = introPages.indexOf(currentRoute)
            val introTo = introPages.indexOf(screen)
            val tabs = listOf(ScreenIds.HOME, ScreenIds.PET, ScreenIds.CUSTOMIZE, ScreenIds.SETTINGS)
            val from = tabs.indexOf(current)
            val to = tabs.indexOf(screen)
            if (introFrom >= 0 && introTo >= 0 && introFrom != introTo) {
                if (introTo > introFrom) {
                    setCustomAnimations(R.anim.screen_enter_right, R.anim.screen_exit_left,
                        R.anim.screen_enter_left, R.anim.screen_exit_right)
                } else {
                    setCustomAnimations(R.anim.screen_enter_left, R.anim.screen_exit_right,
                        R.anim.screen_enter_right, R.anim.screen_exit_left)
                }
            } else if (from >= 0 && to >= 0 && from != to) {
                if (to > from) {
                    setCustomAnimations(R.anim.screen_enter_right, R.anim.screen_exit_left,
                        R.anim.screen_enter_left, R.anim.screen_exit_right)
                } else {
                    setCustomAnimations(R.anim.screen_enter_left, R.anim.screen_exit_right,
                        R.anim.screen_enter_right, R.anim.screen_exit_left)
                }
            } else if (current != null && (screen == ScreenIds.SEARCH || stickerReturnIndex >= 0)) {
                setCustomAnimations(R.anim.screen_enter_right, R.anim.screen_exit_left,
                    R.anim.screen_enter_left, R.anim.screen_exit_right)
            }
            replace(container, fragment, screen)
            if (backStack) addToBackStack(screen)
        }.commit()
    }

    @JvmStatic fun isMainTab(id: String) = id == ScreenIds.HOME || id == ScreenIds.PET || id == ScreenIds.CUSTOMIZE || id == ScreenIds.SETTINGS
    @JvmStatic fun isPet(id: String) = id in setOf(ScreenIds.PET, ScreenIds.PET_STATE_2, ScreenIds.PET_STATE_3, ScreenIds.PET_MORE, ScreenIds.PET_MORE_SELECTED, ScreenIds.PET_APPLYING, ScreenIds.PET_RATING, ScreenIds.PET_SORT, ScreenIds.PET_EXIT, ScreenIds.PET_DETAIL)
    @JvmStatic fun isSticker(id: String) = id in setOf(ScreenIds.SEARCH, ScreenIds.FAVORITES, ScreenIds.FAVORITES_STATE_2, ScreenIds.FAVORITES_DELETE, ScreenIds.ADD_STICKER, ScreenIds.ADD_STICKER_STATE_2, ScreenIds.STICKER_EDIT, ScreenIds.STICKER_MORE, ScreenIds.STICKER_EXIT, ScreenIds.STICKER_DETAIL, ScreenIds.SORT)
    @JvmStatic fun isCharging(id: String) = id in setOf(ScreenIds.CHARGE_PREVIEW, ScreenIds.BATTERY_PREVIEW, ScreenIds.WALLPAPER, ScreenIds.WALLPAPER_MORE, ScreenIds.ANIMATION, ScreenIds.ANIMATION_MORE, ScreenIds.CHARGE_SUCCESS, ScreenIds.APPLY_RESULT)
    @JvmStatic fun isStatus(id: String) = id in setOf(ScreenIds.STATUS_BAR, ScreenIds.STATUS_SCROLL_1, ScreenIds.STATUS_SCROLL_2, ScreenIds.STATUS_SCROLL_3, ScreenIds.STATUS_SCROLL_4, ScreenIds.STATUS_SCROLL_5, ScreenIds.STATUS_SCROLL_6, ScreenIds.STATUS_CONTENT_1, ScreenIds.STATUS_CONTENT_2, ScreenIds.STATUS_CONTENT_3, ScreenIds.STATUS_CONTENT_4, ScreenIds.STATUS_MORE_EMOJI, ScreenIds.STATUS_MORE_BATTERY_1, ScreenIds.STATUS_MORE_BATTERY_2, ScreenIds.STATUS_MORE_BATTERY_3, ScreenIds.STATUS_MORE_BATTERY_4, ScreenIds.DATE_SETTINGS)
    @JvmStatic fun isCustomize(id: String) = id in setOf(ScreenIds.CUSTOMIZE, ScreenIds.GESTURE, ScreenIds.GESTURE_STATE_2, ScreenIds.GESTURE_ACTION, ScreenIds.GESTURE_ACTION_STATE_2, ScreenIds.NOTCH, ScreenIds.NOTCH_EXIT, ScreenIds.BATTERY_TROLL, ScreenIds.BATTERY_TROLL_SETUP, ScreenIds.TROLL_SETUP_2, ScreenIds.TROLL_PERCENT, ScreenIds.TROLL_EXIT)
    @JvmStatic fun isSettings(id: String) = id in setOf(ScreenIds.SETTINGS, ScreenIds.RESTRICTED_APPS, ScreenIds.FEEDBACK, ScreenIds.FEEDBACK_SENT, ScreenIds.HOW_TO_USE, ScreenIds.LANGUAGE, ScreenIds.PREMIUM)
}
