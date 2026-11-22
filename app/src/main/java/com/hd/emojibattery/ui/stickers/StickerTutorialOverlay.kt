package com.hd.emojibattery.ui.stickers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.hd.emojibattery.R

/** Two-step, once-per-install gesture guide matching the reference flow. */
internal class StickerTutorialOverlay(context: Context) : FrameLayout(context) {
    private val hole = RectF()
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xB2000000.toInt() }
    private var target: View? = null
    private var highlightedControls = emptyList<View>()
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
    }
    private val message = TextView(context).apply { textSize = 16f; setTextColor(0xff27205f.toInt()) }
    private val button = TextView(context).apply {
        setText(R.string.sticker_tutorial_got_it)
        textSize = 16f
        gravity = android.view.Gravity.CENTER
        setTextColor(0xff8867ef.toInt())
    }
    init {
        setWillNotDraw(false)
        isClickable = true
        card.addView(message, LinearLayout.LayoutParams(-1, -2))
        card.addView(button, LinearLayout.LayoutParams(-1, dp(48)))
        addView(card, LayoutParams(dp(300), -2))
        viewTreeObserver.addOnGlobalLayoutListener { updateTarget() }
    }
    fun showStep(view: View, text: String, controls: List<View> = emptyList(), next: () -> Unit) {
        target = view
        highlightedControls = controls
        message.text = text
        button.setOnClickListener { next() }
        setOnClickListener { next() }
        post { updateTarget() }
        button.requestFocus()
        announceForAccessibility(text)
    }
    private fun updateTarget() {
        val view = target ?: return
        val origin = IntArray(2)
        val position = IntArray(2)
        getLocationOnScreen(origin)
        view.getLocationOnScreen(position)
        hole.set((position[0] - origin[0] - dp(4)).toFloat(), (position[1] - origin[1] - dp(4)).toFloat(),
            (position[0] - origin[0] + view.width * kotlin.math.abs(view.scaleX) + dp(4)),
            (position[1] - origin[1] + view.height * view.scaleY + dp(4)))
        val left = (hole.centerX() - card.width / 2f).toInt().coerceIn(0, (width - card.width).coerceAtLeast(0))
        val top = (hole.bottom + dp(12)).toInt().coerceIn(0, (height - card.height).coerceAtLeast(0))
        if (card.x != left.toFloat()) card.x = left.toFloat()
        if (card.y != top.toFloat()) card.y = top.toFloat()
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        val spotlight = Path().apply {
            addRoundRect(hole, dp(12).toFloat(), dp(12).toFloat(), Path.Direction.CW)
        }
        val origin = IntArray(2)
        getLocationOnScreen(origin)
        highlightedControls.filter { it.visibility == View.VISIBLE }.forEach { control ->
            val position = IntArray(2)
            control.getLocationOnScreen(position)
            val left = (position[0] - origin[0]).toFloat()
            val top = (position[1] - origin[1]).toFloat()
            spotlight.op(Path().apply {
                addRect(left, top, left + control.width, top + control.height, Path.Direction.CW)
            }, Path.Op.UNION)
        }
        val path = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            op(spotlight, Path.Op.DIFFERENCE)
        }
        canvas.drawPath(path, paint)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
