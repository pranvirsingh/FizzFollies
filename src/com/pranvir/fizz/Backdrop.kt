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

/**
 * Painted scenery flats for the ten reels, in the soft gouache style of 1930s cartoon backgrounds (no ink lines,
 * so the inked characters pop in front). [paint] makes the static flat; [live] adds the few things that dance.
 */
object Backdrop {
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val p = Path()
    private val rf = RectF()

    /** Horizon (world y) for a layout: the lower half of the board, behind Fizz. */
    private fun horizon(L: Layout) = L.Y(Board.DEAD_Y * 0.62f)

    fun paint(c: Canvas, L: Layout, reel: Int) {
        when (reel) {
            0 -> barn(c, L); 1 -> circus(c, L); 2 -> spooky(c, L); 3 -> sea(c, L); 4 -> toyland(c, L)
            5 -> city(c, L); 6 -> moon(c, L); 7 -> winter(c, L); 8 -> jungle(c, L); else -> boiler(c, L)
        }
        paper(c, L, reel)
    }

    // ------------------------------------------------------------------ helpers

    private fun sky(c: Canvas, L: Layout, top: Int, mid: Int, bot: Int) {
        f.alpha = 255; f.shader = LinearGradient(0f, 0f, 0f, L.vh, intArrayOf(top, mid, bot), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1000f, L.vh, f); f.shader = null
    }

    private fun hills(c: Canvas, base: Float, amp: Float, freq: Float, ph: Float, col: Int, bottom: Float, light: Int = 0) {
        p.reset(); p.moveTo(-10f, bottom)
        var x = -10f
        while (x <= 1010f) {
            val y = base - amp * (0.6f * sin(x * freq + ph) + 0.4f * sin(x * freq * 2.3f + ph * 1.7f))
            p.lineTo(x, y); x += 10f
        }
        p.lineTo(1010f, bottom); p.close()
        f.color = col; c.drawPath(p, f)
        if (light != 0) {
            // soft rim of light along the crest
            s.color = light; s.strokeWidth = 6f
            p.reset(); x = -10f
            while (x <= 1010f) {
                val y = base - amp * (0.6f * sin(x * freq + ph) + 0.4f * sin(x * freq * 2.3f + ph * 1.7f)) + 4f
                if (x == -10f) p.moveTo(x, y) else p.lineTo(x, y); x += 10f
            }
            c.drawPath(p, s)
        }
    }

    private fun cloud(c: Canvas, x: Float, y: Float, sz: Float, col: Int, shade: Int) {
        f.color = shade
        for (k in 0 until 5) { val cx = x + (k - 2) * sz * 0.42f; val r = sz * (0.32f + 0.14f * (2 - abs(k - 2))); c.drawCircle(cx, y + 6f, r, f) }
        f.color = col
        for (k in 0 until 5) { val cx = x + (k - 2) * sz * 0.42f; val r = sz * (0.32f + 0.14f * (2 - abs(k - 2))); c.drawCircle(cx, y, r, f) }
        c.drawRect(x - sz * 0.95f, y, x + sz * 0.95f, y + sz * 0.22f, f)
    }

    private fun paper(c: Canvas, L: Layout, reel: Int) {
        // painted-on-board texture: soft blotches and fine specks
        val r = Rng(500L + reel)
        for (q in 0 until 160) {
            val x = r.nextFloat() * 1000f; val y = r.nextFloat() * L.vh
            f.color = if (r.chance(0.5f)) 0x07000000 else 0x05FFFFFF
            c.drawCircle(x, y, 20f + r.nextFloat() * 70f, f)
        }
        for (q in 0 until 900) {
            val x = r.nextFloat() * 1000f; val y = r.nextFloat() * L.vh
            f.color = if (r.chance(0.6f)) 0x16000000 else 0x14FFFFFF
            c.drawCircle(x, y, 0.8f + r.nextFloat() * 1.4f, f)
        }
    }

    private fun pine(c: Canvas, x: Float, y: Float, h: Float, col: Int, snow: Boolean) {
        f.color = scaleRgb(col, 0.7f); c.drawRect(x - h * 0.05f, y - h * 0.15f, x + h * 0.05f, y, f)
        for (k in 0 until 3) {
            val ty = y - h * 0.15f - k * h * 0.26f
            val w = h * (0.42f - k * 0.1f)
            p.reset(); p.moveTo(x - w, ty); p.lineTo(x, ty - h * 0.42f); p.lineTo(x + w, ty); p.close()
            f.color = col; c.drawPath(p, f)
            if (snow) { f.color = 0xFFF2F6FA.toInt(); p.reset(); p.moveTo(x - w * 0.5f, ty - h * 0.2f); p.lineTo(x, ty - h * 0.42f); p.lineTo(x + w * 0.5f, ty - h * 0.2f); p.quadTo(x, ty - h * 0.26f, x - w * 0.5f, ty - h * 0.2f); c.drawPath(p, f) }
        }
    }

    private fun palm(c: Canvas, x: Float, y: Float, h: Float, lean: Float, col: Int, trunk: Int) {
        s.color = trunk; s.strokeWidth = h * 0.07f
        p.reset(); p.moveTo(x, y); p.quadTo(x + lean * 0.3f, y - h * 0.5f, x + lean, y - h); c.drawPath(p, s)
        val tx = x + lean; val ty = y - h
        f.color = col
        for (k in 0 until 6) {
            val a = -2.9f + k * 0.55f
            val ex = tx + cos(a) * h * 0.55f; val ey = ty + sin(a) * h * 0.3f + h * 0.18f
            p.reset(); p.moveTo(tx, ty)
            p.quadTo(tx + cos(a) * h * 0.3f, ty + sin(a) * h * 0.4f - h * 0.12f, ex, ey)
            p.quadTo(tx + cos(a) * h * 0.25f, ty + sin(a) * h * 0.2f, tx, ty)
            c.drawPath(p, f)
        }
    }

