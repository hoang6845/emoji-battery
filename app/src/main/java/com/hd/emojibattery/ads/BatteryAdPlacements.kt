package com.hd.emojibattery.ads

import com.hd.emojibattery.ui.navigation.ScreenIds as S
import com.hd.emojibattery.ui.navigation.ScreenRouter
import tpt.dev.monetization.ads.constants.AbstractAdsConstants

object BatteryAdPlacements : AbstractAdsConstants() {
    override val ADMOB_APP_ID = "ca-app-pub-5698123608094343~8940119691"
    override val ADMOB_INTERSTITIAL_ID = "emoji_inter_in_app"
    override val ADMOB_SPLASH_ID = "emoji_inter_splash"
    override val ADMOB_APP_OPEN_ID = "emoji_open"
    override val REWARD_ID = "emoji_reward"

    fun native(screen: String): String? = when {
        screen == S.LANGUAGE -> "emoji_native_language"
        screen == S.INTRO_1 -> null
        screen == S.INTRO_2 -> "emoji_native_intro1"
        screen == S.INTRO_3 -> "emoji_full_native"
        screen == S.INTRO_5 -> "emoji_native_intro3"
        screen == S.PREMIUM || screen == S.PAYWALL || screen.startsWith("splash") || screen == S.WELCOME_BACK -> null
        ScreenRouter.isPet(screen) -> "emoji_native_collab_pet_on_screen"
        ScreenRouter.isMainTab(screen) || screen == S.THEME_CATEGORY -> "emoji_native_collab_home"
        screen == S.SETTINGS -> "emoji_native_all"
        screen == S.NOTCH || screen == S.NOTCH_EXIT -> "emoji_native_collab_notch"
        screen == S.BATTERY_TROLL || screen == S.BATTERY_TROLL_SETUP || screen == S.TROLL_SETUP_2 || screen == S.TROLL_PERCENT -> "emoji_native_collab_battery_troll"
        screen == S.BATTERY_STYLE_PREVIEW || screen == S.BATTERY_PREVIEW || screen == S.CHARGE_PREVIEW -> "emoji_native_preview"
        screen == S.STATUS_BAR -> "emoji_native_collab_custom"
        ScreenRouter.isSticker(screen) -> "emoji_native_collab_emoji_sticker"
        ScreenRouter.isStatus(screen) -> null
        screen == S.CUSTOMIZE -> "emoji_native_collab_custom"
        else -> null
    }

    fun banner(screen: String): String? = null // No production banner units supplied.

    fun nextNativeScreens(screen: String): List<String> = when (screen) {
        S.LANGUAGE -> listOf(S.INTRO_2)
        S.INTRO_1 -> listOf(S.INTRO_2, S.INTRO_3, S.INTRO_5, S.HOME, S.PET, S.CUSTOMIZE, S.SETTINGS, S.STATUS_BAR, S.NOTCH, S.BATTERY_TROLL, S.ADD_STICKER, S.BATTERY_STYLE_PREVIEW)
        S.INTRO_2 -> listOf(S.INTRO_3, S.INTRO_5)
        S.INTRO_3, S.INTRO_5 -> listOf(S.HOME)
        S.HOME -> listOf(S.PET, S.CUSTOMIZE, S.SETTINGS, S.BATTERY_STYLE_PREVIEW)
        S.CUSTOMIZE -> listOf(S.STATUS_BAR, S.NOTCH, S.BATTERY_TROLL, S.ADD_STICKER)
        else -> emptyList()
    }

}
