package com.pranvir.fizz

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Goal {
    const val CLEAR = 0; const val NOTES = 1; const val PRESS = 2; const val BOSS = 3
    val TITLE = arrayOf("CLEAR THE STAGE", "FREE THE MUSIC", "BEAT THE DROP", "BOIL THE BARON")
}

object Power {
    const val NONE = 0; const val FIRECRACKER = 1; const val RAINBOW = 2; const val TRUMPET = 3
    val NAMES = arrayOf("", "FIRECRACKER", "JAZZ BUBBLE", "TRUMPET BLAST")
}

object Ev {
    const val PLACE = 0; const val POP = 1; const val CAGE_HIT = 2; const val CAGE_BREAK = 3; const val BLAST = 4
    const val BOMB = 5; const val DROP = 6; const val SPOOK = 7; const val INK_SPREAD = 8; const val BOSS_HIT = 9
    const val BOSS_SPAWN = 10; const val PRESS = 11; const val SPLAT = 12; const val PIERCE = 13; const val FIZZLE = 14
    const val RECOLOR = 15; const val INK_WIPE = 16
}

/** One timed thing for the renderer to show; [t] is seconds after the shot lands. */
class Event(val type: Int, val i: Int, val t: Float, val kind: Int = 0, val col: Int = 0, val v: Int = 0, val x: Float = 0f, val y: Float = 0f)

class Shot {
    val trace = Trace()
    var color = 0
    var power = Power.NONE
    val events = ArrayList<Event>()
    var placed = -1
    var popped = 0; var dropped = 0; var blasted = 0; var notes = 0; var tickets = 0; var bossDmg = 0
    var popScore = 0; var dropScore = 0; var bonusScore = 0
    var end = 0f
    var rainbowCol = -1
    fun reset() {
        events.clear(); placed = -1; popped = 0; dropped = 0; blasted = 0; notes = 0; tickets = 0; bossDmg = 0
        popScore = 0; dropScore = 0; bonusScore = 0; end = 0f; rainbowCol = -1; power = Power.NONE
    }
    val total get() = popScore + dropScore + bonusScore
}

/** Optional helpers bought before a level. */
class Boost(var extraShots: Int = 0, var startPower: Int = Power.NONE, var spyglass: Boolean = false)

/**
 * One attempt at a level: the board, the seltzer queue, shots, score and the Baron. Pure logic and fully
 * deterministic for a given seed, so the bot can fork it to look ahead.
 */
class Match(val spec: LevelSpec, seed: Long, boost: Boost? = null, build: Boolean = true) {
    companion object {
        const val PLAYING = 0; const val WON = 1; const val LOST = 2
        const val LOST_SHOTS = 1; const val LOST_OVERFLOW = 2
        const val APPLAUSE_MAX = 40f
        const val NOTE_SCORE = 250; const val CURTAIN_CALL = 3; const val TICKET_SCORE = 100; const val ENCORE_SHOT = 150
    }

    val b = Board()
    val rng = Rng(seed)
    var cur = 0; var next = 0
    var armed = Power.NONE
    var shotsLeft = -1
    var shotsUsed = 0
    var score = 0
    var applause = 0f
    var streak = 0
    var bestStreak = 0
    var notesTotal = 0; var notesFreed = 0
    var tickets = 0
    var bossHp = 0; var bossMax = 0; var bossTimer = 0
    var pressTimer = 0
    var state = PLAYING
    var lostReason = 0
    var inkTimer = 0
    var powerCycle = 0
    var finished = false
    var popsTotal = 0; var dropsTotal = 0

    val powerReady get() = spec.powers != 0 && applause >= APPLAUSE_MAX
    val limited get() = shotsLeft >= 0

    fun copy(): Match {
        val m = Match(spec, 0L, null, false)
        m.b.copyFrom(b); m.rng.s = rng.s
        m.cur = cur; m.next = next; m.armed = armed; m.shotsLeft = shotsLeft; m.shotsUsed = shotsUsed
        m.score = score; m.applause = applause; m.streak = streak; m.bestStreak = bestStreak
        m.notesTotal = notesTotal; m.notesFreed = notesFreed; m.tickets = tickets
        m.bossHp = bossHp; m.bossMax = bossMax; m.bossTimer = bossTimer; m.pressTimer = pressTimer
        m.state = state; m.lostReason = lostReason; m.inkTimer = inkTimer; m.powerCycle = powerCycle
        m.finished = finished; m.popsTotal = popsTotal; m.dropsTotal = dropsTotal
        return m
    }

