package com.uttra.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

/**
 * Decorative, gently animated recording waveform for the Uttra overlay.
 * This is intentionally an ambient animation, not a live microphone level meter.
 * It has no access to the recorder, transcription engine, or recording callbacks.
 */
internal class UttraRecordingWaveView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val pillBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(18, 59, 53)
    }
    private val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Color.rgb(51, 105, 93)
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pill = RectF()
    private val clippingPath = Path()

    // The controller places a 44dp control at each end with a 6dp outer margin.
    // Leave another 6dp of breathing space between each control and the waveform.
    private val waveSideInset = (6f + 44f + 6f) * density
    private var waveLeft = 0f
    private var waveRight = 0f
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1750L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        // The X and check controls provide the accessibility targets.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pill.set(0f, 0f, w.toFloat(), h.toFloat())
        clippingPath.reset()
        val radius = h / 2f
        clippingPath.addRoundRect(pill, radius, radius, Path.Direction.CW)
        waveLeft = waveSideInset.coerceAtMost(w / 2f)
        waveRight = (w - waveSideInset).coerceAtLeast(waveLeft)

        // Keep the mint gradient entirely within the center waveform region.
        wavePaint.shader = LinearGradient(
            waveLeft, 0f, (waveRight).coerceAtLeast(waveLeft + 1f), 0f,
            intArrayOf(
                Color.rgb(76, 195, 171),
                Color.rgb(116, 232, 190),
                Color.rgb(185, 237, 207),
                Color.rgb(116, 232, 190)
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val radius = height / 2f
        canvas.drawRoundRect(pill, radius, radius, pillBackground)
        canvas.save()
        canvas.clipPath(clippingPath)

        val centerY = height / 2f
        val barWidth = 3f * density
        val stride = 7f * density
        val minBarHeight = 6f * density
        val maxExtraHeight = 28f * density
        val availableWidth = waveRight - waveLeft
        val cycle = phase * (2f * Math.PI.toFloat())

        if (availableWidth >= barWidth) {
            // Center the bars between the buttons, never drawing beneath them.
            val numBars = ((availableWidth - barWidth) / stride).toInt() + 1
            val barsWidth = barWidth + (numBars - 1) * stride
            val firstCenter = waveLeft + (availableWidth - barsWidth) / 2f + barWidth / 2f

            canvas.save()
            canvas.clipRect(waveLeft, 0f, waveRight, height.toFloat())
            for (index in 0 until numBars) {
                val x = firstCenter + index * stride
                val carrier = abs(sin(index * 0.55f + cycle * 1.55f))
                val secondary = abs(sin(index * 0.23f - cycle * 0.9f))
                val envelope = 0.23f + 0.77f * (0.68f * carrier + 0.32f * secondary)
                val barHeight = minBarHeight + maxExtraHeight * envelope
                canvas.drawRoundRect(
                    x - barWidth / 2f,
                    centerY - barHeight / 2f,
                    x + barWidth / 2f,
                    centerY + barHeight / 2f,
                    barWidth / 2f,
                    barWidth / 2f,
                    wavePaint
                )
            }
            canvas.restore()
        }

        canvas.restore()
        // Subtle outline keeps the pill visible over both light and dark keyboards.
        val halfStroke = pillBorder.strokeWidth / 2f
        canvas.drawRoundRect(
            halfStroke, halfStroke,
            width - halfStroke, height - halfStroke,
            radius, radius,
            pillBorder
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel() // No animators survive a transition to Processing, Ready or Hidden.
        super.onDetachedFromWindow()
    }
}
