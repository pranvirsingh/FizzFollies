package com.pranvir.fizz

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin

/**
 * The old-projector look laid over everything: grain that changes 24 times a second, the odd scratch and speck of
 * dust, a soft flicker, gate weave and a warm vignette. All drawn in raw pixels.
 */
class Film {
    private val grain = Array(3) { k -> makeGrain(k) }
    private val shaders = Array(3) { BitmapShader(grain[it], Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    private val gp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val path = Path()
    private var vig: RadialGradient? = null
    private var vigW = 0; private var vigH = 0
    private val vp = Paint()
    var t = 0f
    private var frame = 0
    private val scratchX = FloatArray(3); private val scratchLife = FloatArray(3); private val scratchDark = BooleanArray(3)
    private val rng = Rng(77L)
    var weaveX = 0f; var weaveY = 0f
    var enabled = true
    var strength = 1f

    private fun makeGrain(k: Int): Bitmap {
        val n = 128
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val px = IntArray(n * n)
        val r = Rng(1000L + k)
        for (i in px.indices) {
            val v = r.nextFloat()
            px[i] = when {
                v < 0.12f -> withAlpha(0xFF1A1006.toInt(), (r.nextFloat() * 46).toInt())
                v < 0.2f -> withAlpha(0xFFFFF4DC.toInt(), (r.nextFloat() * 38).toInt())
                else -> 0
            }
        }
        b.setPixels(px, 0, n, 0, 0, n, n)
        return b
    }

    fun update(dt: Float) {
        t += dt
        val f = (t * 24f).toInt()
        if (f != frame) {
            frame = f
            // gate weave: a slow drift plus a tiny jitter, in screen pixels
            weaveX = sin(t * 1.3f) * 0.6f + (rng.nextFloat() - 0.5f) * 0.6f
            weaveY = sin(t * 0.9f + 1f) * 0.8f + (rng.nextFloat() - 0.5f) * 0.8f
            for (k in 0 until 3) {
                if (scratchLife[k] > 0f) { scratchX[k] += (rng.nextFloat() - 0.5f) * 3f; continue }
                if (rng.chance(0.012f)) { scratchLife[k] = rng.range(0.15f, 0.9f); scratchX[k] = rng.nextFloat(); scratchDark[k] = rng.chance(0.6f) }
            }
        }
        for (k in 0 until 3) scratchLife[k] -= dt
    }

    /** Overlay on a w x h pixel canvas (no transform). */
    fun draw(c: Canvas, w: Int, h: Int) {
        if (!enabled) { vignette(c, w, h, 0.6f); return }
        val gi = frame % 3
        m.setTranslate(hash01(frame, 1) * 128f, hash01(frame, 2) * 128f)
        shaders[gi].setLocalMatrix(m)
        gp.alpha = 255; gp.shader = shaders[gi]; gp.alpha = (255 * strength).toInt()
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gp)
        gp.shader = null
        // flicker
        val fl = hash01(frame / 2, 9)
        p.style = Paint.Style.FILL
        p.color = if (fl < 0.5f) withAlpha(0xFF000000.toInt(), (fl * 22 * strength).toInt()) else withAlpha(0xFFFFF0D0.toInt(), ((fl - 0.5f) * 14 * strength).toInt())
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // scratches
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.BUTT
        for (k in 0 until 3) if (scratchLife[k] > 0f) {
            val x = scratchX[k] * w
            p.strokeWidth = max(1f, w / 900f) * (if (scratchDark[k]) 1.4f else 1f)
            p.color = if (scratchDark[k]) withAlpha(0xFF201008.toInt(), (110 * strength).toInt()) else withAlpha(0xFFFFF6E0.toInt(), (90 * strength).toInt())
            path.reset(); path.moveTo(x, 0f)
            path.quadTo(x + sin(t * 3f + k) * w * 0.004f, h * 0.5f, x + sin(t * 2f + k * 2) * w * 0.006f, h.toFloat())
            c.drawPath(path, p)
        }
        // dust and hairs, a new handful every frame
        p.style = Paint.Style.FILL
        val nd = (hash01(frame, 5) * 4f).toInt()
        for (q in 0 until nd) {
            val x = hash01(frame, 10 + q) * w; val y = hash01(frame, 20 + q) * h
            val r = (1f + hash01(frame, 30 + q) * 2.6f) * w / 1080f
            p.color = withAlpha(0xFF1A0E06.toInt(), (150 * strength).toInt())
            c.drawCircle(x, y, r, p)
        }
        if (hash01(frame, 40) < 0.05f) {
            val x = hash01(frame, 41) * w; val y = hash01(frame, 42) * h; val L = w * 0.05f
            p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, w / 1000f); p.color = withAlpha(0xFF1A0E06.toInt(), (130 * strength).toInt())
            path.reset(); path.moveTo(x, y); path.cubicTo(x + L * 0.4f, y - L * 0.5f, x + L * 0.6f, y + L * 0.5f, x + L, y + L * 0.1f)
            c.drawPath(path, p)
        }
        vignette(c, w, h, 1f)
    }

    private fun vignette(c: Canvas, w: Int, h: Int, k: Float) {
        if (vig == null || vigW != w || vigH != h) {
            vigW = w; vigH = h
            vig = RadialGradient(w / 2f, h * 0.46f, max(w, h) * 0.72f,
                intArrayOf(0x00000000, 0x00000000, 0x501E0E04, 0xB01A0A02.toInt()), floatArrayOf(0f, 0.5f, 0.82f, 1f), Shader.TileMode.CLAMP)
        }
        vp.alpha = 255; vp.shader = vig; vp.alpha = (255 * k).toInt()
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vp)
    }

    fun release() { for (b in grain) b.recycle() }

    companion object {
        private val ip = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ipath = Path()
        /** Classic iris: everything outside a circle goes black. r in the same units as the canvas. */
        fun iris(c: Canvas, w: Float, h: Float, cx: Float, cy: Float, r: Float) {
            if (r > kotlin.math.hypot(w, h) * 1.2f) return
            ipath.reset()
            ipath.fillType = Path.FillType.EVEN_ODD
            ipath.addRect(-10f, -10f, w + 10f, h + 10f, Path.Direction.CW)
            if (r > 0.5f) ipath.addCircle(cx, cy, r, Path.Direction.CW)
            ip.style = Paint.Style.FILL; ip.color = 0xFF0B0704.toInt()
            c.drawPath(ipath, ip)
            ipath.fillType = Path.FillType.WINDING
        }
    }
}
