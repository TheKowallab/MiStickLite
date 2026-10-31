package pl.misticklite

import java.util.Locale

data class AppKey(val packageName: String, val label: String, val installedAt: Long)
data class HomeState(val order: List<String>? = null, val hidden: Set<String> = emptySet())

/** Pure ordering policy, independent of Android and covered by JVM tests. */
object CatalogOrder {
    private val preferred = listOf(
        setOf("com.netflix.ninja", "com.netflix.mediaclient"),
        setOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
        setOf("com.wbd.stream", "com.hbo.hbonow", "com.hbo.broadband", "com.hbo.hbogo", "com.hbo.hbonow.tv", "com.hbo.hbogo.tv"),
        setOf("com.google.android.youtube.tv", "com.google.android.youtube"),
        setOf("com.skyshowtime.skyshowtime"),
        setOf("pl.tvp.vod.tv", "pl.tvp.vod"),
        setOf("pl.videostar", "pl.wp.pilot"),
        setOf("com.apple.atve.androidtv.appletv")
    )

    fun rank(app: AppKey): Int {
        val packageRank = preferred.indexOfFirst { app.packageName in it }
        if (packageRank >= 0) return packageRank
        // Labels cover regional package variants without hardcoding a launch target.
        val name = app.label.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        return when {
            name == "netflix" -> 0
            name in setOf("primevideo", "amazonprimevideo") -> 1
            name in setOf("max", "hbomax", "hbo", "hbogo") -> 2
            name == "youtube" -> 3
            name == "skyshowtime" -> 4
            name == "tvpvod" -> 5
            name in setOf("pilotwp", "wppilot", "pilot") -> 6
            name == "appletv" -> 7
            else -> Int.MAX_VALUE
        }
    }

    fun reconcile(state: HomeState, installed: List<AppKey>): HomeState {
        val distinct = installed.distinctBy { it.packageName }
        val freshOrder = if (state.order == null) {
            distinct.sortedWith(compareBy<AppKey> { rank(it) }
                .thenBy { it.installedAt }.thenBy { it.label }.thenBy { it.packageName })
        } else {
            val known = state.order.toHashSet()
            distinct.filter { it.packageName !in known }
                .sortedWith(compareBy<AppKey> { it.installedAt }.thenBy { it.label }.thenBy { it.packageName })
        }
        // Keep unavailable packages as tombstones: a reinstall restores position and visibility.
        return state.copy(order = (state.order.orEmpty() + freshOrder.map { it.packageName }).distinct())
    }

    fun visible(state: HomeState, installed: Set<String>): List<String> =
        state.order.orEmpty().filter { it in installed && it !in state.hidden }

    fun move(state: HomeState, installed: Set<String>, packageName: String, delta: Int): HomeState {
        val visible = visible(state, installed)
        val from = visible.indexOf(packageName)
        val to = from + delta
        if (from < 0 || to !in visible.indices) return state
        val order = state.order.orEmpty().toMutableList()
        val a = order.indexOf(packageName)
        val b = order.indexOf(visible[to])
        order[a] = visible[to]
        order[b] = packageName
        return state.copy(order = order)
    }
}
