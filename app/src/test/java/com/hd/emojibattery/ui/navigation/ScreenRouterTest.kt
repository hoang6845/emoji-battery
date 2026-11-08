package com.hd.emojibattery.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class ScreenRouterTest {
    @Test fun stickerSelectionMustRenderItsFeature() {
        listOf(ScreenIds.ADD_STICKER, ScreenIds.ADD_STICKER_STATE_2, ScreenIds.STICKER_EDIT, ScreenIds.STICKER_MORE, ScreenIds.STICKER_EXIT, ScreenIds.STICKER_DETAIL).forEach { assertTrue(it, ScreenRouter.isSticker(it)) }
    }
    @Test fun homeMustNotAlsoRenderStatusEditor() {
        assertFalse(ScreenRouter.isStatus(ScreenIds.HOME))
        assertTrue(ScreenRouter.isStatus(ScreenIds.STATUS_SCROLL_2))
        assertFalse(ScreenRouter.isStatus("870:5499"))
    }

    @Test fun functionalFamiliesDoNotOverlap() {
        val predicates = listOf<(String) -> Boolean>(ScreenRouter::isPet, ScreenRouter::isSticker, ScreenRouter::isCharging, ScreenRouter::isStatus, ScreenRouter::isCustomize, ScreenRouter::isSettings)
        ScreenIds::class.java.fields.filter { it.type == String::class.java }.forEach { field ->
            val route = field.get(null) as String
            assertTrue("Overlapping route ${field.name}", predicates.count { it(route) } <= 1)
        }
    }
}
