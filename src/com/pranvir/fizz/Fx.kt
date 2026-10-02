package com.pranvir.fizz

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One cartoon effect particle. Pooled. */
class Part {
    var type = 0
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var t = 0f; var life = 1f
    var size = 1f; var rot = 0f; var spin = 0f
    var col = 0; var col2 = 0
    var grav = 0f
    var text: String? = null
    var kind = 0; var face = 0
    var tx = 0f; var ty = 0f
    var alive = false
}

/**
 * Pops, puffs, droplets, stars, flying notes, falling critters and score pop-ups, all in board units.
 * The play view feeds it; [onLand] reports falling critters reaching the floor.
 */
class Fx {
    companion object {
        const val RING = 0; const val DROP = 1; const val STAR = 2; const val SMOKE = 3; const val TEXT = 4; const val SPARK = 5
        const val NOTE = 6; const val FALLER = 7; const val BURST = 8; const val CONFETTI = 9; const val WORD = 10; const val INKBLOB = 11
        const val BAR = 12; const val STEAM = 13; const val THROWN = 14; const val PUFF = 15
        const val MAX = 420
    }
    val parts = Array(MAX) { Part() }
    var floorY = 1600f
    var onLand: ((Part) -> Unit)? = null
    var onArrive: ((Part) -> Unit)? = null
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val p = Path()
    private val rf = RectF()
    private val rng = Rng(4242L)

    fun clear() { for (q in parts) q.alive = false }
    val count: Int get() { var n = 0; for (q in parts) if (q.alive) n++; return n }

    fun spawn(type: Int, x: Float, y: Float): Part {
        var best: Part? = null
        for (q in parts) if (!q.alive) { best = q; break }
        if (best == null) {
            // recycle the oldest cosmetic particle (never a falling critter or a thrown bubble)
            var oldest = -1f
            for (q in parts) if (q.type != FALLER && q.type != THROWN && q.t / q.life > oldest) { oldest = q.t / q.life; best = q }
        }
        val q = best ?: parts[0]
        q.alive = true; q.type = type; q.x = x; q.y = y; q.vx = 0f; q.vy = 0f; q.t = 0f; q.life = 1f; q.size = 1f
        q.rot = 0f; q.spin = 0f; q.col = Pal.CREAM; q.col2 = Pal.INK; q.grav = 0f; q.text = null; q.kind = 0; q.face = 0
        return q
    }

    // ------------------------------------------------------------------ recipes

    fun pop(x: Float, y: Float, col: Int, big: Boolean) {
        spawn(RING, x, y).apply { life = 0.32f; size = Board.R; col2 = col }
        val n = if (big) 8 else 6
        for (k in 0 until n) {
            val a = k * TAU / n + rng.range(-0.3f, 0.3f)
            val sp = rng.range(260f, 520f)
            spawn(DROP, x + cos(a) * 18f, y + sin(a) * 18f).apply { vx = cos(a) * sp; vy = sin(a) * sp - 120f; grav = 1500f; life = rng.range(0.35f, 0.6f); size = rng.range(5f, 9f); this.col = col }
        }
        if (big) for (k in 0 until 3) spawn(STAR, x, y).apply { val a = rng.range(0f, TAU); vx = cos(a) * 240f; vy = sin(a) * 240f - 60f; life = 0.55f; size = rng.range(8f, 13f); spin = rng.range(-8f, 8f); this.col = Pal.GOLD }
    }

    fun smoke(x: Float, y: Float, n: Int, sz: Float = 26f, col: Int = 0xFFE8E0D0.toInt()) {
        for (k in 0 until n) {
            val a = rng.range(0f, TAU); val sp = rng.range(40f, 160f)
            spawn(SMOKE, x + cos(a) * 10f, y + sin(a) * 10f).apply { vx = cos(a) * sp; vy = sin(a) * sp - 40f; life = rng.range(0.45f, 0.8f); size = sz * rng.range(0.7f, 1.3f); this.col = col }
        }
    }

