package com.pranvir.fizz

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

interface PlayEvents {
    fun sfx(id: Int, vol: Float = 1f, rate: Float = 1f)
    fun haptic(strong: Boolean)
}

/**
 * The play screen for one attempt: shows the [Match] with every pop, drop and splat timed out, runs the aiming,
 * Fizz, the Baron, the applause meter and the HUD. Board units inside the board transform, world units outside.
 */
class Play(val spec: LevelSpec, val L: Layout, var art: Art, boost: Boost?, seed: Long, private val ev: PlayEvents) {
    companion object {
        const val IDLE = 0; const val FLYING = 1; const val RESOLVING = 2; const val ENCORE = 3; const val DONE = 4
        const val SPEED = 3200f
        /** No shot takes longer than this to arrive, however many bank shots it makes. */
        const val MAX_FLIGHT = 0.75f
        val WORDS = arrayOf("SWELL!", "SWANKY!", "HOT DOG!", "JEEPERS!", "NIFTY!", "WOWZA!", "THE CAT'S PAJAMAS!", "BEE'S KNEES!", "HOTSY TOTSY!", "COPACETIC!")
        val METER_X = 846f
        val METER_Y get() = Board.PIVOT_Y + 64f
    }

    val m = Match(spec, seed, boost)
    val fx = Fx()
    val fizz = Toon.FizzState()
    val baron = Toon.BaronState().also { it.hat = spec.reel }
    var t = 0f
    var beat = 0f
    var phase = IDLE
    var spyglass = boost?.spyglass ?: false

    // view of the board as the audience currently sees it
    private val vk = IntArray(Board.N); private val vc = IntArray(Board.N); private val vh = IntArray(Board.N)
    private var vCeil = 0f; private var ceilFrom = 0f; private var ceilTo = 0f; private var ceilT = 9f
    private val wobT = FloatArray(Board.N) { 9f }; private val wobA = FloatArray(Board.N); private val wobX = FloatArray(Board.N); private val wobY = FloatArray(Board.N)
    private val bornT = FloatArray(Board.N) { 9f }
    private val flashT = FloatArray(Board.N) { 9f }
    private var bossAlive = m.b.hasBoss
    private var bossShown = m.bossHp.toFloat()

    // shot in progress
    private var shot: Shot? = null
    private var flyD = 0f
    private var impactAt = 0f
    private var applied = BooleanArray(0)
    private var popIdx = 0
    private var shownPopScore = false; private var shownDrop = false
    private var pendingFire = Float.NaN

    // aiming
    var aiming = false; private set
    private var aimAng = 1.5708f
    private var aimX = 500f; private var aimY = 600f
    private val aimTr = Trace()
    private var aimDirty = true
    private var downX = 0f; private var downY = 0f; private var downOnFizz = false; private var downOnMeter = false; private var moved = false
    private var shake = 0f
    private var meterFlash = 0f
    private var lastShots = -1
    private var encoreT = 0f
    private var encoreShots = 0
    private var encoreFired = 0
    var encoreBonus = 0; private set
    var finished = false; private set
    var resultShown = false
    private var lowShotWarned = false
    private var wasReady = false

    // HUD geometry (world units), computed in drawHud
    val pauseRect = RectF()
    private var goalX = 500f; private var goalY = 100f

    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val p = Path()
    private val rf = RectF()
    private val nb = IntArray(6)

    init {
        syncView()
        fx.floorY = L.toBy(L.floorTop) + 30f
        fx.onLand = { q -> fx.puff(q.x, fx.floorY - 10f, if (q.kind == Kind.INK) 0xFF3A3040.toInt() else 0xFFF4EAD8.toInt()) }
        fx.onArrive = { q -> val i = q.size.toInt(); if (i in 0 until Board.N) { vk[i] = q.kind; vc[i] = q.col; vh[i] = q.face; bornT[i] = 0f; wobble(Board.cxi(i), cellY(i), 5f) } }
    }

    private fun syncView() {
        System.arraycopy(m.b.kind, 0, vk, 0, Board.N)
        System.arraycopy(m.b.col, 0, vc, 0, Board.N)
        System.arraycopy(m.b.hp, 0, vh, 0, Board.N)
        vCeil = m.b.ceil; ceilFrom = vCeil; ceilTo = vCeil
    }

    private fun cellY(i: Int) = vCeil + Board.R + Board.row(i) * Board.RH

    // ------------------------------------------------------------------ input (board units)

    private fun onFizz(x: Float, y: Float) = y > Board.PIVOT_Y - 40f && abs(x - 500f) < 230f && y < Board.PIVOT_Y + 260f
    private fun onMeter(x: Float, y: Float): Boolean { val dx = x - METER_X; val dy = y - METER_Y; return dx * dx + dy * dy < 110f * 110f }

    fun touchDown(x: Float, y: Float) {
        downX = x; downY = y; moved = false
        downOnMeter = onMeter(x, y)
        downOnFizz = !downOnMeter && onFizz(x, y)
        if (!downOnFizz && !downOnMeter && canAim()) { aiming = true; aimAt(x, y) }
    }

    fun touchMove(x: Float, y: Float) {
        if (abs(x - downX) + abs(y - downY) > 24f) moved = true
        if (aiming) aimAt(x, y)
        else if ((downOnFizz || downOnMeter) && moved && canAim() && y < Board.PIVOT_Y - 80f) { aiming = true; downOnFizz = false; downOnMeter = false; aimAt(x, y) }
    }

    fun touchUp(x: Float, y: Float) {
        if (aiming) {
            aiming = false
            if (y < Board.PIVOT_Y - 30f) {
                if (phase == IDLE) fire(aimAng) else pendingFire = aimAng
            }
        } else if (!moved) {
            if (downOnMeter) tapMeter()
            else if (downOnFizz) swap()
        }
        downOnFizz = false; downOnMeter = false
    }

    fun cancelTouch() { aiming = false; downOnFizz = false; downOnMeter = false }

    private fun canAim() = m.state == Match.PLAYING && phase != ENCORE && phase != DONE

