package pl.misticklite

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.os.Handler
import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class HomeRedirectIntegrationTest {
    private lateinit var controller: ServiceController<HomeKeyDiagnosticService>
    private lateinit var service: HomeKeyDiagnosticService
    @Before fun setup() {
        controller = Robolectric.buildService(HomeKeyDiagnosticService::class.java).create()
        service = controller.get()
        service.onServiceConnected()
        ShadowLog.clear()
    }
    @After fun cleanup() { controller.destroy() }
    private fun key(action: Int, t: Long, down: Long = 1000, code: Int = KeyEvent.KEYCODE_HOME, repeat: Int = 0, flags: Int = 0): Boolean =
        service.onKeyEvent(KeyEvent(down, t, action, code, repeat, 0, 1, 0, flags, 0x101))
    private fun enable() { assertTrue(HomeRedirectControl.command("enable").first) }

    @Test fun `upgrade existing preferences cannot enable session redirect and no start on connect`() {
        service.getSharedPreferences("home", 0).edit().putBoolean("redirect", true).commit()
        service.getSharedPreferences("home_redirect_debug", 0).edit().putBoolean("enabled", true).commit()
        service.onServiceConnected()
        assertFalse(service.redirectEnabled)
        assertTrue(HomeRedirectControl.status().contains("mode=OFF"))
        assertFalse(key(0, 1000)); assertFalse(key(1, 1080))
        assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun `short HOME from service starts explicit launcher once with all three flags`() {
        enable()
        assertTrue(key(0, 1000)); assertNull(shadowOf(service).nextStartedActivity)
        assertTrue(key(1, 1080))
        val intent = shadowOf(service).nextStartedActivity!!
        assertEquals(ComponentName(service, MainActivity::class.java), intent.component)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertNull(shadowOf(service).nextStartedActivity)
        assertFalse(key(1, 1080)); assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun `ALL_APPS and other keys retain normal behavior even with redirect ON`() {
        enable()
        for (code in listOf(KeyEvent.KEYCODE_ALL_APPS, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_VOLUME_UP)) {
            assertFalse(key(0, 1000, code = code)); assertFalse(key(1, 1080, code = code))
        }
        assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun `disable mid gesture consumes closing UP cancels launch and then passes HOME`() {
        enable(); assertTrue(key(0, 1000))
        val result = HomeRedirectControl.command("disable")
        assertTrue(result.first); assertTrue(result.second.contains("mode=OFF")); assertTrue(result.second.contains("pendingPairs=1"))
        assertTrue(key(1, 1080)); assertNull(shadowOf(service).nextStartedActivity)
        assertFalse(key(0, 2000, 2000)); assertFalse(key(1, 2080, 2000))
    }
    @Test fun `enable mid passthrough gesture does not consume its UP`() {
        assertFalse(key(0, 1000)); enable()
        assertFalse(key(1, 1080)); assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun `long HOME repeated HOME and cancelled HOME do not open desktop`() {
        enable()
        assertTrue(key(0, 1000)); assertTrue(key(0, 1500, repeat = 1, flags = KeyEvent.FLAG_LONG_PRESS))
        assertTrue(key(1, 2000)); assertNull(shadowOf(service).nextStartedActivity)
        assertTrue(key(0, 3000, 3000)); assertTrue(key(1, 3800, 3000)); assertNull(shadowOf(service).nextStartedActivity)
        assertTrue(key(0, 4000, 4000)); assertTrue(key(1, 4080, 4000, flags = KeyEvent.FLAG_CANCELED))
        assertNull(shadowOf(service).nextStartedActivity)
        assertTrue(key(0, 5000, 5000)); assertTrue(key(1, 5080, 5000, flags = KeyEvent.FLAG_CANCELED_LONG_PRESS))
        assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun `double HOME opens once and does not form launch loop`() {
        enable(); key(0, 1000); key(1, 1080)
        assertNotNull(shadowOf(service).nextStartedActivity)
        key(0, 1200, 1200); key(1, 1280, 1200)
        assertNull(shadowOf(service).nextStartedActivity)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun `HOME on already resumed launcher never starts another activity`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            enable(); assertTrue(HomeRedirectControl.status().contains("launcher=FOREGROUND"))
            key(0, 1000); key(1, 1080)
            assertNull(shadowOf(service).nextStartedActivity)
        } finally { activity.pause().stop().destroy() }
    }
    @Test fun `activity launch failure switches OFF and next HOME returns to system`() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        service.packageManager.setComponentEnabledSetting(ComponentName(service, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        enable(); assertTrue(key(0, 1000)); assertTrue(key(1, 1080))
        assertFalse(service.redirectEnabled)
        assertFalse(key(0, 2000, 2000)); assertFalse(key(1, 2080, 2000))
        assertTrue(ShadowLog.getLogsForTag("MiStickHomeDiag").any { it.msg.contains("redirect=FAILED mode=OFF") })
    }
    @Test fun `interrupt reconnect and service destruction reset session OFF`() {
        enable(); service.onInterrupt(); assertFalse(service.redirectEnabled)
        enable(); service.onServiceConnected(); assertFalse(service.redirectEnabled)
        enable(); service.onUnbind(Intent(AccessibilityService.SERVICE_INTERFACE))
        assertTrue(HomeRedirectControl.status().contains("service=DISCONNECTED"))
        assertTrue(HomeRedirectControl.status().contains("mode=OFF"))
    }
    @Test fun `control does not enable accessibility and rejects unknown commands`() {
        assertFalse(HomeRedirectControl.command("unknown").first)
        val resolver = service.contentResolver
        assertEquals(0, android.provider.Settings.Secure.getInt(resolver, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 0))
        HomeRedirectControl.command("disable")
        assertNull(shadowOf(service).nextStartedService)
        assertTrue(shadowOf(service).globalActionsPerformed.isEmpty())
    }
    @Test fun `debug receiver protected by DUMP confirms mode and rejects wrong action`() {
        val component = ComponentName(service, HomeRedirectDebugReceiver::class.java)
        @Suppress("DEPRECATION")
        val info = service.packageManager.getReceiverInfo(component, 0)
        assertEquals("android.permission.DUMP", info.permission)
        assertTrue(info.exported)
        fun broadcast(action: String, command: String): Pair<Int, String?> {
            var result: Pair<Int, String?>? = null
            service.sendOrderedBroadcast(Intent(action).setComponent(component).putExtra("command", command), null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) { result = resultCode to resultData }
                }, Handler(Looper.getMainLooper()), 0, null, null)
            shadowOf(Looper.getMainLooper()).idle()
            return result!!
        }
        assertEquals(1, broadcast("wrong", "enable").first)
        assertFalse(service.redirectEnabled)
        val on = broadcast(HomeRedirectControl.ACTION, "enable")
        assertEquals(0, on.first); assertTrue(on.second!!.contains("mode=ON"))
        val off = broadcast(HomeRedirectControl.ACTION, "disable")
        assertEquals(0, off.first); assertTrue(off.second!!.contains("mode=OFF"))
    }
    @Test fun `enable rejected without connected service and close launcher leaves service connected`() {
        service.onUnbind(Intent(AccessibilityService.SERVICE_INTERFACE))
        assertFalse(HomeRedirectControl.command("enable").first)
        service.onServiceConnected()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            enable()
            val result = HomeRedirectControl.command("close-launcher")
            assertTrue(result.first)
            assertTrue(activity.get().isFinishing)
            assertTrue(result.second.contains("launcher=FINISHING"))
            assertTrue(result.second.contains("mode=ON service=CONNECTED"))
        } finally { activity.pause().stop().destroy() }
        assertTrue(HomeRedirectControl.status().contains("launcher=CLOSED"))
    }
    @Test fun `normal service disable waits until a consumed Home pair is drained`() {
        enable(); assertTrue(key(0, 1000))
        val held = HomeRedirectControl.command("disable-service")
        assertFalse(held.first)
        assertTrue(held.second.contains("error=release_home_before_disable_service"))
        assertFalse(service.redirectEnabled)
        assertTrue(key(1, 1080))
        assertNull(shadowOf(service).nextStartedActivity)
        val released = HomeRedirectControl.command("disable-service")
        assertTrue(released.first)
        assertTrue(released.second.contains("disableServiceRequested=true"))
    }
}