    // ------------------------------------------------------------------ queue

    private val present = BooleanArray(8)
    private val nb = IntArray(6)
    private val nb2 = IntArray(6)

    private fun scanPresent(): Int {
        present.fill(false)
        var n = 0
        for (i in 0 until Board.N) if (Kind.colored(b.kind[i]) && b.col[i] in 0 until 8) {
            if (!present[b.col[i]]) { present[b.col[i]] = true; n++ }
        }
        return n
    }

    /** Next seltzer colour: always one still on stage, sometimes (mercy) one sitting on the exposed surface. */
    fun pick(): Int {
        val n = scanPresent()
        if (n == 0) return rng.nextInt(spec.colors)
        if (rng.chance(spec.mercy)) {
            // colours of bubbles that have an empty neighbour (reachable surface)
            var cnt = 0
            val tmp = IntArray(8)
            for (i in 0 until Board.N) {
                if (!Kind.matchable(b.kind[i])) continue
                val k = b.neighbors(i, nb)
                var open = false
                for (q in 0 until k) if (b.kind[nb[q]] == Kind.EMPTY && !b.boss[nb[q]]) { open = true; break }
                if (open) { tmp[b.col[i]]++; cnt++ }
            }
            if (cnt > 0) {
                var r = rng.nextInt(cnt)
                for (c in 0 until 8) { r -= tmp[c]; if (r < 0) return c }
            }
        }
        var r = rng.nextInt(n)
        for (c in 0 until 8) if (present[c]) { if (r == 0) return c; r-- }
        return 0
    }

    fun swap(): Boolean {
        if (state != PLAYING || armed != Power.NONE || cur == next) return false
        val t = cur; cur = next; next = t
        return true
    }

    fun nextPowerType(): Int {
        val mask = spec.powers
        for (k in 0 until 3) {
            val p = 1 + (powerCycle + k) % 3
            if (mask and (1 shl p) != 0) return p
        }
        return Power.FIRECRACKER
    }

    fun arm(): Boolean {
        if (state != PLAYING || !powerReady || armed != Power.NONE) return false
        armed = nextPowerType()
        powerCycle = armed % 3
        return true
    }

    // ------------------------------------------------------------------ firing

    private val gone = BooleanArray(Board.N)
    private val bombed = BooleanArray(Board.N)
    private val caged = BooleanArray(Board.N)
    private val bombQ = ArrayList<Pair<Int, Float>>()
    private var maxT = 0f
    private var inkWiped = false
    private val queue = IntArray(Board.N)
    private val depth = IntArray(Board.N)
    private val seen = BooleanArray(Board.N)

    fun aim(ang: Float, t: Trace) = b.trace(ang, t, armed == Power.TRUMPET)

