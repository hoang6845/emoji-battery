package com.hd.emojibattery.ui.common

import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.hd.emojibattery.R
import com.hd.emojibattery.runtime.AppliedContent
import com.hd.emojibattery.runtime.Favorites
import com.hd.emojibattery.runtime.MediaAssets
import org.json.JSONObject

/** The entire card, including its surface and favorite action, scrolls as one item. */
object BatteryPreviewCarousel {
    fun bind(root: View, items: List<JSONObject>, selectedId: String?, source: String,
             prefs: SharedPreferences, onSelected: (JSONObject) -> Unit) {
        val list = root.findViewById<RecyclerView>(R.id.battery_preview_cards)
        val manager = LinearLayoutManager(root.context, RecyclerView.HORIZONTAL, false)
        list.layoutManager = manager
        list.clearOnScrollListeners()
        list.onFlingListener = null
        val snap = LinearSnapHelper().also { it.attachToRecyclerView(list) }
        val density = root.resources.displayMetrics.density
        var surfaceSize = 272 * density
        var cardWidth = (229.5f * density).toInt()
        var positioned = false
        fun updateCards() {
            val center = list.width / 2f
            for (i in 0 until list.childCount) {
                val child = list.getChildAt(i)
                val distance = kotlin.math.abs((child.left + child.right) / 2f - center)
                val fraction = (distance / cardWidth).coerceIn(0f, 1f)
                val scale = 1f - fraction * (1f - 141f / 272f)
                child.findViewById<View>(R.id.battery_preview_surface).apply {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
        val adapter = object : RecyclerView.Adapter<Card>() {
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Card(
                LayoutInflater.from(parent.context).inflate(R.layout.item_battery_preview_card, parent, false))
            override fun onBindViewHolder(holder: Card, position: Int) {
                val item = items[position]
                holder.itemView.layoutParams.width = cardWidth
                holder.surface.layoutParams = holder.surface.layoutParams.apply {
                    width = surfaceSize.toInt()
                    height = surfaceSize.toInt()
                }
                MediaAssets.load(holder.photo, AppliedContent.url(item))
                val id = item.optString("id")
                val favorite = Favorites.contains(prefs, source, id)
                holder.heart.backgroundTintList = if (favorite)
                    android.content.res.ColorStateList.valueOf(0xffa27cff.toInt()) else null
                holder.heart.contentDescription = if (favorite) "Remove favorite" else "Add favorite"
                holder.heart.setOnClickListener {
                    Favorites.set(prefs, source, id, !Favorites.contains(prefs, source, id))
                    notifyItemChanged(position)
                }
                holder.itemView.setOnClickListener { list.smoothScrollToPosition(position) }
            }
            override fun onViewRecycled(holder: Card) { MediaAssets.clear(holder.photo) }
        }
        list.adapter = adapter
        val index = items.indexOfFirst { it.optString("id") == selectedId }.coerceAtLeast(0)
        list.doOnLayout {
            surfaceSize = minOf(272 * density, list.width * (272f / 412f)).coerceAtLeast(1f)
            cardWidth = (surfaceSize * (229.5f / 272f)).toInt().coerceAtLeast(1)
            val side = (list.width - cardWidth) / 2
            list.setPadding(side, (12 * density).toInt(), side, (12 * density).toInt())
            list.layoutParams = list.layoutParams.apply { height = surfaceSize.toInt() + (24 * density).toInt() }
            adapter.notifyDataSetChanged()
            manager.scrollToPositionWithOffset(index, 0)
            list.doOnLayout {
                positioned = true
                updateCards()
            }
        }
        items.getOrNull(index)?.let(onSelected)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateCards()
                if (positioned) {
                    val view = snap.findSnapView(manager) ?: return
                    items.getOrNull(manager.getPosition(view))?.let(onSelected)
                }
            }
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (!positioned) return
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val view = snap.findSnapView(manager) ?: return
                items.getOrNull(manager.getPosition(view))?.let(onSelected)
            }
        })
    }
    private class Card(view: View) : RecyclerView.ViewHolder(view) {
        val surface: View = view.findViewById(R.id.battery_preview_surface)
        val photo: ImageView = view.findViewById(R.id.battery_preview_image)
        val heart: View = view.findViewById(R.id.battery_preview_heart)
    }
}
