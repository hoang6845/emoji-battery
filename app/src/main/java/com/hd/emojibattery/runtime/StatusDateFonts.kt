package com.hd.emojibattery.runtime

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.hd.emojibattery.R

/** Keep indices stable: saved status bar settings refer to this order. */
object StatusDateFonts {
    val names = listOf("Roboto", "Baloo 2", "Cherry Bomb One", "Inter", "Belgrano", "Basic", "Carter One")

    private val resources = intArrayOf(
        R.font.date_roboto, R.font.date_baloo2, R.font.date_cherrybombone,
        R.font.date_inter, R.font.date_belgrano, R.font.date_basic, R.font.date_carterone
    )

    fun typeface(context: Context, index: Int): Typeface =
        ResourcesCompat.getFont(context, resources[index.takeIf { it in resources.indices } ?: 0])
            ?: Typeface.DEFAULT
}