    // ------------------------------------------------------------------ the ten flats

    private fun barn(c: Canvas, L: Layout) {
        sky(c, L, 0xFFB9D8D2.toInt(), 0xFFF2E4BA.toInt(), 0xFFF0D9A0.toInt())
        val h = horizon(L)
        cloud(c, 220f, L.Y(260f), 130f, 0xFFFBF3DC.toInt(), 0xFFE2D6B6.toInt())
        cloud(c, 760f, L.Y(420f), 100f, 0xFFFBF3DC.toInt(), 0xFFE2D6B6.toInt())
        cloud(c, 520f, L.Y(140f), 80f, 0xFFFBF3DC.toInt(), 0xFFE2D6B6.toInt())
        hills(c, h - 40f, 50f, 0.006f, 1f, 0xFFA9BF86.toInt(), L.vh, 0x40FFFFFF)
        // barn on the far hill
        val bx = 720f; val by = h - 50f
        f.color = 0xFF9E4430.toInt(); c.drawRect(bx - 80f, by - 110f, bx + 80f, by + 10f, f)
        p.reset(); p.moveTo(bx - 96f, by - 104f); p.lineTo(bx - 50f, by - 170f); p.lineTo(bx + 50f, by - 170f); p.lineTo(bx + 96f, by - 104f); p.close()
        f.color = 0xFF7E3022.toInt(); c.drawPath(p, f)
        f.color = 0xFFF0E2C0.toInt(); c.drawRect(bx - 34f, by - 70f, bx + 34f, by + 10f, f)
        f.color = 0xFF8A3828.toInt(); c.drawRect(bx - 28f, by - 64f, bx + 28f, by + 10f, f)
        s.color = 0xFFF0E2C0.toInt(); s.strokeWidth = 5f
        c.drawLine(bx - 28f, by - 64f, bx + 28f, by + 10f, s); c.drawLine(bx + 28f, by - 64f, bx - 28f, by + 10f, s)
        f.color = 0xFFF0E2C0.toInt(); c.drawRect(bx - 16f, by - 140f, bx + 16f, by - 112f, f)
        // silo
        f.color = 0xFFB9B0A0.toInt(); c.drawRect(bx + 96f, by - 150f, bx + 140f, by + 10f, f)
        rf.set(bx + 96f, by - 172f, bx + 140f, by - 128f); f.color = 0xFF8C8478.toInt(); c.drawArc(rf, 180f, 180f, true, f)
        hills(c, h + 30f, 40f, 0.008f, 3f, 0xFF8FAE62.toInt(), L.vh, 0x40FFFFFF)
        // golden field rows
        hills(c, h + 120f, 26f, 0.005f, 5f, 0xFFD8B25E.toInt(), L.vh)
        s.color = 0x30804A10; s.strokeWidth = 4f
        for (q in 0 until 14) { val y = h + 150f + q * 26f; c.drawLine(0f, y, 1000f, y + 10f * sin(q.toFloat()), s) }
        // fence
        val fy = h + 260f
        s.color = 0xFFEADBC0.toInt(); s.strokeWidth = 7f
        c.drawLine(0f, fy - 30f, 1000f, fy - 40f, s); c.drawLine(0f, fy - 10f, 1000f, fy - 20f, s)
        for (q in 0 until 12) { val x = 20f + q * 90f; c.drawLine(x, fy - 54f - q * 0.8f, x, fy + 8f - q * 0.8f, s) }
        hills(c, h + 360f, 22f, 0.01f, 2f, 0xFF7E9E4E.toInt(), L.vh)
    }

    private fun circus(c: Canvas, L: Layout) {
        sky(c, L, 0xFF22324A.toInt(), 0xFF6A4A62.toInt(), 0xFFC07A60.toInt())
        val h = horizon(L)
        val r = Rng(31L)
        for (q in 0 until 40) { f.color = withAlpha(0xFFFFF2C8.toInt(), 120 + r.nextInt(100)); c.drawCircle(r.nextFloat() * 1000f, L.Y(r.nextFloat() * 700f), 1.5f + r.nextFloat() * 2f, f) }
        hills(c, h + 40f, 20f, 0.006f, 2f, 0xFF5E3E4A.toInt(), L.vh)
        // big top
        val cx = 500f; val base = h + 120f; val top = h - 220f
        for (k in 0 until 10) {
            val x0 = cx - 300f + k * 60f; val x1 = x0 + 60f
            p.reset(); p.moveTo(cx, top); p.lineTo(x0, base - 120f); p.lineTo(x1, base - 120f); p.close()
            f.color = if (k % 2 == 0) 0xFFB8443A.toInt() else 0xFFEEDDBB.toInt(); c.drawPath(p, f)
            f.color = if (k % 2 == 0) 0xFF9C362E.toInt() else 0xFFD8C6A0.toInt(); c.drawRect(x0, base - 120f, x1, base, f)
        }
        // scalloped trim
        for (k in 0 until 10) { rf.set(cx - 300f + k * 60f, base - 140f, cx - 240f + k * 60f, base - 100f); f.color = 0xFF2F5A6A.toInt(); c.drawArc(rf, 0f, 180f, true, f) }
        f.color = 0xFF2A1A20.toInt(); p.reset(); p.moveTo(cx - 40f, base); p.quadTo(cx, base - 110f, cx + 40f, base); p.close(); c.drawPath(p, f)
        s.color = 0xFF5A3A2A.toInt(); s.strokeWidth = 5f; c.drawLine(cx, top, cx, top - 50f, s)
        // ground
        hills(c, base + 30f, 8f, 0.01f, 1f, 0xFFB08A5A.toInt(), L.vh)
        hills(c, base + 180f, 12f, 0.012f, 4f, 0xFF8E6C46.toInt(), L.vh)
        // side tents
        for (side in intArrayOf(-1, 1)) {
            val x = 500f + side * 380f
            p.reset(); p.moveTo(x, h - 20f); p.lineTo(x - 90f, base); p.lineTo(x + 90f, base); p.close()
            f.color = 0xFF7A4E6A.toInt(); c.drawPath(p, f)
            f.color = 0xFF9A6A84.toInt(); p.reset(); p.moveTo(x, h - 20f); p.lineTo(x - 30f, base); p.lineTo(x + 30f, base); p.close(); c.drawPath(p, f)
        }
    }