    private fun aimAt(x: Float, y: Float) {
        aimX = x; aimY = y
        val a = atan2(Board.PIVOT_Y - y, x - 500f)
        aimAng = a.coerceIn(Board.MIN_ANG, Board.MAX_ANG)
        if (y > Board.PIVOT_Y - 30f) aimAng = if (x < 500f) Board.MAX_ANG else Board.MIN_ANG
        aimDirty = true
    }

    fun swap() {
        if (phase == ENCORE || phase == DONE) return
        if (m.swap()) { fizz.swapT = 0f; ev.sfx(Sfx.SWAP, 0.8f) } else ev.sfx(Sfx.NOPE, 0.5f)
    }

    fun tapMeter() {
        if (phase == ENCORE || phase == DONE) return
        if (m.arm()) { ev.sfx(Sfx.POWER, 1f); ev.haptic(false); fx.word(500f, Board.PIVOT_Y - 260f, Power.NAMES[m.armed], Pal.CREAM); meterFlash = 1f }
        else ev.sfx(Sfx.NOPE, 0.5f)
    }

    private fun fire(ang: Float) {
        if (m.state != Match.PLAYING) return
        val power = m.armed
        val s0 = m.fire(ang) ?: return
        shot = s0
        flyD = 0f; popIdx = 0; shownPopScore = false; shownDrop = false
        applied = BooleanArray(s0.events.size)
        phase = FLYING
        fizz.fireT = 0f
        aimDirty = true
        ev.sfx(if (power == Power.TRUMPET) Sfx.TRUMPET else if (power == Power.FIRECRACKER) Sfx.FUSE else Sfx.SHOOT, 0.9f, 0.95f + (t * 7f % 0.1f))
        if (power == Power.TRUMPET) { impactAt = t; phase = RESOLVING }
    }

    // ------------------------------------------------------------------ simulation

    fun update(dt: Float, beatPhase: Float) {
        t += dt; beat = beatPhase
        fizz.t = t; fizz.beat = beatPhase; fizz.fireT += dt; fizz.swapT += dt; fizz.moodT += dt
        baron.t = t; baron.beat = beatPhase; baron.hurtT += dt; baron.throwT += dt
        if (baron.dead > 0f) baron.dead = min(1f, baron.dead + dt * 0.6f)
        shake = max(0f, shake - dt * 3f)
        meterFlash = max(0f, meterFlash - dt * 2f)
        for (i in 0 until Board.N) { wobT[i] += dt; bornT[i] += dt; flashT[i] += dt }
        if (ceilT < 1f) { ceilT += dt / 0.4f; vCeil = lerp(ceilFrom, ceilTo, easeOutBack(min(1f, ceilT))) }
        fx.update(dt)
        if (m.b.hasBoss && bossAlive) {
            bossShown = approach(bossShown, m.bossHp.toFloat(), dt * 12f)
            if (hash01((t * 3f).toInt(), 4) < 0.02f) fx.steam(500f + 170f, vCeil + m.b.bossOy - 40f, 1f)
        }

        when (phase) {
            FLYING -> {
                val sh = shot!!
                flyD += max(SPEED, sh.trace.length / MAX_FLIGHT) * dt
                if (flyD >= sh.trace.length) {
                    flyD = sh.trace.length
                    impactAt = t; phase = RESOLVING
                    if (sh.power == Power.NONE || sh.power == Power.RAINBOW) ev.sfx(Sfx.LAND, 0.6f, 0.9f + hash01(m.shotsUsed, 3) * 0.2f)
                    wobble(sh.trace.ex, sh.trace.ey, 9f)
                }
            }
            RESOLVING -> resolve()
            ENCORE -> encore(dt)
        }
        if (phase == IDLE && !pendingFire.isNaN()) { val a = pendingFire; pendingFire = Float.NaN; fire(a) }

        // Fizz's mood and gaze
        val low = m.b.lowestRow()
        val danger = if (low < 0) 0f else clamp01((low - (m.b.maxRow - 3)) / 3f)
        fizz.mood = when {
            m.state == Match.WON -> 4
            m.state == Match.LOST -> 3
            danger > 0.3f || (m.limited && m.shotsLeft in 1..3) -> 2
            else -> 0
        }
        val target = if (aiming) aimAng else fizz.ang
        fizz.ang += (target - fizz.ang) * min(1f, dt * 18f)
        fizz.gazeX = cos(fizz.ang); fizz.gazeY = -sin(fizz.ang)
        fizz.blink = (t % 3.7f) < 0.12f
        baron.angry = m.bossHp > 0 && m.bossHp < m.bossMax * 0.4f
        baron.lookX = (500f + cos(fizz.ang) * 200f - 500f) / 300f; baron.lookY = 1f

        val ready = m.powerReady && m.armed == Power.NONE
        if (ready && !wasReady && phase != ENCORE) { ev.sfx(Sfx.HONK, 0.9f); fx.text(METER_X - 40f, METER_Y - 150f, "APPLAUSE!", Pal.GOLD, 44f, 1.2f); meterFlash = 1f }
        wasReady = ready
        if (m.limited && m.shotsLeft != lastShots) {
            if (m.shotsLeft in 1..3 && !lowShotWarned && phase == IDLE) { lowShotWarned = true; fx.text(500f, Board.PIVOT_Y - 230f, "${m.shotsLeft} LEFT!", 0xFFFF8A6A.toInt(), 52f, 1.3f) }
            lastShots = m.shotsLeft
        }
    }

    private fun wobble(x: Float, y: Float, amp: Float) {
        for (i in 0 until Board.N) {
            if (vk[i] == Kind.EMPTY) continue
            val dx = Board.cxi(i) - x; val dy = cellY(i) - y
            val d = sqrt(dx * dx + dy * dy)
            if (d > Board.D * 3.2f || d < 1f) continue
            val k = 1f - d / (Board.D * 3.2f)
            wobT[i] = -d / 1400f; wobA[i] = amp * k; wobX[i] = dx / d; wobY[i] = dy / d
        }
    }

