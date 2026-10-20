package com.bentusk.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * How the user placed a photo on a canvas: rotation about the photo centre, zoom and pan.
 *
 * [zoom] is relative to the cover scale, so 1.0 means the rotated photo exactly covers the canvas.
 * Values below 1.0 shrink the photo inside the canvas and expose the background fill.
 * [offset] is the photo centre's displacement from the canvas centre, in canvas pixels.
 */
data class Framing(
    val rotationDegrees: Double = 0.0,
    val zoom: Double = 1.0,
    val offset: Vec2 = Vec2.Zero,
) {
    companion object {
        val Default = Framing()
        const val MAX_ZOOM = 8.0
    }
}

/** Pure math that turns a [Framing] into an exact source-to-canvas transform. */
object FramingMath {

    /** Scale at which a photo rotated by [degrees] fully covers [canvas]. */
    fun coverScale(source: PxSize, canvas: PxSize, degrees: Double): Double {
        val (c, s) = absCosSin(degrees)
        val needW = canvas.width * c + canvas.height * s
        val needH = canvas.width * s + canvas.height * c
        return max(needW / source.width, needH / source.height)
    }

    /** Scale at which a photo rotated by [degrees] fits entirely inside [canvas]. */
    fun fitScale(source: PxSize, canvas: PxSize, degrees: Double): Double {
        val (c, s) = absCosSin(degrees)
        val boundW = source.width * c + source.height * s
        val boundH = source.width * s + source.height * c
        return min(canvas.width / boundW, canvas.height / boundH)
    }

    fun minZoom(source: PxSize, canvas: PxSize, degrees: Double): Double =
        fitScale(source, canvas, degrees) / coverScale(source, canvas, degrees)

    /** Absolute source-to-canvas scale for [framing]. */
    fun scale(source: PxSize, canvas: PxSize, framing: Framing): Double =
        coverScale(source, canvas, framing.rotationDegrees) * framing.zoom

    /**
     * Brings [framing] back into its legal range: zoom between fit and [Framing.MAX_ZOOM], and an offset that
     * keeps the canvas fully covered when zoomed in, or the photo centre on the canvas when zoomed out.
     */
    fun clamp(source: PxSize, canvas: PxSize, framing: Framing): Framing {
        val degrees = framing.rotationDegrees
        val zoom = framing.zoom.coerceIn(minZoom(source, canvas, degrees), Framing.MAX_ZOOM)
        val s = coverScale(source, canvas, degrees) * zoom

        val offset = if (zoom >= 1.0) {
            // Work in the photo's own axes: the canvas, projected onto each axis, must stay inside the photo.
            val (c, sn) = absCosSin(degrees)
            val spanX = canvas.width * c + canvas.height * sn
            val spanY = canvas.width * sn + canvas.height * c
            val maxX = max(0.0, (source.width * s - spanX) / 2)
            val maxY = max(0.0, (source.height * s - spanY) / 2)
            val local = framing.offset.rotated(-degrees)
            Vec2(local.x.coerceIn(-maxX, maxX), local.y.coerceIn(-maxY, maxY)).rotated(degrees)
        } else {
            Vec2(
                framing.offset.x.coerceIn(-canvas.width / 2.0, canvas.width / 2.0),
                framing.offset.y.coerceIn(-canvas.height / 2.0, canvas.height / 2.0),
            )
        }
        return Framing(degrees, zoom, offset)
    }

    /** Whether the photo leaves any part of the canvas uncovered, so a background fill is needed. */
    fun needsBackground(source: PxSize, canvas: PxSize, framing: Framing): Boolean {
        val clamped = clamp(source, canvas, framing)
        return clamped.zoom < 1.0 - 1e-9
    }

    /**
     * Transform from decoded bitmap pixels to canvas pixels.
     * [decoded] is the size of the bitmap actually in memory, which may be subsampled from [source];
     * mapping each axis separately keeps the result exact even when subsampling rounded a dimension up.
     */
    fun transform(source: PxSize, canvas: PxSize, framing: Framing, decoded: PxSize = source): Affine {
        val s = scale(source, canvas, framing)
        val toSource = Affine(
            a = source.width.toDouble() / decoded.width,
            d = source.height.toDouble() / decoded.height,
        )
        return toSource
            .then(Affine.translate(-source.width / 2.0, -source.height / 2.0))
            .then(Affine.scale(s))
            .then(Affine.rotate(framing.rotationDegrees))
            .then(Affine.translate(canvas.width / 2.0 + framing.offset.x, canvas.height / 2.0 + framing.offset.y))
    }

    /** Transform for one tile: the canvas transform shifted so the tile's top-left is the origin. */
    fun tileTransform(canvasTransform: Affine, tile: PxRect): Affine =
        canvasTransform.then(Affine.translate(-tile.left.toDouble(), -tile.top.toDouble()))

    /**
     * Converts a framing between canvases of different sizes with the same aspect ratio (preview and export),
     * so what the user sees on screen is exactly what gets exported.
     */
    fun rescale(framing: Framing, from: PxSize, to: PxSize): Framing {
        val k = to.width.toDouble() / from.width
        return framing.copy(offset = framing.offset * k)
    }

    private fun absCosSin(degrees: Double): Pair<Double, Double> {
        val r = Math.toRadians(degrees)
        return abs(cos(r)) to abs(sin(r))
    }
}