    private fun spooky(c: Canvas, L: Layout) {
        sky(c, L, 0xFF15122A.toInt(), 0xFF3E3266.toInt(), 0xFF5E4A70.toInt())
        val h = horizon(L)
        // moon (static disc; the face is live)
        f.alpha = 255; f.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x50FFF0C0, 0x00FFF0C0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.save(); c.translate(250f, L.Y(380f)); c.scale(300f, 300f); c.drawCircle(0f, 0f, 1f, f); c.restore(); f.shader = null
        cloud(c, 700f, L.Y(300f), 120f, 0xFF4A3E6E.toInt(), 0xFF352B55.toInt())
        hills(c, h + 20f, 50f, 0.005f, 0.5f, 0xFF2A2240.toInt(), L.vh)
        // crooked manor
        val mx = 690f; val my = h + 10f
        f.color = 0xFF17132A.toInt()
        p.reset(); p.moveTo(mx - 130f, my); p.lineTo(mx - 120f, my - 170f); p.lineTo(mx - 150f, my - 170f); p.lineTo(mx - 60f, my - 280f)
        p.lineTo(mx + 10f, my - 190f); p.lineTo(mx + 20f, my - 330f); p.lineTo(mx + 70f, my - 420f); p.lineTo(mx + 110f, my - 320f); p.lineTo(mx + 100f, my - 160f)
        p.lineTo(mx + 140f, my - 160f); p.lineTo(mx + 120f, my); p.close(); c.drawPath(p, f)
        f.color = 0xFFE8C860.toInt()
        for ((x, y) in listOf(Pair(-90f, -130f), Pair(-40f, -130f), Pair(50f, -270f), Pair(60f, -120f), Pair(-60f, -60f))) { rf.set(mx + x - 10f, my + y - 14f, mx + x + 10f, my + y + 14f); c.drawRect(rf, f) }
        // bare trees
        for ((tx, th) in listOf(Pair(110f, 300f), Pair(260f, 220f), Pair(930f, 260f))) {
            s.color = 0xFF120F22.toInt(); s.strokeWidth = 14f
            c.drawLine(tx, h + 60f, tx + 10f, h + 60f - th, s)
            s.strokeWidth = 6f
            for (k in 0 until 4) { val yy = h + 60f - th * (0.4f + k * 0.15f); val d = if (k % 2 == 0) 1f else -1f; c.drawLine(tx + 8f, yy, tx + d * 70f, yy - 50f, s); c.drawLine(tx + d * 50f, yy - 36f, tx + d * 60f, yy - 80f, s) }
        }
        hills(c, h + 150f, 18f, 0.01f, 2f, 0xFF1E1934.toInt(), L.vh)
        // tombstones
        for (q in 0 until 6) {
            val x = 80f + q * 170f; val y = h + 170f + (q % 2) * 20f
            f.color = 0xFF4A4466.toInt(); rf.set(x - 22f, y - 50f, x + 22f, y); c.drawRoundRect(rf, 20f, 20f, f); c.drawRect(x - 22f, y - 25f, x + 22f, y, f)
            f.color = 0xFF2E2848.toInt(); c.drawRect(x - 3f, y - 40f, x + 3f, y - 14f, f); c.drawRect(x - 10f, y - 34f, x + 10f, y - 28f, f)
        }
    }

