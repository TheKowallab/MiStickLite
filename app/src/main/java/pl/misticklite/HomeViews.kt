package pl.misticklite

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import kotlin.math.min

object Palette {
    val background = Color.rgb(0x12, 0x1A, 0x2B)
    val cream = Color.rgb(0xF3, 0xE8, 0xCE)
    val tile = Color.rgb(0x20, 0x2B, 0x40)
    val muted = Color.rgb(0xAA, 0xB1, 0xC0)
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

class DotsView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    init {
        isFocusable = true
        isClickable = true
        isLongClickable = true
        contentDescription = context.getString(R.string.menu_description)
    }
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = Palette.cream
        paint.style = Paint.Style.FILL
        val step = context.dp(15).toFloat()
        for (i in -1..1) canvas.drawCircle(width / 2f + i * step, height / 2f, context.dp(3).toFloat(), paint)
        if (isFocused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dp(2).toFloat()
            rect.set(2f, 2f, width - 2f, height - 2f)
            canvas.drawRoundRect(rect, context.dp(12).toFloat(), context.dp(12).toFloat(), paint)
        }
    }
}

/** One small View per app, with artwork held only in the shared bounded cache. */
@SuppressLint("ViewConstructor") // Created in code with required app data, never inflated from XML.
class AppTileView(context: Context, val app: TvApp, private val artwork: ArtworkLoader) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.cream
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val tileRect = RectF()
    private val imageRect = RectF()
    private val clip = Path()
    private var label = app.key.label
    var moving: Boolean = false
        set(value) { field = value; invalidate() }

    init {
        id = generateViewId()
        isFocusable = true
        isClickable = true
        isLongClickable = true
        contentDescription = context.getString(R.string.app_description, app.key.label)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inset = context.dp(5).toFloat()
        tileRect.set(inset, inset, w - inset, inset + (w - inset * 2) * 9f / 16f)
        clip.reset()
        clip.addRoundRect(tileRect, context.dp(14).toFloat(), context.dp(14).toFloat(), Path.Direction.CW)
        label = TextUtils.ellipsize(app.key.label, textPaint, (w - context.dp(12)).toFloat(), TextUtils.TruncateAt.END).toString()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        paint.color = Palette.tile
        canvas.drawRoundRect(tileRect, context.dp(14).toFloat(), context.dp(14).toFloat(), paint)
        val image = artwork.get(app.key.packageName)
        if (image != null) {
            val bitmap = image.bitmap
            val maxW = if (image.banner) tileRect.width() else context.dp(68).toFloat()
            val maxH = if (image.banner) tileRect.height() else context.dp(68).toFloat()
            val scale = min(maxW / bitmap.width, maxH / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            imageRect.set(tileRect.centerX() - w / 2, tileRect.centerY() - h / 2,
                tileRect.centerX() + w / 2, tileRect.centerY() + h / 2)
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(bitmap, null, imageRect, paint)
            canvas.restore()
        } else {
            canvas.drawText(label, width / 2f, tileRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        }
        if (isFocused || moving) {
            paint.style = Paint.Style.STROKE
            paint.color = Palette.cream
            paint.strokeWidth = context.dp(if (moving) 6 else 4).toFloat()
            canvas.drawRoundRect(tileRect, context.dp(14).toFloat(), context.dp(14).toFloat(), paint)
        }
        textPaint.alpha = if (isFocused) 255 else 210
        canvas.drawText(label, width / 2f, tileRect.bottom + context.dp(29), textPaint)
        textPaint.alpha = 255
    }
}
