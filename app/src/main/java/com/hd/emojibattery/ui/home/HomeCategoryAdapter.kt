package com.hd.emojibattery.ui.home

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.hd.emojibattery.R

/** Native category controls with the artwork exported from Figma node 919:418. */
internal class HomeCategoryAdapter(private val onSelected: (String) -> Unit) :
    RecyclerView.Adapter<HomeCategoryAdapter.Holder>() {
    private var names: List<String> = emptyList()
    private var selected = ""

    fun submit(values: List<String>, current: String) {
        if (names == values && selected == current) return
        names = values.toList()
        selected = current
        notifyDataSetChanged()
    }

    override fun getItemCount() = names.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val density = parent.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(23)).apply {
                marginEnd = dp(13)
            }
            isFocusable = true
        }
        val label = TextView(parent.context).apply {
            textSize = 14f
            typeface = ResourcesCompat.getFont(context, R.font.nunito_medium)
            setTextColor(0xff8880c1.toInt())
            includeFontPadding = false
            gravity = Gravity.CENTER
            setSingleLine()
        }
        row.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(19)))
        val indicator = ImageView(parent.context).apply {
            setImageResource(R.drawable.figma_home_category_indicator)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(indicator, LinearLayout.LayoutParams(dp(39), dp(2)).apply { topMargin = dp(2) })
        return Holder(row, label, indicator)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(names[position])

    inner class Holder(private val row: LinearLayout, private val label: TextView, private val indicator: ImageView) :
        RecyclerView.ViewHolder(row) {
        fun bind(name: String) {
            label.text = name
            indicator.layoutParams = (indicator.layoutParams as LinearLayout.LayoutParams).apply {
                width = (label.paint.measureText(name) - 5 * row.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            }
            row.isSelected = name == selected
            indicator.visibility = if (row.isSelected) View.VISIBLE else View.INVISIBLE
            row.contentDescription = name
            row.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) onSelected(names[bindingAdapterPosition])
            }
        }
    }
}