    fun boom(x: Float, y: Float, big: Boolean) {
        spawn(BURST, x, y).apply { life = 0.45f; size = if (big) 150f else 100f; kind = rng.nextInt(1000) }
        smoke(x, y, if (big) 9 else 6, if (big) 40f else 30f, 0xFF4A4038.toInt())
        for (k in 0 until 10) {
            val a = rng.range(0f, TAU); val sp = rng.range(300f, 700f)
            spawn(SPARK, x, y).apply { vx = cos(a) * sp; vy = sin(a) * sp; life = rng.range(0.2f, 0.4f); size = rng.range(14f, 26f); col = Pal.GOLD }
        }
    }

    fun text(x: Float, y: Float, str: String, col: Int = Pal.CREAM, sz: Float = 46f, life: Float = 0.9f) {
        spawn(TEXT, x, y).apply { text = str; this.col = col; size = sz; this.life = life; vy = -90f }
    }

    fun word(x: Float, y: Float, str: String, col: Int) {
        spawn(WORD, x, y).apply { text = str; this.col = col; size = 74f; life = 1.25f; kind = rng.nextInt(1000) }
    }

    fun note(x: Float, y: Float, tx: Float, ty: Float) {
        spawn(NOTE, x, y).apply { this.tx = tx; this.ty = ty; life = 1.1f; size = 30f; spin = rng.range(-3f, 3f); vx = rng.range(-300f, 300f); vy = -500f }
    }

    fun faller(x: Float, y: Float, kind: Int, col: Int, hp: Int) {
        spawn(FALLER, x, y).apply {
            this.kind = kind; this.col = col; face = hp
            vx = rng.range(-180f, 180f); vy = rng.range(-260f, -60f); grav = 2400f; life = 6f; spin = rng.range(-5f, 5f)
        }
    }

    fun thrown(x: Float, y: Float, tx: Float, ty: Float, kind: Int, col: Int, hp: Int, cell: Int) {
        spawn(THROWN, x, y).apply { this.tx = tx; this.ty = ty; this.kind = kind; this.col = col; face = hp; size = cell.toFloat(); life = 0.38f; vx = x; vy = y }
    }

    fun confetti(x0: Float, x1: Float, y: Float, n: Int) {
        for (k in 0 until n) spawn(CONFETTI, rng.range(x0, x1), y + rng.range(-60f, 0f)).apply {
            vx = rng.range(-80f, 80f); vy = rng.range(-200f, 100f); grav = 380f; life = rng.range(2.2f, 3.6f); size = rng.range(8f, 14f)
            spin = rng.range(-10f, 10f); col = if (rng.chance(0.5f)) Pal.BUB[rng.nextInt(6)] else Pal.GOLD
        }
    }

    fun inkSplat(x: Float, y: Float) {
        for (k in 0 until 7) { val a = rng.range(0f, TAU); val sp = rng.range(150f, 420f)
            spawn(INKBLOB, x, y).apply { vx = cos(a) * sp; vy = sin(a) * sp - 100f; grav = 1400f; life = rng.range(0.4f, 0.7f); size = rng.range(6f, 12f) } }
    }

    fun bars(x: Float, y: Float) {
        for (k in 0 until 3) spawn(BAR, x + (k - 1) * 21f, y).apply { vx = (k - 1) * 220f + rng.range(-60f, 60f); vy = rng.range(-420f, -260f); grav = 2000f; life = 0.9f; spin = rng.range(-12f, 12f) }
    }

    fun steam(x: Float, y: Float, dirX: Float) {
        spawn(STEAM, x, y).apply { vx = dirX * rng.range(120f, 200f); vy = rng.range(-160f, -90f); life = rng.range(0.7f, 1.1f); size = rng.range(16f, 26f) }
    }

    fun puff(x: Float, y: Float, col: Int) {
        spawn(PUFF, x, y).apply { life = 0.45f; size = 30f; this.col = col }
    }

    // ------------------------------------------------------------------ simulation

