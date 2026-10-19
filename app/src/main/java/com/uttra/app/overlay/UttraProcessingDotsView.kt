package com.uttra.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.roundToInt
import kotlin.math.sin

/** Three mint dots that animate only while attached to the processing overlay. */
class UttraProcessingDotsView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(116, 232, 190)
        style = Paint.Style.FILL
    }

    private var phase = 0f
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator != null) return

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 960L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                phase = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val step = width / 4f

        for (index in 0 until 3) {
            val ripple = ((sin(2.0 * Math.PI * phase - index * 0.85) + 1.0) / 2.0).toFloat()
            val radius = (3.2f + 1.8f * ripple) * density
            paint.alpha = (140f + 115f * ripple).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(step * (index + 1), centerY, radius, paint)
        }
    }
}
