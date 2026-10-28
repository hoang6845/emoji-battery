package com.hd.emojibattery.ui.stickers

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.common.CatalogAdapter
import com.hd.emojibattery.ui.common.CategoryAdapter
import com.hd.emojibattery.ui.common.toCatalogEntries
import com.hd.emojibattery.ui.navigation.ScreenIds

open class StickerFragment<VM : StickerViewModel> : BaseFeatureFragment<VM>() {
    private val categories = listOf("Halloween", "Christmas", "Premier League", "Animals", "Cute", "Food")
    private var adapter: CatalogAdapter? = null
    private var watcher: TextWatcher? = null

    override fun bindFeature() {
        val panel = view<View>(R.id.feature_stickers)
        val search = panel.findViewById<EditText>(R.id.sticker_search)
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
            val count = prefs.all.count { it.key.startsWith("sticker_selected_") && it.value == true }
            toast("$count sticker(s) selected")
        }
        val categoriesView = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_categories)
        categoriesView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        categoriesView.adapter = CategoryAdapter { value -> prefs.edit().putString("sticker_category", value).apply(); updateCatalog() }
            .also { it.submit(categories, prefs.getString("sticker_category", "Halloween") ?: "Halloween") }
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sticker_grid)
        grid.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = CatalogAdapter(onSelected = { item ->
            val route = if (screen == ScreenIds.STICKER_EDIT || screen == ScreenIds.ADD_STICKER_STATE_2) ScreenIds.STICKER_EDIT else ScreenIds.STICKER_DETAIL
            navigate(route, android.os.Bundle().apply { putString("content_id", item.id); putString("content_name", item.name); putString("content_emoji", item.emoji) })
        }, onFavorite = { item -> prefs.edit().putBoolean("fav_${item.id}", !item.favorite).apply(); updateCatalog() })
        grid.adapter = adapter
        if (screen == ScreenIds.STICKER_EDIT || screen == ScreenIds.ADD_STICKER_STATE_2) panel.findViewById<android.widget.TextView>(R.id.sticker_apply).text = "Save sticker"
        updateCatalog()
    }

    private fun updateCatalog() {
        val all = catalog().array("stickers").toCatalogEntries { prefs.getBoolean("fav_$it", false) }
        val category = prefs.getString("sticker_category", "Halloween") ?: "Halloween"
        val query = viewModel.query.value.orEmpty().trim()
        val filtered = all.filter { item ->
            val catJson = catalog().array("stickers")
            val categoryMatches = screen == ScreenIds.SEARCH || screen == ScreenIds.FAVORITES || screen == ScreenIds.FAVORITES_STATE_2 || screen == ScreenIds.FAVORITES_DELETE || category == "Halloween" ||
                (0 until (catJson?.length() ?: 0)).any { i -> catJson?.optJSONObject(i)?.let { it.optString("id", it.optString("name")) == item.id && it.optString("category").equals(category, true) } == true }
            categoryMatches && (screen !in listOf(ScreenIds.FAVORITES, ScreenIds.FAVORITES_STATE_2, ScreenIds.FAVORITES_DELETE) || item.favorite) &&
                (query.isEmpty() || item.name.contains(query, true) || item.emoji.contains(query))
        }
        adapter?.submit(filtered)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        startActivityForResult(Intent.createChooser(intent, "Choose sticker image"), 541)
    }

    @Deprecated("Legacy callback required by the minimum SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 541 && resultCode == android.app.Activity.RESULT_OK) {
            data?.data?.let { uri -> prefs.edit().putString("custom_sticker_uri", uri.toString()).apply(); toast("Sticker image saved") }
        }
    }

    override fun onDestroyView() {
        _binding?.root?.findViewById<EditText>(R.id.sticker_search)?.removeTextChangedListener(watcher)
        watcher = null
        super.onDestroyView()
    }
}
