package com.hd.emojibattery.ui.stickers

import com.hd.emojibattery.runtime.Favorites
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.view.doOnNextLayout
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
    private var adjustmentDialog: BottomSheetDialog? = null
    private var returningSelection = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        previewId = savedInstanceState?.getString("sticker_draft_id") ?: arguments?.getString("content_id")
        if (screen in setOf(ScreenIds.ADD_STICKER, ScreenIds.ADD_STICKER_STATE_2, ScreenIds.STICKER_EDIT, ScreenIds.STICKER_DETAIL, ScreenIds.STICKER_EXIT)) {
            parentFragmentManager.setFragmentResultListener("sticker_picker_result", this) { _, result ->
                val id = result.getString("content_id") ?: return@setFragmentResultListener
                previewId = id
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
        super.onSaveInstanceState(outState)
    }

    private fun appliedStickers(): org.json.JSONArray =
        runCatching { org.json.JSONArray(prefs.getString("applied_stickers", "[]")) }.getOrDefault(org.json.JSONArray())

    private fun removeAppliedSticker(id: String) {
        val previous = appliedStickers()
        val remaining = org.json.JSONArray()
        for (i in 0 until previous.length()) previous.optJSONObject(i)?.let {
            if (it.optString("id", "custom") != id) remaining.put(it)
        }
        prefs.edit().putString("applied_stickers", remaining.toString())
            .putBoolean("stickers_applied", remaining.length() > 0).apply()
        if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && android.provider.Settings.canDrawOverlays(requireContext())) {
            com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())
        }
        bindFeature()
    }

    override fun bindFeature() {
        val content = catalog().collection("stickers")
        categories = com.hd.emojibattery.runtime.CatalogGroups.names(content, "stickers")
        val panel = view<View>(R.id.feature_stickers)
        val library = screen in setOf(ScreenIds.FAVORITES, ScreenIds.FAVORITES_STATE_2, ScreenIds.FAVORITES_DELETE)
        val editing = screen in setOf(ScreenIds.ADD_STICKER, ScreenIds.ADD_STICKER_STATE_2, ScreenIds.STICKER_EDIT, ScreenIds.STICKER_DETAIL, ScreenIds.STICKER_EXIT)
        val emptyEditor = screen == ScreenIds.ADD_STICKER_STATE_2
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
                setOnCheckedChangeListener { _, enabled ->
                    if (enabled) {
                        requireAccessibilityUsageAccepted {
                            if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE || com.hd.emojibattery.runtime.FeatureApply.permission(requireActivity())) {
                                prefs.edit().putBoolean("stickers_enabled", true).apply()
                                if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE) com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())
                            }
                            bindFeature()
                        }
                        // Do not show an enabled state while permission is still pending.
                        bindFeature()
                    } else {
                        prefs.edit().putBoolean("stickers_enabled", false).apply()
                        if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && android.provider.Settings.canDrawOverlays(requireContext())) {
                            com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity())
                        }
                    }
                }
            }
        }
        if (editing) configureEditorScrolling(panel)
        panel.findViewById<View>(R.id.sticker_selected_panel).isVisible = editing
        panel.findViewById<View>(R.id.sticker_empty_hint).isVisible = emptyEditor
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
                setOnCheckedChangeListener(null)
                thumbDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.toggle_thumb)
                trackDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.toggle_track)
                thumbTintList = null
                trackTintList = null
                switchMinWidth = dp(58)
                setPadding(0, 0, 0, 0)
                isChecked = prefs.getBoolean("stickers_enabled", false)
                contentDescription = "Enable stickers"
                setOnCheckedChangeListener { _, enabled -> prefs.edit().putBoolean("stickers_enabled", enabled).apply(); if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE) com.hd.emojibattery.runtime.FeatureApply.decorations(requireActivity()) }
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
            panel.findViewById<android.widget.TextView>(R.id.sticker_banner_title).text = "Find your sticker"
            panel.findViewById<android.widget.TextView>(R.id.sticker_banner_subtitle).text = "Search across every pack"
        }
        panel.findViewById<View>(R.id.sticker_explore).setOnClickListener { navigate(ScreenIds.STICKER_MORE) }
        panel.findViewById<View>(R.id.sticker_view_more).setOnClickListener {
            prefs.edit().putString("sticker_category", "All").apply()
            navigate(ScreenIds.STICKER_MORE, android.os.Bundle().apply {
                putString("content_id", previewId)
                putBoolean("return_sticker_selection", true)
            })
        }
        val applied = appliedStickers().toCatalogEntries()
        panel.findViewById<View>(R.id.sticker_remove).isVisible = false
        panel.findViewById<View>(R.id.sticker_empty_hint).isVisible = editing && previewId == null
        panel.findViewById<android.widget.TextView>(R.id.sticker_selection_count).text = "My sticker (${applied.size})"
        panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_selected_items).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = CatalogAdapter(stickerStyle = true, cardWidth = 120, removeAction = true,
                onSelected = {}, onFavorite = { item -> removeAppliedSticker(item.id) }).also { it.submit(applied) }
        }
        panel.findViewById<View>(R.id.sticker_canvas).apply {
            contentDescription = "Edit selected sticker"
            setOnClickListener {
                requireAccessibilityUsageAccepted { showAdjustment(previewId) }
            }
            androidx.core.view.ViewCompat.replaceAccessibilityAction(
                this, androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                "Edit selected sticker", null
            )
        }
        if (screen == ScreenIds.STICKER_EXIT) showDiscardChanges { navigate(ScreenIds.HOME) }
        if (screen == ScreenIds.SORT) {
            val themeCategories = com.hd.emojibattery.runtime.CatalogGroups.names(catalog().collection("themes"), "themes")
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Choose category").setItems(themeCategories.toTypedArray()) { _, index ->
                    prefs.edit().putString("home_category", themeCategories[index]).apply(); navigate(ScreenIds.HOME)
                }.show().also { trackDialog(it) }
        }
        val search = panel.findViewById<EditText>(R.id.sticker_search)
        panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.search_quick_tags).apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = CategoryAdapter(pillGrid = true) { search.setText(it) }.also { it.submit(listOf("Anime", "Cute", "Kpop", "Heart", "Loopy", "Heart"), "") }
        }
        search.hint = if (screen == ScreenIds.SEARCH) "Search stickers" else "Search stickers"
        watcher?.let(search::removeTextChangedListener)
        search.setText(viewModel.query.value.orEmpty().takeIf { screen == ScreenIds.SEARCH }.orEmpty())
        watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { viewModel.setQuery(s?.toString().orEmpty()); updateCatalog() }
            override fun afterTextChanged(s: Editable?) = Unit
        }.also(search::addTextChangedListener)
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
        adapter = CatalogAdapter(stickerStyle = true, selection = { previewId }, onSelected = { item ->
            if (returningSelection) return@CatalogAdapter
            previewId = item.id
            adapter?.refreshSelection()
            if (editing) {
                renderPreview()
                panel.findViewById<View>(R.id.sticker_empty_hint).isVisible = false
                adapter?.refreshSelection()
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
        }, onFavorite = { item -> Favorites.set(prefs, "stickers", item.id, !item.favorite); updateCatalog() })
        grid.adapter = adapter
        panel.findViewById<android.widget.TextView>(R.id.sticker_apply).text = "Apply"
        updateCatalog()
        if (editing) renderPreview()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

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
        (canvas.background as? android.graphics.drawable.Animatable)?.stop()
        canvas.tag = null
        canvas.background = null
        canvas.text = ""
        val item = com.hd.emojibattery.runtime.AppliedContent.find(requireContext(), "stickers", previewId)
        val url = if (previewId == "custom") prefs.getString("custom_sticker_uri", "").orEmpty() else com.hd.emojibattery.runtime.AppliedContent.url(item)
        if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && url.isNotBlank()) com.hd.emojibattery.runtime.MediaAssets.background(canvas, url)
        // The gallery canvas always stays at its original size and position.
        canvas.rotation = 0f
        canvas.scaleX = 1f
        canvas.scaleY = 1f
        canvas.translationX = 0f
        canvas.translationY = 0f
    }

    private fun updateCatalog() {
        val content = catalog().collection("stickers")
        val all = content.optJSONArray("stickers").toCatalogEntries { Favorites.contains(prefs, "stickers", it) }
        val category = prefs.getString("sticker_category", "All").takeIf { it in categories } ?: "All"
        val allowed = com.hd.emojibattery.runtime.CatalogGroups.ids(content, "stickers", category)
        val query = viewModel.query.value.orEmpty().trim()
        val filtered = all.filter { item ->
            val categoryMatches = screen == ScreenIds.SEARCH || screen == ScreenIds.FAVORITES || screen == ScreenIds.FAVORITES_STATE_2 || screen == ScreenIds.FAVORITES_DELETE || item.id in allowed
            categoryMatches && (screen !in listOf(ScreenIds.FAVORITES, ScreenIds.FAVORITES_STATE_2, ScreenIds.FAVORITES_DELETE) || item.favorite) &&
                (com.hd.emojibattery.BuildConfig.UI_TEST_MODE || query.isEmpty() || item.name.contains(query, true) || item.emoji.contains(query))
        }
        adapter?.submit(filtered)
        val library = screen in setOf(ScreenIds.FAVORITES, ScreenIds.FAVORITES_STATE_2, ScreenIds.FAVORITES_DELETE)
        view<View>(R.id.sticker_empty).isVisible = library && filtered.isEmpty()
        view<View>(R.id.sticker_grid).isVisible = !library || filtered.isNotEmpty()
        view<View>(R.id.sticker_delete).isVisible = library && filtered.isNotEmpty()
    }

    private fun selectedSticker(targetId: String): org.json.JSONObject? =
        com.hd.emojibattery.runtime.AppliedContent.find(requireContext(), "stickers", targetId)
            ?: prefs.getString("custom_sticker_uri", "").orEmpty()
                .takeIf { targetId == "custom" && it.isNotBlank() }
                ?.let { org.json.JSONObject().put("id", "custom").put("asset_url", it) }

    private fun applySelectedSticker() {
        val targetId = previewId ?: run { toast("Choose a sticker first"); return }
        val item = selectedSticker(targetId) ?: run { toast("Choose a sticker first"); return }
        val draft = org.json.JSONObject(item.toString())
            .put("overlay_size", prefs.getInt("sticker_size_$targetId", 50))
            .put("overlay_rotation", prefs.getInt("sticker_rotation_$targetId", 0))
            .put("overlay_x", prefs.getFloat("sticker_offset_x_$targetId", 0f))
            .put("overlay_y", prefs.getFloat("sticker_offset_y_$targetId", 0f))
        if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE &&
            !com.hd.emojibattery.runtime.FeatureApply.stickers(requireActivity(), draft)) return
        toast("Sticker applied")
        navigate(ScreenIds.HOME)
    }

    private fun showAdjustment(itemId: String? = null) {
        if (adjustmentDialog?.isShowing == true) return
        val targetId = itemId ?: previewId ?: run { toast("Choose a sticker first"); return }
        val uiTest = com.hd.emojibattery.BuildConfig.UI_TEST_MODE
        val item = selectedSticker(targetId)
        if (item == null) { toast("Choose a sticker first"); return }
        if (!uiTest && !com.hd.emojibattery.runtime.FeatureApply.permission(requireActivity())) return
        val activity = requireActivity()
        val draft = org.json.JSONObject(item.toString())
        val live = if (uiTest) null else com.hd.emojibattery.runtime.StickerEditPreview(activity)
        var committed = false
        fun option(name: String) = "${name}_$targetId"
        val dialog = BottomSheetDialog(requireContext())
        adjustmentDialog = dialog
        val controls = layoutInflater.inflate(R.layout.sheet_sticker_adjustment, null)
        dialog.setContentView(controls)
        val viewport = controls.findViewById<View>(R.id.adjust_preview_viewport)
        val previewScreen = controls.findViewById<android.widget.FrameLayout>(R.id.adjust_preview_screen)
        val previewSticker = controls.findViewById<android.widget.ImageView>(R.id.adjust_preview_sticker)
        com.hd.emojibattery.runtime.MediaAssets.load(previewSticker, com.hd.emojibattery.runtime.AppliedContent.url(item))
        val size = controls.findViewById<android.widget.SeekBar>(R.id.adjust_size)
        val rotate = controls.findViewById<android.widget.SeekBar>(R.id.adjust_rotation)
        size.progress = prefs.getInt(option("sticker_size"), 50)
        rotate.progress = prefs.getInt(option("sticker_rotation"), 0)
        var offsetX = prefs.getFloat(option("sticker_offset_x"), 0f)
        var offsetY = prefs.getFloat(option("sticker_offset_y"), 0f)
        var previousSize = com.hd.emojibattery.runtime.StickerEditPreview.sizePixels(activity, size.progress)
        fun updateDraft() {
            draft.put("overlay_size", size.progress).put("overlay_rotation", rotate.progress)
                .put("overlay_x", offsetX).put("overlay_y", offsetY)
        }
        fun updatePositionPreview() {
            if (viewport.height == 0) return
            val metrics = resources.displayMetrics
            val scale = minOf(viewport.width.toFloat() / metrics.widthPixels, viewport.height.toFloat() / metrics.heightPixels)
            previewScreen.layoutParams = previewScreen.layoutParams.apply {
                width = (metrics.widthPixels * scale).toInt()
                height = (metrics.heightPixels * scale).toInt()
            }
            val pixels = com.hd.emojibattery.runtime.StickerEditPreview.sizePixels(activity, size.progress)
            previewSticker.layoutParams = previewSticker.layoutParams.apply {
                width = (pixels * scale).toInt().coerceAtLeast(1)
                height = (pixels * scale).toInt().coerceAtLeast(1)
            }
            previewSticker.translationX = offsetX * scale
            previewSticker.translationY = (80 * metrics.density + offsetY) * scale
            previewSticker.rotation = rotate.progress.toFloat()
        }
        viewport.doOnNextLayout { updatePositionPreview() }
        fun updateLive() {
            val nextSize = com.hd.emojibattery.runtime.StickerEditPreview.sizePixels(activity, size.progress)
            // Keep the image center fixed, retaining half pixels to avoid drift on repeated resizing.
            val centerOffset = (previousSize - nextSize) / 2f
            offsetX += centerOffset
            offsetY += centerOffset
            previousSize = nextSize
            updateDraft()
            live?.update(draft)
            offsetX = draft.optDouble("overlay_x").toFloat()
            offsetY = draft.optDouble("overlay_y").toFloat()
            updatePositionPreview()
        }
        updateDraft()
        val confirm = controls.findViewById<View>(R.id.adjust_apply)
        confirm.isEnabled = uiTest
        confirm.alpha = if (uiTest) 1f else .5f
        if (live != null) {
            if (!live.show(draft) { ready ->
                    confirm.isEnabled = ready
                    confirm.alpha = if (ready) 1f else .5f
                    if (!ready && isAdded) { toast("Could not load sticker. Try again."); dialog.dismiss() }
                }) {
                live.close()
                com.hd.emojibattery.runtime.MediaAssets.clear(previewSticker)
                adjustmentDialog = null
                toast("Could not show sticker. Check overlay permission.")
                return
            }
            com.hd.emojibattery.runtime.FeatureApply.decorations(activity)
            offsetX = draft.optDouble("overlay_x").toFloat()
            offsetY = draft.optDouble("overlay_y").toFloat()
        }
        dialog.setOnDismissListener {
            com.hd.emojibattery.runtime.MediaAssets.clear(previewSticker)
            live?.close()
            adjustmentDialog = null
            if (!uiTest && !committed && android.provider.Settings.canDrawOverlays(activity)) {
                com.hd.emojibattery.runtime.FeatureApply.decorations(activity)
            }
        }
        fun labels() {
            controls.findViewById<android.widget.TextView>(R.id.adjust_size_label).text = "${140 + size.progress * 280 / 100}"
            controls.findViewById<android.widget.TextView>(R.id.adjust_rotation_label).text = "${rotate.progress}°"
        }
        labels()
        listOf(size, rotate).forEach { it.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, user: Boolean) { labels(); updateLive() }
            override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
        }) }
        controls.findViewById<View>(R.id.adjust_size_plus).setOnClickListener { size.progress += 5 }
        controls.findViewById<View>(R.id.adjust_size_minus).setOnClickListener { size.progress -= 5 }
        controls.findViewById<View>(R.id.adjust_rotate_left).setOnClickListener { rotate.progress = (rotate.progress + 345) % 360 }
        controls.findViewById<View>(R.id.adjust_rotate_right).setOnClickListener { rotate.progress = (rotate.progress + 15) % 360 }
        controls.findViewById<View>(R.id.adjust_up).setOnClickListener { offsetY -= 8; updateLive() }
        controls.findViewById<View>(R.id.adjust_down).setOnClickListener { offsetY += 8; updateLive() }
        controls.findViewById<View>(R.id.adjust_left).setOnClickListener { offsetX -= 8; updateLive() }
        controls.findViewById<View>(R.id.adjust_right).setOnClickListener { offsetX += 8; updateLive() }
        controls.findViewById<View>(R.id.adjust_apply).setOnClickListener {
            if (!uiTest && live?.ready != true) return@setOnClickListener
            updateDraft()
            // End the transient overlay before the service renders the committed sticker.
            live?.close()
            if (!uiTest && !com.hd.emojibattery.runtime.FeatureApply.stickers(activity, draft)) {
                dialog.dismiss()
                return@setOnClickListener
            }
            committed = true
            dialog.dismiss()
            toast("Sticker applied")
            navigate(ScreenIds.HOME)
        }
        trackDialog(dialog)
        dialog.setOnShowListener {
            dialog.findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.layoutParams = it.layoutParams.apply { height = (resources.displayMetrics.heightPixels * .9f).toInt() }
                controls.layoutParams = controls.layoutParams.apply { height = android.view.ViewGroup.LayoutParams.MATCH_PARENT }
                it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(it).apply {
                    state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
            dialog.window?.setDimAmount(0f)
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
            data?.data?.let { uri -> runCatching { requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; prefs.edit().putString("custom_sticker_uri", uri.toString()).apply(); previewId = "custom"; toast("Sticker image saved"); bindFeature() }
        }
    }

    override fun onStop() {
        adjustmentDialog?.dismiss()
        super.onStop()
    }

    override fun onDestroyView() {
        returningSelection = false
        adjustmentDialog?.dismiss()
        _binding?.root?.findViewById<EditText>(R.id.sticker_search)?.removeTextChangedListener(watcher)
        watcher = null
        super.onDestroyView()
    }
}
