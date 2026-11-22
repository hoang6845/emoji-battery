package com.hd.emojibattery.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** One bounded scrolling surface: the page header spans the grid, cards are recycled. */
class HomeCatalogRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : com.hd.emojibattery.ui.common.BoundedCatalogRecyclerView(context, attrs, defStyleAttr) {
    fun showCatalog(header: View, cards: Adapter<*>, columns: Int) {
        val density = resources.displayMetrics.density
        val inset = (18 * density).toInt()
        val footer = View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                val radius = 35 * density
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (20 * density).toInt())
        }
        val combined = ConcatAdapter(SingleViewAdapter(header), cards, SingleViewAdapter(footer))
        layoutManager = GridLayoutManager(context, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (position == 0 || position == combined.itemCount - 1) columns else 1
            }
        }
        addItemDecoration(object : ItemDecoration() {
            private val paint = android.graphics.Paint().apply { color = Color.WHITE }
            private val bounds = Rect()
            override fun onDraw(canvas: Canvas, parent: RecyclerView, state: State) {
                for (index in 0 until parent.childCount) {
                    val child = parent.getChildAt(index)
                    val position = parent.getChildAdapterPosition(child)
                    if (position > 0 && position < combined.itemCount - 1) {
                        parent.getDecoratedBoundsWithMargins(child, bounds)
                        canvas.drawRect(0f, bounds.top + child.translationY,
                            parent.width.toFloat(), bounds.bottom + child.translationY, paint)
                    }
                }
            }
        })
        clipToPadding = false
        setPadding(inset, 0, inset, (44 * density).toInt())
        adapter = combined
    }

    private class SingleViewAdapter(private val view: View) : Adapter<ViewHolder>() {
        override fun getItemCount() = 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            (view.parent as? ViewGroup)?.removeView(view)
            val originalHeight = view.layoutParams?.height ?: LayoutParams.WRAP_CONTENT
            view.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, originalHeight).apply {
                val inset = (18 * view.resources.displayMetrics.density).toInt()
                leftMargin = -inset
                rightMargin = -inset
            }
            return object : ViewHolder(view) {}
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit
    }
}
