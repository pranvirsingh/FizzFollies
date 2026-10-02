package com.pranvir.fizz

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Screen layout in world units (1000 wide). The board is scaled by [k] and anchored to the bottom. */
class Layout {
    var wPx = 1080; var hPx = 2340
    var s = 1.08f          // pixels per world unit
    var vh = 2166f         // world height
    var iT = 0f; var iB = 0f
    var k = 1f             // board scale
    var bx = 0f; var by = 0f
    val boardH get() = Board.PIVOT_Y + 250f

    fun set(w: Int, h: Int, insetTop: Int, insetBottom: Int) {
        wPx = w; hPx = h
        s = w / 1000f
        vh = h / s
        iT = insetTop / s; iB = insetBottom / s
        val hudMin = iT + 200f
        k = min(1f, (vh - iB - hudMin) / boardH).coerceAtLeast(0.6f)
        bx = (1000f - 1000f * k) / 2f
        by = vh - iB - boardH * k
    }

    fun X(x: Float) = bx + x * k
    fun Y(y: Float) = by + y * k
    /** World -> board units. */
    fun toBx(x: Float) = (x - bx) / k
    fun toBy(y: Float) = (y - by) / k
    val floorTop get() = Y(Board.PIVOT_Y + 150f)
    val lipTop get() = Y(Board.PIVOT_Y + 222f)
    val valanceTop get() = by - 74f * k
    val wallL get() = X(Board.BX)
    val wallR get() = X(Board.W - Board.BX)
}

/** The theatre around the bubbles: velvet curtains, the valance, the proscenium, the stage floor and footlights. */
object Stage {
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val p = Path()
    private val rf = RectF()

    private val velvetFold = LinearGradient(0f, 0f, 46f, 0f,
        intArrayOf(0xFF4A0A10.toInt(), 0xFF9E1E28.toInt(), 0xFFD0414A.toInt(), 0xFF9E1E28.toInt(), 0xFF4A0A10.toInt()),
        floatArrayOf(0f, 0.3f, 0.5f, 0.72f, 1f), Shader.TileMode.REPEAT)

    /** Paint the static frame (over an already painted backdrop). */
    fun bake(c: Canvas, L: Layout, reel: Int) {
        proscenium(c, L)
        valance(c, L)
        sideCurtains(c, L)
        floor(c, L)
    }

    private fun proscenium(c: Canvas, L: Layout) {
        val bottom = L.valanceTop + 10f
        // deep maroon wall with gilded deco ribs
        f.alpha = 255; f.shader = LinearGradient(0f, 0f, 0f, bottom, intArrayOf(0xFF1C0A0C.toInt(), 0xFF3A1216.toInt(), 0xFF26090D.toInt()), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1000f, bottom, f); f.shader = null
        // sunburst rays behind the marquee
        val cx = 500f; val cy = bottom + 20f
        for (q in 0 until 26) {
            val a0 = Math.PI.toFloat() + q * Math.PI.toFloat() / 26f
            val a1 = a0 + Math.PI.toFloat() / 52f
            p.reset(); p.moveTo(cx, cy)
            p.lineTo(cx + cos(a0) * 1400f, cy + sin(a0) * 1400f); p.lineTo(cx + cos(a1) * 1400f, cy + sin(a1) * 1400f); p.close()
            f.color = 0x14E7B347; c.drawPath(p, f)
        }
        s.color = withAlpha(Pal.GOLD, 120); s.strokeWidth = 3f
        c.drawLine(0f, bottom - 6f, 1000f, bottom - 6f, s)
        s.strokeWidth = 1.5f; c.drawLine(0f, bottom - 14f, 1000f, bottom - 14f, s)
    }

