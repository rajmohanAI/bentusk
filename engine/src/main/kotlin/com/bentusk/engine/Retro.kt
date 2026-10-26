package com.bentusk.engine

import kotlinx.serialization.Serializable

/** A film look for old-photo edits. Everything is drawn on the phone; nothing is generated or uploaded. */
@Serializable
enum class RetroStyle { Seventies, SlideFilm, Instant, Disposable, Sepia, Noir, Faded, Cross, HomeMovie }

/**
 * A film look on one photo. [strength] blends from the original (0) to the full look (1).
 * [stamp] is the orange date printed in the corner, like a 90s point-and-shoot, or null for none.
 */
@Serializable
data class Look(val style: RetroStyle, val strength: Double = 1.0, val stamp: String? = null)

/**
 * What the renderer draws for a [Look]: a colour matrix for the photo, then grain, a vignette and an optional
 * light leak on top. [matrix] is Android's 4x5 ColorMatrix layout, row-major, offsets in 0..255.
 */
class RetroEffects(
    val matrix: DoubleArray,
    /** 0..1, how strongly the grain overlay shows. */
    val grain: Double,
    /** 0..1, how dark the corners get. */
    val vignette: Double,
    /** ARGB of a warm leak glowing in from one corner, alpha already scaled by strength; null for none. */
    val leak: Int?,
)

object Retro {

    const val MAX_FADE = 0.25

    private class Recipe(
        val saturation: Double = 1.0,
        val contrast: Double = 1.0,
        val gains: Triple<Double, Double, Double> = Triple(1.0, 1.0, 1.0),
        val offsets: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
        val fade: Double = 0.0,
        val grain: Double = 0.0,
        val vignette: Double = 0.0,
        val leak: Int? = null,
    )

    private fun recipe(style: RetroStyle): Recipe = when (style) {
        RetroStyle.Seventies -> Recipe(
            saturation = 0.85, contrast = 0.9, gains = Triple(1.10, 1.0, 0.80), offsets = Triple(8.0, 2.0, -4.0),
            fade = 0.10, grain = 0.35, vignette = 0.45, leak = 0xFFFF7A1A.toInt(),
        )
        RetroStyle.SlideFilm -> Recipe(saturation = 1.25, contrast = 1.12, gains = Triple(1.06, 1.0, 0.92), grain = 0.2, vignette = 0.3)
        RetroStyle.Instant -> Recipe(
            saturation = 0.8, contrast = 0.85, gains = Triple(1.02, 1.0, 0.94), offsets = Triple(0.0, 4.0, 10.0),
            fade = 0.14, grain = 0.2, vignette = 0.35,
        )
        RetroStyle.Disposable -> Recipe(
            saturation = 1.15, contrast = 1.1, gains = Triple(1.0, 1.05, 0.92), offsets = Triple(0.0, 6.0, -6.0),
            grain = 0.55, vignette = 0.4,
        )
        RetroStyle.Sepia -> Recipe(
            saturation = 0.0, contrast = 0.95, gains = Triple(1.12, 0.94, 0.72), fade = 0.06, grain = 0.35, vignette = 0.55,
        )
        RetroStyle.Noir -> Recipe(saturation = 0.0, contrast = 1.3, grain = 0.5, vignette = 0.5)
        RetroStyle.Faded -> Recipe(saturation = 0.7, contrast = 0.8, gains = Triple(1.0, 1.0, 1.02), fade = 0.18, grain = 0.15, vignette = 0.2)
        RetroStyle.Cross -> Recipe(saturation = 1.2, contrast = 1.15, gains = Triple(0.95, 1.05, 0.85), offsets = Triple(0.0, 0.0, 25.0), grain = 0.25, vignette = 0.3)
        RetroStyle.HomeMovie -> Recipe(
            saturation = 0.9, contrast = 1.05, gains = Triple(1.08, 0.98, 0.85), fade = 0.08, grain = 0.65, vignette = 0.6,
            leak = 0xFFE8401C.toInt(),
        )
    }

    fun effects(look: Look): RetroEffects {
        val t = look.strength.coerceIn(0.0, 1.0)
        val r = recipe(look.style)
        val full = matrix(r)
        val id = Matrix5.identity()
        val blended = DoubleArray(20) { i -> id[i] + (full[i] - id[i]) * t }
        val leak = r.leak?.let { c -> ((((c ushr 24) * t * LEAK_ALPHA).toInt() and 0xFF) shl 24) or (c and 0xFFFFFF) }
        return RetroEffects(blended, r.grain * t, r.vignette * t, leak)
    }

