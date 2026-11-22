package com.hd.emojibattery.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hd.emojibattery.data.RemoteImageLoader
import com.hd.emojibattery.databinding.ItemCatalogCardBinding
import org.json.JSONArray

data class CatalogEntry(
    val id: String,
    val name: String,
    val emoji: String,
    val imageUrl: String,
    val color: String = "#F7F6FF",
    val favorite: Boolean = false,
    val locked: Boolean = false
)

class CatalogAdapter(
    private val onSelected: (CatalogEntry) -> Unit,
    private val onFavorite: (CatalogEntry) -> Unit = {},
    private val cardHeight: Int = 109,
    private val petStyle: Boolean = false,
    private val chargingStyle: Boolean = false,
    private val stickerStyle: Boolean = false,
    private var cardWidth: Int = 100,
    private val showFavorite: Boolean = true,
    private val removeAction: Boolean = false,
    private val selection: (() -> String?)? = null,
    private val imageScaleType: android.widget.ImageView.ScaleType? = null,
    private val statusBackgroundStyle: Boolean = false,
    private val showEditBadge: Boolean = false,
    private val onEdit: ((CatalogEntry) -> Unit)? = null
) : RecyclerView.Adapter<CatalogAdapter.Holder>() {
    private val entries = mutableListOf<CatalogEntry>()
    private val selectionPayload = Any()
    private val favoritePayload = Any()
    private var selectedId: String? = null
    fun updateHorizontalCardWidth(width: Int) {
        if (width <= 0 || cardWidth == width) return
        cardWidth = width
        notifyDataSetChanged()
    }
    fun submit(items: List<CatalogEntry>) {
        if (entries == items) { refreshSelection(); return }
        val previous = entries.toList()
        val next = items.toList()
        val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(old: Int, new: Int) = previous[old].id == next[new].id
            override fun areContentsTheSame(old: Int, new: Int) = previous[old] == next[new]
            override fun getChangePayload(old: Int, new: Int): Any? =
                if (previous[old].copy(favorite = next[new].favorite) == next[new]) favoritePayload else null
        })
        entries.clear(); entries.addAll(next)
        diff.dispatchUpdatesTo(this)
        refreshSelection()
    }

    fun updateFavorite(id: String, favorite: Boolean) {
        val position = positionOf(id)
        if (position < 0 || entries[position].favorite == favorite) return
        entries[position] = entries[position].copy(favorite = favorite)
        notifyItemChanged(position, favoritePayload)
    }

    fun refreshSelection() {
        val current = selection?.invoke()
        if (selectedId == current) return
        val previous = entries.indexOfFirst { it.id == selectedId }
        val next = entries.indexOfFirst { it.id == current }
        selectedId = current
        if (previous >= 0) notifyItemChanged(previous, selectionPayload)
        if (next >= 0) notifyItemChanged(next, selectionPayload)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCatalogCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val manager = (parent as? RecyclerView)?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
        binding.root.layoutParams.height = (cardHeight * parent.resources.displayMetrics.density).toInt()
        if (manager?.orientation == RecyclerView.HORIZONTAL) binding.root.layoutParams.width = (cardWidth * parent.resources.displayMetrics.density).toInt()
        binding.catalogFavorite.visibility = if (showFavorite) android.view.View.VISIBLE else android.view.View.GONE
        imageScaleType?.let { binding.catalogImage.scaleType = it }
        if (statusBackgroundStyle) {
            val density = parent.resources.displayMetrics.density
            fun roundedOutline(radius: Float) = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            // Keep the image inside the 2dp border, with concentric rounded corners.
            val inset = (2 * density).toInt()
            binding.root.outlineProvider = roundedOutline(17 * density)
            binding.root.clipToOutline = true
            (binding.catalogImage.parent as android.view.View).apply {
                outlineProvider = roundedOutline(17 * density - inset)
                clipToOutline = true
            }
        }
        return Holder(binding)
    }
    override fun getItemCount() = entries.size
    fun positionOf(id: String): Int = entries.indexOfFirst { it.id == id }
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(entries[position])
    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it === selectionPayload || it === favoritePayload }) {
            if (payloads.any { it === selectionPayload }) holder.bindSelection(entries[position])
            if (payloads.any { it === favoritePayload }) holder.bindFavorite(entries[position])
        } else holder.bind(entries[position])
    }
    override fun onViewRecycled(holder: Holder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    inner class Holder(private val binding: ItemCatalogCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun clear() = RemoteImageLoader.clear(binding.catalogImage)
        fun bindSelection(item: CatalogEntry) {
            if (selection != null) {
                binding.root.setBackgroundResource(if (selection.invoke() == item.id) com.hd.emojibattery.R.drawable.bg_status_selected else com.hd.emojibattery.R.drawable.bg_catalog_card)
                binding.root.clipToOutline = true
                val inset = (2 * binding.root.resources.displayMetrics.density).toInt()
                binding.root.setPadding(inset, inset, inset, inset)
                binding.root.isSelected = selection.invoke() == item.id
                if (statusBackgroundStyle && !binding.root.isSelected) {
                    binding.root.background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 17 * binding.root.resources.displayMetrics.density
                        setColor(0xfff7f6ff.toInt())
                        setStroke((binding.root.resources.displayMetrics.density).toInt().coerceAtLeast(1), 0xffece8fa.toInt())
                    }
                }
            }
            if (petStyle) {
                binding.root.setBackgroundResource(if (selection?.invoke() == item.id) com.hd.emojibattery.R.drawable.bg_pet_selected else com.hd.emojibattery.R.drawable.bg_pet_catalog)
            }
            // ViewGroup selection propagates to children; favorites keep their own state.
            bindFavorite(item)
        }
        fun bindFavorite(item: CatalogEntry) {
            binding.catalogFavorite.isSelected = item.favorite
            binding.catalogFavorite.imageTintList = if (chargingStyle && item.favorite)
                android.content.res.ColorStateList.valueOf(android.graphics.Color.RED) else null
            binding.catalogFavorite.contentDescription = if (stickerStyle && removeAction) "Turn off sticker"
                else if (item.favorite) "Remove favorite" else "Add favorite"
        }
        fun bind(item: CatalogEntry) {
            // Horizontal rows can share the responsive column width of a grid.
            if (binding.root.layoutParams.width > 0) {
                binding.root.layoutParams.width = (cardWidth * binding.root.resources.displayMetrics.density).toInt()
            }
            bindSelection(item)
            binding.catalogEdit.visibility = if (showEditBadge) android.view.View.VISIBLE else android.view.View.GONE
            binding.catalogEdit.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEdit?.invoke(entries[position]) ?: binding.root.performClick()
                }
            }
            val locked = item.locked &&
                !com.hd.emojibattery.ads.SessionUnlocks.contains(item.id) &&
                !tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed()
            binding.catalogLock.visibility = if (locked) android.view.View.VISIBLE else android.view.View.GONE
            binding.catalogName.text = item.name
            binding.catalogEmoji.text = if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) "" else item.emoji
            binding.catalogEmoji.visibility = android.view.View.VISIBLE
            binding.root.contentDescription = item.name.ifBlank { "Preview item ${bindingAdapterPosition + 1}" }
            binding.catalogFavorite.setImageResource(com.hd.emojibattery.R.drawable.figma_sticker_heart)
            binding.catalogFavorite.imageTintList = null
            binding.catalogFavorite.isSelected = item.favorite
            binding.catalogFavorite.contentDescription = if (item.favorite) "Remove favorite" else "Add favorite"
            val favoriteDensity = binding.root.resources.displayMetrics.density
            // Keep the 26dp artwork at its original inset inside a native 48dp target.
            // The target stays within this card, so adjacent cards cannot receive its taps.
            binding.catalogFavorite.layoutParams = android.widget.FrameLayout.LayoutParams(
                (48 * favoriteDensity).toInt(), (48 * favoriteDensity).toInt(),
                android.view.Gravity.TOP or android.view.Gravity.END
            )
            binding.catalogFavorite.setPadding(
                (14 * favoriteDensity).toInt(), (7 * favoriteDensity).toInt(),
                (8 * favoriteDensity).toInt(), (15 * favoriteDensity).toInt()
            )
            if (petStyle) {
                binding.root.setBackgroundResource(if (selection?.invoke() == item.id) com.hd.emojibattery.R.drawable.bg_pet_selected else com.hd.emojibattery.R.drawable.bg_pet_catalog)
                binding.root.clipToOutline = true
                binding.root.elevation = 2 * binding.root.resources.displayMetrics.density
                binding.catalogEmoji.text = ""
                binding.catalogImage.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.catalogFavorite.setImageResource(com.hd.emojibattery.R.drawable.figma_sticker_heart)
                binding.catalogFavorite.imageTintList = null
                binding.catalogFavorite.contentDescription = if (item.favorite) "Remove favorite" else "Add favorite"
            }
            if (chargingStyle) {
                val dp = binding.root.resources.displayMetrics.density
                binding.root.clipToOutline = true
                binding.catalogImage.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 15 * dp; setColor(0xfff8f7fd.toInt()) }
                binding.catalogImage.clipToOutline = true
                binding.catalogFavorite.setImageResource(com.hd.emojibattery.R.drawable.figma_charging_heart)
                binding.catalogFavorite.imageTintList = if (item.favorite) android.content.res.ColorStateList.valueOf(android.graphics.Color.RED) else null
                binding.catalogFavorite.contentDescription = if (item.favorite) "Remove favorite" else "Add favorite"
            }
            if (stickerStyle) {
                binding.catalogEmoji.text = ""
                binding.catalogImage.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                if (removeAction) {
                    binding.catalogFavorite.setImageResource(com.hd.emojibattery.R.drawable.figma_sticker_remove)
                    binding.catalogFavorite.imageTintList = null
                    binding.catalogFavorite.contentDescription = "Turn off sticker"
                }
            }
            clear()
            binding.catalogImage.visibility = android.view.View.GONE
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val chosen = entries[position]
                    val host = generateSequence(binding.root.context) { (it as? android.content.ContextWrapper)?.baseContext }
                        .filterIsInstance<com.hd.emojibattery.MainActivity>().firstOrNull()
                    val fragment = host?.supportFragmentManager?.findFragmentById(com.hd.emojibattery.R.id.main_container)
                    val select = {
                        onSelected(chosen)
                        binding.catalogLock.visibility = android.view.View.GONE
                        refreshSelection()
                    }
                    if (host != null && fragment != null && chosen.locked) {
                        host.batteryAds.unlock(fragment, chosen.id, select)
                    } else select()
                }
            }
            bindFavorite(item)
            binding.catalogFavorite.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onFavorite(entries[position])
            }
            if (item.imageUrl.isNotBlank()) RemoteImageLoader.load(binding.root.context, item.imageUrl, binding.catalogImage, binding.catalogEmoji)
        }
    }
}

