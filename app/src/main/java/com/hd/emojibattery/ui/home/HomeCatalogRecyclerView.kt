package com.hd.emojibattery.ui.home

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/** Expands every catalog row; the surrounding Home ScrollView owns vertical scrolling. */
class HomeCatalogRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val expandedHeightSpec = if (layoutParams.height == LayoutParams.WRAP_CONTENT) {
            // A ScrollView's UNSPECIFIED spec can carry a viewport-sized hint. Give
            // GridLayoutManager room to lay out every row during auto-measure instead.
            View.MeasureSpec.makeMeasureSpec(Int.MAX_VALUE shr 2, View.MeasureSpec.AT_MOST)
        } else {
            heightSpec
        }
        super.onMeasure(widthSpec, expandedHeightSpec)
    }
}
