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
    private var languageWatcher: TextWatcher? = null
    override fun bindFeature() {
        val panel = view<View>(R.id.feature_settings)
        val settingsHome = screen == ScreenIds.SETTINGS
        val languagePage = screen == ScreenIds.LANGUAGE
        panel.findViewById<View>(R.id.settings_top_actions).isVisible = settingsHome
        panel.findViewById<View>(R.id.settings_main_content).isVisible = settingsHome
        panel.findViewById<View>(R.id.settings_language_content).isVisible = languagePage
        panel.findViewById<View>(R.id.settings_help_content).isVisible = screen == ScreenIds.HOW_TO_USE
        panel.findViewById<View>(R.id.settings_feedback_content).isVisible = screen == ScreenIds.FEEDBACK
        panel.findViewById<View>(R.id.settings_feedback_sent).isVisible = screen == ScreenIds.FEEDBACK_SENT
        panel.findViewById<View>(R.id.settings_restricted_content).isVisible = screen == ScreenIds.RESTRICTED_APPS
        panel.findViewById<View>(R.id.settings_search).setOnClickListener { navigate(ScreenIds.SEARCH) }
        panel.findViewById<View>(R.id.settings_library).setOnClickListener { navigate(ScreenIds.FAVORITES) }
        panel.findViewById<View>(R.id.settings_premium_shortcut).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.settings_premium).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.settings_how_to).setOnClickListener { navigate(ScreenIds.HOW_TO_USE) }
        panel.findViewById<View>(R.id.settings_restricted).setOnClickListener { navigate(ScreenIds.RESTRICTED_APPS) }
        panel.findViewById<View>(R.id.settings_language).setOnClickListener { navigate(ScreenIds.LANGUAGE) }
        panel.findViewById<View>(R.id.settings_feedback).setOnClickListener { navigate(ScreenIds.FEEDBACK) }
        panel.findViewById<View>(R.id.settings_privacy).setOnClickListener { openLink("privacy_policy") }
        panel.findViewById<View>(R.id.settings_terms).setOnClickListener { openLink("terms") }
        panel.findViewById<View>(R.id.settings_dmca).setOnClickListener { openLink("dmca_notice") }
        panel.findViewById<View>(R.id.help_home).setOnClickListener { navigate(ScreenIds.HOME) }
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
                names.forEachIndexed { index, name -> panel.findViewById<View>(ids[index]).isVisible = name.contains(s.orEmpty(), true) }
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

    override fun onDestroyView() {
        _binding?.root?.findViewById<android.widget.EditText>(R.id.language_search)?.removeTextChangedListener(languageWatcher)
        languageWatcher = null
        super.onDestroyView()
    }
}