    fun fire(ang: Float, s: Shot = Shot()): Shot? {
        if (state != PLAYING) return null
        s.reset()
        val power = armed
        s.power = power; s.color = cur
        val tr = s.trace
        b.trace(ang, tr, power == Power.TRUMPET)
        if (power == Power.NONE) { shotsUsed++; if (shotsLeft > 0) shotsLeft-- }
        else { armed = Power.NONE; applause = 0f }
        gone.fill(false); bombed.fill(false); caged.fill(false); bombQ.clear(); maxT = 0f; inkWiped = false

        when {
            power == Power.TRUMPET -> {
                for (q in 0 until tr.nThrough) {
                    val j = tr.through[q]
                    if (b.kind[j] == Kind.EMPTY) continue
                    val t = q * 0.025f
                    if (b.kind[j] == Kind.BOMB) queueBomb(j, t) else remove(j, t, Ev.PIERCE, s)
                }
                if (tr.throughBoss) hurtBoss(8, 0.15f, s)
            }
            tr.boss -> {
                s.events.add(Event(Ev.SPLAT, -1, 0f, 0, cur, power, tr.ex, tr.ey))
                if (power == Power.FIRECRACKER) { hurtBoss(7, 0f, s); explodeAt(tr.ex, tr.ey, 0f, -1, s) }
                else hurtBoss(2, 0f, s)
            }
            tr.land < 0 -> s.events.add(Event(Ev.FIZZLE, -1, 0f, 0, cur, 0, tr.ex, tr.ey))
            else -> {
                val L = tr.land
                if (power == Power.FIRECRACKER) {
                    explodeAt(Board.cxi(L), b.cyi(L), 0f, L, s)
                } else {
                    s.placed = L
                    if (power == Power.RAINBOW) {
                        b.set(L, Kind.BUBBLE, rainbowColor(L))
                        s.rainbowCol = b.col[L]
                    } else b.set(L, Kind.BUBBLE, cur)
                    s.events.add(Event(Ev.PLACE, L, 0f, Kind.BUBBLE, b.col[L], power))
                    if (tr.hit >= 0 && b.kind[tr.hit] == Kind.BOMB) queueBomb(tr.hit, 0.06f)
                    if (power == Power.RAINBOW) rainbowMatch(L, s) else match(L, s)
                }
            }
        }
        processBombs(s)
        dropFloating(maxT + 0.1f, s)

        // tallies
        s.popScore = s.popped * 10 + s.blasted * 10 + max(0, s.popped - 3) * 10
        s.dropScore = s.dropped * 20 + s.dropped * max(0, s.dropped - 1) * 2
        s.bonusScore = s.notes * NOTE_SCORE + s.tickets * TICKET_SCORE + s.bossDmg * 50
        score += s.total
        notesFreed += s.notes; tickets += s.tickets
        popsTotal += s.popped + s.blasted; dropsTotal += s.dropped
        if (power == Power.NONE && spec.powers != 0) {
            var gain = s.popped + s.blasted * 0.5f + s.dropped * 1.5f + s.notes * 2f
            if (s.popped + s.dropped > 0) { streak++; gain *= 1f + 0.12f * min(streak, 6) } else streak = 0
            applause = min(APPLAUSE_MAX, applause + gain)
        } else if (s.popped + s.dropped > 0) streak++ else streak = 0
        bestStreak = max(bestStreak, streak)

        var tEnd = maxT + 0.2f
        val won0 = checkWin()
        if (!won0) tEnd = afterTurn(tEnd, s)

        if (power == Power.NONE) { cur = next; next = pick() }
        if (b.coloredCount() > 0) {
            scanPresent()
            if (!present[cur]) { cur = pick(); s.events.add(Event(Ev.RECOLOR, 0, tEnd, 0, cur)) }
            if (!present[next]) { next = pick(); s.events.add(Event(Ev.RECOLOR, 1, tEnd, 0, next)) }
        }
        s.end = tEnd
        if (checkWin()) state = WON
        else if (b.lowestRow() > b.maxRow) { state = LOST; lostReason = LOST_OVERFLOW }
        else if (shotsLeft == 0 && armed == Power.NONE && !powerReady) { state = LOST; lostReason = LOST_SHOTS }
        return s
    }

    /** True when out of shots but a power shot is still waiting to be used. */
    val mustUsePower get() = state == PLAYING && shotsLeft == 0

    private fun checkWin(): Boolean = when (spec.goal) {
        Goal.NOTES -> notesFreed >= notesTotal
        Goal.BOSS -> bossHp <= 0
        else -> b.coloredCount() <= CURTAIN_CALL
    }