    private fun resolve() {
        val sh = shot ?: run { phase = IDLE; return }
        val el = t - impactAt
        for ((q, e) in sh.events.withIndex()) {
            if (applied[q] || e.t > el) continue
            applied[q] = true
            apply(e, sh)
        }
        if (el >= sh.end + 0.05f) {
            // make sure the view agrees with the model, then hand control back
            for (i in 0 until Board.N) if (vk[i] != m.b.kind[i] || vc[i] != m.b.col[i]) bornT[i] = 9f
            syncView()
            shot = null
            phase = IDLE
            if (m.state == Match.WON) startEncore()
            else if (m.state == Match.LOST) { phase = DONE; finished = true; ev.sfx(Sfx.FAIL, 1f) }
        }
    }

    private fun boardScore(x: Float, y: Float, n: Int, col: Int = Pal.CREAM, sz: Float = 46f) { if (n > 0) fx.text(x, y, "+$n", col, sz) }

    private fun apply(e: Event, sh: Shot) {
        val i = e.i
        val x = if (i >= 0) Board.cxi(i) else e.x
        val y = if (i >= 0) cellY(i) else e.y
        when (e.type) {
            Ev.PLACE -> { vk[i] = Kind.BUBBLE; vc[i] = e.col; vh[i] = 0; bornT[i] = 0.2f; wobble(x, y, 8f); fx.puff(x, y - 10f, withAlpha(Pal.CREAM, 160)) }
            Ev.POP, Ev.INK_WIPE -> {
                clearCell(i)
                if (e.kind == Kind.INK) { fx.inkSplat(x, y); ev.sfx(Sfx.SPLAT, 0.6f) }
                else {
                    fx.pop(x, y, Pal.BUB[e.col.coerceIn(0, 5)], popIdx > 6)
                    ev.sfx(Sfx.POP, 0.75f, min(2f, 0.9f + popIdx * 0.07f))
                    popIdx++
                }
                freed(e, x, y)
                if (!shownPopScore && sh.popped >= 3) {
                    shownPopScore = true
                    boardScore(if (sh.placed >= 0) Board.cxi(sh.placed) else x, (if (sh.placed >= 0) cellY(sh.placed) else y) - 30f, sh.popScore)
                    if (sh.popped + sh.dropped >= 9) {
                        fx.word(500f, max(260f, y - 140f), WORDS[(m.shotsUsed + sh.popped) % WORDS.size], Pal.CREAM)
                        ev.sfx(Sfx.CHEER, 0.8f)
                    }
                    ev.haptic(sh.popped >= 6)
                }
            }
            Ev.BLAST, Ev.PIERCE -> {
                clearCell(i)
                if (e.kind == Kind.STONE) fx.smoke(x, y, 3, 22f, 0xFFB8AE9E.toInt())
                else fx.pop(x, y, if (Kind.colored(e.kind)) Pal.BUB[e.col.coerceIn(0, 5)] else 0xFF6A6470.toInt(), false)
                if (e.type == Ev.PIERCE) fx.note(x, y, x + 60f, y - 200f)
                freed(e, x, y)
            }
            Ev.BOMB -> {
                if (i >= 0) clearCell(i)
                fx.boom(x, y, true)
                fx.text(x, y - 40f, if (e.v == 1) "BANG!" else "KA-BOOM!", 0xFFFFE07A.toInt(), 60f, 0.8f)
                shake = 1f; ev.sfx(Sfx.BOOM, 1f); ev.haptic(true)
                wobble(x, y, 16f)
            }
            Ev.CAGE_HIT -> { vh[i] = e.v; flashT[i] = 0f; ev.sfx(Sfx.CLANK, 0.7f, 1.15f); fx.smoke(x, y, 2, 14f, 0xFFB8BEC8.toInt()) }
            Ev.CAGE_BREAK -> { vk[i] = Kind.BUBBLE; vh[i] = 0; flashT[i] = 0f; fx.bars(x, y); ev.sfx(Sfx.CLANK, 0.9f, 0.85f) }
            Ev.DROP -> {
                clearCell(i)
                fx.faller(x, y, e.kind, e.col, e.v)
                freed(e, x, y)
                if (!shownDrop) {
                    shownDrop = true
                    ev.sfx(Sfx.WHISTLE, 0.7f, 1f)
                    boardScore(x, y - 20f, sh.dropScore, 0xFFFFE07A.toInt(), 54f)
                    if (sh.dropped >= 6) ev.haptic(true)
                }
            }
            Ev.SPOOK -> { vc[i] = e.col; flashT[i] = 0f; if (popIdx == 0 || hash01(i, m.shotsUsed) < 0.3f) ev.sfx(Sfx.SPOOK, 0.35f, 1f + hash01(i, 2) * 0.3f) }
            Ev.INK_SPREAD -> { vk[i] = Kind.INK; vc[i] = 0; bornT[i] = 0f; fx.inkSplat(x, y); ev.sfx(Sfx.SPLAT, 0.7f, 0.8f) }
            Ev.BOSS_HIT -> {
                baron.hurtT = 0f
                fx.text(500f, vCeil + m.b.bossOy - 60f, "-${e.v}", 0xFFFF8A6A.toInt(), 58f)
                fx.boom(500f + (hash01(m.shotsUsed, 9) - 0.5f) * 120f, vCeil + m.b.bossOy + 40f, false)
                ev.sfx(Sfx.BONK, 1f); ev.haptic(true); shake = 0.6f
                if (m.bossHp <= 0 && bossAlive) { bossAlive = false; baron.dead = 0.001f; ev.sfx(Sfx.BOSS_DOWN, 1f) }
            }
            Ev.BOSS_SPAWN -> {
                baron.throwT = 0f
                fx.thrown(500f - 170f, vCeil + m.b.bossOy - 30f, x, y, e.kind, e.col, e.v, i)
                if (popIdx == 0) ev.sfx(Sfx.THROW, 0.7f)
                popIdx = max(popIdx, 1)
            }
            Ev.PRESS -> { ceilFrom = vCeil; ceilTo = m.b.ceil; ceilT = 0f; shake = 0.8f; ev.sfx(Sfx.THUD, 1f); ev.haptic(true) }
            Ev.SPLAT -> { fx.pop(e.x, e.y, Pal.BUB[e.col.coerceIn(0, 5)], true); ev.sfx(Sfx.SPLAT, 0.8f) }
            Ev.FIZZLE -> { fx.pop(e.x, e.y, Pal.BUB[e.col.coerceIn(0, 5)], false); ev.sfx(Sfx.POP, 0.5f, 0.7f) }
            Ev.RECOLOR -> fx.puff(500f + (if (i == 0) 0f else Toon.gloveX(fizz)), Board.PIVOT_Y + (if (i == 0) -Board.SPOUT else Toon.gloveY(fizz)), Pal.CREAM)
        }
    }

