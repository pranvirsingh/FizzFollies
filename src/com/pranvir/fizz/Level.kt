package com.pranvir.fizz

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The ten reels (chapters) of the show, twelve scenes each; scene 12 is always the Baron. */
object Reels {
    const val COUNT = 10
    const val PER = 12
    const val TOTAL = COUNT * PER
    val NAMES = arrayOf(
        "BARNYARD HOEDOWN", "BIG TOP BALLYHOO", "SPOOKY SHUFFLE", "SEA SHANTY", "TOYLAND TWO-STEP",
        "SKYSCRAPER SWING", "MOONBEAM MAMBO", "SNOWBALL SERENADE", "JUNGLE JAMBOREE", "THE BOILERWORKS")
    val TAGS = arrayOf(
        "Down on the farm the Baron bottled up the barn dance.",
        "The circus band went silent. Somebody pinched the oompah.",
        "Things go bump in the night, and the ghosts won't stay one colour.",
        "Anchors aweigh! The scenery drops lower with every shot.",
        "The toy shop's music box is full of ink. Don't let it spread.",
        "Swing time in the big city. Every trick in the book.",
        "Up where the moon croons. Double-locked cages ahead.",
        "A frosty number with ink, iron bars and a falling sky.",
        "Hot rhythm in the jungle. Six colours, no mercy.",
        "The Baron's own boiler room. The grand finale!")
    /** Goal per scene (C clear, N notes, P beat the drop); scene 12 is the boss. */
    private val GOALS = arrayOf(
        "CCNCNCNCNCN", "CNCNNCNCNCN", "NCNCNNCNCNC", "CNPCNPNCPNP", "NPCNPNCPNCN",
        "PNCNPCNPNCP", "NCPNPNCPNCN", "PNCPNPCNPNP", "NPCNPNCPNPC", "PNCPNPNCPNP")
    fun goal(n: Int): Int {
        val r = n / PER; val k = n % PER
        if (k == PER - 1) return Goal.BOSS
        return when (GOALS[r][k]) { 'N' -> Goal.NOTES; 'P' -> Goal.PRESS; else -> Goal.CLEAR }
    }
    /** Stars needed to open reel r (and the previous reel's Baron must be beaten). */
    fun starsNeeded(r: Int) = r * 18
}

/** Things that get a silent-film title card the first time they appear. */
object Intro {
    const val HOWTO = 0; const val NOTES = 1; const val APPLAUSE = 2; const val BOSS = 3; const val STONE = 4; const val BOMB = 5
    const val RAINBOW = 6; const val SPOOK = 7; const val TRUMPET = 8; const val CAGE = 9; const val PRESS = 10; const val INK = 11; const val CAGE2 = 12
    const val COUNT = 13
    /** First level index where each thing shows up. */
    val LEVEL = intArrayOf(0, 2, 2, 11, 12, 12, 14, 24, 26, 36, 38, 48, 72)
}

/**
 * Everything about one scene: goal, colours, shot budget, the Baron's temper and how to lay out the opening board.
 * Generated deterministically from the level number; budgets and star targets come from [LevelTable], which the
 * calibration tool fills by letting the bot play every level.
 */
class LevelSpec(val n: Int, variantOverride: Int = -1) {
    val reel = n / Reels.PER
    val idx = n % Reels.PER
    val goal = Reels.goal(n)
    val t = idx / (Reels.PER - 1f)
    /** Overall difficulty 0..1: rises through a reel, and each reel starts a notch above the last one's start. */
    val diff = min(1f, 0.06f + reel * 0.085f + t * 0.24f)
    val variant: Int
    var shots: Int
    /** Shots a good player needs in the unlimited modes (Beat the Drop, the Baron); beating it pays a bonus. */
    var par: Int = 0
    var pressEvery: Int
    var s2: Int
    var s3: Int
    val colors: Int
    val mercy: Float
    val inkEvery: Int
    val powers: Int
    val guide: Float
    val bossHp: Int
    val bossEvery: Int
    var bossSpawn: Int
    var bossPress: Int
    val bossExtra: Int
    val slack: Float