    private fun valance(c: Canvas, L: Layout) {
        val top = L.valanceTop; val k = L.k
        val bottom = L.by + 6f * k
        // swags: a row of scallops
        val n = 7
        val w = 1000f / n
        c.save()
        f.alpha = 255; f.shader = velvetFold
        c.drawRect(0f, top, 1000f, top + 30f * k, f)
        for (q in 0 until n) {
            val x0 = q * w
            p.reset()
            p.moveTo(x0 - 4f, top)
            p.lineTo(x0 + w + 4f, top)
            p.lineTo(x0 + w + 4f, top + 30f * k)
            p.quadTo(x0 + w / 2f, bottom + 40f * k, x0 - 4f, top + 30f * k)
            p.close()
            f.alpha = 255; f.shader = velvetFold
            c.drawPath(p, f)
            f.shader = null
            // swag shading and fringe
            s.color = withAlpha(0xFF2A0508.toInt(), 150); s.strokeWidth = 3f * k
            for (r in 1..3) {
                val yy = top + 30f * k + r * 10f * k
                p.reset(); p.moveTo(x0 + 6f, yy - 22f * k); p.quadTo(x0 + w / 2f, yy + 24f * k + r * 4f * k, x0 + w - 6f, yy - 22f * k)
                c.drawPath(p, s)
            }
            s.color = Pal.GOLD; s.strokeWidth = 5f * k
            p.reset(); p.moveTo(x0, top + 30f * k); p.quadTo(x0 + w / 2f, bottom + 40f * k, x0 + w, top + 30f * k)
            c.drawPath(p, s)
            s.color = Pal.GOLD_DK; s.strokeWidth = 2f * k
            for (t in 1 until 12) {
                val tt = t / 12f
                val bx = lerp(lerp(x0, x0 + w / 2f, tt), lerp(x0 + w / 2f, x0 + w, tt), tt)
                val byy = lerp(lerp(top + 30f * k, bottom + 40f * k, tt), lerp(bottom + 40f * k, top + 30f * k, tt), tt)
                c.drawLine(bx, byy, bx, byy + 9f * k, s)
            }
            // tassel at each joint
            f.color = Pal.INK; c.drawCircle(x0, top + 30f * k, 9f * k, f)
            f.color = Pal.GOLD; c.drawCircle(x0, top + 30f * k, 6.5f * k, f)
            rf.set(x0 - 5f * k, top + 34f * k, x0 + 5f * k, top + 58f * k)
            f.color = Pal.INK; c.drawRect(rf.left - 1.5f, rf.top, rf.right + 1.5f, rf.bottom + 1.5f, f)
            f.color = Pal.GOLD_DK; c.drawRect(rf, f)
        }
        c.restore()
        // gilded rail along the top edge
        f.color = Pal.INK; c.drawRect(0f, top - 9f, 1000f, top + 3f, f)
        f.color = Pal.GOLD; c.drawRect(0f, top - 7f, 1000f, top + 1f, f)
        f.color = 0xFFF6D67E.toInt(); c.drawRect(0f, top - 6f, 1000f, top - 4f, f)
    }

    private fun sideCurtains(c: Canvas, L: Layout) {
        val top = L.valanceTop
        val bot = L.lipTop
        for (side in 0..1) {
            val inner = if (side == 0) L.wallL else L.wallR
            c.save()
            // the curtain hangs straight, then is tied back with a gold rope near the top
            p.reset()
            if (side == 0) {
                p.moveTo(0f, top); p.lineTo(inner + 70f, top); p.quadTo(inner + 30f, top + 140f, inner, top + 260f)
                p.lineTo(inner, bot); p.lineTo(0f, bot); p.close()
            } else {
                p.moveTo(1000f, top); p.lineTo(inner - 70f, top); p.quadTo(inner - 30f, top + 140f, inner, top + 260f)
                p.lineTo(inner, bot); p.lineTo(1000f, bot); p.close()
            }
            f.alpha = 255; f.shader = velvetFold; c.drawPath(p, f); f.shader = null
            s.color = Pal.INK; s.strokeWidth = 5f; c.drawPath(p, s)
            // tie-back rope
            val ry = top + 250f
            s.color = Pal.INK; s.strokeWidth = 11f
            c.drawLine(if (side == 0) 0f else 1000f, ry - 20f, inner, ry, s)
            s.color = Pal.GOLD; s.strokeWidth = 6f
            c.drawLine(if (side == 0) 0f else 1000f, ry - 20f, inner, ry, s)
            f.color = Pal.INK; c.drawCircle(inner, ry + 4f, 10f, f)
            f.color = Pal.GOLD; c.drawCircle(inner, ry + 4f, 7f, f)
            c.restore()
        }
    }

