package com.bolke.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.bolke.keyboard.R
import kotlin.math.min

/**
 * A custom view that draws a microphone icon with animated ripples
 * that react to voice amplitude.
 */
class VoiceRippleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val micPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var amplitude: Float = 0f
    private var baseRadius: Float = 0f
    
    private val rippleColor = ContextCompat.getColor(context, R.color.accent_blue)
    private val micColor = ContextCompat.getColor(context, R.color.key_text)

    init {
        // Use a static mic icon drawable to draw in the center
    }

    /**
     * Set the current voice amplitude (typically 0.0 to 1.0).
     */
    fun setAmplitude(value: Float) {
        amplitude = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        baseRadius = (min(w, h) / 4f)
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f

        // Draw 3 layers of ripples
        for (i in 3 downTo 1) {
            val rippleScale = 1f + (amplitude * (i * 0.5f)) + (i * 0.2f)
            val alpha = (255 * (1f - (i / 4f)) * (0.3f + (amplitude * 0.7f))).toInt()
            
            ripplePaint.color = rippleColor
            ripplePaint.alpha = alpha.coerceIn(0, 255)
            
            canvas.drawCircle(centerX, centerY, baseRadius * rippleScale, ripplePaint)
        }

        // Draw the center circle (Mic base)
        ripplePaint.color = ContextCompat.getColor(context, R.color.mic_gradient_start)
        ripplePaint.alpha = 255
        canvas.drawCircle(centerX, centerY, baseRadius, ripplePaint)

        // Draw Mic Icon (Simplified as a small rect + semi-circle for this implementation)
        micPaint.color = ContextCompat.getColor(context, R.color.keyboard_bg)
        val micWidth = baseRadius * 0.4f
        val micHeight = baseRadius * 0.7f
        canvas.drawRoundRect(
            centerX - micWidth / 2f,
            centerY - micHeight / 2f,
            centerX + micWidth / 2f,
            centerY + micHeight / 2f,
            micWidth / 2f,
            micWidth / 2f,
            micPaint
        )
    }
}
