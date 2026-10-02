package com.pranvir.fizz

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

object Sfx {
    const val SHOOT = 0; const val LAND = 1; const val POP = 2; const val SWAP = 3; const val NOPE = 4; const val POWER = 5
    const val TRUMPET = 6; const val FUSE = 7; const val SPLAT = 8; const val CHEER = 9; const val BOOM = 10; const val CLANK = 11
    const val WHISTLE = 12; const val SPOOK = 13; const val BONK = 14; const val BOSS_DOWN = 15; const val THROW = 16; const val THUD = 17
    const val NOTE = 18; const val TICKET = 19; const val WIN = 20; const val FAIL = 21; const val FIREWORK = 22; const val TAP = 23
    const val STAR = 24; const val CURTAIN = 25; const val HONK = 26; const val PROJECTOR = 27
    const val COUNT = 28
}

/** Music scenes the mixer crossfades between. */
object Scene { const val SILENT = -1; const val MENU = 0; const val PLAY = 1; const val BOSS = 2 }

/**
 * Everything audible is synthesised here: cartoon sound effects (slide whistles, bonks, a sad trombone) and three
 * hot-jazz tunes in the 1930s manner, each rendered as stems (rhythm, harmony, lead) so the band can swell with
 * the applause.
 */
object Synth {
    const val RATE = 22050
    const val BPM = 138f
    private const val TAU = (2 * PI).toFloat()
    val BEAT = 60f / BPM
    val BAR = BEAT * 4f
    const val BARS = 16
    val LOOP_N = (BAR * BARS * RATE).toInt()
    const val SONGS = 3
    const val STEMS = 3   // 0 rhythm (tuba, drums), 1 harmony (banjo, piano), 2 lead (clarinet, xylophone)

    fun hz(m: Float) = (440.0 * 2.0.pow((m - 69.0) / 12.0)).toFloat()
    private fun buf(sec: Float) = FloatArray((sec * RATE).toInt().coerceAtLeast(1))

    fun pcm(f: FloatArray, gain: Float): ShortArray {
        var peak = 0.0001f
        for (v in f) { val a = abs(v); if (a > peak) peak = a }
        val g = gain / peak
        return ShortArray(f.size) { (tanh((f[it] * g).toDouble()) * 30000).toInt().toShort() }
    }

    /** Fixed-gain conversion (stems keep their relative levels). */
    fun pcmFixed(f: FloatArray, gain: Float): ShortArray = ShortArray(f.size) { (tanh((f[it] * gain).toDouble()) * 30000).toInt().toShort() }

    private fun put(b: FloatArray, i: Int, v: Float, wrap: Boolean) {
        val n = b.size
        if (wrap) b[((i % n) + n) % n] += v else if (i in 0 until n) b[i] += v
    }

    // ------------------------------------------------------------------ voices

    private fun sine(b: FloatArray, start: Float, f0: Float, amp: Float, decay: Float, att: Float = 0.003f, f1: Float = f0, glide: Float = 30f, wrap: Boolean = false, dur: Float = 4f) {
        val o = (start * RATE).toInt()
        var ph = 0f
        val n = (dur * RATE).toInt()
        for (j in 0 until n) {
            val t = j / RATE.toFloat()
            val e = (if (t < att) t / att else 1f) * exp(-(t - att).coerceAtLeast(0f) * decay)
            if (t > att && e < 0.0004f) break
            val fr = f1 + (f0 - f1) * exp(-t * glide)
            ph += fr / RATE
            put(b, o + j, sin(TAU * ph) * e * amp, wrap)
        }
    }

    /** Pitch-gliding sine with vibrato: slide whistles, theremins. */
    private fun slide(b: FloatArray, start: Float, dur: Float, f0: Float, f1: Float, amp: Float, vib: Float = 0f, breath: Float = 0.05f, rnd: Random? = null, curve: Float = 1f) {
        val o = (start * RATE).toInt(); val n = (dur * RATE).toInt()
        var ph = 0f
        for (j in 0 until n) {
            val t = j / RATE.toFloat(); val k = t / dur
            val e = min(1f, t / 0.02f) * min(1f, (dur - t) / 0.05f)
            val fr = f0 * (f1 / f0).pow(k.pow(curve)) * (1f + vib * sin(TAU * 6f * t))
            ph += fr / RATE
            val nz = if (rnd != null) (rnd.nextFloat() * 2f - 1f) * breath else 0f
            put(b, o + j, (sin(TAU * ph) + 0.15f * sin(TAU * ph * 2f) + nz) * e * amp, false)
        }
    }