    fun update(dt: Float) {
        for (q in parts) {
            if (!q.alive) continue
            q.t += dt
            when (q.type) {
                NOTE -> {
                    // curve towards the goal display
                    val k = clamp01(q.t / q.life)
                    q.vx *= 0.9f; q.vy += 900f * dt
                    q.x += q.vx * dt; q.y += q.vy * dt
                    q.x = lerp(q.x, q.tx, k * k * 0.35f); q.y = lerp(q.y, q.ty, k * k * 0.35f)
                }
                THROWN -> {
                    val k = clamp01(q.t / q.life)
                    q.x = lerp(q.vx, q.tx, k); q.y = lerp(q.vy, q.ty, k) - sin(k * PI_F) * 120f
                    if (k >= 1f) { q.alive = false; onArrive?.invoke(q); continue }
                }
                else -> {
                    q.vy += q.grav * dt
                    q.x += q.vx * dt; q.y += q.vy * dt
                    if (q.type == SMOKE || q.type == STEAM) { q.vx *= 0.92f; q.vy *= 0.92f }
                    if (q.type == CONFETTI) { q.vx = q.vx * 0.98f + sin(q.t * 6f + q.spin) * 12f; q.vy = min(q.vy, 160f) }
                }
            }
            q.rot += q.spin * dt
            if (q.type == FALLER && q.y > floorY) { q.alive = false; onLand?.invoke(q); continue }
            if (q.t >= q.life) q.alive = false
        }
    }

    // ------------------------------------------------------------------ drawing

    /** Particles that live among the bubbles. Fallers are drawn by the play view (they need the art). */
    fun drawWorld(c: Canvas) {
        for (q in parts) {
            if (!q.alive) continue
            val k = clamp01(q.t / q.life)
            when (q.type) {
                RING -> { s.color = withAlpha(q.col2, (230 * (1f - k)).toInt()); s.strokeWidth = 7f * (1f - k) + 1f; c.drawCircle(q.x, q.y, q.size * (0.7f + 0.7f * easeOut(k)), s)
                    s.color = withAlpha(0xFFFFFFFF.toInt(), (200 * (1f - k)).toInt()); s.strokeWidth = 3f * (1f - k); c.drawCircle(q.x, q.y, q.size * (0.5f + 0.9f * easeOut(k)), s) }
                DROP -> { f.color = Pal.INK; c.drawCircle(q.x, q.y, q.size * (1f - k * 0.5f) + 2f, f); f.color = q.col; c.drawCircle(q.x, q.y, q.size * (1f - k * 0.5f), f) }
                STAR -> { Draw.star(p, q.x, q.y, q.size * (1f - k * 0.4f), 0.45f, q.rot); f.color = Pal.INK; c.save(); c.scale(1.3f, 1.3f, q.x, q.y); c.drawPath(p, f); c.restore(); f.color = q.col; c.drawPath(p, f) }
                SMOKE, PUFF -> { val r = q.size * (0.6f + 0.8f * easeOut(k)); f.color = withAlpha(Pal.INK, (200 * (1f - k)).toInt()); c.drawCircle(q.x, q.y, r + 3f, f)
                    f.color = withAlpha(q.col, (255 * (1f - k)).toInt()); c.drawCircle(q.x, q.y, r, f)
                    f.color = withAlpha(0xFFFFFFFF.toInt(), (120 * (1f - k)).toInt()); c.drawCircle(q.x - r * 0.3f, q.y - r * 0.3f, r * 0.35f, f) }
                SPARK -> { s.color = withAlpha(q.col, (255 * (1f - k)).toInt()); s.strokeWidth = 5f
                    val l = q.size * (1f - k); val sp = sqrt(q.vx * q.vx + q.vy * q.vy).coerceAtLeast(1f)
                    c.drawLine(q.x, q.y, q.x - q.vx / sp * l, q.y - q.vy / sp * l, s) }
                BURST -> {
                    val r = q.size * (0.5f + 0.6f * easeOut(k * 2f))
                    Draw.burst(p, q.x, q.y, r, 11, q.kind)
                    f.color = withAlpha(Pal.INK, (255 * (1f - k)).toInt()); c.save(); c.scale(1.08f, 1.08f, q.x, q.y); c.drawPath(p, f); c.restore()
                    f.color = withAlpha(0xFFFFD34A.toInt(), (255 * (1f - k)).toInt()); c.drawPath(p, f)
                    Draw.burst(p, q.x, q.y, r * 0.6f, 9, q.kind + 3)
                    f.color = withAlpha(0xFFFFF6D8.toInt(), (255 * (1f - k)).toInt()); c.drawPath(p, f)
                }
                CONFETTI -> { c.save(); c.translate(q.x, q.y); c.rotate(q.rot * 57f); c.scale(1f, cos(q.t * 8f + q.spin))
                    f.color = withAlpha(q.col, (255 * min(1f, (1f - k) * 3f)).toInt()); c.drawRect(-q.size / 2f, -q.size / 4f, q.size / 2f, q.size / 4f, f); c.restore() }
                INKBLOB -> { f.color = withAlpha(0xFF120D18.toInt(), (255 * (1f - k * 0.5f)).toInt()); c.drawCircle(q.x, q.y, q.size, f) }
                BAR -> { c.save(); c.translate(q.x, q.y); c.rotate(q.rot * 57f)
                    f.color = Pal.INK; c.drawRect(-4f, -30f, 4f, 30f, f); f.color = 0xFF6C7380.toInt(); c.drawRect(-2.5f, -28f, 2.5f, 28f, f); c.restore() }
                STEAM -> { val r = q.size * (0.6f + 1.2f * k); f.color = withAlpha(0xFFF4F0EA.toInt(), (190 * (1f - k)).toInt()); c.drawCircle(q.x, q.y, r, f) }
            }
        }
    }

