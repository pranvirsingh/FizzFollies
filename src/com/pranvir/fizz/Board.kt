package com.pranvir.fizz

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** What sits in a grid cell. */
object Kind {
    const val EMPTY = 0
    const val BUBBLE = 1   // plain coloured critter
    const val STONE = 2    // never matches, only falls or gets blown up
    const val BOMB = 3     // goes off when a neighbour pops or a shot strikes it
    const val CAGE = 4     // coloured critter behind bars; neighbouring pops wear the bars down
    const val SPOOK = 5    // coloured ghost that changes colour after every shot
    const val INK = 6      // spreading blot; a neighbouring pop wipes it out
    const val NOTE = 7     // coloured critter holding a music note (the goal of "Free the Music")
    const val TICKET = 8   // coloured critter holding a golden ticket
    fun colored(k: Int) = k == BUBBLE || k == CAGE || k == SPOOK || k == NOTE || k == TICKET
    fun matchable(k: Int) = k == BUBBLE || k == SPOOK || k == NOTE || k == TICKET
}

/** Result of tracing a shot from the seltzer nozzle. */
class Trace {
    val px = FloatArray(24); val py = FloatArray(24)
    var n = 0
    var hit = -1          // the occupied cell we bumped into
    var land = -1         // cell the bubble snaps into (-1: none)
    var boss = false
    var ceiling = false
    var ex = 0f; var ey = 0f   // contact point (bubble centre at touch)
    var length = 0f
    val through = IntArray(160); var nThrough = 0
    var throughBoss = false
    fun reset() { n = 0; hit = -1; land = -1; boss = false; ceiling = false; length = 0f; nThrough = 0; throughBoss = false }
    fun add(x: Float, y: Float) {
        if (n > 0) { val dx = x - px[n - 1]; val dy = y - py[n - 1]; length += sqrt(dx * dx + dy * dy) }
        if (n < px.size) { px[n] = x; py[n] = y; n++ } else { px[n - 1] = x; py[n - 1] = y }
    }
}

/**
 * Hex grid of bubbles in "board units": 1000 wide, the ceiling at y = [ceil]. Even rows hold 11 cells and odd rows 10,
 * shifted half a bubble to the right. Pure logic, shared by the game, the bot and the tests.
 */
class Board {
    companion object {
        const val COLS = 11
        const val ROWS = 22
        const val N = COLS * ROWS
        const val D = 88f
        const val R = 44f
        val RH = D * sqrt(3f) / 2f
        const val BX = 16f
        const val W = 1000f
        const val DEAD_ROWS = 16
        val DEAD_Y = 2 * R + (DEAD_ROWS - 1) * RH
        const val LAUNCH_X = 500f
        val LAUNCH_Y = DEAD_Y + 170f
        /** Fizz's siphon head pivots here; the loaded bubble sits [SPOUT] further along the aim. */
        val PIVOT_Y = LAUNCH_Y + 58f
        const val SPOUT = 58f
        const val HIT = D * 0.82f
        const val MIN_ANG = 0.13f
        const val MAX_ANG = (Math.PI - 0.13).toFloat()
        fun cols(r: Int) = if (r and 1 == 0) COLS else COLS - 1
        fun valid(r: Int, c: Int) = r >= 0 && r < ROWS && c >= 0 && c < cols(r)
        fun row(i: Int) = i / COLS
        fun col(i: Int) = i % COLS
        fun idx(r: Int, c: Int) = r * COLS + c
        fun cx(r: Int, c: Int) = BX + R + c * D + (if (r and 1 == 1) R else 0f)
        fun cxi(i: Int) = cx(row(i), col(i))
    }

    val kind = IntArray(N)
    val col = IntArray(N)
    val hp = IntArray(N)
    val boss = BooleanArray(N)
    var ceil = 0f
    var drops = 0
    var hasBoss = false
    var bossX = 500f
    var bossOy = 0f        // boss centre relative to the ceiling
    var bossR = 0f

    fun cy(r: Int) = ceil + R + r * RH
    fun cyi(i: Int) = cy(row(i))
    val bossY get() = ceil + bossOy
    /** Lowest row a bubble may occupy without spilling over the footlights. */
    val maxRow get() = DEAD_ROWS - 1 - drops

