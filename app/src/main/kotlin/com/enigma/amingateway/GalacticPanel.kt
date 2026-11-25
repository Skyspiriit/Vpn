package com.enigma.amingateway

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Original vector artwork. The rendered rings follow the verified state from GatewayConnection. */
internal class GalacticPanel(context: Context) : View(context) {
    private data class Star(val x: Float, val y: Float, val size: Float, val color: Int)

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val startTime = SystemClock.uptimeMillis()
    private val stars = Random(76119).let { random ->
        List(240) { index ->
            val brightness = if (index % 17 == 0) 138 else 18 + random.nextInt(65)
            Star(random.nextFloat(), random.nextFloat(), (0.3f + random.nextFloat() * 0.55f) * dp,
                (brightness shl 24) or if (index % 5 == 0) 0xbbaee8 else 0x9bd6ef)
        }
    }

    private var x = 0f
    private var y = 0f
    private var r = 0f
    private var headerEnd = 0f
    private var footerY = 0f
    private var haze: Shader? = null
    private var starlight: Shader? = null
    private var face: Shader? = null
    private var liveFace: Shader? = null
    private var halo: Shader? = null
    private var scanning: Shader? = null
    private var amberScan: Shader? = null
    private var edge: Shader? = null

    var phase = Phase.OFF
        set(value) {
            if (field == value) return
            field = value
            changedAt = SystemClock.uptimeMillis()
            invalidate()
        }
    private var changedAt = startTime