    /** Pop-up lettering and flying notes go above everything in the board. */
    fun drawTop(c: Canvas) {
        for (q in parts) {
            if (!q.alive) continue
            val k = clamp01(q.t / q.life)
            when (q.type) {
                TEXT -> {
                    val sc = if (k < 0.15f) easeOutBack(k / 0.15f) else 1f
                    val a = if (k > 0.7f) 1f - (k - 0.7f) / 0.3f else 1f
                    c.save(); c.translate(q.x, q.y); c.scale(sc, sc)
                    Draw.toon(c, q.text ?: "", 0f, 0f, q.size, alphaF(q.col, a), Fonts.chunky, q.size * 0.16f, q.size * 0.06f)
                    c.restore()
                }
                WORD -> {
                    val sc = if (k < 0.2f) easeOutBack(k / 0.2f) else 1f + (k - 0.2f) * 0.1f
                    val a = if (k > 0.75f) 1f - (k - 0.75f) / 0.25f else 1f
                    c.save(); c.translate(q.x, q.y); c.scale(sc, sc); c.rotate(-6f + sin(q.t * 9f) * 2f)
                    Draw.burst(p, 0f, 0f, q.size * 2.3f, 14, q.kind, 0.72f)
                    f.color = alphaF(Pal.INK, a); c.save(); c.scale(1.06f, 1.1f); c.drawPath(p, f); c.restore()
                    f.color = alphaF(0xFFE04A3B.toInt(), a); c.drawPath(p, f)
                    val fs = Draw.fit(q.text ?: "", q.size, q.size * 4.3f, Fonts.title)
                    Draw.toon(c, q.text ?: "", 0f, 0f, fs, alphaF(q.col, a), Fonts.title, fs * 0.12f, fs * 0.05f)
                    c.restore()
                }
                NOTE -> {
                    val a = if (k > 0.85f) 1f - (k - 0.85f) / 0.15f else 1f
                    c.save(); c.translate(q.x, q.y); c.rotate(sin(q.t * 8f) * 15f)
                    f.color = alphaF(Pal.INK, a)
                    rf.set(-15f, 6f, 1f, 18f); c.drawOval(rf, f); c.drawRect(-3f, -22f, 2f, 12f, f)
                    p.reset(); p.moveTo(2f, -22f); p.cubicTo(10f, -14f, 18f, -12f, 14f, 0f); p.cubicTo(13f, -8f, 9f, -11f, 2f, -12f); p.close(); c.drawPath(p, f)
                    f.color = alphaF(Pal.GOLD, a); rf.set(-12.5f, 8.5f, -1.5f, 15.5f); c.drawOval(rf, f)
                    c.restore()
                }
            }
        }
    }
}
