package com.hd.emojibattery.runtime

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Scalable Wi-Fi indicator for the live status overlay. */
internal class WifiStatusDrawable(color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        canvas.drawArc(1f, 4f, 23f, 26f, 225f, 90f, false, paint)
        canvas.drawArc(6f, 9f, 18f, 21f, 225f, 90f, false, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(12f, 20f, 1.5f, paint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
