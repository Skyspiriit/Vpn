package com.openrung.state

import android.content.Context
import com.openrung.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ColdStartStatusTest {
    private fun restore(saved: ConnectionStatus, expected: ConnectionStatus) {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(AppConfig.STATUS_PREFS, Context.MODE_PRIVATE)
            .edit().putString("status", saved.name).commit()
        OpenRungStatusStore.initialize(context)
        assertEquals(expected, OpenRungStatusStore.uiState.value.status)
    }

    @Test fun `a stopped preparation is disconnected on cold launch`() =
        restore(ConnectionStatus.PREPARING, ConnectionStatus.DISCONNECTED)

    @Test fun `a stopped connection attempt is disconnected on cold launch`() =
        restore(ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTED)

    @Test fun `a stopped disconnect is disconnected on cold launch`() =
        restore(ConnectionStatus.DISCONNECTING, ConnectionStatus.DISCONNECTED)

    @Test fun `a stopped tunnel is disconnected on cold launch`() =
        restore(ConnectionStatus.CONNECTED, ConnectionStatus.DISCONNECTED)

    @Test fun `a recorded failure remains available on cold launch`() =
        restore(ConnectionStatus.FAILED, ConnectionStatus.FAILED)
}
