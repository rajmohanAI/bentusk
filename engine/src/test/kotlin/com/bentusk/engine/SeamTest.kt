package com.bentusk.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.math.floor

/**
 * Renders a synthetic photo once as a whole canvas and once tile by tile, the way the app exports,
 * and checks every tile pixel matches the whole-canvas pixel. That is what makes carousel seams invisible.
 */
class SeamTest {

    private val source = PxSize(997, 613)
    private val photo = IntArray(source.width * source.height) { i ->
        val x = i % source.width
        val y = i / source.width
        (x * 31 + y * 17) xor (x * y)
    }

    @Test
    fun `carousel tiles are identical to the whole render`() {
        // Scaled-down canvas keeps the test fast; the math is resolution independent.
        val layout = CarouselLayout(4, PostRatio.Portrait)
        checkSeams(layout, PxSize(layout.canvas.width / 10, layout.canvas.height / 10), layout.tileSize.width / 10)
    }

    @Test
    fun `grid tiles are identical to the whole render`() {
        val layout = GridLayout(3, PostRatio.Tall)
        checkSeams(layout, PxSize(layout.canvas.width / 12, layout.canvas.height / 12), layout.tileSize.width / 12)
    }

    private fun checkSeams(layout: SliceLayout, canvas: PxSize, tileW: Int) {
        val tileH = canvas.height / layout.rows
        val framing = FramingMath.clamp(source, canvas, Framing(rotationDegrees = 11.5, zoom = 1.37, offset = Vec2(9.0, -4.0)))
        val transform = FramingMath.transform(source, canvas, framing)
        val whole = render(transform, canvas)

        for (tile in layout.tiles) {
            val rect = PxRect(tile.column * tileW, tile.row * tileH, (tile.column + 1) * tileW, (tile.row + 1) * tileH)
            val rendered = render(FramingMath.tileTransform(transform, rect), rect.size)
            val expected = IntArray(rect.width * rect.height) { i ->
                whole[(rect.top + i / rect.width) * canvas.width + rect.left + i % rect.width]
            }
            assertArrayEquals("tile ${tile.index}", expected, rendered)
        }
    }

    /** Nearest-neighbour inverse-mapping rasterizer sampling at pixel centres, like a GPU or Skia does. */
    private fun render(transform: Affine, size: PxSize): IntArray {
        val inverse = transform.inverse()
        return IntArray(size.width * size.height) { i ->
            val p = inverse.map(Vec2(i % size.width + 0.5, i / size.width + 0.5))
            val sx = floor(p.x).toInt()
            val sy = floor(p.y).toInt()
            if (sx in 0 until source.width && sy in 0 until source.height) photo[sy * source.width + sx] else -1
        }
    }
}