    /** The 4x5 colour matrix for a recipe, as saturation, then contrast, then tint, then fade. */
    private fun matrix(r: Recipe): DoubleArray {
        var m = Matrix5.saturation(r.saturation)
        m = Matrix5.contrast(r.contrast) * m
        m = Matrix5.scaleOffset(r.gains, r.offsets) * m
        m = Matrix5.fade(r.fade.coerceIn(0.0, MAX_FADE)) * m
        return m.toColorMatrix()
    }

    /** Applies a 4x5 colour matrix to one opaque colour, clamped like Android does. For tests and swatches. */
    fun apply(matrix: DoubleArray, argb: Int): Int {
        val r = (argb shr 16 and 0xFF).toDouble()
        val g = (argb shr 8 and 0xFF).toDouble()
        val b = (argb and 0xFF).toDouble()
        fun row(i: Int) = (matrix[i * 5] * r + matrix[i * 5 + 1] * g + matrix[i * 5 + 2] * b + matrix[i * 5 + 3] * 255 + matrix[i * 5 + 4])
            .toInt().coerceIn(0, 255)
        return (argb and 0xFF000000.toInt()) or (row(0) shl 16) or (row(1) shl 8) or row(2)
    }

    /** A date stamp in the 'YY MM DD style of 90s compact cameras. */
    fun stamp(year: Int, month: Int, day: Int): String = "'%02d %02d %02d".format(year % 100, month, day)

    /**
     * A square tile of film grain, row-major, each value a grey level around 128 (128 leaves a pixel unchanged
     * in an overlay blend). Same seed, same tile, so preview and export match.
     */
    fun grainTile(size: Int = GRAIN_TILE, seed: Long = 0x5EED): IntArray {
        var x = seed or 1L
        fun next(): Int {
            x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
            return ((x ushr 33) and 0xFF).toInt()
        }
        // The mean of three uniforms is close to a bell curve, so most grains are faint and a few stand out.
        return IntArray(size * size) { (next() + next() + next()) / 3 }
    }

    const val GRAIN_TILE = 256
    private const val LEAK_ALPHA = 0.55
}

/** A 5x5 colour matrix (the 4x5 one plus an identity row), so steps compose by multiplication. */
private class Matrix5(val v: DoubleArray) {
    operator fun times(o: Matrix5): Matrix5 = Matrix5(DoubleArray(25) { i ->
        val row = i / 5
        val col = i % 5
        (0 until 5).sumOf { k -> v[row * 5 + k] * o.v[k * 5 + col] }
    })

    fun toColorMatrix(): DoubleArray = v.copyOf(20)

    companion object {
        private const val LR = 0.2126
        private const val LG = 0.7152
        private const val LB = 0.0722

        fun identity(): DoubleArray = DoubleArray(20) { if (it % 6 == 0) 1.0 else 0.0 }

        private fun of(vararg rows: DoubleArray) = Matrix5(rows.flatMap { it.toList() }.toDoubleArray() + doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0))

        fun saturation(s: Double): Matrix5 {
            val ir = (1 - s) * LR
            val ig = (1 - s) * LG
            val ib = (1 - s) * LB
            return of(
                doubleArrayOf(ir + s, ig, ib, 0.0, 0.0),
                doubleArrayOf(ir, ig + s, ib, 0.0, 0.0),
                doubleArrayOf(ir, ig, ib + s, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0),
            )
        }

        fun contrast(c: Double): Matrix5 = scaleOffset(Triple(c, c, c), Triple(128 * (1 - c), 128 * (1 - c), 128 * (1 - c)))

        fun fade(f: Double): Matrix5 = scaleOffset(Triple(1 - f, 1 - f, 1 - f), Triple(255 * f, 255 * f, 255 * f))

        fun scaleOffset(gain: Triple<Double, Double, Double>, offset: Triple<Double, Double, Double>): Matrix5 = of(
            doubleArrayOf(gain.first, 0.0, 0.0, 0.0, offset.first),
            doubleArrayOf(0.0, gain.second, 0.0, 0.0, offset.second),
            doubleArrayOf(0.0, 0.0, gain.third, 0.0, offset.third),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0),
        )
    }
}
