package com.hd.emojibattery.ui.customize

import android.app.AlertDialog
import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.common.toCatalogEntries
import com.hd.emojibattery.ui.navigation.ScreenIds
import com.hd.emojibattery.ui.navigation.ScreenRouter

open class CustomizeFragment<VM : CustomizeViewModel> : BaseFeatureFragment<VM>() {
    private var selectedNotchStyle = 0
    private var notchDraft = android.os.Bundle()
    private var appliedNotchStyle = 0
    private var gestureSheet: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private fun notchDefault(id: Int) = when (id) {
        R.id.notch_width -> 120
        R.id.notch_height -> 16
        else -> 0
    }
    override fun hasUnsavedChanges(): Boolean = when {
        trollEditor -> trollDraft != com.hd.emojibattery.runtime.TrollBatteryConfig.read(prefs)
        screen in setOf(ScreenIds.NOTCH, ScreenIds.NOTCH_EXIT) -> selectedNotchStyle != appliedNotchStyle ||
            listOf(R.id.notch_vertical, R.id.notch_horizontal, R.id.notch_height, R.id.notch_width).any { id ->
                val key = "custom_size_$id"
                notchDraft.containsKey(key) && notchDraft.getInt(key) != prefs.getInt(key, notchDefault(id))
            }
        else -> false
    }
    private lateinit var trollDraft: com.hd.emojibattery.runtime.TrollBatteryConfig
    private var trollBackCallback: androidx.activity.OnBackPressedCallback? = null
    private val trollPreviewOwner = Any()
    private var trollPermissionPrompted = false
    private var realBatteryPercentage: Int? = null
    private var trollBatteryReceiver: android.content.BroadcastReceiver? = null
    private val trollEditor: Boolean get() = screen in setOf(ScreenIds.BATTERY_TROLL_SETUP, ScreenIds.TROLL_SETUP_2, ScreenIds.TROLL_PERCENT, ScreenIds.TROLL_EXIT)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val appliedNotchId = runCatching {
            org.json.JSONObject(prefs.getString("applied_notch", "").orEmpty()).optString("id")
        }.getOrNull()
        appliedNotchStyle = if (!prefs.getBoolean("notch_enabled", false)) 0
        else if (prefs.contains("notch_style")) prefs.getInt("notch_style", 0).coerceIn(0, 3)
        else if (!appliedNotchId.isNullOrBlank()) {
            catalog().array("notches").toCatalogEntries().indexOfFirst { it.id == appliedNotchId } + 1
        } else 0
        selectedNotchStyle = (savedInstanceState?.getInt("notch_draft_style", appliedNotchStyle)
            ?: appliedNotchStyle).coerceIn(0, 3)
        notchDraft = savedInstanceState?.getBundle("notch_draft_sizes") ?: android.os.Bundle()
        trollDraft = (savedInstanceState?.getSerializable("troll_draft") as? com.hd.emojibattery.runtime.TrollBatteryConfig)
            ?: com.hd.emojibattery.runtime.TrollBatteryConfig.read(prefs).let { config ->
                config.copy(artwork = arguments?.getString("troll_artwork") ?: config.artwork,
                    random = if (screen == ScreenIds.TROLL_SETUP_2) true else config.random)
            }
        trollPermissionPrompted = savedInstanceState?.getBoolean("troll_permission_prompted") ?: false
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("notch_draft_style", selectedNotchStyle)
        outState.putBundle("notch_draft_sizes", notchDraft)
        outState.putSerializable("troll_draft", trollDraft)
        outState.putBoolean("troll_permission_prompted", trollPermissionPrompted)
    }

    override fun onStart() {
        super.onStart()
        if (!trollEditor) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                realBatteryPercentage = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
                renderTrollPercentage()
            }
        }
        trollBatteryReceiver = receiver
        androidx.core.content.ContextCompat.registerReceiver(requireContext(), receiver,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        trollBatteryReceiver?.let { requireContext().unregisterReceiver(it) }
        trollBatteryReceiver = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && !trollEditor) bindFeature()
        if (trollEditor) {
            updateTrollPreview()
            if (!trollPermissionPrompted && !com.hd.emojibattery.BuildConfig.UI_TEST_MODE && com.hd.emojibattery.GestureAccessibilityService.active == null) {
                trollPermissionPrompted = true
                requestTrollPermission()
            }
        }
    }

    override fun onPause() {
        com.hd.emojibattery.GestureAccessibilityService.clearTrollPreview(trollPreviewOwner)
        super.onPause()
    }

    override fun onDestroyView() {
        _binding?.root?.findViewById<android.widget.ImageView>(R.id.troll_selected_artwork)?.let {
            com.hd.emojibattery.runtime.MediaAssets.clear(it)
        }
        gestureSheet?.setOnDismissListener(null)
        gestureSheet?.dismiss()
        gestureSheet = null
        trollBackCallback?.remove()
        trollBackCallback = null
        super.onDestroyView()
    }

    private fun renderTrollPercentage() {
        if (!trollEditor) return
        val root = _binding?.root ?: return
        root.findViewById<android.widget.TextView>(R.id.troll_percentage_label).text =
            if (trollDraft.real) "Percentage" else "Fake percentage"
        val percentage = if (trollDraft.real) realBatteryPercentage?.toString() ?: "—" else trollDraft.number.toString()
        root.findViewById<android.widget.TextView>(R.id.troll_value).text = "$percentage%"
        root.findViewById<View>(R.id.troll_percentage).isVisible = !trollDraft.real
    }

    private fun updateTrollPreview() {
        renderTrollPercentage()
        if (trollEditor) {
            _binding?.root?.findViewById<android.widget.ImageView>(R.id.troll_selected_artwork)?.let { image ->
                val url = com.hd.emojibattery.runtime.AppliedContent.url(
                    runCatching { org.json.JSONObject(trollDraft.artwork) }.getOrNull())
                if (image.tag != url) {
                    com.hd.emojibattery.runtime.MediaAssets.clear(image)
                    if (url.isNotBlank()) com.hd.emojibattery.runtime.MediaAssets.load(image, url)
                }
                image.isVisible = url.isNotBlank()
            }
        }
        if (trollEditor && isResumed && !com.hd.emojibattery.BuildConfig.UI_TEST_MODE) com.hd.emojibattery.GestureAccessibilityService.previewTroll(trollPreviewOwner, trollDraft)
    }

    private fun requestTrollPermission() {
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) return
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Show Battery Troll on the status bar")
            .setMessage("Enable Emoji Battery in Accessibility to show your selected battery over the system battery icon, including its live preview. The service does not read screen content.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open settings") { _, _ -> com.hd.emojibattery.runtime.AppSettings.openAccessibility(requireActivity()) }
            .create()
        trackDialog(dialog)
        dialog.show()
    }

    private fun showTrollDiscard() {
        if (!hasUnsavedChanges()) {
            com.hd.emojibattery.GestureAccessibilityService.clearTrollPreview(trollPreviewOwner)
            finishFeature()
            return
        }
        showDiscardChanges {
            com.hd.emojibattery.GestureAccessibilityService.clearTrollPreview(trollPreviewOwner)
            finishFeature()
        }
    }

    private fun configureTroll(panel: View, key: String) {
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        fun selectedArtwork(id: String) = com.hd.emojibattery.runtime.AppliedContent.find(requireContext(), key, id)?.toString().orEmpty()
        panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.troll_catalog).apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)
            adapter = com.hd.emojibattery.ui.common.CatalogAdapter(showFavorite = false, onSelected = { item ->
                navigate(ScreenIds.BATTERY_TROLL_SETUP, android.os.Bundle().apply { putString("troll_artwork", selectedArtwork(item.id)) })
            }).also { it.submit(catalog().array(key).toCatalogEntries()) }
        }
        if (!trollEditor) return
        binding.featureContent.setPadding(0, dp(8), 0, 0)
        binding.featureToolbar.setPadding(dp(19), dp(16), dp(19), dp(10))
        binding.featureTitle.textSize = 22f
        binding.featureTitle.setTextColor(0xff27205f.toInt())
        binding.featureFooter.setBackgroundColor(android.graphics.Color.WHITE)
        binding.featureFooterAction.textSize = 18f
        binding.featureFooterAction.layoutParams.height = dp(57)
        if (trollBackCallback == null) {
            trollBackCallback = object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = showTrollDiscard()
            }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
        }
        panel.findViewById<View>(R.id.troll_enable).setOnClickListener {
            if (com.hd.emojibattery.GestureAccessibilityService.active == null) requestTrollPermission() else updateTrollPreview()
        }
        fun target(real: Boolean) {
            trollDraft = trollDraft.copy(real = real)
            panel.findViewById<View>(R.id.troll_tab_fake).isSelected = !real
            panel.findViewById<View>(R.id.troll_tab_real).isSelected = real
            updateTrollPreview()
        }
        target(trollDraft.real)
        panel.findViewById<View>(R.id.troll_tab_fake).setOnClickListener { target(false) }
        panel.findViewById<View>(R.id.troll_tab_real).setOnClickListener { target(true) }
        fun mode(random: Boolean) {
            trollDraft = trollDraft.copy(random = random)
            panel.findViewById<View>(R.id.troll_style_section).isVisible = !random
            listOf(R.id.troll_custom_mode, R.id.troll_random_mode).forEachIndexed { index, id ->
                panel.findViewById<android.widget.TextView>(id).apply {
                    val selected = (index == 1) == random
                    setBackgroundResource(if (selected) R.drawable.bg_primary_button else android.R.color.transparent)
                    setTextColor(if (selected) android.graphics.Color.WHITE else 0xff999999.toInt())
                }
            }
            updateTrollPreview()
        }
        mode(trollDraft.random)
        panel.findViewById<View>(R.id.troll_custom_mode).setOnClickListener { mode(false) }
        panel.findViewById<View>(R.id.troll_random_mode).setOnClickListener { mode(true) }
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.troll_emoji_enabled).apply {
            setOnCheckedChangeListener(null)
            isChecked = trollDraft.emoji
            setOnCheckedChangeListener { _, checked -> trollDraft = trollDraft.copy(emoji = checked); updateTrollPreview() }
        }
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.troll_percentage_enabled).apply {
            setOnCheckedChangeListener(null)
            isChecked = trollDraft.showNumber
            setOnCheckedChangeListener { _, checked -> trollDraft = trollDraft.copy(showNumber = checked); updateTrollPreview() }
        }
        fun slider(id: Int, label: Int, value: Int, minValue: Int, maxValue: Int, change: (Int) -> Unit) {
            panel.findViewById<android.widget.SeekBar>(id).apply {
                setOnSeekBarChangeListener(null)
                min = minValue
                max = maxValue
                progress = value
                panel.findViewById<android.widget.TextView>(label).text = "${value}dp"
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        change(progress)
                        panel.findViewById<android.widget.TextView>(label).text = "${progress}dp"
                        updateTrollPreview()
                    }
                    override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
                })
            }
        }
        slider(R.id.troll_size, R.id.troll_size_value, trollDraft.size, 15, 120) { trollDraft = trollDraft.copy(size = it) }
        slider(R.id.troll_text_size, R.id.troll_text_size_value, trollDraft.textSize, 8, 32) { trollDraft = trollDraft.copy(textSize = it) }
        panel.findViewById<View>(R.id.troll_percentage).setOnClickListener { if (!trollDraft.real) editPercentage() }
        val styles = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.troll_styles)
        val batteries = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.troll_battery_styles)
        listOf(styles to key, batteries to "battery_styles").forEach { (grid, catalogKey) ->
            grid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)
            grid.adapter = com.hd.emojibattery.ui.common.CatalogAdapter(showFavorite = false,
                selection = { runCatching { org.json.JSONObject(trollDraft.artwork).optString("id") }.getOrNull() },
                onSelected = { item ->
                    trollDraft = trollDraft.copy(artwork = com.hd.emojibattery.runtime.AppliedContent.find(requireContext(), catalogKey, item.id)?.toString().orEmpty())
                    (styles.adapter as? com.hd.emojibattery.ui.common.CatalogAdapter)?.refreshSelection()
                    (batteries.adapter as? com.hd.emojibattery.ui.common.CatalogAdapter)?.refreshSelection()
                    updateTrollPreview()
                }
            ).also { adapter ->
                val entries = catalog().array(catalogKey).toCatalogEntries()
                adapter.submit(entries)
            }
        }
        panel.findViewById<View>(R.id.troll_save).setOnClickListener {
            requireAccessibilityUsageAccepted {
                if (com.hd.emojibattery.GestureAccessibilityService.active == null) {
                    requestTrollPermission()
                    return@requireAccessibilityUsageAccepted
                }
                // Preserve the existing overlay permission and successful Apply destination.
                if (com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())) {
                    trollDraft.save(prefs)
                    com.hd.emojibattery.GestureAccessibilityService.clearTrollPreview(trollPreviewOwner)
                    finishFeature()
                }
            }

        }
        if (screen == ScreenIds.TROLL_EXIT) panel.post { if (isAdded && _binding != null) showTrollDiscard() }
        updateTrollPreview()
    }

    private fun setGestureEnabled(enabled: Boolean) {
        val applyChange = {
            val previous = prefs.getBoolean("gesture_enabled", false)
            prefs.edit().putBoolean("gesture_enabled", enabled).apply()
            if (!com.hd.emojibattery.runtime.FeatureApply.gestures(requireActivity())) {
                prefs.edit().putBoolean("gesture_enabled", previous).apply()
            }
            bindFeature()
        }
        if (enabled) {
            requireAccessibilityUsageAccepted { applyChange() }
            bindFeature()
        } else applyChange()
    }

    override fun bindFeature() {
        val panel = view<View>(R.id.feature_customize)
        val trollKey = "troll_styles"
        val gesture = screen == ScreenIds.GESTURE || screen == ScreenIds.GESTURE_STATE_2 || screen == ScreenIds.GESTURE_ACTION || screen == ScreenIds.GESTURE_ACTION_STATE_2
        val notch = screen == ScreenIds.NOTCH || screen == ScreenIds.NOTCH_EXIT
        val troll = screen in listOf(ScreenIds.BATTERY_TROLL, ScreenIds.BATTERY_TROLL_SETUP, ScreenIds.TROLL_SETUP_2, ScreenIds.TROLL_PERCENT, ScreenIds.TROLL_EXIT)
        if (gesture) {
            binding.featureToolbar.isVisible = true
            binding.featureTitle.text = "Gesture"
        }
        panel.findViewById<View>(R.id.customize_dashboard).isVisible = !gesture && !notch && !troll
        panel.findViewById<View>(R.id.customize_top_actions).isVisible = !gesture && !notch && !troll
        panel.findViewById<View>(R.id.customize_enable_row).isVisible = !troll &&
            (!gesture || !(requireActivity() as com.hd.emojibattery.MainActivity).hasEnabledPermissionsOnce())
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.gesture_enabled).apply {
            isVisible = true
            // Preferences own this state. Restoring an older view's checked value
            // must not invoke the listener and overwrite the user's saved setting.
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            setOnCheckedChangeListener(null)
            isChecked = prefs.getBoolean("gesture_enabled", false) && prefs.getBoolean("status_overlay_enabled", false) &&
                (com.hd.emojibattery.BuildConfig.UI_TEST_MODE || (com.hd.emojibattery.GestureAccessibilityService.active != null))
            // Saved assignments remain available even while the overlay is off.
            (panel.findViewById<View>(R.id.gesture_single).parent as View).isVisible = true
            setOnCheckedChangeListener { _, enabled -> setGestureEnabled(enabled) }
        }

        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.gesture_vibration).apply {
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            setOnCheckedChangeListener(null)
            isChecked = prefs.getBoolean("gesture_vibration", false)
            setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean("gesture_vibration", enabled).apply()
                if (enabled) com.hd.emojibattery.runtime.GestureActions.vibrateTouch(context)
            }
        }
        panel.findViewById<View>(R.id.gesture_editor).isVisible = gesture
        panel.findViewById<View>(R.id.notch_editor).isVisible = notch
        if (notch) configureNotchScreen(panel)
        panel.findViewById<View>(R.id.troll_editor).isVisible = troll && screen != ScreenIds.BATTERY_TROLL
        panel.findViewById<View>(R.id.troll_catalog).isVisible = screen == ScreenIds.BATTERY_TROLL
        if (troll) configureTroll(panel, trollKey)
        val insetTop = androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top
        val statusBarResource = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarDp = (((insetTop ?: if (statusBarResource != 0) resources.getDimensionPixelSize(statusBarResource)
            else (24 * resources.displayMetrics.density).toInt()) / resources.displayMetrics.density).toInt()).coerceAtLeast(6)
        listOf(R.id.notch_vertical, R.id.notch_horizontal, R.id.notch_height, R.id.notch_width).forEach { id ->
            panel.findViewById<android.widget.SeekBar>(id).apply {
                setOnSeekBarChangeListener(null)
                val range = when (id) {
                    R.id.notch_vertical -> -statusBarDp..statusBarDp
                    R.id.notch_horizontal -> -50..50
                    R.id.notch_height -> 5..statusBarDp
                    else -> 10..250
                }
                max = range.last
                min = range.first
                val key = "custom_size_$id"
                progress = (if (notchDraft.containsKey(key)) notchDraft.getInt(key) else prefs.getInt(key, notchDefault(id))).coerceIn(range)
                val label = when (id) {
                    R.id.notch_vertical -> R.id.notch_vertical_value
                    R.id.notch_horizontal -> R.id.notch_horizontal_value
                    R.id.notch_height -> R.id.notch_height_value
                    else -> R.id.notch_width_value
                }
                panel.findViewById<android.widget.TextView>(label).text = "${progress}dp"
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, user: Boolean) {
                        if (user) notchDraft.putInt(key, value)
                        panel.findViewById<android.widget.TextView>(label).text = "${value}dp"
                    }
                    override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
                })
            }
        }
        if (screen == ScreenIds.TROLL_PERCENT) panel.post { if (isAdded && _binding != null) editPercentage() }
        panel.findViewById<View>(R.id.customize_status).setOnClickListener { navigate(ScreenIds.STATUS_BAR) }
        panel.findViewById<View>(R.id.customize_search).setOnClickListener { navigate(ScreenIds.SEARCH) }
        panel.findViewById<View>(R.id.customize_library).setOnClickListener { navigate(ScreenIds.FAVORITES) }
        panel.findViewById<View>(R.id.customize_premium).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.customize_enable).setOnClickListener {
            if (gesture) setGestureEnabled(true)
            else (requireActivity() as com.hd.emojibattery.MainActivity).requestEmojiEnable()
        }
        panel.findViewById<View>(R.id.customize_gesture).setOnClickListener { navigate(ScreenIds.GESTURE) }
        panel.findViewById<View>(R.id.customize_notch).setOnClickListener { navigate(ScreenIds.NOTCH) }
        panel.findViewById<View>(R.id.customize_troll).setOnClickListener { navigate(ScreenIds.BATTERY_TROLL) }
        panel.findViewById<View>(R.id.customize_pet).setOnClickListener { navigate(ScreenIds.PET) }
        panel.findViewById<View>(R.id.customize_status_card).setOnClickListener { navigate(ScreenIds.STATUS_BAR) }
        panel.findViewById<View>(R.id.customize_charge).setOnClickListener { navigate(ScreenIds.ANIMATION) }
        panel.findViewById<View>(R.id.customize_wallpaper).setOnClickListener { navigate(ScreenIds.WALLPAPER) }
        panel.findViewById<View>(R.id.customize_date).setOnClickListener { choose("Date format", listOf("Default", "MM/dd", "dd/MM", "EEE, MMM d"), "date_format") }
        val gestureActions = listOf(R.id.gesture_single, R.id.gesture_down, R.id.gesture_left, R.id.gesture_right, R.id.gesture_long)
        val gestureNames = listOf("Single-tap action", "Swipe up to down", "Swipe left to right", "Swipe right to left", "Long press action")
        listOf(R.id.gesture_single_value, R.id.gesture_down_value, R.id.gesture_left_value, R.id.gesture_right_value, R.id.gesture_long_value).forEachIndexed { index, id ->
            panel.findViewById<android.widget.TextView>(id).text = prefs.getString("gesture_action_${gestureNames[index]}", "No action")
        }
        fun showGestureAction(name: String) {
            if (gestureSheet?.isShowing == true) return
            val actions = listOf("No action", "Open App", "Back", "Home", "Recent", "Notifications", "Quick Settings", "Power Menu", "Lock screen", "Screenshot")
            val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
            gestureSheet = sheet
            sheet.setOnDismissListener {
                gestureSheet = null
                if (screen in setOf(ScreenIds.GESTURE_ACTION, ScreenIds.GESTURE_ACTION_STATE_2) && isAdded && isResumed && _binding != null) finishFeature(ScreenIds.GESTURE)
            }
            val content = layoutInflater.inflate(R.layout.sheet_gesture_action, null)
            content.findViewById<View>(R.id.gesture_action_close).setOnClickListener { sheet.dismiss() }
            var selected = prefs.getString("gesture_action_$name", "No action") ?: "No action"
            val ids = listOf(R.id.gesture_action_0, R.id.gesture_action_1, R.id.gesture_action_2, R.id.gesture_action_3, R.id.gesture_action_4, R.id.gesture_action_5, R.id.gesture_action_6, R.id.gesture_action_7, R.id.gesture_action_8, R.id.gesture_action_9)
            val icons = listOf(R.drawable.gesture_action_glyph_0, R.drawable.gesture_action_glyph_1, R.drawable.gesture_action_glyph_2, R.drawable.gesture_action_glyph_3, R.drawable.gesture_action_glyph_4, R.drawable.gesture_action_glyph_5, R.drawable.gesture_action_glyph_6, R.drawable.gesture_action_glyph_7, R.drawable.gesture_action_glyph_8, R.drawable.gesture_action_glyph_9)
            fun renderSelection() {
                fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
                ids.forEachIndexed { index, id ->
                    val active = actions[index] == selected
                    val circle = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(android.graphics.Color.parseColor(if (active) "#EBE3FF" else "#F6F6F7"))
                    }
                    val glyph = androidx.core.content.ContextCompat.getDrawable(requireContext(), icons[index])!!.mutate().apply {
                        setTint(android.graphics.Color.parseColor(if (active) "#27205F" else "#1A1A1F"))
                    }
                    val icon = android.graphics.drawable.LayerDrawable(arrayOf(circle, glyph)).apply {
                        // Preserve the exported artwork's bounds inside the 40dp circle.
                        val horizontal = if (index == 3) 8 else if (index == 2) 0 else -2
                        val vertical = if (index == 3) 8 else if (index == 2) 4 else -2
                        setLayerInset(1, dp(horizontal), dp(vertical), dp(horizontal), dp(vertical))
                        setBounds(0, 0, dp(40), dp(40))
                    }
                    content.findViewById<android.widget.TextView>(id).apply {
                        isSelected = active
                        setBackgroundResource(R.drawable.bg_gesture_action_option)
                        setCompoundDrawables(icon, null, null, null)
                        compoundDrawablePadding = dp(9)
                    }
                }
            }
            ids.forEachIndexed { index, id -> content.findViewById<View>(id).setOnClickListener { selected = actions[index]; renderSelection() } }
            renderSelection()
            content.findViewById<View>(R.id.gesture_action_apply).setOnClickListener {
                prefs.edit().putString("gesture_name", name).putString("gesture_action", selected).putString("gesture_action_$name", selected).apply()
                sheet.dismiss()
                val valueIds = listOf(R.id.gesture_single_value, R.id.gesture_down_value, R.id.gesture_left_value, R.id.gesture_right_value, R.id.gesture_long_value)
                gestureNames.indexOf(name).takeIf { it >= 0 }?.let { index ->
                    panel.findViewById<android.widget.TextView>(valueIds[index]).text = selected
                }
            }
            sheet.setContentView(content)
            sheet.setOnShowListener { sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED }
            trackDialog(sheet); sheet.show()
        }
        gestureActions.forEachIndexed { index, id ->
            panel.findViewById<View>(id).setOnClickListener {
                viewModel.selectGesture(gestureNames[index])
                showGestureAction(gestureNames[index])
            }
        }
        if (screen in setOf(ScreenIds.GESTURE_ACTION, ScreenIds.GESTURE_ACTION_STATE_2)) {
            showGestureAction(arguments?.getString("gesture_name") ?: gestureNames.first())
        }
        panel.findViewById<View>(R.id.gesture_save).setOnClickListener {
            setGestureEnabled(prefs.getBoolean("gesture_enabled", false))
        }
        (requireActivity() as com.hd.emojibattery.MainActivity).updateEnableControls()
        val notchStyles = listOf(R.id.notch_style_0, R.id.notch_style_1, R.id.notch_style_2, R.id.notch_style_3)
        fun renderNotchSelection() {
            notchStyles.forEachIndexed { index, id -> panel.findViewById<View>(id).isSelected = index == selectedNotchStyle }
        }
        notchStyles.forEachIndexed { index, id ->
            panel.findViewById<View>(id).setOnClickListener { selectedNotchStyle = index; renderNotchSelection() }
        }
        renderNotchSelection()
        panel.findViewById<View>(R.id.notch_position).setOnClickListener {
            panel.findViewById<android.widget.SeekBar>(R.id.notch_vertical).progress = 0
            panel.findViewById<android.widget.SeekBar>(R.id.notch_horizontal).progress = 0
            notchDraft.putInt("custom_size_${R.id.notch_vertical}", 0)
            notchDraft.putInt("custom_size_${R.id.notch_horizontal}", 0)
        }
        panel.findViewById<View>(R.id.notch_save).setOnClickListener {

            requireAccessibilityUsageAccepted {
                val previous = prefs.all
                val sizes = prefs.edit()
                notchDraft.keySet().forEach { sizes.putInt(it, notchDraft.getInt(it)) }
                sizes.apply()
                // The cards describe fixed shapes, not positions in the remote catalog.
                prefs.edit().putInt("notch_style", selectedNotchStyle)
                    .putBoolean("notch_enabled", selectedNotchStyle != 0)
                    .remove("applied_notch").apply()
                if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE || com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())) {
                    appliedNotchStyle = selectedNotchStyle
                    notchDraft.clear()
                    toast(if (prefs.getBoolean("status_overlay_enabled", false)) "Notch applied" else "Notch saved. Enable Emoji Battery to display it.")
                }
                else {
                    val rollback = prefs.edit()
                    (notchDraft.keySet() + setOf("notch_style", "notch_enabled", "applied_notch")).forEach { key ->
                        when (val value = previous[key]) {
                            is Int -> rollback.putInt(key, value)
                            is Boolean -> rollback.putBoolean(key, value)
                            is String -> rollback.putString(key, value)
                            null -> rollback.remove(key)
                        }
                    }
                    rollback.apply()
                }

            }

        }

        if (screen == ScreenIds.NOTCH_EXIT && hasUnsavedChanges()) {
            showDiscardChanges { finishFeature() }
        }
    }

    private fun configureNotchScreen(panel: View) {
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        binding.featureToolbar.setPadding(dp(19), dp(16), dp(19), dp(10))
        binding.featureTitle.apply {
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#27205F"))
            typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.nunito_bold)
        }
        binding.featureBack.apply {
            setImageResource(R.drawable.notch_back_icon)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(dp(16), dp(12), dp(16), dp(13))
        }
        binding.featureContent.setPadding(dp(19), dp(8), dp(18), 0)
        panel.setPadding(0, 0, 0, 0)
        panel.findViewById<android.view.ViewGroup>(R.id.customize_enable_row).apply {
            (layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin = 0
            setBackgroundResource(R.drawable.bg_notch_enable)
            setPadding(dp(18), 0, dp(8), 0)
            (getChildAt(0) as android.widget.TextView).textSize = 14f
        }
        panel.findViewById<View>(R.id.customize_enable).layoutParams.apply { width = dp(80); height = dp(39) }
        binding.featureFooter.setPadding(dp(26), dp(8), dp(26), dp(20))
        binding.featureFooterAction.apply {
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams.height = dp(57)
        }
    }

    private fun chooseGesture() {
        val gestures = listOf("Single-tap action", "Swipe up to down", "Swipe left to right", "Swipe right to left", "Long press action")
        AlertDialog.Builder(requireContext()).setTitle("Gesture").setItems(gestures.toTypedArray()) { _, index ->
            viewModel.selectGesture(gestures[index])
            navigate(ScreenIds.GESTURE_ACTION, android.os.Bundle().apply { putString("gesture_name", gestures[index]) })
        }.show().also { trackDialog(it) }
    }

    private fun choose(title: String, options: List<String>, key: String) {
        AlertDialog.Builder(requireContext()).setTitle(title).setItems(options.toTypedArray()) { _, index ->
            prefs.edit().putString(key, options[index]).apply(); bindFeature()
        }.show().also { trackDialog(it) }
    }

    private fun editPercentage() {
        val content = layoutInflater.inflate(R.layout.sheet_troll_percentage, null)
        val input = content.findViewById<EditText>(R.id.percentage_input)
        input.setText(trollDraft.number.toString())
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        sheet.setContentView(content)
        sheet.setOnShowListener {
            (content.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            sheet.window?.setDimAmount(0.7f)
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
        content.findViewById<View>(R.id.percentage_cancel).setOnClickListener { sheet.dismiss() }
        content.findViewById<View>(R.id.percentage_apply).setOnClickListener {
            val value = input.text.toString().toIntOrNull()
            if (value == null || value !in 0..999) input.error = "Enter 0 to 999"
            else {
                trollDraft = trollDraft.copy(number = value)
                updateTrollPreview()
                sheet.dismiss()
            }
        }
        trackDialog(sheet); sheet.show()
    }
}