    private fun noise(b: FloatArray, start: Float, len: Float, amp: Float, lpHz: Float, hp: Boolean, rnd: Random, decay: Float, att: Float = 0.001f, wrap: Boolean = false) {
        val o = (start * RATE).toInt(); val cnt = (len * RATE).toInt()
        var y = 0f
        val a = 1f - exp(-TAU * lpHz / RATE)
        for (j in 0 until cnt) {
            val t = j / RATE.toFloat()
            val e = (if (t < att) t / att else 1f) * exp(-t * decay)
            val w = rnd.nextFloat() * 2f - 1f
            y += a * (w - y)
            put(b, o + j, (if (hp) w - y else y) * e * amp, wrap)
        }
    }

    /** Band-limited-ish brass/reed: saw or square through a swept low-pass. */
    private fun horn(b: FloatArray, start: Float, dur: Float, f: Float, amp: Float, bright: Float, square: Boolean = false, vib: Float = 0.006f,
                     att: Float = 0.03f, rel: Float = 0.08f, wah: Float = 0f, bend: Float = 0f, wrap: Boolean = false) {
        val o = (start * RATE).toInt(); val n = ((dur + rel) * RATE).toInt()
        var ph = 0f; var lp = 0f; var lp2 = 0f
        for (j in 0 until n) {
            val t = j / RATE.toFloat()
            val e = (if (t < att) t / att else 1f) * (if (t > dur) exp(-(t - dur) / rel * 3f) else 1f)
            val fr = f * (1f + vib * sin(TAU * 5.5f * t) * clamp01(t / 0.25f)) * (1f + bend * exp(-t * 18f))
            ph += fr / RATE; if (ph >= 1f) ph -= 1f
            val raw = if (square) (if (ph < 0.5f) 1f else -1f) else ph * 2f - 1f
            val cut = (fr * (1.5f + bright * e * 4f) * (1f + wah * (0.5f + 0.5f * sin(TAU * 3f * t)))).coerceAtMost(9000f)
            val a = 1f - exp(-TAU * cut / RATE)
            lp += a * (raw - lp); lp2 += a * (lp - lp2)
            put(b, o + j, lp2 * e * amp, wrap)
        }
    }

    /** Karplus-Strong pluck: banjo. */
    private fun pluck(b: FloatArray, start: Float, f: Float, amp: Float, rnd: Random, decay: Float = 0.996f, bright: Float = 0.5f, wrap: Boolean = false, len: Float = 0.9f) {
        val period = max(2, (RATE / f).toInt())
        val line = FloatArray(period) { rnd.nextFloat() * 2f - 1f }
        val o = (start * RATE).toInt(); val n = (len * RATE).toInt()
        var idx = 0; var last = 0f
        for (j in 0 until n) {
            val cur = line[idx]
            val nxt = line[(idx + 1) % period]
            val v = (cur * (0.5f + bright * 0.5f) + nxt * (0.5f - bright * 0.5f)) * decay
            line[idx] = v
            idx = (idx + 1) % period
            last += 0.6f * (cur - last)
            put(b, o + j, (cur * 0.7f + last * 0.3f) * amp * min(1f, (n - j) / 400f), wrap)
        }
    }

    /** Struck bar: xylophone / bell (sine partials, fast decay). */
    private fun xylo(b: FloatArray, start: Float, f: Float, amp: Float, wrap: Boolean = false, bell: Boolean = false) {
        sine(b, start, f, amp, if (bell) 4f else 14f, 0.001f, wrap = wrap, dur = if (bell) 2f else 0.6f)
        sine(b, start, f * (if (bell) 2.76f else 3.93f), amp * 0.35f, if (bell) 8f else 34f, 0.001f, wrap = wrap, dur = 0.5f)
        sine(b, start, f * (if (bell) 5.4f else 9.2f), amp * 0.12f, 60f, 0.001f, wrap = wrap, dur = 0.2f)
    }

