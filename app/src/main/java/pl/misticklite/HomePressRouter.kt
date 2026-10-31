package pl.misticklite

/**
 * Only the current HOME gestures are retained, one per input device. No event objects/history.
 * A gesture's consume decision is latched at its initial DOWN, including across mode changes.
 */
internal class HomePressRouter(private val longPressMillis: Long, private val doubleTapMillis: Long) {
    enum class Decision(val consume: Boolean, val launch: Boolean) {
        PASS(false, false), CONSUME(true, false), LAUNCH(true, true)
    }
    private class Press(val downTime: Long, val startedAt: Long, val consumed: Boolean,
                        var launchAllowed: Boolean, var long: Boolean, val doubleTap: Boolean)
    private val presses = HashMap<Int, Press>()
    private var lastLaunchAt = -1L
    var enabled: Boolean = false
        private set
    val pendingConsumedPairs: Int get() = presses.values.count { it.consumed }
    val pendingPairs: Int get() = presses.size

    fun setEnabled(value: Boolean) {
        enabled = value
        // OFF cancels pending launches, but still drains a previously consumed pair.
        // Re-enabling before UP must not revive an already cancelled gesture.
        if (!value) presses.values.forEach { it.launchAllowed = false }
    }

    fun reset() {
        enabled = false
        presses.clear()
        lastLaunchAt = -1L
    }

    /** Android action integers: DOWN=0, UP=1. Unknown actions always pass through. */
    fun home(action: Int, repeat: Int, device: Int, downTime: Long, time: Long,
             cancelled: Boolean = false, longFlag: Boolean = false): Decision {
        when (action) {
            0 -> {
                val current = presses[device]
                if (current != null && (current.downTime == downTime || repeat > 0 || longFlag)) {
                    current.long = current.long || repeat > 0 || longFlag || time - current.startedAt >= longPressMillis
                    if (cancelled) current.launchAllowed = false
                    return if (current.consumed) Decision.CONSUME else Decision.PASS
                }
                // An orphan repeat has no observed initial DOWN: do not claim half a stream.
                if (repeat != 0 || longFlag) return Decision.PASS
                // A new DOWN replaces a malformed stream whose previous UP never arrived.
                val doubleTap = lastLaunchAt >= 0 && time - lastLaunchAt in 0..doubleTapMillis
                presses[device] = Press(downTime, time, enabled, enabled && !cancelled, false, doubleTap)
                return if (enabled) Decision.CONSUME else Decision.PASS
            }
            1 -> {
                val current = presses.remove(device) ?: return Decision.PASS
                if (!current.consumed) return Decision.PASS
                val duration = time - current.startedAt
                val overlappingLaunch = lastLaunchAt >= current.startedAt &&
                    lastLaunchAt >= 0 && time - lastLaunchAt in 0..doubleTapMillis
                if (enabled && current.launchAllowed && !cancelled && !longFlag &&
                    !current.long && repeat == 0 && duration in 0 until longPressMillis &&
                    !current.doubleTap && !overlappingLaunch) {
                    lastLaunchAt = time
                    return Decision.LAUNCH
                }
                return Decision.CONSUME
            }
            2 -> {
                val current = presses[device] ?: return Decision.PASS
                current.long = true
                return if (current.consumed) Decision.CONSUME else Decision.PASS
            }
            else -> return Decision.PASS
        }
    }
}
