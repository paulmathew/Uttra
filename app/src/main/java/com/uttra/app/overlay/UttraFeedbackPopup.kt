
package com.uttra.app.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

internal class UttraFeedbackPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var popupView: View? = null

    private val darkGreen = Color.rgb(18, 59, 53)
    private val mint = Color.rgb(116, 232, 190)

    private fun Int.dp(): Int =
        (this * context.resources.displayMetrics.density).toInt()

    fun show(onSubmit: (Int) -> Unit) {
        dismiss()

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = "How accurate?"
            textSize = 13f
            setTextColor(Color.WHITE)
            includeFontPadding = false
        }

        val ratingLabel = TextView(context).apply {
            text = "50 / 100"
            textSize = 13f
            setTextColor(mint)
            gravity = Gravity.END
            includeFontPadding = false
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(ratingLabel)

        val slider = SeekBar(context).apply {
            max = 100
            progress = 50

            progressTintList = ColorStateList.valueOf(mint)
            thumbTintList = ColorStateList.valueOf(mint)
            progressBackgroundTintList =
                ColorStateList.valueOf(
                    Color.argb(75, 255, 255, 255)
                )

            contentDescription = "Dictation accuracy rating"

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        ratingLabel.text = "$progress / 100"
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) = Unit

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) = Unit
                }
            )
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val skip = TextView(context).apply {
            text = "Skip"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Skip accuracy rating"

            setOnClickListener { dismiss() }
        }

        val done = TextView(context).apply {
            text = "Done"
            textSize = 13f
            setTextColor(darkGreen)
            gravity = Gravity.CENTER
            contentDescription = "Finish accuracy rating"

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14.dp().toFloat()
                setColor(mint)
            }

            setOnClickListener {
                val rating = slider.progress
                dismiss()
                onSubmit(rating)
            }
        }

        actions.addView(
            skip,
            LinearLayout.LayoutParams(68.dp(), 44.dp())
        )

        actions.addView(
            done,
            LinearLayout.LayoutParams(76.dp(), 44.dp())
        )

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                12.dp(), 6.dp(),
                12.dp(), 4.dp()
            )

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18.dp().toFloat()
                setColor(darkGreen)
            }

            elevation = 8.dp().toFloat()

            addView(header)

            addView(
                slider,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    28.dp()
                )
            )

            addView(actions)
        }

        val params = WindowManager.LayoutParams(
            248.dp(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 8.dp()
            y = (-195).dp()
        }

        windowManager.addView(card, params)
        popupView = card
    }

    fun dismiss() {
        val view = popupView ?: return
        popupView = null

        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // Already detached.
        }
    }
}