    private fun clearCell(i: Int) { if (i in 0 until Board.N) { vk[i] = Kind.EMPTY; vh[i] = 0 } }

    private fun freed(e: Event, x: Float, y: Float) {
        if (e.kind == Kind.NOTE) {
            fx.note(x, y, L.toBx(goalX), L.toBy(goalY))
            fx.text(x, y - 50f, "+${Match.NOTE_SCORE}", Pal.GOLD, 44f)
            ev.sfx(Sfx.NOTE, 0.9f, 1f + (m.notesFreed % 5) * 0.06f)
        } else if (e.kind == Kind.TICKET) {
            fx.text(x, y - 40f, "TICKET!", Pal.GOLD, 40f)
            ev.sfx(Sfx.TICKET, 0.8f)
        }
    }

    // ------------------------------------------------------------------ encore

    private fun startEncore() {
        phase = ENCORE; encoreT = 0f; encoreFired = 0
        encoreShots = if (m.limited) max(0, m.shotsLeft) else max(0, spec.par - m.shotsUsed)
        encoreBonus = m.finish()
        ev.sfx(Sfx.WIN, 1f)
        fx.word(500f, 520f, if (spec.goal == Goal.BOSS) "BOILED!" else "CURTAIN CALL!", Pal.CREAM)
        // everyone left on stage takes a bow (and falls off it)
        var n = 0
        for (i in 0 until Board.N) if (vk[i] != Kind.EMPTY) { fx.faller(Board.cxi(i), cellY(i), vk[i], vc[i], vh[i]); vk[i] = Kind.EMPTY; n++ }
        if (n > 0) boardScore(500f, 700f, n * 10, 0xFFFFE07A.toInt(), 50f)
        fx.confetti(60f, 940f, 80f, 70)
    }

    private fun encore(dt: Float) {
        encoreT += dt
        val shown = min(encoreShots, 18)
        val due = ((encoreT - 0.9f) / 0.16f).toInt()
        while (encoreFired < shown && encoreFired <= due) {
            val x = 140f + hash01(encoreFired, 3) * 720f; val y = 280f + hash01(encoreFired, 5) * 520f
            fx.boom(x, y, false)
            fx.confetti(x - 60f, x + 60f, y, 10)
            val each = if (encoreFired == shown - 1) (encoreShots - shown + 1) * Match.ENCORE_SHOT else Match.ENCORE_SHOT
            fx.text(x, y - 40f, "+$each", Pal.GOLD, 44f)
            ev.sfx(Sfx.FIREWORK, 0.7f, 0.9f + hash01(encoreFired, 7) * 0.3f)
            encoreFired++
            fizz.fireT = 0f
        }
        if (encoreT > 1.4f + shown * 0.16f + 0.6f) { phase = DONE; finished = true }
    }

    // ------------------------------------------------------------------ drawing

    fun draw(c: Canvas) {
        Stage.batten(c, L, vCeil)
        val low = m.b.lowestRow()
        val danger = if (low < 0) 0f else clamp01((low - (m.b.maxRow - 2)) / 2f)
        Stage.deadLine(c, L, m.b.drops, danger, t)
        c.save()
        val sx = if (shake > 0f) sin(t * 70f) * 9f * shake else 0f
        val sy = if (shake > 0f) cos(t * 63f) * 7f * shake else 0f
        c.translate(L.bx + sx, L.by + sy)
        c.scale(L.k, L.k)
        if (m.b.hasBoss && (bossAlive || baron.dead < 1f)) {
            c.save(); c.translate(m.b.bossX, vCeil + m.b.bossOy); Toon.baron(c, baron); c.restore()
        }
        drawBubbles(c)
        fx.drawWorld(c)
        drawFallers(c)
        if (aiming && phase != ENCORE) drawGuide(c)
        drawShot(c)
        drawFizz(c)
        drawMeter(c)
        fx.drawTop(c)
        c.restore()
        drawHud(c)
        drawBanner(c)
    }

    /** "Scene 3: Free the Music" slides in while the curtains part. */
    private fun drawBanner(c: Canvas) {
        if (t < 0.35f || t > 2.6f) return
        val k = t - 0.35f
        val inX = if (k < 0.35f) lerp(-700f, 0f, easeOutBack(k / 0.35f)) else if (k > 1.9f) lerp(0f, 900f, easeIn((k - 1.9f) / 0.35f)) else 0f
        val cy = L.vh * 0.42f
        c.save(); c.translate(inX, 0f); c.rotate(-4f, 500f, cy)
        rf.set(110f, cy - 95f, 890f, cy + 95f)
        UI.ticket(c, rf, "", false, Pal.VELVET)
        Draw.text(c, "REEL ${spec.reel + 1}  \u2022  SCENE ${spec.idx + 1}", 500f, cy - 40f, 28f, Pal.GOLD, Fonts.deco, spacing = 0.2f)
        Draw.toon(c, Goal.TITLE[spec.goal], 500f, cy + 22f, Draw.fit(Goal.TITLE[spec.goal], 64f, 700f, Fonts.title), Pal.CREAM, Fonts.title, 7f, 3f)
        c.restore()
    }

    private fun boil(i: Int) = (((t * 7f).toInt() + (hash01(i, 11) * 3f).toInt()) % 3)

    private fun gazeIndex(x: Float, y: Float, tx: Float, ty: Float): Int {
        val dx = tx - x; val dy = ty - y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val gx = if (dx / d > 0.38f) 1 else if (dx / d < -0.38f) -1 else 0
        val gy = if (dy / d > 0.38f) 1 else if (dy / d < -0.38f) -1 else 0
        return (gy + 1) * 3 + (gx + 1)
    }

