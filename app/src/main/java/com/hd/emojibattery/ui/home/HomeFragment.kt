package com.hd.emojibattery.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.common.CatalogAdapter
import com.hd.emojibattery.ui.common.CategoryAdapter
import com.hd.emojibattery.ui.common.toCatalogEntries
import com.hd.emojibattery.ui.navigation.ScreenIds

open class HomeFragment<VM : HomeViewModel> : BaseFeatureFragment<VM>() {
    private val categories = listOf("Halloween", "Christmas", "Premier League", "Animals", "Cute", "Food")
    private var itemsAdapter: CatalogAdapter? = null
    private var categoryAdapter: CategoryAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.selectCategory(prefs.getString("home_category", "Halloween") ?: "Halloween")
    }

    override fun bindFeature() {
        val panel = view<View>(R.id.feature_home)
        val themesMode = screen == ScreenIds.THEME_CATEGORY
        val categoriesRecycler = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.home_categories)
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.home_grid)
        panel.findViewById<View>(R.id.home_search).setOnClickListener { navigate(ScreenIds.SEARCH) }
        panel.findViewById<View>(R.id.home_library).setOnClickListener { navigate(ScreenIds.FAVORITES) }
        panel.findViewById<View>(R.id.home_premium).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.home_banner).setOnClickListener { navigate(ScreenIds.ADD_STICKER) }
        panel.findViewById<View>(R.id.shortcut_pet).setOnClickListener { navigate(ScreenIds.PET) }
        panel.findViewById<View>(R.id.shortcut_diy).setOnClickListener { navigate(ScreenIds.CUSTOMIZE) }
        panel.findViewById<View>(R.id.shortcut_charge).setOnClickListener { navigate(ScreenIds.CHARGE_PREVIEW) }
        panel.findViewById<View>(R.id.shortcut_troll).setOnClickListener { navigate(ScreenIds.BATTERY_TROLL) }
        val enableSwitch = panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.home_enable)
        enableSwitch.isChecked = prefs.getBoolean("enabled", false)
        enableSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled) (requireActivity() as com.hd.emojibattery.MainActivity).enableChargingMonitor()
            else (requireActivity() as com.hd.emojibattery.MainActivity).disableChargingMonitor()
        }
        panel.findViewById<View>(R.id.home_sort).setOnClickListener { navigate(ScreenIds.SORT) }
        val chosen = viewModel.category.value ?: "Halloween"
        categoriesRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        categoryAdapter = CategoryAdapter { value ->
            viewModel.selectCategory(value)
            prefs.edit().putString("home_category", value).apply()
            bindFeature()
        }.also { it.submit(categories, chosen) }
        categoriesRecycler.adapter = categoryAdapter
        grid.layoutManager = GridLayoutManager(requireContext(), if (themesMode) 2 else 3)
        itemsAdapter = CatalogAdapter(onSelected = { item ->
            val args = Bundle().apply { putString("content_id", item.id) }
            navigate(if (themesMode) ScreenIds.CHARGE_PREVIEW else ScreenIds.STICKER_DETAIL, args)
        }, onFavorite = { item ->
            prefs.edit().putBoolean("fav_${item.id}", !item.favorite).apply()
            bindFeature()
        }).also { adapter ->
            val key = if (themesMode) "themes" else "stickers"
            var entries = catalog().array(key).toCatalogEntries { prefs.getBoolean("fav_$it", false) }
            if (!themesMode && chosen != "Halloween") entries = entries.filter { item ->
                val json = catalog().array("stickers")
                (0 until (json?.length() ?: 0)).any { i -> json?.optJSONObject(i)?.let { it.optString("id", it.optString("name")) == item.id && it.optString("category").equals(chosen, true) } == true }
            }
            adapter.submit(entries)
        }
        grid.adapter = itemsAdapter
        panel.findViewById<View>(R.id.home_banner).isVisible = !themesMode
    }
}