    fun copyFrom(o: Board) {
        System.arraycopy(o.kind, 0, kind, 0, N); System.arraycopy(o.col, 0, col, 0, N)
        System.arraycopy(o.hp, 0, hp, 0, N); System.arraycopy(o.boss, 0, boss, 0, N)
        ceil = o.ceil; drops = o.drops; hasBoss = o.hasBoss; bossX = o.bossX; bossOy = o.bossOy; bossR = o.bossR
    }

    fun clear() { kind.fill(0); col.fill(0); hp.fill(0); boss.fill(false); ceil = 0f; drops = 0; hasBoss = false }

    fun set(i: Int, k: Int, c: Int = 0, h: Int = 0) { kind[i] = k; col[i] = c; hp[i] = h }
    fun empty(i: Int) { kind[i] = Kind.EMPTY; col[i] = 0; hp[i] = 0 }

    /** Fills [out] with neighbour indices of cell i; returns the count. */
    fun neighbors(i: Int, out: IntArray): Int {
        val r = row(i); val c = col(i)
        var n = 0
        fun add(rr: Int, cc: Int) { if (valid(rr, cc)) out[n++] = idx(rr, cc) }
        add(r, c - 1); add(r, c + 1)
        if (r and 1 == 0) { add(r - 1, c - 1); add(r - 1, c); add(r + 1, c - 1); add(r + 1, c) }
        else { add(r - 1, c); add(r - 1, c + 1); add(r + 1, c); add(r + 1, c + 1) }
        return n
    }

    fun placeBoss(x: Float, oy: Float, r: Float) {
        hasBoss = true; bossX = x; bossOy = oy; bossR = r
        for (i in 0 until N) {
            val rr = row(i); val cc = col(i)
            if (!valid(rr, cc)) continue
            val dx = cxi(i) - x; val dy = (R + rr * RH) - oy
            boss[i] = dx * dx + dy * dy < (r + R * 0.15f) * (r + R * 0.15f)
            if (boss[i]) empty(i)
        }
    }

    fun clearBoss() { hasBoss = false; boss.fill(false) }

    fun count(pred: (Int) -> Boolean): Int { var n = 0; for (i in 0 until N) if (pred(kind[i])) n++; return n }
    fun occupied() = count { it != Kind.EMPTY }
    fun coloredCount() = count { Kind.colored(it) }

    /** Lowest occupied row, or -1. */
    fun lowestRow(): Int { for (i in N - 1 downTo 0) if (kind[i] != Kind.EMPTY) return row(i); return -1 }

    // ------------------------------------------------------------------ tracing

    private val nb = IntArray(6)

    /** Nearest occupied cell overlapping a bubble centred at (x, y) within [rad], or -1. */
    fun collide(x: Float, y: Float, rad: Float = HIT): Int {
        val rf = (y - ceil - R) / RH
        val r0 = floor(rf).toInt()
        var best = -1; var bd = rad * rad
        for (r in r0 - 1..r0 + 2) {
            if (r < 0 || r >= ROWS) continue
            val off = if (r and 1 == 1) R else 0f
            val cc = ((x - BX - R - off) / D).roundToInt()
            val yy = cy(r)
            for (c in cc - 1..cc + 1) {
                if (!valid(r, c)) continue
                val i = idx(r, c)
                if (kind[i] == Kind.EMPTY) continue
                val dx = cx(r, c) - x; val dy = yy - y
                val d = dx * dx + dy * dy
                if (d < bd) { bd = d; best = i }
            }
        }
        return best
    }

    private fun attached(i: Int): Boolean {
        if (row(i) == 0) return true
        val n = neighbors(i, nb)
        for (k in 0 until n) if (kind[nb[k]] != Kind.EMPTY || boss[nb[k]]) return true
        return false
    }

