package com.bentusk.app.core.image

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * Sample "photos" drawn in code, so template previews look like finished posts without shipping or licensing
 * any images. Each scene is a few shapes, so drawing dozens of them in the gallery stays cheap.
 */
object MockArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    const val SCENES = 10

    fun draw(canvas: Canvas, r: RectF, seed: Int) {
        canvas.save()
        canvas.clipRect(r)
        when (Math.floorMod(seed, SCENES)) {
            0 -> sunset(canvas, r)
            1 -> mountains(canvas, r)
            2 -> dunes(canvas, r)
            3 -> city(canvas, r)
            4 -> forest(canvas, r)
            5 -> blossoms(canvas, r)
            6 -> coffee(canvas, r)
            7 -> beach(canvas, r)
            8 -> arches(canvas, r)
            else -> lavender(canvas, r)
        }
        canvas.restore()
    }

    private fun gradient(canvas: Canvas, r: RectF, top: Long, bottom: Long, from: Float = 0f, to: Float = 1f) {
        val y0 = r.top + r.height() * from
        val y1 = r.top + r.height() * to
        paint.shader = LinearGradient(0f, y0, 0f, y1, top.toInt(), bottom.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(r.left, y0, r.right, y1, paint)
        paint.shader = null
    }

    private fun color(c: Long) {
        paint.shader = null
        paint.color = c.toInt()
    }

    private fun x(r: RectF, f: Float) = r.left + r.width() * f
    private fun y(r: RectF, f: Float) = r.top + r.height() * f
    private fun s(r: RectF) = minOf(r.width(), r.height())

    private fun sunset(c: Canvas, r: RectF) {
        gradient(c, r, 0xFF3A2C6B, 0xFFF39A6B, 0f, 0.62f)
        color(0xFFFFD58A); c.drawCircle(x(r, 0.5f), y(r, 0.6f), s(r) * 0.16f, paint)
        gradient(c, r, 0xFF2B3D6B, 0xFF14213D, 0.62f, 1f)
        color(0x66FFD58A)
        for (i in 0 until 5) {
            val yy = y(r, 0.66f + i * 0.06f)
            val half = s(r) * (0.18f - i * 0.025f)
            c.drawRect(x(r, 0.5f) - half, yy, x(r, 0.5f) + half, yy + s(r) * 0.012f, paint)
        }
    }

    private fun mountains(c: Canvas, r: RectF) {
        gradient(c, r, 0xFF8EC5E8, 0xFFE3F1F8)
        color(0xFF7C93AE)
        c.drawPath(Path().apply { moveTo(x(r, -0.1f), y(r, 0.85f)); lineTo(x(r, 0.35f), y(r, 0.3f)); lineTo(x(r, 0.8f), y(r, 0.85f)); close() }, paint)
        color(0xFF51677F)
        c.drawPath(Path().apply { moveTo(x(r, 0.3f), y(r, 0.9f)); lineTo(x(r, 0.72f), y(r, 0.38f)); lineTo(x(r, 1.15f), y(r, 0.9f)); close() }, paint)
        color(0xFFF5F8FB)
        c.drawPath(Path().apply { moveTo(x(r, 0.35f), y(r, 0.3f)); lineTo(x(r, 0.27f), y(r, 0.4f)); lineTo(x(r, 0.43f), y(r, 0.4f)); close() }, paint)
        c.drawPath(Path().apply { moveTo(x(r, 0.72f), y(r, 0.38f)); lineTo(x(r, 0.65f), y(r, 0.47f)); lineTo(x(r, 0.79f), y(r, 0.47f)); close() }, paint)
        color(0xFF3E6B48); c.drawRect(r.left, y(r, 0.85f), r.right, r.bottom, paint)
    }

    private fun dunes(c: Canvas, r: RectF) {
        gradient(c, r, 0xFFF6C99A, 0xFFFBE7CF, 0f, 0.55f)
        color(0xFFFFF4DC); c.drawCircle(x(r, 0.75f), y(r, 0.25f), s(r) * 0.1f, paint)
        color(0xFFE0A06A)
        c.drawPath(Path().apply { moveTo(r.left, y(r, 0.6f)); quadTo(x(r, 0.4f), y(r, 0.4f), r.right, y(r, 0.65f)); lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close() }, paint)
        color(0xFFC77E4A)
        c.drawPath(Path().apply { moveTo(r.left, y(r, 0.85f)); quadTo(x(r, 0.6f), y(r, 0.6f), r.right, y(r, 0.8f)); lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close() }, paint)
    }

    private fun city(c: Canvas, r: RectF) {
        gradient(c, r, 0xFF0F1630, 0xFF3B3F7A)
        color(0xFFF2E8C9); c.drawCircle(x(r, 0.78f), y(r, 0.2f), s(r) * 0.07f, paint)
        val widths = floatArrayOf(0.14f, 0.1f, 0.16f, 0.12f, 0.18f, 0.12f, 0.14f)
        val heights = floatArrayOf(0.45f, 0.6f, 0.38f, 0.7f, 0.5f, 0.62f, 0.42f)
        var left = -0.02f
        for (i in widths.indices) {
            val l = x(r, left)
            val rr = x(r, left + widths[i])
            val t = y(r, 1f - heights[i])
            color(if (i % 2 == 0) 0xFF1C2347 else 0xFF242C58)
            c.drawRect(l, t, rr, r.bottom, paint)
            color(0xFFFFD27A)
            var wy = t + s(r) * 0.04f
            while (wy < r.bottom - s(r) * 0.03f) {
                if (((wy * 7 + l).toInt() / 13) % 3 != 0) c.drawRect(l + s(r) * 0.02f, wy, l + s(r) * 0.04f, wy + s(r) * 0.02f, paint)
                wy += s(r) * 0.06f
            }
            left += widths[i]
        }
    }

    private fun forest(c: Canvas, r: RectF) {
        gradient(c, r, 0xFFDDEBD7, 0xFFA9C9A0)
        for (row in 0..1) {
            color(if (row == 0) 0xFF5E8C61 else 0xFF2F5A3A)
            val base = y(r, if (row == 0) 0.75f else 1.0f)
            val h = r.height() * (if (row == 0) 0.4f else 0.5f)
            var cx = x(r, if (row == 0) 0.05f else -0.05f)
            while (cx < r.right + s(r) * 0.2f) {
                c.drawPath(Path().apply { moveTo(cx, base - h); lineTo(cx - s(r) * 0.11f, base); lineTo(cx + s(r) * 0.11f, base); close() }, paint)
                cx += s(r) * 0.17f
            }
        }
    }

    private fun blossoms(c: Canvas, r: RectF) {
        gradient(c, r, 0xFFFCE4EC, 0xFFF8BBD0)
        val centres = listOf(0.25f to 0.3f, 0.7f to 0.25f, 0.45f to 0.6f, 0.8f to 0.75f, 0.15f to 0.8f)
        for ((i, p) in centres.withIndex()) {
            val cx = x(r, p.first)
            val cy = y(r, p.second)
            val rad = s(r) * (0.09f + (i % 2) * 0.03f)
            color(if (i % 2 == 0) 0xFFFFFFFF else 0xFFF48FB1)
            for (k in 0 until 5) {
                val a = Math.toRadians(k * 72.0)
                c.drawCircle(cx + (Math.cos(a) * rad).toFloat(), cy + (Math.sin(a) * rad).toFloat(), rad * 0.75f, paint)
            }
            color(0xFFFFC94D); c.drawCircle(cx, cy, rad * 0.45f, paint)
        }
    }

    private fun coffee(c: Canvas, r: RectF) {
        gradient(c, r, 0xFFE9DCC9, 0xFFD8C3A5)
        val cx = x(r, 0.5f)
        val cy = y(r, 0.5f)
        val rad = s(r) * 0.28f
        color(0xFFF7F3EE); c.drawCircle(cx, cy, rad * 1.35f, paint)
        color(0xFFFFFFFF); c.drawCircle(cx, cy, rad, paint)
        color(0xFF6B4226); c.drawCircle(cx, cy, rad * 0.78f, paint)
        color(0xFFC89F7A); c.drawCircle(cx - rad * 0.1f, cy - rad * 0.1f, rad * 0.25f, paint)
        color(0xFFFFFFFF)
        c.drawRoundRect(RectF(cx + rad * 0.95f, cy - rad * 0.15f, cx + rad * 1.35f, cy + rad * 0.15f), rad * 0.15f, rad * 0.15f, paint)
    }

    private fun beach(c: Canvas, r: RectF) {
        gradient(c, r, 0xFF6EC6F0, 0xFFC7EAF8, 0f, 0.5f)
        gradient(c, r, 0xFF1F9DB5, 0xFF4CC3C9, 0.5f, 0.68f)
        color(0xFFF3DDB3); c.drawRect(r.left, y(r, 0.68f), r.right, r.bottom, paint)
        color(0xFFFFFFFF)
        c.drawPath(Path().apply { moveTo(r.left, y(r, 0.68f)); quadTo(x(r, 0.5f), y(r, 0.73f), r.right, y(r, 0.68f)); lineTo(r.right, y(r, 0.7f)); quadTo(x(r, 0.5f), y(r, 0.76f), r.left, y(r, 0.7f)); close() }, paint)
        color(0xFFE85D5D)
        c.drawArc(RectF(x(r, 0.55f), y(r, 0.62f), x(r, 0.85f), y(r, 0.82f)), 180f, 180f, true, paint)
        color(0xFF5A4636); c.drawRect(x(r, 0.695f), y(r, 0.72f), x(r, 0.705f), y(r, 0.9f), paint)
    }

    private fun arches(c: Canvas, r: RectF) {
        color(0xFFE8B49A); c.drawRect(r, paint)
        color(0xFFC96F53)
        c.drawRoundRect(RectF(x(r, 0.15f), y(r, 0.25f), x(r, 0.55f), r.bottom + s(r)), s(r) * 0.2f, s(r) * 0.2f, paint)
        color(0xFFF4D9C6)
        c.drawRoundRect(RectF(x(r, 0.45f), y(r, 0.45f), x(r, 0.85f), r.bottom + s(r)), s(r) * 0.2f, s(r) * 0.2f, paint)
        color(0xFF8E3B2E); c.drawCircle(x(r, 0.78f), y(r, 0.2f), s(r) * 0.08f, paint)
    }

    private fun lavender(c: Canvas, r: RectF) {
        gradient(c, r, 0xFFFFE0C2, 0xFFE9C9E8, 0f, 0.45f)
        gradient(c, r, 0xFF9C7CC9, 0xFF5B3E8C, 0.45f, 1f)
        color(0xFFB79AE0)
        val rows = 6
        for (i in 0 until rows) {
            val t = 0.45f + i * (0.55f / rows)
            val yy = y(r, t)
            c.drawRect(r.left, yy, r.right, yy + r.height() * 0.02f * (i + 1) / 2, paint)
        }
    }
}