    init {
        val row = LevelTable.row(n)
        variant = if (variantOverride >= 0) variantOverride else row?.get(0) ?: 0
        colors = when {
            n < 2 -> 3
            else -> {
                val base = intArrayOf(4, 4, 5, 5, 5, 5, 6, 6, 6, 6)[reel]
                val late = if (reel == 1 && idx >= 6) 1 else if (reel == 5 && idx >= 6) 1 else 0
                val early = if (idx < 2 && reel > 0) 1 else 0
                (base + late - early).coerceIn(4, 6)
            }
        }
        mercy = (0.42f - diff * 0.34f).coerceIn(0.06f, 0.42f)
        inkEvery = if (reel >= 7) 2 else 3
        var p = 0
        if (n >= Intro.LEVEL[Intro.APPLAUSE]) p = p or (1 shl Power.FIRECRACKER)
        if (n >= Intro.LEVEL[Intro.RAINBOW]) p = p or (1 shl Power.RAINBOW)
        if (n >= Intro.LEVEL[Intro.TRUMPET]) p = p or (1 shl Power.TRUMPET)
        powers = p
        guide = 2600f - 1100f * diff
        slack = 1.9f - 0.62f * diff
        bossHp = 20 + reel * 2
        bossEvery = max(2, 4 - reel / 4)
        bossSpawn = 3 + reel / 3
        bossPress = 10 - reel / 3
        bossExtra = intArrayOf(Kind.EMPTY, Kind.STONE, Kind.SPOOK, Kind.CAGE, Kind.INK, Kind.STONE, Kind.SPOOK, Kind.INK, Kind.CAGE, Kind.INK)[reel]
        pressEvery = if (goal == Goal.PRESS) (9f - 4f * diff).roundToInt() else 0
        shots = 0; s2 = 0; s3 = 0
        if (row != null && variantOverride < 0) {
            // the opening reels get extra breathing room on top of the calibrated budget
            val ease = when (reel) { 0 -> 1.25f; 1 -> 1.12f; else -> 1f }
            if (limited) shots = (row[1] * ease).roundToInt() else par = row[1]
            if (goal == Goal.PRESS) pressEvery = row[2]
            if (goal == Goal.BOSS) bossPress = row[2]
            s2 = row[3]; s3 = row[4]
        } else {
            // rough defaults until calibrated
            shots = if (goal == Goal.CLEAR || goal == Goal.NOTES) 40 else 0
            par = 30
            s2 = 3000; s3 = 6000
        }
    }

    val limited get() = goal == Goal.CLEAR || goal == Goal.NOTES
    val seed: Long get() = 1932L * 7919L + n * 104729L + variant * 15485863L

    // ------------------------------------------------------------------ board layout

    fun build(b: Board) {
        b.clear()
        val rng = Rng(seed)
        val rows = rowsFor(rng)
        if (goal == Goal.BOSS) b.placeBoss(500f, Board.R + 2.2f * Board.RH, 148f)
        val pat = patternFor(rng)
        val ph = rng.nextFloat() * 6.28f
        val mask = BooleanArray(Board.N)
        for (r in 0 until rows) for (c in 0 until Board.cols(r)) {
            val i = Board.idx(r, c)
            if (b.boss[i]) continue
            val u = (Board.cx(r, c) - 500f) / 484f
            val v = if (rows > 1) r / (rows - 1f) else 0f
            mask[i] = shape(pat, u, v, r, c, ph, rng.s)
        }
        // keep only what hangs from the ceiling (or from the Baron)
        val keep = connected(b, mask)
        var count = keep.count { it }
        if (count < 16) { for (r in 0 until rows) for (c in 0 until Board.cols(r)) { val i = Board.idx(r, c); mask[i] = !b.boss[i] }; count = 0 }
        val final = if (count == 0) connected(b, mask) else keep
        paint(b, final, rng)
        features(b, rng)
    }