    private fun floor(c: Canvas, L: Layout) {
        val top = L.floorTop; val lip = L.lipTop; val bottom = L.vh + 4f
        // planks with a little perspective
        f.alpha = 255; f.shader = LinearGradient(0f, top, 0f, lip, intArrayOf(0xFF3E2414.toInt(), 0xFF8A5A33.toInt(), 0xFFA8733F.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, top, 1000f, lip, f); f.shader = null
        s.color = withAlpha(Pal.WOOD_DK, 170)
        var y = top; var gap = 8f; var row = 0
        while (y < lip) {
            s.strokeWidth = 1.5f + gap * 0.08f
            c.drawLine(0f, y, 1000f, y, s)
            // board seams, staggered
            val off = (row % 2) * 90f
            var x = off
            while (x < 1000f) { c.drawLine(x, y, x, min(lip, y + gap), s); x += 180f }
            y += gap; gap *= 1.35f; row++
        }
        // perspective lines converging to centre-back
        s.color = withAlpha(Pal.WOOD_DK, 60); s.strokeWidth = 1.5f
        for (q in -6..6) c.drawLine(500f + q * 60f, top, 500f + q * 110f, lip, s)
        // shadow at the back where the floor meets the backdrop
        f.alpha = 255; f.shader = LinearGradient(0f, top - 20f, 0f, top + 30f, intArrayOf(0x00000000, 0x90000000.toInt(), 0x00000000), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, top - 20f, 1000f, top + 30f, f); f.shader = null
        // stage lip with gilded trim
        f.color = Pal.INK; c.drawRect(0f, lip - 4f, 1000f, bottom, f)
        f.alpha = 255; f.shader = LinearGradient(0f, lip, 0f, bottom, intArrayOf(0xFF5A0E15.toInt(), 0xFF2C060A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, lip, 1000f, bottom, f); f.shader = null
        f.color = Pal.GOLD; c.drawRect(0f, lip, 1000f, lip + 5f, f)
        f.color = Pal.GOLD_DK; c.drawRect(0f, lip + 5f, 1000f, lip + 7f, f)
        // footlight hoods
        val n = 9
        for (q in 0 until n) {
            val x = (q + 0.5f) * 1000f / n
            f.color = Pal.INK; rf.set(x - 36f, lip - 18f, x + 36f, lip + 22f); c.drawArc(rf, 180f, 180f, true, f)
            f.alpha = 255; f.shader = LinearGradient(0f, lip - 16f, 0f, lip + 2f, intArrayOf(0xFFB9C1CC.toInt(), 0xFF5D6570.toInt()), null, Shader.TileMode.CLAMP)
            rf.set(x - 32f, lip - 14f, x + 32f, lip + 18f); c.drawArc(rf, 180f, 180f, true, f); f.shader = null
        }
    }

    /** Live footlight glow, pulsing with the music. */
    fun footlights(c: Canvas, L: Layout, beat: Float, mood: Float) {
        val lip = L.lipTop
        val n = 9
        val pulse = 0.75f + 0.25f * sin(beat * TAU)
        for (q in 0 until n) {
            val x = (q + 0.5f) * 1000f / n
            f.alpha = 255; f.shader = glow
            c.save(); c.translate(x, lip - 6f); c.scale(150f, 120f * pulse)
            f.alpha = (170 * (0.6f + 0.4f * mood)).toInt()
            c.drawCircle(0f, 0f, 1f, f)
            c.restore()
            f.shader = null; f.alpha = 255
            f.color = 0xFFFFF0B8.toInt(); rf.set(x - 20f, lip - 10f, x + 20f, lip - 2f); c.drawOval(rf, f)
        }
    }
    private val glow = RadialGradient(0f, 0f, 1f, intArrayOf(0x90FFE7A0.toInt(), 0x40FFC860, 0x00FFC860), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)

    /** A lowered scenery batten (Beat the Drop / the Baron pushing down): bubbles hang from it. */
    fun batten(c: Canvas, L: Layout, ceil: Float) {
        if (ceil <= 0.5f) return
        val y = L.Y(ceil)
        // ropes up to the valance
        s.color = 0xFF6B4A2B.toInt(); s.strokeWidth = 4f
        for (x in floatArrayOf(120f, 380f, 620f, 880f)) c.drawLine(L.X(x), L.valanceTop, L.X(x), y - 18f * L.k, s)
        f.color = Pal.INK; c.drawRect(L.wallL - 4f, y - 26f * L.k, L.wallR + 4f, y + 2f * L.k, f)
        f.alpha = 255; f.shader = LinearGradient(0f, y - 22f * L.k, 0f, y, intArrayOf(0xFFB98552.toInt(), 0xFF7A4A26.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(L.wallL, y - 22f * L.k, L.wallR, y - 1f * L.k, f); f.shader = null
        f.color = withAlpha(Pal.CREAM, 160)
        for (q in 0 until 12) { val x = L.X(40f + q * 84f); c.drawCircle(x, y - 11f * L.k, 3f * L.k, f) }
        // painted backdrop flat above it (so the space the ceiling ate looks like scenery)
        f.color = withAlpha(0xFF2A0E12.toInt(), 235)
        c.drawRect(L.wallL, L.valanceTop + 30f, L.wallR, y - 26f * L.k, f)
        s.color = withAlpha(Pal.GOLD, 90); s.strokeWidth = 2f
        var yy = y - 60f * L.k
        while (yy > L.valanceTop + 40f) { c.drawLine(L.wallL + 10f, yy, L.wallR - 10f, yy, s); yy -= 46f * L.k }
    }

    /** The chalk line bubbles must not cross. */
    fun deadLine(c: Canvas, L: Layout, ceilDrops: Int, danger: Float, t: Float) {
        val y = L.Y(Board.DEAD_Y)
        val a = 0.35f + danger * (0.45f + 0.2f * sin(t * 10f))
        s.color = withAlpha(lerpColor(Pal.CREAM, 0xFFFF4030.toInt(), danger), (255 * a).toInt())
        s.strokeWidth = 4f * L.k
        var x = L.wallL + 10f
        while (x < L.wallR - 10f) { c.drawLine(x, y, min(L.wallR - 10f, x + 22f * L.k), y, s); x += 40f * L.k }
    }
}
