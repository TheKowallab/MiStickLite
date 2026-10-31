package pl.misticklite

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent

/**
 * Diagnostic by default; short-HOME redirect is a debug-only, explicit session opt-in.
 * No window access, polling, network, event history or persistent mode setting.
 */
class HomeKeyDiagnosticService : AccessibilityService() {
    private val home = HomePressRouter(ViewConfiguration.getLongPressTimeout().toLong(),
        ViewConfiguration.getDoubleTapTimeout().toLong())
    internal val redirectEnabled: Boolean get() = home.enabled
    internal val pendingConsumedHomePairs: Int get() = home.pendingConsumedPairs
    internal fun setHomeRedirectEnabled(enabled: Boolean) { home.setEnabled(BuildConfig.DEBUG && enabled) }

    public override fun onServiceConnected() {
        home.reset() // OFF on every reconnect/update/restart, including 1.1 -> 1.2.
        HomeRedirectControl.attach(this)
        // Keep only the key-filter request; subscribe to no accessibility/window events.
        // The immutable key-filter capability is declared in the XML metadata.
        val config = serviceInfo ?: AccessibilityServiceInfo()
        config.eventTypes = 0
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        config.notificationTimeout = 0
        config.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = config
        Log.i(TAG, "connected sdk=${Build.VERSION.SDK_INT} keyFilterRequested=true eventTypes=0 windowContent=false")
    }

    public override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_HOME -> "HOME(3)"
            KeyEvent.KEYCODE_ALL_APPS -> "ALL_APPS(284)"
            else -> return false
        }
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> "ACTION_DOWN"
            KeyEvent.ACTION_UP -> "ACTION_UP"
            KeyEvent.ACTION_MULTIPLE -> "ACTION_MULTIPLE"
            else -> event.action.toString()
        }
        // Do not log text, timestamps, device identities, packages or window contents.
        // Logcat is the OS's transient buffer; there is no app-owned history or counter.
        Log.i(TAG, "key=$key action=$action repeat=${event.repeatCount} source=0x${event.source.toString(16)} flags=0x${event.flags.toString(16)}")
        if (!BuildConfig.DEBUG || event.keyCode != KeyEvent.KEYCODE_HOME) return false
        val decision = home.home(event.action, event.repeatCount, event.deviceId,
            event.downTime, event.eventTime,
            event.isCanceled || (event.flags and KeyEvent.FLAG_CANCELED_LONG_PRESS) != 0, event.isLongPress)
        if (decision.launch && !HomeRedirectControl.launcherForeground()) openLauncher()
        return decision.consume
    }

    private fun openLauncher() {
        try {
            startActivity(HomeRedirectControl.launchIntent(this))
            Log.i(TAG, "redirect=START_REQUESTED")
        } catch (error: RuntimeException) { launchFailed(error) }
    }

    private fun launchFailed(error: RuntimeException) {
        home.setEnabled(false) // The consumed pair is complete; next press returns to stock HOME.
        Log.w(TAG, "redirect=FAILED mode=OFF error=${error.javaClass.simpleName}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { home.setEnabled(false) }
    override fun onUnbind(intent: Intent?): Boolean {
        home.reset()
        HomeRedirectControl.detach(this)
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        home.reset()
        HomeRedirectControl.detach(this)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MiStickHomeDiag"
    }
}