    private fun sea(c: Canvas, L: Layout) {
        sky(c, L, 0xFF9CC9D2.toInt(), 0xFFF1E3BE.toInt(), 0xFFF3D9A6.toInt())
        val h = horizon(L)
        cloud(c, 300f, L.Y(300f), 120f, 0xFFFFF8E6.toInt(), 0xFFE0D2B8.toInt())
        cloud(c, 820f, L.Y(200f), 90f, 0xFFFFF8E6.toInt(), 0xFFE0D2B8.toInt())
        // lighthouse on a rock
        f.color = 0xFF7E7A6A.toInt(); p.reset(); p.moveTo(40f, h + 20f); p.quadTo(120f, h - 60f, 230f, h + 20f); p.close(); c.drawPath(p, f)
        for (k in 0 until 5) { f.color = if (k % 2 == 0) 0xFFE8E0CC.toInt() else 0xFFC0463A.toInt(); val y0 = h - 40f - k * 44f; p.reset(); p.moveTo(110f - (34f - k * 3f), y0); p.lineTo(110f - (31f - k * 3f), y0 - 44f); p.lineTo(110f + (31f - k * 3f), y0 - 44f); p.lineTo(110f + (34f - k * 3f), y0); p.close(); c.drawPath(p, f) }
        f.color = 0xFF3A3A44.toInt(); c.drawRect(86f, h - 290f, 134f, h - 260f, f)
        f.color = 0xFFFFE9A0.toInt(); c.drawRect(92f, h - 284f, 128f, h - 266f, f)
        p.reset(); p.moveTo(80f, h - 290f); p.lineTo(110f, h - 320f); p.lineTo(140f, h - 290f); p.close(); f.color = 0xFF3A3A44.toInt(); c.drawPath(p, f)
        // sea
        f.alpha = 255; f.shader = LinearGradient(0f, h, 0f, L.vh, intArrayOf(0xFF5E9CB0.toInt(), 0xFF2F6684.toInt(), 0xFF244E68.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, h, 1000f, L.vh, f); f.shader = null
        // a sailing ship on the horizon
        val sx = 640f; val sy = h + 10f
        f.color = 0xFF6A4A32.toInt(); p.reset(); p.moveTo(sx - 110f, sy - 40f); p.lineTo(sx + 120f, sy - 40f); p.lineTo(sx + 90f, sy); p.lineTo(sx - 90f, sy); p.close(); c.drawPath(p, f)
        s.color = 0xFF4A3424.toInt(); s.strokeWidth = 6f; c.drawLine(sx - 30f, sy - 40f, sx - 30f, sy - 250f, s); c.drawLine(sx + 50f, sy - 40f, sx + 50f, sy - 200f, s)
        f.color = 0xFFF4EEDD.toInt()
        p.reset(); p.moveTo(sx - 30f, sy - 240f); p.quadTo(sx + 30f, sy - 170f, sx - 30f, sy - 60f); p.close(); c.drawPath(p, f)
        p.reset(); p.moveTo(sx + 50f, sy - 190f); p.quadTo(sx + 100f, sy - 130f, sx + 50f, sy - 60f); p.close(); c.drawPath(p, f)
        f.color = 0xFFC0463A.toInt(); p.reset(); p.moveTo(sx - 30f, sy - 250f); p.lineTo(sx + 10f, sy - 240f); p.lineTo(sx - 30f, sy - 230f); p.close(); c.drawPath(p, f)
        // static wave bands
        for (q in 0 until 6) {
            val y = h + 60f + q * 70f
            s.color = withAlpha(0xFFE8F4F0.toInt(), 70 - q * 8); s.strokeWidth = 4f
            p.reset(); var x = -20f; p.moveTo(x, y)
            while (x < 1020f) { p.quadTo(x + 25f, y - 14f, x + 50f, y); x += 50f }
            c.drawPath(p, s)
        }
    }

    private fun toyland(c: Canvas, L: Layout) {
        // nursery wallpaper with stripes and a dado rail
        f.color = 0xFFE7CDA4.toInt(); c.drawRect(0f, 0f, 1000f, L.vh, f)
        f.color = 0xFFDCBC8E.toInt(); var x = 0f; while (x < 1000f) { c.drawRect(x, 0f, x + 30f, L.vh, f); x += 80f }
        f.color = 0x22B05A3A
        val r = Rng(9L)
        for (q in 0 until 60) { val px = r.nextFloat() * 1000f; val py = r.nextFloat() * L.vh; Draw.star(p, px, py, 10f); c.drawPath(p, f) }
        val h = horizon(L)
        // shelves
        for (shelf in 0 until 2) {
            val y = h - 260f + shelf * 300f
            f.color = 0xFF7A4E2C.toInt(); c.drawRect(0f, y, 1000f, y + 22f, f)
            f.color = 0xFF5A3820.toInt(); c.drawRect(0f, y + 22f, 1000f, y + 30f, f)
            val rr = Rng(40L + shelf)
            var bx = 30f
            while (bx < 960f) {
                val kind = rr.nextInt(4)
                val col = intArrayOf(0xFFC0563E.toInt(), 0xFF4E7AA8.toInt(), 0xFFD8A63E.toInt(), 0xFF5E9A5A.toInt())[rr.nextInt(4)]
                when (kind) {
                    0 -> { f.color = col; c.drawRect(bx, y - 60f, bx + 60f, y, f); Draw.text(c, "ABCXYZ"[rr.nextInt(6)].toString(), bx + 30f, y - 30f, 40f, 0xFFF5E8C8.toInt(), Fonts.chunky); bx += 76f }
                    1 -> { f.color = col; c.drawCircle(bx + 36f, y - 36f, 36f, f); f.color = 0x50FFFFFF; c.drawCircle(bx + 24f, y - 48f, 10f, f); bx += 86f }
                    2 -> { // toy soldier
                        f.color = 0xFFB8443A.toInt(); c.drawRect(bx + 10f, y - 110f, bx + 40f, y - 50f, f)
                        f.color = 0xFF2A3458.toInt(); c.drawRect(bx + 10f, y - 50f, bx + 40f, y, f); c.drawRect(bx + 8f, y - 140f, bx + 42f, y - 112f, f)
                        f.color = 0xFFF0D8B0.toInt(); c.drawCircle(bx + 25f, y - 118f, 12f, f); bx += 64f }
                    else -> { f.color = col; c.drawRect(bx, y - 90f, bx + 70f, y, f); f.color = 0x30000000; c.drawRect(bx + 50f, y - 90f, bx + 70f, y, f); bx += 86f }
                }
            }
        }
        // floorboards in the distance
        hills(c, h + 140f, 0f, 0.01f, 0f, 0xFFA87444.toInt(), L.vh)
        s.color = 0x30000000; s.strokeWidth = 3f
        for (q in 0 until 10) c.drawLine(0f, h + 160f + q * 40f, 1000f, h + 160f + q * 40f, s)
    }

    private fun city(c: Canvas, L: Layout) {
        sky(c, L, 0xFF141A2E.toInt(), 0xFF332A4C.toInt(), 0xFF6A4A5A.toInt())
        val h = horizon(L)
        val r = Rng(55L)
        for (q in 0 until 30) { f.color = withAlpha(0xFFFFF2C8.toInt(), 100 + r.nextInt(120)); c.drawCircle(r.nextFloat() * 1000f, L.Y(r.nextFloat() * 600f), 1.2f + r.nextFloat() * 1.8f, f) }
        for (layer in 0 until 3) {
            val col = intArrayOf(0xFF3A3654.toInt(), 0xFF2A2840.toInt(), 0xFF1C1A2C.toInt())[layer]
            var x = -20f
            while (x < 1000f) {
                val w = 60f + r.nextFloat() * 70f
                val bh = 160f + r.nextFloat() * 260f + layer * 40f - (if (layer == 2) 120f else 0f)
                val base = h + 60f + layer * 90f
                f.color = col; c.drawRect(x, base - bh, x + w, L.vh, f)
                // deco spires and steps
                if (r.chance(0.5f)) { c.drawRect(x + w * 0.2f, base - bh - 40f, x + w * 0.8f, base - bh, f); c.drawRect(x + w * 0.4f, base - bh - 80f, x + w * 0.6f, base - bh - 40f, f) }
                if (r.chance(0.3f)) { s.color = col; s.strokeWidth = 4f; c.drawLine(x + w / 2f, base - bh - 80f, x + w / 2f, base - bh - 150f, s) }
                for (wy in 0 until (bh / 30f).toInt()) for (wx in 0 until (w / 22f).toInt()) {
                    if (r.chance(0.35f + layer * 0.1f)) { f.color = withAlpha(0xFFFFD87A.toInt(), 140 + layer * 30); c.drawRect(x + 8f + wx * 22f, base - bh + 14f + wy * 30f, x + 18f + wx * 22f, base - bh + 28f + wy * 30f, f) }
                }
                x += w + 6f
            }
        }
    }

    private fun moon(c: Canvas, L: Layout) {
        sky(c, L, 0xFF0B0E20.toInt(), 0xFF221C44.toInt(), 0xFF3C2E5A.toInt())
        val h = horizon(L)
        val r = Rng(66L)
        for (q in 0 until 90) { f.color = withAlpha(0xFFFFF4D6.toInt(), 80 + r.nextInt(150)); c.drawCircle(r.nextFloat() * 1000f, r.nextFloat() * L.vh, 1f + r.nextFloat() * 2f, f) }
        // ringed planet
        val px = 790f; val py = L.Y(520f)
        f.color = 0xFFC08A5A.toInt(); c.drawCircle(px, py, 70f, f)
        f.color = 0xFFA06A44.toInt(); c.drawRect(px - 70f, py - 10f, px + 70f, py + 6f, f)
        s.color = 0xFFE8C890.toInt(); s.strokeWidth = 8f; rf.set(px - 130f, py - 26f, px + 130f, py + 26f); c.drawOval(rf, s)
        // lunar ground
        hills(c, h + 120f, 30f, 0.006f, 1f, 0xFF6A6488.toInt(), L.vh, 0x30FFFFFF)
        for (q in 0 until 7) { f.color = 0xFF57507A.toInt(); val cx = 60f + q * 150f; rf.set(cx - 40f, h + 160f + (q % 3) * 40f, cx + 40f, h + 180f + (q % 3) * 40f); c.drawOval(rf, f) }
        hills(c, h + 300f, 20f, 0.01f, 3f, 0xFF4E4870.toInt(), L.vh)
    }

    private fun winter(c: Canvas, L: Layout) {
        sky(c, L, 0xFF9DB8D0.toInt(), 0xFFDCE6EE.toInt(), 0xFFEFF2F2.toInt())
        val h = horizon(L)
        cloud(c, 260f, L.Y(260f), 120f, 0xFFF6F8FA.toInt(), 0xFFD0DAE4.toInt())
        hills(c, h - 30f, 70f, 0.005f, 1f, 0xFFDDE8F0.toInt(), L.vh, 0x60FFFFFF)
        for (q in 0 until 7) pine(c, 80f + q * 150f + (q % 2) * 30f, h + 20f - (q % 3) * 20f, 150f + (q % 3) * 30f, 0xFF3E6A5A.toInt(), true)
        hills(c, h + 110f, 40f, 0.008f, 3f, 0xFFF2F6FA.toInt(), L.vh, 0x50B0C8DC)
        // snowman
        val sx = 820f; val sy = h + 150f
        f.color = 0xFFFFFFFF.toInt(); c.drawCircle(sx, sy, 50f, f); c.drawCircle(sx, sy - 70f, 36f, f); c.drawCircle(sx, sy - 120f, 26f, f)
        f.color = 0xFF2A2A30.toInt(); c.drawCircle(sx - 9f, sy - 126f, 4f, f); c.drawCircle(sx + 9f, sy - 126f, 4f, f)
        f.color = 0xFFE88A3A.toInt(); p.reset(); p.moveTo(sx, sy - 118f); p.lineTo(sx + 26f, sy - 114f); p.lineTo(sx, sy - 110f); p.close(); c.drawPath(p, f)
        f.color = 0xFFC0463A.toInt(); c.drawRect(sx - 30f, sy - 98f, sx + 30f, sy - 88f, f)
        hills(c, h + 260f, 20f, 0.012f, 5f, 0xFFE4ECF2.toInt(), L.vh)
    }

    private fun jungle(c: Canvas, L: Layout) {
        sky(c, L, 0xFFE8C88A.toInt(), 0xFFEBD9A4.toInt(), 0xFFB8C890.toInt())
        val h = horizon(L)
        // volcano with a lazy plume
        f.color = 0xFF8A6A58.toInt(); p.reset(); p.moveTo(380f, h + 20f); p.lineTo(520f, h - 280f); p.lineTo(590f, h - 280f); p.lineTo(760f, h + 20f); p.close(); c.drawPath(p, f)
        f.color = 0xFFC86A3A.toInt(); p.reset(); p.moveTo(520f, h - 280f); p.lineTo(590f, h - 280f); p.lineTo(570f, h - 230f); p.lineTo(540f, h - 240f); p.close(); c.drawPath(p, f)
        cloud(c, 560f, h - 340f, 70f, 0xFFD8CCC0.toInt(), 0xFFB8ACA0.toInt())
        cloud(c, 620f, h - 420f, 90f, 0xFFE2D8CC.toInt(), 0xFFC0B4A8.toInt())
        hills(c, h + 40f, 40f, 0.007f, 2f, 0xFF5E8A4A.toInt(), L.vh)
        palm(c, 120f, h + 120f, 320f, 50f, 0xFF3E6E3A.toInt(), 0xFF6A4A2A.toInt())
        palm(c, 880f, h + 140f, 360f, -60f, 0xFF3E6E3A.toInt(), 0xFF6A4A2A.toInt())
        palm(c, 300f, h + 80f, 220f, -20f, 0xFF4E7E46.toInt(), 0xFF7A5A36.toInt())
        hills(c, h + 170f, 36f, 0.01f, 4f, 0xFF4A7A3E.toInt(), L.vh)
        // big leaves in front
        for (q in 0 until 8) {
            val x = q * 140f; val y = h + 260f + (q % 2) * 30f
            f.color = if (q % 2 == 0) 0xFF3A6A34.toInt() else 0xFF4E8040.toInt()
            p.reset(); p.moveTo(x, y + 60f); p.quadTo(x - 60f, y - 40f, x + 20f, y - 90f); p.quadTo(x + 60f, y - 20f, x, y + 60f); c.drawPath(p, f)
        }
    }

    private fun boiler(c: Canvas, L: Layout) {
        sky(c, L, 0xFF1E120C.toInt(), 0xFF3E2216.toInt(), 0xFF6A3418.toInt())
        val h = horizon(L)
        // furnace glow
        f.alpha = 255; f.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x80FF8A2A.toInt(), 0x30FF6A1A, 0x00FF6A1A), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.save(); c.translate(500f, h + 120f); c.scale(600f, 380f); c.drawCircle(0f, 0f, 1f, f); c.restore(); f.shader = null
        // pipes
        for (q in 0 until 5) {
            val x = 60f + q * 220f
            s.color = 0xFF2A1A12.toInt(); s.strokeWidth = 46f; c.drawLine(x, 0f, x, h + 200f, s)
            s.color = 0xFF8A5A3A.toInt(); s.strokeWidth = 36f; c.drawLine(x, 0f, x, h + 200f, s)
            s.color = 0x40FFD0A0; s.strokeWidth = 8f; c.drawLine(x - 8f, 0f, x - 8f, h + 200f, s)
            for (j in 0 until 6) { f.color = 0xFF5A3A26.toInt(); c.drawRect(x - 26f, L.Y(200f + j * 260f), x + 26f, L.Y(214f + j * 260f), f) }
        }
        s.color = 0xFF8A5A3A.toInt(); s.strokeWidth = 30f; c.drawLine(0f, L.Y(700f), 1000f, L.Y(680f), s)
        // gauges
        for ((gx, gy) in listOf(Pair(170f, L.Y(560f)), Pair(830f, L.Y(500f)))) {
            f.color = 0xFF2A1A12.toInt(); c.drawCircle(gx, gy, 52f, f)
            f.color = 0xFFC0703A.toInt(); c.drawCircle(gx, gy, 46f, f)
            f.color = 0xFFF2E6CC.toInt(); c.drawCircle(gx, gy, 38f, f)
            s.color = 0xFF2A1A12.toInt(); s.strokeWidth = 2f
            for (k in 0 until 9) { val a = 2.5f + k * 0.55f; c.drawLine(gx + cos(a) * 30f, gy + sin(a) * 30f, gx + cos(a) * 36f, gy + sin(a) * 36f, s) }
        }
        // grated floor
        f.color = 0xFF2A1A12.toInt(); c.drawRect(0f, h + 200f, 1000f, L.vh, f)
        s.color = 0xFF4A2E1E.toInt(); s.strokeWidth = 3f
        var x = 0f; while (x < 1000f) { c.drawLine(x, h + 200f, x + 60f, L.vh, s); x += 40f }
    }

