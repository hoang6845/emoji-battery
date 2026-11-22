package com.hd.emojibattery.ui.statusbar

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ImageView
import android.view.ViewGroup
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.hd.emojibattery.runtime.StatusDateFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    private val editorSession by lazy { androidx.lifecycle.ViewModelProvider(requireActivity())[StatusEditorViewModel::class.java] }
    @Suppress("DEPRECATION")
    override fun hasUnsavedChanges(): Boolean = if (isSelectionScreen) selectionDraft.keySet().any { key ->
        val selected = selectionDraft.get(key)
        val current: Any? = when (selected) {
            is Int -> prefs.getInt(key, Int.MIN_VALUE)
            is Boolean -> prefs.getBoolean(key, !selected)
            is String -> prefs.getString(key, null)
            else -> null
        }
        selected != current
    } else editorSession.hasChanges(com.hd.emojibattery.runtime.AppliedContent.prefs(requireContext()))
    override val prefs: android.content.SharedPreferences by lazy {
        editorSession.preferences(requireContext().getSharedPreferences(
            if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) "emoji_battery_preview" else "emoji_battery", 0))
    }
    private fun selectedStatus(context: android.content.Context, slot: String): org.json.JSONObject? =
        runCatching { org.json.JSONObject(prefs.getString("applied_$slot", "").orEmpty()) }.getOrNull()
    private fun selectStatus(context: android.content.Context, slot: String, key: String, id: String): Boolean {
        val item = com.hd.emojibattery.runtime.AppliedContent.find(context, key, id) ?: return false
        setOption("applied_$slot", item.toString())
        if (slot == "battery") {
            setOption("status_battery_style", id)
            setOption("status_part_battery", true)
        }
        return true
    }
    private val isShowBackgroud = false
    private val isShowEmojiIcon = false
    private val tabs = listOf("Emoji", "Battery", "Emotion", "Animation", "Background")
    private var previewListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? =
        null
    private var previewHost: android.widget.FrameLayout? = null
    private var datePreviewJob: Job? = null
    private var selectionDraft = android.os.Bundle()
    private var selectedThemeTab: Int? = null
    private var selectedIconTab: Int? = null
    private var pendingBatteryId: String? = null
    private var previewRefreshPending = false
    private val displayRefreshers = mutableListOf<() -> Unit>()
    private var contentListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? =
        null
    private var refreshPreview: (() -> Unit)? = null
    private val isSelectionScreen: Boolean
        get() = screen in setOf(
            ScreenIds.STATUS_MORE_EMOJI, ScreenIds.STATUS_MORE_BATTERY_1, ScreenIds.STATUS_MORE_BATTERY_2,
            ScreenIds.STATUS_MORE_BATTERY_3, ScreenIds.STATUS_MORE_BATTERY_4, ScreenIds.DATE_SETTINGS, ScreenIds.STATUS_SCROLL_5
        )

    private var refreshThemeColors: (() -> Unit)? = null
    private val backgroundCrop = registerForActivityResult(BackgroundCropContract()) { uri ->
        if (uri != null) {
            val context = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val valid = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val file = com.hd.emojibattery.runtime.MediaAssets.file(context, uri.toString())
                            ?: error("Unable to read image")
                        com.hd.emojibattery.runtime.MediaAssets.drawable(file)
                    }.isSuccess
                }
                if (valid) {
                    val item = org.json.JSONObject().put("id", "custom_background")
                        .put("asset_url", uri.toString())
                    prefs.edit().putString("applied_background", item.toString())
                        .putString("status_custom_background", item.toString())
                        .putBoolean("status_part_background", true).apply()
                    view?.findViewById<View>(R.id.status_custom_photo)?.isVisible = true
                    toast("Photo selected. Tap Apply to use the status bar.")
                } else toast("Unable to open this image. Choose another photo.")
            }
        }
    }
    private val backgroundPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            backgroundCrop.launch(uri)
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        selectionDraft =
            savedInstanceState?.getBundle("status_selection_draft") ?: android.os.Bundle()
        if (savedInstanceState == null) arguments?.getString("content_id")?.let {
            val source = arguments?.getString("content_source") ?: "battery_styles"
            val slot = when (source) { "animations" -> "animation"; "emotions" -> "emotion"; "emojis" -> "emoji"; "themes" -> "theme"; else -> "battery" }
            if (!selectStatus(requireContext(), slot, source, it) && source == "battery_styles") {
                pendingBatteryId = it
            }
            if (source == "themes") com.hd.emojibattery.runtime.AppliedContent.find(requireContext(), source, it)?.let { theme ->
                listOf("battery_src" to "battery", "emoji_src" to "emoji").forEach { (field, target) ->
                    setOption("applied_$target", org.json.JSONObject().put("asset_url", com.hd.emojibattery.runtime.AppliedContent.url(theme, field)).toString())
                }
            }
        }
        savedInstanceState?.let { state ->
            pendingBatteryId = state.getString("status_pending_battery_id")
            if (state.containsKey("status_editor_theme_tab")) selectedThemeTab = state.getInt("status_editor_theme_tab")
            if (state.containsKey("status_editor_icon_tab")) selectedIconTab = state.getInt("status_editor_icon_tab")
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("status_selection_draft", selectionDraft)
        outState.putString("status_pending_battery_id", pendingBatteryId)
        selectedThemeTab?.let { outState.putInt("status_editor_theme_tab", it) }
        selectedIconTab?.let { outState.putInt("status_editor_icon_tab", it) }
    }

    private fun optionInt(key: String, default: Int) =
        if (isSelectionScreen && selectionDraft.containsKey(key)) selectionDraft.getInt(key) else prefs.getInt(
            key,
            default
        )

    private fun setOption(key: String, value: Any) {
        if (isSelectionScreen) {
            when (value) {
                is Int -> selectionDraft.putInt(key, value)
                is Boolean -> selectionDraft.putBoolean(key, value)
                is String -> selectionDraft.putString(key, value)
            }
            refreshPreview?.invoke()
        } else {
            val edit = prefs.edit()
            when (value) {
                is Int -> edit.putInt(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is String -> edit.putString(key, value)
            }
            edit.apply()
        }
    }

    private fun leaveSelection() {
        if (parentFragmentManager.backStackEntryCount > 0) parentFragmentManager.popBackStack()
        else (requireActivity() as com.hd.emojibattery.MainActivity).openScreen(
            ScreenIds.STATUS_BAR,
            false
        )
    }

    private fun showSelectionDiscard() {
        if (!hasUnsavedChanges()) { selectionDraft.clear(); leaveSelection(); return }
        showDiscardChanges {
            selectionDraft.clear()
            leaveSelection()
        }
    }

    override fun initView() {
        if (prefs.getInt(
                "icon_style_2",
                0
            ) !in com.hd.emojibattery.runtime.StatusRenderer.chargeStyles.indices
        ) {
            prefs.edit().putInt("icon_style_2", 0).apply()
        }
        super.initView()
        if (isSelectionScreen) {
            binding.featureFooter.isVisible = false
            binding.featureScroll.setPadding(0, 0, 0, 0)
            view<View>(R.id.status_apply).isVisible = false
            val toolbar = binding.featureToolbar
            toolbar.removeViewAt(toolbar.childCount - 1)
            toolbar.addView(android.widget.ImageButton(requireContext()).apply {
                contentDescription = "Done"
                setImageResource(R.drawable.ic_status_done)
                setBackgroundResource(R.drawable.bg_back_button)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener {
                    val edit = prefs.edit()
                    selectionDraft.keySet().forEach { key ->
                        @Suppress("DEPRECATION")
                        when (val value = selectionDraft.get(key)) {
                            is Int -> edit.putInt(key, value)
                            is Boolean -> edit.putBoolean(key, value)
                            is String -> edit.putString(key, value)

                            else -> Unit
                        }
                    }
                    edit.apply()
                    selectionDraft.clear()
                    leaveSelection()
                }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() = showSelectionDiscard()
                })
        }
        if (!isSelectionScreen) {
            binding.featureFooterAction.setOnClickListener { applyStatusOverlay() }
        }
        configurePinnedPreview()
    }

    private fun configurePinnedPreview() {
        val panel = view<LinearLayout>(R.id.feature_status_bar)
        val preview = panel.findViewById<View>(R.id.status_live_card)
        if (!preview.isVisible) return

        // Bound the editor to the available screen height. Only the compact live
        // preview stays above the controls; Start and all settings can scroll.
        val outerScroll = binding.featureScroll
        val host = outerScroll.parent as ViewGroup
        (panel.parent as ViewGroup).removeView(panel)
        val controls = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val children = (0 until panel.childCount).map { panel.getChildAt(it) }
        children.filter { it !== preview }.forEach {
            panel.removeView(it)
            controls.addView(it)
        }
        val content = binding.featureContent
        panel.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, 0)
        val controlsScroll = android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, content.paddingBottom + outerScroll.paddingBottom)
            addView(controls, ViewGroup.LayoutParams(-1, -2))
        }
        binding.featureFooter.addOnLayoutChangeListener { footer, _, _, _, _, _, _, _, _ ->
            controlsScroll.setPadding(0, 0, 0, content.paddingBottom + footer.height)
        }
        panel.addView(controlsScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        outerScroll.isVisible = false
        host.addView(panel, host.indexOfChild(outerScroll) + 1, ViewGroup.LayoutParams(-1, -1))
        // Reparenting detaches StatusRenderer, which clears its loaded artwork.
        // Render again in the pinned host using the current editor selection.
        refreshPreview?.invoke()
    }

    private fun updateDatePreview(
        target: TextView,
        pattern: String,
        locale: java.util.Locale,
        suffix: String = ""
    ) {
        datePreviewJob?.cancel()
        datePreviewJob = viewLifecycleOwner.lifecycleScope.launch {
            target.text = StatusDateFormatter.format(pattern, locale) + suffix
        }
    }

    override fun onDestroyView() {
        datePreviewJob?.cancel()
        datePreviewJob = null
        previewListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        previewListener = null; previewHost = null
        previewRefreshPending = false
        contentListener?.let {
            com.hd.emojibattery.runtime.AppliedContent.prefs(requireContext())
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        contentListener = null
        displayRefreshers.clear()
        refreshThemeColors = null
        refreshPreview = null
        super.onDestroyView()
    }

    private fun scrollToSelectedBattery(
        list: androidx.recyclerview.widget.RecyclerView,
        entries: List<CatalogEntry>
    ) {
        val selected =
            selectedStatus(requireContext(), "battery")
                ?: return
        val selectedUrl = com.hd.emojibattery.runtime.AppliedContent.url(selected)
        val raw = catalog().array("battery_styles")
        val index = entries.indexOfFirst { entry ->
            entry.id == selected.optString("id").takeIf { it.isNotBlank() } ||
                    (selectedUrl.isNotBlank() && raw != null && (0 until raw.length()).any { i ->
                        val item = raw.optJSONObject(i)
                        item?.optString("id") == entry.id && com.hd.emojibattery.runtime.AppliedContent.url(
                            item
                        ) == selectedUrl
                    })
        }
        if (index >= 0) (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
            index,
            0
        )
    }

    private fun bindRuntimePreview(panel: View) {
        val label = panel.findViewById<TextView>(R.id.status_preview_value)
        if (previewHost == null) {
            val parent = label.parent as android.view.ViewGroup
            previewHost = android.widget.FrameLayout(requireContext()).also {
                parent.addView(
                    it,
                    parent.indexOfChild(label),
                    android.view.ViewGroup.LayoutParams(
                        -1,
                        (48 * resources.displayMetrics.density).toInt()
                    )
                )
            }
        }
        label.visibility = View.GONE
        fun render() {
            val holder = previewHost ?: return
            if (!isAdded) return
            val height = (prefs.getInt("status_height", 45) * resources.displayMetrics.density).toInt()
            if (holder.layoutParams.height != height) {
                holder.layoutParams.height = height
                holder.requestLayout()
            }
            listOf(
                R.id.status_background_grid, R.id.status_background_samples,
                R.id.status_emoji_samples, R.id.status_battery_samples,
                R.id.status_emotion_samples, R.id.status_animation_samples
            ).forEach { id ->
                (panel.findViewById<androidx.recyclerview.widget.RecyclerView>(id).adapter as? CatalogAdapter)?.refreshSelection()
            }
            holder.removeAllViews()
            val previewPrefs = object : android.content.SharedPreferences by prefs {
                override fun getInt(key: String?, defValue: Int): Int =
                    if (isSelectionScreen && selectionDraft.containsKey(key)) selectionDraft.getInt(
                        key
                    ) else prefs.getInt(key, defValue)

                override fun getBoolean(key: String?, defValue: Boolean): Boolean =
                    if (isSelectionScreen && selectionDraft.containsKey(key)) selectionDraft.getBoolean(
                        key
                    ) else prefs.getBoolean(key, defValue)

                override fun getString(key: String?, defValue: String?): String? =
                    if (isSelectionScreen && selectionDraft.containsKey(key)) selectionDraft.getString(
                        key
                    ) else prefs.getString(key, defValue)
            }
            holder.addView(
                com.hd.emojibattery.runtime.StatusRenderer(
                    requireContext(),
                    prefs = previewPrefs
                ), android.widget.FrameLayout.LayoutParams(-1, -1)
            )
        }
        fun scheduleRender() {
            val holder = previewHost ?: return
            if (previewRefreshPending) return
            previewRefreshPending = true
            holder.post {
                if (previewHost === holder) {
                    previewRefreshPending = false
                    render()
                    displayRefreshers.forEach { it() }
                }
            }
        }
        refreshPreview = { scheduleRender() }
        if (previewListener == null) {
            previewListener =
                android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    // Editor navigation is not a rendered status-bar setting.
                    if (key !in setOf("icon_tab", "status_theme_tab", "status_category") &&
                        key?.startsWith("status_picker_category_") != true &&
                        key?.startsWith("favorite:") != true &&
                        key?.startsWith("fav_") != true
                    ) scheduleRender()
                }
            prefs.registerOnSharedPreferenceChangeListener(previewListener)
            val contentPrefs = com.hd.emojibattery.runtime.AppliedContent.prefs(requireContext())
            if (contentPrefs !== prefs) {
                contentListener =
                    android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                        scheduleRender()
                    }
                contentPrefs.registerOnSharedPreferenceChangeListener(contentListener)
            }
        }
        render()
    }

    override fun bindFeature() {
        pendingBatteryId?.let { id ->
            if (selectStatus(requireContext(), "battery", "battery_styles", id)) pendingBatteryId = null
        }
        val panel = view<View>(R.id.feature_status_bar)
        val category =
            panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.status_categories)
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.status_grid)
        val picker = screen in setOf(
            ScreenIds.STATUS_MORE_EMOJI,
            ScreenIds.STATUS_MORE_BATTERY_1,
            ScreenIds.STATUS_MORE_BATTERY_2,
            ScreenIds.STATUS_MORE_BATTERY_3,
            ScreenIds.STATUS_MORE_BATTERY_4
        )
        val date = screen == ScreenIds.DATE_SETTINGS || screen == ScreenIds.STATUS_SCROLL_5
        val iconEditor = screen in setOf(
            ScreenIds.STATUS_SCROLL_3,
            ScreenIds.STATUS_SCROLL_4,
            ScreenIds.STATUS_SCROLL_6
        )
        panel.findViewById<View>(R.id.status_icons).isVisible = false
        panel.findViewById<View>(R.id.status_icons)
            .setOnClickListener { navigate(ScreenIds.STATUS_SCROLL_3) }
        panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.status_emoji_categories).isVisible =
            false

        panel.findViewById<View>(R.id.status_editor_sections).isVisible = !picker && !date
        panel.findViewById<View>(R.id.status_live_card).isVisible = !picker
        panel.findViewById<View>(R.id.status_activation_card).isVisible = !picker && !date
        panel.findViewById<View>(R.id.status_icon_panel).isVisible = !picker && !date
        panel.findViewById<View>(R.id.status_enable).isVisible = picker || date
        val part = when (screen) {
            ScreenIds.STATUS_MORE_BATTERY_1 -> "battery"; ScreenIds.STATUS_MORE_BATTERY_2 -> "emotion"; ScreenIds.STATUS_MORE_BATTERY_3 -> "animation"; ScreenIds.STATUS_MORE_BATTERY_4 -> "background"; ScreenIds.DATE_SETTINGS, ScreenIds.STATUS_SCROLL_5 -> "date"; else -> "emoji"
        }
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.status_enable).apply {
            text =
                if (date) "Enable Date" else "Enable ${if (screen == ScreenIds.STATUS_MORE_BATTERY_2) "Emotion" else if (screen == ScreenIds.STATUS_MORE_BATTERY_3) "Animation" else "Emoji Battery"}"
            setOnCheckedChangeListener(null)
            val key = "status_part_$part"
            isChecked =
                if (isSelectionScreen && selectionDraft.containsKey(key)) selectionDraft.getBoolean(
                    key
                ) else prefs.getBoolean(key, true)
            setOnCheckedChangeListener { _, checked -> setOption(key, checked) }
        }
        panel.findViewById<View>(R.id.status_date).isVisible = !picker && !date
        if (date) {
            val live = panel.findViewById<android.widget.LinearLayout>(R.id.status_live_card)
            live.layoutParams.height = (118 * resources.displayMetrics.density).toInt()
            for (index in 3 until live.childCount) live.getChildAt(index).isVisible = false
        }
        setupDateAndIcons(panel)
        panel.findViewById<View>(R.id.status_date_panel).isVisible = date
        panel.findViewById<View>(R.id.status_categories).isVisible = picker
        panel.findViewById<View>(R.id.status_picker_title).isVisible = picker
        grid.isVisible = picker
        val sections = listOf(
            Triple(R.id.status_emoji_samples, R.id.status_more_emoji, ScreenIds.STATUS_MORE_EMOJI),
            Triple(
                R.id.status_battery_samples,
                R.id.status_more_battery,
                ScreenIds.STATUS_MORE_BATTERY_1
            ),
            Triple(
                R.id.status_emotion_samples,
                R.id.status_more_emotion,
                ScreenIds.STATUS_MORE_BATTERY_2
            ),
            Triple(
                R.id.status_animation_samples,
                R.id.status_more_animation,
                ScreenIds.STATUS_MORE_BATTERY_3
            ),
            Triple(
                R.id.status_background_samples,
                R.id.status_more_background,
                ScreenIds.STATUS_MORE_BATTERY_4
            )
        )
        sections.forEachIndexed { index, (gridId, moreId, destination) ->
            val samples = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(gridId)
            samples.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            samples.adapter = CatalogAdapter(
                onSelected = { item ->
                    selectStatus(
                        requireContext(),
                        listOf("emoji", "battery", "emotion", "animation", "background")[index],
                        listOf(
                            "emojis",
                            "battery_styles",
                            "emotions",
                            "animations",
                            "backgrounds"
                        )[index],
                        item.id
                    )
                },
                cardHeight = 96,
                showFavorite = false,
                selection = {
                    selectedStatus(
                        requireContext(),
                        listOf("emoji", "battery", "emotion", "animation", "background")[index]
                    )?.optString("id")
                }).also {
                it.submit(
                    catalog().array(
                        listOf(
                            "emojis",
                            "battery_styles",
                            "emotions",
                            "animations",
                            "backgrounds"
                        )[index]
                    ).toCatalogEntries()
                )
            }
            if (gridId == R.id.status_battery_samples) {
                scrollToSelectedBattery(
                    samples,
                    catalog().array("battery_styles").toCatalogEntries()
                )
            }
            panel.findViewById<View>(moreId).setOnClickListener { navigate(destination) }
            val section = samples.parent as android.view.ViewGroup
            section.isVisible = when (gridId) {
                R.id.status_background_samples -> isShowBackgroud
                R.id.status_emoji_samples -> isShowEmojiIcon
                else -> true
            }
            val oldHeader = section.getChildAt(0)
            val title = (oldHeader as? TextView)?.text?.toString()?.substringBefore("  ")
                ?: listOf("Emoji", "Battery", "Emotion", "Animation", "Background")[index]
            if (oldHeader is TextView) section.removeView(oldHeader)
            val children = (0 until section.childCount).map { section.getChildAt(it) }
                .filter { it.tag != "collapse_header" }
            bindCollapseHeader(section as LinearLayout, title, children)

        }
        panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.status_background_grid)
            .apply {
                layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                adapter = CatalogAdapter(
                    onSelected = { item ->
                        selectStatus(
                            requireContext(),
                            "background",
                            "backgrounds",
                            item.id
                        )
                    },
                    cardHeight = 66,
                    cardWidth = 136,
                    showFavorite = false,
                    imageScaleType = ImageView.ScaleType.FIT_XY,
                    statusBackgroundStyle = true,
                    selection = {
                        selectedStatus(
                            requireContext(),
                            "background"
                        )?.optString("id")
                    }).also { it.submit(catalog().array("backgrounds").toCatalogEntries()) }
            }
        listOf(
            R.id.status_size_emoji,
            R.id.status_size_battery,
            R.id.status_size_emotion,
            R.id.status_size_animation,
            R.id.date_size
        ).forEach { id ->
            panel.findViewById<android.widget.SeekBar>(id).apply {
                progress = optionInt(
                    "size_$id", when (id) {
                        R.id.date_size -> 15
                        R.id.status_size_battery -> 35
                        else -> 40
                    }
                )
                val sizeLabel = ((parent as android.view.ViewGroup).getChildAt(
                    (parent as android.view.ViewGroup).indexOfChild(this) - 1
                ) as? TextView)
                val sizeTitle = sizeLabel?.text?.toString()?.substringBefore("  ") ?: "Size"
                sizeLabel?.text = "$sizeTitle    ${progress}dp"
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        bar: android.widget.SeekBar?,
                        value: Int,
                        fromUser: Boolean
                    ) {
                        sizeLabel?.text =
                            "$sizeTitle    ${value}dp"; if (fromUser) setOption("size_$id", value)
                        if (id == R.id.date_size) {
                            panel.findViewById<TextView>(R.id.date_size_label).text =
                                "Size                                               ${value}dp"; panel.findViewById<TextView>(
                                R.id.status_preview_value
                            ).textSize = value.coerceAtLeast(8).toFloat()
                        }
                    }

                    override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
                })
            }
        }
        val tab = when (screen) {
            ScreenIds.STATUS_MORE_EMOJI -> "Emoji"
            ScreenIds.STATUS_MORE_BATTERY_1 -> "Battery"
            ScreenIds.STATUS_MORE_BATTERY_2 -> "Emotion"
            ScreenIds.STATUS_MORE_BATTERY_3 -> "Animation"
            ScreenIds.STATUS_MORE_BATTERY_4 -> "Background"
            else -> viewModel.selectedTab.value ?: "Background"
        }
        if (category.layoutManager == null) category.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        category.isVisible =
            false // Original emoji/battery sources are flat lists, with no category field.
        category.adapter = CategoryAdapter { value ->
            prefs.edit().putString("status_category", value).apply()
            bindFeature()
        }.also { it.submit(emptyList(), "") }
        panel.findViewById<TextView>(R.id.status_picker_title).isVisible = false
        val favoriteSource = when (tab) {
            "Battery" -> "battery_styles"; "Background" -> "backgrounds"; "Animation" -> "animations"; "Emotion" -> "emotions"; else -> "emojis"
        }
        val values = when (tab) {
            "Battery" -> catalog().array("battery_styles").toCatalogEntries()
            "Background" -> catalog().array("backgrounds").toCatalogEntries()
            "Animation" -> catalog().array("animations").toCatalogEntries()
            "Emotion" -> catalog().array("emotions").toCatalogEntries()
            else -> catalog().array("emojis").toCatalogEntries()
        }
        val columns = if (tab == "Background") 2 else 3
        val manager = (grid.layoutManager as? GridLayoutManager)
            ?: GridLayoutManager(requireContext(), columns).also { grid.layoutManager = it }
        manager.spanCount = columns
        val reusableAdapter = (grid.adapter as? CatalogAdapter).takeIf { grid.tag == tab }
        grid.adapter = (reusableAdapter ?: CatalogAdapter(
            selection = {
                selectionDraft.getString("applied_${tab.lowercase()}")
                    ?.let { org.json.JSONObject(it).optString("id") }
                    ?: selectedStatus(
                        requireContext(),
                        tab.lowercase()
                    )?.optString("id")
            },
            cardHeight = if (tab == "Background") 90 else if (tab == "Animation") 174 else 109,
            showFavorite = tab !in listOf("Background", "Animation"),
            onSelected = { item ->
                val source = when (tab) {
                    "Battery" -> "battery_styles"; "Background" -> "backgrounds"; "Animation" -> "animations"; "Emotion" -> "emotions"; else -> "emojis"
                }
                if (isSelectionScreen) {
                    val selected = com.hd.emojibattery.runtime.AppliedContent.find(
                        requireContext(),
                        source,
                        item.id
                    )
                    if (selected != null) selectionDraft.putString(
                        "applied_${tab.lowercase()}",
                        selected.toString()
                    )
                } else selectStatus(
                    requireContext(),
                    tab.lowercase(),
                    source,
                    item.id
                )
                when (tab) {
                    "Battery" -> setOption("status_battery_style", item.id)
                    "Background" -> setOption("status_background", item.id)
                    "Animation" -> setOption("status_animation", item.id)
                    "Emotion" -> setOption("status_emotion", item.emoji)
                    else -> setOption("status_emoji", item.emoji)
                }
                panel.findViewById<TextView>(R.id.status_preview_emoji).text =
                    prefs.getString("status_emoji", item.emoji)
            },
            onFavorite = { item ->
                com.hd.emojibattery.runtime.Favorites.set(
                    prefs,
                    favoriteSource,
                    item.id,
                    !item.favorite
                )
                bindFeature()
            })).also {
            it.submit(values.map { item ->
                item.copy(
                    favorite = com.hd.emojibattery.runtime.Favorites.contains(
                        prefs,
                        favoriteSource,
                        item.id
                    )
                )
            })
        }
        grid.tag = tab
        if (tab == "Battery" && reusableAdapter == null) scrollToSelectedBattery(grid, values)
        if (screen == ScreenIds.STATUS_MORE_EMOJI || screen == ScreenIds.STATUS_MORE_BATTERY_1) {
            bindEmojiPickerDesign(panel, grid, favoriteSource, values)
        }
        panel.findViewById<TextView>(R.id.status_preview_emoji).text =
            prefs.getString("status_emoji", "😊")
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) {
            updateDatePreview(
                panel.findViewById(R.id.status_preview_value),
                if (date) "EEE, MMM dd" else "h:mm a", java.util.Locale.ENGLISH
            )
        }
        panel.findViewById<View>(R.id.status_start).setOnClickListener { applyStatusOverlay() }
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.status_enabled).apply {
            setOnCheckedChangeListener(null)
            isChecked = (requireActivity() as com.hd.emojibattery.MainActivity).isEmojiEnabled()
            setOnCheckedChangeListener { _, enabled ->
                if (enabled) applyStatusOverlay(leaveAfterApply = false) else {
                    (requireActivity() as com.hd.emojibattery.MainActivity).disableBatteryOverlay()
                    (requireActivity() as com.hd.emojibattery.MainActivity).updateEnableControls()
                }
            }
        }
        panel.findViewById<View>(R.id.status_theme_more)
            .setOnClickListener { navigate(ScreenIds.STATUS_MORE_BATTERY_4) }
        val themeTabs = listOf(R.id.status_tab_0, R.id.status_tab_1, R.id.status_tab_2)
        fun selectThemeTab(selected: Int) {
            selectedThemeTab = selected
            refreshThemeColors?.invoke()
            themeTabs.forEachIndexed { index, id ->
                panel.findViewById<TextView>(id).apply {
                    setBackgroundResource(if (index == selected) R.drawable.bg_primary_button else android.R.color.transparent)
                    setTextColor(if (index == selected) android.graphics.Color.WHITE else 0xff27205f.toInt())
                }
            }
        }
        selectThemeTab(selectedThemeTab ?: prefs.getInt("status_theme_tab", 0).coerceIn(0, 2))
        themeTabs.forEachIndexed { index, id ->
            panel.findViewById<View>(id).setOnClickListener { selectThemeTab(index) }
        }
        val colors = panel.findViewById<LinearLayout>(R.id.status_colors)
        fun colorKey() = when (selectedThemeTab ?: 0) {
            1 -> "status_percentage_color"
            2 -> "color_${R.id.date_colors}"
            else -> "status_color_0"
        }
        fun chooseColor(color: Int) {
            val edit = prefs.edit().putInt(colorKey(), color)
            if ((selectedThemeTab ?: 0) == 0) edit.remove("applied_background")
            edit.apply()
            refreshThemeColors?.invoke()
        }
        refreshThemeColors = {
            colors.removeAllViews()
            colors.addView(artwork(R.drawable.figma_status_custom_color, "Choose custom color").apply {
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(14) }
                setOnClickListener {
                    val key = colorKey()
                    StatusColorPicker.show(requireContext(), prefs.getInt(key, 0xff3b82f6.toInt())) { color ->
                        val palette = customColors(key).toMutableList().apply { remove(color); add(0, color) }
                        prefs.edit().putString("custom_palette_$key", palette.joinToString(",")).apply()
                        chooseColor(color)
                    }
                }
            })
            colors.addView(View(requireContext()).apply {
                setBackgroundColor(0xffdedbfa.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(1), dp(46)).apply { marginEnd = dp(17) }
            })
            val defaults = listOf(0xff3b82f6.toInt(), 0xff26215e.toInt(), 0xffedf0fa.toInt(),
                0xfff04f61.toInt(), 0xff2eb87d.toInt(), 0xff61b8ff.toInt())
            (customColors(colorKey()) + defaults).distinct().forEach { color ->
                colors.addView(View(requireContext()).apply {
                    contentDescription = "Select color #${String.format("%06X", color and 0xffffff)}"
                    isSelected = prefs.getInt(colorKey(), 0xff3b82f6.toInt()) == color
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(color)
                        if (prefs.getInt(colorKey(), 0xff3b82f6.toInt()) == color)
                            setStroke(dp(1).coerceAtLeast(1), 0xff9c4ff5.toInt())
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(20) }
                    setOnClickListener { chooseColor(color) }
                })
            }
        }
        refreshThemeColors?.invoke()
        panel.findViewById<View>(R.id.status_upload_photo).setOnClickListener {
            backgroundPicker.launch(arrayOf("image/*"))
        }
        panel.findViewById<View>(R.id.status_custom_photo).apply {
            isVisible = prefs.contains("status_custom_background")
            setOnClickListener {
                prefs.getString("status_custom_background", null)?.let { photo ->
                    prefs.edit().putString("applied_background", photo)
                        .putBoolean("status_part_background", true).apply()
                }
            }
        }
        panel.findViewById<View>(R.id.status_date)
            .setOnClickListener { navigate(ScreenIds.DATE_SETTINGS) }
        panel.findViewById<View>(R.id.status_apply).setOnClickListener { applyStatusOverlay() }
        if (isSelectionScreen) panel.findViewById<View>(R.id.status_apply).isVisible = false
        bindDisplayOptions(panel, !picker && !date)
        bindStatusArtwork(panel)
        bindRuntimePreview(panel)
        (requireActivity() as com.hd.emojibattery.MainActivity).updateEnableControls()
    }

    private fun customColors(key: String): List<Int> =
        prefs.getString("custom_palette_$key", "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 16f
        setTextColor(0xff27205f.toInt())
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.nunito_medium)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun column(padding: Int = 0) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    private fun artwork(resource: Int, description: String? = null) =
        ImageView(requireContext()).apply {
            setImageResource(resource)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = description
        }

    private fun bindCollapseHeader(host: LinearLayout, title: String, content: List<View>) {
        (0 until host.childCount).map { host.getChildAt(it) }
            .firstOrNull { it.tag == "collapse_header" }?.let { host.removeView(it) }
        val header = LinearLayout(requireContext()).apply {
            tag = "collapse_header"
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(48))
        }
        header.addView(label(title), LinearLayout.LayoutParams(0, -1, 1f))
        val arrow = artwork(R.drawable.ic_section_chevron)
        header.addView(arrow, LinearLayout.LayoutParams(dp(19), dp(11)))
        var expanded = content.firstOrNull()?.isVisible != false
        arrow.rotation = if (expanded) 0f else 180f
        header.contentDescription = "$title, " + if (expanded) "expanded" else "collapsed"
        header.setOnClickListener {
            expanded = !expanded
            // One transition moves following sections while fading the content.
            val root = view?.findViewById<ViewGroup>(R.id.feature_status_bar) ?: host
            android.transition.TransitionManager.beginDelayedTransition(
                root,
                android.transition.TransitionSet().apply {
                    ordering = android.transition.TransitionSet.ORDERING_TOGETHER
                    addTransition(android.transition.ChangeBounds())
                    addTransition(android.transition.Fade())
                    duration = 240
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                })
            content.forEach { it.isVisible = expanded }
            arrow.animate().cancel()
            arrow.animate().rotation(if (expanded) 0f else 180f).setDuration(240)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).start()
            header.contentDescription = "$title, " + if (expanded) "expanded" else "collapsed"
        }
        host.addView(header, 0)
    }

    private fun bindDisplayOptions(panel: View, visible: Boolean) {
        displayRefreshers.clear()
        val host = panel.findViewById<LinearLayout>(R.id.status_icon_panel)
        val parent = host.parent as LinearLayout
        val dateContent = panel.findViewById<View>(R.id.status_date_panel)
        if ((dateContent.parent as? View)?.tag == "date_section") {
            (dateContent.parent as ViewGroup).removeView(dateContent)
            parent.addView(dateContent, parent.indexOfChild(host))
        }
        host.findViewWithTag<View>("display_options_divider")?.let { host.removeView(it) }
        listOf("display_options", "appearance", "date_section").forEach { tag ->
            parent.findViewWithTag<View>(tag)?.let { (it.parent as ViewGroup).removeView(it) }
        }
        panel.findViewById<View>(R.id.status_date).isVisible = false
        if (!visible) return

        val iconContent = host.findViewById<View>(R.id.icon_editor_content)
        bindCollapseHeader(host, "Icon Color Customization", listOf(iconContent))
        val options = column().apply { tag = "display_options" }
        val rows = column(12).apply { setBackgroundResource(R.drawable.bg_status_outline) }
        val keys = listOf("emotion", "animation", "date")
        val icons =
            listOf(R.drawable.figma_emotion, R.drawable.figma_animation, R.drawable.figma_date)
        val destinations = listOf(
            ScreenIds.STATUS_MORE_BATTERY_2,
            ScreenIds.STATUS_MORE_BATTERY_3,
            ScreenIds.DATE_SETTINGS
        )
        keys.forEachIndexed { index, key ->
            val row = LinearLayout(requireContext()).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(artwork(icons[index]), LinearLayout.LayoutParams(dp(28), dp(28)))
            row.addView(
                label(key.replaceFirstChar { it.uppercase() }),
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(9) })
            row.addView(androidx.appcompat.widget.SwitchCompat(requireContext()).apply {
                contentDescription = "Enable $key"
                thumbDrawable = androidx.core.content.ContextCompat.getDrawable(
                    context,
                    R.drawable.toggle_thumb
                )
                trackDrawable = androidx.core.content.ContextCompat.getDrawable(
                    context,
                    R.drawable.toggle_track
                )
                thumbTintList = null
                trackTintList = null
                switchMinWidth = dp(58)
                isChecked = prefs.getBoolean("status_part_$key", true)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("status_part_$key", checked).apply()
                }
            }, LinearLayout.LayoutParams(dp(60), dp(48)))
            rows.addView(row)
            val selected = LinearLayout(requireContext()).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(0xffede9fd.toInt()); cornerRadius = dp(13).toFloat()
                }
                setPadding(dp(8), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(-1, dp(54))
                    .apply { marginStart = dp(40); topMargin = dp(5); bottomMargin = dp(12) }
                setOnClickListener { navigate(destinations[index]) }
            }
            selected.addView(
                label(
                    listOf(
                        "Selected Emoji",
                        "Selected Style",
                        "Selected Format"
                    )[index]
                ).apply { textSize = 14f },
                LinearLayout.LayoutParams(0, -1, 1f)
            )
            val value = LinearLayout(requireContext()).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                setBackgroundResource(R.drawable.bg_home_panel)
            }
            if (key == "date") {
                val dateLabel = label("").apply { textSize = 14f }
                value.addView(dateLabel)
                var dateJob: Job? = null
                val refresh: () -> Unit = {
                    dateJob?.cancel()
                    dateJob = viewLifecycleOwner.lifecycleScope.launch {
                        dateLabel.text = StatusDateFormatter.format(
                            prefs.getString("date_format", "EEE, MMM dd") ?: "EEE, MMM dd",
                            java.util.Locale.getDefault()
                        )
                    }
                }
                displayRefreshers.add(refresh)
                refresh()
            } else {
                val selectedArt = artwork(icons[index])
                value.addView(selectedArt, LinearLayout.LayoutParams(dp(24), dp(24)))
                var renderedItem: String? = null
                val refresh: () -> Unit = {
                    val item =
                        selectedStatus(requireContext(), key)
                    val identity = item?.toString().orEmpty()
                    if (identity != renderedItem) {
                        renderedItem = identity
                        com.hd.emojibattery.runtime.MediaAssets.clear(selectedArt)
                        selectedArt.setImageResource(icons[index])
                        val url = com.hd.emojibattery.runtime.AppliedContent.url(item)
                        if (url.isNotBlank()) com.hd.emojibattery.runtime.MediaAssets.load(
                            selectedArt,
                            url
                        )
                    }
                }
                displayRefreshers.add(refresh)
                refresh()
            }
            value.addView(
                artwork(R.drawable.ic_section_chevron).apply { rotation = 180f },
                LinearLayout.LayoutParams(dp(19), dp(11)).apply { marginStart = dp(8) })
            selected.addView(value, LinearLayout.LayoutParams(-2, dp(37)))
            rows.addView(selected)
        }
        options.addView(rows)
        bindCollapseHeader(options, "Display Options", listOf(rows))
        host.addView(options, 0)
        host.addView(View(requireContext()).apply {
            tag = "display_options_divider"
            setBackgroundColor(0xffdedbfa.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(15) }
        }, 1)

        val appearance = column().apply {
            tag = "appearance"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) }
        }
        appearance.addView(label("Appearance"), LinearLayout.LayoutParams(-1, dp(35)))
        val card = column(18).apply { setBackgroundResource(R.drawable.bg_home_panel) }
        listOf(
            Triple("status_height", "Height", 45),
            Triple("status_margin_left", "Left Margin", 12),
            Triple("status_margin_right", "Right Margin", 12)
        ).forEachIndexed { index, (key, title, default) ->
            val heading = LinearLayout(requireContext()).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(label(title), LinearLayout.LayoutParams(0, dp(34), 1f))
            val valueLabel = label("${prefs.getInt(key, default)}dp")
            heading.addView(valueLabel)
            card.addView(heading)
            card.addView(android.widget.SeekBar(requireContext()).apply {
                max = if (key == "status_height") 80 else 65
                progress = prefs.getInt(key, default)
                progressDrawable = androidx.core.content.ContextCompat.getDrawable(
                    context,
                    R.drawable.bg_slider_track
                )
                thumb = androidx.core.content.ContextCompat.getDrawable(
                    context,
                    R.drawable.bg_slider_thumb
                )
                progressTintList = null; progressBackgroundTintList = null; thumbTintList = null
                splitTrack = false
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(-1, dp(40))
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        bar: android.widget.SeekBar?,
                        value: Int,
                        user: Boolean
                    ) {
                        val size = if (key == "status_height") value.coerceAtLeast(24) else value
                        valueLabel.text = "${size}dp"
                        if (user) prefs.edit().putInt(key, size).apply()
                    }

                    override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
                })
            })
            if (index < 2) card.addView(View(requireContext()).apply {
                setBackgroundColor(0xffdedbfa.toInt())
                layoutParams = LinearLayout.LayoutParams(-1, dp(1))
                    .apply { topMargin = dp(12); bottomMargin = dp(11) }
            })
        }
        appearance.addView(card)
        parent.addView(appearance, parent.indexOfChild(host) + 1)

        (dateContent.parent as ViewGroup).removeView(dateContent)
        val dateSection = column(18).apply {
            tag = "date_section"
            setBackgroundResource(R.drawable.bg_home_panel)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) }
        }
        dateContent.isVisible = false
        dateContent.setPadding(dp(12), dp(16), dp(12), dp(16))
        dateContent.setBackgroundResource(R.drawable.bg_status_outline)
        dateSection.addView(dateContent)
        bindCollapseHeader(dateSection, "Date and time", listOf(dateContent))
        parent.addView(dateSection, parent.indexOfChild(appearance) + 1)
    }

    private fun bindStatusArtwork(panel: View) {
        fun icon(id: Int, width: Int, height: Int) =
            androidx.core.content.ContextCompat.getDrawable(requireContext(), id)?.apply {
                setBounds(
                    0,
                    0,
                    (width * resources.displayMetrics.density).toInt(),
                    (height * resources.displayMetrics.density).toInt()
                )
            }

        fun decorate(target: TextView, label: String, start: Int? = null, end: Int? = null) {
            target.text = label
            target.compoundDrawablePadding = (9 * resources.displayMetrics.density).toInt()
            target.setCompoundDrawablesRelative(
                start?.let { icon(it, 20, 20) },
                null,
                end?.let { icon(it, 22, 15) },
                null
            )
        }
        listOf(
            R.id.status_more_emoji,
            R.id.status_more_battery,
            R.id.status_more_emotion,
            R.id.status_more_animation,
            R.id.status_more_background
        ).forEach {
            decorate(
                panel.findViewById(it),
                "View more",
                R.drawable.ic_status_grid,
                R.drawable.ic_status_more
            )
            panel.findViewById<View>(it).setBackgroundResource(R.drawable.bg_status_selected)
        }
        decorate(
            panel.findViewById(R.id.status_theme_more),
            "View more",
            end = R.drawable.ic_status_more
        )

    }

    private fun setupDateAndIcons(panel: View) {
        val density = resources.displayMetrics.density
        val preview = panel.findViewById<TextView>(R.id.status_preview_value)
        fun options(id: Int, labels: List<String>, key: String, selected: (Int) -> Unit) {
            val group = panel.findViewById<android.widget.GridLayout>(id)
            group.removeAllViews()
            fun render() {
                for (i in 0 until group.childCount) group.getChildAt(i).setBackgroundResource(
                    if (i == optionInt(
                            key,
                            0
                        )
                    ) R.drawable.bg_plan_selected else R.drawable.bg_catalog_card
                )
            }
            labels.forEachIndexed { index, label ->
                group.addView(TextView(requireContext()).apply {
                    text = label
                    textSize = if (id == R.id.date_fonts) 14f else 13f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xff27205f.toInt())
                    layoutParams = android.widget.GridLayout.LayoutParams().apply {
                        width = 0; height = (48 * density).toInt()
                        columnSpec =
                            android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                        setMargins(
                            (4 * density).toInt(),
                            (4 * density).toInt(),
                            (4 * density).toInt(),
                            (4 * density).toInt()
                        )
                    }
                    setOnClickListener { setOption(key, index); selected(index); render() }
                })
            }
            render()
        }

        val formats = listOf("EEE, MMM dd", "EEE, dd", "MMM dd", "EEEE")
        options(
            R.id.date_format_options,
            listOf("Fri, Oct 23", "Fri, 23", "Oct 23", "Friday"),
            "date_format_index"
        ) {
            setOption("date_format", formats[it])
            updateDatePreview(preview, formats[it], java.util.Locale.getDefault())
        }
        options(
            R.id.date_fonts,
            listOf("Roboto", "Baloo", "Cherry B..", "Inter", "Belgrano", "Basic", "Carter ..."),
            "date_font_index"
        ) {
            preview.typeface = android.graphics.Typeface.create(
                if (it == 4) "serif" else "sans-serif",
                if (it == 6) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
        }
        val iconTabs = listOf(R.id.icon_signal, R.id.icon_data, R.id.icon_charge)
        fun iconTab(index: Int) {
            selectedIconTab = index
            iconTabs.forEachIndexed { i, id ->
                panel.findViewById<View>(id).let { target ->
                    val tab = (target.parent as? View)?.takeIf { it.tag == "icon_tab" } ?: target
                    target.background = null
                    tab.background = if (i == index) android.graphics.drawable.LayerDrawable(
                        arrayOf(
                            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
                            androidx.core.content.ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.bg_primary_button
                            )!!
                        )
                    ).apply {
                        setLayerHeight(1, dp(3)); setLayerGravity(
                        1,
                        Gravity.BOTTOM
                    )
                    } else null
                }
            }
            val group = panel.findViewById<android.widget.GridLayout>(R.id.icon_styles)
            group.removeAllViews()
            // Signal has color and size controls; only Data and Charge have style grids in Figma.
            group.isVisible = index != 0
            panel.findViewById<View>(R.id.icon_style_label).isVisible = index != 0
            val styles = if (index == 1) intArrayOf(
                R.drawable.figma_data_style_0,
                R.drawable.figma_data_style_1,
                R.drawable.figma_data_style_2,
                R.drawable.figma_data_style_3,
                R.drawable.figma_data_style_4,
                R.drawable.figma_data_style_5,
                R.drawable.figma_data_style_6,
                R.drawable.figma_data_style_7
            ) else intArrayOf(
                R.drawable.figma_charge_style_0,
                R.drawable.figma_charge_style_1,
                R.drawable.figma_charge_style_2,
                R.drawable.figma_charge_style_3,
                R.drawable.figma_charge_style_4,
                R.drawable.figma_charge_style_5,
                R.drawable.figma_charge_style_6,
                R.drawable.figma_charge_style_7
            )
            if (index != 0) styles.forEachIndexed { i, drawable ->
                group.addView(
                    artwork(
                        drawable,
                        if (index == 1) "${i + 2}G" else "Charge style ${i + 1}"
                    ).apply {
                        setBackgroundResource(R.drawable.bg_status_style)
                        isSelected = prefs.getInt("icon_style_$index", 0) == i
                        val inset = if (index == 1) 16 else 10
                        setPadding(dp(inset), dp(inset), dp(inset), dp(inset))
                        layoutParams = android.widget.GridLayout.LayoutParams().apply {
                            width = 0; height = dp(57)
                            columnSpec = android.widget.GridLayout.spec(i % 4, 1f)
                            rowSpec = android.widget.GridLayout.spec(i / 4)
                            setMargins(dp(7), dp(7), dp(7), dp(8))
                        }
                        setOnClickListener {
                            val selected = i
                            prefs.edit().putInt("icon_style_$index", selected).apply()
                            for (child in 0 until group.childCount) group.getChildAt(child).isSelected =
                                child == selected
                        }
                    })
            }
        }

        iconTabs.forEachIndexed { index, id ->
            val text = panel.findViewById<TextView>(id)
            val tabHost = text.parent as ViewGroup
            if (tabHost.tag != "icon_tab") {
                val position = tabHost.indexOfChild(text)
                val params = text.layoutParams
                tabHost.removeView(text)
                val row = LinearLayout(requireContext()).apply {
                    tag = "icon_tab"
                    gravity = Gravity.CENTER
                    orientation = LinearLayout.HORIZONTAL
                }
                row.addView(
                    artwork(
                        listOf(
                            R.drawable.figma_signal,
                            R.drawable.figma_data,
                            R.drawable.figma_charge
                        )[index]
                    ),
                    LinearLayout.LayoutParams(dp(28), dp(28))
                )
                text.text = listOf("Signal", "Data", "Charge")[index]
                text.textSize = 16f
                row.addView(text, LinearLayout.LayoutParams(-2, -1).apply { marginStart = dp(8) })
                tabHost.addView(row, position, params)
                row.setOnClickListener { iconTab(index) }
            }
            text.setOnClickListener { iconTab(index) }
        }
        iconTab(selectedIconTab ?: when (screen) {
            ScreenIds.STATUS_SCROLL_3 -> 1
            ScreenIds.STATUS_SCROLL_4, ScreenIds.STATUS_SCROLL_6 -> 2
            else -> prefs.getInt("icon_tab", 0).coerceIn(0, 2)
        })
        listOf(R.id.date_colors, R.id.icon_colors).forEach { id ->
            fun colorKey() = if (id == R.id.icon_colors) {
                "status_icon_color_${selectedIconTab ?: 0}"
            } else "color_$id"
            val row = panel.findViewById<android.widget.LinearLayout>(id)
            row.removeAllViews()
            if (id == R.id.icon_colors) {
                row.addView(
                    artwork(
                        R.drawable.figma_color_picker,
                        "Choose custom icon color"
                    ).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(14) }
                        setOnClickListener {
                            val input = android.widget.EditText(requireContext()).apply {
                                setSingleLine()
                                hint = "#RRGGBB"
                                setText(
                                    String.format(
                                        "#%06X",
                                        prefs.getInt(colorKey(), 0xff26215e.toInt()) and 0xffffff
                                    )
                                )
                            }
                            val dialog =
                                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    .setTitle("Icon color").setView(input)
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Apply", null).create()
                            dialog.setOnShowListener {
                                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                                    .setOnClickListener {
                                        val color = runCatching {
                                            android.graphics.Color.parseColor(
                                                input.text.toString().trim()
                                            )
                                        }.getOrNull()
                                        if (color == null) input.error =
                                            "Enter a color such as #3B82F6"
                                        else {
                                            prefs.edit().putInt(colorKey(), color)
                                                .apply(); dialog.dismiss()
                                        }
                                    }
                            }
                            dialog.show()
                        }
                    })
                row.addView(View(requireContext()).apply {
                    setBackgroundColor(0xffdedbfa.toInt())
                    layoutParams =
                        LinearLayout.LayoutParams(dp(1), dp(46)).apply { marginEnd = dp(17) }
                })
            }
            listOf(
                0xff3b82f6,
                0xff26215e,
                0xffedf0fa,
                0xfff04f61,
                0xff2eb87d,
                0xff61b8ff
            ).forEach { color ->
                row.addView(View(requireContext()).apply {
                    contentDescription = "Select color ${color.toString(16)}"
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape =
                            android.graphics.drawable.GradientDrawable.OVAL; setColor(color.toInt())
                    }
                    layoutParams = android.widget.LinearLayout.LayoutParams(dp(40), dp(40))
                        .apply { marginEnd = dp(20) }
                    setOnClickListener {
                        setOption(
                            colorKey(),
                            color.toInt()
                        ); preview.setTextColor(color.toInt())
                    }
                })
            }
        }
        panel.findViewById<android.widget.SeekBar>(R.id.icon_size).apply {
            progress = prefs.getInt("icon_size", 15)
            panel.findViewById<TextView>(R.id.icon_size_label).text = "${progress}dp"
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: android.widget.SeekBar?,
                    value: Int,
                    user: Boolean
                ) {
                    panel.findViewById<TextView>(R.id.icon_size_label).text =
                        "${value}dp"; if (user) prefs.edit().putInt("icon_size", value).apply()
                }

                override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
            })
        }
    }

    private fun bindEmojiPickerDesign(
        panel: View,
        grid: androidx.recyclerview.widget.RecyclerView,
        source: String,
        values: List<CatalogEntry>
    ) {
        binding.featureTitle.text = "Emoji Icon"
        binding.featureTitle.textSize = 22f
        binding.featureContent.setPadding(dp(16), dp(10), dp(15), dp(14))
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.status_enable).apply {
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { height = dp(56); bottomMargin = dp(12) }
            setPadding(dp(19), 0, dp(19), 0)
            textSize = 16f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.nunito_medium)
            background = GradientDrawable().apply {
                cornerRadius = dp(65).toFloat()
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(dp(1).coerceAtLeast(1), 0x54a2a2a2)
            }
            setThumbResource(R.drawable.toggle_thumb)
            setTrackResource(R.drawable.toggle_track)
            thumbTintList = null
            trackTintList = null
            switchMinWidth = dp(58)
            compoundDrawablePadding = dp(12)
        }
        val host = panel as LinearLayout
        host.findViewWithTag<View>("emoji_picker_categories")?.let { host.removeView(it) }
        val row = LinearLayout(requireContext()).apply {
            tag = "emoji_picker_categories"
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(34)).apply { bottomMargin = dp(8) }
        }
        row.addView(artwork(R.drawable.figma_status_picker_hot, "Hot"), LinearLayout.LayoutParams(dp(63), dp(34)))
        val scroll = android.widget.HorizontalScrollView(requireContext()).apply { isHorizontalScrollBarEnabled = false }
        val categories = LinearLayout(requireContext())
        val names = listOf("Naruto", "BTS", "One Piece", "Halloween", "Undertale", "Pokemon", "Mario", "Sonic", "LOL", "Character", "Marvel", "Attach on Titan", "SpongerBob", "Rick & Morty", "Anime Star", "Stitch", "Bleach", "Geshin Impact", "Horror and Creppypasta", "Loopy")
        val selected = prefs.getString("status_picker_category_$source", names.first()) ?: names.first()
        fun showCategory(name: String, alphabetical: Boolean = false) {
            val raw = catalog().array(source)
            val matches = values.filter { entry ->
                (0 until (raw?.length() ?: 0)).any { index ->
                    val item = raw?.optJSONObject(index)
                    item != null && item.optString("id") == entry.id && item.optString("category").equals(name, true)
                }
            }
            // Legacy battery sources have no category metadata; retain their available artwork.
            val items = matches.ifEmpty { values }
            (grid.adapter as? CatalogAdapter)?.submit((if (alphabetical) items.sortedBy { it.name.lowercase() } else items).map {
                it.copy(favorite = com.hd.emojibattery.runtime.Favorites.contains(prefs, source, it.id))
            })
            for (i in 0 until categories.childCount) {
                val chip = categories.getChildAt(i) as TextView
                chip.background = if (chip.text == name) android.graphics.drawable.LayerDrawable(arrayOf(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
                    androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_primary_button)!!
                )).apply { setLayerHeight(1, dp(2)); setLayerGravity(1, Gravity.BOTTOM) } else null
            }
        }
        names.forEach { name ->
            categories.addView(TextView(requireContext()).apply {
                text = name; textSize = 14f; gravity = Gravity.CENTER
                setTextColor(0xff8880c1.toInt())
                setPadding(0, 0, 0, dp(4))
                layoutParams = LinearLayout.LayoutParams(-2, dp(25)).apply { marginEnd = dp(13) }
                setOnClickListener {
                    prefs.edit().putString("status_picker_category_$source", name).apply()
                    showCategory(name)
                }
            })
        }
        scroll.addView(categories)
        row.addView(scroll, LinearLayout.LayoutParams(0, dp(25), 1f).apply { marginStart = dp(7); marginEnd = dp(14) })
        row.addView(artwork(R.drawable.figma_status_picker_sort, "Sort emoji icons").apply {
            setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Sort emoji icons")
                    .setItems(arrayOf("Default", "Name A–Z")) { _, which ->
                        showCategory(prefs.getString("status_picker_category_$source", names.first()) ?: names.first(), which == 1)
                    }.show()
            }
        }, LinearLayout.LayoutParams(dp(25), dp(25)))
        host.addView(row, host.indexOfChild(grid))
        if (grid.itemDecorationCount == 0) grid.addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
                outRect.bottom = dp(6)
            }
        })
        showCategory(selected)
    }

    fun setStatusEnabled(enabled: Boolean) {
        if (enabled) applyStatusOverlay(leaveAfterApply = false) else {
            (requireActivity() as com.hd.emojibattery.MainActivity).disableBatteryOverlay()
            (requireActivity() as com.hd.emojibattery.MainActivity).updateEnableControls()
        }
    }

    private fun applyStatusOverlay(leaveAfterApply: Boolean = true) {
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) {
            editorSession.commit(requireContext().getSharedPreferences("emoji_battery_preview", 0)) {
                requireContext().getSharedPreferences("emoji_battery_preview", 0).edit()
                    .putBoolean("status_overlay_enabled", true).apply()
                true
            }
            (requireActivity() as com.hd.emojibattery.MainActivity).updateEnableControls()
            toast("Preview applied")
            if (leaveAfterApply) finishFeature()
            return
        }
        requireAccessibilityUsageAccepted {
            val committed = com.hd.emojibattery.runtime.AppliedContent.prefs(requireContext())
            if (editorSession.commit(committed) { com.hd.emojibattery.runtime.FeatureApply.status(requireActivity()) }) {
                toast("Status bar applied")
                if (leaveAfterApply) finishFeature()
            }
        }
    }
}
