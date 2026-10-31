package pl.misticklite

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.HorizontalScrollView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
class HomeBehaviorTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity

    @Before fun setup() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup().windowFocusChanged(true)
        activity = controller.get()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        // UI fixtures are deliberately not installed in the fake PackageManager.
        // Disable only the initial asynchronous real scan so it cannot replace them.
        field<android.os.Handler>("main").removeCallbacksAndMessages(null)
        put("generation", field<Int>("generation") + 1)
        flushPreferences()
        shadowOf(Looper.getMainLooper()).idle()
    }
    @After fun cleanup() { controller.pause().stop().destroy() }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(name: String): T = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity) as T
    private fun put(name: String, value: Any) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    private fun render() {
        MainActivity::class.java.getDeclaredMethod("render", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(activity, true)
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 960, 540)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun fixtures(count: Int = 12) {
        val apps = (1..count).map {
            val pkg = "fixture.app$it"
            val info = ActivityInfo().apply {
                packageName = pkg; name = "$pkg.Main"
                applicationInfo = ApplicationInfo().apply { packageName = pkg; enabled = true }
                enabled = true; exported = true
            }
            val titles = listOf("Netflix", "Prime Video", "HBO / Max", "YouTube", "SkyShowtime", "TVP VOD", "Pilot WP", "Apple TV")
            TvApp(AppKey(pkg, titles.getOrNull(it - 1) ?: "Aplikacja $it", it.toLong()), ComponentName(pkg, info.name), info)
        }
        put("installed", apps)
        put("state", HomeState(apps.map { it.key.packageName }))
        render()
    }
    private fun tiles(): LinkedHashMap<String, AppTileView> = field("tiles")
    private fun key(code: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun choose(index: Int) {
        val popup: AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
        popup.listView.performItemClick(null, index, index.toLong())
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun flushPreferences() { field<ExecutorService>("io").submit {}.get(5, TimeUnit.SECONDS) }

    @Test fun `dots short press has exactly the requested three entries`() {
        field<DotsView>("dots").performClick()
        val popup = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(listOf("Konto", "Ustawienia", "Sklep z aplikacjami"),
            (0 until popup.listView.adapter.count).map { popup.listView.adapter.getItem(it).toString() })
    }

    @Test fun `DPAD reaches twelfth app scrolls and returns from dots`() {
        fixtures()
        repeat(11) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertTrue(tiles().getValue("fixture.app12").isFocused)
        assertTrue(field<HorizontalScrollView>("scroller").scrollX > 0)
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue(field<DotsView>("dots").isFocused)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(tiles().getValue("fixture.app12").isFocused)
    }

    @Test fun `moving commits to disk and back cancels next move`() {
        fixtures(3)
        tiles().getValue("fixture.app1").performLongClick()
        choose(0)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        flushPreferences()
        val expected = listOf("fixture.app2", "fixture.app1", "fixture.app3")
        assertEquals(expected, OrderStore(activity).load().order)
        tiles().getValue("fixture.app1").performLongClick()
        choose(0)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_BACK)
        assertEquals(expected, field<HomeState>("state").order)
        assertEquals(expected, tiles().keys.toList())
    }

    @Test fun `hiding every app keeps dots reachable and long press restores an app`() {
        fixtures(2)
        repeat(2) { tiles().values.first().performLongClick(); choose(1) }
        assertTrue(tiles().isEmpty())
        assertTrue(field<DotsView>("dots").isFocused)
        field<DotsView>("dots").performLongClick()
        choose(0)
        assertEquals(1, tiles().size)
        flushPreferences()
        assertEquals(1, OrderStore(activity).load().hidden.size)
    }

    @Suppress("DEPRECATION")
    private fun register(pkg: String, name: String, category: String, enabled: Boolean = true) {
        val appInfo = ApplicationInfo().apply { packageName = pkg; this.enabled = enabled }
        shadowOf(activity.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg; applicationInfo = appInfo; firstInstallTime = 123L
        })
        val info = ActivityInfo().apply {
            packageName = pkg; this.name = name; applicationInfo = appInfo
            this.enabled = enabled; exported = true; nonLocalizedLabel = pkg
        }
        shadowOf(activity.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(category),
            ResolveInfo().apply { activityInfo = info })
    }

    @Test fun `discovery prefers TV deduplicates package and includes launcher fallback`() {
        register("fixture.tv", "fixture.tv.Tv", Intent.CATEGORY_LEANBACK_LAUNCHER)
        register("fixture.tv", "fixture.tv.Phone", Intent.CATEGORY_LAUNCHER)
        register("fixture.fallback", "fixture.fallback.Main", Intent.CATEGORY_LAUNCHER)
        register("fixture.disabled", "fixture.disabled.Main", Intent.CATEGORY_LEANBACK_LAUNCHER, false)
        val result = AppCatalog(activity).scan()
        assertEquals(2, result.size)
        assertEquals("fixture.tv.Tv", result.first { it.key.packageName == "fixture.tv" }.component.className)
        assertTrue(result.any { it.key.packageName == "fixture.fallback" })
    }

    @Test fun `missing activities fail safely without leaving launcher`() {
        assertFalse(SafeLaunch.first(activity, listOf(Intent().setComponent(ComponentName("absent", "absent.Main")))))
        assertNull(shadowOf(activity).nextStartedActivity)
        SystemScreens(activity).account()
        SystemScreens(activity).settings()
        SystemScreens(activity).store()
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `native layout fits TV and renders a preview with sample apps`() {
        fixtures(8)
        key(KeyEvent.KEYCODE_DPAD_UP)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        // Native graphics initializes a touch window; leave touch mode as a TV remote does.
        val first = tiles().values.first()
        assertTrue(first.requestFocusFromTouch())
        assertTrue(first.isFocused)
        assertTrue(content.width > 0 && content.height > 0)
        val bitmap = android.graphics.Bitmap.createBitmap(content.width, content.height, android.graphics.Bitmap.Config.ARGB_8888)
        content.draw(android.graphics.Canvas(bitmap))
        val output = java.io.File(System.getProperty("mistick.preview.dir"), "home-sample.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(Palette.background, bitmap.getPixel(1, 1))
        assertTrue(output.length() > 2000)
    }
}