    private fun drawBubbles(c: Canvas) {
        val sh = shot
        var tx = 500f; var ty = Board.PIVOT_Y
        if (aiming) { tx = aimX; ty = aimY }
        else if (phase == FLYING && sh != null) { val pt = pointAt(sh.trace, flyD); tx = pt[0]; ty = pt[1] }
        val scared = phase == FLYING && sh != null && sh.power == Power.FIRECRACKER
        for (i in 0 until Board.N) {
            val k = vk[i]
            if (k == Kind.EMPTY) continue
            val r = Board.row(i)
            var x = Board.cxi(i); var y = cellY(i)
            if (wobT[i] < 1.2f && wobT[i] > 0f) { val w = boing(wobT[i], 3.4f, 5.5f) * wobA[i]; x += wobX[i] * w; y += wobY[i] * w }
            val pulse = max(0f, cos((beat - r * 0.035f) * TAU))
            val bump = pulse * pulse * pulse * pulse
            var sx = 1f + 0.035f * bump; var sy = 1f - 0.04f * bump
            y -= bump * 2.5f
            if (bornT[i] < 0.3f) { val g = easeOutBack(bornT[i] / 0.3f); sx *= g; sy *= g }
            val b = boil(i)
            val col = vc[i].coerceIn(0, 5)
            when (k) {
                Kind.STONE -> art.stone[b].draw(c, x, y, sx, sy)
                Kind.BOMB -> { art.bomb[b].draw(c, x, y, sx, sy); fuse(c, x, y) }
                Kind.INK -> art.ink[b].draw(c, x, y, sx * (1f + 0.04f * sin(t * 5f + i)), sy)
                Kind.SPOOK -> {
                    val bob = sin(t * 3f + i) * 3f
                    art.spook[col][b].draw(c, x, y + bob, sx, sy)
                    if (flashT[i] < 0.3f) { f.color = withAlpha(0xFFFFFFFF.toInt(), (200 * (1f - flashT[i] / 0.3f)).toInt()); c.drawCircle(x, y + bob, Board.R, f) }
                }
                else -> {
                    art.body[col][b].draw(c, x, y, sx, sy)
                    if (k == Kind.NOTE) art.note.draw(c, x, y + sin(t * 4f + i) * 2f, sx, sy)
                    else {
                        val blink = ((t + hash01(i, 7) * 6f) % 5.3f) < 0.13f
                        val fi = when { scared -> Art.F_SCARED; blink -> Art.F_BLINK; m.state == Match.LOST -> Art.F_HAPPY; else -> gazeIndex(x, y, tx, ty) }
                        art.face[col][fi].draw(c, x, y, sx, sy)
                    }
                    if (k == Kind.CAGE) (if (vh[i] >= 2) art.cage2 else art.cage1).draw(c, x, y, sx, sy)
                    if (k == Kind.TICKET) art.ticket.draw(c, x, y, sx, sy)
                    if (flashT[i] < 0.25f) { f.color = withAlpha(0xFFFFFFFF.toInt(), (180 * (1f - flashT[i] / 0.25f)).toInt()); c.drawCircle(x, y, Board.R - 3f, f) }
                }
            }
        }
    }

    private fun fuse(c: Canvas, x: Float, y: Float) {
        val fx0 = x + 30f; val fy0 = y - 40f
        s.color = Pal.INK; s.strokeWidth = 4f
        p.reset(); p.moveTo(fx0, fy0); p.quadTo(fx0 + 12f, fy0 - 14f, fx0 + 6f, fy0 - 24f); c.drawPath(p, s)
        val fl = 0.6f + 0.4f * sin(t * 30f)
        Draw.star(p, fx0 + 6f, fy0 - 26f, 9f * fl + 4f, 0.4f, t * 9f, 6)
        f.color = 0xFFFFD34A.toInt(); c.drawPath(p, f)
        f.color = 0xFFFFFFFF.toInt(); c.drawCircle(fx0 + 6f, fy0 - 26f, 2.5f, f)
    }

    private fun drawFallers(c: Canvas) {
        for (q in fx.parts) {
            if (!q.alive) continue
            if (q.type == Fx.FALLER || q.type == Fx.THROWN) {
                c.save(); c.translate(q.x, q.y); c.rotate(q.rot * 20f)
                drawCritter(c, q.kind, q.col, q.face, if (q.type == Fx.FALLER) Art.F_SCARED else Art.F_HAPPY, 1f)
                c.restore()
            }
        }
    }

    private fun drawCritter(c: Canvas, kind: Int, col0: Int, hp: Int, face: Int, sc: Float) {
        val col = col0.coerceIn(0, 5)
        when (kind) {
            Kind.STONE -> art.stone[0].draw(c, 0f, 0f, sc)
            Kind.BOMB -> art.bomb[0].draw(c, 0f, 0f, sc)
            Kind.INK -> art.ink[0].draw(c, 0f, 0f, sc)
            Kind.SPOOK -> art.spook[col][0].draw(c, 0f, 0f, sc)
            else -> {
                art.body[col][0].draw(c, 0f, 0f, sc)
                if (kind == Kind.NOTE) art.note.draw(c, 0f, 0f, sc) else art.face[col][face].draw(c, 0f, 0f, sc)
                if (kind == Kind.CAGE) (if (hp >= 2) art.cage2 else art.cage1).draw(c, 0f, 0f, sc)
                if (kind == Kind.TICKET) art.ticket.draw(c, 0f, 0f, sc)
            }
        }
    }

    private val pt = FloatArray(2)
    private fun pointAt(tr: Trace, d: Float): FloatArray {
        var rem = d
        for (q in 1 until tr.n) {
            val dx = tr.px[q] - tr.px[q - 1]; val dy = tr.py[q] - tr.py[q - 1]
            val l = sqrt(dx * dx + dy * dy)
            if (rem <= l || q == tr.n - 1) { val k = if (l > 0f) clamp01(rem / l) else 1f; pt[0] = tr.px[q - 1] + dx * k; pt[1] = tr.py[q - 1] + dy * k; return pt }
            rem -= l
        }
        pt[0] = tr.px[0]; pt[1] = tr.py[0]; return pt
    }