    /** True while the central button has D-pad focus: draws a bright selection ring. */
    var focused = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun place(x: Float, y: Float, radius: Float, headerEnd: Float, footerY: Float) {
        if (radius <= 0f) return
        if (this.x != x || this.y != y || r != radius) {
            this.x = x
            this.y = y
            r = radius
            haze = radial(x, y, r * 3.85f, intArrayOf(0xff102639.toInt(), 0xff0a1424.toInt(), 0xff040812.toInt()),
                floatArrayOf(0f, 0.5f, 1f))
            starlight = radial(x - r * 1.8f, y + r * 0.4f, r * 2.4f,
                intArrayOf(0x225d4f99, 0x0c67509a, 0x00564c9e), null)
            face = radial(x, y - r * 0.62f, r * 1.8f,
                intArrayOf(0xff132d3d.toInt(), 0xff0a1929.toInt(), 0xff07121f.toInt()), null)
            liveFace = radial(x, y - r * 0.4f, r * 1.61f,
                intArrayOf(0xff183e4c.toInt(), 0xff102d3a.toInt(), 0xff061923.toInt()), null)
            halo = radial(x, y, r * 1.85f,
                intArrayOf(0x000afbfb, 0x0037eedb, 0x963df9eb.toInt(), 0x352bced7, 0x0023589a),
                floatArrayOf(0f, 0.47f, 0.54f, 0.66f, 1f))
            scanning = SweepGradient(x, y,
                intArrayOf(0x003afaef, 0x003afaef, 0x183afaef, 0xcc8bfff5.toInt(), 0x003afaef),
                floatArrayOf(0f, 0.64f, 0.79f, 0.965f, 1f))
            amberScan = SweepGradient(x, y,
                intArrayOf(0x00ffc93a, 0x00ffc93a, 0x22ffc93a, 0xeeffe27a.toInt(), 0x00ffc93a),
                floatArrayOf(0f, 0.6f, 0.77f, 0.965f, 1f))
            edge = LinearGradient(x - r, y - r, x + r, y + r,
                intArrayOf(0xff62bfcc.toInt(), 0xff285369.toInt(), 0xff1c3f55.toInt(), 0xff3a8395.toInt()), null, Shader.TileMode.CLAMP)
        }
        this.headerEnd = headerEnd
        this.footerY = footerY
        invalidate()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (r <= 0f) return
        val now = SystemClock.uptimeMillis()
        val motion = ValueAnimator.areAnimatorsEnabled()
        val phaseSeconds = if (motion) (now - changedAt) / 1000f else 1f
        val on = phase == Phase.ON
        val active = phase == Phase.CONNECTING || phase == Phase.DISCONNECTING
        val connecting = phase == Phase.CONNECTING
        val failed = phase == Phase.FAILED
        val power = if (on) (phaseSeconds / 0.72f).coerceIn(0f, 1f) else 0f
        val wave = (0.5f + 0.5f * sin(phaseSeconds * 1.7f))

        shaded(haze)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        shaded(starlight)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        for (star in stars) {
            color(star.color)
            canvas.drawCircle(star.x * width, star.y * height, star.size, paint)
        }
        drawFrame(canvas)
        drawInstrument(canvas, on)

        if (on) {
            shaded(halo, ((185 + wave * 48) * power).toInt())
            canvas.drawCircle(x, y, r * 1.85f, paint)
            val pulse = (phaseSeconds % 5.0f) / 5.0f
            circle(canvas, r * (1.02f + pulse * 0.32f), 1.0f,
                (((1f - pulse) * power * 144).toInt() shl 24) or 0x4bffef)
        }

        shaded(if (on) liveFace else face)
        canvas.drawCircle(x, y, r, paint)
        if (on) {
            circle(canvas, r, 2.2f, 0xff8dfff2.toInt())
        } else if (failed) {
            circle(canvas, r, 2.6f, 0xffff5f56.toInt())
        } else if (connecting) {
            circle(canvas, r, 2.2f, (((150 + wave * 105).toInt()) shl 24) or 0xffc93a)
        } else {
            shaded(edge, stroke = 1.35f)
            canvas.drawCircle(x, y, r, paint)
        }
        circle(canvas, r * 0.955f, 0.8f, if (on) 0x8065eedd.toInt() else 0x54418198)
        circle(canvas, r * 0.775f, 0.7f, if (on) 0x4549e4e2 else 0x394d849b)
        val faceY = y - r * 0.16f
        val glyph = r * 0.25f
        if (on) drawPower(canvas, faceY, glyph, 10f, 0x2e4cffff)
        val glyphTint = when {
            on -> 0xffb6fff6.toInt()
            connecting -> 0xffffd866.toInt()
            failed -> 0xffff7d74.toInt()
            phase == Phase.OFF -> 0xff9aa7b0.toInt() // grey: disconnected
            else -> 0xff71b5c7.toInt()
        }
        drawPower(canvas, faceY, glyph, if (on) 3.9f else 3f, glyphTint)
        if (focused) circle(canvas, r * 1.075f, 3.2f, 0xeef4fbff.toInt())

        if (active) {
            canvas.save()
            canvas.rotate(phaseSeconds * 86f, x, y)
            shaded(if (connecting) amberScan else scanning, stroke = if (connecting) 3.2f else 2.5f)
            canvas.drawCircle(x, y, r * 1.12f, paint)
            canvas.restore()
        }
        if (on) {
            for (i in 0 until 6) {
                val angle = phaseSeconds * 0.22 + i * (2 * PI / 6)
                val orbit = r * (1.19f + (i % 3) * 0.05f)
                color(if (i % 2 == 0) 0xaa93fff1.toInt() else 0x797e95d1)
                canvas.drawCircle(x + cos(angle).toFloat() * orbit, y + sin(angle).toFloat() * orbit,
                    if (i % 2 == 0) dp * 1.75f else dp * 1.1f, paint)
            }
        }
        if ((on || active) && motion && windowVisibility == VISIBLE && isShown) postInvalidateDelayed(FRAME_MS)
    }

    private companion object {
        // ~24 fps: smooth enough for the slow rings, light on a low-end TV box GPU/CPU.
        const val FRAME_MS = 42L
    }

