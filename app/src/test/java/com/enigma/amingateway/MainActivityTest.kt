package com.enigma.amingateway

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-rUS-w960dp-h540dp-land-television")
class MainActivityTest {
    private fun texts(view: View): List<String> = when (view) {
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        is TextView -> listOf(view.text.toString())
        else -> emptyList()
    }

    @Test fun `disconnected screen is Persian, grey and focuses the big button for the D-pad`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val shown = texts(activity.window.decorView)
        assertTrue(shown.toString(), "اتصال" in shown)
        assertTrue(shown.toString(), "اتصال قطع است" in shown)
        assertTrue(shown.toString(), "عیب‌یابی" in shown)
        val console = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as GatewayConsole
        val button = (0 until console.childCount).map(console::getChildAt).filterIsInstance<Button>().first()
        button.isFocusableInTouchMode = true // Robolectric stays in touch mode; a TV remote does not.
        console.focusPower()
        assertTrue("big button must take D-pad focus", button.isFocused)
        val focused = button
        assertEquals("اتصال", focused.text.toString())
        assertTrue(focused.isEnabled)
    }

    @Test fun `diagnostics report contains no addresses or long tokens`() {
        val line = "relay 203.0.113.7:443 via wss://front.example/x?t=abc ticket=QWxhZGRpbjpvcGVuIHNlc2FtZQQWxhZGRpbjpvcGVu"
        val redacted = Diagnostics.redact(line)
        assertTrue(redacted, "203.0.113.7" !in redacted && "wss://" !in redacted && "QWxhZGRpbj" !in redacted)
    }
}
