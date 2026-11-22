package com.hd.emojibattery.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Keep vertical catalogs recyclable even when a page measures with UNSPECIFIED height. */
open class BoundedCatalogRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val vertical = (layoutManager as? LinearLayoutManager)?.orientation == VERTICAL
        if (vertical && View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED) {
            var ancestor = parent
            var viewport = resources.displayMetrics.heightPixels
            while (ancestor is View) {
                if (ancestor is androidx.core.widget.NestedScrollView && ancestor.height > 0) {
                    viewport = ancestor.height - ancestor.paddingTop - ancestor.paddingBottom
                    break
                }
                ancestor = ancestor.parent
            }
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(viewport.coerceAtLeast(1), View.MeasureSpec.AT_MOST))
        } else super.onMeasure(widthSpec, heightSpec)
    }
}
