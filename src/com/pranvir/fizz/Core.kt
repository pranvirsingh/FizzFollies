package com.pranvir.fizz

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val TAU = (2 * PI).toFloat()
const val PI_F = PI.toFloat()

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
fun smooth(t: Float): Float { val x = clamp01(t); return x * x * (3f - 2f * x) }
fun approach(v: Float, target: Float, rate: Float) = if (v < target) min(target, v + rate) else max(target, v - rate)
fun easeOutBack(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val t = clamp01(x) - 1f; return 1f + c3 * t * t * t + c1 * t * t }
fun easeOut(x: Float): Float { val t = 1f - clamp01(x); return 1f - t * t * t }
fun easeIn(x: Float): Float { val t = clamp01(x); return t * t * t }
/** Damped spring settling from 1 to 0 with an overshoot, like a rubber-hose landing. */
fun boing(t: Float, freq: Float = 3.2f, damp: Float = 5f): Float = if (t <= 0f) 0f else (kotlin.math.exp(-damp * t) * cos(TAU * freq * t))

/** Cheap deterministic hash to [0,1). */
fun hash01(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216f
}

fun lerpColor(a: Int, b: Int, t: Float): Int {
    val tt = clamp01(t)
    val aa = (a ushr 24) and 255; val ar = (a shr 16) and 255; val ag = (a shr 8) and 255; val ab = a and 255
    val ba = (b ushr 24) and 255; val br = (b shr 16) and 255; val bg = (b shr 8) and 255; val bb = b and 255
    return ((aa + (ba - aa) * tt).toInt() shl 24) or ((ar + (br - ar) * tt).toInt() shl 16) or
        ((ag + (bg - ag) * tt).toInt() shl 8) or (ab + (bb - ab) * tt).toInt()
}
fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
fun alphaF(c: Int, f: Float): Int = withAlpha(c, (((c ushr 24) and 255) * clamp01(f)).toInt())
fun scaleRgb(c: Int, k: Float): Int {
    val r = (((c shr 16) and 255) * k).toInt().coerceIn(0, 255)
    val g = (((c shr 8) and 255) * k).toInt().coerceIn(0, 255)
    val b = ((c and 255) * k).toInt().coerceIn(0, 255)
    return (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
}

/** The show's palette: warm two-strip-Technicolor print colours on cream paper. */
object Pal {
    const val INK = 0xFF1E140D.toInt()
    const val INK_SOFT = 0xFF3A2A1E.toInt()
    const val CREAM = 0xFFF5E8C8.toInt()
    const val PAPER = 0xFFE8D3A2.toInt()
    const val SEPIA = 0xFF6B4A2B.toInt()
    const val GOLD = 0xFFE7B347.toInt()
    const val GOLD_DK = 0xFF9C6B1E.toInt()
    const val VELVET = 0xFFB3262E.toInt()
    const val VELVET_DK = 0xFF5A0E15.toInt()
    const val WOOD = 0xFF8A5A33.toInt()
    const val WOOD_DK = 0xFF4E2F18.toInt()
    const val TEAL_DK = 0xFF1F4F55.toInt()
    const val NIGHT = 0xFF241A2E.toInt()
    const val RED = 0xFFD8402F.toInt()
    const val GREEN = 0xFF4FA35A.toInt()
    /** Bubble critter colours: cherry, butter, mint, cornflower, plum, apple. */
    val BUB = intArrayOf(0xFFE04A3B.toInt(), 0xFFF2BE38.toInt(), 0xFF2FAAA0.toInt(), 0xFF4C7CD8.toInt(), 0xFF9C5BC4.toInt(), 0xFF78BA3E.toInt())
    val NAMES = arrayOf("CHERRY", "BUTTER", "MINT", "BLUEBELL", "PLUM", "PICKLE")
}

object Fonts {
    var title: Typeface = Typeface.DEFAULT_BOLD   // Fascinate: big cartoon title cards
    var chunky: Typeface = Typeface.DEFAULT_BOLD  // Ultra: numbers, buttons
    var body: Typeface = Typeface.DEFAULT_BOLD    // Josefin Sans bold: readable text
    var deco: Typeface = Typeface.DEFAULT         // Limelight: art-deco labels
}

object Draw {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val grad = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = Path()
    val path2 = Path()
    val r1 = RectF()
    val r2 = RectF()
    private val fm = Paint.FontMetrics()

    fun text(c: Canvas, s: String, x: Float, cy: Float, size: Float, col: Int, face: Typeface = Fonts.body, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f) {
        text.typeface = face; text.textSize = size; text.color = col; text.textAlign = align
        text.letterSpacing = spacing; text.style = Paint.Style.FILL
        text.getFontMetrics(fm)
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, text)
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = 0f
    }

    /** Cartoon lettering: ink outline, optional drop shadow, solid fill. */
    fun toon(c: Canvas, s: String, x: Float, cy: Float, size: Float, col: Int, face: Typeface = Fonts.chunky,
             outline: Float = size * 0.14f, shadow: Float = size * 0.07f, ink: Int = Pal.INK, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f) {
        text.typeface = face; text.textSize = size; text.textAlign = align; text.letterSpacing = spacing
        text.getFontMetrics(fm)
        val y = cy - (fm.ascent + fm.descent) / 2f
        text.strokeJoin = Paint.Join.ROUND
        if (shadow > 0f) {
            text.style = Paint.Style.FILL_AND_STROKE; text.strokeWidth = outline; text.color = alphaF(ink, ((col ushr 24) and 255) / 255f)
            c.drawText(s, x + shadow, y + shadow, text)
        }
        if (outline > 0f) {
            text.style = Paint.Style.FILL_AND_STROKE; text.strokeWidth = outline; text.color = alphaF(ink, ((col ushr 24) and 255) / 255f)
            c.drawText(s, x, y, text)
        }
        text.style = Paint.Style.FILL; text.color = col
        c.drawText(s, x, y, text)
        text.textAlign = Paint.Align.CENTER; text.letterSpacing = 0f
    }

    fun width(s: String, size: Float, face: Typeface = Fonts.body, spacing: Float = 0f): Float {
        text.typeface = face; text.textSize = size; text.letterSpacing = spacing
        val w = text.measureText(s)
        text.letterSpacing = 0f
        return w
    }

    fun fit(s: String, size: Float, maxW: Float, face: Typeface = Fonts.body, spacing: Float = 0f): Float {
        val w = width(s, size, face, spacing)
        return if (w <= maxW || w <= 0f) size else size * maxW / w
    }

    /** Bouncy title: each letter rides its own little wave and squash, like a 1930s title card. */
    fun bouncy(c: Canvas, s: String, x: Float, cy: Float, size: Float, col: Int, t: Float, face: Typeface = Fonts.title, amp: Float = 0.08f, outline: Float = size * 0.1f) {
        val total = width(s, size, face)
        var cx = x - total / 2f
        for ((i, ch) in s.withIndex()) {
            val str = ch.toString()
            val w = width(str, size, face)
            if (ch != ' ') {
                val ph = t * 5.5f - i * 0.55f
                val dy = sin(ph) * size * amp
                val sq = 1f + 0.06f * sin(ph + 1.2f)
                c.save()
                c.translate(cx + w / 2f, cy + dy)
                c.scale(1f / sq, sq)
                c.rotate(sin(ph * 0.7f) * 3f)
                toon(c, str, 0f, 0f, size, col, face, outline, size * 0.06f)
                c.restore()
            }
            cx += w
        }
    }

    /** Wobbly hand-inked circle outline as a filled ring; thicker on the shadow side. [boil] picks the wobble. */
    fun inkRing(p: Path, cx: Float, cy: Float, r: Float, w: Float, boil: Int, wob: Float = 0.018f) {
        p.reset()
        val n = 40
        for (k in 0..n) {
            val a = k * TAU / n
            val rr = r * (1f + wob * (sin(a * 3f + boil * 2.1f) * 0.6f + sin(a * 5f + boil * 4.3f) * 0.4f))
            val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        for (k in n downTo 0) {
            val a = k * TAU / n
            val rr = r * (1f + wob * (sin(a * 3f + boil * 2.1f) * 0.6f + sin(a * 5f + boil * 4.3f) * 0.4f))
            val ww = w * (1f + 0.5f * cos(a - PI_F / 4f)) * (1f + 0.12f * sin(a * 7f + boil * 1.7f))
            val x = cx + cos(a) * (rr - ww); val y = cy + sin(a) * (rr - ww)
            if (k == n) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** A brush stroke between two points that swells in the middle. */
    fun brush(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int) {
        val dx = x1 - x0; val dy = y1 - y0
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx = -dy / len * w * 0.5f; val ny = dx / len * w * 0.5f
        path2.reset()
        path2.moveTo(x0, y0)
        path2.quadTo((x0 + x1) / 2 + nx, (y0 + y1) / 2 + ny, x1, y1)
        path2.quadTo((x0 + x1) / 2 - nx, (y0 + y1) / 2 - ny, x0, y0)
        path2.close()
        fill.color = col
        c.drawPath(path2, fill)
    }

    fun circle(c: Canvas, x: Float, y: Float, r: Float, col: Int) { fill.color = col; c.drawCircle(x, y, r, fill) }
    fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, col: Int) { fill.color = col; r1.set(x - rx, y - ry, x + rx, y + ry); c.drawOval(r1, fill) }
    fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int) { stroke.color = col; stroke.strokeWidth = w; c.drawLine(x0, y0, x1, y1, stroke) }
    fun ring(c: Canvas, x: Float, y: Float, r: Float, w: Float, col: Int) { stroke.color = col; stroke.strokeWidth = w; c.drawCircle(x, y, r, stroke) }

    /** Five-point star path. */
    fun star(p: Path, cx: Float, cy: Float, r: Float, inner: Float = 0.45f, rot: Float = -PI_F / 2f, points: Int = 5) {
        p.reset()
        for (k in 0 until points * 2) {
            val a = rot + k * PI_F / points
            val rr = if (k % 2 == 0) r else r * inner
            val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** Spiky cartoon burst ("POW!") path. */
    fun burst(p: Path, cx: Float, cy: Float, r: Float, spikes: Int, seed: Int, inner: Float = 0.62f) {
        p.reset()
        for (k in 0 until spikes * 2) {
            val a = k * PI_F / spikes + hash01(seed, k) * 0.12f
            val rr = if (k % 2 == 0) r * (0.85f + 0.3f * hash01(seed + 7, k)) else r * inner
            val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** Art-deco panel: rounded plate with a double gold frame and ink edge. */
    fun decoPanel(c: Canvas, r: RectF, fillCol: Int, rad: Float = 26f, frame: Int = Pal.GOLD, lw: Float = 6f) {
        fill.color = withAlpha(Pal.INK, 120)
        r2.set(r.left + 8f, r.top + 10f, r.right + 8f, r.bottom + 10f)
        c.drawRoundRect(r2, rad, rad, fill)
        fill.color = Pal.INK
        r2.set(r.left - lw * 0.9f, r.top - lw * 0.9f, r.right + lw * 0.9f, r.bottom + lw * 0.9f)
        c.drawRoundRect(r2, rad + lw, rad + lw, fill)
        fill.color = fillCol
        c.drawRoundRect(r, rad, rad, fill)
        stroke.color = frame; stroke.strokeWidth = lw * 0.55f
        r2.set(r.left + lw * 1.4f, r.top + lw * 1.4f, r.right - lw * 1.4f, r.bottom - lw * 1.4f)
        c.drawRoundRect(r2, rad * 0.7f, rad * 0.7f, stroke)
        stroke.strokeWidth = lw * 0.25f
        r2.set(r.left + lw * 2.6f, r.top + lw * 2.6f, r.right - lw * 2.6f, r.bottom - lw * 2.6f)
        c.drawRoundRect(r2, rad * 0.55f, rad * 0.55f, stroke)
        // deco corner fans
        for (k in 0 until 4) {
            val x = if (k % 2 == 0) r.left + lw * 2.6f else r.right - lw * 2.6f
            val y = if (k < 2) r.top + lw * 2.6f else r.bottom - lw * 2.6f
            val sx = if (k % 2 == 0) 1f else -1f; val sy = if (k < 2) 1f else -1f
            for (q in 0 until 3) {
                val a = (q + 1) * 0.36f
                stroke.strokeWidth = lw * 0.25f
                c.drawLine(x, y, x + sx * cos(a) * rad * 0.9f, y + sy * sin(a) * rad * 0.9f, stroke)
            }
        }
    }
}

/** Number formatting that never depends on the phone's locale (digits must exist in our fonts). */
fun fmt(pattern: String, v: Int): String = String.format(java.util.Locale.US, pattern, v)
