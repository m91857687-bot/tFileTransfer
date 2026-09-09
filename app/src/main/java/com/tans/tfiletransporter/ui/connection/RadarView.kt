package com.tans.tfiletransporter.ui.connection

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.tans.tfiletransporter.R
import androidx.core.content.ContextCompat

class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, R.color.teal_200)
    }
    
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.teal_200)
    }

    private var radius = 0f
    private var maxRadius = 0f
    private var animator: ValueAnimator? = null
    
    private var isSearching = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maxRadius = (Math.min(w, h) / 2f) * 0.9f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        // Draw center pulse
        canvas.drawCircle(cx, cy, 20f, centerPaint)

        if (isSearching) {
            // Draw expanding circle
            paint.alpha = (255 * (1f - radius / maxRadius)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, radius, paint)
            
            // Draw second expanding circle
            var radius2 = radius - (maxRadius / 2f)
            if (radius2 < 0) radius2 += maxRadius
            paint.alpha = (255 * (1f - radius2 / maxRadius)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, radius2, paint)
        }
    }

    fun start() {
        if (isSearching) return
        isSearching = true
        animator = ValueAnimator.ofFloat(0f, maxRadius).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                radius = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        isSearching = false
        animator?.cancel()
        animator = null
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}
