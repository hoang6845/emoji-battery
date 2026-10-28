package com.hd.emojibattery.ui.statusbar

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hd.emojibattery.R
import com.hd.emojibattery.StatusBarOverlayService
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.common.CatalogAdapter
import com.hd.emojibattery.ui.common.CatalogEntry
import com.hd.emojibattery.ui.common.CategoryAdapter
import com.hd.emojibattery.ui.common.toCatalogEntries
import com.hd.emojibattery.ui.navigation.ScreenIds

open class StatusBarFragment<VM : StatusBarViewModel> : BaseFeatureFragment<VM>() {
    private val tabs = listOf("Emoji", "Battery", "Emotion", "Animation", "Background")
    override fun bindFeature() {
        val panel = view<View>(R.id.feature_status_bar)
        val category = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.status_categories)
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.status_grid)
        val tab = when (screen) {
            ScreenIds.STATUS_MORE_EMOJI -> "Emoji"
            ScreenIds.STATUS_MORE_BATTERY_1 -> "Battery"
            ScreenIds.STATUS_MORE_BATTERY_2 -> "Emotion"
            ScreenIds.STATUS_MORE_BATTERY_3 -> "Animation"
            ScreenIds.STATUS_MORE_BATTERY_4 -> "Background"
            else -> viewModel.selectedTab.value ?: "Background"
        }
        category.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        category.adapter = CategoryAdapter { value ->
            viewModel.selectTab(value)
            val route = when (value) {
                "Emoji" -> ScreenIds.STATUS_MORE_EMOJI
                "Battery" -> ScreenIds.STATUS_MORE_BATTERY_1
                "Emotion" -> ScreenIds.STATUS_MORE_BATTERY_2
                "Animation" -> ScreenIds.STATUS_MORE_BATTERY_3
                else -> ScreenIds.STATUS_MORE_BATTERY_4
            }
            navigate(route)
        }.also { it.submit(tabs, tab) }
        panel.findViewById<TextView>(R.id.status_picker_title).text = tab
        val values = when (tab) {
            "Battery" -> catalog().array("battery_styles").toCatalogEntries()
            "Background" -> catalog().array("wallpapers").toCatalogEntries()
            "Animation" -> catalog().array("animations")?.let { array -> (0 until array.length()).map { val name = array.optString(it); CatalogEntry(name, name, "⚡", "") } }.orEmpty()
            else -> catalog().array("emojis").toCatalogEntries()
        }
        grid.layoutManager = GridLayoutManager(requireContext(), 4)
        grid.adapter = CatalogAdapter(onSelected = { item ->
            when (tab) {
                "Battery" -> prefs.edit().putString("status_battery_style", item.id).apply()
                "Background" -> prefs.edit().putString("status_background", item.id).apply()
                "Animation" -> prefs.edit().putString("status_animation", item.id).apply()
                "Emotion" -> prefs.edit().putString("status_emotion", item.emoji).apply()
                else -> prefs.edit().putString("status_emoji", item.emoji).apply()
            }
            panel.findViewById<TextView>(R.id.status_preview_emoji).text = prefs.getString("status_emoji", item.emoji)
            toast("${item.name} selected")
        }).also { it.submit(values) }
        panel.findViewById<TextView>(R.id.status_preview_emoji).text = prefs.getString("status_emoji", "😊")
        panel.findViewById<TextView>(R.id.status_preview_value).text = "86%  ${prefs.getString("status_battery_style", "▰▰▰")}"
        panel.findViewById<View>(R.id.status_date).setOnClickListener { navigate(ScreenIds.DATE_SETTINGS) }
        panel.findViewById<View>(R.id.status_apply).setOnClickListener { applyStatusOverlay() }
    }

    private fun applyStatusOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(requireContext())) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}")))
            toast("Allow display over other apps, then tap Apply again.")
            return
        }
        prefs.edit().putBoolean("status_overlay_enabled", true).apply()
        requireContext().startService(Intent(requireContext(), StatusBarOverlayService::class.java))
        toast("Status bar style applied")
    }
}