    // ------------------------------------------------------------------ live bits

    fun live(c: Canvas, L: Layout, reel: Int, t: Float, beat: Float) {
        val h = horizon(L)
        val bounce = abs(sin(beat * PI_F))
        when (reel) {
            0 -> { sunFace(c, 840f, L.Y(170f), 80f, t, bounce, 0xFFF6C24A.toInt()); windmill(c, 250f, h - 10f, t) }
            1 -> pennants(c, L, h, t)
            2 -> { moonFace(c, 250f, L.Y(380f), 110f, t, bounce); bats(c, L, t) }
            3 -> { gull(c, 300f + sin(t * 0.4f) * 200f, L.Y(500f) + sin(t * 2f) * 20f, t); waves(c, L, h, t) }
            4 -> spinTop(c, 820f, h + 120f, t)
            5 -> searchlights(c, L, h, t)
            6 -> { moonFace(c, 230f, L.Y(300f), 120f, t, bounce, crescent = true); twinkle(c, L, t) }
            7 -> snow(c, L, t)
            8 -> fireflies(c, L, h, t)
            else -> { gear(c, 140f, L.Y(980f), 90f, t * 0.6f); gear(c, 860f, L.Y(860f), 70f, -t * 0.8f); steam(c, L, t) }
        }
    }

