package com.hd.emojibattery.ui.charging

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.common.CatalogAdapter
import com.hd.emojibattery.ui.common.CatalogEntry
import com.hd.emojibattery.ui.common.toCatalogEntries
import com.hd.emojibattery.ui.navigation.ScreenIds

open class ChargingFragment<VM : ChargingViewModel> : BaseFeatureFragment<VM>() {
    override fun bindFeature() {
        val panel = view<View>(R.id.feature_charging)
        val preview = panel.findViewById<View>(R.id.charging_preview)
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.charging_grid)
        val animation = screen == ScreenIds.ANIMATION || screen == ScreenIds.ANIMATION_MORE
        val wallpaper = screen == ScreenIds.WALLPAPER || screen == ScreenIds.WALLPAPER_MORE
        val result = screen == ScreenIds.CHARGE_SUCCESS || screen == ScreenIds.APPLY_RESULT
        preview.visibility = if (animation || wallpaper || result) View.GONE else View.VISIBLE
        grid.visibility = if (animation || wallpaper) View.VISIBLE else View.GONE
        listOf(R.id.charging_animation, R.id.charging_wallpaper, R.id.charging_preview_full).forEach { panel.findViewById<View>(it).visibility = if (animation || wallpaper || result) View.GONE else View.VISIBLE }
        val battery = requireContext().registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (scale > 0) level * 100 / scale else 0
        val temperature = (battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        panel.findViewById<TextView>(R.id.charging_emoji).text = prefs.getString("emoji", "🐶")
        panel.findViewById<TextView>(R.id.charging_percent).text = "$percent%"
        panel.findViewById<TextView>(R.id.charging_status).text = String.format("Charging • %.1f°C", temperature)
        panel.findViewById<View>(R.id.charging_animation).setOnClickListener { navigate(ScreenIds.ANIMATION) }
        panel.findViewById<View>(R.id.charging_wallpaper).setOnClickListener { navigate(ScreenIds.WALLPAPER) }
        panel.findViewById<View>(R.id.charging_preview_full).setOnClickListener { navigate(ScreenIds.BATTERY_PREVIEW) }
        panel.findViewById<View>(R.id.charging_apply).setOnClickListener { prefs.edit().putBoolean("charge_applied", true).apply(); navigate(ScreenIds.CHARGE_SUCCESS) }
        if (screen == ScreenIds.CHARGE_PREVIEW) {
            val full = binding.root.findViewById<View>(R.id.charging_full_screen)
            val fullPercent = full.findViewById<TextView>(R.id.charging_full_percent)
            val fullStatus = full.findViewById<TextView>(R.id.charging_full_status)
            fullPercent.text = "$percent%"
            fullStatus.text = if (battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0) "Charging in progress" else "Not charging in progress"
            full.findViewById<TextView>(R.id.charging_full_temp).text = "◷\n${if (temperature > 0f) String.format("%.1f°C", temperature) else "Unknown"}"
            val voltage = (battery?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
            full.findViewById<TextView>(R.id.charging_full_voltage).text = "♨\n${if (voltage > 0f) String.format("%.2fV", voltage) else "—"}"
            full.findViewById<View>(R.id.charging_full_back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            full.findViewById<View>(R.id.charging_full_apply).setOnClickListener { prefs.edit().putBoolean("charge_applied", true).apply(); navigate(ScreenIds.CHARGE_SUCCESS) }
        }
        if (screen == ScreenIds.BATTERY_PREVIEW) {
            val carousel = binding.root.findViewById<View>(R.id.battery_preview_screen)
            carousel.findViewById<View>(R.id.battery_preview_back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            val heart = carousel.findViewById<TextView>(R.id.battery_preview_heart)
            heart.setOnClickListener { heart.text = if (heart.text == "♡") "♥" else "♡" }
            carousel.findViewById<View>(R.id.battery_preview_get).setOnClickListener { navigate(ScreenIds.CHARGE_PREVIEW) }
        }
        val entries = if (animation) {
            val raw = catalog().array("animations")
            (0 until (raw?.length() ?: 0)).map { index -> raw?.optString(index) ?: "Pulse" }.map { CatalogEntry(it, it, "⚡", "") }
        } else if (wallpaper) catalog().array("wallpapers").toCatalogEntries() else emptyList()
        grid.layoutManager = GridLayoutManager(requireContext(), if (wallpaper) 3 else 2)
        grid.adapter = CatalogAdapter(onSelected = { item ->
            if (animation) { prefs.edit().putString("animation", item.name).apply(); viewModel.selectAnimation(item.name) }
            else prefs.edit().putString("wallpaper_id", item.id).putString("wallpaper", item.name).putString("charge_color", item.color).apply()
            toast("${item.name} selected")
        }).also { it.submit(entries) }
        if (result) {
            panel.findViewById<TextView>(R.id.charging_apply).text = "Done"
            panel.findViewById<View>(R.id.charging_apply).setOnClickListener { navigate(ScreenIds.HOME) }
        }
    }
}