    /** Spooks shift, ink spreads, the Baron boils over and the ceiling drops. Returns the new end time. */
    private fun afterTurn(t0: Float, s: Shot): Float {
        var t = t0
        // spooks
        var anySpook = false
        for (i in 0 until Board.N) if (b.kind[i] == Kind.SPOOK) {
            val nc = (b.col[i] + 1) % spec.colors
            b.col[i] = nc
            s.events.add(Event(Ev.SPOOK, i, t, Kind.SPOOK, nc))
            anySpook = true
        }
        if (anySpook) t += 0.15f
        // ink
        val inks = b.count { it == Kind.INK }
        if (inks in 1..13) {
            if (inkWiped) inkTimer = 0
            else if (++inkTimer >= spec.inkEvery) {
                inkTimer = 0
                val cands = ArrayList<Int>()
                for (i in 0 until Board.N) if (b.kind[i] == Kind.INK) {
                    val k = b.neighbors(i, nb)
                    for (q in 0 until k) if (b.kind[nb[q]] == Kind.BUBBLE) cands.add(i * Board.N + nb[q])
                }
                if (cands.isNotEmpty()) {
                    val pick = cands[rng.nextInt(cands.size)]
                    val from = pick / Board.N; val to = pick % Board.N
                    val oc = b.col[to]
                    b.set(to, Kind.INK)
                    s.events.add(Event(Ev.INK_SPREAD, to, t, Kind.INK, oc, from))
                    t += 0.25f
                }
            }
        }
        // the Baron
        if (spec.goal == Goal.BOSS && bossHp > 0) {
            if (--bossTimer <= 0) {
                val angry = bossHp < bossMax * 0.4f
                bossTimer = if (angry) max(1, spec.bossEvery - 1) else spec.bossEvery
                val n = spec.bossSpawn + (if (angry) 1 else 0)
                val added = bossSpawn(n, t, s)
                if (added > 0) t += 0.45f + added * 0.08f
            }
        }
        // ceiling
        val every = if (spec.goal == Goal.BOSS) spec.bossPress else spec.pressEvery
        if (every > 0) {
            if (--pressTimer <= 0) {
                pressTimer = every
                b.ceil += Board.RH; b.drops++
                s.events.add(Event(Ev.PRESS, -1, t))
                t += 0.45f
            }
        }
        return t
    }

    private fun bossSpawn(n: Int, t0: Float, s: Shot): Int {
        val cands = ArrayList<Int>()
        val w = ArrayList<Float>()
        for (i in 0 until Board.N) {
            val r = Board.row(i); val c = Board.col(i)
            if (!Board.valid(r, c) || b.kind[i] != Kind.EMPTY || b.boss[i] || r > b.maxRow - 4) continue
            val k = b.neighbors(i, nb)
            var att = r == 0; var nearBoss = false
            for (q in 0 until k) { if (b.kind[nb[q]] != Kind.EMPTY) att = true; if (b.boss[nb[q]]) { att = true; nearBoss = true } }
            if (!att) continue
            cands.add(i); w.add(if (nearBoss) 6f else 1f / (1f + r * 0.3f))
        }
        var added = 0
        for (q in 0 until n) {
            if (cands.isEmpty()) break
            var tot = 0f; for (x in w) tot += x
            var r = rng.nextFloat() * tot
            var k = 0
            while (k < w.size - 1 && r > w[k]) { r -= w[k]; k++ }
            val i = cands.removeAt(k); w.removeAt(k)
            var kind = Kind.BUBBLE
            val roll = rng.nextFloat()
            if (spec.bossExtra == Kind.INK && roll < 0.25f) kind = Kind.INK
            else if (spec.bossExtra == Kind.STONE && roll < 0.2f) kind = Kind.STONE
            else if (spec.bossExtra == Kind.SPOOK && roll < 0.3f) kind = Kind.SPOOK
            else if (spec.bossExtra == Kind.CAGE && roll < 0.25f) kind = Kind.CAGE
            val c = rng.nextInt(spec.colors)
            b.set(i, kind, if (Kind.colored(kind)) c else 0, if (kind == Kind.CAGE) 1 else 0)
            s.events.add(Event(Ev.BOSS_SPAWN, i, t0 + q * 0.08f, kind, b.col[i], b.hp[i]))
            added++
        }
        return added
    }

    private fun hurtBoss(dmg: Int, t: Float, s: Shot) {
        if (spec.goal != Goal.BOSS || bossHp <= 0 || dmg <= 0) return
        val d = min(dmg, bossHp)
        bossHp -= d
        s.bossDmg += d
        s.events.add(Event(Ev.BOSS_HIT, -1, t, 0, 0, d))
        maxT = max(maxT, t)
    }

    private fun remove(j: Int, t: Float, type: Int, s: Shot) {
        val k = b.kind[j]
        if (k == Kind.EMPTY) return
        s.events.add(Event(type, j, t, k, b.col[j], b.hp[j]))
        when (type) { Ev.DROP -> s.dropped++; Ev.POP -> s.popped++; Ev.INK_WIPE -> s.popped++; else -> s.blasted++ }
        if (k == Kind.NOTE) s.notes++
        if (k == Kind.TICKET) s.tickets++
        b.empty(j)
        gone[j] = true
        maxT = max(maxT, t)
    }

    private fun queueBomb(i: Int, t: Float) {
        if (b.kind[i] != Kind.BOMB || bombed[i]) return
        bombed[i] = true
        bombQ.add(Pair(i, t))
    }