    private fun sunFace(c: Canvas, x: Float, y: Float, r: Float, t: Float, bounce: Float, col: Int) {
        c.save(); c.translate(x, y - bounce * 8f); c.rotate(sin(t) * 6f)
        f.color = withAlpha(col, 70)
        for (k in 0 until 12) { c.save(); c.rotate(k * 30f + t * 10f); p.reset(); p.moveTo(-14f, -r * 0.9f); p.lineTo(0f, -r * 1.55f); p.lineTo(14f, -r * 0.9f); p.close(); c.drawPath(p, f); c.restore() }
        f.color = col; c.drawCircle(0f, 0f, r, f)
        f.color = 0x40FFFFFF; c.drawCircle(-r * 0.3f, -r * 0.3f, r * 0.45f, f)
        f.color = 0xFF8A4A1A.toInt()
        rf.set(-r * 0.42f, -r * 0.35f, -r * 0.16f, r * 0.05f); c.drawOval(rf, f)
        rf.set(r * 0.16f, -r * 0.35f, r * 0.42f, r * 0.05f); c.drawOval(rf, f)
        s.color = 0xFF8A4A1A.toInt(); s.strokeWidth = r * 0.07f
        p.reset(); p.moveTo(-r * 0.4f, r * 0.25f); p.quadTo(0f, r * 0.62f, r * 0.4f, r * 0.25f); c.drawPath(p, s)
        f.color = 0x50E86A4A; c.drawCircle(-r * 0.55f, r * 0.2f, r * 0.14f, f); c.drawCircle(r * 0.55f, r * 0.2f, r * 0.14f, f)
        c.restore()
    }

