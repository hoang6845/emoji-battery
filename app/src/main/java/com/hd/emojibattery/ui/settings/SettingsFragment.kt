package com.hd.emojibattery.ui.settings

import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.core.view.isVisible
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.navigation.ScreenIds

open class SettingsFragment<VM : SettingsViewModel> : BaseFeatureFragment<VM>() {
    private var restrictedSheet: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var restrictedEntryScheduled = false
    private var languageWatcher: TextWatcher? = null
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        restrictedEntryScheduled = savedInstanceState?.getBoolean("restricted_entry_consumed") ?: false
    }
    override fun onSaveInstanceState(outState: android.os.Bundle) {
        outState.putBoolean("restricted_entry_consumed", restrictedEntryScheduled)
        super.onSaveInstanceState(outState)
    }
    override fun bindFeature() {
        val panel = view<View>(R.id.feature_settings)
        val settingsHome = screen in setOf(ScreenIds.SETTINGS, ScreenIds.RESTRICTED_APPS, ScreenIds.FEEDBACK)
        val languagePage = screen == ScreenIds.LANGUAGE
        view<View>(R.id.feature_scroll).isVerticalScrollBarEnabled = !languagePage
        panel.findViewById<View>(R.id.settings_top_actions).isVisible = settingsHome
        panel.findViewById<View>(R.id.settings_main_content).isVisible = settingsHome
        panel.findViewById<View>(R.id.settings_language_content).isVisible = languagePage
        panel.findViewById<View>(R.id.settings_help_content).isVisible = screen == ScreenIds.HOW_TO_USE
        panel.findViewById<View>(R.id.settings_feedback_content).isVisible = false
        panel.findViewById<View>(R.id.settings_feedback_sent).isVisible = screen == ScreenIds.FEEDBACK_SENT
        panel.findViewById<View>(R.id.settings_restricted_content).isVisible = false
        if (screen == ScreenIds.FEEDBACK) panel.post { if (isAdded && _binding != null) showFeedbackDialog() }
        // A content refresh must not enqueue another modal for the same route.
        if (screen == ScreenIds.RESTRICTED_APPS && !restrictedEntryScheduled) {
            restrictedEntryScheduled = true
            panel.post { if (isAdded && _binding?.root?.findViewById<View>(R.id.feature_settings) === panel) showRestrictedSheet() }
        }
        panel.findViewById<android.widget.TextView>(R.id.settings_language_value).text =
            prefs.getString("language", "English") ?: "English"
        (panel.findViewById<View>(R.id.settings_enable).parent as View).isVisible = !(requireActivity() as com.hd.emojibattery.MainActivity).hasAcceptedAccessibilityUsage()
        panel.findViewById<View>(R.id.settings_enable).setOnClickListener { (requireActivity() as com.hd.emojibattery.MainActivity).requestEmojiEnable() }
        panel.findViewById<View>(R.id.settings_search).setOnClickListener { navigate(ScreenIds.SEARCH) }
        panel.findViewById<View>(R.id.settings_library).setOnClickListener { navigate(ScreenIds.FAVORITES) }
        panel.findViewById<View>(R.id.settings_premium_shortcut).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.settings_premium).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.settings_how_to).setOnClickListener { navigate(ScreenIds.HOW_TO_USE) }
        panel.findViewById<View>(R.id.settings_restricted).setOnClickListener { showRestrictedSheet() }
        panel.findViewById<View>(R.id.settings_language).setOnClickListener { navigate(ScreenIds.LANGUAGE) }
        panel.findViewById<View>(R.id.settings_feedback).setOnClickListener { navigate(ScreenIds.FEEDBACK) }
        panel.findViewById<View>(R.id.settings_privacy).setOnClickListener { openLink("privacy_policy") }
        panel.findViewById<View>(R.id.settings_terms).setOnClickListener { openLink("terms") }
        panel.findViewById<View>(R.id.settings_dmca).setOnClickListener { openLink("dmca_notice") }
        panel.findViewById<View>(R.id.help_home).setOnClickListener { if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) navigate(ScreenIds.SETTINGS) else startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        panel.findViewById<View>(R.id.feedback_done).setOnClickListener { navigate(ScreenIds.HOME) }
        panel.findViewById<View>(R.id.restricted_open).setOnClickListener { openRestrictedApps() }
        listOf(
            R.id.feedback_reason_issue to "Something is not working",
            R.id.feedback_reason_suggestion to "I have a suggestion",
            R.id.feedback_reason_help to "I need help",
            R.id.feedback_reason_other to "Other"
        ).forEach { (id, reason) -> panel.findViewById<View>(id).setOnClickListener { sendFeedback(reason) } }
        if (languagePage) setupLanguagePage(panel)
        panel.findViewById<android.widget.TextView>(R.id.settings_version).text = "Emoji Battery  •  ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}"
    }

    private fun setupLanguagePage(panel: View) {
        val options = panel.findViewById<android.widget.RadioGroup>(R.id.language_options)
        val ids = listOf(R.id.language_english, R.id.language_vietnamese, R.id.language_french, R.id.language_german, R.id.language_spanish, R.id.language_portuguese, R.id.language_italian, R.id.language_russian, R.id.language_indonesian, R.id.language_dutch, R.id.language_romanian)
        val names = listOf("English", "Vietnamese", "French", "German", "Spanish", "Portuguese", "Italian", "Russian", "Indonesian", "Dutch", "Romanian")
        val selected = prefs.getString("language", "English") ?: "English"
        options.check(ids[names.indexOf(selected).takeIf { it >= 0 } ?: 0])
        val search = panel.findViewById<android.widget.EditText>(R.id.language_search)
        languageWatcher?.let(search::removeTextChangedListener)
        languageWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                names.forEachIndexed { index, name -> panel.findViewById<View>(ids[index]).isVisible = name.contains(s?:"".orEmpty(), true) }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }.also(search::addTextChangedListener)
        panel.findViewById<View>(R.id.language_save).setOnClickListener {
            val choice = ids.indexOf(options.checkedRadioButtonId).takeIf { it >= 0 } ?: 0
            prefs.edit().putString("language", names[choice]).apply()
            navigate(ScreenIds.SETTINGS)
        }
    }

    private fun sendFeedback(reason: String) {
        viewModel.setFeedbackReason(reason)
        prefs.edit().putString("feedback_reason", reason).apply()
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) { navigate(ScreenIds.FEEDBACK_SENT); return }
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@emojibattery.app"))
            .putExtra(Intent.EXTRA_SUBJECT, "Emoji Battery feedback: $reason")
        runCatching { startActivity(Intent.createChooser(mail, "Send feedback")) }
            .onFailure { toast("Feedback saved. Add a support email to send it.") }
        navigate(ScreenIds.FEEDBACK_SENT)
    }

    private fun openLink(key: String) {
        val url = catalog().snapshot().optJSONObject("links")?.optString(key).orEmpty()
        if (url.isBlank()) { toast("This link will be added with the remote content update."); return }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("Unable to open link") }
    }

    private fun openRestrictedApps() {
        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(intent) }.onFailure { toast("No restricted apps settings available") }
    }

    private fun showFeedbackDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_feedback, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext()).setView(content).create()
        content.findViewById<View>(R.id.feedback_send).setOnClickListener {
            val selected = listOf(R.id.feedback_choice_0, R.id.feedback_choice_1, R.id.feedback_choice_2, R.id.feedback_choice_3, R.id.feedback_choice_4, R.id.feedback_choice_5, R.id.feedback_choice_6)
                .map { content.findViewById<android.widget.CheckBox>(it) }.filter { it.isChecked }.map { it.text.toString() }
            if (selected.isEmpty()) toast("Choose a reason") else { dialog.dismiss(); sendFeedback(selected.joinToString(", ")) }
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        trackDialog(dialog); dialog.show()
    }

    private fun showRestrictedSheet() {
        if (restrictedSheet != null || !isAdded || _binding == null) return
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        restrictedSheet = dialog
        dialog.setOnDismissListener {
            restrictedSheet = null
            // Direct route/preview entries also finish at Settings, even with no back stack.
            if (screen == ScreenIds.RESTRICTED_APPS && isAdded && _binding != null) {
                navigate(ScreenIds.SETTINGS)
            }
        }
        val sheet = layoutInflater.inflate(R.layout.sheet_restricted_apps, null)
        dialog.setContentView(sheet)
        val options = sheet.findViewById<android.widget.LinearLayout>(R.id.restricted_apps_list)
        val pm = requireContext().packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != requireContext().packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val selection = apps.associate { app ->
            val pkg = app.activityInfo.packageName
            pkg to com.hd.emojibattery.runtime.RestrictedApps.selected(prefs, pkg, app.loadLabel(pm).toString())
        }.toMutableMap()
        apps.forEach { app ->
            val pkg = app.activityInfo.packageName
            val row = layoutInflater.inflate(R.layout.item_restricted_app, options, false)
            row.findViewById<android.widget.ImageView>(R.id.restricted_app_icon).setImageDrawable(app.loadIcon(pm))
            row.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.restricted_app_toggle).apply {
                text = app.loadLabel(pm)
                isChecked = selection.getValue(pkg)
                setOnCheckedChangeListener { _, checked -> selection[pkg] = checked }
            }
            options.addView(row)
        }
        sheet.findViewById<View>(R.id.restricted_accessibility).apply {
            isVisible = com.hd.emojibattery.GestureAccessibilityService.active == null
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        sheet.findViewById<View>(R.id.restricted_apply).setOnClickListener {
            prefs.edit().also { editor ->
                selection.forEach { (name, checked) -> editor.putBoolean(com.hd.emojibattery.runtime.RestrictedApps.key(name), checked) }
            }.apply()
            com.hd.emojibattery.runtime.RestrictedApps.refresh(requireContext())
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onDestroyView() {
        restrictedSheet?.setOnDismissListener(null)
        restrictedSheet?.dismiss()
        restrictedSheet = null
        _binding?.root?.findViewById<android.widget.EditText>(R.id.language_search)?.removeTextChangedListener(languageWatcher)
        languageWatcher = null
        super.onDestroyView()
    }
}
