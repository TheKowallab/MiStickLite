package pl.misticklite

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeKeyDiagnosticServiceTest {
    private lateinit var controller: ServiceController<HomeKeyDiagnosticService>
    private lateinit var service: HomeKeyDiagnosticService

    @Before fun setup() {
        controller = Robolectric.buildService(HomeKeyDiagnosticService::class.java).create()
        service = controller.get()
        ShadowLog.clear()
    }
    @After fun cleanup() { controller.destroy() }

    private fun logs() = ShadowLog.getLogsForTag("MiStickHomeDiag")

    @Test fun `HOME and ALL_APPS down up and repeats always pass through unchanged`() {
        val expected = listOf(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_ALL_APPS)
        for (code in expected) for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP, KeyEvent.ACTION_MULTIPLE)) {
            val event = KeyEvent(123456789L, 987654321L, action, code, 4,
                0, 987, 456, KeyEvent.FLAG_FROM_SYSTEM, 0x101)
            val before = event.toString()
            assertFalse(service.onKeyEvent(event))
            assertEquals(before, event.toString())
        }
        assertEquals(6, logs().size)
        assertTrue(logs().all { it.type == Log.INFO })
        val allowed = Regex("key=(HOME\\(3\\)|ALL_APPS\\(284\\)) action=ACTION_(DOWN|UP|MULTIPLE) repeat=4 source=0x101 flags=0x8")
        assertTrue(logs().all { allowed.matches(it.msg) })
        assertTrue(logs().none { it.msg.contains("123456789") || it.msg.contains("987654321") || it.msg.contains("device") || it.msg.contains("scanCode") })
    }

    @Test fun `all other keys are silent and pass through`() {
        for (code in 0..KeyEvent.getMaxKeyCode()) {
            if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_ALL_APPS) continue
            assertFalse(service.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)))
            assertFalse(service.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)))
        }
        assertTrue(logs().isEmpty())
    }

    @Test fun `connection requests only key filtering without window event subscription`() {
        service.onServiceConnected()
        // With no system connection Robolectric keeps pending configuration in mInfo.
        val config = AccessibilityService::class.java.getDeclaredField("mInfo").apply { isAccessible = true }
            .get(service) as AccessibilityServiceInfo
        assertEquals(AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS, config.flags)
        assertEquals(0, config.eventTypes)
        assertEquals(0L, config.notificationTimeout)
        assertEquals(1, logs().size)
        assertEquals("connected sdk=28 keyFilterRequested=true eventTypes=0 windowContent=false", logs().single().msg)
    }

    @Test fun `manifest metadata grants key filtering only and requires system bind permission`() {
        @Suppress("DEPRECATION")
        val resolved = service.packageManager.queryIntentServices(
            Intent(AccessibilityService.SERVICE_INTERFACE).setPackage(service.packageName), PackageManager.GET_META_DATA).single()
        val declared = resolved.serviceInfo
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", declared.permission)
        assertTrue(declared.exported)
        assertTrue(declared.enabled) // Discoverable; enabled in accessibility settings is separate.
        assertEquals(service.packageName, declared.processName)
        // The platform parser constructor is hidden from the SDK; use it only inside this test.
        val config = AccessibilityServiceInfo::class.java.getDeclaredConstructor(ResolveInfo::class.java, Context::class.java)
            .apply { isAccessible = true }.newInstance(resolved, service)
        assertEquals(AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS, config.capabilities)
        assertEquals(AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS, config.flags)
        assertEquals(0, config.eventTypes)
        @Suppress("DEPRECATION")
        assertFalse(config.canRetrieveWindowContent)
    }

    @Test fun `service never enables itself or changes HOME preferences`() {
        val resolver = service.contentResolver
        val enabledBefore = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val switchBefore = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        fun homeComponent(): ComponentName? = service.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.let {
                ComponentName(it.packageName, it.name)
            }
        val homeBefore = homeComponent()
        assertNull(enabledBefore)
        assertEquals(0, switchBefore)
        service.onServiceConnected()
        service.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALL_APPS))
        assertEquals(enabledBefore, Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        assertEquals(switchBefore, Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0))
        assertNull(shadowOf(service).nextStartedActivity)
        assertNull(shadowOf(service).nextStartedService)
        assertTrue(shadowOf(service).globalActionsPerformed.isEmpty())
        assertTrue(shadowOf(service).gesturesDispatched.isEmpty())
        assertEquals(homeBefore, homeComponent())
    }

    @Test fun `window events interrupts and unrelated text produce no logs or stored data`() {
        val prefsBefore = service.getSharedPreferences("home", Context.MODE_PRIVATE).all
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED).apply {
            text.add("PRIVATE_TEXT_NOT_TO_BE_READ")
            contentDescription = "PRIVATE_DESCRIPTION"
            packageName = "private.foreground.package"
        }
        service.onAccessibilityEvent(event)
        service.onAccessibilityEvent(null)
        service.onInterrupt()
        assertFalse(service.onKeyEvent(KeyEvent(1L, "PRIVATE_TYPED_TEXT", 12, 0)))
        assertTrue(logs().isEmpty())
        assertEquals(prefsBefore, service.getSharedPreferences("home", Context.MODE_PRIVATE).all)
        assertTrue(service.filesDir.list().orEmpty().isEmpty())
        assertTrue(service.databaseList().isEmpty())
        @Suppress("DEPRECATION")
        event.recycle()
    }

    @Test fun `repeated diagnostics retain no key state or event history in service fields`() {
        repeat(1000) { assertFalse(service.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALL_APPS))) }
        // ShadowLog is a test recorder; production uses only Android's transient logcat buffer.
        val router = HomeKeyDiagnosticService::class.java.getDeclaredField("home").apply { isAccessible = true }
            .get(service) as HomePressRouter
        assertEquals(0, router.pendingPairs)
        assertTrue(service.filesDir.list().orEmpty().isEmpty())
        assertTrue(service.databaseList().isEmpty())
    }
}