/** Presentation only: category keys must remain unchanged for catalog filtering. */
fun categoryDisplayName(value: String): String = value.replace('_', ' ').trim()
    .split(Regex("\\s+"))
    .joinToString(" ") { word ->
        word.lowercase(java.util.Locale.ROOT).replaceFirstChar { it.titlecase(java.util.Locale.ROOT) }
    }

class CategoryAdapter(
    private val pillGrid: Boolean = false,
    private val chargingStyle: Boolean = false,
    private val stickerStyle: Boolean = false,
    private val onSelected: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.Holder>() {
    private val names = mutableListOf<String>()
    private var selected = ""
    private val selectionPayload = Any()
    private fun select(current: String) {
        if (selected == current) return
        val previous = selected
        selected = current
        names.forEachIndexed { index, name ->
            if (name == previous || name == current) notifyItemChanged(index, selectionPayload)
        }
    }
    fun submit(values: List<String>, current: String) {
        if (names == values) { select(current); return }
        names.clear(); names.addAll(values); selected = current; notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(com.hd.emojibattery.R.layout.item_category_chip, parent, false)
        if (pillGrid) {
            val dp = parent.resources.displayMetrics.density
            (view as android.widget.TextView).apply {
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xff27205f.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 24 * dp
                    setColor(0xfff5f5f5.toInt())
                }
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (32 * dp).toInt()).apply {
                    setMargins((4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt())
                }
            }
        }
        if (chargingStyle) (view as android.widget.TextView).apply {
            val dp = resources.displayMetrics.density
            textSize = 14f
            setPadding((19 * dp).toInt(), 0, (19 * dp).toInt(), 0)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (39 * dp).toInt()).apply { rightMargin = (7 * dp).toInt() }
        }
        if (stickerStyle) (view as android.widget.TextView).apply {
            val dp = resources.displayMetrics.density
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding((7 * dp).toInt(), 0, (7 * dp).toInt(), 0)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (34 * dp).toInt())
        }
        return Holder(view as android.widget.TextView)
    }
    override fun getItemCount() = names.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(names[position])
    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it === selectionPayload }) {
            holder.bindSelection(names[position])
        } else holder.bind(names[position])
    }
    inner class Holder(private val view: android.widget.TextView) : RecyclerView.ViewHolder(view) {
        fun bindSelection(name: String) {
            view.isSelected = name == selected
            if (stickerStyle) {
                view.setTextColor(0xff8880c1.toInt())
                view.background = null
                view.paintFlags = if (view.isSelected) view.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    else view.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
            }
            if (chargingStyle) {
                view.setTextColor(if (view.isSelected) android.graphics.Color.WHITE else 0xff27205f.toInt())
                view.setBackgroundResource(if (view.isSelected) com.hd.emojibattery.R.drawable.bg_primary_button else com.hd.emojibattery.R.drawable.bg_charging_category)
            }
            if (pillGrid) view.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24 * view.resources.displayMetrics.density
                setColor(if (view.isSelected) 0xffeee8ff.toInt() else 0xfff5f5f5.toInt())
                if (view.isSelected) setStroke((view.resources.displayMetrics.density).toInt().coerceAtLeast(1), 0xff8566ff.toInt())
            }
        }
        fun bind(name: String) {
            view.text = if (stickerStyle) categoryDisplayName(name) else name
            bindSelection(name)
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val current = names[position]
                    select(current)
                    onSelected(current)
                }
            }
        }
    }
}

fun JSONArray?.toCatalogEntries(favorites: (String) -> Boolean = { false }): List<CatalogEntry> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val json = optJSONObject(index) ?: return@mapNotNull (opt(index) as? String)?.let { CatalogEntry(it, it, "", "") }
        val id = json.optString("id", json.optString("name", "item_$index"))
        CatalogEntry(id, json.optString("name", id), json.optString("emoji", "✨"),
            json.optString("image_url", json.optString("animation_url", "")), json.optString("color", "#F7F6FF"), favorites(id), locked = !json.optBoolean("free", true))
    }
}
