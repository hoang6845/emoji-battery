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
    private lateinit var trollDraft: com.hd.emojibattery.runtime.TrollBatteryConfig
    private var trollBackCallback: androidx.activity.OnBackPressedCallback? = null
    private val trollPreviewOwner = Any()
    private var trollPermissionPrompted = false
    private var trollExitDialog: android.app.Dialog? = null
    private val trollEditor: Boolean get() = screen in setOf(ScreenIds.BATTERY_TROLL_SETUP, ScreenIds.TROLL_SETUP_2, ScreenIds.TROLL_PERCENT, ScreenIds.TROLL_EXIT)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        trollDraft = (savedInstanceState?.getSerializable("troll_draft") as? com.hd.emojibattery.runtime.TrollBatteryConfig)
            ?: com.hd.emojibattery.runtime.TrollBatteryConfig.read(prefs).let { config ->
                config.copy(artwork = arguments?.getString("troll_artwork") ?: config.artwork,
                    random = if (screen == ScreenIds.TROLL_SETUP_2) true else config.random)
            }
        trollPermissionPrompted = savedInstanceState?.getBoolean("troll_permission_prompted") ?: false
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("troll_draft", trollDraft)
        outState.putBoolean("troll_permission_prompted", trollPermissionPrompted)
    }

    override fun onResume() {
        super.onResume()
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
        trollBackCallback?.remove()
        trollBackCallback = null
        trollExitDialog = null
        super.onDestroyView()
    }

    private fun updateTrollPreview() {
        if (trollEditor && isResumed && !com.hd.emojibattery.BuildConfig.UI_TEST_MODE) com.hd.emojibattery.GestureAccessibilityService.previewTroll(trollPreviewOwner, trollDraft)
    }

    private fun requestTrollPermission() {
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) return
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Show Battery Troll on the status bar")
            .setMessage("Enable Emoji Battery in Accessibility to show your selected battery over the system battery icon, including its live preview. The service does not read screen content.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open settings") { _, _ -> startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .create()
        trackDialog(dialog)
        dialog.show()
    }

    private fun showTrollDiscard() {
        if (trollExitDialog?.isShowing == true) return
        val content = layoutInflater.inflate(R.layout.dialog_troll_discard, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext()).setView(content).create()
        trollExitDialog = dialog
        content.findViewById<View>(R.id.discard_exit).setOnClickListener {
            dialog.dismiss()
            com.hd.emojibattery.GestureAccessibilityService.clearTrollPreview(trollPreviewOwner)
            navigate(ScreenIds.CUSTOMIZE)
        }
        content.findViewById<View>(R.id.discard_keep).setOnClickListener { dialog.dismiss() }
        trackDialog(dialog)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.7f)
            setLayout(minOf(resources.displayMetrics.widthPixels - (43 * resources.displayMetrics.density).toInt(), (369 * resources.displayMetrics.density).toInt()), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
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
        panel.findViewById<android.widget.TextView>(R.id.troll_value).text = trollDraft.number.toString()
        panel.findViewById<View>(R.id.troll_percentage).setOnClickListener { editPercentage() }
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
                    navigate(ScreenIds.CUSTOMIZE)
                }
            }

        }
        if (screen == ScreenIds.TROLL_EXIT) panel.post { if (isAdded && _binding != null) showTrollDiscard() }
        updateTrollPreview()
    }

    override fun bindFeature() {
        val panel = view<View>(R.id.feature_customize)
        val trollKey = "troll_styles"
        val gesture = screen == ScreenIds.GESTURE || screen == ScreenIds.GESTURE_STATE_2 || screen == ScreenIds.GESTURE_ACTION || screen == ScreenIds.GESTURE_ACTION_STATE_2
        val notch = screen == ScreenIds.NOTCH || screen == ScreenIds.NOTCH_EXIT
        val troll = screen in listOf(ScreenIds.BATTERY_TROLL, ScreenIds.BATTERY_TROLL_SETUP, ScreenIds.TROLL_SETUP_2, ScreenIds.TROLL_PERCENT, ScreenIds.TROLL_EXIT)
        panel.findViewById<View>(R.id.customize_dashboard).isVisible = !gesture && !notch && !troll
        panel.findViewById<View>(R.id.customize_top_actions).isVisible = !notch && !troll
        panel.findViewById<View>(R.id.customize_enable_row).isVisible = !troll && !(requireActivity() as com.hd.emojibattery.MainActivity).hasAcceptedAccessibilityUsage()
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.gesture_enabled).apply {
            isChecked = screen != ScreenIds.GESTURE_STATE_2 && prefs.getBoolean("gesture_enabled", true)
            fun showRows(enabled: Boolean) { (panel.findViewById<View>(R.id.gesture_single).parent as View).isVisible = enabled }
            showRows(isChecked)
            setOnCheckedChangeListener { _, enabled -> prefs.edit().putBoolean("gesture_enabled", enabled).apply(); showRows(enabled) }
        }
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.gesture_vibration).apply {
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
        listOf(R.id.notch_vertical, R.id.notch_horizontal, R.id.notch_height, R.id.notch_width).forEach { id ->
            panel.findViewById<android.widget.SeekBar>(id).apply {
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, user: Boolean) { if (user) prefs.edit().putInt("custom_size_$id", value).apply()
                        val label = when (id) { R.id.notch_vertical -> R.id.notch_vertical_value; R.id.notch_horizontal -> R.id.notch_horizontal_value; R.id.notch_height -> R.id.notch_height_value; R.id.notch_width -> R.id.notch_width_value; else -> null }
                        label?.let { panel.findViewById<android.widget.TextView>(it).text = "${value}dp" } }
                    override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
                })
                progress = prefs.getInt("custom_size_$id", if (id == R.id.notch_vertical || id == R.id.notch_height) 15 else 10)
                val label = when (id) { R.id.notch_vertical -> R.id.notch_vertical_value; R.id.notch_horizontal -> R.id.notch_horizontal_value; R.id.notch_height -> R.id.notch_height_value; R.id.notch_width -> R.id.notch_width_value; else -> null }
                label?.let { panel.findViewById<android.widget.TextView>(it).text = "${progress}dp" }
            }
        }
        if (screen == ScreenIds.TROLL_PERCENT) panel.post { if (isAdded && _binding != null) editPercentage() }
        panel.findViewById<View>(R.id.customize_status).setOnClickListener { navigate(ScreenIds.STATUS_BAR) }
        panel.findViewById<View>(R.id.customize_search).setOnClickListener { navigate(ScreenIds.SEARCH) }
        panel.findViewById<View>(R.id.customize_library).setOnClickListener { navigate(ScreenIds.FAVORITES) }
        panel.findViewById<View>(R.id.customize_premium).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.customize_enable).setOnClickListener { (requireActivity() as com.hd.emojibattery.MainActivity).requestEmojiEnable() }
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
            val actions = listOf("No action", "Open App", "Back", "Home", "Recent", "Notifications", "Quick Settings", "Power Menu", "Lock screen", "Screenshot")
            val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
            val content = layoutInflater.inflate(R.layout.sheet_gesture_action, null)
            content.findViewById<View>(R.id.gesture_action_close).setOnClickListener { sheet.dismiss() }
            var selected = prefs.getString("gesture_action_$name", "No action") ?: "No action"
            val ids = listOf(R.id.gesture_action_0, R.id.gesture_action_1, R.id.gesture_action_2, R.id.gesture_action_3, R.id.gesture_action_4, R.id.gesture_action_5, R.id.gesture_action_6, R.id.gesture_action_7, R.id.gesture_action_8, R.id.gesture_action_9)
            val icons = listOf(R.drawable.gesture_action_icon_0, R.drawable.gesture_action_icon_1, R.drawable.gesture_action_icon_2, R.drawable.gesture_action_icon_3, R.drawable.gesture_action_icon_4, R.drawable.gesture_action_icon_5, R.drawable.gesture_action_icon_6, R.drawable.gesture_action_icon_7, R.drawable.gesture_action_icon_8, R.drawable.gesture_action_icon_9)
            ids.forEachIndexed { index, id ->
                val icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), icons[index])!!
                val size = (40 * resources.displayMetrics.density).toInt()
                icon.setBounds(0, 0, size, size)
                content.findViewById<android.widget.TextView>(id).apply {
                    setCompoundDrawables(icon, null, null, null)
                    compoundDrawablePadding = (9 * resources.displayMetrics.density).toInt()
                }
            }
            fun renderSelection() { ids.forEachIndexed { index, id -> content.findViewById<View>(id).setBackgroundResource(if (actions[index] == selected) R.drawable.bg_plan_selected else R.drawable.bg_plan) } }
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
        if (screen == ScreenIds.GESTURE_ACTION) {
            showGestureAction(arguments?.getString("gesture_name") ?: gestureNames.first())
        }
        if (gesture) {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() { navigate(ScreenIds.CUSTOMIZE) }
                })
        }

        panel.findViewById<View>(R.id.gesture_save).setOnClickListener { prefs.edit().putBoolean("gesture_enabled", panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.gesture_enabled).isChecked).apply(); com.hd.emojibattery.runtime.FeatureApply.gestures(requireActivity()) }
        val notchStyles = listOf(R.id.notch_style_0, R.id.notch_style_1, R.id.notch_style_2, R.id.notch_style_3)
        fun renderNotchSelection() {
            notchStyles.forEachIndexed { index, id -> panel.findViewById<View>(id).isSelected = index == selectedNotchStyle }
        }
        notchStyles.forEachIndexed { index, id ->
            panel.findViewById<View>(id).setOnClickListener { selectedNotchStyle = index; renderNotchSelection() }
        }
        renderNotchSelection()
        panel.findViewById<View>(R.id.notch_position).setOnClickListener {
            panel.findViewById<android.widget.SeekBar>(R.id.notch_vertical).progress = 15
            panel.findViewById<android.widget.SeekBar>(R.id.notch_horizontal).progress = 10
            prefs.edit().putInt("custom_size_${R.id.notch_vertical}", 15).putInt("custom_size_${R.id.notch_horizontal}", 10).apply()
        }
        panel.findViewById<View>(R.id.notch_save).setOnClickListener {

            requireAccessibilityUsageAccepted {
                if (selectedNotchStyle == 0) prefs.edit().putBoolean("notch_enabled", false).remove("applied_notch").apply()
                else {
                    val item = catalog().array("notches").toCatalogEntries().getOrNull(selectedNotchStyle - 1)
                    if (item == null) { toast("Notch style is not available yet"); return@requireAccessibilityUsageAccepted }
                    com.hd.emojibattery.runtime.AppliedContent.select(requireContext(), "notch", "notches", item.id)
                    prefs.edit().putBoolean("notch_enabled", true).apply()
                }
                if (com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())) navigate(ScreenIds.CUSTOMIZE)

            }

        }

        if (screen == ScreenIds.NOTCH_EXIT || screen == ScreenIds.GESTURE_ACTION_STATE_2) {
            val content = layoutInflater.inflate(R.layout.dialog_discard_changes, null)
            val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext()).setView(content).create()
            content.findViewById<View>(R.id.discard_exit).setOnClickListener { dialog.dismiss(); navigate(ScreenIds.CUSTOMIZE) }
            content.findViewById<View>(R.id.discard_keep).setOnClickListener { dialog.dismiss() }
            trackDialog(dialog); dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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
                view<android.widget.TextView>(R.id.troll_value).text = value.toString()
                updateTrollPreview()
                sheet.dismiss()
            }
        }
        trackDialog(sheet); sheet.show()
    }
}
