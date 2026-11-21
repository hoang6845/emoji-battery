package com.hd.emojibattery.ads

import com.hd.emojibattery.ui.navigation.ScreenIds as S
import com.hd.emojibattery.ui.navigation.ScreenRouter
import tpt.dev.monetization.ads.constants.AbstractAdsConstants

object BatteryAdPlacements : AbstractAdsConstants() {
    override val ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    override val ADMOB_INTERSTITIAL_ID = "emoji_inter_in_app"
    override val ADMOB_SPLASH_ID = "emoji_inter_splash"
    override val ADMOB_APP_OPEN_ID = "emoji_open"
    override val REWARD_ID = "emoji_reward"

    fun native(screen: String): String? = when {
        screen == S.LANGUAGE -> "emoji_native_language"
        screen == S.INTRO_1 -> "emoji_native_intro1"
        screen == S.INTRO_2 -> "emoji_native_intro2"
        screen == S.INTRO_3 -> "emoji_native_intro3"
        screen == S.INTRO_5 -> "emoji_full_native"
        screen == S.PREMIUM || screen == S.PAYWALL || screen.startsWith("splash") || screen == S.WELCOME_BACK -> null
        screen == S.HOME || screen == S.THEME_CATEGORY -> "emoji_native_collab_home"
        screen == S.SETTINGS -> "emoji_native_all"
        screen == S.NOTCH || screen == S.NOTCH_EXIT -> "emoji_native_collab_notch"
        screen == S.BATTERY_TROLL || screen == S.BATTERY_TROLL_SETUP || screen == S.TROLL_SETUP_2 || screen == S.TROLL_PERCENT -> "emoji_native_collab_battery_troll"
        screen == S.BATTERY_STYLE_PREVIEW || screen == S.BATTERY_PREVIEW || screen == S.CHARGE_PREVIEW -> "emoji_native_preview"
        ScreenRouter.isPet(screen) -> "emoji_native_collab_pet_on_screen"
        ScreenRouter.isSticker(screen) -> "emoji_native_collab_emoji_sticker"
        ScreenRouter.isStatus(screen) -> "emoji_native_list_data_custom"
        screen == S.CUSTOMIZE -> "emoji_native_collab_custom"
        else -> null
    }

    fun banner(screen: String): String? = when (screen) {
        S.SEARCH, S.FAVORITES -> "emoji_banner_all"
        S.HOW_TO_USE -> "emoji_banner_guide"
        S.PET_MORE, S.PET_DETAIL -> "emoji_banner_pet_on_screen"
        S.DATE_SETTINGS -> "emoji_banner_custom"
        else -> null
    }

    fun nextNativeScreens(screen: String): List<String> = when (screen) {
        S.LANGUAGE -> listOf(S.INTRO_1, S.INTRO_2)
        S.INTRO_1 -> listOf(S.INTRO_2, S.INTRO_3)
        S.INTRO_2 -> listOf(S.INTRO_3, S.INTRO_5)
        S.INTRO_3, S.INTRO_5 -> listOf(S.HOME)
        S.HOME -> listOf(S.PET, S.CUSTOMIZE, S.SETTINGS, S.BATTERY_STYLE_PREVIEW)
        S.CUSTOMIZE -> listOf(S.STATUS_BAR, S.NOTCH, S.BATTERY_TROLL, S.ADD_STICKER)
        else -> emptyList()
    }

    fun transitionNeedsInter(target: String): Boolean = target in setOf(
        S.SEARCH, S.FAVORITES, S.STATUS_BAR, S.NOTCH, S.BATTERY_TROLL,
        S.ADD_STICKER, S.PET_MORE, S.BATTERY_STYLE_PREVIEW,
        S.ANIMATION, S.WALLPAPER, S.STATUS_MORE_EMOJI, S.STATUS_MORE_BATTERY_1,
        S.STATUS_MORE_BATTERY_2, S.STATUS_MORE_BATTERY_3, S.STATUS_MORE_BATTERY_4
    )
}