    private fun moonFace(c: Canvas, x: Float, y: Float, r: Float, t: Float, bounce: Float, crescent: Boolean = false) {
        c.save(); c.translate(x, y - bounce * 5f); c.rotate(sin(t * 0.7f) * 5f)
        f.color = 0xFFF2E2A8.toInt(); c.drawCircle(0f, 0f, r, f)
        if (crescent) { f.color = 0xFF1A1636.toInt(); c.drawCircle(r * 0.45f, -r * 0.15f, r * 0.85f, f) }
        f.color = 0x30A08040
        c.drawCircle(-r * 0.4f, r * 0.4f, r * 0.12f, f); c.drawCircle(r * 0.1f, r * 0.55f, r * 0.08f, f)
        val ex = if (crescent) -r * 0.55f else -r * 0.3f
        val blink = (t % 4.5f) < 0.12f
        s.color = 0xFF6A5030.toInt(); s.strokeWidth = r * 0.06f
        if (blink) c.drawLine(ex - r * 0.1f, -r * 0.15f, ex + r * 0.1f, -r * 0.15f, s)
        else { f.color = 0xFF6A5030.toInt(); rf.set(ex - r * 0.08f, -r * 0.28f, ex + r * 0.08f, -r * 0.02f); c.drawOval(rf, f) }
        if (!crescent) { if (blink) c.drawLine(r * 0.2f, -r * 0.15f, r * 0.4f, -r * 0.15f, s) else { rf.set(r * 0.22f, -r * 0.28f, r * 0.38f, -r * 0.02f); c.drawOval(rf, f) } }
        p.reset(); p.moveTo(ex - r * 0.15f, r * 0.25f); p.quadTo(ex + r * 0.15f, r * 0.45f, ex + r * (if (crescent) 0.2f else 0.6f), r * 0.22f); c.drawPath(p, s)
        c.restore()
    }

    private fun windmill(c: Canvas, x: Float, y: Float, t: Float) {
        f.color = 0xFF8A6A4A.toInt()
        p.reset(); p.moveTo(x - 30f, y); p.lineTo(x - 10f, y - 170f); p.lineTo(x + 10f, y - 170f); p.lineTo(x + 30f, y); p.close(); c.drawPath(p, f)
        c.save(); c.translate(x, y - 170f); c.rotate(t * 40f)
        for (k in 0 until 4) { c.save(); c.rotate(k * 90f); f.color = 0xFFEADBC0.toInt(); c.drawRect(-6f, -100f, 6f, 0f, f); f.color = 0xFFD8C6A0.toInt(); c.drawRect(6f, -96f, 26f, -30f, f); c.restore() }
        f.color = 0xFF5A4030.toInt(); c.drawCircle(0f, 0f, 9f, f)
        c.restore()
    }

    private fun pennants(c: Canvas, L: Layout, h: Float, t: Float) {
        for (line in 0 until 2) {
            val y0 = L.Y(380f + line * 220f)
            s.color = 0xFF3A2A20.toInt(); s.strokeWidth = 3f
            p.reset(); p.moveTo(L.wallL, y0); p.quadTo(500f, y0 + 90f, L.wallR, y0); c.drawPath(p, s)
            for (k in 0 until 14) {
                val tt = (k + 0.5f) / 14f
                val x = lerp(lerp(L.wallL, 500f, tt), lerp(500f, L.wallR, tt), tt)
                val y = lerp(lerp(y0, y0 + 90f, tt), lerp(y0 + 90f, y0, tt), tt)
                val sw = sin(t * 4f + k) * 6f
                f.color = intArrayOf(0xFFC85A44.toInt(), 0xFFE8C860.toInt(), 0xFF4E8AA0.toInt())[k % 3]
                p.reset(); p.moveTo(x - 16f, y); p.lineTo(x + 16f, y); p.lineTo(x + sw, y + 40f); p.close(); c.drawPath(p, f)
            }
        }
    }

    private fun bats(c: Canvas, L: Layout, t: Float) {
        for (k in 0 until 3) {
            val ph = (t * 0.08f + k * 0.33f) % 1f
            val x = lerp(-60f, 1060f, ph); val y = L.Y(300f + k * 150f) + sin(t * 3f + k) * 30f
            val fl = sin(t * 18f + k) * 14f
            f.color = 0xFF0C0A16.toInt()
            p.reset(); p.moveTo(x, y); p.lineTo(x - 30f, y - 10f + fl); p.lineTo(x - 22f, y + 2f); p.lineTo(x - 36f, y + 4f + fl * 0.6f); p.lineTo(x, y + 10f)
            p.lineTo(x + 36f, y + 4f + fl * 0.6f); p.lineTo(x + 22f, y + 2f); p.lineTo(x + 30f, y - 10f + fl); p.close()
            c.drawPath(p, f); c.drawCircle(x, y + 2f, 8f, f)
        }
    }