    private fun piano(b: FloatArray, start: Float, f: Float, amp: Float, wrap: Boolean = true) {
        for ((k, a) in floatArrayOf(1f, 0.5f, 0.28f, 0.14f).withIndex()) sine(b, start, f * (k + 1) * (1f + k * 0.0015f), amp * a, 3.5f + k * 2.5f, 0.002f, wrap = wrap, dur = 1.2f)
    }

    // ------------------------------------------------------------------ sound effects

    fun render(id: Int): ShortArray {
        val rnd = Random(321L + id * 17)
        return when (id) {
            Sfx.SHOOT -> { val b = buf(0.32f); noise(b, 0f, 0.3f, 0.9f, 6000f, true, rnd, 11f, 0.01f); sine(b, 0f, 900f, 0.3f, 18f, 0.002f, 380f, 14f); pcm(b, 0.55f) }
            Sfx.LAND -> { val b = buf(0.15f); sine(b, 0f, 300f, 0.8f, 40f, 0.001f, 180f, 50f); noise(b, 0f, 0.02f, 0.3f, 3000f, false, rnd, 120f); pcm(b, 0.5f) }
            Sfx.POP -> { val b = buf(0.14f); sine(b, 0f, 620f, 0.9f, 32f, 0.001f, 1300f, 60f); noise(b, 0f, 0.012f, 0.45f, 9000f, true, rnd, 200f); pcm(b, 0.6f) }
            Sfx.SWAP -> { val b = buf(0.36f); slide(b, 0f, 0.17f, 700f, 1500f, 0.6f, rnd = rnd); slide(b, 0.17f, 0.17f, 1500f, 900f, 0.6f, rnd = rnd); pcm(b, 0.45f) }
            Sfx.NOPE -> { val b = buf(0.2f); sine(b, 0f, 190f, 0.9f, 22f, 0.002f, 150f, 20f); noise(b, 0f, 0.03f, 0.3f, 1200f, false, rnd, 60f); pcm(b, 0.45f) }
            Sfx.POWER -> { val b = buf(1.0f); slide(b, 0f, 0.5f, 400f, 2200f, 0.5f, vib = 0.01f, rnd = rnd, curve = 0.7f); xylo(b, 0.48f, hz(84f), 0.7f, bell = true); xylo(b, 0.56f, hz(91f), 0.5f, bell = true); pcm(b, 0.6f) }
            Sfx.TRUMPET -> {
                val b = buf(1.0f)
                for ((k, m) in floatArrayOf(67f, 72f, 76f, 79f).withIndex()) horn(b, k * 0.09f, if (k == 3) 0.5f else 0.08f, hz(m), 0.5f, 1.2f, vib = 0.012f, att = 0.012f, bend = -0.04f)
                pcm(b, 0.65f)
            }
            Sfx.FUSE -> { val b = buf(0.45f); for (k in 0 until 30) noise(b, k * 0.012f, 0.01f, 0.5f + rnd.nextFloat() * 0.4f, 7000f, true, rnd, 300f); sine(b, 0.36f, 240f, 0.6f, 25f, 0.002f, 120f); pcm(b, 0.55f) }
            Sfx.SPLAT -> { val b = buf(0.35f); noise(b, 0f, 0.3f, 1f, 1400f, false, rnd, 16f, 0.003f); sine(b, 0f, 120f, 0.8f, 20f, 0.002f, 60f, 30f); pcm(b, 0.6f) }
            Sfx.CHEER -> {
                val b = buf(1.2f)
                for (k in 0 until 90) { val t0 = rnd.nextFloat() * 0.9f; noise(b, t0, 0.03f, 0.3f + rnd.nextFloat() * 0.3f, 2500f + rnd.nextFloat() * 3000f, true, rnd, 70f) }
                slide(b, 0.05f, 0.45f, 1400f, 2600f, 0.25f, vib = 0.02f, rnd = rnd, curve = 0.5f)
                pcm(b, 0.55f)
            }
            Sfx.BOOM -> { val b = buf(1.1f); noise(b, 0f, 1f, 1f, 1100f, false, rnd, 4.5f); sine(b, 0f, 120f, 1f, 4f, 0.003f, 35f, 6f); noise(b, 0f, 0.08f, 0.6f, 7000f, true, rnd, 30f); pcm(b, 0.9f) }
            Sfx.CLANK -> { val b = buf(0.6f); for ((k, fr) in floatArrayOf(523f, 1187f, 1610f, 2433f, 3120f).withIndex()) sine(b, 0f, fr, 0.5f / (k + 1), 9f + k * 3f, 0.001f, dur = 0.6f); noise(b, 0f, 0.02f, 0.5f, 6000f, true, rnd, 150f); pcm(b, 0.6f) }
            Sfx.WHISTLE -> { val b = buf(0.75f); slide(b, 0f, 0.7f, 2200f, 500f, 0.6f, vib = 0.008f, rnd = rnd, curve = 1.3f); pcm(b, 0.5f) }
            Sfx.SPOOK -> { val b = buf(0.9f); slide(b, 0f, 0.85f, 520f, 380f, 0.5f, vib = 0.03f, breath = 0f); pcm(b, 0.45f) }
            Sfx.BONK -> {
                val b = buf(0.7f)
                sine(b, 0f, 880f, 0.7f, 30f, 0.001f); sine(b, 0f, 1320f, 0.4f, 40f, 0.001f)
                // the "boing" after
                val o = (0.05f * RATE).toInt(); var ph = 0f
                for (j in 0 until (0.6f * RATE).toInt()) { val t = j / RATE.toFloat(); val fr = 180f * (1f + 0.35f * sin(TAU * 13f * t) * exp(-t * 3f)); ph += fr / RATE; put(b, o + j, sin(TAU * ph) * exp(-t * 4.5f) * 0.6f, false) }
                pcm(b, 0.7f)
            }
            Sfx.BOSS_DOWN -> {
                val b = buf(2.4f)
                for ((k, m) in floatArrayOf(58f, 57f, 56f).withIndex()) horn(b, k * 0.42f, 0.36f, hz(m), 0.55f, 0.8f, vib = 0.01f, wah = 0.6f)
                horn(b, 1.26f, 0.9f, hz(55f), 0.6f, 0.8f, vib = 0.05f, wah = 0.8f)
                slide(b, 1.0f, 1.1f, 1800f, 300f, 0.3f, rnd = rnd, curve = 0.8f)
                pcm(b, 0.7f)
            }
            Sfx.THROW -> { val b = buf(0.35f); noise(b, 0f, 0.3f, 0.8f, 2200f, false, rnd, 6f, 0.12f); slide(b, 0f, 0.3f, 300f, 900f, 0.2f); pcm(b, 0.45f) }
            Sfx.THUD -> { val b = buf(0.6f); sine(b, 0f, 90f, 1f, 7f, 0.002f, 45f, 10f); noise(b, 0f, 0.15f, 0.7f, 600f, false, rnd, 20f); for (k in 0 until 3) noise(b, 0.08f + k * 0.07f, 0.03f, 0.25f, 3000f, true, rnd, 60f); pcm(b, 0.8f) }
            Sfx.NOTE -> { val b = buf(0.8f); for ((k, m) in floatArrayOf(76f, 79f, 84f).withIndex()) xylo(b, k * 0.07f, hz(m), 0.6f); pcm(b, 0.55f) }
            Sfx.TICKET -> { val b = buf(0.9f); noise(b, 0f, 0.05f, 0.6f, 5000f, true, rnd, 60f); xylo(b, 0.06f, hz(88f), 0.6f, bell = true); xylo(b, 0.06f, hz(95f), 0.4f, bell = true); pcm(b, 0.55f) }
            Sfx.WIN -> {
                val b = buf(2.2f)
                val seq = floatArrayOf(65f, 69f, 72f, 77f)
                for ((k, m) in seq.withIndex()) horn(b, k * 0.13f, if (k == 3) 0.9f else 0.11f, hz(m), 0.45f, 1.2f, vib = 0.014f, att = 0.012f, bend = -0.03f)
                for ((k, m) in seq.withIndex()) horn(b, k * 0.13f, if (k == 3) 0.9f else 0.11f, hz(m - 12f), 0.3f, 0.9f, square = true, att = 0.012f)
                noise(b, 0.39f, 1.4f, 0.25f, 7000f, true, rnd, 2.2f)
                xylo(b, 0.39f, hz(89f), 0.35f, bell = true)
                pcm(b, 0.75f)
            }
            Sfx.FAIL -> {
                val b = buf(2.6f)
                // the sad trombone: wah, wah, wah, waaaah
                for ((k, m) in floatArrayOf(55f, 54f, 53f).withIndex()) horn(b, k * 0.48f, 0.4f, hz(m), 0.6f, 0.9f, vib = 0.008f, wah = 0.9f)
                horn(b, 1.44f, 1.0f, hz(52f), 0.65f, 0.9f, vib = 0.045f, wah = 1f)
                pcm(b, 0.7f)
            }
            Sfx.FIREWORK -> { val b = buf(0.9f); slide(b, 0f, 0.35f, 900f, 2400f, 0.3f, rnd = rnd); noise(b, 0.35f, 0.5f, 0.9f, 2600f, false, rnd, 7f); for (k in 0 until 8) noise(b, 0.45f + rnd.nextFloat() * 0.35f, 0.02f, 0.4f, 7000f, true, rnd, 120f); pcm(b, 0.6f) }
            Sfx.TAP -> { val b = buf(0.12f); sine(b, 0f, 1250f, 0.8f, 45f, 0.001f); sine(b, 0f, 2100f, 0.3f, 60f, 0.001f); noise(b, 0f, 0.01f, 0.4f, 5000f, true, rnd, 200f); pcm(b, 0.45f) }
            Sfx.STAR -> { val b = buf(1.1f); xylo(b, 0f, hz(84f), 0.7f, bell = true); xylo(b, 0f, hz(91f), 0.4f, bell = true); noise(b, 0f, 0.05f, 0.3f, 8000f, true, rnd, 40f); pcm(b, 0.6f) }
            Sfx.CURTAIN -> { val b = buf(0.8f); noise(b, 0f, 0.75f, 0.9f, 1600f, false, rnd, 3f, 0.25f); noise(b, 0f, 0.75f, 0.3f, 6000f, true, rnd, 4f, 0.3f); pcm(b, 0.45f) }
            Sfx.HONK -> {
                val b = buf(0.6f)
                for (k in 0 until 2) horn(b, k * 0.22f, 0.14f, 340f, 0.6f, 2.5f, square = true, vib = 0f, att = 0.005f, bend = 0.25f)
                pcm(b, 0.55f)
            }
            Sfx.PROJECTOR -> { val b = buf(1.2f); for (k in 0 until 29) { noise(b, k * 0.041f, 0.012f, 0.5f, 3500f, true, rnd, 220f); sine(b, k * 0.041f, 95f, 0.25f, 60f, 0.001f) }; noise(b, 0f, 1.2f, 0.06f, 4000f, true, rnd, 0f); pcm(b, 0.4f) }
            else -> pcm(buf(0.05f), 0.1f)
        }
    }

