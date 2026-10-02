package com.pranvir.fizz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A pre-rendered sprite: bitmap plus its half-size in board units, drawn centred. */
class Spr(val bmp: Bitmap, val half: Float) {
    fun draw(c: Canvas, x: Float, y: Float, sx: Float = 1f, sy: Float = sx, p: Paint = Draw.bmp) {
        Draw.r1.set(x - half * sx, y - half * sy, x + half * sx, y + half * sy)
        c.drawBitmap(bmp, null, Draw.r1, p)
    }
    fun release() { if (!bmp.isRecycled) bmp.recycle() }
}

/**
 * Bubble-critter sprite set, inked at the device's real pixel size. Bodies come in three "boil" variants (the
 * hand-drawn line shimmer of 1930s cels) and every colour has its own face, so colours read even without colour.
 */
class Art(val u: Float) {
    companion object {
        const val BOILS = 3
        const val GAZES = 9
        const val F_BLINK = 9; const val F_SCARED = 10; const val F_HAPPY = 11
        const val FACES = 12
        const val R = Board.R
        const val EX = 12.5f
        const val EY = -6f
    }

    private val all = ArrayList<Spr>()
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val p = Path()
    private val p2 = Path()
    private val rf = RectF()
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun mk(half: Float, draw: (Canvas) -> Unit): Spr {
        val px = max(4, ceil(half * 2f * u).toInt())
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.save()
        c.scale(px / (half * 2f), px / (half * 2f))
        c.translate(half, half)
        draw(c)
        c.restore()
        return Spr(b, half).also { all.add(it) }
    }

    private val H = R * 1.3f

    val body: Array<Array<Spr>> = Array(6) { col -> Array(BOILS) { b -> mk(H) { c -> drawBody(c, Pal.BUB[col], b) } } }
    val face: Array<Array<Spr>> = Array(6) { col -> Array(FACES) { k -> mk(H) { c -> drawFace(c, col, k) } } }
    val stone: Array<Spr> = Array(BOILS) { b -> mk(H) { c -> drawStone(c, b) } }
    val bomb: Array<Spr> = Array(BOILS) { b -> mk(H) { c -> drawBomb(c, b) } }
    val ink: Array<Spr> = Array(BOILS) { b -> mk(H) { c -> drawInk(c, b) } }
    val spook: Array<Array<Spr>> = Array(6) { col -> Array(BOILS) { b -> mk(H) { c -> drawSpook(c, Pal.BUB[col], b) } } }
    val note: Spr = mk(H) { c -> drawNoteBadge(c) }
    val ticket: Spr = mk(H) { c -> drawTicket(c) }
    val cage1: Spr = mk(H) { c -> drawCage(c, 1) }
    val cage2: Spr = mk(H) { c -> drawCage(c, 2) }
    val rainbow: Array<Spr> = Array(BOILS) { b -> mk(H) { c -> drawRainbow(c, b) } }
    val firecracker: Spr = mk(H) { c -> drawFirecracker(c) }
    val trumpet: Spr = mk(H) { c -> drawTrumpet(c) }

    fun release() { for (x in all) x.release(); all.clear() }

    // ------------------------------------------------------------------ bodies

    private fun shadow(c: Canvas) {
        f.color = withAlpha(Pal.INK, 70); f.shader = null
        rf.set(-R * 0.92f + 5f, -R * 0.86f + 9f, R * 0.92f + 5f, R * 0.86f + 9f)
        c.drawOval(rf, f)
    }

