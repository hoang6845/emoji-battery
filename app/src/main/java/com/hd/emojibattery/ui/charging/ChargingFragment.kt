package com.hd.emojibattery.ui.charging

import com.hd.emojibattery.runtime.Favorites
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
    private var previewPaused = false
    private var successDismissed = false
    private var successSheet: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        successDismissed = savedInstanceState?.getBoolean("charging_success_dismissed") ?: false
    }
    override fun onSaveInstanceState(outState: android.os.Bundle) {
        outState.putBoolean("charging_success_dismissed", successDismissed)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() {
        successSheet?.setOnDismissListener(null)
        successSheet?.dismiss()
        successSheet = null
        super.onDestroyView()
    }
    override fun initView() {
        super.initView()
        val panel = view<View>(R.id.feature_charging)
        fun updateFooterSpace() {
            val footerHeight =
                if (binding.featureFooter.visibility == View.VISIBLE) binding.featureFooter.height else 0
            panel.setPadding(
                panel.paddingLeft, panel.paddingTop, panel.paddingRight,
                footerHeight + (8 * resources.displayMetrics.density).toInt()
            )
        }
        binding.featureFooter.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateFooterSpace() }
        updateFooterSpace()
    }

    private fun selectChargingTab(tab: String) {
        if (arguments?.getString("charging_tab", screen) == tab) return
        arguments?.putString("charging_tab", tab)
        bindFeature()
        view<androidx.recyclerview.widget.RecyclerView>(R.id.charging_grid).scrollToPosition(0)
    }

    override fun onStop() {
        if (_binding != null) binding.root.findViewWithTag<com.hd.emojibattery.runtime.ChargingMediaView>(
            "runtime_charging"
        )?.let { it.close(); previewPaused = true }
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (previewPaused && _binding != null && screen == ScreenIds.CHARGE_PREVIEW) {
            previewPaused = false; bindFeature()
        }
    }

    override fun bindFeature() {
        val panel = view<View>(R.id.feature_charging)
        panel.visibility =
            if (screen == ScreenIds.CHARGE_PREVIEW || screen == ScreenIds.BATTERY_PREVIEW) View.GONE else View.VISIBLE
        val preview = panel.findViewById<View>(R.id.charging_preview)
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.charging_grid)
        val tab = if (screen in setOf(
                ScreenIds.ANIMATION,
                ScreenIds.WALLPAPER,
                ScreenIds.CHARGE_SUCCESS
            )
        ) {
            arguments?.getString("charging_tab") ?: screen
        } else screen
        val animation =
            tab == ScreenIds.ANIMATION || tab == ScreenIds.ANIMATION_MORE || tab == ScreenIds.CHARGE_SUCCESS
        val wallpaper = tab == ScreenIds.WALLPAPER || tab == ScreenIds.WALLPAPER_MORE
        val result = screen == ScreenIds.CHARGE_SUCCESS || screen == ScreenIds.APPLY_RESULT
        val more = screen == ScreenIds.ANIMATION_MORE || screen == ScreenIds.WALLPAPER_MORE
        panel.findViewById<View>(R.id.charging_landscape_options)?.visibility = if (more) View.GONE else View.VISIBLE
        panel.findViewById<View>(R.id.charging_success).visibility = View.GONE
        panel.findViewById<View>(R.id.charging_controls).visibility =
            if (!more && (animation || wallpaper)) View.VISIBLE else View.GONE
        panel.findViewById<View>(R.id.charging_categories).visibility =
            if (more) View.VISIBLE else View.GONE
        val categories =
            panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.charging_categories)
        if (categories.layoutManager == null) categories.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(
                requireContext(),
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                false
            )
        val dataKey = if (animation) "charging_animations" else "wallpapers"
        val content = catalog().collection(dataKey)
        val names = com.hd.emojibattery.runtime.CatalogGroups.names(content, dataKey)
        val categoryAdapter =
            (categories.adapter as? com.hd.emojibattery.ui.common.CategoryAdapter).takeIf { categories.tag == dataKey }
                ?: com.hd.emojibattery.ui.common.CategoryAdapter(chargingStyle = true) { selected ->
                    prefs.edit().putString("charging_category_$dataKey", selected)
                        .apply(); bindFeature()
                }
        if (categories.adapter !== categoryAdapter) categories.adapter = categoryAdapter
        categories.tag = dataKey
        categoryAdapter.submit(
            names,
            prefs.getString("charging_category_$dataKey", "All").takeIf { it in names } ?: "All")
        panel.findViewById<View>(R.id.charging_tab_animation)
            .setOnClickListener { selectChargingTab(ScreenIds.ANIMATION) }
        panel.findViewById<View>(R.id.charging_tab_wallpaper)
            .setOnClickListener { selectChargingTab(ScreenIds.WALLPAPER) }
        listOf(
            R.id.charging_tab_animation,
            R.id.charging_tab_wallpaper
        ).forEachIndexed { index, id ->
            panel.findViewById<TextView>(id).apply {
                val selected = (index == 0) == animation
                isSelected = selected
                setBackgroundResource(if (selected) R.drawable.bg_primary_button else android.R.color.transparent)
                setTextColor(if (selected) android.graphics.Color.WHITE else 0xff27205f.toInt())
            }
        }
        panel.findViewById<TextView>(R.id.charging_list_title).text =
            if (animation) "Animation" else "Wallpaper"
        panel.findViewById<View>(R.id.charging_more)
            .setOnClickListener { navigate(if (animation) ScreenIds.ANIMATION_MORE else ScreenIds.WALLPAPER_MORE) }
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.charging_show_info).apply {
            setOnCheckedChangeListener(null)
            isChecked = prefs.getBoolean("charging_show_info", true)
            setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean("charging_show_info", enabled).apply()
            }
        }
        panel.findViewById<android.widget.SeekBar>(R.id.charging_hide_after).apply {
            setOnSeekBarChangeListener(null)
            progress = prefs.getInt("charging_hide_after", 15)
            val hideLabel = panel.findViewById<TextView>(R.id.charging_hide_label)
            fun updateHideLabel(value: Int) {
                hideLabel.text = "Hide after                                             ${value}s"
            }
            updateHideLabel(progress)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: android.widget.SeekBar?,
                    value: Int,
                    user: Boolean
                ) {
                    updateHideLabel(value)
                    if (user) prefs.edit().putInt("charging_hide_after", value).apply()
                }

                override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
            })
        }
        preview.visibility = if (animation || wallpaper || result) View.GONE else View.VISIBLE
        grid.visibility = if (animation || wallpaper) View.VISIBLE else View.GONE
        listOf(
            R.id.charging_animation,
            R.id.charging_wallpaper,
            R.id.charging_preview_full
        ).forEach {
            panel.findViewById<View>(it).visibility =
                if (animation || wallpaper || result) View.GONE else View.VISIBLE
        }
        val battery = requireContext().registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (scale > 0) level * 100 / scale else 0
        val temperature =
            (battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        panel.findViewById<TextView>(R.id.charging_emoji).text = prefs.getString("emoji", "🐶")
        panel.findViewById<TextView>(R.id.charging_percent).text = "$percent%"
        panel.findViewById<TextView>(R.id.charging_status).text =
            String.format("Charging • %.1f°C", temperature)
        panel.findViewById<View>(R.id.charging_animation)
            .setOnClickListener { navigate(ScreenIds.ANIMATION) }
        panel.findViewById<View>(R.id.charging_wallpaper)
            .setOnClickListener { navigate(ScreenIds.WALLPAPER) }
        panel.findViewById<View>(R.id.charging_preview_full).setOnClickListener {
            navigate(ScreenIds.BATTERY_PREVIEW, android.os.Bundle().apply {
                putString("favorite_source", dataKey)
                putString(
                    "content_id",
                    com.hd.emojibattery.runtime.AppliedContent.selected(
                        requireContext(),
                        if (animation) "charging" else "wallpaper"
                    )?.optString("id")
                )
            })
        }
        panel.findViewById<View>(R.id.charging_apply).setOnClickListener {
            com.hd.emojibattery.runtime.FeatureApply.charging(
                requireActivity() as com.hd.emojibattery.MainActivity
            ) { if (isAdded && _binding != null) navigate(ScreenIds.CHARGE_SUCCESS) }
        }
        if (screen == ScreenIds.CHARGE_PREVIEW) {
            val full = binding.root.findViewById<View>(R.id.charging_full_screen)
            if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && full is android.widget.FrameLayout) {
                full.findViewWithTag<View>("runtime_charging")?.let { full.removeView(it) }
                val media = com.hd.emojibattery.runtime.ChargingMediaView(
                    requireContext(),
                    showInfo = false
                ).apply { tag = "runtime_charging" }
                full.addView(media, 1, android.widget.FrameLayout.LayoutParams(-1, -1))
            }
            val fullPercent = full.findViewById<TextView>(R.id.charging_full_percent)
            (fullPercent.parent as View).visibility =
                if (prefs.getBoolean("charging_show_info", true)) View.VISIBLE else View.GONE
            val fullStatus = full.findViewById<TextView>(R.id.charging_full_status)
            fullPercent.text = "$percent%"
            fullStatus.text = if ((battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
                    ?: 0) != 0
            ) "Charging in progress" else "Not charging in progress"
            val plugged =
                (battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val remaining =
                if (plugged && android.os.Build.VERSION.SDK_INT >= 28) requireContext().getSystemService(
                    android.os.BatteryManager::class.java
                ).computeChargeTimeRemaining() else -1L
            full.findViewById<TextView>(R.id.charging_full_temp).text =
                if (remaining > 0) "${(remaining / 60000).coerceAtLeast(1)} min" else "Unknown"
            val voltage =
                (battery?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
            full.findViewById<TextView>(R.id.charging_full_voltage).text =
                "${if (voltage > 0f) String.format("%.2fV", voltage) else "—"}"
            full.findViewById<View>(R.id.charging_full_back)
                .setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            full.findViewById<View>(R.id.charging_full_apply).setOnClickListener {
                com.hd.emojibattery.runtime.FeatureApply.charging(requireActivity() as com.hd.emojibattery.MainActivity) {
                    if (isAdded && _binding != null) navigate(
                        ScreenIds.CHARGE_SUCCESS
                    )
                }
            }
        }
        if (screen == ScreenIds.BATTERY_PREVIEW) {
            val carousel = binding.root.findViewById<View>(R.id.battery_preview_screen)
            carousel.findViewById<View>(R.id.battery_preview_back)
                .setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            val source = arguments?.getString("favorite_source") ?: "charging_animations"
            val contentId = arguments?.getString("preview_content_id") ?: arguments?.getString("content_id")
                ?: com.hd.emojibattery.runtime.AppliedContent.selected(requireContext(), if (source == "wallpapers") "wallpaper" else "charging")?.optString("id")
            val raw = catalog().array(source)
            val items = (0 until (raw?.length() ?: 0)).mapNotNull { raw?.optJSONObject(it) }
            var selected = contentId
            com.hd.emojibattery.ui.common.BatteryPreviewCarousel.bind(carousel, items, contentId, source, prefs) {
                selected = it.optString("id")
                arguments?.putString("preview_content_id", selected)
            }
            carousel.findViewById<View>(R.id.battery_preview_get).setOnClickListener {
                if (selected != null) com.hd.emojibattery.runtime.AppliedContent.select(requireContext(),
                    if (source == "wallpapers") "wallpaper" else "charging", source, selected!!)
                navigate(ScreenIds.CHARGE_PREVIEW)
            }
        }
        val entries = if (animation) {
            content.optJSONArray("charging_animations")
                .toCatalogEntries { Favorites.contains(prefs, dataKey, it) }
        } else if (wallpaper) content.optJSONArray("wallpapers")
            .toCatalogEntries { Favorites.contains(prefs, dataKey, it) } else emptyList()
        val group =
            prefs.getString("charging_category_$dataKey", "All").takeIf { it in names } ?: "All"
        val allowed = com.hd.emojibattery.runtime.CatalogGroups.ids(content, dataKey, group)
        val slot = if (animation) "charging" else "wallpaper"
        val displayed = if (more) entries.filter { it.id in allowed } else entries
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val columns = if (landscape && !more) 2 else 3
        val cardHeight = if (more) 145 else 174
        // A bounded viewport lets RecyclerView recycle off-screen media cards.
        grid.setHasFixedSize(true)
        if (grid.layoutManager == null) grid.layoutManager =
            GridLayoutManager(requireContext(), columns)
        val adapter = (grid.adapter as? CatalogAdapter).takeIf { grid.tag == dataKey }
            ?: CatalogAdapter(
                cardHeight = cardHeight, chargingStyle = true, showFavorite = !more,
                imageScaleType = if (wallpaper) android.widget.ImageView.ScaleType.FIT_XY else null,
                selection = {
                    com.hd.emojibattery.runtime.AppliedContent.selected(
                        requireContext(),
                        slot
                    )?.optString("id")
                },
                onFavorite = { item ->
                    Favorites.set(
                        prefs,
                        dataKey,
                        item.id,
                        !item.favorite
                    )
                    (grid.adapter as? CatalogAdapter)?.updateFavorite(item.id, !item.favorite)
                }, onSelected = { item ->
                    com.hd.emojibattery.runtime.AppliedContent.select(
                        requireContext(),
                        if (animation) "charging" else "wallpaper",
                        dataKey,
                        item.id
                    )
                    if (animation) {
                        prefs.edit().putString("animation", item.name)
                            .apply(); viewModel.selectAnimation(item.name)
                    } else prefs.edit().putString("wallpaper_id", item.id)
                        .putString("wallpaper", item.name).putString("charge_color", item.color)
                        .apply()
                    if (more) {
                        prefs.edit().putString("charging_scroll_$dataKey", item.id).apply()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                })
        if (grid.adapter !== adapter) grid.adapter = adapter
        grid.tag = dataKey
        adapter.submit(displayed)
        if (!more) prefs.getString("charging_scroll_$dataKey", null)?.let { selected ->
            val index = displayed.indexOfFirst { it.id == selected }
            if (index >= 0) grid.post {
                if (_binding != null) {
                    (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(adapter.positionOf(selected).coerceAtLeast(0), 0)
                    prefs.edit().remove("charging_scroll_$dataKey").apply()
                }
            }
        }
        panel.findViewById<TextView>(R.id.charging_apply).text = "Preview Charging"
        panel.findViewById<View>(R.id.charging_apply).visibility =
            if (more || binding.featureFooter.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        panel.findViewById<View>(R.id.charging_apply)
            .setOnClickListener { navigate(ScreenIds.CHARGE_PREVIEW) }
        if (result && !successDismissed && successSheet?.isShowing != true) {
            val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
            successSheet = sheet
            sheet.setOnDismissListener { successSheet = null; successDismissed = true }
            val content = layoutInflater.inflate(R.layout.layout_charging_success_sheet, null)
            sheet.setContentView(content)
            trackDialog(sheet); sheet.show()
            sheet.behavior.skipCollapsed = true
            sheet.behavior.state =
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
    }
}
