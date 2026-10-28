package com.hd.emojibattery.ui.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.hd.emojibattery.PetOverlayService
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.common.CatalogAdapter
import com.hd.emojibattery.ui.common.CategoryAdapter
import com.hd.emojibattery.ui.common.toCatalogEntries
import com.hd.emojibattery.ui.navigation.ScreenIds

open class PetFragment<VM : PetViewModel> : BaseFeatureFragment<VM>() {
    private var adapter: CatalogAdapter? = null
    private val categories = listOf("Naruto", "BTS", "One Piece", "Halloween", "Undertale", "Pokemon", "Mario", "Sonic", "LOL", "Character", "Marvel", "Attack on Titan", "SpongeBob", "Rick & Morty", "Anime Star", "Stitch", "Bleach", "Genshin Impact", "Horror", "Loopy")

    override fun bindFeature() {
        val panel = view<View>(R.id.feature_pet)
        val expanded = screen in setOf(ScreenIds.PET_MORE, ScreenIds.PET_MORE_SELECTED, ScreenIds.PET_SORT, ScreenIds.PET_APPLYING, ScreenIds.PET_RATING, ScreenIds.PET_EXIT)
        val basePet = screen == ScreenIds.PET || screen == ScreenIds.PET_STATE_2 || screen == ScreenIds.PET_STATE_3
        val detail = screen == ScreenIds.PET_DETAIL
        val result = false
        val browse = basePet || expanded
        panel.findViewById<View>(R.id.pet_browse_header).isVisible = expanded
        panel.findViewById<View>(R.id.pet_browse_back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        panel.findViewById<View>(R.id.pet_enable).parent.let { (it as View).isVisible = basePet }
        panel.findViewById<View>(R.id.pet_slots_title).isVisible = basePet
        panel.findViewById<View>(R.id.pet_slots).isVisible = basePet
        panel.findViewById<View>(R.id.pet_hero).setBackgroundResource(if (expanded) android.R.color.transparent else R.drawable.bg_home_panel)
        panel.findViewById<View>(R.id.pet_top_actions).isVisible = basePet
        panel.findViewById<View>(R.id.pet_hero).isVisible = browse || detail || result
        panel.findViewById<View>(R.id.pet_more).isVisible = basePet
        panel.findViewById<View>(R.id.pet_categories).isVisible = browse
        panel.findViewById<View>(R.id.pet_grid).isVisible = browse
        panel.findViewById<View>(R.id.pet_choose_header).isVisible = basePet
        panel.findViewById<View>(R.id.pet_detail_card).isVisible = detail || result
        panel.findViewById<View>(R.id.pet_detail_emoji).let { (it as android.widget.TextView).text = prefs.getString("pet_emoji", "🐱") }
        panel.findViewById<View>(R.id.pet_detail_title).let { (it as android.widget.TextView).text = when (screen) { ScreenIds.PET_RATING -> "Enjoying your new pet?"; else -> prefs.getString("pet_name", "Your pet is ready") } }
        panel.findViewById<View>(R.id.pet_position).isVisible = basePet || detail
        panel.findViewById<View>(R.id.pet_motion).isVisible = basePet || detail
        panel.findViewById<View>(R.id.pet_apply).isVisible = screen == ScreenIds.PET_MORE_SELECTED || detail || basePet
        panel.findViewById<View>(R.id.pet_more).setOnClickListener { navigate(ScreenIds.PET_MORE) }
        panel.findViewById<View>(R.id.pet_sort).setOnClickListener { navigate(ScreenIds.PET_SORT) }
        panel.findViewById<View>(R.id.pet_search).setOnClickListener { navigate(ScreenIds.SEARCH) }
        panel.findViewById<View>(R.id.pet_library).setOnClickListener { navigate(ScreenIds.FAVORITES) }
        panel.findViewById<View>(R.id.pet_premium).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.pet_enable).setOnClickListener { (requireActivity() as com.hd.emojibattery.MainActivity).enableChargingMonitor() }
        if (screen == ScreenIds.PET_STATE_2) {
            binding.root.findViewById<View>(R.id.pet_permission_dismiss).setOnClickListener { navigate(ScreenIds.PET) }
            binding.root.findViewById<View>(R.id.pet_permission_continue).setOnClickListener { openOverlaySettings() }
        }
        val flowModal = binding.root.findViewById<View>(R.id.pet_flow_modal)
        flowModal.findViewById<View>(R.id.pet_download_card).isVisible = screen == ScreenIds.PET_APPLYING
        flowModal.findViewById<View>(R.id.pet_rating_card).isVisible = screen == ScreenIds.PET_RATING
        flowModal.findViewById<View>(R.id.pet_sort_card).isVisible = screen == ScreenIds.PET_SORT
        flowModal.findViewById<View>(R.id.pet_exit_card).isVisible = screen == ScreenIds.PET_EXIT
        flowModal.findViewById<View>(R.id.pet_download_cancel).setOnClickListener { navigate(ScreenIds.PET) }
        flowModal.findViewById<View>(R.id.pet_rating_later).setOnClickListener { navigate(ScreenIds.PET) }
        flowModal.findViewById<View>(R.id.pet_rating_stars).setOnClickListener {
            val stars = flowModal.findViewById<android.widget.TextView>(R.id.pet_rating_stars)
            val rating = (prefs.getInt("pet_rating", 0) % 5) + 1
            prefs.edit().putInt("pet_rating", rating).apply()
            stars.text = "${"★ ".repeat(rating)}${"☆ ".repeat(5 - rating)}".trim()
            stars.setTextColor(0xffffb84d.toInt())
        }
        flowModal.findViewById<View>(R.id.pet_rating_rate).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${requireContext().packageName}"))
            runCatching { startActivity(intent) }.onFailure { toast("Thank you for your feedback!") }
            navigate(ScreenIds.PET)
        }
        flowModal.findViewById<View>(R.id.pet_sort_close).setOnClickListener { navigate(ScreenIds.PET_MORE) }
        val sortList = flowModal.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.pet_sort_categories)
        sortList.layoutManager = GridLayoutManager(requireContext(), 3)
        sortList.adapter = CategoryAdapter { value -> prefs.edit().putString("pet_sort", value).apply(); navigate(ScreenIds.PET_MORE) }
            .also { it.submit(categories, prefs.getString("pet_sort", "Naruto") ?: "Naruto") }
        flowModal.findViewById<View>(R.id.pet_exit_keep).setOnClickListener { navigate(ScreenIds.PET) }
        flowModal.findViewById<View>(R.id.pet_exit_confirm).setOnClickListener { prefs.edit().putBoolean("pet_overlay_enabled", false).apply(); navigate(ScreenIds.HOME) }
        panel.findViewById<View>(R.id.pet_position).setOnClickListener { choose("Position", listOf("Bottom right", "Bottom left", "Top right", "Free drag"), "pet_position") }
        panel.findViewById<View>(R.id.pet_motion).setOnClickListener { choose("Movement", listOf("Idle", "Walk", "Bounce", "Follow touch"), "pet_motion") }
        panel.findViewById<View>(R.id.pet_apply).setOnClickListener { applyPet() }
        val cat = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.pet_categories)
        cat.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        cat.adapter = CategoryAdapter { value -> prefs.edit().putString("pet_category", value).apply(); bindFeature() }
            .also { it.submit(categories, prefs.getString("pet_category", "Naruto") ?: "Naruto") }
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.pet_grid)
        grid.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = CatalogAdapter(onSelected = { item ->
            viewModel.selectPet(item.id)
            prefs.edit().putString("pet_selected_id", item.id).putString("pet_emoji", item.emoji).putString("pet_name", item.name).apply()
            if (basePet) {
                val selected = prefs.getStringSet("selected_pets", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (item.id in selected) selected.remove(item.id) else if (selected.size < 6) selected.add(item.id)
                prefs.edit().putStringSet("selected_pets", selected).putString("pet_emoji_${item.id}", item.emoji).apply()
                navigate(ScreenIds.PET_STATE_3)
            } else if (expanded) navigate(ScreenIds.PET_MORE_SELECTED) else navigate(ScreenIds.PET_DETAIL, android.os.Bundle().apply {
                putString("pet_id", item.id); putString("pet_name", item.name); putString("pet_emoji", item.emoji)
            })
        }, onFavorite = { item -> prefs.edit().putBoolean("fav_${item.id}", !item.favorite).apply(); bindFeature() })
            .also { it.submit(catalog().array("pets").toCatalogEntries { prefs.getBoolean("fav_$it", false) }) }
        grid.adapter = adapter
        val selectedPets = prefs.getStringSet("selected_pets", emptySet())?.toList().orEmpty()
        val slots = panel.findViewById<android.widget.GridLayout>(R.id.pet_slots)
        panel.findViewById<android.widget.TextView>(R.id.pet_slots_title).text = "Get Your Pet (${selectedPets.size}/6)"
        for (index in 0 until slots.childCount) {
            val slot = slots.getChildAt(index) as android.widget.TextView
            slot.text = selectedPets.getOrNull(index)?.let { "${prefs.getString("pet_emoji_$it", "🐱")}  ✕" } ?: if (index == 0 && selectedPets.isEmpty()) "＋\nAdd new" else "＋"
            slot.setOnClickListener {
                val id = selectedPets.getOrNull(index)
                if (id == null) navigate(ScreenIds.PET_MORE) else {
                    val updated = selectedPets.toMutableSet().apply { remove(id) }
                    prefs.edit().putStringSet("selected_pets", updated).apply()
                    navigate(ScreenIds.PET_STATE_3)
                }
            }
        }
        if (screen == ScreenIds.PET_APPLYING || screen == ScreenIds.PET_RATING) {
            panel.findViewById<android.widget.TextView>(R.id.pet_apply).text = if (screen == ScreenIds.PET_RATING) "Done" else "Your pet is ready — Continue"
            panel.findViewById<View>(R.id.pet_apply).setOnClickListener { navigate(if (screen == ScreenIds.PET_RATING) ScreenIds.HOME else ScreenIds.PET_RATING) }
        }
    }

    private fun choose(title: String, values: List<String>, key: String) {
        AlertDialog.Builder(requireContext()).setTitle(title).setItems(values.toTypedArray()) { _, index ->
            prefs.edit().putString(key, values[index]).apply()
            bindFeature()
        }.show()
    }

    private fun applyPet() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(requireContext())) {
            navigate(ScreenIds.PET_STATE_2)
            return
        }
        prefs.edit().putBoolean("pet_overlay_enabled", true).apply()
        requireContext().startService(Intent(requireContext(), PetOverlayService::class.java))
        navigate(ScreenIds.PET_APPLYING)
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}")))
        toast("Allow display over other apps, then tap Apply again.")
    }
}
