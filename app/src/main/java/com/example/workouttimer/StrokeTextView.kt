package com.example.workouttimer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.example.workouttimer.constants.AppMagicNumbers

class StrokeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = AppMagicNumbers.Common.INT_ZERO,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var strokeColor: Int = Color.BLACK
    private var strokeWidthPx: Float = AppMagicNumbers.Common.FLOAT_ZERO
    private var fillColor: Int = currentTextColor

    init {
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.StrokeTextView)
            strokeColor = ta.getColor(R.styleable.StrokeTextView_strokeColor, Color.BLACK)
            strokeWidthPx = ta.getDimension(R.styleable.StrokeTextView_strokeWidth, AppMagicNumbers.Common.FLOAT_ZERO)
            ta.recycle()
        }
    }

    override fun setTextColor(color: Int) {
        super.setTextColor(color)
        fillColor = color
    }

    override fun setTextColor(colors: ColorStateList?) {
        super.setTextColor(colors)
        fillColor = currentTextColor
    }

    override fun onDraw(canvas: Canvas) {
        if (strokeWidthPx > AppMagicNumbers.Common.FLOAT_ZERO) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidthPx
            super.setTextColor(strokeColor)
            super.onDraw(canvas)

            paint.style = Paint.Style.FILL
            super.setTextColor(fillColor)
        }
        super.onDraw(canvas)
    }
}



