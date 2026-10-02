package com.pranvir.fizz

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A tappable thing on screen (world units). */
class Btn(val id: Int, val r: RectF, var label: String = "", var style: Int = UI.S_TICKET, var icon: Int = -1, var enabled: Boolean = true, var arg: Int = 0)

object UI {
    const val S_TICKET = 0; const val S_ROUND = 1; const val S_PLAIN = 2; const val S_TOGGLE = 3
    const val I_PAUSE = 0; const val I_PLAY = 1; const val I_REPLAY = 2; const val I_MAP = 3; const val I_GEAR = 4; const val I_SOUND = 5
    const val I_MUSIC = 6; const val I_BACK = 7; const val I_CLOSE = 8; const val I_LOCK = 9; const val I_STAR = 10; const val I_TICKET = 11
    const val I_FILM = 12; const val I_NEXT = 13; const val I_VIB = 14

    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val p = Path()
    private val rf = RectF()

    /** Ticket-stub button: cream plate, notched ends, ink rim, gold inner line. */
    fun ticket(c: Canvas, r: RectF, label: String, pressed: Boolean, col: Int = Pal.VELVET, txt: Int = Pal.CREAM, enabled: Boolean = true, icon: Int = -1, t: Float = 0f) {
        val sq = if (pressed) 0.94f else 1f
        c.save(); c.scale(sq, sq, r.centerX(), r.centerY())
        val notch = r.height() * 0.22f
        fun shape(o: Float) {
            p.reset()
            p.moveTo(r.left - o, r.top - o); p.lineTo(r.right + o, r.top - o)
            p.lineTo(r.right + o, r.centerY() - notch); p.quadTo(r.right - notch * 0.8f, r.centerY(), r.right + o, r.centerY() + notch)
            p.lineTo(r.right + o, r.bottom + o); p.lineTo(r.left - o, r.bottom + o)
            p.lineTo(r.left - o, r.centerY() + notch); p.quadTo(r.left + notch * 0.8f, r.centerY(), r.left - o, r.centerY() - notch)
            p.close()
        }
        c.save(); c.translate(6f, 8f); shape(5f); f.color = withAlpha(Pal.INK, 110); c.drawPath(p, f); c.restore()
        shape(5f); f.color = Pal.INK; c.drawPath(p, f)
        shape(0f)
        val base = if (enabled) col else 0xFF6A5A50.toInt()
        f.alpha = 255; f.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(lerpColor(base, 0xFFFFFFFF.toInt(), 0.18f), base, scaleRgb(base, 0.72f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(p, f); f.shader = null
        s.color = withAlpha(Pal.GOLD, if (enabled) 255 else 120); s.strokeWidth = 3f
        rf.set(r.left + 12f, r.top + 9f, r.right - 12f, r.bottom - 9f); c.drawRoundRect(rf, 6f, 6f, s)
        val size = r.height() * 0.42f
        if (icon >= 0 && label.isEmpty()) icon(c, icon, r.centerX(), r.centerY(), r.height() * 0.3f, txt)
        else if (icon >= 0) {
            val w = Draw.width(label, size, Fonts.chunky)
            icon(c, icon, r.centerX() - w / 2f - size * 0.5f, r.centerY(), size * 0.55f, txt)
            Draw.toon(c, label, r.centerX() + size * 0.55f, r.centerY(), Draw.fit(label, size, r.width() - 90f, Fonts.chunky), if (enabled) txt else 0xFFB0A090.toInt(), Fonts.chunky, size * 0.14f, size * 0.05f)
        } else Draw.toon(c, label, r.centerX(), r.centerY(), Draw.fit(label, size, r.width() - 50f, Fonts.chunky), if (enabled) txt else 0xFFB0A090.toInt(), Fonts.chunky, size * 0.14f, size * 0.05f)
        c.restore()
    }

    fun round(c: Canvas, x: Float, y: Float, r: Float, icon: Int, pressed: Boolean, col: Int = Pal.VELVET, on: Boolean = true) {
        val sq = if (pressed) 0.9f else 1f
        c.save(); c.scale(sq, sq, x, y)
        f.color = withAlpha(Pal.INK, 110); c.drawCircle(x + 5f, y + 7f, r + 4f, f)
        f.color = Pal.INK; c.drawCircle(x, y, r + 4f, f)
        f.color = if (on) col else 0xFF5A4A44.toInt(); c.drawCircle(x, y, r, f)
        f.color = 0x30FFFFFF; c.drawCircle(x - r * 0.25f, y - r * 0.3f, r * 0.5f, f)
        s.color = Pal.GOLD; s.strokeWidth = 3.5f; c.drawCircle(x, y, r - 6f, s)
        icon(c, icon, x, y, r * 0.45f, if (on) Pal.CREAM else 0xFFAA9A8A.toInt())
        if (!on) { s.color = 0xFFE04A3B.toInt(); s.strokeWidth = 6f; c.drawLine(x - r * 0.55f, y + r * 0.55f, x + r * 0.55f, y - r * 0.55f, s) }
        c.restore()
    }

    fun icon(c: Canvas, id: Int, x: Float, y: Float, r: Float, col: Int) {
        f.color = col; s.color = col; s.strokeWidth = r * 0.28f
        when (id) {
            I_PAUSE -> { c.drawRect(x - r * 0.6f, y - r * 0.75f, x - r * 0.15f, y + r * 0.75f, f); c.drawRect(x + r * 0.15f, y - r * 0.75f, x + r * 0.6f, y + r * 0.75f, f) }
            I_PLAY, I_NEXT -> { p.reset(); p.moveTo(x - r * 0.55f, y - r * 0.8f); p.lineTo(x + r * 0.85f, y); p.lineTo(x - r * 0.55f, y + r * 0.8f); p.close(); c.drawPath(p, f)
                if (id == I_NEXT) c.drawRect(x + r * 0.7f, y - r * 0.8f, x + r * 1.0f, y + r * 0.8f, f) }
            I_REPLAY -> { rf.set(x - r * 0.75f, y - r * 0.75f, x + r * 0.75f, y + r * 0.75f); c.drawArc(rf, -60f, 290f, false, s)
                p.reset(); p.moveTo(x + r * 0.2f, y - r * 1.1f); p.lineTo(x + r * 0.9f, y - r * 0.7f); p.lineTo(x + r * 0.2f, y - r * 0.2f); p.close(); c.drawPath(p, f) }
            I_MAP -> { p.reset(); p.moveTo(x - r, y - r * 0.6f); p.lineTo(x - r * 0.33f, y - r * 0.85f); p.lineTo(x + r * 0.33f, y - r * 0.6f); p.lineTo(x + r, y - r * 0.85f)
                p.lineTo(x + r, y + r * 0.6f); p.lineTo(x + r * 0.33f, y + r * 0.85f); p.lineTo(x - r * 0.33f, y + r * 0.6f); p.lineTo(x - r, y + r * 0.85f); p.close(); s.strokeWidth = r * 0.18f; c.drawPath(p, s)
                c.drawLine(x - r * 0.33f, y - r * 0.85f, x - r * 0.33f, y + r * 0.6f, s); c.drawLine(x + r * 0.33f, y - r * 0.6f, x + r * 0.33f, y + r * 0.85f, s) }
            I_GEAR -> { for (k in 0 until 8) { c.save(); c.rotate(k * 45f, x, y); c.drawRect(x - r * 0.18f, y - r * 1.05f, x + r * 0.18f, y - r * 0.6f, f); c.restore() }
                s.strokeWidth = r * 0.36f; c.drawCircle(x, y, r * 0.58f, s) }
            I_SOUND -> { p.reset(); p.moveTo(x - r, y - r * 0.35f); p.lineTo(x - r * 0.45f, y - r * 0.35f); p.lineTo(x + r * 0.15f, y - r * 0.85f); p.lineTo(x + r * 0.15f, y + r * 0.85f); p.lineTo(x - r * 0.45f, y + r * 0.35f); p.lineTo(x - r, y + r * 0.35f); p.close(); c.drawPath(p, f)
                s.strokeWidth = r * 0.16f; rf.set(x - r * 0.1f, y - r * 0.55f, x + r * 0.6f, y + r * 0.55f); c.drawArc(rf, -50f, 100f, false, s)
                rf.set(x - r * 0.15f, y - r * 0.95f, x + r * 1.0f, y + r * 0.95f); c.drawArc(rf, -50f, 100f, false, s) }
            I_MUSIC -> { rf.set(x - r * 0.95f, y + r * 0.25f, x - r * 0.25f, y + r * 0.85f); c.drawOval(rf, f); rf.set(x + r * 0.15f, y + r * 0.05f, x + r * 0.85f, y + r * 0.65f); c.drawOval(rf, f)
                c.drawRect(x - r * 0.4f, y - r * 0.8f, x - r * 0.25f, y + r * 0.55f, f); c.drawRect(x + r * 0.7f, y - r, x + r * 0.85f, y + r * 0.35f, f)
                p.reset(); p.moveTo(x - r * 0.4f, y - r * 0.8f); p.lineTo(x + r * 0.85f, y - r); p.lineTo(x + r * 0.85f, y - r * 0.65f); p.lineTo(x - r * 0.4f, y - r * 0.45f); p.close(); c.drawPath(p, f) }
            I_BACK -> { p.reset(); p.moveTo(x + r * 0.5f, y - r * 0.8f); p.lineTo(x - r * 0.4f, y); p.lineTo(x + r * 0.5f, y + r * 0.8f); s.strokeWidth = r * 0.34f; c.drawPath(p, s) }
            I_CLOSE -> { s.strokeWidth = r * 0.3f; c.drawLine(x - r * 0.6f, y - r * 0.6f, x + r * 0.6f, y + r * 0.6f, s); c.drawLine(x + r * 0.6f, y - r * 0.6f, x - r * 0.6f, y + r * 0.6f, s) }
            I_LOCK -> { rf.set(x - r * 0.7f, y - r * 0.1f, x + r * 0.7f, y + r * 0.9f); c.drawRoundRect(rf, r * 0.15f, r * 0.15f, f)
                s.strokeWidth = r * 0.22f; rf.set(x - r * 0.45f, y - r * 0.85f, x + r * 0.45f, y + r * 0.2f); c.drawArc(rf, 180f, 180f, false, s)
                c.drawLine(x - r * 0.45f, y - r * 0.32f, x - r * 0.45f, y, s); c.drawLine(x + r * 0.45f, y - r * 0.32f, x + r * 0.45f, y, s) }
            I_STAR -> { Draw.star(p, x, y, r); c.drawPath(p, f) }
            I_TICKET -> { c.save(); c.rotate(-15f, x, y); rf.set(x - r, y - r * 0.6f, x + r, y + r * 0.6f); c.drawRect(rf, f)
                f.color = Pal.INK; c.drawCircle(x - r, y, r * 0.25f, f); c.drawCircle(x + r, y, r * 0.25f, f); s.color = Pal.INK; s.strokeWidth = r * 0.1f
                c.drawLine(x - r * 0.5f, y - r * 0.2f, x + r * 0.5f, y - r * 0.2f, s); c.drawLine(x - r * 0.5f, y + r * 0.15f, x + r * 0.3f, y + r * 0.15f, s); c.restore() }
            I_FILM -> { rf.set(x - r, y - r * 0.75f, x + r, y + r * 0.75f); s.strokeWidth = r * 0.16f; c.drawRect(rf, s)
                for (k in 0 until 4) { c.drawRect(x - r * 0.85f + k * r * 0.5f, y - r * 0.68f, x - r * 0.65f + k * r * 0.5f, y - r * 0.45f, f); c.drawRect(x - r * 0.85f + k * r * 0.5f, y + r * 0.45f, x - r * 0.65f + k * r * 0.5f, y + r * 0.68f, f) }
                c.drawCircle(x, y, r * 0.28f, f) }
            I_VIB -> { rf.set(x - r * 0.45f, y - r * 0.85f, x + r * 0.45f, y + r * 0.85f); s.strokeWidth = r * 0.16f; c.drawRoundRect(rf, r * 0.15f, r * 0.15f, s)
                c.drawLine(x - r * 0.75f, y - r * 0.4f, x - r * 0.75f, y + r * 0.4f, s); c.drawLine(x + r * 0.75f, y - r * 0.4f, x + r * 0.75f, y + r * 0.4f, s)
                c.drawLine(x - r * 1.0f, y - r * 0.25f, x - r * 1.0f, y + r * 0.25f, s); c.drawLine(x + r * 1.0f, y - r * 0.25f, x + r * 1.0f, y + r * 0.25f, s) }
        }
    }

    /** A gold star with an ink rim; [fill] 0..1 for the stamp-in animation. */
    fun star(c: Canvas, x: Float, y: Float, r: Float, got: Boolean, scale: Float = 1f, face: Boolean = false) {
        if (scale <= 0.01f) return
        c.save(); c.scale(scale, scale, x, y)
        Draw.star(p, x, y, r * 1.18f, 0.48f); f.color = Pal.INK; c.drawPath(p, f)
        Draw.star(p, x, y, r, 0.48f); f.color = if (got) Pal.GOLD else 0xFF5A4636.toInt(); c.drawPath(p, f)
        if (got) {
            Draw.star(p, x - r * 0.08f, y - r * 0.12f, r * 0.55f, 0.48f); f.color = 0xFFFFE9A0.toInt(); c.drawPath(p, f)
            if (face) {
                f.color = Pal.INK
                rf.set(x - r * 0.28f, y - r * 0.2f, x - r * 0.1f, y + r * 0.12f); c.drawOval(rf, f)
                rf.set(x + r * 0.1f, y - r * 0.2f, x + r * 0.28f, y + r * 0.12f); c.drawOval(rf, f)
                s.color = Pal.INK; s.strokeWidth = r * 0.08f
                p.reset(); p.moveTo(x - r * 0.22f, y + r * 0.25f); p.quadTo(x, y + r * 0.42f, x + r * 0.22f, y + r * 0.25f); c.drawPath(p, s)
            }
        }
        c.restore()
    }

    /** Silent-film title card: black field, ornate white frame, centred lines. */
    fun intertitle(c: Canvas, r: RectF, title: String, lines: List<String>, t: Float) {
        f.color = 0xF00C0906.toInt(); c.drawRect(r, f)
        val inset = 26f
        s.color = Pal.CREAM; s.strokeWidth = 4f
        rf.set(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset); c.drawRect(rf, s)
        s.strokeWidth = 1.5f
        rf.set(r.left + inset + 10f, r.top + inset + 10f, r.right - inset - 10f, r.bottom - inset - 10f); c.drawRect(rf, s)
        // corner flourishes
        for (k in 0 until 4) {
            val x = if (k % 2 == 0) r.left + inset + 10f else r.right - inset - 10f
            val y = if (k < 2) r.top + inset + 10f else r.bottom - inset - 10f
            val sx = if (k % 2 == 0) 1f else -1f; val sy = if (k < 2) 1f else -1f
            s.strokeWidth = 3f
            p.reset(); p.moveTo(x, y + sy * 60f); p.cubicTo(x + sx * 10f, y + sy * 20f, x + sx * 20f, y + sy * 10f, x + sx * 60f, y)
            c.drawPath(p, s)
            p.reset(); p.moveTo(x + sx * 14f, y + sy * 40f); p.cubicTo(x + sx * 18f, y + sy * 22f, x + sx * 22f, y + sy * 18f, x + sx * 40f, y + sy * 14f)
            c.drawPath(p, s)
            f.color = Pal.CREAM; c.drawCircle(x + sx * 24f, y + sy * 24f, 5f, f)
        }
        var y = r.top + r.height() * 0.2f
        if (title.isNotEmpty()) {
            Draw.text(c, title, r.centerX(), y, Draw.fit(title, 54f, r.width() - 140f, Fonts.deco, 0.06f), Pal.CREAM, Fonts.deco, spacing = 0.06f)
            s.color = Pal.CREAM; s.strokeWidth = 2f
            c.drawLine(r.centerX() - 120f, y + 46f, r.centerX() + 120f, y + 46f, s)
            f.color = Pal.CREAM; c.drawCircle(r.centerX(), y + 46f, 5f, f)
            y += 110f
        }
        for (line in lines) {
            Draw.text(c, line, r.centerX(), y, Draw.fit(line, 40f, r.width() - 120f, Fonts.body), Pal.CREAM, Fonts.body)
            y += 58f
        }
    }

    /** Cream paper panel with an art-deco gold frame and a velvet title tab. */
    fun paperPanel(c: Canvas, r: RectF, title: String, t: Float) {
        Draw.decoPanel(c, r, 0xFFF3E4C0.toInt(), 30f, Pal.GOLD_DK, 8f)
        // paper speckle
        f.color = 0x10000000
        for (k in 0 until 40) c.drawCircle(r.left + hash01(k, 1) * r.width(), r.top + hash01(k, 2) * r.height(), 2f + hash01(k, 3) * 5f, f)
        if (title.isNotEmpty()) {
            val tw = min(r.width() - 60f, Draw.width(title, 62f, Fonts.title) + 110f)
            rf.set(r.centerX() - tw / 2f, r.top - 52f, r.centerX() + tw / 2f, r.top + 46f)
            ticket(c, rf, "", false)
            Draw.bouncy(c, title, r.centerX(), r.top - 4f, Draw.fit(title, 58f, tw - 70f, Fonts.title), Pal.CREAM, t, Fonts.title, 0.05f)
        }
    }

    /** Rotating cartoon sunburst (title cards). */
    fun sunburst(c: Canvas, cx: Float, cy: Float, R: Float, rot: Float, a: Int, b: Int, rays: Int = 24) {
        f.color = a; c.drawCircle(cx, cy, R, f)
        f.color = b
        for (k in 0 until rays) {
            if (k % 2 == 1) continue
            val a0 = rot + k * TAU / rays; val a1 = a0 + TAU / rays
            p.reset(); p.moveTo(cx, cy); p.lineTo(cx + cos(a0) * R, cy + sin(a0) * R); p.lineTo(cx + cos(a1) * R, cy + sin(a1) * R); p.close()
            c.drawPath(p, f)
        }
    }
}
