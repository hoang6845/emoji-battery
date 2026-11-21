package com.hd.emojibattery.ui.home

import com.hd.emojibattery.runtime.Favorites
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
    private var itemsAdapter: CatalogAdapter? = null
    private var categoryAdapter: HomeCategoryAdapter? = null
    private var homePanel: View? = null
    private var homeGrid: HomeCatalogRecyclerView? = null

    // The header can be recycled off screen; permission callbacks still update it.
    fun enableControlsRoot(): View? = homePanel

    override fun onDestroyView() {
        // Release attached artwork before removing the adapter/header hierarchy.
        super.onDestroyView()
        homeGrid?.adapter = null
        homeGrid = null
        homePanel = null
        itemsAdapter = null
        categoryAdapter = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.selectCategory(
            prefs.getString(
                "home_category",
                "All"
            ) ?: "All"
        )
    }

    override fun bindFeature() {
        val content = catalog().collection("battery_styles")
        val categories = com.hd.emojibattery.runtime.CatalogGroups.names(content, "battery_styles")
        val panel = homePanel ?: view<View>(R.id.feature_home).also { homePanel = it }
        val themesMode = screen == ScreenIds.THEME_CATEGORY
        val categoriesRecycler =
            panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.home_categories)
        val grid = homeGrid ?: panel.findViewById<HomeCatalogRecyclerView>(R.id.home_grid)
            .also { homeGrid = it }
        val gallery = panel.findViewById<View>(R.id.home_gallery)
        // Expand only the Home panel. Keep the shared content padding for other screens
        // and for siblings such as the catalog loading/offline message.
        (panel.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
            marginStart = -binding.featureContent.paddingStart
            marginEnd = -binding.featureContent.paddingEnd
            panel.layoutParams = this
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            gallery.outlineAmbientShadowColor = 0xff6545f5.toInt()
            gallery.outlineSpotShadowColor = 0xff6545f5.toInt()
        }
        panel.findViewById<View>(R.id.home_search).setOnClickListener { navigate(ScreenIds.SEARCH) }
        panel.findViewById<View>(R.id.home_library)
            .setOnClickListener { navigate(ScreenIds.FAVORITES) }
        panel.findViewById<View>(R.id.home_premium)
            .setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.home_banner)
            .setOnClickListener { navigate(ScreenIds.ADD_STICKER) }
        panel.findViewById<View>(R.id.shortcut_pet).setOnClickListener { navigate(ScreenIds.PET) }
        panel.findViewById<View>(R.id.shortcut_diy)
            .setOnClickListener { navigate(ScreenIds.STATUS_BAR) }
        panel.findViewById<View>(R.id.shortcut_charge)
            .setOnClickListener { navigate(ScreenIds.ANIMATION) }
        panel.findViewById<View>(R.id.shortcut_troll)
            .setOnClickListener { navigate(ScreenIds.BATTERY_TROLL) }
        val enableSwitch =
            panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.home_enable)
        enableSwitch.setOnCheckedChangeListener(null)
        enableSwitch.isChecked = (requireActivity() as com.hd.emojibattery.MainActivity).isEmojiEnabled()
        enableSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled) (requireActivity() as com.hd.emojibattery.MainActivity).requestEmojiEnable()
            else (requireActivity() as com.hd.emojibattery.MainActivity).disableBatteryOverlay()
        }
        panel.findViewById<View>(R.id.home_sort).setOnClickListener { showCategories(categories) }
        val chosen = viewModel.category.value.takeIf { it in categories } ?: "All"
        if (categoriesRecycler.layoutManager == null) categoriesRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        categoryAdapter =
            ((categoriesRecycler.adapter as? HomeCategoryAdapter) ?: HomeCategoryAdapter { value ->
                selectCategory(value, categories)
            }).also { it.submit(categories, chosen) }
        if (categoriesRecycler.adapter !== categoryAdapter) categoriesRecycler.adapter =
            categoryAdapter
        itemsAdapter = (itemsAdapter ?: CatalogAdapter(onSelected = { item ->
            val args = Bundle().apply { putString("content_id", item.id) }
            navigate(ScreenIds.BATTERY_STYLE_PREVIEW, args)
        }, onFavorite = { item ->
            Favorites.set(prefs, "battery_styles", item.id, !item.favorite)
            itemsAdapter?.updateFavorite(item.id, !item.favorite)
        })).also { adapter ->
            val allowed =
                com.hd.emojibattery.runtime.CatalogGroups.ids(content, "battery_styles", chosen)
            val entries = content.optJSONArray("battery_styles")
                .toCatalogEntries { Favorites.contains(prefs, "battery_styles", it) }
                .filter { it.id in allowed }
            adapter.submit(entries)
        }
        if (grid.adapter == null) {
            // A bounded RecyclerView owns the whole page. The former ScrollView
            // content becomes one full-span header, preserving all native controls.
            (grid.parent as android.view.ViewGroup).removeView(grid)
            gallery.setPadding(gallery.paddingLeft, gallery.paddingTop, gallery.paddingRight, 0)
            gallery.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                val radius = 35 * resources.displayMetrics.density
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            panel.setPadding(panel.paddingLeft, panel.paddingTop, panel.paddingRight, 0)
            binding.featureContent.setPadding(binding.featureContent.paddingLeft,
                binding.featureContent.paddingTop, binding.featureContent.paddingRight, 0)
            binding.featureScroll.removeView(binding.featureContent)
            binding.featureScroll.isVisible = false
            val host = binding.featureScroll.parent as android.view.ViewGroup
            host.addView(grid, host.indexOfChild(binding.featureScroll) + 1,
                android.widget.FrameLayout.LayoutParams(-1, -1))
            grid.showCatalog(binding.featureContent, requireNotNull(itemsAdapter), if (themesMode) 2 else 3)
        }
        panel.findViewById<View>(R.id.home_banner).isVisible = !themesMode
    }

    private fun selectCategory(value: String, categories: List<String>) {
        viewModel.selectCategory(value)
        prefs.edit().putString("home_category", value).apply()
        bindFeature()
        val recycler = homePanel?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.home_categories)
            ?: return
        val position = categories.indexOf(value)
        if (position >= 0) {
            (recycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                position,
                0
            )
        }
    }

    private fun showCategories(categories: List<String>) {
        val context = requireContext()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

        val dialog = android.app.Dialog(context)
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        val list = androidx.recyclerview.widget.RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 3)
            setPadding(dp(10), dp(24), dp(9), dp(25))
            clipToPadding = false
            background = rounded(android.graphics.Color.WHITE, 26)
            adapter = CategoryAdapter(pillGrid = true) { category ->
                dialog.dismiss()
                selectCategory(category, categories)
            }.also { it.submit(categories.filterNot { name -> name == "All" }, "") }
        }
        // Keep the seven-row Figma panel; allow scrolling on smaller displays.
        val height =
            minOf(dp(441), resources.displayMetrics.heightPixels - dp(160)).coerceAtLeast(dp(112))
        container.addView(list, android.widget.LinearLayout.LayoutParams(-1, height))
        val close = androidx.appcompat.widget.AppCompatImageButton(context).apply {
            setImageResource(R.drawable.ic_home_category_close)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(0xffbfbfbf.toInt(), 43)
            contentDescription = context.getString(R.string.home_category_close)
            setOnClickListener { dialog.dismiss() }
        }
        container.addView(close, android.widget.LinearLayout.LayoutParams(dp(30), dp(30)).apply {
            topMargin = dp(20)
        })
        dialog.setContentView(container)
        trackDialog(dialog)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(minOf(dp(391), resources.displayMetrics.widthPixels - dp(22)), -2)
            setGravity(android.view.Gravity.CENTER)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }
    }
}