    private fun processBombs(s: Shot) {
        var guard = 0
        while (bombQ.isNotEmpty() && guard++ < 200) {
            val (i, t) = bombQ.removeAt(0)
            val x = Board.cxi(i); val y = b.cyi(i)
            remove(i, t, Ev.BOMB, s)
            blastAround(x, y, t, s)
        }
    }

    private fun explodeAt(x: Float, y: Float, t: Float, cell: Int, s: Shot) {
        s.events.add(Event(Ev.BOMB, cell, t, 0, 0, 1, x, y))
        blastAround(x, y, t, s)
        maxT = max(maxT, t)
    }

    private fun blastAround(x: Float, y: Float, t: Float, s: Shot) {
        val rad = Board.D * 2.05f
        for (j in 0 until Board.N) {
            if (b.kind[j] == Kind.EMPTY) continue
            val dx = Board.cxi(j) - x; val dy = b.cyi(j) - y
            val d2 = dx * dx + dy * dy
            if (d2 > rad * rad) continue
            val tt = t + sqrt(d2) * 0.0006f
            if (b.kind[j] == Kind.BOMB) queueBomb(j, t + 0.14f) else remove(j, tt, Ev.BLAST, s)
        }
        if (b.hasBoss) {
            val dx = b.bossX - x; val dy = b.bossY - y
            if (sqrt(dx * dx + dy * dy) < b.bossR + rad) hurtBoss(4, t + 0.05f, s)
        }
    }

    /** Best colour for a Jazz bubble landing at L: the neighbouring colour with the biggest group. */
    private fun rainbowColor(L: Int): Int {
        val k = b.neighbors(L, nb2)
        var best = -1; var bestN = 0
        for (q in 0 until k) {
            val j = nb2[q]
            if (!Kind.matchable(b.kind[j])) continue
            val n = groupSize(j, b.col[j])
            if (n > bestN) { bestN = n; best = b.col[j] }
        }
        return if (best >= 0) best else cur
    }

    private fun groupSize(start: Int, color: Int): Int {
        seen.fill(false)
        var head = 0; var tail = 0
        queue[tail++] = start; seen[start] = true
        while (head < tail) {
            val i = queue[head++]
            val k = b.neighbors(i, nb)
            for (q in 0 until k) {
                val j = nb[q]
                if (!seen[j] && Kind.matchable(b.kind[j]) && b.col[j] == color) { seen[j] = true; queue[tail++] = j }
            }
        }
        return tail
    }

    private fun rainbowMatch(L: Int, s: Shot) {
        // a jazz bubble also pops every other neighbouring colour group of two or more
        val k = b.neighbors(L, nb2)
        val cols = ArrayList<Int>()
        for (q in 0 until k) { val j = nb2[q]; if (Kind.matchable(b.kind[j]) && b.col[j] !in cols) cols.add(b.col[j]) }
        val own = b.col[L]
        match(L, s)
        for (c in cols) {
            if (c == own) continue
            for (q in 0 until k) {
                val j = nb2[q]
                if (b.kind[j] != Kind.EMPTY && Kind.matchable(b.kind[j]) && b.col[j] == c && groupSize(j, c) >= 2) { popGroup(j, c, 0.05f, s); break }
            }
        }
    }

    private fun match(L: Int, s: Shot) {
        val color = b.col[L]
        if (groupSize(L, color) >= 3) popGroup(L, color, 0f, s)
    }

    private val order = IntArray(Board.N)

