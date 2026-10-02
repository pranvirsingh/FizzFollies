package com.pranvir.fizz

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Live-drawn rubber-hose characters: Fizz the seltzer bottle and Baron Von Boil. Board units, y down. */
object Toon {
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val p = Path()
    private val p2 = Path()
    private val rf = RectF()
    private const val INK = Pal.INK
    private const val GLOVE = 0xFFFFFBF0.toInt()

    // shaders are built once around the origin; the canvas carries every transform
    private val chrome = LinearGradient(0f, -24f, 0f, 16f, intArrayOf(0xFFF4F6FA.toInt(), 0xFFB9C1CC.toInt(), 0xFF6D7684.toInt(), 0xFFA9B2BF.toInt()), floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
    private val tube = LinearGradient(0f, -9f, 0f, 9f, intArrayOf(0xFF7D8694.toInt(), 0xFFF4F6FA.toInt(), 0xFF8A93A0.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    private val glass = LinearGradient(-50f, 0f, 50f, 0f, intArrayOf(0xFF2E7A6C.toInt(), 0xFF7CCDB4.toInt(), 0xFF9BE0C7.toInt(), 0xFF4FA08C.toInt(), 0xFF1F5A50.toInt()), floatArrayOf(0f, 0.25f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP)
    private val iron = RadialGradient(-50f, -20f, 190f, intArrayOf(0xFF6B6474.toInt(), 0xFF3A3441.toInt(), 0xFF1C1822.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

    class FizzState {
        var ang = 1.5708f
        var t = 0f
        var beat = 0f          // 0..1 within the beat
        var fireT = 9f         // seconds since the last shot
        var mood = 0           // 0 idle, 1 happy, 2 worried, 3 sad, 4 dancing
        var moodT = 0f
        var swapT = 9f
        var gazeX = 0f; var gazeY = -1f
        var blink = false
        var pressing = 0f
    }

    /** Fizz is drawn this much bigger than his layout units. */
    const val FS = 1.42f

    /** Where Fizz's left glove holds the next bubble (relative to the pivot, board units). */
    fun gloveX(st: FizzState): Float = (-112f + sin(st.t * 2.2f) * 3f) * FS
    fun gloveY(st: FizzState): Float = (50f + abs(sin(st.beat * PI_F)) * -4f) * FS

    fun fizz(c: Canvas, st: FizzState) {
        c.save(); c.scale(FS, FS)
        fizzBody(c, st)
        c.restore()
    }

    private fun fizzBody(c: Canvas, st: FizzState) {
        val bounce = abs(sin(st.beat * PI_F))
        val danceAmp = when (st.mood) { 1, 4 -> 1f; 3 -> 0.15f; else -> 0.4f }
        val fireSq = if (st.fireT < 0.35f) (1f - st.fireT / 0.35f) * 0.09f else 0f
        val sad = if (st.mood == 3) min(1f, st.moodT * 2f) else 0f
        // floor shadow
        f.shader = null; f.color = withAlpha(INK, 70)
        rf.set(-78f, 132f, 78f, 152f); c.drawOval(rf, f)
        c.save()
        val sway = sin(st.beat * TAU) * 2.4f * danceAmp + (if (st.mood == 4) sin(st.t * 9f) * 6f else 0f)
        c.rotate(sway, 0f, 140f)
        val sy = 1f - fireSq - 0.03f * bounce * danceAmp - sad * 0.12f
        val sx = 1f + fireSq * 0.7f + 0.02f * bounce * danceAmp + sad * 0.06f
        c.scale(sx, sy, 0f, 140f)
        val hop = if (st.mood == 1 || st.mood == 4) -bounce * 10f else 0f
        c.translate(0f, hop)

        legs(c, st, bounce)
        armRight(c, st)
        bottle(c, st, sad)
        spout(c, st.ang)
        head(c, st)
        face(c, st, sad)
        armLeft(c, st)
        c.restore()
    }

    private fun hose(c: Canvas, x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, w: Float) {
        p2.reset(); p2.moveTo(x0, y0); p2.quadTo(cx, cy, x1, y1)
        s.shader = null; s.color = INK; s.strokeWidth = w
        c.drawPath(p2, s)
    }

    private fun glove(c: Canvas, x: Float, y: Float, rot: Float, open: Boolean, scale: Float = 1f) {
        c.save(); c.translate(x, y); c.rotate(rot); c.scale(scale, scale)
        f.shader = null
        // cuff
        f.color = INK; rf.set(-12f, 8f, 12f, 20f); c.drawRoundRect(rf, 5f, 5f, f)
        f.color = GLOVE; rf.set(-10f, 10f, 10f, 18f); c.drawRoundRect(rf, 4f, 4f, f)
        // palm and fingers
        f.color = INK; rf.set(-15f, -14f, 15f, 13f); c.drawOval(rf, f)
        if (open) for (k in 0 until 4) { val a = -2.4f + k * 0.42f; rf.set(cos(a) * 14f - 6.5f, sin(a) * 14f - 6.5f, cos(a) * 14f + 6.5f, sin(a) * 14f + 6.5f); c.drawOval(rf, f) }
        f.color = GLOVE; rf.set(-12.5f, -11.5f, 12.5f, 10.5f); c.drawOval(rf, f)
        if (open) for (k in 0 until 4) { val a = -2.4f + k * 0.42f; rf.set(cos(a) * 14f - 4.5f, sin(a) * 14f - 4.5f, cos(a) * 14f + 4.5f, sin(a) * 14f + 4.5f); c.drawOval(rf, f) }
        s.color = INK; s.strokeWidth = 1.8f
        c.drawLine(-4f, -2f, -4f, 6f, s); c.drawLine(3f, -2f, 3f, 6f, s)
        c.restore()
    }

    private fun shoe(c: Canvas, x: Float, y: Float, rot: Float, flip: Float) {
        c.save(); c.translate(x, y); c.rotate(rot); c.scale(flip, 1f)
        f.shader = null
        f.color = INK; rf.set(-16f, -12f, 30f, 10f); c.drawOval(rf, f)
        f.color = 0xFF7A3F22.toInt(); rf.set(-13.5f, -9.5f, 27.5f, 7.5f); c.drawOval(rf, f)
        f.color = 0x60FFFFFF; rf.set(4f, -7f, 20f, -2f); c.drawOval(rf, f)
        c.restore()
    }

    private fun legs(c: Canvas, st: FizzState, bounce: Float) {
        val tap = max(0f, sin(st.beat * TAU)) * (if (st.mood == 3) 0f else 1f)
        hose(c, -20f, 100f, -26f, 122f, -36f, 134f, 9f)
        hose(c, 20f, 100f, 28f, 120f - tap * 6f, 38f, 132f - tap * 7f, 9f)
        shoe(c, -40f, 136f, 0f, -1f)
        shoe(c, 42f, 134f - tap * 7f, -tap * 18f, 1f)
    }

    private fun bottle(c: Canvas, st: FizzState, sad: Float) {
        // body outline: neck, shoulders, belly, flat base
        p.reset()
        p.moveTo(-20f, 12f); p.lineTo(-20f, 26f)
        p.cubicTo(-22f, 34f, -48f, 36f, -48f, 52f)
        p.lineTo(-48f, 100f)
        p.quadTo(-48f, 112f, -36f, 112f)
        p.lineTo(36f, 112f)
        p.quadTo(48f, 112f, 48f, 100f)
        p.lineTo(48f, 52f)
        p.cubicTo(48f, 36f, 22f, 34f, 20f, 26f)
        p.lineTo(20f, 12f)
        p.close()
        s.shader = null; s.color = INK; s.strokeWidth = 9f
        c.drawPath(p, s)
        f.alpha = 255; f.shader = glass; c.drawPath(p, f); f.shader = null
        // liquid line and fizz
        c.save(); c.clipPath(p)
        val level = 46f + sad * 30f
        f.color = withAlpha(0xFFDDF8EC.toInt(), 70); c.drawRect(-50f, level, 50f, 115f, f)
        s.color = withAlpha(0xFFFFFFFF.toInt(), 150); s.strokeWidth = 2.2f
        c.drawLine(-48f, level, 48f, level, s)
        for (k in 0 until 9) {
            val ph = (st.t * (0.35f + hash01(k, 3) * 0.3f) + hash01(k, 9)) % 1f
            val x = -36f + hash01(k, 1) * 72f + sin(st.t * 3f + k) * 2f
            val y = lerp(110f, level + 4f, ph)
            val r = 1.6f + hash01(k, 5) * 2.4f
            s.strokeWidth = 1.4f; s.color = withAlpha(0xFFFFFFFF.toInt(), (200 * (1f - ph * 0.6f)).toInt())
            c.drawCircle(x, y, r, s)
        }
        // vertical glints
        f.color = withAlpha(0xFFFFFFFF.toInt(), 120); rf.set(-36f, 44f, -28f, 104f); c.drawRoundRect(rf, 4f, 4f, f)
        f.color = withAlpha(0xFFFFFFFF.toInt(), 60); rf.set(28f, 54f, 33f, 100f); c.drawRoundRect(rf, 3f, 3f, f)
        c.restore()
        // label
        f.color = INK; rf.set(-49f, 78f, 49f, 100f); c.drawRect(rf, f)
        f.color = Pal.CREAM; rf.set(-46f, 81f, 46f, 97f); c.drawRect(rf, f)
        f.color = Pal.VELVET; c.drawRect(-46f, 81f, 46f, 83.5f, f); c.drawRect(-46f, 94.5f, 46f, 97f, f)
        Draw.text(c, "FIZZ", 0f, 89f, 12f, Pal.VELVET, Fonts.chunky, spacing = 0.12f)
    }

    private fun spout(c: Canvas, ang: Float) {
        c.save()
        c.rotate(-ang * 180f / PI_F)
        // tube along +x
        f.color = INK; rf.set(-4f, -11f, 44f, 11f); c.drawRoundRect(rf, 5f, 5f, f)
        f.alpha = 255; f.shader = tube; rf.set(-2f, -8.5f, 41f, 8.5f); c.drawRoundRect(rf, 4f, 4f, f); f.shader = null
        f.color = INK; rf.set(34f, -14f, 46f, 14f); c.drawRoundRect(rf, 4f, 4f, f)
        f.color = 0xFFC7CED8.toInt(); rf.set(36f, -11.5f, 44f, 11.5f); c.drawRoundRect(rf, 3f, 3f, f)
        c.restore()
    }

    private fun head(c: Canvas, st: FizzState) {
        val press = if (st.fireT < 0.25f) 1f - st.fireT / 0.25f else st.pressing
        // lever
        c.save(); c.rotate(-10f + press * 16f, -6f, -18f)
        f.color = INK; rf.set(-10f, -26f, 40f, -14f); c.drawRoundRect(rf, 6f, 6f, f)
        f.alpha = 255; f.shader = chrome; rf.set(-8f, -24f, 38f, -16f); c.drawRoundRect(rf, 4f, 4f, f); f.shader = null
        c.restore()
        // cap block
        f.color = INK; rf.set(-31f, -22f, 31f, 16f); c.drawRoundRect(rf, 12f, 12f, f)
        f.alpha = 255; f.shader = chrome; rf.set(-27.5f, -18.5f, 27.5f, 12.5f); c.drawRoundRect(rf, 9f, 9f, f); f.shader = null
        f.color = 0x90FFFFFF.toInt(); rf.set(-20f, -15f, -6f, -9f); c.drawOval(rf, f)
        f.color = INK; rf.set(-25f, 10f, 25f, 18f); c.drawRoundRect(rf, 3f, 3f, f)
        f.color = Pal.GOLD; rf.set(-22f, 11.5f, 22f, 16f); c.drawRoundRect(rf, 2f, 2f, f)
    }

    private fun face(c: Canvas, st: FizzState, sad: Float) {
        val gx = st.gazeX.coerceIn(-1f, 1f); val gy = st.gazeY.coerceIn(-1f, 1f)
        val ey = 52f
        val happy = st.mood == 1 || st.mood == 4
        for (side in intArrayOf(-1, 1)) {
            val x = side * 15f
            if (st.blink || happy) {
                s.color = INK; s.strokeWidth = 3.4f
                p2.reset()
                if (happy) { p2.moveTo(x - 7f, ey + 3f); p2.quadTo(x, ey - 9f, x + 7f, ey + 3f) } else { p2.moveTo(x - 7f, ey); p2.quadTo(x, ey + 6f, x + 7f, ey) }
                c.drawPath(p2, s)
                continue
            }
            f.color = INK; rf.set(x - 10f, ey - 13f, x + 10f, ey + 13f); c.drawOval(rf, f)
            f.color = 0xFFFFFBF0.toInt(); rf.set(x - 8f, ey - 11f, x + 8f, ey + 11f); c.drawOval(rf, f)
            val px = x + gx * 3f; val py = ey + gy * 3.5f + 1f + sad * 3f
            c.save(); p.reset(); p.addOval(rf, Path.Direction.CW); c.clipPath(p)
            f.color = INK; rf.set(px - 5.2f, py - 8f, px + 5.2f, py + 8f); c.drawOval(rf, f)
            f.color = 0xFFFFFBF0.toInt()
            p2.reset(); p2.moveTo(px + 0.5f, py - 1.5f); p2.lineTo(px + 6.5f, py - 6f); p2.lineTo(px + 2.4f, py - 10f); p2.close(); c.drawPath(p2, f)
            if (sad > 0f) { f.color = 0xFF5FA592.toInt(); c.drawRect(x - 9f, ey - 12f, x + 9f, ey - 12f + sad * 9f, f) }
            c.restore()
        }
        s.color = INK; s.strokeWidth = 3f
        if (st.mood == 2) { // worried brows + sweat
            c.drawLine(-22f, 34f, -10f, 37f, s); c.drawLine(22f, 34f, 10f, 37f, s)
            val sw = (st.t * 1.4f) % 1f
            f.color = 0xFFBFE8FF.toInt(); val dx = 36f; val dy = 40f + sw * 18f
            p2.reset(); p2.moveTo(dx, dy - 8f); p2.quadTo(dx + 6f, dy, dx, dy + 4f); p2.quadTo(dx - 6f, dy, dx, dy - 8f); p2.close()
            c.drawPath(p2, f); s.strokeWidth = 1.6f; c.drawPath(p2, s)
        }
        // mouth
        s.strokeWidth = 3.2f
        when {
            sad > 0f -> { p2.reset(); p2.moveTo(-11f, 76f); p2.quadTo(0f, 67f, 11f, 76f); c.drawPath(p2, s) }
            st.fireT < 0.3f -> { f.color = INK; rf.set(-7f, 64f, 7f, 76f); c.drawOval(rf, f) }
            else -> {
                p2.reset(); p2.moveTo(-16f, 66f); p2.quadTo(0f, 70f, 16f, 66f); p2.quadTo(12f, 80f, 0f, 80f); p2.quadTo(-12f, 80f, -16f, 66f); p2.close()
                f.color = INK; c.drawPath(p2, f)
                f.color = 0xFFE86A6A.toInt(); rf.set(-6f, 72f, 6f, 79f); c.drawOval(rf, f)
            }
        }
    }

    private fun armRight(c: Canvas, st: FizzState) {
        val press = if (st.fireT < 0.25f) 1f - st.fireT / 0.25f else 0f
        val hx = 30f; val hy = -30f + press * 6f
        hose(c, 44f, 50f, 78f, 20f, hx + 4f, hy + 10f, 8f)
        glove(c, hx, hy, 160f + press * 10f, false, 0.9f)
    }

    private fun armLeft(c: Canvas, st: FizzState) {
        val gx = gloveX(st) / FS; val gy = gloveY(st) / FS + 30f
        val swing = if (st.swapT < 0.4f) sin(st.swapT / 0.4f * PI_F) * 30f else 0f
        hose(c, -44f, 54f, -76f, 80f + swing * 0.3f, gx + 6f, gy - 4f - swing * 0.5f, 8f)
        glove(c, gx, gy - swing * 0.5f, 0f, true, 0.95f)
    }

    // ------------------------------------------------------------------ the Baron

    class BaronState {
        var t = 0f
        var beat = 0f
        var hurtT = 9f       // since last hit
        var angry = false
        var throwT = 9f      // since last bubble throw
        var hat = 0
        var dead = 0f        // 0..1 defeat spin
        var lookX = 0f; var lookY = 1f
    }

    fun baron(c: Canvas, st: BaronState) {
        val hurt = if (st.hurtT < 0.5f) 1f - st.hurtT / 0.5f else 0f
        val bob = sin(st.beat * TAU) * 4f
        c.save()
        c.translate(0f, bob)
        if (st.dead > 0f) {
            c.translate(0f, -st.dead * st.dead * 900f)
            c.rotate(st.dead * 720f)
        }
        val sq = 1f + hurt * 0.12f * cos(st.hurtT * 40f)
        c.scale(sq, 2f - sq, 0f, 60f)
        // arms behind the body
        val wave = sin(st.t * (if (st.angry) 9f else 4f))
        val thr = if (st.throwT < 0.5f) sin(st.throwT / 0.5f * PI_F) else 0f
        hose(c, -118f, 40f, -168f, 20f + wave * 10f, -170f, -20f - thr * 30f + wave * 8f, 10f)
        glove(c, -172f, -30f - thr * 30f + wave * 8f, 200f + wave * 15f, thr > 0.2f, 1.25f)
        hose(c, 118f, 40f, 166f, 70f, 176f, 20f + wave * 6f, 10f)
        glove(c, 180f, 12f + wave * 6f, -60f + wave * 10f, false, 1.25f)
        // handle and spout
        s.shader = null; s.color = INK; s.strokeWidth = 22f
        rf.set(-150f, -40f, -86f, 90f); c.drawArc(rf, 100f, 160f, false, s)
        s.color = 0xFF2B2630.toInt(); s.strokeWidth = 12f; c.drawArc(rf, 104f, 152f, false, s)
        p.reset(); p.moveTo(104f, 10f); p.quadTo(150f, 20f, 170f, -38f); p.lineTo(184f, -30f); p.quadTo(166f, 48f, 108f, 62f); p.close()
        f.color = INK; c.save(); c.scale(1.08f, 1.08f, 140f, 10f); c.drawPath(p, f); c.restore()
        f.alpha = 255; f.shader = iron; c.drawPath(p, f); f.shader = null
        // body
        p.reset()
        p.moveTo(-132f, 40f)
        p.cubicTo(-140f, -40f, -70f, -64f, 0f, -64f)
        p.cubicTo(70f, -64f, 140f, -40f, 132f, 40f)
        p.cubicTo(128f, 110f, 90f, 132f, 0f, 132f)
        p.cubicTo(-90f, 132f, -128f, 110f, -132f, 40f)
        p.close()
        s.color = INK; s.strokeWidth = 14f; c.drawPath(p, s)
        f.alpha = 255; f.shader = iron; c.drawPath(p, f); f.shader = null
        c.save(); c.clipPath(p)
        // copper band with rivets
        f.color = INK; c.drawRect(-140f, 94f, 140f, 120f, f)
        f.color = 0xFFC0703A.toInt(); c.drawRect(-140f, 97f, 140f, 117f, f)
        f.color = 0xFFE9A266.toInt(); c.drawRect(-140f, 99f, 140f, 103f, f)
        for (k in -5..5) { f.color = INK; c.drawCircle(k * 24f, 107f, 3.6f, f); f.color = 0xFFF2C79A.toInt(); c.drawCircle(k * 24f - 1f, 106f, 1.6f, f) }
        // shine
        f.color = 0x50FFFFFF; rf.set(-104f, -46f, -40f, -20f); c.drawOval(rf, f)
        if (st.angry) { f.color = withAlpha(0xFFFF3020.toInt(), (60 + 40 * sin(st.t * 12f)).toInt()); c.drawRect(-140f, -70f, 140f, 140f, f) }
        if (hurt > 0f) { f.color = withAlpha(0xFFFFFFFF.toInt(), (190 * hurt).toInt()); c.drawRect(-140f, -70f, 140f, 140f, f) }
        c.restore()
        // lid and knob (rattles when angry)
        val rattle = if (st.angry) sin(st.t * 31f) * 4f else 0f
        val lift = if (hurt > 0.5f) -18f * hurt else 0f
        c.save(); c.translate(0f, lift); c.rotate(rattle)
        f.color = INK; rf.set(-82f, -86f, 82f, -46f); c.drawOval(rf, f)
        f.alpha = 255; f.shader = iron; rf.set(-76f, -81f, 76f, -51f); c.drawOval(rf, f); f.shader = null
        f.color = INK; c.drawCircle(0f, -94f, 17f, f)
        f.color = 0xFFC0703A.toInt(); c.drawCircle(0f, -94f, 12f, f)
        f.color = 0xFFF2C79A.toInt(); c.drawCircle(-4f, -98f, 4f, f)
        hat(c, st.hat, st.t)
        c.restore()
        baronFace(c, st, hurt)
        if (st.dead > 0f) {
            for (k in 0 until 5) {
                val a = st.t * 5f + k * TAU / 5f
                Draw.star(p2, cos(a) * 90f, -130f + sin(a) * 22f, 14f)
                f.color = INK; c.drawPath(p2, f)
                Draw.star(p2, cos(a) * 90f, -130f + sin(a) * 22f, 10f); f.color = Pal.GOLD; c.drawPath(p2, f)
            }
        }
        c.restore()
    }

    private fun baronFace(c: Canvas, st: BaronState, hurt: Float) {
        val lx = st.lookX.coerceIn(-1f, 1f) * 6f; val ly = st.lookY.coerceIn(-1f, 1f) * 6f
        for (side in intArrayOf(-1, 1)) {
            val x = side * 44f; val y = 6f
            if (hurt > 0.3f || st.dead > 0f) {
                s.color = 0xFFFFFBF0.toInt(); s.strokeWidth = 6f
                c.drawLine(x - 12f, y - 12f, x + 12f, y + 12f, s); c.drawLine(x + 12f, y - 12f, x - 12f, y + 12f, s)
                continue
            }
            f.color = INK; rf.set(x - 22f, y - 24f, x + 22f, y + 24f); c.drawOval(rf, f)
            f.color = 0xFFFFF3D6.toInt(); rf.set(x - 18f, y - 20f, x + 18f, y + 20f); c.drawOval(rf, f)
            val px = x + lx; val py = y + ly + 3f
            c.save(); p2.reset(); p2.addOval(rf, Path.Direction.CW); c.clipPath(p2)
            f.color = INK; rf.set(px - 10f, py - 15f, px + 10f, py + 15f); c.drawOval(rf, f)
            f.color = 0xFFFFF3D6.toInt(); p2.reset(); p2.moveTo(px + 1f, py - 3f); p2.lineTo(px + 12f, py - 11f); p2.lineTo(px + 4f, py - 19f); p2.close(); c.drawPath(p2, f)
            // scowling lid
            f.color = 0xFF2B2630.toInt()
            p2.reset(); p2.moveTo(x - 24f, y - 26f); p2.lineTo(x + 24f, y - 26f)
            if (side < 0) p2.lineTo(x + 24f, y - 6f) else p2.lineTo(x - 24f, y - 6f)
            p2.close(); c.drawPath(p2, f)
            c.restore()
            s.color = INK; s.strokeWidth = 7f
            if (side < 0) c.drawLine(x - 24f, y - 28f, x + 22f, y - 10f, s) else c.drawLine(x + 24f, y - 28f, x - 22f, y - 10f, s)
        }
        // monocle on his right eye
        if (hurt < 0.3f && st.dead <= 0f) {
            s.color = INK; s.strokeWidth = 8f; c.drawCircle(44f, 6f, 27f, s)
            s.color = Pal.GOLD; s.strokeWidth = 4.5f; c.drawCircle(44f, 6f, 27f, s)
            f.color = 0x30DDEEFF; c.drawCircle(44f, 6f, 25f, f)
            s.color = Pal.GOLD; s.strokeWidth = 2.4f
            p2.reset(); p2.moveTo(68f, 18f); p2.quadTo(92f, 50f, 84f, 86f); c.drawPath(p2, s)
        }
        // copper nose
        f.color = INK; c.drawCircle(0f, 40f, 16f, f)
        f.color = 0xFFC0703A.toInt(); c.drawCircle(0f, 40f, 12.5f, f)
        f.color = 0xFFF2C79A.toInt(); c.drawCircle(-4f, 36f, 4f, f)
        // sneer
        p2.reset(); p2.moveTo(-34f, 72f); p2.quadTo(0f, 64f, 34f, 70f); p2.quadTo(20f, 92f, 0f, 90f); p2.quadTo(-22f, 90f, -34f, 72f); p2.close()
        f.color = INK; c.drawPath(p2, f)
        f.color = 0xFFFFF3D6.toInt(); c.drawRect(-24f, 71f, 24f, 77f, f)
        s.color = INK; s.strokeWidth = 1.6f; for (k in -2..2) c.drawLine(k * 9f, 71f, k * 9f, 77f, s)
        // handlebar mustache
        val tw = sin(st.t * 3f) * 3f
        c.save(); c.scale(1.35f, 1.35f, 0f, 54f)
        p2.reset()
        p2.moveTo(0f, 50f)
        p2.cubicTo(-20f, 46f, -40f, 50f, -54f, 64f)
        p2.cubicTo(-64f, 74f, -80f, 66f, -76f, 52f + tw)
        p2.cubicTo(-80f, 62f, -70f, 66f, -62f, 58f)
        p2.cubicTo(-46f, 70f, -16f, 66f, 0f, 60f)
        p2.cubicTo(16f, 66f, 46f, 70f, 62f, 58f)
        p2.cubicTo(70f, 66f, 80f, 62f, 76f, 52f - tw)
        p2.cubicTo(80f, 66f, 64f, 74f, 54f, 64f)
        p2.cubicTo(40f, 50f, 20f, 46f, 0f, 50f)
        p2.close()
        f.color = INK; c.drawPath(p2, f)
        f.color = 0x30FFFFFF; rf.set(-40f, 52f, -16f, 57f); c.drawOval(rf, f)
        c.restore()
    }

    private fun hat(c: Canvas, kind: Int, t: Float) {
        c.save(); c.translate(0f, -92f)
        f.shader = null
        fun ink(): Unit { f.color = INK }
        when (kind) {
            0 -> { // straw hat
                ink(); rf.set(-78f, -16f, 78f, 10f); c.drawOval(rf, f)
                f.color = 0xFFE9C46A.toInt(); rf.set(-74f, -13f, 74f, 7f); c.drawOval(rf, f)
                ink(); rf.set(-40f, -52f, 40f, -2f); c.drawRoundRect(rf, 18f, 18f, f)
                f.color = 0xFFE9C46A.toInt(); rf.set(-36f, -48f, 36f, -4f); c.drawRoundRect(rf, 15f, 15f, f)
                f.color = Pal.VELVET; c.drawRect(-36f, -16f, 36f, -6f, f)
                s.color = 0xFFB98E3A.toInt(); s.strokeWidth = 1.5f; for (k in -3..3) c.drawLine(k * 10f, -46f, k * 10f + 3f, -18f, s)
            }
            1 -> topHat(c, Pal.VELVET, Pal.GOLD, 74f)
            2 -> { topHat(c, 0xFF2A2233.toInt(), 0xFF7A4CA0.toInt(), 86f, crooked = true)
                // a little bat on the brim
                val fl = sin(t * 14f) * 6f
                f.color = INK; p2.reset(); p2.moveTo(30f, -10f); p2.lineTo(50f, -26f + fl); p2.lineTo(44f, -16f); p2.lineTo(58f, -18f + fl); p2.lineTo(40f, -6f); p2.close(); c.drawPath(p2, f)
                c.drawCircle(36f, -9f, 6f, f) }
            3 -> { // tricorn
                ink(); p2.reset(); p2.moveTo(-86f, 0f); p2.quadTo(0f, -70f, 86f, 0f); p2.quadTo(0f, -20f, -86f, 0f); p2.close(); c.drawPath(p2, f)
                f.color = 0xFF2A2233.toInt(); c.save(); c.scale(0.92f, 0.86f, 0f, -10f); c.drawPath(p2, f); c.restore()
                s.color = Pal.GOLD; s.strokeWidth = 3.5f; p2.reset(); p2.moveTo(-78f, -4f); p2.quadTo(0f, -62f, 78f, -4f); c.drawPath(p2, s)
                f.color = Pal.CREAM; p2.reset(); p2.moveTo(20f, -36f); p2.quadTo(60f, -80f, 82f, -60f); p2.quadTo(56f, -56f, 26f, -30f); p2.close(); c.drawPath(p2, f)
                s.color = INK; s.strokeWidth = 2f; c.drawPath(p2, s)
            }
            4 -> { // toy soldier shako
                ink(); rf.set(-38f, -80f, 38f, 4f); c.drawRoundRect(rf, 6f, 6f, f)
                f.color = Pal.VELVET; rf.set(-34f, -76f, 34f, 0f); c.drawRoundRect(rf, 4f, 4f, f)
                f.color = Pal.GOLD; c.drawRect(-34f, -10f, 34f, 0f, f); c.drawCircle(0f, -40f, 12f, f)
                ink(); rf.set(-46f, -4f, 46f, 8f); c.drawRoundRect(rf, 4f, 4f, f)
                f.color = 0xFFFFFBF0.toInt(); rf.set(-10f, -112f, 10f, -74f); c.drawOval(rf, f)
                s.color = INK; s.strokeWidth = 3f; c.drawOval(rf, s)
            }
            5 -> { // fedora
                ink(); rf.set(-82f, -14f, 82f, 10f); c.drawOval(rf, f)
                f.color = 0xFF6E6A66.toInt(); rf.set(-78f, -11f, 78f, 7f); c.drawOval(rf, f)
                ink(); p2.reset(); p2.moveTo(-46f, -2f); p2.quadTo(-50f, -56f, -6f, -50f); p2.lineTo(0f, -40f); p2.lineTo(6f, -50f); p2.quadTo(50f, -56f, 46f, -2f); p2.close(); c.drawPath(p2, f)
                f.color = 0xFF6E6A66.toInt(); c.save(); c.scale(0.9f, 0.9f, 0f, -4f); c.drawPath(p2, f); c.restore()
                f.color = INK; c.drawRect(-44f, -18f, 44f, -6f, f)
            }
            6 -> { // space bowl helmet with antenna
                s.color = INK; s.strokeWidth = 4f; c.drawLine(0f, -40f, 0f, -96f, s)
                f.color = if ((t * 3f).toInt() % 2 == 0) 0xFFFF5040.toInt() else 0xFFFFD060.toInt(); c.drawCircle(0f, -100f, 9f, f)
                s.strokeWidth = 3f; c.drawCircle(0f, -100f, 9f, s)
                f.color = 0x40C8E8FF; rf.set(-96f, -60f, 96f, 120f); c.drawOval(rf, f)
                s.color = withAlpha(INK, 200); s.strokeWidth = 5f; c.drawOval(rf, s)
                f.color = 0x80FFFFFF.toInt(); rf.set(-70f, -46f, -30f, -24f); c.drawOval(rf, f)
            }
            7 -> { // bobble beanie
                ink(); rf.set(-52f, -60f, 52f, 8f); c.drawOval(rf, f)
                f.color = 0xFF2F6FB0.toInt(); rf.set(-48f, -56f, 48f, 4f); c.drawOval(rf, f)
                f.color = 0xFFF5E8C8.toInt(); c.drawRect(-48f, -30f, 48f, -18f, f)
                ink(); rf.set(-54f, -8f, 54f, 10f); c.drawRoundRect(rf, 8f, 8f, f)
                f.color = 0xFFE04A3B.toInt(); rf.set(-50f, -5f, 50f, 7f); c.drawRoundRect(rf, 6f, 6f, f)
                ink(); c.drawCircle(0f, -62f, 16f, f); f.color = 0xFFFFFBF0.toInt(); c.drawCircle(0f, -62f, 13f, f)
            }
            8 -> { // pith helmet
                ink(); rf.set(-84f, -14f, 84f, 12f); c.drawOval(rf, f)
                f.color = 0xFFD8C08A.toInt(); rf.set(-80f, -11f, 80f, 9f); c.drawOval(rf, f)
                ink(); rf.set(-54f, -66f, 54f, 30f); c.drawArc(rf, 180f, 180f, true, f)
                f.color = 0xFFE3CE98.toInt(); rf.set(-50f, -62f, 50f, 26f); c.drawArc(rf, 180f, 180f, true, f)
                f.color = 0xFF9A7B45.toInt(); c.drawRect(-50f, -16f, 50f, -6f, f)
            }
            else -> { // crown
                ink(); p2.reset(); p2.moveTo(-58f, 4f); p2.lineTo(-62f, -54f); p2.lineTo(-32f, -28f); p2.lineTo(0f, -70f); p2.lineTo(32f, -28f); p2.lineTo(62f, -54f); p2.lineTo(58f, 4f); p2.close(); c.drawPath(p2, f)
                f.color = Pal.GOLD; c.save(); c.scale(0.88f, 0.86f, 0f, -4f); c.drawPath(p2, f); c.restore()
                f.color = Pal.GOLD_DK; c.drawRect(-52f, -10f, 52f, 0f, f)
                for ((k, col) in intArrayOf(Pal.BUB[0], Pal.BUB[3], Pal.BUB[5]).withIndex()) { f.color = INK; c.drawCircle(-30f + k * 30f, -18f, 8f, f); f.color = col; c.drawCircle(-30f + k * 30f, -18f, 5.5f, f) }
            }
        }
        c.restore()
    }

    private fun topHat(c: Canvas, col: Int, band: Int, h: Float, crooked: Boolean = false) {
        f.color = INK; rf.set(-70f, -12f, 70f, 10f); c.drawOval(rf, f)
        f.color = col; rf.set(-66f, -9f, 66f, 7f); c.drawOval(rf, f)
        c.save(); if (crooked) c.rotate(8f, 0f, 0f)
        f.color = INK; rf.set(-42f, -h, 42f, 0f); c.drawRect(rf, f)
        f.color = col; rf.set(-38f, -h + 4f, 38f, -2f); c.drawRect(rf, f)
        f.color = band; c.drawRect(-38f, -22f, 38f, -8f, f)
        f.color = 0x30FFFFFF; c.drawRect(-30f, -h + 8f, -18f, -24f, f)
        f.color = INK; rf.set(-44f, -h - 6f, 44f, -h + 6f); c.drawOval(rf, f)
        f.color = scaleRgb(col, 1.2f); rf.set(-40f, -h - 3f, 40f, -h + 3f); c.drawOval(rf, f)
        c.restore()
    }
}