    private fun rowsFor(rng: Rng): Int {
        if (n == 0) return 4
        if (n == 1) return 5
        return when (goal) {
            Goal.CLEAR -> 5 + (diff * 5f).roundToInt() + rng.nextInt(2)
            Goal.NOTES -> 6 + (diff * 5f).roundToInt() + rng.nextInt(2)
            Goal.PRESS -> 5 + (diff * 3.5f).roundToInt() + rng.nextInt(2)
            else -> 6 + reel / 3
        }.coerceAtMost(11)
    }

    private fun patternFor(rng: Rng): Int {
        if (n < 2 || goal == Goal.BOSS) return 0
        val pool = when {
            reel == 0 && idx < 5 -> intArrayOf(0, 1, 7, 13)
            reel < 2 -> intArrayOf(0, 1, 2, 3, 5, 7, 9, 13)
            else -> intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
        }
        return pool[rng.nextInt(pool.size)]
    }

    private fun shape(p: Int, u0: Float, v: Float, r: Int, c: Int, ph: Float, salt: Long): Boolean {
        val u = abs(u0)
        return when (p) {
            1 -> u <= 1.05f - v * 0.72f
            2 -> u <= 0.22f + v * 0.85f
            3 -> (u + abs(v - 0.55f) * 1.5f <= 0.95f) || (u < 0.12f && v < 0.5f) || v < 0.08f
            4 -> { val x = u * 1.3f; val y = (0.38f - v) * 1.7f; val q = x * x + y * y - 1f; q * q * q - x * x * y * y * y <= 0f || v < 0.06f }
            5 -> ((u * 3.6f).toInt() % 2 == 0) || v < 0.15f
            6 -> v < 0.2f || u > 0.6f || v > 0.72f
            7 -> v <= 0.58f + 0.38f * sin(u * PI.toFloat() * 1.6f + ph)
            8 -> { val dy = (v - 0.55f) * 1.4f; val d = sqrt(u * u + dy * dy); (d in 0.42f..0.88f) || v < 0.12f }
            9 -> !((r % 3 == 1) && (c % 3 == 1))
            10 -> v < 0.12f || (u < 0.1f && v < 0.5f) || (u * u + ((v - 0.72f) * 1.5f).let { it * it } <= 0.42f)
            11 -> abs(u - 0.48f) * 1.4f + abs(v - 0.5f) * 1.3f <= 0.72f || v < 0.1f
            12 -> { val h = hash01(((u * 9f).toInt() * 31 + r) xor salt.toInt(), r * 7 + 3); h < 0.72f || v < 0.12f }
            13 -> (r % 3 != 2) || u < 0.2f
            14 -> abs(u - (1f - v) * 0.8f) < 0.26f || v < 0.12f || abs(u - v * 0.8f) < 0.2f
            else -> true
        }
    }

    private fun connected(b: Board, mask: BooleanArray): BooleanArray {
        val out = BooleanArray(Board.N)
        val q = IntArray(Board.N); var h = 0; var t = 0
        val nb = IntArray(6)
        for (i in 0 until Board.N) {
            if (!mask[i]) continue
            var anchor = Board.row(i) == 0
            if (!anchor && b.hasBoss) { val k = b.neighbors(i, nb); for (z in 0 until k) if (b.boss[nb[z]]) anchor = true }
            if (anchor) { out[i] = true; q[t++] = i }
        }
        while (h < t) {
            val i = q[h++]
            val k = b.neighbors(i, nb)
            for (z in 0 until k) { val j = nb[z]; if (mask[j] && !out[j]) { out[j] = true; q[t++] = j } }
        }
        return out
    }

