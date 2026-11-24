package com.hd.emojibattery.ui.onboarding

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class IntroSwipeLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var onSwipe: ((forward: Boolean) -> Unit)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var swiping = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                swiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                if (abs(dx) > slop && abs(dx) > abs(event.y - startY) * 1.5f) {
                    swiping = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startX
                if (abs(dx) > slop * 3 && abs(dx) > abs(event.y - startY) * 1.5f) {
                    onSwipe?.invoke(dx < 0)
                } else if (!swiping) performClick()
                swiping = false
            }
            MotionEvent.ACTION_CANCEL -> swiping = false
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
