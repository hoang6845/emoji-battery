package com.hd.emojibattery.ui.stickers

import com.hd.emojibattery.runtime.Favorites
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.app.Dialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.common.CatalogAdapter
import com.hd.emojibattery.ui.common.CategoryAdapter
import com.hd.emojibattery.ui.common.categoryDisplayName
import com.hd.emojibattery.ui.common.toCatalogEntries
import com.hd.emojibattery.ui.navigation.ScreenIds

open class StickerFragment<VM : StickerViewModel> : BaseFeatureFragment<VM>() {
    private var categories: List<String> = listOf("All")
    private var adapter: CatalogAdapter? = null
    private var selectedAdapter: CatalogAdapter? = null
    private var gestureItem: org.json.JSONObject? = null
    private var adjustmentChanged: ((org.json.JSONObject) -> Unit)? = null
    private var watcher: TextWatcher? = null
    private var previewId: String? = null
    private var selectedCatalogId: String? = null
    private var previewOverlay: android.widget.FrameLayout? = null
    private var removePreviewPreDrawListener: (() -> Unit)? = null
    private var tutorialOverlay: StickerTutorialOverlay? = null
    private var previewHandles = emptyList<View>()
    private var adjustmentDialog: Dialog? = null
    private var returningSelection = false
    private var draftStickers = org.json.JSONArray()
    private var pendingSticker: org.json.JSONObject? = null
    private var applyJob: kotlinx.coroutines.Job? = null
    private var searchItems = emptyMap<String, Pair<String, com.hd.emojibattery.ui.common.CatalogEntry>>()
    override fun hasUnsavedChanges(): Boolean = isEditor() &&
        (pendingSticker != null || draftStickers.toString() != prefs.getString("applied_stickers", "[]"))
    private fun isEditor() = screen in setOf(ScreenIds.ADD_STICKER, ScreenIds.ADD_STICKER_STATE_2,
        ScreenIds.STICKER_EDIT, ScreenIds.STICKER_DETAIL, ScreenIds.STICKER_EXIT)

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!isEditor()) return
        // These actions remain reachable while a movable preview or its guide is shown.
        binding.featureBack.setOnClickListener { exitEditor() }
        binding.featureFooter.isVisible = true
        binding.featureFooterAction.text = "Apply"
        binding.featureFooterAction.setOnClickListener {
            dismissTutorial()
            requireAccessibilityUsageAccepted { applySelectedSticker() }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = exitEditor()
            })
    }

    private fun dismissTutorial() {
        tutorialOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        tutorialOverlay = null
    }

    private fun exitEditor() {
        dismissTutorial()
        if (hasUnsavedChanges()) showDiscardChanges { finishFeature() } else finishFeature()
    }

    private fun touchesEditorAction(event: android.view.MotionEvent): Boolean {
        if (_binding == null) return false
        val bounds = android.graphics.Rect()
        val actions = adjustmentDialog?.takeIf { it.isShowing }?.let { dialog ->
            listOfNotNull(dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet))
        } ?: listOf(binding.featureToolbar, binding.featureFooter)
        return actions.any { action ->
            action.isVisible && action.getGlobalVisibleRect(bounds) &&
                bounds.contains(event.rawX.toInt(), event.rawY.toInt())
        }
    }

    private fun addSticker(catalogId: String) {
        if (draftStickers.length() >= 5) { toast("You can add up to 5 stickers"); return }
        val source = com.hd.emojibattery.runtime.AppliedContent.find(requireContext(), "stickers", catalogId)
            ?: return
        addStickerCopy(source)
    }

    private fun addStickerCopy(source: org.json.JSONObject) {
        if (draftStickers.length() >= 5) { toast("You can add up to 5 stickers"); return }
        val item = org.json.JSONObject(source.toString())
        val instance = java.util.UUID.randomUUID().toString()
        val metrics = resources.displayMetrics
        val pixels = dp(140)
        val preview = _binding?.root?.findViewById<View>(R.id.sticker_selection)
        val location = IntArray(2)
        preview?.getLocationOnScreen(location)
        val initialY = if (preview != null && preview.height > 0)
            location[1] + (preview.height - pixels) / 2f - dp(80) else dp(100).toFloat()
        item.put("instance_id", instance).put("overlay_size_px", pixels).put("overlay_rotation", 0)
            .put("overlay_x", (metrics.widthPixels - pixels) / 2f)
            .put("overlay_y", initialY)
        // Catalog taps replace the pending preview; only Apply adds it to My sticker.
        pendingSticker = item
        previewId = instance
        selectedCatalogId = source.optString("id")
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        previewId = savedInstanceState?.getString("sticker_draft_id")
        selectedCatalogId = savedInstanceState?.getString("sticker_catalog_id")
        pendingSticker = savedInstanceState?.getString("sticker_pending_item")?.let { value ->
            runCatching { org.json.JSONObject(value) }.getOrNull()
        }
        draftStickers = runCatching { org.json.JSONArray(savedInstanceState?.getString("sticker_draft_items")
            ?: prefs.getString("applied_stickers", "[]")) }.getOrDefault(org.json.JSONArray())
        if (savedInstanceState == null && isEditor()) arguments?.getString("content_id")?.let(::addSticker)
        if (screen in setOf(ScreenIds.ADD_STICKER, ScreenIds.ADD_STICKER_STATE_2, ScreenIds.STICKER_EDIT, ScreenIds.STICKER_DETAIL, ScreenIds.STICKER_EXIT)) {
            parentFragmentManager.setFragmentResultListener("sticker_picker_result", this) { _, result ->
                val id = result.getString("content_id") ?: return@setFragmentResultListener
                addSticker(id)
                prefs.edit().putString("sticker_category", "All").apply()
                viewModel.setQuery("")
                if (_binding != null) {
                    renderPreview()
                    adapter?.refreshSelection()
                    selectedAdapter?.refreshSelection()
                    showStickerTutorialIfNeeded()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        outState.putString("sticker_draft_id", previewId)
        outState.putString("sticker_catalog_id", selectedCatalogId)
        outState.putString("sticker_draft_items", draftStickers.toString())
        outState.putString("sticker_pending_item", pendingSticker?.toString())
        super.onSaveInstanceState(outState)
    }

    private fun appliedStickers(): org.json.JSONArray =
        draftStickers

    private fun removeAppliedSticker(id: String) {
        val previous = appliedStickers()
        val remaining = org.json.JSONArray()
        for (i in 0 until previous.length()) previous.optJSONObject(i)?.let {
            if (com.hd.emojibattery.runtime.StickerState.instanceId(it) != id) remaining.put(it)
        }
        draftStickers = remaining
        // Remove only this committed instance immediately; other editor changes
        // remain drafts until Apply.
        val committed = runCatching { org.json.JSONArray(prefs.getString("applied_stickers", "[]")) }
            .getOrDefault(org.json.JSONArray())
        val kept = org.json.JSONArray()
        for (index in 0 until committed.length()) committed.optJSONObject(index)?.let { saved ->
            if (com.hd.emojibattery.runtime.StickerState.instanceId(saved) != id) kept.put(saved)
        }
        if (kept.length() != committed.length()) {
            prefs.edit().putString("applied_stickers", kept.toString()).apply {
                if (kept.length() == 0) putBoolean("stickers_applied", false).putBoolean("stickers_enabled", false)
            }.apply()
            if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE) {
                com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())
            }
        }
        if (previewId == id) previewId = remaining.optJSONObject(remaining.length() - 1)?.let(com.hd.emojibattery.runtime.StickerState::instanceId)
        bindFeature()
    }

    override fun bindFeature() {
        val content = catalog().collection("stickers")
        categories = com.hd.emojibattery.runtime.CatalogGroups.names(content, "stickers")
        val panel = view<View>(R.id.feature_stickers)
        val library = screen in setOf(ScreenIds.FAVORITES, ScreenIds.FAVORITES_STATE_2, ScreenIds.FAVORITES_DELETE)
        val editing = screen in setOf(ScreenIds.ADD_STICKER, ScreenIds.ADD_STICKER_STATE_2, ScreenIds.STICKER_EDIT, ScreenIds.STICKER_DETAIL, ScreenIds.STICKER_EXIT)
        val more = screen == ScreenIds.STICKER_MORE
        panel.findViewById<View>(R.id.sticker_enable_row).isVisible = more
        if (more) {
            binding.featureContent.setPadding(dp(16), dp(10), dp(16), dp(14))
            binding.featureTitle.textSize = 22f
            binding.featureTitle.setTextColor(android.graphics.Color.rgb(39, 32, 95))
            val gallery = panel.findViewById<View>(R.id.sticker_gallery)
            gallery.background = null
            gallery.setPadding(0, 0, 0, 0)
            panel.findViewById<View>(R.id.sticker_category_row).layoutParams.height = dp(46)
            panel.findViewById<android.widget.ImageView>(R.id.sticker_hot).setImageResource(R.drawable.figma_sticker_catalog_hot)
            panel.findViewById<android.widget.ImageView>(R.id.sticker_sort).setImageResource(R.drawable.figma_sticker_catalog_sort)
            panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.sticker_enable).apply {
                setOnCheckedChangeListener(null)
                thumbDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.toggle_thumb)
                trackDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.toggle_track)
                thumbTintList = null
                trackTintList = null
                switchMinWidth = dp(58)
                isChecked = prefs.getBoolean("stickers_enabled", false)
                setOnCheckedChangeListener { _, enabled -> setStickersEnabled(enabled) }
            }
        }
        panel.findViewById<View>(R.id.sticker_start).setOnClickListener { setStickersEnabled(true) }
        if (editing && !(requireActivity() as com.hd.emojibattery.MainActivity).hasEnabledPermissionsOnce()) {
            panel.findViewById<View>(R.id.sticker_enable_row).isVisible = true
        }
        if (editing) configureEditorScrolling(panel)
        panel.findViewById<View>(R.id.sticker_selected_panel).isVisible = editing && draftStickers.length() > 0
        panel.findViewById<View>(R.id.sticker_empty_hint).isVisible = editing && previewId == null
        if (editing) {
            binding.featureContent.setPadding(0, 0, 0, 0)
            binding.featureFooter.setBackgroundColor(android.graphics.Color.WHITE)
            binding.featureFooter.setPadding(dp(26), dp(14), dp(26), dp(15))
            binding.featureFooterAction.layoutParams.height = dp(57)
            binding.featureFooterAction.textSize = 18f
            binding.featureBack.setImageResource(R.drawable.figma_sticker_back)
            binding.featureBack.setPadding(dp(16), dp(12), dp(16), dp(12))
            binding.featureBack.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            binding.featureTitle.text = ""
            val toolbar = binding.featureToolbar
            val last = toolbar.getChildAt(toolbar.childCount - 1)
            val toggle = (last as? androidx.appcompat.widget.SwitchCompat) ?: androidx.appcompat.widget.SwitchCompat(requireContext()).also {
                last.isVisible = false
                toolbar.addView(it, android.widget.LinearLayout.LayoutParams((60 * resources.displayMetrics.density).toInt(), (44 * resources.displayMetrics.density).toInt()))
            }
            toggle.apply {
                isVisible = (requireActivity() as com.hd.emojibattery.MainActivity).hasEnabledPermissionsOnce()
                setOnCheckedChangeListener(null)
                thumbDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.toggle_thumb)
                trackDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.toggle_track)
                thumbTintList = null
                trackTintList = null
                switchMinWidth = dp(58)
                setPadding(0, 0, 0, 0)
                isChecked = prefs.getBoolean("stickers_enabled", false)
                contentDescription = "Enable stickers"
                setOnCheckedChangeListener { _, enabled -> setStickersEnabled(enabled) }
            }

        }
        panel.findViewById<View>(R.id.sticker_banner).isVisible = library || screen == ScreenIds.SEARCH
        panel.findViewById<View>(R.id.sticker_banner_back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        panel.findViewById<View>(R.id.sticker_search_row).isVisible = screen == ScreenIds.SEARCH
        if (library || screen == ScreenIds.SEARCH) {
            binding.featureToolbar.isVisible = false
            binding.featureContent.setPadding(0, 0, 0, 0)
        }
        panel.findViewById<View>(R.id.sticker_library_tabs).isVisible = library
        panel.findViewById<View>(R.id.sticker_selection).isVisible = editing
        panel.findViewById<View>(R.id.sticker_apply).isVisible = false
        panel.findViewById<View>(R.id.sticker_category_row).isVisible = !library && screen != ScreenIds.SEARCH
        panel.findViewById<View>(R.id.sticker_add_heading).isVisible = editing
        panel.findViewById<View>(R.id.sticker_hot).setOnClickListener { prefs.edit().putString("sticker_category", "All").apply(); bindFeature() }
        panel.findViewById<View>(R.id.sticker_sort).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Choose category")
                .setItems(categories.map(::categoryDisplayName).toTypedArray()) { _, index -> prefs.edit().putString("sticker_category", categories[index]).apply(); bindFeature() }
                .show().also { trackDialog(it) }
        }
        panel.findViewById<View>(R.id.search_suggestions).isVisible = screen == ScreenIds.SEARCH
        panel.findViewById<View>(R.id.sticker_search).isVisible = screen == ScreenIds.SEARCH
        panel.findViewById<View>(R.id.sticker_add).isVisible = editing
        if (screen == ScreenIds.SEARCH) {
            panel.findViewById<android.widget.ImageView>(R.id.sticker_banner_image).setImageResource(R.drawable.search_banner)
            panel.findViewById<android.widget.TextView>(R.id.sticker_banner_title).text = "Find your style"
            panel.findViewById<android.widget.TextView>(R.id.sticker_banner_subtitle).text = "Search battery styles, stickers and pets"
        }
        panel.findViewById<View>(R.id.sticker_explore).setOnClickListener { navigate(ScreenIds.STICKER_MORE) }
        panel.findViewById<View>(R.id.sticker_view_more).setOnClickListener {
            prefs.edit().putString("sticker_category", "All").apply()
            navigate(ScreenIds.STICKER_MORE, android.os.Bundle().apply {
                putString("content_id", previewId)
                putBoolean("return_sticker_selection", true)
            })
        }
        val applied = (0 until draftStickers.length()).mapNotNull { index ->
            draftStickers.optJSONObject(index)?.let { item ->
                org.json.JSONArray().put(item).toCatalogEntries().firstOrNull()?.copy(id = com.hd.emojibattery.runtime.StickerState.instanceId(item))
            }
        }
        (previewOverlay?.findViewById<View>(R.id.sticker_remove) ?: panel.findViewById<View>(R.id.sticker_remove)).isVisible = false
        panel.findViewById<View>(R.id.sticker_empty_hint).isVisible = editing && previewId == null
        panel.findViewById<android.widget.TextView>(R.id.sticker_selection_count).text = "My sticker (${applied.size}/5)"
        panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_selected_items).apply {
            if (layoutManager == null) layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            itemAnimator = null
            if (selectedAdapter == null) selectedAdapter = CatalogAdapter(stickerStyle = true, cardWidth = 120, removeAction = true, showEditBadge = true, selection = { previewId },
                onSelected = { item ->
                    pendingSticker = null
                    previewId = item.id
                    selectedCatalogId = selectedSticker(item.id)?.optString("id")
                    renderPreview()
                    this@StickerFragment.adapter?.refreshSelection()
                    (panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_selected_items).adapter as? CatalogAdapter)?.refreshSelection()

                },
                onEdit = { item -> requireAccessibilityUsageAccepted { showAdjustment(item.id) } },
                onFavorite = { item -> removeAppliedSticker(item.id) })
            if (adapter !== selectedAdapter) adapter = selectedAdapter
            selectedAdapter?.submit(applied)
        }
        previewView<View>(R.id.sticker_canvas).apply {
            contentDescription = "Selected sticker preview"
            setOnClickListener(null)
            isClickable = false
            androidx.core.view.ViewCompat.removeAccessibilityAction(
                this, androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK
            )
        }
        if (screen == ScreenIds.STICKER_EXIT && (com.hd.emojibattery.BuildConfig.UI_TEST_MODE || hasUnsavedChanges())) showDiscardChanges { finishFeature() }
        if (screen == ScreenIds.SORT) {
            val themeCategories = com.hd.emojibattery.runtime.CatalogGroups.names(catalog().collection("themes"), "themes")
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Choose category").setItems(themeCategories.toTypedArray()) { _, index ->
                    prefs.edit().putString("home_category", themeCategories[index]).apply(); navigate(ScreenIds.HOME)
                }.show().also { trackDialog(it) }
        }
        val search = panel.findViewById<EditText>(R.id.sticker_search)
        val searchSuggestions = listOf("Anime", "Cute", "Kpop", "Heart", "Loopy", "Heart")
            .distinctBy { title -> title.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT) }
        panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.search_quick_tags).apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = CategoryAdapter(pillGrid = true) { search.setText(it) }.also { it.submit(searchSuggestions, "") }
        }
        search.hint = if (screen == ScreenIds.SEARCH) "Search" else "Search stickers"
        watcher?.let(search::removeTextChangedListener)
        search.setText(viewModel.query.value.orEmpty().takeIf { screen == ScreenIds.SEARCH }.orEmpty())
        watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { viewModel.setQuery(s?.toString().orEmpty()); updateCatalog() }
            override fun afterTextChanged(s: Editable?) = Unit
        }.also(search::addTextChangedListener)
        panel.findViewById<View>(R.id.sticker_search_clear).setOnClickListener {
            search.text.clear()
            search.requestFocus()
        }
        panel.findViewById<View>(R.id.sticker_add).setOnClickListener { openImagePicker() }
        panel.findViewById<View>(R.id.sticker_apply).setOnClickListener {
            requireAccessibilityUsageAccepted {
                applySelectedSticker()
            }
        }
        val categoriesView = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_categories)
        if (categoriesView.layoutManager == null) categoriesView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        categoriesView.adapter = ((categoriesView.adapter as? CategoryAdapter) ?: CategoryAdapter(stickerStyle = true) { value -> prefs.edit().putString("sticker_category", value).apply(); updateCatalog() })
            .also { it.submit(categories, prefs.getString("sticker_category", "All").takeIf { it in categories } ?: "All") }
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_grid)
        if (grid.layoutManager == null) grid.layoutManager = GridLayoutManager(requireContext(), 3)
        if (grid.getTag(R.id.sticker_selected_items) != true) {
            grid.setTag(R.id.sticker_selected_items, true)
            grid.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                // The selected row and catalog use the same card frame, including
                // on narrow screens where a fixed 120dp card would be wider.
                val columnWidth = (right - left - grid.paddingLeft - grid.paddingRight) / 3
                selectedAdapter?.updateHorizontalCardWidth(
                    ((columnWidth - dp(8)) / resources.displayMetrics.density).toInt()
                )
            }
        }
        grid.itemAnimator = null
        if (adapter == null) adapter = CatalogAdapter(stickerStyle = true, selection = { selectedCatalogId }, onSelected = { item ->
            if (screen == ScreenIds.SEARCH) {
                val (source, entry) = searchItems[item.id] ?: return@CatalogAdapter
                val args = android.os.Bundle().apply { putString("content_id", entry.id); putString("content_source", source) }
                when (source) {
                    "pets" -> (requireActivity() as com.hd.emojibattery.MainActivity).openScreen(ScreenIds.PET, false,
                        args.apply { putString("apply_pet_id", entry.id) })
                    "battery_styles" -> navigate(ScreenIds.BATTERY_STYLE_PREVIEW, args)
                    else -> navigate(ScreenIds.ADD_STICKER, args)
                }
                return@CatalogAdapter
            }
            if (returningSelection) return@CatalogAdapter
            val asset = com.hd.emojibattery.runtime.AppliedContent.url(selectedSticker(item.id))
            if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && !isCatalogOnline() &&
                !com.hd.emojibattery.runtime.MediaAssets.isCached(requireContext(), asset)) {
                toast("You're offline. Connect to download this sticker, then try again.")
                return@CatalogAdapter
            }
            selectedCatalogId = item.id
            adapter?.refreshSelection()
            if (editing) {
                addSticker(item.id)
                renderPreview()
                selectedAdapter?.refreshSelection()
                showStickerTutorialIfNeeded()
            } else if (screen == ScreenIds.STICKER_MORE && arguments?.getBoolean("return_sticker_selection") == true) {
                returningSelection = true
                if (isAdded && _binding != null && !parentFragmentManager.isStateSaved) {
                        parentFragmentManager.setFragmentResult("sticker_picker_result", android.os.Bundle().apply { putString("content_id", item.id) })
                        parentFragmentManager.popBackStack()
                } else returningSelection = false
            } else {
                navigate(ScreenIds.ADD_STICKER, android.os.Bundle().apply { putString("content_id", item.id) })
            }
        }, onFavorite = { item ->
            val found = if (screen == ScreenIds.SEARCH) searchItems[item.id] else null
            Favorites.set(prefs, found?.first ?: "stickers", found?.second?.id ?: item.id, !item.favorite)
            updateCatalog()
        })
        if (grid.adapter !== adapter) grid.adapter = adapter
        panel.findViewById<android.widget.TextView>(R.id.sticker_apply).text = "Apply"
        updateCatalog()
        if (editing) { renderPreview(); showStickerTutorialIfNeeded() }
        configureCatalogScrolling(grid)
        (requireActivity() as com.hd.emojibattery.MainActivity).updateEnableControls()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun setStickersEnabled(enabled: Boolean) {
        if (enabled) {
            requireAccessibilityUsageAccepted {
                val previous = prefs.getBoolean("stickers_enabled", false)
                prefs.edit().putBoolean("stickers_enabled", true).apply()
                if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE &&
                    !com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())) {
                    prefs.edit().putBoolean("stickers_enabled", previous).apply()
                }
                bindFeature()
            }
            bindFeature()
        } else {
            prefs.edit().putBoolean("stickers_enabled", false).apply()
            if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && (com.hd.emojibattery.GestureAccessibilityService.active != null)) {
                com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())
            }
        }
    }

    private fun configureEditorScrolling(panel: View) {
        panel.setPadding(0, 0, 0, 0)
    }

    private fun configureCatalogScrolling(grid: androidx.recyclerview.widget.RecyclerView) {
        // The catalog is the only vertical scroll surface, as in the reference app.
        val content = binding.featureContent
        val scroll = binding.featureScroll
        val host = scroll.parent as android.widget.FrameLayout
        if (content.parent === scroll) {
            scroll.removeView(content)
            host.addView(content, host.indexOfChild(scroll), android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        scroll.isVisible = false
        content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, 0)
        val panel = view<android.widget.LinearLayout>(R.id.feature_stickers)
        panel.layoutParams = android.widget.LinearLayout.LayoutParams(-1, 0, 1f)
        view<View>(R.id.sticker_gallery).layoutParams = android.widget.LinearLayout.LayoutParams(-1, 0, 1f)
        grid.layoutParams = android.widget.LinearLayout.LayoutParams(-1, 0, 1f)
        grid.isNestedScrollingEnabled = false
        grid.setHasFixedSize(true)
        fun reserveFooter() {
            if (_binding == null) return
            val params = content.layoutParams as android.widget.FrameLayout.LayoutParams
            val inset = if (binding.featureFooter.isVisible) binding.featureFooter.height else 0
            if (params.bottomMargin != inset) {
                params.bottomMargin = inset
                content.layoutParams = params
            }
        }
        if (grid.getTag(R.id.sticker_grid) != true) {
            grid.setTag(R.id.sticker_grid, true)
            binding.featureFooter.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reserveFooter() }
        }
        reserveFooter()
        attachPreviewLayer()
        installPreviewGestures()
    }

    private fun attachPreviewLayer() {
        if (!isEditor() || previewOverlay != null) return
        val canvas = previewView<View>(R.id.sticker_canvas)
        val wrapper = canvas.parent as android.widget.FrameLayout
        val placeholder = wrapper.parent as android.widget.FrameLayout
        val decor = requireActivity().window.decorView as android.view.ViewGroup
        val layer = object : android.widget.FrameLayout(requireContext()) {
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                // A sticker dragged over Back or Apply must not steal their taps.
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && touchesEditorAction(event)) return false
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        placeholder.removeView(wrapper)
        layer.addView(wrapper, android.widget.FrameLayout.LayoutParams(-1, -1))
        decor.addView(layer, android.view.ViewGroup.LayoutParams(-1, -1))
        previewOverlay = layer
        fun positionOrigin() {
            if (_binding == null || !layer.isAttachedToWindow) return
            val anchor = IntArray(2)
            val origin = IntArray(2)
            placeholder.getLocationOnScreen(anchor)
            layer.getLocationOnScreen(origin)
            val params = canvas.layoutParams as android.widget.FrameLayout.LayoutParams
            val top = anchor[1] - origin[1] + dp(9)
            if (params.topMargin != top) {
                params.topMargin = top
                canvas.layoutParams = params

            }
        }
        // The canvas starts GONE. On the first selection it has not yet acquired
        // its centered left/top or measured size; reproject after that layout.
        canvas.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (_binding != null) updatePreviewTransform(gestureItem ?: previewId?.let(::selectedSticker))
        }
        placeholder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionOrigin() }
        layer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            positionOrigin()
            if (_binding != null) updatePreviewTransform(gestureItem ?: previewId?.let(::selectedSticker))
        }
        layer.post { positionOrigin() }
    }

    private inline fun <reified T : View> previewView(id: Int): T =
        previewOverlay?.findViewById<T>(id) ?: view<T>(id)

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun installPreviewGestures() {
        if (!isEditor()) return
        val canvas = previewView<View>(R.id.sticker_canvas)
        if (canvas.getTag(R.id.sticker_canvas) == true) return
        canvas.setTag(R.id.sticker_canvas, true)
        var startX = 0f
        var startY = 0f
        var itemX = 0f
        var itemY = 0f
        var moved = false
        var scaling = false
        fun current() = gestureItem ?: previewId?.let(::selectedSticker)
        val scale = android.view.ScaleGestureDetector(requireContext(), object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                val item = current() ?: return false
                scaling = true
                val old = com.hd.emojibattery.runtime.StickerState.sizePixels(requireContext(), item)
                val next = (old * detector.scaleFactor).toInt().coerceIn(dp(60), dp(200))
                item.put("overlay_size_px", next)
                    .put("overlay_x", item.optDouble("overlay_x") + (old - next) / 2f)
                    .put("overlay_y", item.optDouble("overlay_y") + (old - next) / 2f)
                updatePreviewTransform(item)
                adjustmentChanged?.invoke(item)
                return true
            }
        })
        val parent = canvas.parent as android.widget.FrameLayout
        fun handle(artwork: Int, description: String, width: Int, height: Int): android.widget.ImageButton {
            return android.widget.ImageButton(requireContext()).apply {
                setImageResource(artwork)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = description
                background = null
                setPadding(0, 0, 0, 0)
                parent.addView(this, android.widget.FrameLayout.LayoutParams(dp(width), dp(height)))
            }
        }
        val close = handle(R.drawable.sticker_handle_remove, "Remove selected sticker", 33, 33)
        val flip = handle(R.drawable.sticker_handle_flip, "Flip selected sticker", 39, 38)
        val resize = handle(R.drawable.sticker_handle_resize, "Resize selected sticker", 39, 38)
        previewHandles = listOf(close, flip, resize)
        fun positionHandles() {
            val scaleFactor = kotlin.math.abs(canvas.scaleX)
            val left = canvas.x + canvas.width * (1 - scaleFactor) / 2f
            val top = canvas.y + canvas.height * (1 - canvas.scaleY) / 2f
            // Positions relative to the 131dp selection frame in node 961:3590.
            close.x = left + canvas.width * scaleFactor - dp(19); close.y = top - dp(12)
            flip.x = left - dp(18); flip.y = top - dp(14)
            resize.x = left + canvas.width * scaleFactor - dp(20)
            resize.y = top + canvas.height * canvas.scaleY - dp(24)
            listOf(close, flip, resize).forEach { it.isVisible = canvas.isVisible }
        }
        val previewRoot = binding.root
        val observer = parent.viewTreeObserver
        val preDrawListener = android.view.ViewTreeObserver.OnPreDrawListener {
            if (!isAdded || _binding?.root !== previewRoot) return@OnPreDrawListener true
            // Use the final screen origin before drawing, including the first
            // GONE -> VISIBLE layout and window inset changes.
            if (canvas.isVisible) updatePreviewTransform(current())
            positionHandles()
            true
        }
        removePreviewPreDrawListener?.invoke()
        observer.addOnPreDrawListener(preDrawListener)
        removePreviewPreDrawListener = {
            if (observer.isAlive) observer.removeOnPreDrawListener(preDrawListener)
            val currentObserver = parent.viewTreeObserver
            if (currentObserver !== observer && currentObserver.isAlive) {
                currentObserver.removeOnPreDrawListener(preDrawListener)
            }
        }
        close.setOnClickListener {
            val id = previewId ?: return@setOnClickListener
            adjustmentDialog?.dismiss()
            if (pendingSticker?.let(com.hd.emojibattery.runtime.StickerState::instanceId) == id) {
                pendingSticker = null
                previewId = null
                selectedCatalogId = null
                renderPreview()
                adapter?.refreshSelection()
            } else removeAppliedSticker(id)
        }
        flip.setOnClickListener {
            val item = current() ?: return@setOnClickListener
            item.put("overlay_flip", -item.optDouble("overlay_flip", 1.0))
            updatePreviewTransform(item)
        }
        var resizeStart = 0f
        var originalSize = 0
        var originalX = 0.0
        var originalY = 0.0
        resize.setOnTouchListener { _, event ->
            val item = current() ?: return@setOnTouchListener false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    resizeStart = event.rawX + event.rawY
                    originalSize = com.hd.emojibattery.runtime.StickerState.sizePixels(requireContext(), item)
                    originalX = item.optDouble("overlay_x")
                    originalY = item.optDouble("overlay_y")
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val next = (originalSize + (event.rawX + event.rawY - resizeStart) / 2f).toInt().coerceIn(dp(60), dp(200))
                    item.put("overlay_size_px", next).put("overlay_x", originalX + (originalSize - next) / 2f)
                        .put("overlay_y", originalY + (originalSize - next) / 2f)
                    updatePreviewTransform(item)
                    adjustmentChanged?.invoke(item)
                }
            }
            true
        }
        canvas.setOnTouchListener { target, event ->
            val item = current() ?: return@setOnTouchListener false
            scale.onTouchEvent(event)
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    itemX = item.optDouble("overlay_x").toFloat()
                    itemY = item.optDouble("overlay_y").toFloat()
                    moved = false
                    scaling = false
                    target.parent.requestDisallowInterceptTouchEvent(true)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    if (moved && !scaling && event.pointerCount == 1) {
                        val pixels = com.hd.emojibattery.runtime.StickerState.sizePixels(requireContext(), item)
                        val metrics = resources.displayMetrics
                        item.put("overlay_x", (itemX + dx).coerceIn(-pixels / 2f, metrics.widthPixels - pixels / 2f))
                            .put("overlay_y", (itemY + dy).coerceIn(-pixels / 2f - dp(80), (metrics.heightPixels - pixels).coerceAtLeast(0).toFloat() - dp(80)))
                        updatePreviewTransform(item)
                        adjustmentChanged?.invoke(item)
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    target.parent.requestDisallowInterceptTouchEvent(false)
                    if (!moved && !scaling && event.actionMasked == android.view.MotionEvent.ACTION_UP) target.performClick()
                }
            }
            true
        }
    }

    private fun showStickerTutorialIfNeeded() {
        if (!isEditor() || tutorialOverlay != null) return
        val anchor = previewView<View>(R.id.sticker_canvas)
        anchor.post {
            if (_binding == null || !isResumed || tutorialOverlay != null) return@post
            val list = view<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_selected_items)
            val first = list.findViewHolderForAdapterPosition(0)?.itemView
            val showGestures = previewId != null && !prefs.getBoolean("sticker_gesture_tutorial_shown", false)
            val showEdit = first != null && list.isVisible && !prefs.getBoolean("sticker_edit_tutorial_shown", false)
            if (!showGestures && !showEdit) return@post
            val host = requireActivity().window.decorView as android.view.ViewGroup
            val overlay = StickerTutorialOverlay(requireContext())
            overlay.passThroughTouch = ::touchesEditorAction
            tutorialOverlay = overlay
            host.addView(overlay, android.view.ViewGroup.LayoutParams(-1, -1))
            fun close() { host.removeView(overlay); tutorialOverlay = null }
            fun editStep() {
                if (showEdit && first != null) {
                    prefs.edit().putBoolean("sticker_edit_tutorial_shown", true).apply()
                    overlay.showStep(first, getString(R.string.sticker_tutorial_edit)) { close() }
                } else close()
            }
            if (showGestures) {
                prefs.edit().putBoolean("sticker_gesture_tutorial_shown", true).apply()
                overlay.showStep(anchor, getString(R.string.sticker_tutorial_gestures), previewHandles) { editStep() }
            } else editStep()
        }
    }

    private fun renderPreview(previewItem: org.json.JSONObject? = previewId?.let(::selectedSticker)) {
        val canvas = previewView<android.widget.ImageView>(R.id.sticker_canvas)
        val empty = previewId == null
        view<View>(R.id.sticker_hint_art).isVisible = empty
        view<View>(R.id.sticker_empty_hint).isVisible = empty
        canvas.isVisible = !empty
        val item = previewItem
        syncCommittedPreview(previewId)
        val url = com.hd.emojibattery.runtime.AppliedContent.url(item)
        if (url != canvas.tag) {
            (canvas.drawable as? android.graphics.drawable.Animatable)?.stop()
            canvas.tag = null
            canvas.setImageDrawable(null)
        }
        if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && url.isNotBlank() && canvas.tag != url) com.hd.emojibattery.runtime.MediaAssets.load(canvas, url) { loaded ->
            if (!loaded && isAdded && _binding != null && previewId != null) {
                toast("Could not load sticker. Check your connection and try again.")
            }
        }
        updatePreviewTransform(item)
    }

    private fun syncCommittedPreview(id: String?) {
        val changed = com.hd.emojibattery.runtime.StickerEditPreview.selectInline(id)
        if (changed && !com.hd.emojibattery.BuildConfig.UI_TEST_MODE &&
            com.hd.emojibattery.GestureAccessibilityService.active != null) {
            com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())
        }
    }

    private fun updatePreviewTransform(item: org.json.JSONObject?) {
        val canvas = previewView<android.widget.ImageView>(R.id.sticker_canvas)
        // The chosen instance supplies its own rotation; changing selection never
        // reads transforms from another copy of the same catalog sticker.
        canvas.rotation = item?.optInt("overlay_rotation", 0)?.toFloat() ?: 0f
        val pixels = item?.let { com.hd.emojibattery.runtime.StickerState.sizePixels(requireContext(), it) } ?: dp(140)
        // Both preview and the applied WindowManager view use screen pixels.
        // Account for the scaled View pivot so its visible top-left is exact.
        val baseWidth = canvas.width.takeIf { it > 0 } ?: dp(131)
        val baseHeight = canvas.height.takeIf { it > 0 } ?: dp(131)
        canvas.scaleX = pixels / baseWidth.toFloat() * (item?.optDouble("overlay_flip", 1.0)?.toFloat() ?: 1f)
        canvas.scaleY = pixels / baseHeight.toFloat()
        val parent = canvas.parent as View
        val origin = IntArray(2)
        parent.getLocationOnScreen(origin)
        val x = item?.optDouble("overlay_x", ((resources.displayMetrics.widthPixels - pixels) / 2f).toDouble())?.toFloat() ?: 0f
        val y = item?.let { com.hd.emojibattery.runtime.StickerState.screenY(requireContext(), it) } ?: 0f
        canvas.translationX = x - origin[0] - canvas.left + (pixels - baseWidth) / 2f
        canvas.translationY = y - origin[1] - canvas.top + (pixels - baseHeight) / 2f
    }

    override fun onCatalogStateChanged() {
        if (adapter != null && _binding != null) updateCatalog()
    }

    private fun updateCatalog() {
        if (screen == ScreenIds.SEARCH) { updateSearchCatalog(); return }
        val content = catalog().collection("stickers")
        val all = content.optJSONArray("stickers").toCatalogEntries { Favorites.contains(prefs, "stickers", it) }
        val category = prefs.getString("sticker_category", "All").takeIf { it in categories } ?: "All"
        val allowed = com.hd.emojibattery.runtime.CatalogGroups.ids(content, "stickers", category)
        val query = viewModel.query.value.orEmpty().trim()
        val filtered = all.filter { item ->
            val categoryMatches = screen == ScreenIds.SEARCH || screen == ScreenIds.FAVORITES || screen == ScreenIds.FAVORITES_STATE_2 || screen == ScreenIds.FAVORITES_DELETE || item.id in allowed
            categoryMatches && (screen !in listOf(ScreenIds.FAVORITES, ScreenIds.FAVORITES_STATE_2, ScreenIds.FAVORITES_DELETE) || item.favorite) &&
                ((com.hd.emojibattery.BuildConfig.UI_TEST_MODE && screen != ScreenIds.SEARCH) || query.isEmpty() || item.name.contains(query, true) || item.emoji.contains(query))
        }
        adapter?.submit(filtered)
        val library = screen in setOf(ScreenIds.FAVORITES, ScreenIds.FAVORITES_STATE_2, ScreenIds.FAVORITES_DELETE)
        // Filtering is synchronous. An unavailable catalog is not a completed search.
        val noSearchResults = screen == ScreenIds.SEARCH && !catalog().isLoading && (com.hd.emojibattery.BuildConfig.UI_TEST_MODE || isCatalogOnline()) && query.isNotEmpty() && all.isNotEmpty() && filtered.isEmpty()
        view<View>(R.id.sticker_search_empty).isVisible = noSearchResults
        view<android.widget.TextView>(R.id.sticker_search_empty_message).text =
            getString(R.string.sticker_search_no_results, query)
        view<View>(R.id.search_suggestions).isVisible = screen == ScreenIds.SEARCH && query.isEmpty()
        view<View>(R.id.sticker_empty).isVisible = library && filtered.isEmpty()
        view<View>(R.id.sticker_grid).isVisible = !noSearchResults && (!library || filtered.isNotEmpty())
        view<View>(R.id.sticker_delete).isVisible = library && filtered.isNotEmpty()
    }

    private fun updateSearchCatalog() {
        val query = viewModel.query.value.orEmpty().trim()
        val sources = listOf("battery_styles", "stickers", "pets")
        val descriptions = mutableMapOf<String, String>()
        val all = sources.flatMap { source ->
            val items = catalog().array(source)
            for (index in 0 until (items?.length() ?: 0)) items?.optJSONObject(index)?.let { item ->
                descriptions["$source|${item.optString("id")}"] = listOf("name", "category", "en").joinToString(" ") { item.optString(it) }
            }
            items.toCatalogEntries { Favorites.contains(prefs, source, it) }.map { source to it }
        }
        val filtered = all.filter { (source, item) -> query.isBlank() || item.name.contains(query, true) || item.emoji.contains(query, true) ||
            descriptions["$source|${item.id}"]?.contains(query, true) == true }
        searchItems = filtered.associateBy { (source, item) -> "$source|${item.id}" }
        adapter?.submit(filtered.map { (source, item) -> item.copy(id = "$source|${item.id}") })
        val noResults = !catalog().isLoading && query.isNotEmpty() && filtered.isEmpty() && all.isNotEmpty()
        view<View>(R.id.sticker_search_empty).isVisible = noResults
        view<android.widget.TextView>(R.id.sticker_search_empty_message).text = getString(R.string.sticker_search_no_results, query)
        view<View>(R.id.search_suggestions).isVisible = query.isBlank()
        view<View>(R.id.sticker_empty).isVisible = false
        view<View>(R.id.sticker_grid).isVisible = !noResults
        view<View>(R.id.sticker_delete).isVisible = false
    }

    private fun selectedSticker(targetId: String): org.json.JSONObject? =
        pendingSticker?.takeIf { com.hd.emojibattery.runtime.StickerState.instanceId(it) == targetId }
            ?: (0 until draftStickers.length()).mapNotNull { draftStickers.optJSONObject(it) }.firstOrNull { com.hd.emojibattery.runtime.StickerState.instanceId(it) == targetId }
            ?: com.hd.emojibattery.runtime.AppliedContent.find(requireContext(), "stickers", targetId)
            ?: prefs.getString("custom_sticker_uri", "").orEmpty()
                .takeIf { targetId == "custom" && it.isNotBlank() }
                ?.let { org.json.JSONObject().put("id", "custom").put("asset_url", it) }

    private fun applySelectedSticker() {
        if (applyJob?.isActive == true) return
        val snapshot = org.json.JSONArray(draftStickers.toString())
        pendingSticker?.let { snapshot.put(org.json.JSONObject(it.toString())) }
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) {
            prefs.edit().putString("applied_stickers", snapshot.toString()).putBoolean("stickers_applied", snapshot.length() > 0)
                .putBoolean("stickers_enabled", snapshot.length() > 0).apply()
            draftStickers = snapshot
            pendingSticker = null
            previewId = null
            selectedCatalogId = null
            bindFeature()
            return
        }
        val app = requireContext().applicationContext
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setMessage("Preparing stickers…")
            .setNegativeButton("Cancel") { _, _ -> applyJob?.cancel() }.create()
        dialog.setOnCancelListener { applyJob?.cancel() }
        trackDialog(dialog)
        dialog.show()
        binding.featureFooterAction.isEnabled = false
        applyJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ready = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    (0 until snapshot.length()).all { index ->
                        val item = snapshot.optJSONObject(index) ?: return@all false
                        runCatching {
                            val file = com.hd.emojibattery.runtime.MediaAssets.file(app, com.hd.emojibattery.runtime.AppliedContent.url(item))
                                ?: return@runCatching false
                            com.hd.emojibattery.runtime.MediaAssets.drawable(file)
                            true
                        }.getOrDefault(false)
                    }
                }
                if (!ready) { toast("Could not load a sticker. Check your connection and try again."); return@launch }
                if (com.hd.emojibattery.runtime.FeatureApply.stickerCollection(requireActivity(), snapshot)) {
                    draftStickers = snapshot
                    pendingSticker = null
                    previewId = null
                    selectedCatalogId = null
                    toast("Sticker applied")
                    bindFeature()
                }
            } finally {
                dialog.dismiss()
                if (_binding != null) binding.featureFooterAction.isEnabled = true
            }
        }
    }

    private fun showAdjustment(itemId: String? = null) {
        if (adjustmentDialog?.isShowing == true) return
        dismissTutorial()
        val targetId = itemId ?: previewId ?: run { toast("Choose a sticker first"); return }
        previewId = targetId
        val uiTest = com.hd.emojibattery.BuildConfig.UI_TEST_MODE
        val item = selectedSticker(targetId)
        if (item == null) { toast("Choose a sticker first"); return }
        if (!uiTest && !com.hd.emojibattery.runtime.FeatureApply.permission(requireActivity())) return
        val activity = requireActivity()
        val draft = org.json.JSONObject(item.toString())
        fun option(name: String) = "${name}_$targetId"
        // Keep the window content-sized so touches above the controls reach the preview.
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        adjustmentDialog = dialog
        val controls = layoutInflater.inflate(R.layout.sheet_sticker_adjustment, null)
        val number = (0 until draftStickers.length()).indexOfFirst {
            draftStickers.optJSONObject(it)?.let(com.hd.emojibattery.runtime.StickerState::instanceId) == targetId
        } + 1
        controls.contentDescription = "Adjusting sticker $number: ${item.optString("name", "Sticker")}"
        dialog.setContentView(controls)
        val size = controls.findViewById<android.widget.SeekBar>(R.id.adjust_size)
        val rotate = controls.findViewById<android.widget.SeekBar>(R.id.adjust_rotation)
        size.max = dp(200)
        size.min = dp(60)
        size.progress = com.hd.emojibattery.runtime.StickerState.sizePixels(activity, draft)
        rotate.max = 360
        rotate.progress = com.hd.emojibattery.runtime.StickerState.rotation(draft) + 180
        var offsetX = draft.optDouble("overlay_x", prefs.getFloat(option("sticker_offset_x"), 0f).toDouble()).toFloat()
        var offsetY = draft.optDouble("overlay_y", prefs.getFloat(option("sticker_offset_y"), 0f).toDouble()).toFloat()
        var previousSize = size.progress
        fun updateDraft() {
            draft.put("overlay_size_px", size.progress).put("overlay_rotation", rotate.progress - 180)
                .put("overlay_x", offsetX).put("overlay_y", offsetY)
        }
        fun updateLive() {
            val nextSize = size.progress
            // Keep the image center fixed, retaining half pixels to avoid drift on repeated resizing.
            val centerOffset = (previousSize - nextSize) / 2f
            offsetX += centerOffset
            offsetY += centerOffset
            previousSize = nextSize
            val metrics = resources.displayMetrics
            offsetX = offsetX.coerceIn(-nextSize / 2f, metrics.widthPixels - nextSize / 2f)
            offsetY = (offsetY + dp(80)).coerceIn(-nextSize / 2f, (metrics.heightPixels - nextSize).coerceAtLeast(0).toFloat()) - dp(80)
            updateDraft()
            updatePreviewTransform(draft)
            offsetX = draft.optDouble("overlay_x").toFloat()
            offsetY = draft.optDouble("overlay_y").toFloat()
        }
        updateDraft()
        gestureItem = draft
        adjustmentChanged = { changed ->
            offsetX = changed.optDouble("overlay_x").toFloat()
            offsetY = changed.optDouble("overlay_y").toFloat()
            previousSize = com.hd.emojibattery.runtime.StickerState.sizePixels(activity, changed)
            size.progress = previousSize
        }
        val confirm = controls.findViewById<View>(R.id.adjust_apply)
        confirm.isEnabled = true
        dialog.setOnDismissListener {
            adjustmentDialog = null
            gestureItem = null
            adjustmentChanged = null
            // Return the floating controls to the editor window after modal editing.
            previewOverlay?.let { layer ->
                (layer.parent as? android.view.ViewGroup)?.removeView(layer)
                (activity.window.decorView as android.view.ViewGroup).addView(layer,
                    android.view.ViewGroup.LayoutParams(-1, -1))
                layer.requestLayout()
            }
            if (isAdded && isResumed && _binding != null) renderPreview()
        }
        fun labels() {
            controls.findViewById<android.widget.TextView>(R.id.adjust_size_label).text = "${size.progress}"
            controls.findViewById<android.widget.TextView>(R.id.adjust_rotation_label).text = "${rotate.progress - 180}°"
        }
        labels()
        listOf(size, rotate).forEach { it.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, user: Boolean) { labels(); updateLive() }
            override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
        }) }
        controls.findViewById<View>(R.id.adjust_size_plus).setOnClickListener { size.progress += 10 }
        controls.findViewById<View>(R.id.adjust_size_minus).setOnClickListener { size.progress -= 10 }
        controls.findViewById<View>(R.id.adjust_rotate_left).setOnClickListener { rotate.progress -= 5 }
        controls.findViewById<View>(R.id.adjust_rotate_right).setOnClickListener { rotate.progress += 5 }
        controls.findViewById<View>(R.id.adjust_up).setOnClickListener { offsetY -= 10; updateLive() }
        controls.findViewById<View>(R.id.adjust_down).setOnClickListener { offsetY += 10; updateLive() }
        controls.findViewById<View>(R.id.adjust_left).setOnClickListener { offsetX -= 10; updateLive() }
        controls.findViewById<View>(R.id.adjust_right).setOnClickListener { offsetX += 10; updateLive() }
        controls.findViewById<View>(R.id.adjust_apply).setOnClickListener {
            if (!uiTest && previewView<android.widget.ImageView>(R.id.sticker_canvas).drawable == null) {
                toast("Wait for the sticker to finish loading")
                return@setOnClickListener
            }
            updateDraft()
            val snapshot = org.json.JSONArray()
            var replaced = false
            for (index in 0 until draftStickers.length()) draftStickers.optJSONObject(index)?.let { saved ->
                if (com.hd.emojibattery.runtime.StickerState.instanceId(saved) == targetId) {
                    snapshot.put(org.json.JSONObject(draft.toString()))
                    replaced = true
                } else snapshot.put(org.json.JSONObject(saved.toString()))
            }
            if (!replaced) {
                if (snapshot.length() >= 5) { toast("You can add up to 5 stickers"); return@setOnClickListener }
                snapshot.put(org.json.JSONObject(draft.toString()))
            }
            val applied = if (uiTest) {
                prefs.edit().putString("applied_stickers", snapshot.toString())
                    .putBoolean("stickers_applied", true).putBoolean("stickers_enabled", true).apply()
                true
            } else com.hd.emojibattery.runtime.FeatureApply.stickerCollection(activity, snapshot)
            if (!applied) return@setOnClickListener
            draftStickers = snapshot
            if (pendingSticker?.let(com.hd.emojibattery.runtime.StickerState::instanceId) == targetId) pendingSticker = null
            previewId = null
            selectedCatalogId = null
            dialog.dismiss()
            bindFeature()
        }
        trackDialog(dialog)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            dialog.setCanceledOnTouchOutside(false)
            dialog.window?.apply {
                setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                setDimAmount(0.5f)
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                // A modal sheet blocks the editor, while its native sticker and
                // handles remain interactive in this window above the dim layer.
                previewOverlay?.let { layer ->
                    (layer.parent as? android.view.ViewGroup)?.removeView(layer)
                    (decorView as android.view.ViewGroup).addView(layer,
                        android.view.ViewGroup.LayoutParams(-1, -1))
                    layer.requestLayout()
                }
            }
            renderPreview(draft)
        }
        dialog.show()
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(Intent.createChooser(intent, "Choose sticker image"), 541)
    }

    @Deprecated("Legacy callback required by the minimum SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 541 && resultCode == android.app.Activity.RESULT_OK) {
            data?.data?.let { uri ->
                runCatching { requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                addStickerCopy(org.json.JSONObject().put("id", "custom").put("name", "Custom sticker")
                    .put("image_url", uri.toString()).put("asset_url", uri.toString()))
                bindFeature()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        previewOverlay?.isVisible = true
        if (_binding != null && isEditor()) syncCommittedPreview(previewId)
    }

    override fun onStop() {
        previewOverlay?.isVisible = false
        if (isEditor()) syncCommittedPreview(null)
        tutorialOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        tutorialOverlay = null
        adjustmentDialog?.dismiss()
        super.onStop()
    }

    override fun onDestroyView() {
        removePreviewPreDrawListener?.invoke()
        removePreviewPreDrawListener = null
        applyJob?.cancel()
        applyJob = null
        returningSelection = false
        adjustmentDialog?.dismiss()
        _binding?.root?.findViewById<EditText>(R.id.sticker_search)?.removeTextChangedListener(watcher)
        watcher = null
        previewOverlay?.findViewById<android.widget.ImageView>(R.id.sticker_canvas)?.let(com.hd.emojibattery.runtime.MediaAssets::clear)
        previewOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        previewOverlay = null
        previewHandles = emptyList()
        adapter = null
        selectedAdapter = null
        gestureItem = null
        adjustmentChanged = null
        super.onDestroyView()
    }
}