    private fun popGroup(start: Int, color: Int, t0: Float, s: Shot) {
        seen.fill(false)
        var head = 0; var tail = 0
        queue[tail++] = start; seen[start] = true; depth[start] = 0
        while (head < tail) {
            val i = queue[head++]
            val k = b.neighbors(i, nb)
            for (q in 0 until k) {
                val j = nb[q]
                if (!seen[j] && Kind.matchable(b.kind[j]) && b.col[j] == color) { seen[j] = true; depth[j] = depth[i] + 1; queue[tail++] = j }
            }
        }
        val n = tail
        for (q in 0 until n) order[q] = queue[q]
        var adjBoss = 0
        for (q in 0 until n) {
            val i = order[q]
            val t = t0 + depth[i] * 0.045f
            if (b.hasBoss) {
                val k = b.neighbors(i, nb)
                for (z in 0 until k) if (b.boss[nb[z]]) { adjBoss++; break }
            }
            remove(i, t, Ev.POP, s)
        }
        // knock-on effects on neighbours
        for (q in 0 until n) {
            val i = order[q]
            val t = t0 + depth[i] * 0.045f
            val k = b.neighbors(i, nb)
            for (z in 0 until k) {
                val j = nb[z]
                when (b.kind[j]) {
                    Kind.CAGE -> if (!caged[j]) {
                        caged[j] = true
                        b.hp[j]--
                        if (b.hp[j] <= 0) { b.kind[j] = Kind.BUBBLE; b.hp[j] = 0; s.events.add(Event(Ev.CAGE_BREAK, j, t + 0.05f, Kind.CAGE, b.col[j])) }
                        else s.events.add(Event(Ev.CAGE_HIT, j, t + 0.05f, Kind.CAGE, b.col[j], b.hp[j]))
                        maxT = max(maxT, t + 0.05f)
                    }
                    Kind.INK -> { inkWiped = true; remove(j, t + 0.06f, Ev.INK_WIPE, s) }
                    Kind.BOMB -> queueBomb(j, t + 0.08f)
                }
            }
        }
        if (adjBoss > 0) hurtBoss(min(3, adjBoss), t0 + 0.1f, s)
    }

    private fun dropFloating(t: Float, s: Shot) {
        seen.fill(false)
        var head = 0; var tail = 0
        for (i in 0 until Board.N) {
            if (b.kind[i] == Kind.EMPTY) continue
            var anchor = Board.row(i) == 0
            if (!anchor && b.hasBoss && bossHp > 0) {
                val k = b.neighbors(i, nb)
                for (q in 0 until k) if (b.boss[nb[q]]) { anchor = true; break }
            }
            if (anchor) { seen[i] = true; queue[tail++] = i }
        }
        while (head < tail) {
            val i = queue[head++]
            val k = b.neighbors(i, nb)
            for (q in 0 until k) { val j = nb[q]; if (!seen[j] && b.kind[j] != Kind.EMPTY) { seen[j] = true; queue[tail++] = j } }
        }
        var n = 0
        for (i in 0 until Board.N) if (b.kind[i] != Kind.EMPTY && !seen[i]) { remove(i, t + (n % 7) * 0.012f, Ev.DROP, s); n++ }
    }

    // ------------------------------------------------------------------ end of level

    /** Encore bonus once the level is won: every unused shot and every bubble left on stage pays out. */
    fun encoreBonus(): Int {
        val spare = if (limited) max(0, shotsLeft) else max(0, spec.par - shotsUsed)
        return spare * ENCORE_SHOT + b.occupied() * 10
    }

    fun finish(): Int {
        if (finished) return 0
        finished = true
        val bonus = if (state == WON) encoreBonus() else 0
        score += bonus
        return bonus
    }

    fun stars(): Int = if (state != WON) 0 else if (score >= spec.s3) 3 else if (score >= spec.s2) 2 else 1

    /** Carry on after losing: more shots, or the bottom rows swept away after an overflow. */
    fun encoreContinue(extra: Int): Boolean {
        if (state != LOST) return false
        if (lostReason == LOST_SHOTS) shotsLeft += extra
        else {
            val keep = b.maxRow - 4
            for (i in 0 until Board.N) if (b.kind[i] != Kind.EMPTY && Board.row(i) > keep) b.empty(i)
            val s = Shot()
            dropFloating(0f, s)
            if (shotsLeft >= 0) shotsLeft += extra / 2
        }
        state = PLAYING; lostReason = 0
        if (checkWin()) state = WON
        return true
    }

    // runs last so every field above is initialised
    init {
        if (build) {
            spec.build(b)
            notesTotal = b.count { it == Kind.NOTE }
            shotsLeft = if (spec.shots > 0) spec.shots + (boost?.extraShots ?: 0) else -1
            if (spec.goal == Goal.BOSS) { bossMax = spec.bossHp; bossHp = bossMax; bossTimer = spec.bossEvery }
            pressTimer = if (spec.goal == Goal.BOSS) spec.bossPress else spec.pressEvery
            cur = pick(); next = pick()
            if (boost != null && boost.startPower != Power.NONE) armed = boost.startPower
        }
    }

}
