package com.enigma.amingateway

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Screen options. The defaults reproduce the original Amin Gateway (legacy Capsule) screen exactly;
 * Amin VPN passes Persian text, a larger type scale for TV viewing distance and a secondary button.
 */
internal data class ConsoleOptions(
    val title: Int = R.string.gateway_title,
    val signoff: Int = R.string.gateway_signoff,
    val scale: Float = 1f,
    val secondaryLabel: Int? = null,
    val onSecondary: (() -> Unit)? = null,
)

@SuppressLint("ViewConstructor") // The activity creates and wires this view directly.
internal class GatewayConsole(
    context: Context,
    private val options: ConsoleOptions = ConsoleOptions(),
    onPower: () -> Unit,
) : ViewGroup(context) {
    private val density = resources.displayMetrics.density
    private val art = GalacticPanel(context)
    private val title = text(29f, 0xffe2f5fd.toInt()).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        letterSpacing = 0.22f
        setText(options.title)
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }
    private val power = Button(context).apply {
        textSize = 13f * options.scale
        letterSpacing = 0.13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        setAllCaps(false)
        isSingleLine = false
        maxLines = 2
        ellipsize = null
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        stateListAnimator = null
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        background = RippleDrawable(ColorStateList.valueOf(0x5081ffff), null, mask)
        minWidth = 0
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        setOnClickListener { onPower() }
        // D-pad / remote control: the big button is the default focus and shows a bright ring.
        isFocusable = true
        setOnFocusChangeListener { _, focused -> art.focused = focused }
    }
    private val status = text(19f * options.scale, 0xffd3e7ee.toInt()).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val detail = text(13f * options.scale, 0xffa9bfcb.toInt()).apply { visibility = GONE }
    private val footer = text(13f * (1f + (options.scale - 1f) / 2f), 0xff9ab3c4.toInt()).apply {
        typeface = Typeface.create("serif", Typeface.ITALIC)
        setText(options.signoff)
    }
    private val secondary: Button? = options.secondaryLabel?.let { label ->
        Button(context).apply {
            setText(label)
            textSize = 12f * options.scale
            setAllCaps(false)
            stateListAnimator = null
            setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(0xff04121c.toInt(), 0xffcfe6ef.toInt())))
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), pill(0xffbff9f3.toInt(), 0xffbff9f3.toInt()))
                addState(intArrayOf(android.R.attr.state_pressed), pill(0x4081ffff, 0xff8dfff2.toInt()))
                addState(intArrayOf(), pill(0x1881ffff, 0x80568fa3.toInt()))
            }
            setPadding(dp(22), dp(8), dp(22), dp(8))
            isFocusable = true
            setOnClickListener { options.onSecondary?.invoke() }
        }
    }

    private var leftInset = 0
    private var topInset = 0
    private var rightInset = 0
    private var bottomInset = 0
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var titleY = 0f
    private var statusY = 0f
    private var bottomY = 0f
    private var statusGap = 0

    init {
        addView(art)
        addView(title)
        addView(power)
        addView(status)
        addView(detail)
        addView(footer)
        secondary?.let { button ->
            addView(button)
            power.id = generateViewId()
            button.id = generateViewId()
            power.nextFocusDownId = button.id
            button.nextFocusUpId = power.id
        }
        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                leftInset = safe.left
                topInset = safe.top
                rightInset = safe.right
                bottomInset = safe.bottom
            } else {
                @Suppress("DEPRECATION")
                leftInset = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                rightInset = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            requestLayout()
            insets
        }
    }

    fun focusPower() = power.requestFocus()

    fun render(state: ConsoleState) {
        art.phase = state.phase
        val label = state.label ?: when {
            state.phase == Phase.CONNECTING -> "CONNECTING"
            state.phase == Phase.DISCONNECTING -> "DISCONNECTING"
            state.canDisconnect -> "DISCONNECT FROM\nTHE GALAXY"
            else -> "CONNECT TO\nTHE GALAXY"
        }
        power.text = label
        power.contentDescription = label.replace('\n', ' ')
        power.isEnabled = state.enabled ?: (state.phase != Phase.CONNECTING && state.phase != Phase.DISCONNECTING)
        power.setTextColor(when (state.phase) {
            Phase.ON -> 0xffbefff6.toInt()
            Phase.CONNECTING -> 0xffffe9a8.toInt()
            Phase.FAILED -> 0xffffc9c4.toInt()
            else -> 0xffd5f2fb.toInt()
        })
        status.text = state.status
        status.setTextColor(when (state.phase) {
            Phase.ON -> 0xff9affed.toInt()
            Phase.CONNECTING -> 0xffffd866.toInt()
            Phase.FAILED -> 0xffff8f86.toInt()
            else -> 0xffd3e7ee.toInt()
        })
        detail.text = state.detail
        detail.visibility = if (state.detail.isEmpty()) GONE else VISIBLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val safeWidth = (width - leftInset - rightInset).coerceAtLeast(1)
        val safeHeight = (height - topInset - bottomInset).coerceAtLeast(1)
        val compact = safeHeight < density * 510f
        val wrapWidth = MeasureSpec.makeMeasureSpec((safeWidth - dp(40)).coerceAtLeast(1), MeasureSpec.AT_MOST)
        val wrapHeight = MeasureSpec.makeMeasureSpec(safeHeight, MeasureSpec.AT_MOST)
        title.measure(wrapWidth, wrapHeight)
        status.measure(wrapWidth, wrapHeight)
        if (detail.visibility == VISIBLE) detail.measure(wrapWidth, wrapHeight)
        footer.measure(wrapWidth, wrapHeight)
        secondary?.measure(wrapWidth, wrapHeight)
        val secondaryRoom = secondary?.let { it.measuredHeight + dp(if (compact) 8 else 14) } ?: 0

        centerX = leftInset + safeWidth / 2f
        titleY = topInset + (safeHeight * 0.112f).coerceIn(dp(12).toFloat(), dp(92).toFloat()) + title.measuredHeight / 2f
        bottomY = height - bottomInset - dp(if (compact) 16 else 27) - footer.measuredHeight / 2f
        val maxAbove = titleY + title.measuredHeight / 2f + dp(if (compact) 16 else 34)
        val maxBelow = bottomY - footer.measuredHeight / 2f - dp(15) - secondaryRoom
        statusGap = dp(if (compact) 6 else 10)
        val statusBlock = status.measuredHeight + if (detail.visibility == VISIBLE) detail.measuredHeight + statusGap else 0
        val statusMargin = dp(if (compact) 10 else 19)
        val available = (maxBelow - maxAbove - statusBlock - statusMargin).coerceAtLeast(dp(90).toFloat())
        radius = min(min(dp(178).toFloat(), safeWidth * 0.325f), available / 2.67f)
        centerY = ((maxAbove + maxBelow - statusBlock - statusMargin) / 2f).coerceAtLeast(maxAbove + radius * 1.32f)
        statusY = centerY + radius * 1.32f + statusMargin

        val side = (radius * 2f).roundToInt()
        power.setPadding(dp(4), 0, dp(4), (radius * 0.26f).roundToInt())
        power.measure(exact(side), exact(side))
        art.measure(exact(width), exact(height))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        art.layout(0, 0, measuredWidth, measuredHeight)
        art.place(centerX, centerY, radius, titleY + title.measuredHeight / 2f, bottomY)
        centered(title, titleY)
        centered(power, centerY)
        centered(status, statusY + status.measuredHeight / 2f)
        if (detail.visibility == VISIBLE) centered(detail,
            statusY + status.measuredHeight + statusGap + detail.measuredHeight / 2f)
        centered(footer, bottomY)
        secondary?.let { centered(it, bottomY - footer.measuredHeight / 2f - dp(10) - it.measuredHeight / 2f) }
    }

    private fun pill(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(40).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun centered(view: View, y: Float) {
        val xStart = (centerX - view.measuredWidth / 2f).roundToInt()
        val yStart = (y - view.measuredHeight / 2f).roundToInt()
        view.layout(xStart, yStart, xStart + view.measuredWidth, yStart + view.measuredHeight)
    }

    private fun text(size: Float, color: Int) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        gravity = Gravity.CENTER
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun dp(value: Int) = (value * density).roundToInt()
    private fun exact(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
}
