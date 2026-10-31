package pl.misticklite

import android.content.Intent
import java.lang.ref.WeakReference

/** Same-process debug control. Weak references never keep the service or desktop alive. */
internal object HomeRedirectControl {
    const val ACTION = "pl.misticklite.debug.HOME_REDIRECT"
    private var service = WeakReference<HomeKeyDiagnosticService>(null)
    private var launcher = WeakReference<MainActivity>(null)
    private var resumed = false

    fun attach(value: HomeKeyDiagnosticService) { service = WeakReference(value) }
    fun detach(value: HomeKeyDiagnosticService) { if (service.get() === value) service.clear() }
    fun created(value: MainActivity) { launcher = WeakReference(value) }
    fun resumed(value: MainActivity) { launcher = WeakReference(value); resumed = true }
    fun paused(value: MainActivity) { if (launcher.get() === value) resumed = false }
    fun destroyed(value: MainActivity) {
        if (launcher.get() === value) { launcher.clear(); resumed = false }
    }
    fun launcherForeground(): Boolean = launcherState() == "FOREGROUND"
    private fun launcherState(): String {
        val activity = launcher.get() ?: return "CLOSED"
        return when {
            activity.isDestroyed -> "CLOSED"
            activity.isFinishing -> "FINISHING"
            resumed -> "FOREGROUND"
            else -> "BACKGROUND"
        }
    }
    fun status(): String {
        val live = service.get()
        return "mode=${if (live?.redirectEnabled == true) "ON" else "OFF"} " +
            "service=${if (live != null) "CONNECTED" else "DISCONNECTED"} " +
            "pendingPairs=${live?.pendingConsumedHomePairs ?: 0} launcher=${launcherState()} persistent=false"
    }

    /** Called only by the debug receiver on the same main thread as the key callbacks. */
    fun command(command: String): Pair<Boolean, String> {
        if (!BuildConfig.DEBUG) return false to "error=debug_only ${status()}"
        val live = service.get()
        when (command) {
            "status" -> Unit
            "enable" -> {
                if (live == null) return false to "error=service_not_connected ${status()}"
                live.setHomeRedirectEnabled(true)
            }
            "disable" -> live?.setHomeRedirectEnabled(false)
            "disable-service" -> {
                live?.setHomeRedirectEnabled(false)
                if ((live?.pendingConsumedHomePairs ?: 0) > 0) {
                    return false to "error=release_home_before_disable_service ${status()}"
                }
                live?.disableSelf() // Platform removes only this service; no global settings rewrite.
                return true to "disableServiceRequested=${live != null} ${status()}"
            }
            "close-launcher" -> launcher.get()?.finish()
            else -> return false to "error=unknown_command ${status()}"
        }
        return true to status()
    }

    fun launchIntent(service: HomeKeyDiagnosticService): Intent =
        Intent(service, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
