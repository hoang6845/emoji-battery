package com.hd.emojibattery.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.hd.emojibattery.BuildConfig
import com.hd.emojibattery.ui.navigation.ScreenIds

/** Called when an app screen becomes resumed, including back navigation and restoration. */
object ScreenAnalytics {
    fun track(context: Context, route: String, screenClass: String) {
        if (BuildConfig.UI_TEST_MODE) return
        FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, nameFor(route))
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        })
    }

    private fun nameFor(route: String): String = when (route) {
        ScreenIds.HOME -> "home"
        ScreenIds.PET -> "pet"
        ScreenIds.PET_STATE_2 -> "pet_state_2"
        ScreenIds.PET_STATE_3 -> "pet_state_3"
        ScreenIds.GESTURE -> "gesture"
        ScreenIds.GESTURE_STATE_2 -> "gesture_state_2"
        ScreenIds.NOTCH -> "notch"
        ScreenIds.NOTCH_EXIT -> "notch_exit"
        ScreenIds.PET_MORE -> "pet_more"
        ScreenIds.PET_MORE_SELECTED -> "pet_more_selected"
        ScreenIds.PET_APPLYING -> "pet_applying"
        ScreenIds.PET_RATING -> "pet_rating"
        ScreenIds.PET_SORT -> "pet_sort"
        ScreenIds.PET_EXIT -> "pet_exit"
        ScreenIds.GESTURE_ACTION -> "gesture_action"
        ScreenIds.GESTURE_ACTION_STATE_2 -> "gesture_action_state_2"
        ScreenIds.WALLPAPER_MORE -> "wallpaper_more"
        ScreenIds.ANIMATION_MORE -> "animation_more"
        ScreenIds.ANIMATION -> "animation"
        ScreenIds.WALLPAPER -> "wallpaper"
        ScreenIds.CHARGE_SUCCESS -> "charge_success"
        ScreenIds.ADD_STICKER -> "add_sticker"
        ScreenIds.ADD_STICKER_STATE_2 -> "add_sticker_state_2"
        ScreenIds.STICKER_EDIT -> "sticker_edit"
        ScreenIds.FAVORITES -> "favorites"
        ScreenIds.SEARCH -> "search"
        ScreenIds.FAVORITES_STATE_2 -> "favorites_state_2"
        ScreenIds.HOW_TO_USE -> "how_to_use"
        ScreenIds.FAVORITES_DELETE -> "favorites_delete"
        ScreenIds.SETTINGS -> "settings"
        ScreenIds.RESTRICTED_APPS -> "restricted_apps"
        ScreenIds.FEEDBACK -> "feedback"
        ScreenIds.CUSTOMIZE -> "customize"
        ScreenIds.CHARGE_PREVIEW -> "charge_preview"
        ScreenIds.BATTERY_TROLL -> "battery_troll"
        ScreenIds.BATTERY_TROLL_SETUP -> "battery_troll_setup"
        ScreenIds.STATUS_BAR -> "status_bar"
        ScreenIds.STATUS_SCROLL_1 -> "status_scroll_1"
        ScreenIds.STATUS_SCROLL_2 -> "status_scroll_2"
        ScreenIds.STATUS_SCROLL_3 -> "status_scroll_3"
        ScreenIds.STATUS_SCROLL_4 -> "status_scroll_4"
        ScreenIds.STATUS_SCROLL_5 -> "status_scroll_5"
        ScreenIds.STATUS_SCROLL_6 -> "status_scroll_6"
        ScreenIds.STATUS_CONTENT_1 -> "status_content_1"
        ScreenIds.STATUS_CONTENT_2 -> "status_content_2"
        ScreenIds.STATUS_CONTENT_3 -> "status_content_3"
        ScreenIds.STATUS_CONTENT_4 -> "status_content_4"
        ScreenIds.TROLL_SETUP_2 -> "troll_setup_2"
        ScreenIds.TROLL_PERCENT -> "troll_percent"
        ScreenIds.TROLL_EXIT -> "troll_exit"
        ScreenIds.STICKER_MORE -> "sticker_more"
        ScreenIds.BATTERY_PREVIEW -> "battery_preview"
        ScreenIds.BATTERY_STYLE_PREVIEW -> "battery_style_preview"
        ScreenIds.STICKER_EXIT -> "sticker_exit"
        ScreenIds.STATUS_MORE_EMOJI -> "status_more_emoji"
        ScreenIds.STATUS_MORE_BATTERY_1 -> "status_more_battery_1"
        ScreenIds.STATUS_MORE_BATTERY_2 -> "status_more_battery_2"
        ScreenIds.STATUS_MORE_BATTERY_3 -> "status_more_battery_3"
        ScreenIds.STATUS_MORE_BATTERY_4 -> "status_more_battery_4"
        ScreenIds.WELCOME_BACK -> "welcome_back"
        ScreenIds.SPLASH -> "splash"
        ScreenIds.SPLASH_LOADING -> "splash_loading"
        ScreenIds.SPLASH_READY -> "splash_ready"
        ScreenIds.PAYWALL -> "paywall"
        ScreenIds.INTRO_3 -> "intro_3"
        ScreenIds.INTRO_1 -> "intro_1"
        ScreenIds.INTRO_2 -> "intro_2"
        ScreenIds.INTRO_5 -> "intro_5"
        ScreenIds.LANGUAGE -> "language"
        ScreenIds.TEXT_NODE -> "text_node"
        ScreenIds.THEME_CATEGORY -> "theme_category"
        ScreenIds.STICKER_DETAIL -> "sticker_detail"
        ScreenIds.PET_DETAIL -> "pet_detail"
        ScreenIds.PREMIUM -> "premium"
        ScreenIds.SORT -> "sort"
        ScreenIds.DATE_SETTINGS -> "date_settings"
        ScreenIds.APPLY_RESULT -> "apply_result"
        ScreenIds.FEEDBACK_SENT -> "feedback_sent"
        "image_crop" -> "image_crop"
        else -> "unknown_screen"
    }
}