    private fun drawGuide(c: Canvas) {
        if (aimDirty) { m.aim(aimAng, aimTr); aimDirty = false }
        val maxLen = if (spyglass) 99999f else spec.guide
        val len = min(aimTr.length, maxLen)
        val col = if (m.armed != Power.NONE) Pal.GOLD else Pal.BUB[m.cur.coerceIn(0, 5)]
        val off = (t * 120f) % 30f
        var d = 40f + off
        while (d < len - 10f) {
            val q = pointAt(aimTr, d)
            val fade = if (d > len - 200f && len < aimTr.length) clamp01((len - d) / 200f) else 1f
            f.color = withAlpha(Pal.INK, (200 * fade).toInt()); c.drawCircle(q[0], q[1], 8f, f)
            f.color = withAlpha(lerpColor(col, Pal.CREAM, 0.35f), (255 * fade).toInt()); c.drawCircle(q[0], q[1], 5.5f, f)
            d += 30f
        }
        if (len >= aimTr.length - 1f) {
            if (aimTr.land >= 0 && m.armed != Power.TRUMPET) {
                val x = Board.cxi(aimTr.land); val y = cellY(aimTr.land)
                s.color = withAlpha(Pal.INK, 170); s.strokeWidth = 7f; c.drawCircle(x, y, Board.R - 4f, s)
                s.color = withAlpha(lerpColor(col, Pal.CREAM, 0.3f), 230); s.strokeWidth = 4f
                for (k in 0 until 8) { val a = k * TAU / 8f + t * 2f; rf.set(x - Board.R + 4f, y - Board.R + 4f, x + Board.R - 4f, y + Board.R - 4f); c.drawArc(rf, a * 57.3f, 24f, false, s) }
            } else if (aimTr.boss) {
                Draw.star(p, aimTr.ex, aimTr.ey - 20f, 22f + 4f * sin(t * 9f)); f.color = Pal.INK; c.drawPath(p, f)
                Draw.star(p, aimTr.ex, aimTr.ey - 20f, 16f + 4f * sin(t * 9f)); f.color = Pal.GOLD; c.drawPath(p, f)
            }
        }
    }

    private fun shotSprite(c: Canvas, power: Int, col: Int, x: Float, y: Float, sc: Float, rot: Float = 0f) {
        when (power) {
            Power.FIRECRACKER -> { c.save(); c.translate(x, y); c.rotate(rot); art.firecracker.draw(c, 0f, 0f, sc); c.restore()
                Draw.star(p, x + 18f * sc, y - 34f * sc, (10f + 4f * sin(t * 30f)) * sc, 0.4f, t * 8f, 6); f.color = 0xFFFFD34A.toInt(); c.drawPath(p, f) }
            Power.RAINBOW -> { c.save(); c.translate(x, y); c.rotate(t * 90f); art.rainbow[boil(7)].draw(c, 0f, 0f, sc); c.restore() }
            Power.TRUMPET -> { c.save(); c.translate(x, y); c.rotate(rot); art.trumpet.draw(c, 0f, 0f, sc * 1.2f); c.restore() }
            else -> { val cc = col.coerceIn(0, 5); art.body[cc][boil(3)].draw(c, x, y, sc); art.face[cc][4].draw(c, x, y, sc) }
        }
    }

    private fun drawShot(c: Canvas) {
        val sh = shot ?: return
        if (phase != FLYING && !(sh.power == Power.TRUMPET && phase == RESOLVING && t - impactAt < sh.trace.length / max(SPEED, sh.trace.length / MAX_FLIGHT))) return
        val d = if (sh.power == Power.TRUMPET) (t - impactAt) * max(SPEED, sh.trace.length / MAX_FLIGHT) else flyD
        val q = pointAt(sh.trace, d)
        // fizzy trail
        for (k in 1..5) {
            val b = pointAt(sh.trace, max(0f, d - k * 22f))
            f.color = withAlpha(0xFFFFFFFF.toInt(), 150 - k * 25); c.drawCircle(b[0] + sin(t * 40f + k) * 4f, b[1], 9f - k, f)
        }
        val q2 = pointAt(sh.trace, d)
        shotSprite(c, sh.power, sh.color, q2[0], q2[1], 1f, t * 600f)
    }

    private fun drawFizz(c: Canvas) {
        c.save(); c.translate(500f, Board.PIVOT_Y)
        Toon.fizz(c, fizz)
        // loaded bubble on the spout, next one in the glove (they trade places on a swap)
        val sx = cos(fizz.ang) * Board.SPOUT; val sy = -sin(fizz.ang) * Board.SPOUT
        val gx = Toon.gloveX(fizz); val gy = Toon.gloveY(fizz)
        val k = if (fizz.swapT < 0.28f) easeOut(fizz.swapT / 0.28f) else 1f
        val loading = phase == FLYING || (fizz.fireT < 0.18f)
        if (m.state == Match.PLAYING && phase != ENCORE) {
            val curX = lerp(gx, sx, k); val curY = lerp(gy, sy, k) - sin(k * PI_F) * 60f
            val nxtX = lerp(sx, gx, k); val nxtY = lerp(sy, gy, k) - sin(k * PI_F) * 90f
            val nsc = lerp(1f, 0.72f, k)
            shotSprite(c, Power.NONE, m.next, nxtX, nxtY, nsc)
            if (!loading) shotSprite(c, m.armed, m.cur, curX, curY, lerp(0.72f, 1f, k), -fizz.ang * 57.3f + 90f)
            else { val g = clamp01((fizz.fireT - 0.06f) / 0.12f); if (g > 0f && phase != FLYING) shotSprite(c, m.armed, m.cur, sx, sy, g) }
        }
        c.restore()
    }

