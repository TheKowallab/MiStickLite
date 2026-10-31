package pl.misticklite

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var store: OrderStore
    private lateinit var catalog: AppCatalog
    private lateinit var artwork: ArtworkLoader
    private lateinit var screens: SystemScreens
    private lateinit var dots: DotsView
    private lateinit var scroller: HorizontalScrollView
    private lateinit var row: LinearLayout
    private lateinit var status: TextView
    private lateinit var moveHint: TextView
    private val tiles = LinkedHashMap<String, AppTileView>()
    private var state = HomeState()
    private var installed = emptyList<TvApp>()
    private var focusedPackage: String? = null
    private var movingPackage: String? = null
    private var beforeMove: HomeState? = null
    private var dialog: AlertDialog? = null
    private var resumed = false
    private var destroyed = false
    private var generation = 0

    private val refresh = Runnable { scan() }
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let { artwork.invalidatePackage(it) }
            // Refresh on replacement completion, not the temporary uninstall during an update.
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            if (resumed) requestRefresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = OrderStore(this)
        state = store.load()
        focusedPackage = savedInstanceState?.getString("focused")
        catalog = AppCatalog(this)
        screens = SystemScreens(this)
        artwork = ArtworkLoader(applicationContext) { pkg -> tiles[pkg]?.invalidate() }
        buildHome()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packages, filter, Context.RECEIVER_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(packages, filter)
    }

    private fun buildHome() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Palette.background)
            isFocusable = false
        }
        val clock = TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            textSize = 76f
            setTextColor(Palette.cream)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
            isFocusable = false
        }
        root.addView(clock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(52); topMargin = dp(34)
        })
        dots = DotsView(this).apply {
            id = View.generateViewId()
            setOnClickListener { openMenu() }
            setOnLongClickListener { restoreHidden(); true }
            setOnKeyListener { _, code, event ->
                if (code in listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)) {
                    if (event.action == KeyEvent.ACTION_DOWN) focusRow()
                    true
                } else false
            }
        }
        root.addView(dots, FrameLayout.LayoutParams(dp(72), dp(56), Gravity.TOP or Gravity.END).apply {
            rightMargin = dp(47); topMargin = dp(38)
        })
        row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(47), dp(8), dp(47), dp(8))
            clipToPadding = false
        }
        scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isSmoothScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = false
            clipToPadding = false
            addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnScrollChangeListener { _, _, _, _, _ -> loadVisibleArtwork() }
        }
        root.addView(scroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(198), Gravity.CENTER_VERTICAL).apply {
            topMargin = dp(80)
        })
        status = TextView(this).apply {
            textSize = 18f
            setTextColor(Palette.muted)
            gravity = Gravity.CENTER
            setPadding(dp(52), 0, dp(52), 0)
            setText(R.string.loading)
        }
        root.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply { topMargin = dp(80) })
        moveHint = TextView(this).apply {
            textSize = 18f
            setTextColor(Palette.cream)
            gravity = Gravity.CENTER
            setText(R.string.move_hint)
            visibility = View.GONE
        }
        root.addView(moveHint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(42) })
        setContentView(root)
        dots.requestFocus()
        immersive()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        immersive()
        requestRefresh()
        main.post { loadVisibleArtwork() }
    }

    override fun onPause() {
        resumed = false
        generation++ // Ignore a scan completed after leaving the foreground.
        main.removeCallbacks(refresh)
        cancelMove()
        dialog?.dismiss()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        cancelMove()
        dialog?.dismiss()
        if (resumed) requestRefresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("focused", focusedPackage)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun requestRefresh() {
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, 150)
    }

    private fun scan() {
        val ticket = ++generation
        io.execute {
            val apps = try { catalog.scan() } catch (error: RuntimeException) {
                Log.w("MiStickLite", "Package scan failed", error)
                null
            }
            main.post {
                if (destroyed || !resumed || ticket != generation) return@post
                if (apps == null) {
                    if (installed.isEmpty()) status.setText(R.string.catalog_error)
                    return@post
                }
                cancelMove()
                dialog?.dismiss()
                installed = apps
                val updated = CatalogOrder.reconcile(state, apps.map { it.key })
                if (state != updated) { state = updated; persist() }
                render()
            }
        }
    }

    private fun render(forceRowFocus: Boolean = false) {
        val keepMenuFocus = dots.hasFocus() && tiles.isNotEmpty() && !forceRowFocus
        val previousIndex = tiles.keys.indexOf(focusedPackage).coerceAtLeast(0)
        val byPackage = installed.associateBy { it.key.packageName }
        tiles.clear()
        row.removeAllViews()
        for (pkg in CatalogOrder.visible(state, byPackage.keys)) {
            val app = byPackage.getValue(pkg)
            val tile = AppTileView(this, app, artwork).apply {
                moving = pkg == movingPackage
                nextFocusUpId = dots.id
                setOnClickListener {
                    if (movingPackage != null) finishMove()
                    else if (!catalog.launch(app)) { toast(R.string.app_unavailable); requestRefresh() }
                }
                setOnLongClickListener { if (movingPackage == null) appOptions(app); true }
                onFocusChangeListener = View.OnFocusChangeListener { view, focused ->
                    view.invalidate()
                    if (focused) {
                        focusedPackage = pkg
                        scroller.post { reveal(view) }
                    }
                }
                setOnKeyListener { _, code, event ->
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (event.action == KeyEvent.ACTION_DOWN && movingPackage == null) dots.requestFocus()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> true
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (event.action == KeyEvent.ACTION_DOWN && movingPackage == null) {
                                val keys = tiles.keys.toList()
                                val index = keys.indexOf(pkg) + if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                                keys.getOrNull(index)?.let { tiles[it]?.requestFocus() }
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
            tiles[pkg] = tile
            row.addView(tile, LinearLayout.LayoutParams(dp(242), dp(182)).apply { rightMargin = dp(13) })
        }
        status.visibility = if (tiles.isEmpty()) View.VISIBLE else View.GONE
        status.setText(R.string.empty)
        moveHint.visibility = if (movingPackage == null) View.GONE else View.VISIBLE
        if (tiles.isEmpty() || keepMenuFocus) dots.requestFocus()
        else {
            val target = focusedPackage?.takeIf { it in tiles } ?: tiles.keys.elementAt(previousIndex.coerceAtMost(tiles.size - 1))
            tiles[target]?.requestFocus()
        }
        scroller.post { loadVisibleArtwork() }
    }

    private fun reveal(view: View) {
        val margin = dp(47)
        val target = when {
            view.left - margin < scroller.scrollX -> view.left - margin
            view.right + margin > scroller.scrollX + scroller.width -> view.right + margin - scroller.width
            else -> scroller.scrollX
        }
        scroller.scrollTo(target.coerceAtLeast(0), 0)
        loadVisibleArtwork()
    }

    private fun loadVisibleArtwork() {
        if (!resumed) return
        val start = scroller.scrollX - dp(255)
        val end = scroller.scrollX + scroller.width + dp(255)
        tiles.values.filter { it.right >= start && it.left <= end }.forEach { artwork.request(it.app) }
    }

    private fun focusRow() {
        (tiles[focusedPackage] ?: tiles.values.firstOrNull())?.requestFocus()
    }

    private fun openMenu() {
        // Keep this list EXACTLY three entries. App editing lives on long press.
        showChoices(null, arrayOf(getString(R.string.account), getString(R.string.settings), getString(R.string.store))) {
            when (it) { 0 -> screens.account(); 1 -> screens.settings(); 2 -> screens.store() }
        }
    }

    private fun appOptions(app: TvApp) {
        showChoices(app.key.label, arrayOf(getString(R.string.move), getString(R.string.hide))) {
            when (it) {
                0 -> {
                    beforeMove = state
                    movingPackage = app.key.packageName
                    focusedPackage = movingPackage
                    render(true)
                }
                1 -> {
                    state = state.copy(hidden = state.hidden + app.key.packageName)
                    persist()
                    render(true)
                }
            }
        }
    }

    private fun restoreHidden() {
        val hidden = installed.filter { it.key.packageName in state.hidden }.sortedBy { it.key.label }
        if (hidden.isEmpty()) { toast(R.string.no_hidden); return }
        showChoices(getString(R.string.hidden_title), hidden.map { it.key.label }.toTypedArray()) {
            val pkg = hidden[it].key.packageName
            state = state.copy(hidden = state.hidden - pkg)
            focusedPackage = pkg
            persist()
            render(true)
        }
    }

    private fun showChoices(title: String?, labels: Array<String>, selected: (Int) -> Unit) {
        dialog?.dismiss()
        val popup = AlertDialog.Builder(this).apply {
            if (title != null) setTitle(title)
            setItems(labels) { _, index -> selected(index) }
        }.create()
        dialog = popup
        popup.setOnDismissListener { if (dialog === popup) dialog = null }
        popup.setOnShowListener {
            popup.window?.setBackgroundDrawable(GradientDrawable().apply {
                setColor(Palette.background); cornerRadius = dp(16).toFloat()
            })
            popup.listView.apply {
                setSelector(GradientDrawable().apply { setColor(Palette.tile); setStroke(dp(2), Palette.cream) })
                isFocusable = true
                requestFocus()
                setSelection(0)
            }
        }
        popup.show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            event.action == KeyEvent.ACTION_DOWN && event.keyCode in listOf(KeyEvent.KEYCODE_HOME, 284)) {
            Log.i("MiStickLite", "Received keyCode=${event.keyCode}; system may consume HOME/ALL_APPS before delivery")
        }
        val pkg = movingPackage
        if (pkg != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        state = CatalogOrder.move(state, installed.map { it.key.packageName }.toSet(), pkg,
                            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        render(true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) finishMove()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (event.action == KeyEvent.ACTION_UP) { cancelMove(); render(true) }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun finishMove() {
        movingPackage = null
        beforeMove = null
        persist()
        render(true)
    }

    private fun cancelMove() {
        if (movingPackage == null) return
        beforeMove?.let { state = it }
        movingPackage = null
        beforeMove = null
        moveHint.visibility = View.GONE
        tiles.values.forEach { it.moving = false }
    }

    private fun persist() {
        val snapshot = state
        io.execute { if (!store.save(snapshot)) main.post { if (!destroyed) toast(R.string.save_error) } }
    }

    @Deprecated("Platform callback is appropriate for the Android TV 9 target")
    override fun onBackPressed() {
        // A launcher stays on the desktop. Back from the row returns to its first tile.
        if (dialog?.isShowing == true) dialog?.dismiss()
        else if (movingPackage != null) { cancelMove(); render(true) }
        else if (tiles.isNotEmpty()) tiles.values.first().requestFocus()
        else dots.requestFocus()
    }

    @Suppress("DEPRECATION") // API-28 device still sends these memory pressure levels.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) artwork.trim()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (resumed) requestRefresh()
    }

    override fun onDestroy() {
        destroyed = true
        unregisterReceiver(packages)
        main.removeCallbacksAndMessages(null)
        artwork.close()
        io.shutdown() // Allow already queued preference commits to finish.
        super.onDestroy()
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
