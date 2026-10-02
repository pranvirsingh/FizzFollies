package com.pranvir.fizz

import kotlin.math.max
import kotlin.math.min

/**
 * Greedy player: tries every distinct landing spot for the current bubble (and the swapped one, and an armed
 * power) and keeps the best. Used to calibrate shot budgets, to sanity-check levels, and in tests.
 * [skill] < 1 adds human-like aiming wobble and the odd second-best choice.
 */
object Bot {
    class Pick { var ang = 1.57f; var swap = false; var power = false; var value = -1e9f }
    class Result(val won: Boolean, val shots: Int, val score: Int, val reason: Int, val turns: Int)

    private const val ANGLES = 120

    fun choose(m: Match, skill: Float = 1f, noise: Rng? = null): Pick {
        val best = Pick()
        val cands = ArrayList<Pick>()
        val tr = Trace()
        val options = ArrayList<Int>()   // 0 = as is, 1 = swap, 2 = power
        if (m.armed != Power.NONE) options.add(0)
        else {
            if (m.shotsLeft != 0) { options.add(0); if (m.next != m.cur) options.add(1) }
            if (m.powerReady) options.add(2)
        }
        for (o in options) {
            val base = m.copy()
            if (o == 1) base.swap()
            if (o == 2) base.arm()
            var runKey = Int.MIN_VALUE; var runStart = 0
            for (a in 0..ANGLES) {
                val key: Int
                if (a < ANGLES) {
                    val ang = Board.MIN_ANG + (Board.MAX_ANG - Board.MIN_ANG) * a / (ANGLES - 1)
                    base.aim(ang, tr)
                    key = when { tr.boss -> -2; tr.land < 0 && base.armed != Power.TRUMPET -> -3; base.armed == Power.TRUMPET -> (ang * 8).toInt(); else -> tr.land }
                } else key = Int.MAX_VALUE
                if (key != runKey) {
                    if (a > 0 && runKey != -3) {
                        val mid = (runStart + a - 1) / 2
                        val ang = Board.MIN_ANG + (Board.MAX_ANG - Board.MIN_ANG) * mid / (ANGLES - 1)
                        val width = a - runStart
                        val sim = base.copy()
                        val s = sim.fire(ang)
                        if (s != null) {
                            var v = value(sim, s)
                            if (o == 2) v += 25f
                            if (skill < 1f && width < 3) v -= (1f - skill) * 60f   // fiddly bank shots are hard for people
                            val p = Pick(); p.ang = ang; p.swap = o == 1; p.power = o == 2; p.value = v
                            cands.add(p)
                        }
                    }
                    runKey = key; runStart = a
                }
            }
        }
        if (cands.isEmpty()) { best.ang = 1.57f; return best }
        cands.sortByDescending { it.value }
        var pick = cands[0]
        if (noise != null && skill < 1f) {
            if (noise.chance((1f - skill) * 0.35f) && cands.size > 1) pick = cands[1 + noise.nextInt(min(3, cands.size - 1))]
            val p2 = Pick(); p2.ang = (pick.ang + (noise.nextFloat() - 0.5f) * (1f - skill) * 0.07f).coerceIn(Board.MIN_ANG, Board.MAX_ANG)
            p2.swap = pick.swap; p2.power = pick.power; p2.value = pick.value
            pick = p2
        }
        return pick
    }

    fun value(m: Match, s: Shot): Float {
        if (m.state == Match.WON) return 1e6f - m.shotsUsed * 10f
        if (m.state == Match.LOST) return -1e6f
        var v = s.popped * 10f + s.blasted * 6f + s.dropped * 16f + s.notes * 90f + s.bossDmg * 45f + s.tickets * 4f
        if (s.popped == 0 && s.dropped == 0 && s.placed >= 0 && s.placed < Board.N && m.b.kind[s.placed] != Kind.EMPTY) {
            val nb = IntArray(6)
            val k = m.b.neighbors(s.placed, nb)
            var same = 0
            for (q in 0 until k) if (Kind.matchable(m.b.kind[nb[q]]) && m.b.col[nb[q]] == m.b.col[s.placed]) same++
            v += same * 7f - Board.row(s.placed) * 1.2f
        }
        val low = m.b.lowestRow()
        val margin = m.b.maxRow - low
        if (margin < 4) v -= (4 - margin) * 45f
        if (m.spec.goal == Goal.CLEAR || m.spec.goal == Goal.PRESS) v -= m.b.coloredCount() * 0.3f
        return v
    }