    private fun drawMeter(c: Canvas) {
        if (spec.powers == 0) return
        val x = METER_X; val y = METER_Y
        val fill = clamp01(m.applause / Match.APPLAUSE_MAX)
        val ready = m.powerReady && m.armed == Power.NONE
        val bounce = if (ready) abs(sin(t * 6f)) * 8f else 0f
        c.save(); c.translate(x, y - bounce)
        // stand
        f.color = Pal.INK; c.drawRect(-8f, 20f, 8f, 120f, f)
        f.color = Pal.GOLD_DK; c.drawRect(-5f, 20f, 5f, 118f, f)
        f.color = Pal.INK; rf.set(-40f, 110f, 40f, 132f); c.drawRoundRect(rf, 8f, 8f, f)
        // dial
        f.color = Pal.INK; rf.set(-82f, -78f, 82f, 40f); c.drawRoundRect(rf, 22f, 22f, f)
        f.color = 0xFF5A0E15.toInt(); rf.set(-76f, -72f, 76f, 34f); c.drawRoundRect(rf, 18f, 18f, f)
        f.color = 0xFFF5E8C8.toInt(); rf.set(-62f, -60f, 62f, 64f); c.drawArc(rf, 180f, 180f, true, f)
        // coloured fill arc
        f.color = if (ready) lerpColor(Pal.GOLD, 0xFFFFFFFF.toInt(), 0.5f + 0.5f * sin(t * 12f)) else 0xFFE8A040.toInt()
        rf.set(-54f, -52f, 54f, 56f); c.drawArc(rf, 180f, 180f * fill, true, f)
        f.color = 0xFFF5E8C8.toInt(); rf.set(-30f, -28f, 30f, 32f); c.drawArc(rf, 180f, 180f, true, f)
        s.color = Pal.INK; s.strokeWidth = 2f
        for (q in 0..8) { val a = PI_F + q * PI_F / 8f; c.drawLine(cos(a) * 44f, 2f + sin(a) * 44f, cos(a) * 58f, 2f + sin(a) * 58f, s) }
        val na = PI_F + fill * PI_F + (if (ready) sin(t * 20f) * 0.06f else 0f)
        s.color = Pal.VELVET; s.strokeWidth = 5f; c.drawLine(0f, 2f, cos(na) * 50f, 2f + sin(na) * 50f, s)
        f.color = Pal.INK; c.drawCircle(0f, 2f, 7f, f)
        Draw.text(c, "APPLAUSE", 0f, 20f, 15f, Pal.GOLD, Fonts.deco, spacing = 0.08f)
        // bulbs
        for (q in 0 until 7) {
            val on = ready && ((t * 8f).toInt() + q) % 2 == 0 || (!ready && q < (fill * 7f).toInt())
            f.color = Pal.INK; c.drawCircle(-60f + q * 20f, -70f, 7f, f)
            f.color = if (on) 0xFFFFE9A0.toInt() else 0xFF6A5040.toInt(); c.drawCircle(-60f + q * 20f, -70f, 5f, f)
        }
        if (ready) {
            val p2 = m.nextPowerType()
            val sc = 0.8f + 0.06f * sin(t * 8f)
            shotSprite(c, p2, 0, 0f, -128f, sc, sin(t * 4f) * 10f)
            Draw.toon(c, "TAP!", 0f, -186f, 34f, Pal.CREAM, Fonts.chunky)
        }
        if (m.armed != Power.NONE) Draw.toon(c, "LOADED!", 0f, -100f, 26f, Pal.GOLD, Fonts.chunky)
        c.restore()
    }

    // ------------------------------------------------------------------ HUD (world units)

