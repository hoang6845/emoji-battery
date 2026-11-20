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
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.common.CatalogAdapter
import com.hd.emojibattery.ui.common.CategoryAdapter
import com.hd.emojibattery.ui.common.toCatalogEntries
import com.hd.emojibattery.ui.navigation.ScreenIds

open class StickerFragment<VM : StickerViewModel> : BaseFeatureFragment<VM>() {
    private var categories: List<String> = listOf("All")
    private var adapter: CatalogAdapter? = null
    private var watcher: TextWatcher? = null
    private var previewId: String? = null
    private var selectedCatalogId: String? = null
    private var adjustmentDialog: BottomSheetDialog? = null
    private var returningSelection = false
    private var draftStickers = org.json.JSONArray()
    private var applyJob: kotlinx.coroutines.Job? = null
    private var searchItems = emptyMap<String, Pair<String, com.hd.emojibattery.ui.common.CatalogEntry>>()
    override fun hasUnsavedChanges(): Boolean = isEditor() && draftStickers.toString() != prefs.getString("applied_stickers", "[]")
    private fun isEditor() = screen in setOf(ScreenIds.ADD_STICKER, ScreenIds.ADD_STICKER_STATE_2,
        ScreenIds.STICKER_EDIT, ScreenIds.STICKER_DETAIL, ScreenIds.STICKER_EXIT)

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
        item.put("instance_id", instance).put("overlay_size_px", pixels).put("overlay_rotation", 0)
            .put("overlay_x", (metrics.widthPixels - pixels) / 2f)
            .put("overlay_y", dp(100).toFloat())
        putDraft(item)
        previewId = instance
        selectedCatalogId = source.optString("id")
    }

    private fun putDraft(item: org.json.JSONObject) {
        val updated = org.json.JSONArray()
        var replaced = false
        for (i in 0 until draftStickers.length()) draftStickers.optJSONObject(i)?.let {
            if (com.hd.emojibattery.runtime.StickerState.instanceId(it) == com.hd.emojibattery.runtime.StickerState.instanceId(item)) {
                updated.put(org.json.JSONObject(item.toString()))
                replaced = true
            } else updated.put(it)
        }
        if (!replaced) updated.put(org.json.JSONObject(item.toString()))
        draftStickers = updated
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        previewId = savedInstanceState?.getString("sticker_draft_id")
        selectedCatalogId = savedInstanceState?.getString("sticker_catalog_id")
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
                    bindFeature()
                    scrollToSticker(id)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        outState.putString("sticker_draft_id", previewId)
        outState.putString("sticker_catalog_id", selectedCatalogId)
        outState.putString("sticker_draft_items", draftStickers.toString())
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
        panel.findViewById<View>(R.id.sticker_selected_panel).isVisible = editing
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
                .setItems(categories.toTypedArray()) { _, index -> prefs.edit().putString("sticker_category", categories[index]).apply(); bindFeature() }
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
        panel.findViewById<View>(R.id.sticker_remove).isVisible = false
        panel.findViewById<View>(R.id.sticker_empty_hint).isVisible = editing && previewId == null
        panel.findViewById<android.widget.TextView>(R.id.sticker_selection_count).text = "My sticker (${applied.size}/5)"
        panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_selected_items).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = CatalogAdapter(stickerStyle = true, cardWidth = 120, removeAction = true, selection = { previewId },
                onSelected = { item ->
                    previewId = item.id
                    selectedCatalogId = selectedSticker(item.id)?.optString("id")
                    renderPreview()
                    val target = item.id
                    requireAccessibilityUsageAccepted { showAdjustment(target) }
                },
                onFavorite = { item -> removeAppliedSticker(item.id) }).also { it.submit(applied) }
        }
        panel.findViewById<View>(R.id.sticker_canvas).apply {
            contentDescription = "Edit selected sticker"
            setOnClickListener {
                val target = previewId ?: return@setOnClickListener
                requireAccessibilityUsageAccepted { showAdjustment(target) }
            }
            androidx.core.view.ViewCompat.replaceAccessibilityAction(
                this, androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                "Edit selected sticker", null
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
        categoriesView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        categoriesView.adapter = CategoryAdapter(stickerStyle = true) { value -> prefs.edit().putString("sticker_category", value).apply(); updateCatalog() }
            .also { it.submit(categories, prefs.getString("sticker_category", "All").takeIf { it in categories } ?: "All") }
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_grid)
        grid.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = CatalogAdapter(stickerStyle = true, selection = { selectedCatalogId }, onSelected = { item ->
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
                bindFeature()
            } else if (screen == ScreenIds.STICKER_MORE && arguments?.getBoolean("return_sticker_selection") == true) {
                returningSelection = true
                // Let the selected border render before returning to the existing editor.
                grid.postDelayed({
                    if (isAdded && _binding != null && !parentFragmentManager.isStateSaved) {
                        parentFragmentManager.setFragmentResult("sticker_picker_result", android.os.Bundle().apply { putString("content_id", item.id) })
                        parentFragmentManager.popBackStack()
                    } else returningSelection = false
                }, 120)
            } else {
                navigate(ScreenIds.ADD_STICKER, android.os.Bundle().apply { putString("content_id", item.id) })
            }
        }, onFavorite = { item ->
            val found = if (screen == ScreenIds.SEARCH) searchItems[item.id] else null
            Favorites.set(prefs, found?.first ?: "stickers", found?.second?.id ?: item.id, !item.favorite)
            updateCatalog()
        })
        grid.adapter = adapter
        panel.findViewById<android.widget.TextView>(R.id.sticker_apply).text = "Apply"
        updateCatalog()
        if (editing) renderPreview()
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
            if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && android.provider.Settings.canDrawOverlays(requireContext())) {
                com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())
            }
        }
    }

    private fun configureEditorScrolling(panel: View) {
        // Scroll the whole editor so fixed preview/selection panels cannot squeeze
        // the Add sticker grid down to a partially visible row on short screens.
        panel.setPadding(0, 0, 0, 0)
        panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_grid)
            .isNestedScrollingEnabled = false
        binding.featureFooter.doOnNextLayout { footer ->
            if (_binding != null) {
                binding.featureScroll.setPadding(0, 0, 0, footer.height)
            }
        }
    }

    private fun scrollToSticker(id: String) {
        val grid = view<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_grid)
        grid.doOnNextLayout {
            if (_binding == null || !isAdded) return@doOnNextLayout
            val position = adapter?.positionOf(id) ?: -1
            val item = grid.layoutManager?.findViewByPosition(position) ?: return@doOnNextLayout
            val bounds = android.graphics.Rect()
            item.getDrawingRect(bounds)
            binding.featureContent.offsetDescendantRectToMyCoords(item, bounds)
            binding.featureScroll.smoothScrollTo(0, bounds.top)
        }
        grid.requestLayout()
    }

    private fun renderPreview() {
        val canvas = view<android.widget.TextView>(R.id.sticker_canvas)
        canvas.text = ""
        val empty = previewId == null
        view<View>(R.id.sticker_hint_art).isVisible = empty
        view<View>(R.id.sticker_empty_hint).isVisible = empty
        canvas.isVisible = !empty
        val item = previewId?.let(::selectedSticker)
        val url = com.hd.emojibattery.runtime.AppliedContent.url(item)
        if (url != canvas.tag) {
            (canvas.background as? android.graphics.drawable.Animatable)?.stop()
            canvas.tag = null
            canvas.background = null
        }
        if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && url.isNotBlank() && canvas.tag != url) com.hd.emojibattery.runtime.MediaAssets.background(canvas, url) { loaded ->
            if (!loaded && isAdded && _binding != null && previewId != null) {
                toast("Could not load sticker. Check your connection and try again.")
            }
        }
        // The chosen instance supplies its own rotation; changing selection never
        // reads transforms from another copy of the same catalog sticker.
        canvas.rotation = item?.optInt("overlay_rotation", 0)?.toFloat() ?: 0f
        val pixels = item?.let { com.hd.emojibattery.runtime.StickerState.sizePixels(requireContext(), it) } ?: dp(140)
        val previewScale = dp(131) / dp(140).toFloat()
        canvas.scaleX = pixels / dp(140).toFloat()
        canvas.scaleY = canvas.scaleX
        // Convert overlay coordinates to the existing preview's centered origin.
        val centeredX = (resources.displayMetrics.widthPixels - pixels) / 2f
        canvas.translationX = ((item?.optDouble("overlay_x", centeredX.toDouble()) ?: centeredX.toDouble()).toFloat() - centeredX) * previewScale
        canvas.translationY = ((item?.optDouble("overlay_y", dp(100).toDouble()) ?: dp(100).toDouble()).toFloat() - dp(100) + (pixels - dp(140)) / 2f) * previewScale
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
        (0 until draftStickers.length()).mapNotNull { draftStickers.optJSONObject(it) }.firstOrNull { com.hd.emojibattery.runtime.StickerState.instanceId(it) == targetId }
            ?: com.hd.emojibattery.runtime.AppliedContent.find(requireContext(), "stickers", targetId)
            ?: prefs.getString("custom_sticker_uri", "").orEmpty()
                .takeIf { targetId == "custom" && it.isNotBlank() }
                ?.let { org.json.JSONObject().put("id", "custom").put("asset_url", it) }

    private fun applySelectedSticker() {
        if (applyJob?.isActive == true) return
        val snapshot = org.json.JSONArray(draftStickers.toString())
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) {
            prefs.edit().putString("applied_stickers", snapshot.toString()).putBoolean("stickers_applied", snapshot.length() > 0)
                .putBoolean("stickers_enabled", snapshot.length() > 0).apply()
            finishFeature()
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
                    toast("Sticker applied")
                    finishFeature()
                }
            } finally {
                dialog.dismiss()
                if (_binding != null) binding.featureFooterAction.isEnabled = true
            }
        }
    }

    private fun showAdjustment(itemId: String? = null) {
        if (adjustmentDialog?.isShowing == true) return
        val targetId = itemId ?: previewId ?: run { toast("Choose a sticker first"); return }
        previewId = targetId
        val uiTest = com.hd.emojibattery.BuildConfig.UI_TEST_MODE
        val item = selectedSticker(targetId)
        if (item == null) { toast("Choose a sticker first"); return }
        if (!uiTest && !com.hd.emojibattery.runtime.FeatureApply.permission(requireActivity())) return
        val activity = requireActivity()
        val draft = org.json.JSONObject(item.toString())
        fun option(name: String) = "${name}_$targetId"
        val dialog = BottomSheetDialog(requireContext())
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
            putDraft(draft)
            renderPreview()
            offsetX = draft.optDouble("overlay_x").toFloat()
            offsetY = draft.optDouble("overlay_y").toFloat()
        }
        updateDraft()
        val confirm = controls.findViewById<View>(R.id.adjust_apply)
        confirm.isEnabled = true
        dialog.setOnDismissListener {
            adjustmentDialog = null
            if (isAdded && isResumed && _binding != null) bindFeature()
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
            updateDraft()
            putDraft(draft)
            dialog.dismiss()
            if (!uiTest && android.provider.Settings.canDrawOverlays(activity)) com.hd.emojibattery.runtime.FeatureApply.decorations(activity)
            bindFeature()
        }
        trackDialog(dialog)
        dialog.setOnShowListener {
            dialog.findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.layoutParams = it.layoutParams.apply { height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT }
                controls.layoutParams = controls.layoutParams.apply { height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT }
                it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(it).apply {
                    state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
            dialog.window?.setDimAmount(.2f)
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

    override fun onStop() {
        adjustmentDialog?.dismiss()
        super.onStop()
    }

    override fun onDestroyView() {
        applyJob?.cancel()
        applyJob = null
        returningSelection = false
        adjustmentDialog?.dismiss()
        _binding?.root?.findViewById<EditText>(R.id.sticker_search)?.removeTextChangedListener(watcher)
        watcher = null
        super.onDestroyView()
    }
}
