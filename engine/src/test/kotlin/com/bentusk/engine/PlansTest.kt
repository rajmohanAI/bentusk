package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class PlansTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val start = 1_760_000_000_000L

    @Test
    fun `trial lasts ninety days with everything unlocked`() {
        val first = Plans.resolve(ownsPro = false, trialStartMillis = start, nowMillis = start)
        assertEquals(Entitlement.Trial(90), first)
        assertTrue(ProFeature.entries.all { first.allows(it) })
        assertFalse(first.showsAds)

        assertEquals(Entitlement.Trial(1), Plans.resolve(false, start, start + 89 * day))
        assertEquals(Entitlement.Free, Plans.resolve(false, start, start + 90 * day))
    }

    @Test
    fun `free tier shows ads and locks pro features`() {
        val free = Plans.resolve(false, start, start + 200 * day)
        assertTrue(free.showsAds)
        assertTrue(ProFeature.entries.none { free.allows(it) })
    }

    @Test
    fun `winding the clock back does not extend the trial`() {
        val e = Plans.resolve(false, start, nowMillis = start + 5 * day, lastSeenMillis = start + 95 * day)
        assertEquals(Entitlement.Free, e)
    }

    @Test
    fun `pro always wins`() {
        assertEquals(Entitlement.Pro, Plans.resolve(true, start, start + 500 * day))
        assertFalse(Entitlement.Pro.showsAds)
    }
}
