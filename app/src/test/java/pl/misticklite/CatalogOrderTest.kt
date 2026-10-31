package pl.misticklite

import org.junit.Assert.*
import org.junit.Test

class CatalogOrderTest {
    private fun app(pkg: String, label: String = pkg, time: Long = 1L) = AppKey(pkg, label, time)

    @Test fun `first run uses requested streaming order then remaining apps`() {
        val apps = listOf(app("extra"), app("com.apple.atve.androidtv.appletv"),
            app("pl.videostar"), app("pl.tvp.vod.tv"), app("com.skyshowtime.skyshowtime"),
            app("com.google.android.youtube.tv"), app("com.wbd.stream"),
            app("com.amazon.amazonvideo.livingroom"), app("com.netflix.ninja"))
        val result = CatalogOrder.reconcile(HomeState(), apps)
        assertEquals(listOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.wbd.stream",
            "com.google.android.youtube.tv", "com.skyshowtime.skyshowtime", "pl.tvp.vod.tv", "pl.videostar",
            "com.apple.atve.androidtv.appletv", "extra"), result.order)
    }

    @Test fun `newly installed preferred app is appended instead of changing custom order`() {
        val state = HomeState(listOf("youtube", "prime"))
        assertEquals(listOf("youtube", "prime", "com.netflix.ninja"),
            CatalogOrder.reconcile(state, listOf(app("prime"), app("youtube"), app("com.netflix.ninja"))).order)
    }

    @Test fun `no eight app limit and no duplicate packages`() {
        val apps = (1..150).map { app("app.$it", time = it.toLong()) }
        val state = CatalogOrder.reconcile(HomeState(), apps + apps)
        assertEquals(150, state.order!!.size)
        assertEquals(150, CatalogOrder.visible(state, apps.map { it.packageName }.toSet()).size)
    }

    @Test fun `hidden and removed packages keep position on reinstall`() {
        val original = HomeState(listOf("a", "b", "c"), setOf("b"))
        val removed = CatalogOrder.reconcile(original, listOf(app("a"), app("c")))
        assertEquals(listOf("a", "c"), CatalogOrder.visible(removed, setOf("a", "c")))
        val restored = CatalogOrder.reconcile(removed, listOf(app("c"), app("b"), app("a")))
        assertEquals(original, restored)
        assertEquals(listOf("a", "b", "c"), CatalogOrder.visible(restored.copy(hidden = emptySet()), setOf("a", "b", "c")))
    }

    @Test fun `moves skip hidden and missing slots and stop at edges`() {
        val original = HomeState(listOf("a", "hidden", "missing", "b", "c"), setOf("hidden"))
        val installed = setOf("a", "hidden", "b", "c")
        val moved = CatalogOrder.move(original, installed, "b", -1)
        assertEquals(listOf("b", "a", "c"), CatalogOrder.visible(moved, installed))
        assertEquals(moved, CatalogOrder.move(moved, installed, "b", -1))
        assertEquals(moved, CatalogOrder.move(moved, installed, "c", 1))
        assertEquals(original, CatalogOrder.move(moved, installed, "b", 1))
    }

    @Test fun `regional label fallback and missing streaming apps work`() {
        val apps = listOf(app("regional.apple", "Apple TV"), app("regional.tvp", "TVP VOD"), app("regional.max", "HBO Max"))
        assertEquals(listOf("regional.max", "regional.tvp", "regional.apple"), CatalogOrder.reconcile(HomeState(), apps).order)
    }

    @Test fun `simultaneous additions use install time with deterministic tie break`() {
        val state = HomeState(listOf("old"))
        val apps = listOf(app("new2", "Z", 3), app("old"), app("new1", "A", 2), app("new3", "B", 2))
        assertEquals(listOf("old", "new1", "new3", "new2"), CatalogOrder.reconcile(state, apps).order)
    }
}
