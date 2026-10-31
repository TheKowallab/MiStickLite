package pl.misticklite

import org.junit.Assert.*
import org.junit.Test
import pl.misticklite.HomePressRouter.Decision.*

class HomePressRouterTest {
    private fun router(enabled: Boolean = true) = HomePressRouter(500, 300).apply { setEnabled(enabled) }

    @Test fun `default and reset are OFF`() {
        val r = HomePressRouter(500, 300)
        assertFalse(r.enabled)
        assertEquals(PASS, r.home(0, 0, 1, 1000, 1000))
        assertEquals(PASS, r.home(1, 0, 1, 1000, 1080))
        r.setEnabled(true); r.home(0, 0, 1, 2000, 2000); r.reset()
        assertFalse(r.enabled); assertEquals(0, r.pendingPairs)
        assertEquals(PASS, r.home(1, 0, 1, 2000, 2080))
    }
    @Test fun `short pair consumes both and launches once only at UP`() {
        val r = router()
        assertEquals(CONSUME, r.home(0, 0, 1, 1000, 1000))
        assertEquals(LAUNCH, r.home(1, 0, 1, 1000, 1080))
        assertEquals(PASS, r.home(1, 0, 1, 1000, 1080))
        assertEquals(0, r.pendingPairs)
    }
    @Test fun `orphan UP repeat and multiple never claim half a pair`() {
        val r = router()
        assertEquals(PASS, r.home(1, 0, 1, 1000, 1080))
        assertEquals(PASS, r.home(0, 1, 1, 1000, 1500))
        assertEquals(PASS, r.home(2, 1, 1, 1000, 1500))
        assertEquals(0, r.pendingPairs)
    }
    @Test fun `enabling between DOWN and UP passes both through`() {
        val r = router(false)
        assertEquals(PASS, r.home(0, 0, 1, 1000, 1000))
        r.setEnabled(true)
        assertEquals(PASS, r.home(0, 1, 1, 1000, 1400))
        assertEquals(PASS, r.home(1, 0, 1, 1000, 1500))
    }
    @Test fun `disabling between DOWN and UP drains the consumed pair without launch`() {
        val r = router()
        assertEquals(CONSUME, r.home(0, 0, 1, 1000, 1000))
        r.setEnabled(false)
        assertEquals(1, r.pendingConsumedPairs)
        assertEquals(CONSUME, r.home(1, 0, 1, 1000, 1080))
        assertEquals(0, r.pendingConsumedPairs)
        assertEquals(PASS, r.home(0, 0, 1, 2000, 2000))
        assertEquals(PASS, r.home(1, 0, 1, 2000, 2080))
    }
    @Test fun `OFF then ON before UP does not revive cancelled launch`() {
        val r = router(); r.home(0, 0, 1, 1000, 1000)
        r.setEnabled(false); r.setEnabled(true)
        assertEquals(CONSUME, r.home(1, 0, 1, 1000, 1080))
    }
    @Test fun `long duration without any repeat is consumed without launch`() {
        val r = router()
        assertEquals(CONSUME, r.home(0, 0, 1, 1000, 1000))
        assertEquals(CONSUME, r.home(1, 0, 1, 1000, 1500))
    }
    @Test fun `repeats including vendor changed downTime never launch`() {
        val r = router(); r.home(0, 0, 1, 1000, 1000)
        repeat(10) { assertEquals(CONSUME, r.home(0, it + 1, 1, 1234, 1010L + it)) }
        assertEquals(CONSUME, r.home(1, 0, 1, 1000, 1100))
    }
    @Test fun `long flag cancellation and MULTIPLE suppress short redirect`() {
        for (variant in 0..2) {
            val r = router(); r.home(0, 0, 1, 1000, 1000)
            if (variant == 0) r.home(0, 0, 1, 1000, 1010, longFlag = true)
            if (variant == 1) r.home(2, 1, 1, 1000, 1010)
            assertEquals(CONSUME, r.home(1, 0, 1, 1000, 1080, cancelled = variant == 2))
        }
    }
    @Test fun `duplicate initial DOWN does not reset duration or launch twice`() {
        val r = router()
        r.home(0, 0, 1, 1000, 1000)
        assertEquals(CONSUME, r.home(0, 0, 1, 1000, 1300))
        assertEquals(CONSUME, r.home(1, 0, 1, 1000, 1510))
    }
    @Test fun `double tap launches first pair only and later separate tap launches`() {
        val r = router()
        r.home(0, 0, 1, 1000, 1000); assertEquals(LAUNCH, r.home(1, 0, 1, 1000, 1080))
        assertEquals(CONSUME, r.home(0, 0, 1, 1300, 1300))
        assertEquals(CONSUME, r.home(1, 0, 1, 1300, 1400))
        r.home(0, 0, 1, 2000, 2000); assertEquals(LAUNCH, r.home(1, 0, 1, 2000, 2080))
    }
    @Test fun `simultaneous devices keep independent pair consumption and coalesce launches`() {
        val r = router()
        assertEquals(CONSUME, r.home(0, 0, 1, 1000, 1000))
        assertEquals(CONSUME, r.home(0, 0, 2, 1010, 1010))
        assertEquals(LAUNCH, r.home(1, 0, 2, 1010, 1080))
        assertEquals(CONSUME, r.home(1, 0, 1, 1000, 1090))
        assertEquals(0, r.pendingPairs)
    }
    @Test fun `unknown actions and backwards timestamps cannot launch`() {
        val r = router()
        assertEquals(PASS, r.home(99, 0, 1, 1000, 1000))
        r.home(0, 0, 1, 1000, 1000)
        assertEquals(CONSUME, r.home(1, 0, 1, 1000, 900))
    }
    @Test fun `new initial DOWN resynchronizes malformed stream with missing UP`() {
        val r = router(); r.home(0, 0, 1, 1000, 1000)
        assertEquals(CONSUME, r.home(0, 0, 1, 2000, 2000))
        assertEquals(LAUNCH, r.home(1, 0, 1, 2000, 2080))
        assertEquals(0, r.pendingPairs)
    }
    @Test fun `completed gesture state does not grow with key history`() {
        val r = router()
        repeat(1000) {
            val t = 1000L + it * 1000
            r.home(0, 0, it % 2, t, t)
            r.home(1, 0, it % 2, t, t + 50)
        }
        assertEquals(0, r.pendingPairs)
    }
}