    private fun drawFrame(canvas: Canvas) {
        val inset = minOf(dp * 31f, width * 0.06f)
        val room = minOf(width * 0.22f, r * 1.03f)
        val axis = r * 1.43f
        color(0x39498099, 0.85f)
        canvas.drawLine(x - room, headerEnd + dp * 15, x - dp * 16, headerEnd + dp * 15, paint)
        canvas.drawLine(x + dp * 16, headerEnd + dp * 15, x + room, headerEnd + dp * 15, paint)
        color(0xff54a6b6.toInt(), 1.1f)
        canvas.drawLine(x - 3.5f * dp, headerEnd + dp * 15, x + 3.5f * dp, headerEnd + dp * 15, paint)
        color(0x555999ae, 0.8f)
        val leftEdge = x - axis - 12 * dp
        val rightEdge = x + axis + 12 * dp
        if (leftEdge > inset + dp * 5) canvas.drawLine(inset, y, leftEdge, y, paint)
        if (rightEdge < width - inset - dp * 5) canvas.drawLine(rightEdge, y, width - inset, y, paint)
        for (side in floatArrayOf(inset, width - inset)) {
            canvas.drawLine(side, y - dp * 23, side, y + dp * 23, paint)
            canvas.drawLine(side - dp * 2.5f, y, side + dp * 2.5f, y, paint)
        }
        val span = minOf(width * 0.36f, dp * 240f)
        color(0x3a68869a, 0.8f)
        canvas.drawLine(x - span, footerY - dp * 21, x + span, footerY - dp * 21, paint)
    }

    private fun drawInstrument(canvas: Canvas, on: Boolean) {
        circle(canvas, r * 1.32f, 0.7f, 0x4c426f88)
        circle(canvas, r * 1.147f, 0.7f, 0x684f8397)
        circle(canvas, r * 1.044f, 0.7f, 0x4c426f88)
        setOval(r * 1.333f)
        color(if (on) 0x9a6ff3ed.toInt() else 0x9768abc3.toInt(), 1.3f)
        for (angle in listOf(34f, 124f, 214f, 304f)) canvas.drawArc(oval, angle, 22f, false, paint)
        setOval(r * 1.078f)
        color(0x7a6467a2, 1.2f)
        canvas.drawArc(oval, 140f, 32f, false, paint)
        canvas.drawArc(oval, 320f, 32f, false, paint)

        for (i in 0 until 72) {
            val angle = i * PI / 36
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            val major = i % 6 == 0
            val inner = r * (if (major) 1.205f else 1.23f)
            val outer = r * 1.258f
            color(if (major) 0x856cabc0.toInt() else 0x5048849a, if (major) 1.2f else 0.65f)
            canvas.drawLine(x + dx * inner, y + dy * inner, x + dx * outer, y + dy * outer, paint)
        }
    }

    private fun drawPower(canvas: Canvas, cy: Float, radius: Float, thickness: Float, tint: Int) {
        color(tint, thickness)
        paint.strokeCap = Paint.Cap.ROUND
        oval.set(x - radius, cy - radius, x + radius, cy + radius)
        canvas.drawArc(oval, -52f, 284f, false, paint)
        canvas.drawLine(x, cy - radius * 1.22f, x, cy - radius * 0.15f, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun setOval(radius: Float) = oval.set(x - radius, y - radius, x + radius, y + radius)

    private fun circle(canvas: Canvas, radius: Float, stroke: Float, tint: Int) {
        color(tint, stroke)
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun color(tint: Int, stroke: Float = 0f) {
        paint.shader = null
        paint.color = tint
        paint.style = if (stroke == 0f) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = stroke * dp
    }

    private fun shaded(shader: Shader?, alpha: Int = 255, stroke: Float = 0f) {
        color((alpha shl 24) or 0xffffff, stroke)
        paint.shader = shader
    }

    private fun radial(x: Float, y: Float, radius: Float, colors: IntArray, stops: FloatArray?): Shader =
        RadialGradient(x, y, radius, colors, stops, Shader.TileMode.CLAMP)
}
