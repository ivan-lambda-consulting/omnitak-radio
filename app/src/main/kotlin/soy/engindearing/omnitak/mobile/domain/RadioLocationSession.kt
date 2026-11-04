package soy.engindearing.omnitak.mobile.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the live GPS subscription independently of any screen. The visible
 * Activity may use location before a link is up; once it goes away, only a
 * successfully elevated location foreground service may keep updates running.
 * A dataSync fallback keeps the socket alive, but is not a location privilege.
 */
class RadioLocationSession(
    private val hasPermission: () -> Boolean,
    private val startUpdates: () -> Boolean,
    private val stopUpdates: () -> Unit,
) {
    enum class Status { INACTIVE, PERMISSION_REQUIRED, RUNNING, FOREGROUND_UNAVAILABLE, UNAVAILABLE }

    private val _status = MutableStateFlow(Status.INACTIVE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var visible = false
    private var serviceActive = false
    private var serviceHasLocationType = false
    private var running = false

    @Synchronized
    fun activityVisible(value: Boolean) {
        visible = value
        reconcile()
    }

    @Synchronized
    fun serviceForegrounded(locationType: Boolean) {
        serviceActive = true
        serviceHasLocationType = locationType
        reconcile()
    }

    @Synchronized
    fun serviceStopped() {
        serviceActive = false
        serviceHasLocationType = false
        reconcile()
    }

    /** Called after a permission result and on return from system settings. */
    @Synchronized
    fun permissionChanged() = reconcile()

    private fun reconcile() {
        if (!hasPermission()) {
            stopIfRunning()
            _status.value = Status.PERMISSION_REQUIRED
            return
        }

        if (!visible && !serviceHasLocationType) {
            stopIfRunning()
            _status.value = if (serviceActive) Status.FOREGROUND_UNAVAILABLE else Status.INACTIVE
            return
        }

        if (!running) {
            // Permission may be revoked between checkSelfPermission and the
            // fused-provider call, or the device may have no location service.
            running = try {
                startUpdates()
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }
        _status.value = if (running) Status.RUNNING else Status.UNAVAILABLE
    }

    private fun stopIfRunning() {
        if (running) {
            stopUpdates()
            running = false
        }
    }
}