    /**
     * Colours the board in small same-colour clusters. Cluster sizes shrink as difficulty rises (more lonely
     * singles), and no two clusters of one colour touch, so the layout is exactly what the numbers say.
     */
    private fun paint(b: Board, cells: BooleanArray, rng: Rng) {
        val d = diff
        val w = if (n < 2) floatArrayOf(0f, 0.1f, 0.4f, 0.5f) else
            floatArrayOf(0.12f + 0.38f * d, 0.38f, 0.32f - 0.17f * d, 0.18f - 0.13f * d)
        val nb = IntArray(6); val nb2 = IntArray(6)
        val order = ArrayList<Int>()
        for (i in 0 until Board.N) if (cells[i]) order.add(i)
        for (k in order.size - 1 downTo 1) { val j = rng.nextInt(k + 1); val t0 = order[k]; order[k] = order[j]; order[j] = t0 }
        val done = BooleanArray(Board.N)
        val cluster = ArrayList<Int>()
        for (start in order) {
            if (done[start]) continue
            var tot = 0f; for (x in w) tot += x
            var r = rng.nextFloat() * tot; var size = 1
            for (q in w.indices) { if (r < w[q]) { size = q + 1; break }; r -= w[q]; size = q + 1 }
            // colour: avoid every colour already touching the seed
            val k = b.neighbors(start, nb)
            val avoid = BooleanArray(8)
            for (z in 0 until k) if (done[nb[z]]) avoid[b.col[nb[z]]] = true
            val free = (0 until colors).filter { !avoid[it] }
            val c = if (free.isNotEmpty()) free[rng.nextInt(free.size)] else rng.nextInt(colors)
            cluster.clear(); cluster.add(start); done[start] = true; b.set(start, Kind.BUBBLE, c)
            var guard = 0
            while (cluster.size < size && guard++ < 30) {
                val from = cluster[rng.nextInt(cluster.size)]
                val kk = b.neighbors(from, nb)
                val j = nb[rng.nextInt(kk)]
                if (!cells[j] || done[j]) continue
                // j may not touch another cluster of the same colour
                val k2 = b.neighbors(j, nb2)
                var clash = false
                for (z in 0 until k2) { val q = nb2[z]; if (done[q] && b.col[q] == c && q !in cluster) { clash = true; break } }
                if (clash) continue
                done[j] = true; b.set(j, Kind.BUBBLE, c); cluster.add(j)
            }
        }
        // every colour gets at least a pair on stage so the queue never deals a stranger
        for (c in 0 until colors) {
            var have = 0
            for (i in 0 until Board.N) if (b.kind[i] == Kind.BUBBLE && b.col[i] == c) have++
            if (have == 0) {
                val cand = (0 until Board.N).filter { b.kind[it] == Kind.BUBBLE }
                if (cand.isNotEmpty()) for (q in 0 until 2) b.col[cand[rng.nextInt(cand.size)]] = c
            }
        }
    }

    private fun range(lo0: Int, hi0: Int, lo1: Int, hi1: Int, rng: Rng): Int {
        val lo = lo0 + (lo1 - lo0) * t; val hi = hi0 + (hi1 - hi0) * t
        return (lo + rng.nextFloat() * (hi - lo + 0.999f)).toInt()
    }