    fun wav(pcm: ShortArray): ByteArray {
        val dataLen = pcm.size * 2
        val out = java.io.ByteArrayOutputStream(44 + dataLen)
        fun i32(v: Int) { out.write(v and 255); out.write((v shr 8) and 255); out.write((v shr 16) and 255); out.write((v shr 24) and 255) }
        fun i16(v: Int) { out.write(v and 255); out.write((v shr 8) and 255) }
        out.write("RIFF".toByteArray()); i32(36 + dataLen); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(dataLen)
        val bytes = ByteArray(dataLen)
        for (k in pcm.indices) { bytes[k * 2] = (pcm[k].toInt() and 255).toByte(); bytes[k * 2 + 1] = (pcm[k].toInt() shr 8).toByte() }
        out.write(bytes)
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ the band

    /** A chord: root (MIDI) and intervals. */
    private class Ch(val root: Int, val iv: IntArray)
    private val MAJ = intArrayOf(0, 4, 7); private val DOM7 = intArrayOf(0, 4, 7, 10); private val MIN = intArrayOf(0, 3, 7)
    private val MIN6 = intArrayOf(0, 3, 7, 9); private val MAJ6 = intArrayOf(0, 4, 7, 9); private val DIM = intArrayOf(0, 3, 6, 9)

    /** 16 bars x 2 chords (half-bar resolution). */
    private fun progression(song: Int): Array<Ch> {
        fun c(r: Int, iv: IntArray) = Ch(r, iv)
        return when (song) {
            // menu: a jaunty C-major march
            0 -> arrayOf(
                c(48, MAJ6), c(48, MAJ6), c(45, DOM7), c(45, DOM7), c(50, DOM7), c(50, DOM7), c(43, DOM7), c(43, DOM7),
                c(48, MAJ6), c(48, MAJ6), c(52, DOM7), c(52, DOM7), c(45, MIN), c(45, MIN), c(50, DOM7), c(43, DOM7),
                c(53, MAJ6), c(53, MAJ6), c(54, DIM), c(54, DIM), c(48, MAJ6), c(45, DOM7), c(50, DOM7), c(43, DOM7),
                c(48, MAJ6), c(52, DOM7), c(45, DOM7), c(45, DOM7), c(50, DOM7), c(43, DOM7), c(48, MAJ6), c(43, DOM7))
            // play: hot F-major stomp around the circle of fifths
            1 -> arrayOf(
                c(41, MAJ6), c(41, MAJ6), c(41, MAJ6), c(41, MAJ6), c(50, DOM7), c(50, DOM7), c(50, DOM7), c(50, DOM7),
                c(43, DOM7), c(43, DOM7), c(48, DOM7), c(48, DOM7), c(41, MAJ6), c(50, DOM7), c(43, DOM7), c(48, DOM7),
                c(41, MAJ6), c(41, MAJ6), c(41, DOM7), c(41, DOM7), c(46, MAJ6), c(46, MAJ6), c(46, MIN6), c(46, MIN6),
                c(41, MAJ6), c(50, DOM7), c(43, DOM7), c(48, DOM7), c(41, MAJ6), c(50, DOM7), c(43, DOM7), c(48, DOM7))
            // the Baron: D-minor villain stomp with a creeping bass
            else -> arrayOf(
                c(38, MIN6), c(38, MIN6), c(38, MIN6), c(38, MIN6), c(43, MIN), c(43, MIN), c(38, MIN6), c(38, MIN6),
                c(46, DOM7), c(46, DOM7), c(45, DOM7), c(45, DOM7), c(38, MIN6), c(43, MIN), c(45, DOM7), c(45, DOM7),
                c(38, MIN6), c(38, MIN6), c(40, DIM), c(40, DIM), c(43, MIN6), c(43, MIN6), c(38, MIN6), c(38, MIN6),
                c(46, DOM7), c(46, DOM7), c(45, DOM7), c(45, DOM7), c(38, MIN6), c(45, DOM7), c(38, MIN6), c(45, DOM7))
        }
    }

    /** Swing: the off-beat eighth lands two thirds of the way through the beat. */
    private fun swing(beat: Float): Float {
        val whole = kotlin.math.floor(beat)
        val frac = beat - whole
        val sw = if (frac < 0.5f) frac * (4f / 3f) else 2f / 3f + (frac - 0.5f) * (2f / 3f)
        return (whole + sw) * BEAT
    }

    private fun chordAt(prog: Array<Ch>, beat: Float): Ch = prog[((beat / 2f).toInt()).coerceIn(0, prog.size - 1)]

    /** Renders one stem of one song as a seamless loop. */
    fun renderStem(song: Int, stem: Int): ShortArray {
        val b = FloatArray(LOOP_N)
        val rnd = Random(9000L + song * 31 + stem)
        val prog = progression(song)
        val beats = BARS * 4
        when (stem) {
            0 -> { // rhythm: tuba oom-pah, brushes, woodblock, kick
                for (bt in 0 until beats) {
                    val ch = chordAt(prog, bt.toFloat())
                    val tb = bt * BEAT
                    if (bt % 2 == 0) {
                        // tuba on 1 and 3: root then fifth, with a walk-up into chord changes
                        val nxt = chordAt(prog, (bt + 2).toFloat())
                        val note = if (bt % 4 == 0) ch.root - 12 else ch.root - 12 + 7
                        horn(b, tb, BEAT * 0.75f, hz(note.toFloat()), 0.55f, 0.35f, vib = 0.004f, att = 0.025f, rel = 0.1f, wrap = true)
                        sine(b, tb, hz(note.toFloat()), 0.35f, 5f, 0.02f, wrap = true, dur = BEAT)
                        if (nxt.root != ch.root && bt % 4 == 2 && song != 2) horn(b, tb + BEAT, BEAT * 0.4f, hz((nxt.root - 13).toFloat()), 0.35f, 0.35f, att = 0.02f, wrap = true)
                        if (song == 2 && bt % 4 == 2) horn(b, tb + BEAT, BEAT * 0.4f, hz((ch.root - 11).toFloat()), 0.35f, 0.3f, att = 0.02f, wrap = true)
                        // soft kick
                        sine(b, tb, 110f, 0.5f, 18f, 0.002f, 55f, 25f, wrap = true, dur = 0.3f)
                    } else {
                        // brushes on 2 and 4
                        noise(b, tb, 0.18f, 0.35f, 5200f, true, rnd, 16f, 0.004f, wrap = true)
                    }
                    // swinging hi-hat / ride "ting, ting-a"
                    noise(b, swing(bt.toFloat()), 0.05f, 0.12f, 9000f, true, rnd, 60f, wrap = true)
                    if (bt % 2 == 1) noise(b, swing(bt + 0.5f), 0.04f, 0.09f, 9000f, true, rnd, 70f, wrap = true)
                    // woodblock accents at the end of each 4-bar phrase
                    if (bt % 16 == 14 || bt % 16 == 15) { sine(b, swing(bt + 0.5f), 1300f, 0.25f, 50f, 0.001f, wrap = true, dur = 0.2f); sine(b, tb, 950f, 0.25f, 50f, 0.001f, wrap = true, dur = 0.2f) }
                }
            }
            1 -> { // harmony: banjo strums on 2 and 4, a piano on the off-beats
                for (bt in 0 until beats) {
                    val ch = chordAt(prog, bt.toFloat())
                    val tb = bt * BEAT
                    if (bt % 2 == 1 || song == 2) {
                        for ((k, iv) in ch.iv.withIndex()) pluck(b, tb + k * 0.012f, hz((ch.root + 12 + iv).toFloat()), 0.16f, rnd, 0.995f, 0.6f, wrap = true, len = 0.6f)
                    }
                    if (song != 2 && bt % 2 == 1) for (iv in ch.iv) piano(b, swing(bt + 0.5f), hz((ch.root + 24 + iv).toFloat()), 0.05f)
                    if (song == 2 && bt % 4 == 0) for (iv in ch.iv) piano(b, tb, hz((ch.root + 12 + iv).toFloat()), 0.09f)
                }
            }
            else -> { // lead: clarinet tune, with xylophone answering
                val tune = melody(song, prog)
                for (n in tune) {
                    val st = swing(n[0]); val dur = n[1] * BEAT * 0.92f
                    if (song == 2) horn(b, st, dur, hz(n[2]), 0.32f, 0.7f, square = false, vib = 0.012f, att = 0.02f, rel = 0.07f, wah = 0.5f, wrap = true)
                    else horn(b, st, dur, hz(n[2]), 0.3f, 0.6f, square = true, vib = 0.01f, att = 0.025f, rel = 0.06f, wrap = true)
                }
                // xylophone answers at the ends of phrases
                for (bar in 0 until BARS) if (bar % 2 == 1) {
                    val ch = chordAt(prog, bar * 4f + 2f)
                    for ((k, iv) in ch.iv.take(3).withIndex()) xylo(b, swing(bar * 4f + 2f + k * 0.5f), hz((ch.root + 24 + iv).toFloat()), 0.18f, wrap = true)
                }
            }
        }
        return pcmFixed(b, when (stem) { 0 -> 0.9f; 1 -> 1.6f; else -> 0.62f })
    }

    /** Generates a hummable tune from chord tones and passing notes: [beat, length, midi]. */
    fun melodyDebug(song: Int) = melody(song, progression(song))
    private fun melody(song: Int, prog: Array<Ch>): List<FloatArray> {
        val rnd = Random(777L + song * 13)
        val out = ArrayList<FloatArray>()
        val base = if (song == 2) 62 else if (song == 0) 72 else 65
        // a rhythmic motif repeated with variation per 2-bar phrase
        val motifs = arrayOf(
            floatArrayOf(0f, 0.5f, 1f, 1.5f, 2f, 3f),
            floatArrayOf(0f, 1f, 1.5f, 2.5f, 3f),
            floatArrayOf(0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f),
            floatArrayOf(0f, 1.5f, 2f, 3f))
        var last = base + 4
        for (phrase in 0 until BARS / 2) {
            val mo = motifs[if (phrase % 4 == 3) 3 else (phrase % 2) + (if (phrase >= 4) 1 else 0)]
            for (barIn in 0 until 2) {
                val bar = phrase * 2 + barIn
                val ends = barIn == 1
                val pattern = if (ends) floatArrayOf(0f, 1f, 2f) else mo
                for ((k, o) in pattern.withIndex()) {
                    val beat = bar * 4f + o
                    val ch = chordAt(prog, beat)
                    // choose a chord tone near the last note (strong beats) or step from it (weak beats)
                    val strong = o % 1f == 0f
                    var note: Int
                    if (strong || rnd.nextFloat() < 0.5f) {
                        var best = last; var bd = 99
                        for (oct in -1..1) for (iv in ch.iv) {
                            val cand = ch.root + 24 + iv + oct * 12
                            val d = abs(cand - last) + (if (cand < base - 5 || cand > base + 12) 6 else 0) + rnd.nextInt(3)
                            if (d < bd && cand != last) { bd = d; best = cand }
                        }
                        note = best
                    } else note = last + (if (rnd.nextBoolean()) 2 else -2) - (if (last > base + 9) 2 else 0)
                    val next = if (k < pattern.size - 1) pattern[k + 1] else 4f
                    val len = if (ends && k == pattern.size - 1) 1.6f else (next - o)
                    out.add(floatArrayOf(beat, len, note.toFloat()))
                    last = note
                }
            }
        }
        return out
    }
}

/**
 * Mixes the stems live: crossfades between songs, swells the lead with the applause and adds a
 * gramophone crackle. Runs on the audio thread.
 */
class Mixer(private val stems: Array<Array<ShortArray>>) {
    private var pos = 0
    private val gain = Array(Synth.SONGS) { FloatArray(Synth.STEMS) }
    private val rnd = Random(5L)
    private var crackle = 0f
    private var hiss = 0f

