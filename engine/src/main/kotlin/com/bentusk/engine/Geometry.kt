package com.bentusk.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Integer pixel size. All output geometry is integral so tiles meet without gaps or overlap. */
data class PxSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Size must be positive: ${width}x$height" }
    }

    val pixels: Long get() = width.toLong() * height.toLong()
    val aspect: Double get() = width.toDouble() / height.toDouble()

    override fun toString() = "${width}x$height"
}

/** Half-open integer rectangle [left, right) x [top, bottom). */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(right > left && bottom > top) { "Empty rect: $this" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val size: PxSize get() = PxSize(width, height)

    fun contains(x: Int, y: Int) = x in left until right && y in top until bottom

    override fun toString() = "[$left,$top - $right,$bottom]"
}

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)

    fun rotated(degrees: Double): Vec2 {
        val r = Math.toRadians(degrees)
        val c = cos(r)
        val s = sin(r)
        return Vec2(x * c - y * s, x * s + y * c)
    }

    companion object {
        val Zero = Vec2(0.0, 0.0)
    }
}

/**
 * 2D affine transform in the same layout as android.graphics.Matrix:
 * x' = a*x + b*y + tx, y' = c*x + d*y + ty.
 */
data class Affine(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val tx: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val ty: Double = 0.0,
) {
    /** Returns the transform that applies [this] first and then [next]. */
    fun then(next: Affine) = Affine(
        a = next.a * a + next.b * c,
        b = next.a * b + next.b * d,
        tx = next.a * tx + next.b * ty + next.tx,
        c = next.c * a + next.d * c,
        d = next.c * b + next.d * d,
        ty = next.c * tx + next.d * ty + next.ty,
    )

    fun map(p: Vec2) = Vec2(a * p.x + b * p.y + tx, c * p.x + d * p.y + ty)

    fun inverse(): Affine {
        val det = a * d - b * c
        require(abs(det) > 1e-12) { "Transform is not invertible" }
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Affine(ia, ib, -(ia * tx + ib * ty), ic, id, -(ic * tx + id * ty))
    }

    /** Values in android.graphics.Matrix order (MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y, 0, 0, 1). */
    fun toMatrixValues(): FloatArray = floatArrayOf(
        a.toFloat(), b.toFloat(), tx.toFloat(),
        c.toFloat(), d.toFloat(), ty.toFloat(),
        0f, 0f, 1f,
    )

    companion object {
        val Identity = Affine()
        fun translate(dx: Double, dy: Double) = Affine(tx = dx, ty = dy)
        fun scale(s: Double) = Affine(a = s, d = s)
        fun rotate(degrees: Double): Affine {
            val r = Math.toRadians(degrees)
            val c = cos(r)
            val s = sin(r)
            return Affine(a = c, b = -s, c = s, d = c)
        }
    }
}
