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
    val favorite: Boolean = false
)

class CatalogAdapter(
    private val onSelected: (CatalogEntry) -> Unit,
    private val onFavorite: (CatalogEntry) -> Unit = {}
) : RecyclerView.Adapter<CatalogAdapter.Holder>() {
    private val entries = mutableListOf<CatalogEntry>()
    fun submit(items: List<CatalogEntry>) { entries.clear(); entries.addAll(items); notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemCatalogCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun getItemCount() = entries.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(entries[position])

    inner class Holder(private val binding: ItemCatalogCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CatalogEntry) {
            binding.catalogName.text = item.name
            binding.catalogEmoji.text = item.emoji
            binding.catalogFavorite.text = if (item.favorite) "♥" else "♡"
            binding.catalogImage.setImageDrawable(null)
            binding.catalogImage.visibility = android.view.View.GONE
            binding.root.setOnClickListener { onSelected(item) }
            binding.catalogFavorite.setOnClickListener { onFavorite(item) }
            if (item.imageUrl.isNotBlank()) RemoteImageLoader.load(binding.root.context, item.imageUrl, binding.catalogImage, binding.catalogEmoji)
        }
    }
}

class CategoryAdapter(
    private val onSelected: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.Holder>() {
    private val names = mutableListOf<String>()
    private var selected = ""
    fun submit(values: List<String>, current: String) { names.clear(); names.addAll(values); selected = current; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(com.hd.emojibattery.R.layout.item_category_chip, parent, false)
        return Holder(view as android.widget.TextView)
    }
    override fun getItemCount() = names.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(names[position])
    inner class Holder(private val view: android.widget.TextView) : RecyclerView.ViewHolder(view) {
        fun bind(name: String) { view.text = name; view.isSelected = name == selected; view.setOnClickListener { onSelected(name) } }
    }
}

fun JSONArray?.toCatalogEntries(favorites: (String) -> Boolean = { false }): List<CatalogEntry> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val json = optJSONObject(index) ?: return@mapNotNull null
        val id = json.optString("id", json.optString("name", "item_$index"))
        CatalogEntry(id, json.optString("name", id), json.optString("emoji", "✨"),
            json.optString("image_url", json.optString("animation_url", "")), json.optString("color", "#F7F6FF"), favorites(id))
    }
}