    /** Empty cell to snap into when a flying bubble centred at (x, y) touches cell [hit]. */
    fun snap(x: Float, y: Float, hit: Int): Int {
        var best = -1; var bd = Float.MAX_VALUE
        if (hit >= 0) {
            val n = neighbors(hit, nb)
            for (k in 0 until n) {
                val j = nb[k]
                if (kind[j] != Kind.EMPTY || boss[j]) continue
                val dx = cxi(j) - x; val dy = cyi(j) - y
                val d = dx * dx + dy * dy
                if (d < bd) { bd = d; best = j }
            }
            if (best >= 0) return best
        }
        // fallback: nearest empty attached cell around the point
        val r0 = floor((y - ceil - R) / RH).toInt()
        for (r in r0 - 2..r0 + 2) {
            if (r < 0 || r >= ROWS) continue
            for (c in 0 until cols(r)) {
                val j = idx(r, c)
                if (kind[j] != Kind.EMPTY || boss[j] || !attached(j)) continue
                val dx = cx(r, c) - x; val dy = cy(r) - y
                val d = dx * dx + dy * dy
                if (d < bd && d < (D * 1.6f) * (D * 1.6f)) { bd = d; best = j }
            }
        }
        return best
    }

    private fun ceilingCell(x: Float): Int {
        var best = -1; var bd = Float.MAX_VALUE
        for (c in 0 until cols(0)) {
            val j = idx(0, c)
            if (kind[j] != Kind.EMPTY || boss[j]) continue
            val d = abs(cx(0, c) - x)
            if (d < bd) { bd = d; best = j }
        }
        return if (bd < D * 1.2f) best else snap(x, ceil + R, -1)
    }

    /**
     * Follows a shot fired at [ang] (radians, 0 = right, PI/2 = straight up) until it touches something.
     * With [pierce] the shot ploughs through bubbles (and the boss) and only stops at the ceiling.
     */
    fun trace(ang: Float, t: Trace, pierce: Boolean = false, maxLen: Float = 6000f) {
        t.reset()
        var dx = cos(ang); var dy = -sin(ang)
        if (dy > -0.05f) dy = -0.05f
        var x = LAUNCH_X + dx * SPOUT; var y = PIVOT_Y + dy * SPOUT
        t.add(x, y)
        val step = 6f
        val minX = BX + R; val maxX = W - BX - R
        var travelled = 0f
        while (travelled < maxLen) {
            var nx = x + dx * step; val ny = y + dy * step
            if (nx < minX || nx > maxX) {
                val wall = if (nx < minX) minX else maxX
                val k = (wall - x) / (nx - x)
                t.add(wall, y + (ny - y) * k)
                nx = 2 * wall - nx; dx = -dx
            }
            travelled += step
            if (ny <= ceil + R) {
                val k = ((ceil + R) - y) / (ny - y)
                val hx = x + (nx - x) * k
                t.ex = hx; t.ey = ceil + R; t.ceiling = true
                t.add(hx, ceil + R)
                if (!pierce) t.land = ceilingCell(hx)
                return
            }
            if (hasBoss) {
                val bx = nx - bossX; val by = ny - bossY
                val rr = bossR + R * 0.7f
                if (bx * bx + by * by < rr * rr) {
                    if (pierce) t.throughBoss = true
                    else { t.boss = true; t.ex = x; t.ey = y; t.add(x, y); return }
                }
            }
            if (pierce) {
                val j = collide(nx, ny, D * 0.92f)
                if (j >= 0) {
                    var seen = false
                    for (q in 0 until t.nThrough) if (t.through[q] == j) { seen = true; break }
                    if (!seen && t.nThrough < t.through.size) t.through[t.nThrough++] = j
                    // the blast is wide: also take the neighbour on the other side of the line
                    val j2 = collide(nx + dy * R * 0.9f, ny - dx * R * 0.9f, D * 0.6f)
                    if (j2 >= 0 && t.nThrough < t.through.size) {
                        var s2 = false
                        for (q in 0 until t.nThrough) if (t.through[q] == j2) { s2 = true; break }
                        if (!s2) t.through[t.nThrough++] = j2
                    }
                }
            } else {
                val j = collide(nx, ny)
                if (j >= 0) {
                    t.hit = j; t.ex = x; t.ey = y
                    t.add(x, y)
                    t.land = snap(x, y, j)
                    return
                }
            }
            x = nx; y = ny
        }
        t.ex = x; t.ey = y; t.add(x, y)
    }
}