    fun target(scene: Int, intensity: Float, song: Int, stem: Int): Float {
        if (scene == Scene.SILENT) return 0f
        val want = when (scene) { Scene.BOSS -> 2; Scene.MENU -> 0; else -> 1 }
        if (song != want) return 0f
        return when (stem) {
            0 -> 0.8f
            1 -> if (scene == Scene.MENU) 0.75f else 0.55f + 0.2f * intensity
            else -> if (scene == Scene.MENU) 0.7f else 0.35f + 0.45f * intensity
        }
    }

    fun render(out: ShortArray, scene: Int, intensity: Float, musicOn: Boolean, crackleOn: Boolean) {
        val n = Synth.LOOP_N
        val k = 1f / (Synth.RATE * 0.9f)
        for (i in out.indices) {
            var v = 0f
            for (s in 0 until Synth.SONGS) for (st in 0 until Synth.STEMS) {
                val tg = if (musicOn) target(scene, intensity, s, st) else 0f
                val g = gain[s][st]
                val ng = if (g < tg) min(tg, g + k) else max(tg, g - k)
                gain[s][st] = ng
                if (ng > 0.0005f) v += stems[s][st][pos] * ng
            }
            v *= 0.72f
            if (crackleOn && musicOn) {
                // gramophone: soft hiss plus the odd pop
                hiss += 0.2f * ((rnd.nextFloat() - 0.5f) - hiss)
                v += hiss * 260f
                if (rnd.nextFloat() < 0.00035f) crackle = (rnd.nextFloat() - 0.5f) * 5000f
                v += crackle; crackle *= 0.55f
            }
            out[i] = v.toInt().coerceIn(-32767, 32767).toShort()
            pos++; if (pos >= n) pos = 0
        }
    }

    /** Beat phase (0..1) of the music clock, for things that dance in time. */
    fun beatPhase(): Float { val sec = pos / Synth.RATE.toFloat(); return (sec / Synth.BEAT) % 1f }
}
