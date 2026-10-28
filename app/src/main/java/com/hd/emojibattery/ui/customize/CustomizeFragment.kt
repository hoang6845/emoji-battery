package com.hd.emojibattery.ui.customize

import android.app.AlertDialog
import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.navigation.ScreenIds
import com.hd.emojibattery.ui.navigation.ScreenRouter

open class CustomizeFragment<VM : CustomizeViewModel> : BaseFeatureFragment<VM>() {
    override fun bindFeature() {
        val panel = view<View>(R.id.feature_customize)
        val gesture = screen == ScreenIds.GESTURE || screen == ScreenIds.GESTURE_STATE_2 || screen == ScreenIds.GESTURE_ACTION || screen == ScreenIds.GESTURE_ACTION_STATE_2
        val notch = screen == ScreenIds.NOTCH || screen == ScreenIds.NOTCH_EXIT
        val troll = screen in listOf(ScreenIds.BATTERY_TROLL, ScreenIds.BATTERY_TROLL_SETUP, ScreenIds.TROLL_SETUP_2, ScreenIds.TROLL_PERCENT, ScreenIds.TROLL_EXIT)
        panel.findViewById<View>(R.id.customize_dashboard).isVisible = !gesture && !notch && !troll
        panel.findViewById<View>(R.id.gesture_editor).isVisible = gesture
        panel.findViewById<View>(R.id.notch_editor).isVisible = notch
        panel.findViewById<View>(R.id.troll_editor).isVisible = troll
        panel.findViewById<View>(R.id.customize_status).setOnClickListener { navigate(ScreenIds.STATUS_BAR) }
        panel.findViewById<View>(R.id.customize_search).setOnClickListener { navigate(ScreenIds.SEARCH) }
        panel.findViewById<View>(R.id.customize_library).setOnClickListener { navigate(ScreenIds.FAVORITES) }
        panel.findViewById<View>(R.id.customize_premium).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.customize_enable).setOnClickListener { (requireActivity() as com.hd.emojibattery.MainActivity).enableChargingMonitor() }
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
        gestureActions.forEachIndexed { index, id -> panel.findViewById<View>(id).setOnClickListener { viewModel.selectGesture(gestureNames[index]); navigate(ScreenIds.GESTURE_ACTION, android.os.Bundle().apply { putString("gesture_name", gestureNames[index]) }) } }
        val selectedAction = arguments?.getString("gesture_action") ?: prefs.getString("gesture_action", "No action")
        if (screen == ScreenIds.GESTURE_ACTION_STATE_2) {
            val chosenGesture = arguments?.getString("gesture_name") ?: prefs.getString("gesture_name", gestureNames.first())
            val row = gestureActions.getOrNull(gestureNames.indexOf(chosenGesture)) ?: gestureActions.first()
            panel.findViewById<android.widget.TextView>(row).text = "$chosenGesture\n       $selectedAction   ›"
        }
        if (screen == ScreenIds.GESTURE_ACTION) {
            val actions = listOf("No action", "Open app", "Show notifications", "Open quick settings", "Launch camera", "Open recent apps")
            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(arguments?.getString("gesture_name") ?: "Select action")
                .setItems(actions.toTypedArray()) { _, index ->
                    val name = arguments?.getString("gesture_name") ?: gestureNames.first()
                    prefs.edit().putString("gesture_name", name).putString("gesture_action", actions[index]).apply()
                    navigate(ScreenIds.GESTURE_ACTION_STATE_2, android.os.Bundle().apply { putString("gesture_name", name); putString("gesture_action", actions[index]) })
                }.show()
        }
        panel.findViewById<View>(R.id.gesture_save).setOnClickListener { prefs.edit().putBoolean("gesture_enabled", true).apply(); navigate(ScreenIds.CUSTOMIZE) }
        panel.findViewById<View>(R.id.notch_style).setOnClickListener { choose("Notch style", listOf("Default", "Dynamic", "Classic", "Hidden"), "notch_style") }
        panel.findViewById<View>(R.id.notch_position).setOnClickListener { choose("Position", listOf("Top center", "Top left", "Top right"), "notch_position") }
        panel.findViewById<View>(R.id.notch_save).setOnClickListener { prefs.edit().putBoolean("notch_enabled", true).apply(); navigate(ScreenIds.CUSTOMIZE) }
        panel.findViewById<View>(R.id.troll_percentage).setOnClickListener { navigate(ScreenIds.TROLL_PERCENT) }
        panel.findViewById<View>(R.id.troll_save).setOnClickListener { prefs.edit().putBoolean("battery_troll_enabled", true).apply(); navigate(ScreenIds.TROLL_SETUP_2) }
        panel.findViewById<View>(R.id.gesture_vibration).setOnClickListener { prefs.edit().putBoolean("gesture_vibration", !prefs.getBoolean("gesture_vibration", false)).apply() }
        if (screen == ScreenIds.NOTCH_EXIT || screen == ScreenIds.TROLL_EXIT) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Exit setup?").setMessage("Your changes have not been saved.").setNegativeButton("Continue editing", null).setPositiveButton("Exit") { _, _ -> navigate(ScreenIds.CUSTOMIZE) }.show()
        }
        if (screen != ScreenIds.CUSTOMIZE) {
            panel.findViewById<android.widget.TextView>(R.id.customize_status).text = when {
                ScreenRouter.isStatus(screen) -> "Status Bar settings"
                screen.startsWith("870:13") || screen.startsWith("870:14") -> "Choose an action for each gesture"
                screen.startsWith("870:15") || screen.startsWith("870:17") -> "Set notch style and position"
                screen.contains("44") || screen.contains("45") || screen.contains("86") || screen.contains("87") -> "Battery Troll settings"
                else -> "Customize your phone"
            }
            panel.findViewById<android.widget.TextView>(R.id.customize_troll).text = "Fake percentage  ${prefs.getInt("fake_percentage", 999)}%"
            panel.findViewById<android.widget.TextView>(R.id.customize_charge).text = "Animation  ${prefs.getString("animation", "Pulse")}"
            panel.findViewById<android.widget.TextView>(R.id.customize_wallpaper).text = "Wallpaper  ${prefs.getString("wallpaper", "Sky blue")}"
        }
    }

    private fun chooseGesture() {
        val gestures = listOf("Single-tap action", "Swipe up to down", "Swipe left to right", "Swipe right to left", "Long press action")
        AlertDialog.Builder(requireContext()).setTitle("Gesture").setItems(gestures.toTypedArray()) { _, index ->
            viewModel.selectGesture(gestures[index])
            navigate(ScreenIds.GESTURE_ACTION, android.os.Bundle().apply { putString("gesture_name", gestures[index]) })
        }.show()
    }

    private fun choose(title: String, options: List<String>, key: String) {
        AlertDialog.Builder(requireContext()).setTitle(title).setItems(options.toTypedArray()) { _, index ->
            prefs.edit().putString(key, options[index]).apply(); bindFeature()
        }.show()
    }

    private fun editPercentage() {
        val input = EditText(requireContext()).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("fake_percentage", 999).toString()) }
        AlertDialog.Builder(requireContext()).setTitle("Fake battery percentage").setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value == null) input.error = "Enter 0 to 999" else { prefs.edit().putInt("fake_percentage", value.coerceIn(0, 999)).apply(); bindFeature() }
            }.show()
    }
}