    private fun features(b: Board, rng: Rng) {
        val ok = { f: Int -> n >= Intro.LEVEL[f] }
        var stones = 0; var bombs = 0; var cages = 0; var spooks = 0; var inks = 0
        when (reel) {
            0 -> {}
            1 -> { stones = range(1, 3, 3, 6, rng); bombs = range(1, 1, 1, 2, rng) }
            2 -> { spooks = range(2, 4, 5, 8, rng); stones = range(0, 1, 1, 3, rng); bombs = range(0, 1, 0, 1, rng) }
            3 -> { cages = range(2, 3, 4, 7, rng); stones = range(0, 1, 1, 2, rng); spooks = range(0, 1, 1, 2, rng) }
            4 -> { inks = range(1, 1, 2, 3, rng); cages = range(1, 2, 2, 4, rng); bombs = range(1, 1, 1, 1, rng) }
            5 -> { stones = range(1, 3, 3, 5, rng); bombs = range(1, 1, 1, 2, rng); cages = range(1, 3, 3, 5, rng); spooks = range(1, 2, 2, 4, rng); inks = range(0, 0, 0, 1, rng) }
            6 -> { cages = range(2, 3, 4, 6, rng); spooks = range(2, 3, 3, 5, rng); inks = range(0, 1, 1, 2, rng) }
            7 -> { inks = range(1, 2, 2, 3, rng); cages = range(2, 3, 3, 5, rng); stones = range(1, 2, 2, 4, rng); bombs = range(1, 1, 1, 2, rng) }
            8 -> { stones = range(2, 3, 3, 5, rng); bombs = range(1, 2, 2, 2, rng); cages = range(2, 4, 4, 6, rng); spooks = range(2, 3, 3, 5, rng); inks = range(1, 1, 1, 2, rng) }
            else -> { stones = range(2, 4, 4, 6, rng); bombs = range(1, 2, 2, 3, rng); cages = range(3, 4, 5, 7, rng); spooks = range(2, 4, 4, 6, rng); inks = range(1, 2, 2, 3, rng) }
        }
        if (goal == Goal.BOSS) { stones /= 2; cages /= 2; inks = min(inks, 1) }
        if (!ok(Intro.STONE)) stones = 0
        if (!ok(Intro.BOMB)) bombs = 0
        if (!ok(Intro.SPOOK)) spooks = 0
        if (!ok(Intro.CAGE)) cages = 0
        if (!ok(Intro.INK)) inks = 0
        val cells = ArrayList<Int>()
        for (i in 0 until Board.N) if (b.kind[i] == Kind.BUBBLE) cells.add(i)
        fun take(pred: (Int) -> Boolean): Int {
            val c = cells.filter(pred)
            if (c.isEmpty()) return -1
            val i = c[rng.nextInt(c.size)]
            cells.remove(i)
            return i
        }
        val lowest = b.lowestRow().coerceAtLeast(1)
        // notes first, so they spread out nicely
        if (goal == Goal.NOTES) {
            val want = (3 + reel / 2 + (t * 2.5f).toInt()).coerceAtMost(9)
            val placed = ArrayList<Int>()
            var guard = 0
            while (placed.size < want && guard++ < 400) {
                // early on notes sit low and easy; later they hide up high
                val hi = n >= 8 && (diff > 0.3f || rng.chance(0.5f))
                val i = take { j ->
                    val r = Board.row(j)
                    val rowOk = if (hi) r <= max(1, lowest * 2 / 3) || rng.chance(0.25f) else r >= lowest / 3
                    rowOk && placed.all { p -> abs(Board.row(p) - r) + abs(Board.col(p) - Board.col(j)) >= 3 || guard > 200 }
                }
                if (i < 0) break
                b.kind[i] = Kind.NOTE; placed.add(i)
            }
        }
        repeat(stones) { val i = take { Board.row(it) >= 1 }; if (i >= 0) b.set(i, Kind.STONE) }
        repeat(bombs) { val i = take { Board.row(it) in 1..(lowest - 1).coerceAtLeast(1) }; if (i >= 0) b.set(i, Kind.BOMB) }
        repeat(cages) {
            val i = take { true }
            if (i >= 0) { b.kind[i] = Kind.CAGE; b.hp[i] = if (ok(Intro.CAGE2) && rng.chance(0.4f + 0.3f * t)) 2 else 1 }
        }
        repeat(spooks) { val i = take { true }; if (i >= 0) b.kind[i] = Kind.SPOOK }
        repeat(inks) { val i = take { Board.row(it) >= 2 }; if (i >= 0) b.set(i, Kind.INK) }
        if (n >= 3 && rng.chance(0.55f)) repeat(1 + rng.nextInt(2)) { val i = take { true }; if (i >= 0) b.kind[i] = Kind.TICKET }
        // a stone or ink can strand bubbles below it; that's fine, they just hang on it.
    }
}

/** Calibrated per-level numbers: variant, shots (or 0), press/boss-drop interval, 2-star and 3-star scores. */
object LevelTable {
    fun row(n: Int): IntArray? {
        val d = LevelData.D
        if (d.size != Reels.TOTAL * 5 || n !in 0 until Reels.TOTAL) return null
        return intArrayOf(d[n * 5], d[n * 5 + 1], d[n * 5 + 2], d[n * 5 + 3], d[n * 5 + 4])
    }
}
