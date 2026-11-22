package com.hd.emojibattery.ui.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.hd.emojibattery.runtime.Favorites
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
    private var categoryPickerOpen = false
    private var pendingPetId: String? = null
    private var autoPetHandled = false
    private fun petSlots(): List<String> {
        val stored = prefs.getString("pet_slot_ids", null)
        val values = if (stored != null) {
            val json = runCatching { org.json.JSONArray(stored) }.getOrDefault(org.json.JSONArray())
            (0 until json.length()).map { json.optString(it) }
        } else {
            val selected = prefs.getStringSet("selected_pets", emptySet()).orEmpty()
            val applied = runCatching { org.json.JSONArray(prefs.getString("applied_pets", "[]")) }.getOrDefault(org.json.JSONArray())
            val ordered = (0 until applied.length()).mapNotNull { applied.optJSONObject(it)?.optString("id") }.filter { it in selected }
            ordered + selected.filter { it !in ordered }
        }
        return List(6) { values.getOrNull(it).orEmpty() }
    }
    private fun saveSlots(editor: android.content.SharedPreferences.Editor, slots: List<String>) {
        editor.putString("pet_slot_ids", org.json.JSONArray(slots).toString())
            .putStringSet("selected_pets", slots.filter { it.isNotBlank() }.toSet())
    }
    override fun onResume() {
        super.onResume()
        if (_binding != null) bindFeature()
        if (!autoPetHandled) arguments?.getString("apply_pet_id")?.let { id ->
            autoPetHandled = true
            pendingPetId = id
            confirmPetSelection()
        }
    }
    private val categoryBack = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { closeCategoryPicker() }
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        categoryPickerOpen = savedInstanceState?.getBoolean("pet_category_picker_open") ?: false
        pendingPetId = savedInstanceState?.getString("pending_pet_id") ?: arguments?.getString("replace_pet_id") ?: arguments?.getString("preselected_pet_id")
        autoPetHandled = savedInstanceState?.getBoolean("pet_auto_handled") ?: false
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, categoryBack)
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("pet_category_picker_open", categoryPickerOpen)
        outState.putString("pending_pet_id", pendingPetId)
        outState.putBoolean("pet_auto_handled", autoPetHandled)
    }

    private fun closeCategoryPicker() {
        categoryPickerOpen = false
        categoryBack.isEnabled = false
        if (screen == ScreenIds.PET_SORT) {
            if (parentFragmentManager.backStackEntryCount > 0) requireActivity().onBackPressedDispatcher.onBackPressed()
            else navigate(ScreenIds.PET)
        } else bindFeature()
    }

    private fun slotUnlocked(index: Int): Boolean = index == 0 ||
        com.hd.emojibattery.BuildConfig.UI_TEST_MODE ||
        tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed() ||
        prefs.getBoolean("pet_slot_unlocked_$index", false) || petSlots().getOrNull(index)?.isNotBlank() == true

    private fun unlockSlot(index: Int, next: () -> Unit) {
        (requireActivity() as com.hd.emojibattery.MainActivity).batteryAds.unlock(this, "pet_slot_$index") {
            prefs.edit().putBoolean("pet_slot_unlocked_$index", true).apply()
            next()
        }
    }

    private fun confirmPetSelection() {
        val id = pendingPetId ?: return
        val pet = catalog().array("pets")?.let { items ->
            (0 until items.length()).mapNotNull { items.optJSONObject(it) }.firstOrNull { it.optString("id") == id }
        } ?: run { toast("This pet is no longer available"); return }
        val selected = petSlots().toMutableList()
        val index = arguments?.getInt("pet_slot_index", -1)?.takeIf { it in selected.indices }
            ?: selected.indexOfFirst { it.isBlank() }
        if (index < 0) { toast("Remove a pet first (maximum 6)"); return }
        if (com.hd.emojibattery.ads.SessionUnlocks.isLocked(requireContext(), id)) {
            (requireActivity() as com.hd.emojibattery.MainActivity).batteryAds.unlock(this, id, ::confirmPetSelection)
            return
        }
        if (!slotUnlocked(index)) { unlockSlot(index, ::confirmPetSelection); return }
        selected[index] = id
        requirePetPermissions {
            applyPet(selected) {
                prefs.edit().putString("pet_selected_id", id).putString("pet_name", pet.optString("name"))
                    .putString("pet_emoji", pet.optString("emoji")).putString("pet_emoji_$id", pet.optString("emoji")).apply()
                if (screen == ScreenIds.PET) bindFeature() else finishFeature(ScreenIds.PET)
            }
        }
    }
    private val previewCategories = listOf("Naruto", "BTS", "One Piece", "Halloween", "Undertale", "Pokemon", "Mario", "Sonic", "LOL", "Character", "Marvel", "Attack on Titan", "SpongeBob", "Rick & Morty", "Anime Star", "Stitch", "Bleach", "Genshin Impact", "Horror", "Loopy")

    override fun bindFeature() {
        // Resolve once per bind: looking up categories inside the item filter used
        // to serialize and parse the entire catalog for every pet on the UI thread.
        val content = catalog().collection("pets")
        val raw = content.optJSONArray("pets")
        val categories = if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) previewCategories else
            com.hd.emojibattery.runtime.CatalogGroups.names(content, "pets")
        val petsById = (0 until (raw?.length() ?: 0)).mapNotNull { raw?.optJSONObject(it) }
            .associateBy { it.optString("id") }
        // A temporarily empty remote catalog must not erase saved pet slots.
        val panel = view<View>(R.id.feature_pet)
        val density = resources.displayMetrics.density
        panel.findViewById<android.widget.TextView>(R.id.pet_more).apply {
            text = "View more"
            setBackgroundResource(R.drawable.bg_pet_more)
            setTextColor(0xff27205f.toInt())
            layoutParams.height = (36 * density).toInt()
        }
        panel.findViewById<View>(R.id.pet_sort).setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val expanded = screen in setOf(ScreenIds.PET_MORE, ScreenIds.PET_MORE_SELECTED, ScreenIds.PET_SORT, ScreenIds.PET_APPLYING, ScreenIds.PET_RATING, ScreenIds.PET_EXIT)
        val basePet = screen == ScreenIds.PET || screen == ScreenIds.PET_STATE_2 || screen == ScreenIds.PET_STATE_3
        val choosingPet = screen == ScreenIds.PET_MORE || screen == ScreenIds.PET_MORE_SELECTED || screen == ScreenIds.PET_SORT
        panel.findViewById<View>(R.id.pet_home_back).apply {
            isVisible = false
            setOnClickListener { (requireActivity() as com.hd.emojibattery.MainActivity).openScreen(ScreenIds.HOME, false) }
        }
        val detail = screen == ScreenIds.PET_DETAIL
        val result = false
        val browse = basePet || expanded
        panel.findViewById<View>(R.id.pet_browse_header).isVisible = expanded
        panel.findViewById<View>(R.id.pet_browse_apply).apply {
            visibility = if (choosingPet) View.VISIBLE else View.INVISIBLE
            isEnabled = pendingPetId in petsById
            alpha = if (isEnabled) 1f else 0.35f
            contentDescription = "Done: add selected pet"
            setOnClickListener { confirmPetSelection() }
        }
        panel.findViewById<View>(R.id.pet_browse_back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        panel.findViewById<View>(R.id.pet_enable).parent.let { (it as View).isVisible = basePet || choosingPet || detail }
        panel.findViewById<View>(R.id.pet_slots_title).isVisible = basePet
        panel.findViewById<View>(R.id.pet_slots).isVisible = basePet
        panel.findViewById<View>(R.id.pet_hero).apply {
            setBackgroundResource(when {
                expanded -> android.R.color.transparent
                basePet -> R.drawable.bg_pet_gallery
                else -> R.drawable.bg_home_panel
            })
            // The browse list shares the same horizontal bounds as Back and Done.
            val inset = if (choosingPet) 0 else resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
            (layoutParams as android.view.ViewGroup.MarginLayoutParams).apply {
                // Figma 961:981 spans the full screen; keep its controls inset.
                marginStart = if (basePet) -binding.featureContent.paddingStart else inset
                marginEnd = if (basePet) -binding.featureContent.paddingEnd else inset
            }
            setPaddingRelative(if (basePet) binding.featureContent.paddingStart else inset,
                paddingTop, if (basePet) binding.featureContent.paddingEnd else inset, paddingBottom)
        }
        panel.findViewById<View>(R.id.pet_top_actions).isVisible = basePet
        panel.findViewById<View>(R.id.pet_hero).isVisible = browse || detail || result
        panel.findViewById<View>(R.id.pet_more).isVisible = basePet
        panel.findViewById<View>(R.id.pet_categories).isVisible = browse
        panel.findViewById<View>(R.id.pet_grid).isVisible = browse
        panel.findViewById<View>(R.id.pet_choose_header).isVisible = basePet
        panel.findViewById<View>(R.id.pet_detail_card).isVisible = detail || result
        panel.findViewById<View>(R.id.pet_detail_emoji).let { (it as android.widget.TextView).text = prefs.getString("pet_emoji", "🐱") }
        if (!com.hd.emojibattery.BuildConfig.UI_TEST_MODE && detail) {
            val pet = petsById[arguments?.getString("pet_id") ?: prefs.getString("pet_selected_id", "").orEmpty()]
            pet?.let { com.hd.emojibattery.runtime.MediaAssets.background(panel.findViewById(R.id.pet_detail_emoji), com.hd.emojibattery.runtime.PetMedia.frames(it, "walk", 100).firstOrNull().orEmpty()) }
        }
        panel.findViewById<View>(R.id.pet_detail_title).let { (it as android.widget.TextView).text = when (screen) { ScreenIds.PET_RATING -> "Enjoying your new pet?"; else -> arguments?.getString("pet_name") ?: prefs.getString("pet_name", "Your pet is ready") } }
        panel.findViewById<View>(R.id.pet_position).isVisible = basePet || detail
        panel.findViewById<View>(R.id.pet_motion).isVisible = basePet || detail
        panel.findViewById<View>(R.id.pet_settings_title).isVisible = basePet || detail
        panel.findViewById<View>(R.id.pet_apply).isVisible = detail
        panel.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.pet_enabled).apply {
            isVisible = (basePet || choosingPet || detail) && (requireActivity() as com.hd.emojibattery.MainActivity).hasOverlayPermissions()
            setOnCheckedChangeListener(null)
            isChecked = prefs.getBoolean("pet_overlay_enabled", false) &&
                (com.hd.emojibattery.BuildConfig.UI_TEST_MODE || (requireActivity() as com.hd.emojibattery.MainActivity).hasOverlayPermissions())
            setOnCheckedChangeListener { _, enabled ->
                if (enabled) {
                    setOnCheckedChangeListener(null)
                    isChecked = false
                    requirePetPermissions { applyPet() }
                    bindFeature()
                } else {
                    prefs.edit().putBoolean("pet_overlay_enabled", false).apply()
                    requireContext().stopService(Intent(requireContext(), com.hd.emojibattery.AnimatedPetService::class.java))
                    requireContext().stopService(Intent(requireContext(), PetOverlayService::class.java))
                }
            }
        }
        panel.findViewById<View>(R.id.pet_more).setOnClickListener { navigate(ScreenIds.PET_MORE) }
        panel.findViewById<View>(R.id.pet_sort).setOnClickListener {
            categoryPickerOpen = true
            bindFeature()
        }
        panel.findViewById<View>(R.id.pet_search).setOnClickListener { navigate(ScreenIds.SEARCH) }
        panel.findViewById<View>(R.id.pet_library).setOnClickListener { navigate(ScreenIds.FAVORITES) }
        panel.findViewById<View>(R.id.pet_premium).setOnClickListener { navigate(ScreenIds.PREMIUM) }
        panel.findViewById<View>(R.id.pet_enable).setOnClickListener {
            requirePetPermissions { if (petSlots().none { it.isNotBlank() }) bindFeature() else applyPet() }
        }
        if (screen == ScreenIds.PET_STATE_2) {
            binding.root.findViewById<View>(R.id.pet_permission_dismiss).setOnClickListener { navigate(ScreenIds.PET) }
            binding.root.findViewById<View>(R.id.pet_permission_continue).setOnClickListener { openOverlaySettings() }
            binding.root.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.pet_permission_switch).apply {
                isChecked = Settings.canDrawOverlays(requireContext())
                setOnClickListener {
                    // Android Settings owns this permission; do not display a local grant.
                    isChecked = Settings.canDrawOverlays(requireContext())
                    openOverlaySettings()
                }
            }
        }
        val flowModal = binding.root.findViewById<View>(R.id.pet_flow_modal)
        flowModal.isClickable = true
        flowModal.isVisible = categoryPickerOpen || screen in setOf(ScreenIds.PET_APPLYING, ScreenIds.PET_RATING, ScreenIds.PET_SORT, ScreenIds.PET_EXIT)
        categoryBack.isEnabled = categoryPickerOpen
        flowModal.findViewById<View>(R.id.pet_download_card).isVisible = screen == ScreenIds.PET_APPLYING
        flowModal.findViewById<View>(R.id.pet_rating_card).isVisible = screen == ScreenIds.PET_RATING
        flowModal.findViewById<View>(R.id.pet_sort_card).isVisible = categoryPickerOpen || screen == ScreenIds.PET_SORT
        flowModal.findViewById<View>(R.id.pet_exit_card).isVisible = screen == ScreenIds.PET_EXIT
        flowModal.findViewById<View>(R.id.pet_download_cancel).setOnClickListener { navigate(ScreenIds.PET) }
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE && screen == ScreenIds.PET_APPLYING) {
            flowModal.findViewById<android.widget.TextView>(R.id.pet_download_cancel).apply {
                text = "Continue"
                setOnClickListener { navigate(ScreenIds.PET_RATING) }
            }
        }
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
        flowModal.findViewById<View>(R.id.pet_sort_close).setOnClickListener { closeCategoryPicker() }
        val sortList = flowModal.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.pet_sort_categories)
        sortList.layoutManager = GridLayoutManager(requireContext(), 3)
        sortList.adapter = CategoryAdapter(pillGrid = true) { value ->
            prefs.edit().putString("pet_category", value).apply()
            closeCategoryPicker()
        }.also { it.submit(categories, prefs.getString("pet_category", "All").takeIf { it in categories } ?: "All") }
        flowModal.findViewById<View>(R.id.pet_exit_keep).setOnClickListener { navigate(ScreenIds.PET) }
        flowModal.findViewById<View>(R.id.pet_exit_confirm).setOnClickListener { navigate(ScreenIds.HOME) }
        if (prefs.getString("pet_position", "Bottom right") == "Free drag") prefs.edit().putString("pet_position", "Bottom right").apply()
        panel.findViewById<android.widget.TextView>(R.id.pet_position).text = "Starting position: ${prefs.getString("pet_position", "Bottom right")}"
        val motion = prefs.getString("pet_motion", "Walk")
        val motionLabel = when (motion) { "Walk" -> "Roam around screen"; "Bounce" -> "Bounce around screen"; "Follow touch" -> "Drag manually"; else -> "Stay still" }
        panel.findViewById<android.widget.TextView>(R.id.pet_motion).text = "Movement: $motionLabel"
        panel.findViewById<android.widget.TextView>(R.id.pet_enable).text = "Start"
        sortList.layoutParams.height = minOf((441 * resources.displayMetrics.density).toInt(), (resources.displayMetrics.heightPixels * 0.55f).toInt())
        panel.findViewById<View>(R.id.pet_position).setOnClickListener { choose("Starting position", listOf("Bottom right", "Bottom left", "Top right", "Top left"), "pet_position") }
        panel.findViewById<View>(R.id.pet_motion).setOnClickListener { choose("Movement", listOf("Idle", "Walk", "Bounce", "Follow touch"), "pet_motion") }
        panel.findViewById<View>(R.id.pet_apply).setOnClickListener {
            requirePetPermissions {
                val chosen = arguments?.getString("pet_id")
                if (chosen == null) applyPet() else {
                    val ids = petSlots().toMutableList()
                    val index = ids.indexOfFirst { it.isBlank() }
                    if (index < 0) toast("Remove a pet first (maximum 6)")
                    else { ids[index] = chosen; applyPet(ids) { (requireActivity() as com.hd.emojibattery.MainActivity).openScreen(ScreenIds.PET, false) } }
                }
            }
        }
        val cat = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.pet_categories)
        if (cat.layoutManager == null) cat.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        val categoryAdapter = ((cat.adapter as? CategoryAdapter) ?: CategoryAdapter { value -> prefs.edit().putString("pet_category", value).apply(); bindFeature() })
            .also { it.submit(categories, prefs.getString("pet_category", categories.firstOrNull().orEmpty()).takeIf { it in categories } ?: categories.firstOrNull().orEmpty()) }
        if (cat.adapter !== categoryAdapter) cat.adapter = categoryAdapter
        val grid = panel.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.pet_grid)
        if (grid.layoutManager == null) grid.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = ((grid.adapter as? CatalogAdapter) ?: CatalogAdapter(petStyle = true, selection = if (choosingPet) ({ pendingPetId }) else null, onSelected = { item ->
            if (choosingPet) {
                pendingPetId = item.id
                panel.findViewById<View>(R.id.pet_browse_apply).apply { isEnabled = true; alpha = 1f }
                return@CatalogAdapter
            }
            viewModel.selectPet(item.id)
            prefs.edit().putString("pet_selected_id", item.id).putString("pet_emoji", item.emoji).putString("pet_name", item.name).apply()
            if (basePet) {
                val selected = petSlots().toMutableList()
                val index = selected.indexOfFirst { it.isBlank() }
                if (index < 0) { toast("Remove a pet first (maximum 6)"); return@CatalogAdapter }
                selected[index] = item.id
                requirePetPermissions { applyPet(selected) }
            } else navigate(ScreenIds.PET_DETAIL, android.os.Bundle().apply {
                putString("pet_id", item.id); putString("pet_name", item.name); putString("pet_emoji", item.emoji)
            })
        }, onFavorite = { item ->
            val favorite = !Favorites.contains(prefs, "pets", item.id)
            Favorites.set(prefs, "pets", item.id, favorite)
            adapter?.updateFavorite(item.id, favorite)
        }))
            .also {
                val selectedCategory = prefs.getString("pet_category", "All")
                val showAll = selectedCategory == "All" || selectedCategory !in categories
                val catalogItems = (0 until (raw?.length() ?: 0)).mapNotNull { index -> raw?.optJSONObject(index) }
                val allowed = catalogItems
                    .filter { showAll || it.optString("category") == selectedCategory }
                    .map { it.optString("id") }.toSet()
                val entries = raw.toCatalogEntries { id -> Favorites.contains(prefs, "pets", id) }
                    .filter { item -> item.id in allowed }
                val displayed = if (showAll) {
                    // Interleave source categories so All does not start with an
                    // entire category (e.g. Game). Keep ordering stable on rebind.
                    val categoryById = catalogItems.associate { item -> item.optString("id") to item.optString("category") }
                    val groups = entries.groupBy { item -> categoryById[item.id] }.values.map { group -> group.iterator() }
                    buildList {
                        while (groups.any { group -> group.hasNext() }) {
                            groups.forEach { group -> if (group.hasNext()) add(group.next()) }
                        }
                    }
                } else entries
                it.submit(displayed) }
        if (grid.adapter !== adapter) grid.adapter = adapter
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE && screen == ScreenIds.PET_STATE_3 &&
            prefs.getStringSet("selected_pets", emptySet()).isNullOrEmpty() && !prefs.getBoolean("preview_pet_state_seeded", false)) {
            val sampleIds = raw.toCatalogEntries().take(2).map { it.id }.toSet()
            prefs.edit().putStringSet("selected_pets", sampleIds).putBoolean("preview_pet_state_seeded", true).apply()
        }
        val selectedPets = petSlots()
        val slots = panel.findViewById<android.widget.GridLayout>(R.id.pet_slots)
        panel.findViewById<android.widget.TextView>(R.id.pet_slots_title).text = "Get Your Pet (${selectedPets.count { it.isNotBlank() }}/6)"
        for (index in 0 until (if (basePet) slots.childCount else 0)) {
            val slot = slots.getChildAt(index) as android.widget.FrameLayout
            for (child in 0 until slot.childCount) (slot.getChildAt(child) as? android.widget.ImageView)?.let { com.hd.emojibattery.runtime.MediaAssets.clear(it) }
            slot.removeAllViews()
            val id = selectedPets.getOrNull(index)?.takeIf { it.isNotBlank() }
            val density = resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()
            slot.setBackgroundResource(if (id == null) R.drawable.bg_pet_slot else R.drawable.bg_pet_selected)
            if (id != null) {
                val art = android.widget.ImageView(requireContext()).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
                slot.addView(art, android.widget.FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
                petsById[id]?.let { pet -> com.hd.emojibattery.runtime.MediaAssets.load(art, com.hd.emojibattery.runtime.PetMedia.frames(pet, "walk", 100).firstOrNull().orEmpty()) }
            }
            val lockedSlot = !slotUnlocked(index)
            val icon = android.widget.ImageView(requireContext()).apply {
                setImageResource(if (lockedSlot) R.drawable.ic_ads_lock else if (id == null) R.drawable.figma_pet_add else R.drawable.figma_pet_remove)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
            slot.addView(icon, android.widget.FrameLayout.LayoutParams(dp(44), dp(44), if (id == null) android.view.Gravity.CENTER else android.view.Gravity.TOP or android.view.Gravity.END))
            slot.contentDescription = if (id == null) "Add pet" else "Change ${petsById[id]?.optString("name") ?: "pet"}"
            slot.setOnClickListener {
                val choosePet = {
                    navigate(ScreenIds.PET_MORE, android.os.Bundle().apply { putString("preselected_pet_id", id); putInt("pet_slot_index", index) })
                }
                if (lockedSlot) unlockSlot(index, choosePet) else choosePet()
            }
            if (id != null) {
                icon.contentDescription = "Remove ${petsById[id]?.optString("name") ?: "pet"}"
                icon.setOnClickListener {
                    val updated = selectedPets.toMutableList().apply { this[index] = "" }
                    val applied = runCatching { org.json.JSONArray(prefs.getString("applied_pets", "[]")) }.getOrDefault(org.json.JSONArray())
                    val remaining = org.json.JSONArray()
                    for (i in 0 until applied.length()) applied.optJSONObject(i)?.let { pet ->
                        if (pet.optInt("pet_slot_index", i) != index) remaining.put(pet)
                    }
                    val edit = prefs.edit().putString("applied_pets", remaining.toString())
                    saveSlots(edit, updated)
                    edit.apply()
                    if (updated.none { it.isNotBlank() }) {
                        prefs.edit().putBoolean("pet_overlay_enabled", false).apply()
                        requireContext().stopService(Intent(requireContext(), com.hd.emojibattery.AnimatedPetService::class.java))
                    } else if (prefs.getBoolean("pet_overlay_enabled", false)) {
                        com.hd.emojibattery.runtime.FeatureApply.start(requireActivity(), com.hd.emojibattery.AnimatedPetService::class.java)
                    }
                    bindFeature()
                }
            }
        }
        (requireActivity() as com.hd.emojibattery.MainActivity).updateEnableControls()
        if (screen == ScreenIds.PET_APPLYING || screen == ScreenIds.PET_RATING) {
            panel.findViewById<android.widget.TextView>(R.id.pet_apply).text = if (screen == ScreenIds.PET_RATING) "Done" else "Your pet is ready — Continue"
            panel.findViewById<View>(R.id.pet_apply).setOnClickListener { navigate(if (screen == ScreenIds.PET_RATING) ScreenIds.HOME else ScreenIds.PET_RATING) }
        }
    }

    private fun choose(title: String, values: List<String>, key: String) {
        val labels = if (key == "pet_motion") listOf("Stay still", "Roam around screen", "Bounce around screen", "Drag manually") else values
        val current = prefs.getString(key, if (key == "pet_motion") "Walk" else "Bottom right")
        AlertDialog.Builder(requireContext()).setTitle(title).setSingleChoiceItems(labels.toTypedArray(), values.indexOf(current)) { dialog, index ->
            prefs.edit().putString(key, values[index]).apply()
            if (prefs.getBoolean("pet_overlay_enabled", false)) com.hd.emojibattery.runtime.FeatureApply.start(requireActivity(), com.hd.emojibattery.AnimatedPetService::class.java)
            dialog.dismiss()
            bindFeature()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun applyPet(selectedIds: List<String>? = null, onApplied: () -> Unit = { bindFeature() }) {
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) {
            val edit = prefs.edit().putBoolean("pet_overlay_enabled", true)
            saveSlots(edit, selectedIds ?: petSlots())
            edit.apply()
            onApplied()
            return
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(requireContext())) {
            navigate(ScreenIds.PET_STATE_2)
            return
        }
        if (!com.hd.emojibattery.runtime.FeatureApply.overlayPermission(requireActivity())) return
        val ids = selectedIds ?: petSlots()
        val saved = runCatching { org.json.JSONArray(prefs.getString("applied_pets", "[]")) }.getOrDefault(org.json.JSONArray())
        val selected = ids.take(6).mapIndexedNotNull { index, id ->
            if (id.isBlank()) null else {
                val item = com.hd.emojibattery.runtime.AppliedContent.find(requireContext(), "pets", id)
                    ?: (0 until saved.length()).mapNotNull { saved.optJSONObject(it) }.firstOrNull { it.optString("id") == id }
                item?.let { org.json.JSONObject(it.toString()).put("pet_slot_index", index) }
            }
        }
        if (selected.isEmpty()) { toast("Choose a pet first"); return }
        val urls = selected.flatMap { com.hd.emojibattery.runtime.PetMedia.allAssets(it) }.distinct()
        if (urls.isEmpty()) { toast("This pet has no media data"); return }
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val downloadView = layoutInflater.inflate(R.layout.dialog_pet_download, null)
        val progress = downloadView.findViewById<android.widget.ProgressBar>(R.id.pet_download_progress)
        val percent = downloadView.findViewById<android.widget.TextView>(R.id.pet_download_percent)
        val dialog = AlertDialog.Builder(requireContext()).setView(downloadView).create()
        downloadView.findViewById<View>(R.id.pet_download_cancel).setOnClickListener { cancelled.set(true); dialog.dismiss() }
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener { cancelled.set(true) }
        dialog.setOnDismissListener { cancelled.set(true) }
        trackDialog(dialog); dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - (44 * resources.displayMetrics.density).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        val host = requireActivity()
        val app = requireContext().applicationContext
        com.hd.emojibattery.runtime.MediaAssets.io.execute {
            var success = true
            for ((index, url) in urls.withIndex()) {
                if (cancelled.get()) break
                if (runCatching { com.hd.emojibattery.runtime.MediaAssets.file(app, url) }.getOrNull() == null) { success = false; break }
                host.runOnUiThread { if (!cancelled.get()) { val value = (index + 1) * 100 / urls.size; progress.progress = value; percent.text = "$value%" } }
            }
            host.runOnUiThread {
                val wasCancelled = cancelled.get()
                dialog.dismiss()
                if (wasCancelled || !isAdded || _binding == null) return@runOnUiThread
                if (!success) { toast("Download failed. Check your connection and try Apply again."); return@runOnUiThread }
                val previous = prefs.getString("applied_pets", "[]")
                val previousIds = prefs.getStringSet("selected_pets", emptySet())?.toSet()
                val previousSlots = prefs.getString("pet_slot_ids", null)
                val wasEnabled = prefs.getBoolean("pet_overlay_enabled", false)
                val edit = prefs.edit().putString("applied_pets", org.json.JSONArray(selected).toString()).putBoolean("pet_overlay_enabled", true)
                saveSlots(edit, ids)
                edit.apply()
                host.stopService(Intent(host, PetOverlayService::class.java))
                if (com.hd.emojibattery.runtime.FeatureApply.start(host, com.hd.emojibattery.AnimatedPetService::class.java)) onApplied()
                else {
                    prefs.edit().putStringSet("selected_pets", previousIds).putString("applied_pets", previous)
                        .putString("pet_slot_ids", previousSlots).putBoolean("pet_overlay_enabled", wasEnabled).apply()
                    bindFeature()
                }
            }
        }
    }

    private fun requirePetPermissions(onGranted: () -> Unit) {
        if (!isAdded || _binding == null) return
        (requireActivity() as com.hd.emojibattery.MainActivity).requestOverlayEnable {
            if (isAdded && _binding != null) onGranted()
        }
    }

    private fun openOverlaySettings() {
        (requireActivity() as com.hd.emojibattery.MainActivity).requestOverlayEnable {
            if (isAdded && _binding != null) bindFeature()
        }
    }




}
