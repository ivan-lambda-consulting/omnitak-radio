package soy.engindearing.omnitak.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioLocationSessionTest {
    private var permitted = false
    private var starts = 0
    private var stops = 0
    private var startSucceeds = true

    private val session = RadioLocationSession(
        hasPermission = { permitted },
        startUpdates = { starts++; startSucceeds },
        stopUpdates = { stops++ },
    )

    @Test fun permission_grant_starts_location_without_a_map_screen() {
        session.activityVisible(true)
        assertEquals(RadioLocationSession.Status.PERMISSION_REQUIRED, session.status.value)
        assertEquals(0, starts)

        permitted = true
        session.permissionChanged()
        assertEquals(RadioLocationSession.Status.RUNNING, session.status.value)
        assertEquals(1, starts)

        session.permissionChanged()
        assertEquals("a repeat permission callback must not duplicate subscriptions", 1, starts)
    }

    @Test fun location_foreground_service_keeps_updates_running_when_screen_turns_off() {
        permitted = true
        session.activityVisible(true)
        session.serviceForegrounded(locationType = true)
        session.activityVisible(false)

        assertEquals(RadioLocationSession.Status.RUNNING, session.status.value)
        assertEquals(1, starts)
        assertEquals(0, stops)

        session.serviceStopped()
        assertEquals(RadioLocationSession.Status.INACTIVE, session.status.value)
        assertEquals(1, stops)
    }

    @Test fun data_sync_fallback_is_not_treated_as_screen_off_location_privilege() {
        permitted = true
        session.activityVisible(true)
        session.serviceForegrounded(locationType = false)
        session.activityVisible(false)

        assertEquals(RadioLocationSession.Status.FOREGROUND_UNAVAILABLE, session.status.value)
        assertEquals(1, stops)
    }

    @Test fun revocation_stops_updates_and_retry_on_grant_recovers() {
        permitted = true
        session.serviceForegrounded(locationType = true)
        permitted = false
        session.permissionChanged()

        assertEquals(RadioLocationSession.Status.PERMISSION_REQUIRED, session.status.value)
        assertEquals(1, stops)

        permitted = true
        session.permissionChanged()
        assertEquals(RadioLocationSession.Status.RUNNING, session.status.value)
        assertEquals(2, starts)
    }

    @Test fun unsuccessful_location_start_is_not_reported_as_running() {
        permitted = true
        startSucceeds = false
        session.serviceForegrounded(locationType = true)

        assertEquals(RadioLocationSession.Status.UNAVAILABLE, session.status.value)
        assertEquals(1, starts)
        assertEquals(0, stops)

        startSucceeds = true
        session.permissionChanged()
        assertEquals(RadioLocationSession.Status.RUNNING, session.status.value)
    }
}