    fun play(spec: LevelSpec, seed: Long, skill: Float = 1f, maxTurns: Int = 160, boost: Boost? = null): Result {
        val m = Match(spec, seed, boost)
        val noise = if (skill < 1f) Rng(seed * 31 + 7) else null
        var turns = 0
        while (m.state == Match.PLAYING && turns < maxTurns) {
            val p = choose(m, skill, noise)
            if (p.swap) m.swap()
            if (p.power) m.arm()
            m.fire(p.ang) ?: break
            turns++
        }
        if (m.state == Match.WON) m.finish()
        return Result(m.state == Match.WON, m.shotsUsed, m.score, m.lostReason, turns)
    }

    /**
     * Fills in budgets for one level the way the calibration tool does: let the bot play without a shot limit,
     * give people [LevelSpec.slack] times what it needed, then set star scores from how it does with that budget.
     * Returns [variant, shots, interval, s2, s3].
     */
    fun calibrate(n: Int, log: ((String) -> Unit)? = null): IntArray {
        for (variant in 0 until 8) {
            val spec = LevelSpec(n, variant)
            when (spec.goal) {
                Goal.CLEAR, Goal.NOTES -> {
                    spec.shots = 0
                    val runs = (0 until 3).map { play(spec, 1000L + it * 77L) }
                    if (runs.any { !it.won }) { log?.invoke("L$n v$variant unlimited bot lost"); continue }
                    val need = runs.map { it.shots }.sorted()[1]
                    var budget = maxOf((need * spec.slack + 2f).toInt(), need + 4, if (spec.goal == Goal.NOTES) 10 else 12)
                    // make sure a so-so player (wobbly aim, the odd poor choice) clears it at least half the time
                    for (attempt in 0 until 4) {
                        spec.shots = budget
                        val wins = (0 until 4).count { play(spec, 8000L + it * 37L + attempt * 1009L, 0.55f).won }
                        if (wins >= 2) break
                        budget = (budget * 1.15f).toInt() + 1
                    }
                    spec.shots = budget
                    val res = (0 until 3).map { play(spec, 5000L + it * 91L) }
                    if (res.count { it.won } < 2) { log?.invoke("L$n v$variant budget $budget too tight"); continue }
                    val sc = res.filter { it.won }.map { it.score }.sorted()
                    val med = sc[sc.size / 2]
                    return intArrayOf(variant, budget, 0, (med * 0.62f).toInt() / 10 * 10, (med * 0.92f).toInt() / 10 * 10)
                }
                Goal.PRESS -> {
                    var every = spec.pressEvery
                    var okRuns: List<Result>? = null
                    while (every <= spec.pressEvery + 5) {
                        spec.pressEvery = every
                        val runs = (0 until 3).map { play(spec, 2000L + it * 53L) }
                        if (runs.all { it.won }) { okRuns = runs; break }
                        every++
                    }
                    if (okRuns == null) { log?.invoke("L$n v$variant press impossible"); continue }
                    // a little more room for people than the bot needed
                    val give = every + (if (spec.diff < 0.5f) 2 else 1)
                    spec.pressEvery = give
                    spec.par = (okRuns.map { it.shots }.sorted()[1] * 1.3f).toInt() + 2
                    val res = (0 until 3).map { play(spec, 6000L + it * 57L) }
                    val sc = res.filter { it.won }.map { it.score }.sorted()
                    if (sc.isEmpty()) continue
                    val med = sc[sc.size / 2]
                    return intArrayOf(variant, spec.par, give, (med * 0.62f).toInt() / 10 * 10, (med * 0.92f).toInt() / 10 * 10)
                }
                else -> {
                    var press = spec.bossPress
                    var okRuns: List<Result>? = null
                    while (press <= spec.bossPress + 6) {
                        spec.bossPress = press
                        val runs = (0 until 3).map { play(spec, 3000L + it * 59L) }
                        if (runs.all { it.won }) { okRuns = runs; break }
                        press++
                    }
                    if (okRuns == null) { log?.invoke("L$n v$variant boss impossible"); continue }
                    val give = press + 2
                    spec.bossPress = give
                    spec.par = (okRuns.map { it.shots }.sorted()[1] * 1.3f).toInt() + 2
                    val res = (0 until 3).map { play(spec, 7000L + it * 61L) }
                    val sc = res.filter { it.won }.map { it.score }.sorted()
                    if (sc.isEmpty()) continue
                    val med = sc[sc.size / 2]
                    return intArrayOf(variant, spec.par, give, (med * 0.62f).toInt() / 10 * 10, (med * 0.92f).toInt() / 10 * 10)
                }
            }
        }
        val spec = LevelSpec(n, 0)
        return intArrayOf(0, if (spec.limited) 60 else 0, if (spec.goal == Goal.BOSS) 14 else 12, 2000, 5000)
    }
}
