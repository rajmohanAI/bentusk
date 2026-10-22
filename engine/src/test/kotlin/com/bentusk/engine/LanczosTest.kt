package com.bentusk.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LanczosTest {

    private class Image(val w: Int, val h: Int, val px: IntArray)

    private fun resize(src: Image, w: Int, h: Int): Image {
        val out = IntArray(w * h)
        Lanczos.resize(
            src.w, src.h, w, h,
            readRow = { y, row -> System.arraycopy(src.px, y * src.w, row, 0, src.w) },
            writeRow = { y, row -> System.arraycopy(row, 0, out, y * w, w) },
        )
        return Image(w, h, out)
    }

    private fun image(w: Int, h: Int, f: (Int, Int) -> Int) = Image(w, h, IntArray(w * h) { f(it % w, it / w) })

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun lum(p: Int) = p and 0xFF

    @Test
    fun `same size is an exact copy`() {
        val src = image(37, 23) { x, y -> grey((x * 7 + y * 13) % 256) }
        assertArrayEquals(src.px, resize(src, 37, 23).px)
    }

    @Test
    fun `flat colour stays exactly flat at any size`() {
        val colour = 0xFF3A7BD5.toInt()
        val src = image(101, 67) { _, _ -> colour }
        for ((w, h) in listOf(50 to 33, 13 to 9, 1 to 1, 77 to 60)) {
            assertTrue(resize(src, w, h).px.all { it == colour })
        }
    }

    @Test
    fun `fine stripes are smoothed instead of aliasing`() {
        // One-pixel black and white stripes, shrunk by a factor that point sampling or a bilinear draw turns into
        // full-contrast bands (every output pixel black or white). The filter keeps them within a quarter of that.
        val src = image(300, 40) { x, _ -> grey(if (x % 2 == 0) 0 else 255) }
        val out = resize(src, 113, 15)
        for (p in out.px) assertTrue("got ${lum(p)}", abs(lum(p) - 128) <= 32)
    }

    @Test
    fun `smooth gradients keep their shape`() {
        val src = image(400, 10) { x, _ -> grey(x * 255 / 399) }
        val out = resize(src, 100, 5)
        for (x in 3 until 97) {
            val expected = ((x + 0.5) * 4 - 0.5) * 255 / 399
            // The source levels are rounded down to whole numbers, so allow for that and the output rounding.
            assertEquals(expected, lum(out.px[2 * 100 + x]).toDouble(), 1.5)
        }
    }

    @Test
    fun `transparent pixels do not darken opaque neighbours`() {
        val src = image(64, 64) { x, _ -> if (x < 32) 0x00000000 else 0xFFFFFFFF.toInt() }
        val out = resize(src, 16, 16)
        for (p in out.px) {
            if ((p ushr 24) > 0) assertEquals(0xFFFFFF, p and 0xFFFFFF)
        }
    }

    @Test
    fun `weights cover every output pixel and sum to one`() {
        for ((s, t) in listOf(1000 to 333, 333 to 1000, 7 to 1, 5 to 5)) {
            val axis = Lanczos.Axis(s, t)
            for (i in 0 until t) {
                val w = axis.weights[i]!!
                assertEquals(1.0, w.sum().toDouble(), 1e-4)
                assertTrue(axis.start[i] >= 0 && axis.start[i] + w.size <= s)
            }
        }
    }

    @Test
    fun `shrinks only when it is worth it and never below the canvas`() {
        val raw = PxSize(4000, 3000)
        assertEquals(PxSize(1440, 1080), Lanczos.targetSize(raw, PxSize(2000, 1500), 0.36))
        // Already within 5% of the drawn size.
        assertNull(Lanczos.targetSize(raw, PxSize(2000, 1500), 0.49))
        // Enlarged: nothing to shrink.
        assertNull(Lanczos.targetSize(raw, raw, 2.0))
    }
}
