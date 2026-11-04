package soy.engindearing.omnitak.mobile.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import soy.engindearing.omnitak.mobile.domain.ConnectionState

class RadioHomeScreenTest {
    @Test fun shows_connection_state_without_exposing_failure_details() {
        assertEquals("TAK disconnected", connectionLabel(ConnectionState.Disconnected))
        assertEquals("TAK connecting", connectionLabel(ConnectionState.Connecting("server")))
        assertEquals("TAK connected: server", connectionLabel(ConnectionState.Connected("server", true)))
        assertEquals("TAK connection failed", connectionLabel(ConnectionState.Failed("secret detail")))
    }
}