    private fun gull(c: Canvas, x: Float, y: Float, t: Float) {
        val fl = sin(t * 6f) * 10f
        s.color = 0xFF4A4A50.toInt(); s.strokeWidth = 5f
        p.reset(); p.moveTo(x - 34f, y - 6f + fl); p.quadTo(x - 16f, y - 18f, x, y); p.quadTo(x + 16f, y - 18f, x + 34f, y - 6f + fl); c.drawPath(p, s)
    }

    private fun waves(c: Canvas, L: Layout, h: Float, t: Float) {
        for (q in 0 until 2) {
            val y = h + 30f + q * 160f
            p.reset(); var x = -60f + (t * (20f + q * 15f)) % 120f - 60f
            p.moveTo(x, y)
            while (x < 1060f) { p.quadTo(x + 30f, y - 20f + sin(t * 2f + x * 0.01f) * 6f, x + 60f, y); x += 60f }
            s.color = 0x70FFFFFF; s.strokeWidth = 4f; c.drawPath(p, s)
        }
    }

    private fun spinTop(c: Canvas, x: Float, y: Float, t: Float) {
        c.save(); c.translate(x + sin(t * 1.3f) * 30f, y); c.rotate(sin(t * 7f) * 8f)
        val stripe = (t * 8f).toInt() % 2
        p.reset(); p.moveTo(0f, 40f); p.lineTo(-60f, -10f); p.quadTo(0f, -50f, 60f, -10f); p.close()
        f.color = if (stripe == 0) 0xFFC0563E.toInt() else 0xFFE8C860.toInt(); c.drawPath(p, f)
        f.color = if (stripe == 0) 0xFFE8C860.toInt() else 0xFFC0563E.toInt(); c.drawRect(-46f, -14f, 46f, 0f, f)
        f.color = 0xFF6A4A2A.toInt(); c.drawRect(-5f, -60f, 5f, -30f, f)
        c.restore()
    }

    private fun searchlights(c: Canvas, L: Layout, h: Float, t: Float) {
        for (k in 0 until 2) {
            val bx = if (k == 0) 200f else 800f
            val a = -1.5708f + sin(t * 0.5f + k * 2f) * 0.5f
            val len = 1400f
            p.reset(); p.moveTo(bx, h + 100f)
            p.lineTo(bx + cos(a - 0.06f) * len, h + 100f + sin(a - 0.06f) * len)
            p.lineTo(bx + cos(a + 0.06f) * len, h + 100f + sin(a + 0.06f) * len); p.close()
            f.color = 0x22FFF2C0; c.drawPath(p, f)
        }
    }

    private fun twinkle(c: Canvas, L: Layout, t: Float) {
        for (k in 0 until 14) {
            val x = hash01(k, 1) * 1000f; val y = L.Y(hash01(k, 2) * 900f)
            val a = 0.5f + 0.5f * sin(t * (2f + hash01(k, 3) * 3f) + k)
            f.color = withAlpha(0xFFFFF4D6.toInt(), (220 * a).toInt())
            Draw.star(p, x, y, 6f + 6f * a, 0.35f, 0f, 4); c.drawPath(p, f)
        }
    }

    private fun snow(c: Canvas, L: Layout, t: Float) {
        f.color = 0xD0FFFFFF.toInt()
        for (k in 0 until 40) {
            val x = (hash01(k, 1) * 1000f + sin(t + k) * 30f + 1000f) % 1000f
            val y = (hash01(k, 2) * L.vh + t * (40f + hash01(k, 3) * 50f)) % L.vh
            c.drawCircle(x, y, 2.5f + hash01(k, 4) * 3.5f, f)
        }
    }

    private fun fireflies(c: Canvas, L: Layout, h: Float, t: Float) {
        for (k in 0 until 12) {
            val x = hash01(k, 1) * 1000f + sin(t * 0.7f + k) * 40f
            val y = h - 200f + hash01(k, 2) * 500f + cos(t * 0.9f + k) * 30f
            val a = 0.5f + 0.5f * sin(t * 3f + k * 1.7f)
            f.color = withAlpha(0xFFFFF0A0.toInt(), (60 * a).toInt()); c.drawCircle(x, y, 14f, f)
            f.color = withAlpha(0xFFFFF8D0.toInt(), (230 * a).toInt()); c.drawCircle(x, y, 4f, f)
        }
    }

    private fun gear(c: Canvas, x: Float, y: Float, r: Float, a: Float) {
        c.save(); c.translate(x, y); c.rotate(a * 57.3f)
        f.color = 0xFF6A4A32.toInt()
        for (k in 0 until 10) { c.save(); c.rotate(k * 36f); c.drawRect(-r * 0.14f, -r * 1.18f, r * 0.14f, -r * 0.8f, f); c.restore() }
        c.drawCircle(0f, 0f, r * 0.92f, f)
        f.color = 0xFF3E2A1C.toInt(); c.drawCircle(0f, 0f, r * 0.3f, f)
        f.color = 0x30FFD0A0; c.drawCircle(-r * 0.3f, -r * 0.3f, r * 0.3f, f)
        c.restore()
    }

    private fun steam(c: Canvas, L: Layout, t: Float) {
        for (k in 0 until 6) {
            val ph = (t * 0.25f + k / 6f) % 1f
            val x = 280f + (k % 2) * 440f + sin(t + k) * 20f
            val y = L.Y(1150f) - ph * 600f
            f.color = withAlpha(0xFFE8D8C8.toInt(), (70 * (1f - ph)).toInt())
            c.drawCircle(x, y, 30f + ph * 60f, f)
        }
    }
}