    private fun shaded(c: Canvas, col: Int, rad: Float = R - 2f) {
        // cel shadow crescent, then a soft airbrushed glow, then the rubber-ball shine
        f.shader = null
        f.color = scaleRgb(col, 0.68f); c.drawCircle(0f, 0f, rad, f)
        c.save()
        p.reset(); p.addCircle(0f, 0f, rad, Path.Direction.CW); c.clipPath(p)
        f.color = col; c.drawCircle(-5f, -6.5f, rad * 0.93f, f)
        f.alpha = 255; f.shader = RadialGradient(-14f, -16f, rad * 1.1f, intArrayOf(withAlpha(0xFFFFFFFF.toInt(), 95), withAlpha(0xFFFFFFFF.toInt(), 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, rad, f)
        f.shader = null
        // a warm bounce light along the lower edge
        s.color = withAlpha(lerpColor(col, 0xFFFFE8B0.toInt(), 0.5f), 90); s.strokeWidth = 3f
        rf.set(-rad + 4f, -rad + 4f, rad - 4f, rad - 4f)
        c.drawArc(rf, 30f, 80f, false, s)
        c.restore()
        // shine
        c.save(); c.rotate(-38f, -15f, -18f)
        f.color = withAlpha(0xFFFFFFFF.toInt(), 225)
        rf.set(-15f - 9.5f, -18f - 5.5f, -15f + 9.5f, -18f + 5.5f); c.drawOval(rf, f)
        c.restore()
        f.color = withAlpha(0xFFFFFFFF.toInt(), 235); c.drawCircle(-4f, -27f, 2.6f, f)
    }

    private fun inkOutline(c: Canvas, boil: Int, rad: Float = R - 0.5f, w: Float = 4.2f) {
        Draw.inkRing(p, 0f, 0f, rad, w, boil)
        f.shader = null; f.color = Pal.INK
        c.drawPath(p, f)
    }

    private fun drawBody(c: Canvas, col: Int, boil: Int) {
        shadow(c)
        shaded(c, col)
        inkOutline(c, boil)
    }

    // ------------------------------------------------------------------ faces


    /** gaze index 0..8 -> offset in -1..1 */
    private fun gx(k: Int) = if (k >= GAZES) 0f else (k % 3 - 1).toFloat()
    private fun gy(k: Int) = if (k >= GAZES) 0f else (k / 3 - 1).toFloat()

    private fun drawFace(c: Canvas, col: Int, k: Int) {
        val body = Pal.BUB[col]
        when (k) {
            F_BLINK -> { closedEyes(c, false); mouth(c, col, body, false) }
            F_SCARED -> { scaredEyes(c); oMouth(c, 7f, 9f) }
            F_HAPPY -> { closedEyes(c, true); bigSmile(c, true) }
            else -> {
                val dx = gx(k); val dy = gy(k)
                when (col) {
                    0, 3 -> pieEyes(c, dx, dy, brow = col == 3)
                    1 -> roundEyes(c, dx, dy, lashes = false)
                    2 -> sleepyEyes(c, dx, dy, body)
                    4 -> roundEyes(c, dx, dy, lashes = true)
                    else -> wonkyEyes(c, dx, dy)
                }
                mouth(c, col, body, true)
            }
        }
    }

    private fun eyeWhite(c: Canvas, x: Float, y: Float, rx: Float, ry: Float) {
        f.shader = null
        f.color = Pal.INK; rf.set(x - rx - 2f, y - ry - 2f, x + rx + 2f, y + ry + 2f); c.drawOval(rf, f)
        f.color = 0xFFFFFBF0.toInt(); rf.set(x - rx, y - ry, x + rx, y + ry); c.drawOval(rf, f)
    }

    /** Classic pie-cut pupils on big eye whites. */
    private fun pieEyes(c: Canvas, dx: Float, dy: Float, brow: Boolean) {
        for (side in intArrayOf(-1, 1)) {
            val x = side * EX; val y = EY
            eyeWhite(c, x, y, 8.5f, 12f)
            val px = x + dx * 3.2f; val py = y + dy * 3.4f + 1.5f
            c.save()
            p.reset(); rf.set(x - 8.5f, y - 12f, x + 8.5f, y + 12f); p.addOval(rf, Path.Direction.CW); c.clipPath(p)
            f.color = Pal.INK; rf.set(px - 5.6f, py - 8.6f, px + 5.6f, py + 8.6f); c.drawOval(rf, f)
            // the pie slice
            f.color = 0xFFFFFBF0.toInt()
            p2.reset(); p2.moveTo(px + 0.5f, py - 1.5f); p2.lineTo(px + 7f, py - 6.5f); p2.lineTo(px + 2.5f, py - 10.5f); p2.close()
            c.drawPath(p2, f)
            c.restore()
        }
        if (brow) {
            s.color = Pal.INK; s.strokeWidth = 3.2f
            p2.reset(); p2.moveTo(4f, -22f); p2.quadTo(12f, -29f, 21f, -23f); c.drawPath(p2, s)
        }
    }

    private fun roundEyes(c: Canvas, dx: Float, dy: Float, lashes: Boolean) {
        for (side in intArrayOf(-1, 1)) {
            val x = side * EX; val y = EY
            eyeWhite(c, x, y, 9f, 11f)
            val px = x + dx * 3.5f; val py = y + dy * 3.5f + 1f
            f.color = Pal.INK; c.drawCircle(px, py, 4.6f, f)
            f.color = 0xFFFFFFFF.toInt(); c.drawCircle(px + 1.6f, py - 1.8f, 1.5f, f)
            if (lashes) {
                s.color = Pal.INK; s.strokeWidth = 2.4f
                for (q in 0 until 3) {
                    val a = (-PI / 2 + side * (0.35 + q * 0.38)).toFloat()
                    c.drawLine(x + cos(a) * 10f, y + sin(a) * 12f, x + cos(a) * 15f, y + sin(a) * 17f, s)
                }
            }
        }
    }

    private fun sleepyEyes(c: Canvas, dx: Float, dy: Float, body: Int) {
        for (side in intArrayOf(-1, 1)) {
            val x = side * EX; val y = EY + 1f
            eyeWhite(c, x, y, 8.5f, 10f)
            val px = x + dx * 3f; val py = y + 2.5f + dy * 1.5f
            f.color = Pal.INK; c.drawCircle(px, py, 4.4f, f)
            // heavy lid
            c.save()
            p.reset(); rf.set(x - 8.5f, y - 10f, x + 8.5f, y + 10f); p.addOval(rf, Path.Direction.CW); c.clipPath(p)
            f.color = scaleRgb(body, 0.85f); c.drawRect(x - 12f, y - 12f, x + 12f, y - 1f, f)
            c.restore()
            s.color = Pal.INK; s.strokeWidth = 2.6f
            c.drawLine(x - 9f, y - 1f, x + 9f, y - 1f, s)
        }
    }

    private fun wonkyEyes(c: Canvas, dx: Float, dy: Float) {
        eyeWhite(c, -EX - 1f, EY - 1f, 10f, 12.5f)
        eyeWhite(c, EX + 1f, EY + 1f, 7f, 8.5f)
        f.color = Pal.INK
        c.drawCircle(-EX - 1f + dx * 4f, EY + dy * 4f, 5f, f)
        c.drawCircle(EX + 1f + dx * 2.5f, EY + 2f + dy * 2.5f, 3.6f, f)
        f.color = 0xFFFFFFFF.toInt(); c.drawCircle(-EX + 1f + dx * 4f, EY - 2f + dy * 4f, 1.6f, f)
    }

    private fun closedEyes(c: Canvas, happy: Boolean) {
        s.color = Pal.INK; s.strokeWidth = 3.4f
        for (side in intArrayOf(-1, 1)) {
            val x = side * EX; val y = EY
            p2.reset()
            if (happy) { p2.moveTo(x - 7f, y + 3f); p2.quadTo(x, y - 8f, x + 7f, y + 3f) }
            else { p2.moveTo(x - 7f, y); p2.quadTo(x, y + 6f, x + 7f, y) }
            c.drawPath(p2, s)
        }
    }

    private fun scaredEyes(c: Canvas) {
        for (side in intArrayOf(-1, 1)) {
            val x = side * EX; val y = EY - 2f
            eyeWhite(c, x, y, 10f, 13.5f)
            f.color = Pal.INK; c.drawCircle(x, y + 1f, 2.8f, f)
        }
        s.color = Pal.INK; s.strokeWidth = 2.8f
        c.drawLine(-21f, -27f, -8f, -24f, s); c.drawLine(21f, -27f, 8f, -24f, s)
    }

    private fun oMouth(c: Canvas, rx: Float, ry: Float) {
        f.color = Pal.INK; rf.set(-rx - 2f, 14f - ry - 2f, rx + 2f, 14f + ry + 2f); c.drawOval(rf, f)
        f.color = 0xFF7A1E1E.toInt(); rf.set(-rx, 14f - ry, rx, 14f + ry); c.drawOval(rf, f)
    }

    private fun bigSmile(c: Canvas, tongue: Boolean) {
        p2.reset()
        p2.moveTo(-15f, 8f); p2.quadTo(0f, 12f, 15f, 8f); p2.quadTo(13f, 27f, 0f, 27f); p2.quadTo(-13f, 27f, -15f, 8f); p2.close()
        f.color = Pal.INK; c.drawPath(p2, f)
        c.save(); c.scale(0.82f, 0.78f, 0f, 14f)
        f.color = 0xFF7A1E1E.toInt(); c.drawPath(p2, f)
        c.restore()
        if (tongue) { f.color = 0xFFE86A6A.toInt(); rf.set(-7f, 17f, 7f, 25f); c.drawOval(rf, f) }
    }

    private fun mouth(c: Canvas, col: Int, body: Int, open: Boolean) {
        s.color = Pal.INK
        when (col) {
            0 -> bigSmile(c, true)
            1 -> { // toothy grin
                p2.reset(); p2.moveTo(-16f, 9f); p2.quadTo(0f, 14f, 16f, 9f); p2.quadTo(10f, 25f, 0f, 25f); p2.quadTo(-10f, 25f, -16f, 9f); p2.close()
                f.color = Pal.INK; c.drawPath(p2, f)
                f.color = 0xFFFFFBF0.toInt(); c.drawRect(-11f, 11.5f, 11f, 16f, f)
                s.strokeWidth = 1.3f; for (q in -2..2) c.drawLine(q * 4.4f, 11.5f, q * 4.4f, 16f, s)
            }
            2 -> { // whistling o
                f.color = Pal.INK; c.drawCircle(3f, 15f, 5.5f, f)
                f.color = 0xFF7A1E1E.toInt(); c.drawCircle(3f, 15f, 3f, f)
                s.strokeWidth = 2.4f; c.drawLine(-10f, 22f, -4f, 21f, s)
            }
            3 -> { // smirk
                s.strokeWidth = 3.4f
                p2.reset(); p2.moveTo(-13f, 13f); p2.quadTo(0f, 19f, 13f, 9f); c.drawPath(p2, s)
                s.strokeWidth = 2.4f; c.drawLine(13f, 9f, 16f, 6f, s)
            }
            4 -> { // cat mouth
                s.strokeWidth = 3f
                p2.reset(); p2.moveTo(-12f, 12f); p2.quadTo(-6f, 19f, 0f, 13f); p2.quadTo(6f, 19f, 12f, 12f); c.drawPath(p2, s)
                f.color = 0xFFE88AA0.toInt(); c.drawCircle(-19f, 8f, 3.5f, f); c.drawCircle(19f, 8f, 3.5f, f)
            }
            else -> { // goofy tongue
                p2.reset(); p2.moveTo(-14f, 10f); p2.quadTo(0f, 22f, 14f, 8f); p2.quadTo(4f, 15f, -14f, 10f); p2.close()
                f.color = Pal.INK; c.drawPath(p2, f)
                f.color = 0xFFE86A6A.toInt(); rf.set(3f, 13f, 13f, 24f); c.drawOval(rf, f)
                s.strokeWidth = 2f; c.drawOval(rf, s)
            }
        }
    }

    // ------------------------------------------------------------------ specials

    private fun drawStone(c: Canvas, boil: Int) {
        shadow(c)
        shaded(c, 0xFF9D9384.toInt())
        f.color = withAlpha(0xFF5E564B.toInt(), 160)
        for (q in 0 until 7) {
            val a = q * 1.7f + 0.4f; val rr = 12f + (q % 3) * 8f
            rf.set(cos(a) * rr - 5f, sin(a) * rr - 3.5f, cos(a) * rr + 5f, sin(a) * rr + 3.5f); c.drawOval(rf, f)
        }
        s.color = Pal.INK; s.strokeWidth = 2.2f
        p2.reset(); p2.moveTo(-30f, -12f); p2.lineTo(-18f, -6f); p2.lineTo(-20f, 4f); p2.lineTo(-10f, 10f); c.drawPath(p2, s)
        p2.reset(); p2.moveTo(26f, 14f); p2.lineTo(17f, 18f); p2.lineTo(15f, 28f); c.drawPath(p2, s)
        // grumpy sleeper
        s.strokeWidth = 3f
        c.drawLine(-17f, -6f, -6f, -4f, s); c.drawLine(17f, -6f, 6f, -4f, s)
        p2.reset(); p2.moveTo(-8f, 15f); p2.quadTo(0f, 11f, 8f, 15f); c.drawPath(p2, s)
        inkOutline(c, boil)
    }

    private fun drawBomb(c: Canvas, boil: Int) {
        shadow(c)
        f.color = 0xFF2A2830.toInt(); c.drawCircle(0f, 0f, R - 2f, f)
        f.alpha = 255; f.shader = RadialGradient(-14f, -16f, R * 1.1f, intArrayOf(0x806E8CB8.toInt(), 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, R - 2f, f); f.shader = null
        c.save(); c.rotate(-38f, -15f, -18f)
        f.color = 0xCCFFFFFF.toInt(); rf.set(-24f, -23f, -6f, -13f); c.drawOval(rf, f)
        c.restore()
        // cap
        c.save(); c.rotate(35f)
        f.color = Pal.INK; c.drawRect(-9f, -R - 6f, 9f, -R + 8f, f)
        f.color = 0xFF8C8C98.toInt(); c.drawRect(-7f, -R - 4f, 7f, -R + 6f, f)
        c.restore()
        // worried face
        for (side in intArrayOf(-1, 1)) {
            eyeWhite(c, side * 11f, -2f, 7.5f, 9f)
            f.color = Pal.INK; c.drawCircle(side * 11f, -5f, 3.4f, f)
        }
        s.color = 0xFFFFFBF0.toInt(); s.strokeWidth = 2.6f
        p2.reset(); p2.moveTo(-10f, 18f); p2.quadTo(-5f, 13f, 0f, 18f); p2.quadTo(5f, 23f, 10f, 18f); c.drawPath(p2, s)
        inkOutline(c, boil)
    }

    private fun drawInk(c: Canvas, boil: Int) {
        shadow(c)
        p.reset()
        val n = 28
        for (k in 0..n) {
            val a = k * TAU / n
            var rr = R - 3f + 3.5f * sin(a * 4f + boil * 1.9f) + 2f * sin(a * 7f + boil)
            // drips along the bottom
            val down = sin(a)
            if (down > 0.5f) rr += 7f * hash01(k + boil * 13, 5) * (down - 0.5f) * 2f
            val x = cos(a) * rr; val y = sin(a) * rr
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        f.color = 0xFF120D18.toInt(); c.drawPath(p, f)
        c.save(); c.clipPath(p)
        f.alpha = 255; f.shader = RadialGradient(-12f, -14f, R, intArrayOf(0x907A5AA8.toInt(), 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, R + 8f, f); f.shader = null
        c.restore()
        f.color = 0xB0FFFFFF.toInt(); rf.set(-22f, -26f, -10f, -18f); c.drawOval(rf, f)
        // mischievous white pie eyes and grin
        for (side in intArrayOf(-1, 1)) {
            f.color = 0xFFFFFBF0.toInt(); rf.set(side * 11f - 7f, -15f, side * 11f + 7f, 3f); c.drawOval(rf, f)
            f.color = 0xFF120D18.toInt(); rf.set(side * 11f - 3.5f + 1f, -10f, side * 11f + 3.5f + 1f, 1f); c.drawOval(rf, f)
        }
        s.color = 0xFF120D18.toInt()
        p2.reset(); p2.moveTo(-13f, 9f); p2.quadTo(0f, 22f, 13f, 9f); p2.quadTo(0f, 14f, -13f, 9f); p2.close()
        f.color = 0xFFFFFBF0.toInt(); c.drawPath(p2, f)
        s.strokeWidth = 1.4f; for (q in -2..2) c.drawLine(q * 4.4f, 10f, q * 4.4f, 15f, s)
    }

    private fun drawSpook(c: Canvas, col: Int, boil: Int) {
        // a ghost in the critter's colour: round top, scalloped hem
        f.color = withAlpha(Pal.INK, 50); rf.set(-R * 0.8f + 4f, R * 0.55f, R * 0.8f + 4f, R * 0.95f); c.drawOval(rf, f)
        p.reset()
        val top = -R + 2f
        p.moveTo(-R + 4f, 6f)
        rf.set(-R + 4f, top, R - 4f, top + (R - 4f) * 2f)
        p.arcTo(rf, 180f, 180f, false)
        p.lineTo(R - 4f, R - 10f)
        val sc = 4
        for (q in 0 until sc) {
            val x0 = R - 4f - q * (2 * R - 8f) / sc
            val x1 = x0 - (2 * R - 8f) / sc
            val wob = sin(boil * 2.1f + q) * 3f
            p.quadTo((x0 + x1) / 2f, R + 4f + wob, x1, R - 10f)
        }
        p.close()
        val light = lerpColor(col, 0xFFFFFFFF.toInt(), 0.18f)
        f.color = Pal.INK
        c.save(); c.scale(1.08f, 1.06f, 0f, 0f); c.drawPath(p, f); c.restore()
        f.color = light; c.drawPath(p, f)
        c.save(); c.clipPath(p)
        f.alpha = 255; f.shader = RadialGradient(-12f, -16f, R, intArrayOf(0x80FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, R + 8f, f); f.shader = null
        f.color = withAlpha(scaleRgb(col, 0.6f), 120); c.drawRect(-R, R * 0.35f, R, R + 10f, f)
        c.restore()
        // hollow eyes and an "oooh"
        f.color = Pal.INK
        rf.set(-18f, -14f, -6f, 2f); c.drawOval(rf, f)
        rf.set(6f, -14f, 18f, 2f); c.drawOval(rf, f)
        f.color = withAlpha(0xFFFFFFFF.toInt(), 200); c.drawCircle(-10f, -10f, 2f, f); c.drawCircle(14f, -10f, 2f, f)
        f.color = Pal.INK; rf.set(-5f, 8f, 5f, 21f); c.drawOval(rf, f)
    }

    private fun drawNoteBadge(c: Canvas) {
        f.color = Pal.INK; c.drawCircle(0f, 2f, 24f, f)
        f.color = 0xFFFFF4D6.toInt(); c.drawCircle(0f, 2f, 21.5f, f)
        f.color = withAlpha(Pal.GOLD, 120); c.drawCircle(0f, 2f, 17f, f)
        // eighth note
        f.color = Pal.INK
        c.save(); c.rotate(-20f, -5f, 11f)
        rf.set(-13f, 6f, 1f, 16f); c.drawOval(rf, f)
        c.restore()
        c.drawRect(-2f, -16f, 2f, 12f, f)
        p2.reset(); p2.moveTo(2f, -16f); p2.cubicTo(8f, -10f, 14f, -8f, 11f, 2f); p2.cubicTo(11f, -4f, 7f, -7f, 2f, -8f); p2.close()
        c.drawPath(p2, f)
    }

    private fun drawTicket(c: Canvas) {
        c.save(); c.translate(17f, 22f); c.rotate(-18f)
        f.color = Pal.INK; c.drawRect(-15f, -9f, 15f, 9f, f)
        f.color = Pal.GOLD; c.drawRect(-13f, -7f, 13f, 7f, f)
        f.color = Pal.GOLD_DK; c.drawCircle(-13f, 0f, 3f, f); c.drawCircle(13f, 0f, 3f, f)
        s.color = Pal.GOLD_DK; s.strokeWidth = 1.5f; c.drawLine(-7f, -4f, 7f, -4f, s); c.drawLine(-7f, 0f, 7f, 0f, s); c.drawLine(-7f, 4f, 3f, 4f, s)
        c.restore()
    }

    private fun drawCage(c: Canvas, hp: Int) {
        val bar = 0xFF3B3F48.toInt(); val hi = 0xFF9AA3B0.toInt()
        for (x in floatArrayOf(-21f, 0f, 21f)) {
            val h = kotlin.math.sqrt((R * R - x * x).coerceAtLeast(0f)) - 2f
            f.color = Pal.INK; c.drawRect(x - 4.2f, -h, x + 4.2f, h, f)
            f.color = bar; c.drawRect(x - 2.8f, -h + 1f, x + 2.8f, h - 1f, f)
            f.color = hi; c.drawRect(x - 2.2f, -h + 2f, x - 0.6f, h - 2f, f)
        }
        s.color = Pal.INK; s.strokeWidth = 6.5f
        rf.set(-R + 3f, -R + 3f, R - 3f, R - 3f); c.drawArc(rf, 200f, 140f, false, s)
        s.color = bar; s.strokeWidth = 3.6f; c.drawArc(rf, 202f, 136f, false, s)
        if (hp >= 2) {
            // crossed chains
            for (q in 0 until 9) {
                val t = q / 8f
                for (side in intArrayOf(-1, 1)) {
                    val x = lerp(-30f, 30f, t) * side; val y = lerp(-30f, 30f, t)
                    f.color = Pal.INK; rf.set(x - 5f, y - 3.6f, x + 5f, y + 3.6f); c.drawOval(rf, f)
                    f.color = if (q % 2 == 0) 0xFFB8BEC8.toInt() else 0xFF6C7380.toInt(); rf.set(x - 3.6f, y - 2.4f, x + 3.6f, y + 2.4f); c.drawOval(rf, f)
                }
            }
        }
        // padlock
        f.color = Pal.INK; rf.set(-11f, 18f, 11f, 38f); c.drawRoundRect(rf, 4f, 4f, f)
        s.color = Pal.INK; s.strokeWidth = 5f; rf.set(-7f, 9f, 7f, 25f); c.drawArc(rf, 180f, 180f, false, s)
        s.color = 0xFF9AA3B0.toInt(); s.strokeWidth = 2.4f; c.drawArc(rf, 182f, 176f, false, s)
        f.color = Pal.GOLD; rf.set(-8.5f, 20.5f, 8.5f, 35.5f); c.drawRoundRect(rf, 3f, 3f, f)
        f.color = Pal.INK; c.drawCircle(0f, 26f, 2.4f, f); c.drawRect(-1f, 26f, 1f, 31f, f)
    }

    private fun drawRainbow(c: Canvas, boil: Int) {
        shadow(c)
        val rad = R - 2f
        c.save()
        p.reset(); p.addCircle(0f, 0f, rad, Path.Direction.CW); c.clipPath(p)
        for (k in 0 until 6) {
            f.color = Pal.BUB[k]
            c.drawArc(-rad * 1.4f, -rad * 1.4f, rad * 1.4f, rad * 1.4f, k * 60f + boil * 20f, 61f, true, f)
        }
        f.alpha = 255; f.shader = RadialGradient(0f, 0f, rad, intArrayOf(0xF0FFFFFF.toInt(), 0x40FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, rad, f); f.shader = null
        c.restore()
        // star eyes
        for (side in intArrayOf(-1, 1)) {
            Draw.star(p2, side * 12f, -6f, 10f, 0.45f)
            f.color = Pal.INK; c.save(); c.scale(1.25f, 1.25f, side * 12f, -6f); c.drawPath(p2, f); c.restore()
            f.color = Pal.GOLD; c.drawPath(p2, f)
        }
        bigSmile(c, true)
        inkOutline(c, boil)
    }

    private fun drawFirecracker(c: Canvas) {
        c.save(); c.rotate(-20f)
        f.color = Pal.INK; rf.set(-17f, -32f, 17f, 32f); c.drawRoundRect(rf, 7f, 7f, f)
        f.alpha = 255; f.shader = LinearGradient(-14f, 0f, 14f, 0f, intArrayOf(0xFFE8503C.toInt(), 0xFFB52A22.toInt(), 0xFF7E1612.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        rf.set(-14f, -29f, 14f, 29f); c.drawRoundRect(rf, 5f, 5f, f); f.shader = null
        f.color = Pal.CREAM; c.drawRect(-14f, -8f, 14f, 6f, f)
        tp.typeface = Fonts.chunky; tp.textSize = 10f; tp.color = Pal.INK; tp.textAlign = Paint.Align.CENTER
        c.drawText("BANG", 0f, 3.5f, tp)
        f.color = 0xFF2A2830.toInt(); c.drawRect(-10f, -33f, 10f, -27f, f)
        c.restore()
    }

    private fun drawTrumpet(c: Canvas) {
        c.save(); c.rotate(-35f)
        f.color = Pal.INK
        p2.reset(); p2.moveTo(-30f, -6f); p2.lineTo(14f, -6f); p2.lineTo(34f, -20f); p2.lineTo(34f, 20f); p2.lineTo(14f, 6f); p2.lineTo(-30f, 6f); p2.close()
        c.save(); c.scale(1.12f, 1.25f, 0f, 0f); c.drawPath(p2, f); c.restore()
        f.alpha = 255; f.shader = LinearGradient(0f, -20f, 0f, 20f, intArrayOf(0xFFFFE9A0.toInt(), Pal.GOLD, Pal.GOLD_DK), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(p2, f); f.shader = null
        f.color = Pal.GOLD_DK
        for (q in 0 until 3) c.drawRect(-12f + q * 8f, -13f, -8f + q * 8f, -6f, f)
        f.color = Pal.INK; rf.set(30f, -20f, 38f, 20f); c.drawOval(rf, f)
        f.color = Pal.GOLD; rf.set(31.5f, -17f, 36.5f, 17f); c.drawOval(rf, f)
        c.restore()
    }
}