    private fun drawHud(c: Canvas) {
        val top = L.iT + 10f
        val bottom = L.valanceTop - 14f
        val h = (bottom - top).coerceAtLeast(110f)
        val sc = (h / 150f).coerceIn(0.75f, 1.25f)
        val cy = top + h * 0.42f
        // pause medallion
        val pr = 44f * sc
        pauseRect.set(28f, cy - pr, 28f + pr * 2f, cy + pr)
        f.color = Pal.INK; c.drawCircle(28f + pr, cy, pr + 4f, f)
        f.color = Pal.VELVET; c.drawCircle(28f + pr, cy, pr, f)
        s.color = Pal.GOLD; s.strokeWidth = 4f; c.drawCircle(28f + pr, cy, pr - 6f, s)
        f.color = Pal.CREAM; c.drawRect(28f + pr - 14f * sc, cy - 16f * sc, 28f + pr - 5f * sc, cy + 16f * sc, f); c.drawRect(28f + pr + 5f * sc, cy - 16f * sc, 28f + pr + 14f * sc, cy + 16f * sc, f)

        // marquee with chasing bulbs
        val mw = 560f; val mh = h * 0.78f
        rf.set(500f - mw / 2f, cy - mh / 2f, 500f + mw / 2f, cy + mh / 2f)
        Draw.decoPanel(c, rf, 0xFF2A0E12.toInt(), 22f * sc, Pal.GOLD, 6f * sc)
        val nb = 22
        for (q in 0 until nb) {
            val per = q.toFloat() / nb
            perimeter(rf, per); val bx = per2[0]; val by = per2[1]
            val on = ((t * 10f).toInt() + q) % 3 != 0
            f.color = Pal.INK; c.drawCircle(bx, by, 6.5f * sc, f)
            f.color = if (on) 0xFFFFE9A0.toInt() else 0xFF7A5A40.toInt(); c.drawCircle(bx, by, 4.5f * sc, f)
        }
        goalX = 500f; goalY = cy + mh * 0.12f
        Draw.text(c, Goal.TITLE[spec.goal], 500f, cy - mh * 0.24f, Draw.fit(Goal.TITLE[spec.goal], 24f * sc, mw - 80f, Fonts.deco, 0.1f), Pal.GOLD, Fonts.deco, spacing = 0.1f)
        val big = 46f * sc
        when (spec.goal) {
            Goal.NOTES -> {
                val str = "${m.notesFreed} / ${m.notesTotal}"
                val w = Draw.width(str, big, Fonts.chunky)
                noteIcon(c, 500f - w / 2f - 30f * sc, goalY, sc)
                Draw.toon(c, str, 500f + 10f * sc, goalY, big, Pal.CREAM, Fonts.chunky)
            }
            Goal.BOSS -> {
                val bw = mw - 120f; val bh = 26f * sc
                val x0 = 500f - bw / 2f
                rf.set(x0 - 4f, goalY - bh / 2f - 4f, x0 + bw + 4f, goalY + bh / 2f + 4f); f.color = Pal.INK; c.drawRoundRect(rf, 10f, 10f, f)
                rf.set(x0, goalY - bh / 2f, x0 + bw * clamp01(bossShown / m.bossMax.coerceAtLeast(1)), goalY + bh / 2f)
                f.color = if (baron.angry) lerpColor(Pal.RED, 0xFFFF9050.toInt(), 0.5f + 0.5f * sin(t * 10f)) else Pal.RED; c.drawRoundRect(rf, 8f, 8f, f)
                f.color = 0x40FFFFFF; c.drawRect(rf.left, rf.top, rf.right, rf.top + bh * 0.35f, f)
                Draw.text(c, "BARON", x0 + 10f, goalY, 16f * sc, Pal.CREAM, Fonts.chunky, Paint.Align.LEFT)
            }
            else -> {
                val left = max(0, m.b.coloredCount() - Match.CURTAIN_CALL)
                Draw.toon(c, "$left", 500f, goalY, big, Pal.CREAM, Fonts.chunky)
                Draw.text(c, "TO GO", 500f + Draw.width("$left", big, Fonts.chunky) / 2f + 46f * sc, goalY + 6f, 18f * sc, Pal.GOLD, Fonts.deco)
                if (spec.goal == Goal.PRESS) Draw.text(c, "DROP IN ${m.pressTimer}", 500f - 150f * sc, goalY + 4f, 18f * sc, if (m.pressTimer <= 2) 0xFFFF8A6A.toInt() else Pal.CREAM, Fonts.body)
            }
        }
        // shots ticket
        val tx = 1000f - 28f - 120f * sc
        rf.set(tx, cy - 50f * sc, 1000f - 28f, cy + 50f * sc)
        f.color = Pal.INK; c.drawRoundRect(rf.left - 4f, rf.top - 4f, rf.right + 4f, rf.bottom + 4f, 12f, 12f, f)
        val low = m.limited && m.shotsLeft in 0..3
        f.color = if (low) lerpColor(0xFFE04A3B.toInt(), 0xFFF5E8C8.toInt(), 0.5f + 0.5f * sin(t * 10f)) else Pal.CREAM
        c.drawRoundRect(rf, 10f, 10f, f)
        f.color = Pal.INK; c.drawCircle(rf.left, rf.centerY(), 10f, f); c.drawCircle(rf.right, rf.centerY(), 10f, f)
        if (m.limited) {
            Draw.text(c, "SHOTS", rf.centerX(), rf.top + 20f * sc, 15f * sc, Pal.VELVET, Fonts.deco, spacing = 0.1f)
            Draw.toon(c, "${m.shotsLeft}", rf.centerX(), rf.centerY() + 12f * sc, 44f * sc, Pal.GOLD, Fonts.chunky, 6f * sc, 2f * sc)
        } else {
            Draw.text(c, "PAR ${spec.par}", rf.centerX(), rf.top + 20f * sc, 15f * sc, Pal.VELVET, Fonts.deco, spacing = 0.06f)
            Draw.toon(c, "${m.shotsUsed}", rf.centerX(), rf.centerY() + 12f * sc, 40f * sc, if (m.shotsUsed > spec.par) 0xFFE04A3B.toInt() else Pal.GOLD, Fonts.chunky, 6f * sc, 2f * sc)
        }
        // score and star meter under the marquee
        val sy = cy + mh / 2f + 22f * sc
        if (sy + 12f < L.valanceTop + 20f) {
            val bw = mw - 60f; val x0 = 500f - bw / 2f
            val maxS = spec.s3 * 1.12f
            f.color = Pal.INK; rf.set(x0 - 3f, sy - 7f, x0 + bw + 3f, sy + 7f); c.drawRoundRect(rf, 7f, 7f, f)
            f.color = Pal.GOLD; rf.set(x0, sy - 4f, x0 + bw * clamp01(m.score / maxS), sy + 4f); c.drawRoundRect(rf, 4f, 4f, f)
            for ((q, th) in floatArrayOf(spec.s2 * 0.5f, spec.s2.toFloat(), spec.s3.toFloat()).withIndex()) {
                val x = x0 + bw * clamp01(th / maxS)
                val got = m.score >= th
                Draw.star(p, x, sy, 15f); f.color = Pal.INK; c.drawPath(p, f)
                Draw.star(p, x, sy, 11f); f.color = if (got) Pal.GOLD else 0xFF6A5040.toInt(); c.drawPath(p, f)
            }
            Draw.text(c, fmt("%,d", m.score), x0 - 14f, sy, 22f * sc, Pal.CREAM, Fonts.chunky, Paint.Align.RIGHT)
        }
    }

    private val per2 = FloatArray(2)
    private fun perimeter(r: RectF, t: Float) {
        val w = r.width(); val h = r.height(); val per = 2 * (w + h)
        var d = t * per
        if (d < w) { per2[0] = r.left + d; per2[1] = r.top; return }; d -= w
        if (d < h) { per2[0] = r.right; per2[1] = r.top + d; return }; d -= h
        if (d < w) { per2[0] = r.right - d; per2[1] = r.bottom; return }; d -= w
        per2[0] = r.left; per2[1] = r.bottom - d
    }

    private fun noteIcon(c: Canvas, x: Float, y: Float, sc: Float) {
        c.save(); c.translate(x, y); c.scale(sc * 1.2f, sc * 1.2f)
        f.color = Pal.GOLD
        rf.set(-13f, 4f, 1f, 16f); c.drawOval(rf, f); c.drawRect(-2.5f, -22f, 1.5f, 10f, f)
        p.reset(); p.moveTo(1.5f, -22f); p.cubicTo(9f, -14f, 16f, -12f, 13f, 0f); p.cubicTo(12f, -8f, 8f, -11f, 1.5f, -12f); p.close(); c.drawPath(p, f)
        c.restore()
    }

    /** Continue after a loss (Encore! tickets). */
    fun continueAfterLoss(extra: Int) {
        if (m.encoreContinue(extra)) { syncView(); phase = IDLE; finished = false; resultShown = false; fizz.moodT = 0f; ev.sfx(Sfx.POWER, 1f) }
    }

    val busy get() = phase == FLYING || phase == RESOLVING
}
