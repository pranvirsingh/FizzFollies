package com.pranvir.fizz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

interface Host {
    fun loadInt(key: String, def: Int): Int
    fun saveInt(key: String, v: Int)
    fun sound(id: Int, vol: Float, rate: Float)
    fun setAudio(sound: Boolean, music: Boolean, crackle: Boolean)
    fun setMusicState(scene: Int, intensity: Float)
    fun beatPhase(): Float
    fun haptic(strong: Boolean)
}

/** A silent-film title card. */
class Card(val title: String, val lines: List<String>, val illus: Int)

/**
 * The whole show: studio card, title, the reel map, scene cards, play, intermissions and results.
 * World units are 1000 wide; everything below is drawn in them except the film overlay.
 */
class Game(private val host: Host) : PlayEvents {
    companion object {
        const val M_STUDIO = 0; const val M_TITLE = 1; const val M_MAP = 2; const val M_PLAY = 3
        const val O_NONE = 0; const val O_INTRO = 1; const val O_PAUSE = 2; const val O_WIN = 3; const val O_LOSE = 4; const val O_SETTINGS = 5; const val O_CONFIRM = 6; const val O_FINALE = 7
        val BOOST_NAMES = arrayOf("SPYGLASS", "+5 SHOTS", "FIRECRACKER")
        val BOOST_DESC = arrayOf("Full aim line", "More shots", "Start loaded")
        val BOOST_COST = intArrayOf(15, 20, 25)
        const val CONTINUE_COST = 30
        // card illustrations
        const val IL_NONE = 0; const val IL_FIZZ = 1; const val IL_NOTE = 2; const val IL_METER = 3; const val IL_BARON = 4; const val IL_STONEBOMB = 5
        const val IL_RAINBOW = 6; const val IL_SPOOK = 7; const val IL_TRUMPET = 8; const val IL_CAGE = 9; const val IL_PRESS = 10; const val IL_INK = 11; const val IL_CAGE2 = 12; const val IL_POSTER = 13
        const val B_PLAY = 1; const val B_SETTINGS = 2; const val B_BACK = 3; const val B_NODE = 4; const val B_GO = 5; const val B_CLOSE = 6; const val B_BOOST = 7
        const val B_RESUME = 8; const val B_RESTART = 9; const val B_MAP = 10; const val B_NEXT = 11; const val B_CONTINUE = 12; const val B_SOUND = 13; const val B_MUSIC = 14
        const val B_FILM = 15; const val B_VIB = 16; const val B_RESET = 17; const val B_YES = 18; const val B_NO = 19; const val B_PAUSE = 20; const val B_CRACKLE = 21
        const val SEC = 2000f
    }

    var mode = M_STUDIO; private set
    private var modeT = 0f
    var overlay = O_NONE; private set
    private var overlayT = 0f
    val L = Layout()
    private var wPx = 1080; private var hPx = 2340
    private var insL = 0; private var insT = 0; private var insR = 0; private var insB = 0
    var t = 0f; private set

    // assets
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "fizz-art").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private val artRef = AtomicReference<Art?>(null)
    @Volatile private var artWanted = -1f
    private var art: Art? = null
    val ready get() = art != null
    /** Test hook: jump straight into a level. */
    fun debugStart(n: Int) { overlay = O_NONE; startLevel(n); cards.clear() }
    fun debugCards(n: Int) { cards.clear(); seen = 0; storyShown = false; queueIntroCards(n) }
    fun debugOverlay(o: Int) { openOverlay(o); resultT = 5f }
    fun debugIntro(n: Int) { openIntro(n); overlayT = 5f }
    fun debugMap(n: Int) { go(M_MAP); focusMap(n) }
    fun debugTickets(n: Int) { tickets = n }
    fun debugStars(n: Int, st: Int) { stars[n] = st }
    val cardCount get() = cards.size
    /** Test hook: press the button with this id if it was drawn last frame. */
    fun debugTap(id: Int): Boolean {
        val b = btns.lastOrNull { it.id == id && it.enabled } ?: return false
        val x = b.r.centerX() * L.s; val y = b.r.centerY() * L.s
        touchDown(0, x, y); touchUp(0, x, y)
        return true
    }
    fun debugSkipCards() { while (cards.isNotEmpty()) { cardT = 1f; advanceCard() } }
    /** Test hook: centres (in pixels) of the buttons drawn last frame, plus open map nodes. */
    fun tapTargets(): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (b in btns) if (b.enabled) out.add(floatArrayOf(b.r.centerX() * L.s, b.r.centerY() * L.s))
        if (mode == M_MAP && overlay == O_NONE) for (n in 0 until Reels.TOTAL) if (levelOpen(n)) {
            val y = nodeY(n) - mapScroll
            if (y in 150f..L.vh - 50f) out.add(floatArrayOf(nodeX(n) * L.s, y * L.s))
        }
        return out
    }
    private var artScale = -1f
    private val film = Film()
    private var stageBmp: Bitmap? = null
    private var stageKey = ""
    private val posters = arrayOfNulls<Bitmap>(Reels.COUNT)
    private var posterW = 0

    // progress
    private val stars = IntArray(Reels.TOTAL)
    private val best = IntArray(Reels.TOTAL)
    private var tickets = 0
    private val boosts = IntArray(3)
    private var seen = 0
    private var storyShown = false
    var soundOn = true; var musicOn = true; var filmOn = true; var vibOn = true; var crackleOn = true
    private var lastLevel = 0

    // play
    var play: Play? = null; private set
    private var level = 0
    private val boostSel = BooleanArray(3)
    private var continues = 0
    private var resultT = 0f
    private var resultStars = 0
    private var resultTickets = 0
    private var newBest = false
    private var tutorialStep = 0
    private val cards = ArrayList<Card>()
    private var cardT = 0f
    private var pendingAfterCards: (() -> Unit)? = null

    // transitions (iris)
    private var transT = -1f
    private var transAction: (() -> Unit)? = null
    private var transX = 500f; private var transY = 1000f

    // map
    private var mapScroll = 0f
    private var mapVel = 0f
    private var mapDragging = false
    private var dragLastY = 0f; private var dragStartY = 0f; private var dragMoved = false
    private var unlockFlash = -1; private var unlockT = 0f

    // buttons
    private val btns = ArrayList<Btn>()
    private var pressed: Btn? = null
    private var pointer = -1

    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val p = Path()
    private val rf = RectF()
    private val rf2 = RectF()
    private val srcR = Rect()
    private val titleFizz = Toon.FizzState()
    private val mapBaron = Toon.BaronState()

    init { load() }

    // ------------------------------------------------------------------ saves

    private fun load() {
        for (n in 0 until Reels.TOTAL) { stars[n] = host.loadInt("st$n", 0).coerceIn(0, 3); best[n] = max(0, host.loadInt("sc$n", 0)) }
        tickets = max(0, host.loadInt("tk", 40))
        for (k in 0 until 3) boosts[k] = max(0, host.loadInt("b$k", 2))
        seen = host.loadInt("seen", 0)
        storyShown = host.loadInt("story", 0) == 1
        soundOn = host.loadInt("o_snd", 1) == 1; musicOn = host.loadInt("o_mus", 1) == 1
        filmOn = host.loadInt("o_film", 1) == 1; vibOn = host.loadInt("o_vib", 1) == 1; crackleOn = host.loadInt("o_crk", 1) == 1
        lastLevel = host.loadInt("cur", 0).coerceIn(0, Reels.TOTAL - 1)
        film.enabled = filmOn
        host.setAudio(soundOn, musicOn, crackleOn)
    }

    private fun saveOptions() {
        host.saveInt("o_snd", if (soundOn) 1 else 0); host.saveInt("o_mus", if (musicOn) 1 else 0)
        host.saveInt("o_film", if (filmOn) 1 else 0); host.saveInt("o_vib", if (vibOn) 1 else 0); host.saveInt("o_crk", if (crackleOn) 1 else 0)
        film.enabled = filmOn
        host.setAudio(soundOn, musicOn, crackleOn)
    }

    private fun saveTickets() { host.saveInt("tk", tickets) }
    private fun saveBoosts() { for (k in 0 until 3) host.saveInt("b$k", boosts[k]) }

    fun totalStars(): Int = stars.sum()
    fun bossBeaten(r: Int) = stars[r * Reels.PER + Reels.PER - 1] > 0
    fun reelOpen(r: Int) = r == 0 || (bossBeaten(r - 1) && totalStars() >= Reels.starsNeeded(r))
    fun levelOpen(n: Int): Boolean {
        val r = n / Reels.PER
        if (!reelOpen(r)) return false
        return n % Reels.PER == 0 || stars[n - 1] > 0
    }
    /** The next level to play: the first open one without stars, else the last open. */
    fun currentLevel(): Int {
        var lastOpen = 0
        for (n in 0 until Reels.TOTAL) if (levelOpen(n)) { lastOpen = n; if (stars[n] == 0) return n }
        return lastOpen
    }

    // ------------------------------------------------------------------ sizing

    fun resize(w: Int, h: Int, density: Float) {
        wPx = max(1, w); hPx = max(1, h)
        relayout()
    }

    fun setInsets(l: Int, t: Int, r: Int, b: Int) { insL = l; insT = t; insR = r; insB = b; relayout() }

    private fun relayout() {
        L.set(wPx, hPx, insT, insB)
        val want = L.s * L.k
        if (abs(want - artScale) > 0.001f) requestArt(want)
        stageKey = ""
        posterW = 0
    }

    private fun requestArt(scale: Float) {
        artWanted = scale
        exec.execute {
            if (artWanted == scale) {
                try { val a = Art(scale); val old = artRef.getAndSet(a); old?.release() } catch (e: Throwable) { android.util.Log.e("Fizz", "art", e) }
            }
        }
    }

    private fun pollArt() {
        val a = artRef.getAndSet(null) ?: return
        if (abs(a.u - artWanted) > 0.001f) { a.release(); return }
        val old = art
        art = a; artScale = a.u
        play?.art = a
        old?.release()
    }

    fun release() {
        exec.shutdownNow()
        art?.release(); art = null
        artRef.getAndSet(null)?.release()
        stageBmp?.recycle(); stageBmp = null
        for (k in posters.indices) { posters[k]?.recycle(); posters[k] = null }
        film.release()
    }

    // ------------------------------------------------------------------ events from Play

    override fun sfx(id: Int, vol: Float, rate: Float) { if (soundOn) host.sound(id, vol, rate) }
    override fun haptic(strong: Boolean) { if (vibOn) host.haptic(strong) }

    // ------------------------------------------------------------------ flow

    private fun go(m: Int) { mode = m; modeT = 0f; overlay = O_NONE }

    private fun iris(x: Float, y: Float, action: () -> Unit) {
        if (transT >= 0f) return
        transT = 0f; transAction = action; transX = x; transY = y
    }

    private fun openOverlay(o: Int) { overlay = o; overlayT = 0f; pressed = null }

    fun onBack(): Boolean {
        if (cards.isNotEmpty()) { advanceCard(); return true }
        when {
            overlay == O_SETTINGS || overlay == O_CONFIRM -> { openOverlay(if (mode == M_PLAY && play != null) O_PAUSE else O_NONE); return true }
            overlay == O_INTRO -> { overlay = O_NONE; return true }
            mode == M_PLAY && overlay == O_NONE -> { openOverlay(O_PAUSE); return true }
            mode == M_PLAY && overlay == O_PAUSE -> { overlay = O_NONE; return true }
            mode == M_PLAY -> { toMap(); return true }
            mode == M_MAP -> { iris(500f, L.vh / 2f) { go(M_TITLE) }; return true }
        }
        return false
    }

    fun onPause() { if (mode == M_PLAY && overlay == O_NONE && play?.finished == false) openOverlay(O_PAUSE); touchCancel() }

    private fun toMap() {
        iris(500f, L.vh / 2f) {
            releasePlay()
            go(M_MAP)
            focusMap(lastLevel)
        }
    }

    private fun releasePlay() { play = null }

    private fun openIntro(n: Int) {
        level = n; lastLevel = n; host.saveInt("cur", n)
        boostSel.fill(false)
        openOverlay(O_INTRO)
        sfx(Sfx.TAP)
    }

    private fun startLevel(n: Int) {
        level = n; lastLevel = n
        host.saveInt("cur", n)
        val spec = LevelSpec(n)
        val boost = Boost()
        if (boostSel[0] && boosts[0] > 0) { boosts[0]--; boost.spyglass = true }
        if (boostSel[1] && boosts[1] > 0 && spec.limited) { boosts[1]--; boost.extraShots = 5 }
        if (boostSel[2] && boosts[2] > 0) { boosts[2]--; boost.startPower = Power.FIRECRACKER }
        saveBoosts()
        boostSel.fill(false)
        val a = art ?: return
        play = Play(spec, L, a, boost, System.nanoTime(), this)
        continues = 0
        tutorialStep = 0
        go(M_PLAY)
        sfx(Sfx.CURTAIN, 0.7f)
        queueIntroCards(n)
    }

    private fun queueIntroCards(n: Int) {
        if (!storyShown) {
            cards.add(Card("ONCE UPON A TUNE...", listOf("Baron Von Boil can't stand music.", "He bottled up every note in town", "inside a mountain of bubbles!"), IL_BARON))
            cards.add(Card("ENTER: FIZZ!", listOf("Aim and shoot. Match 3 or more", "of a colour to pop them.", "Knock a bunch loose and they all fall!"), IL_FIZZ))
            storyShown = true; host.saveInt("story", 1)
        }
        val r = n / Reels.PER
        val rseen = host.loadInt("rseen", 0)
        if (n % Reels.PER == 0 && rseen and (1 shl r) == 0) {
            host.saveInt("rseen", rseen or (1 shl r))
            cards.add(Card("REEL ${r + 1}: ${Reels.NAMES[r]}", wrap(Reels.TAGS[r], 34), IL_POSTER))
        }
        for (k in 0 until Intro.COUNT) {
            if (Intro.LEVEL[k] != n || seen and (1 shl k) != 0) continue
            seen = seen or (1 shl k)
            when (k) {
                Intro.NOTES -> cards.add(Card("FREE THE MUSIC", listOf("Some bubbles hold stolen music notes.", "Pop them or drop them", "to set the music free!"), IL_NOTE))
                Intro.APPLAUSE -> cards.add(Card("THE APPLAUSE-O-METER", listOf("Big pops and drops fill the meter.", "When it's full, tap it for a FIRECRACKER!", "Power shots never cost a shot."), IL_METER))
                Intro.BOSS -> cards.add(Card("BARON VON BOIL!", listOf("The villain himself! Hit him with bubbles", "or pop bubbles right beside him.", "Mind out: he boils over with more!"), IL_BARON))
                Intro.STONE -> cards.add(Card("STONES & BOMBS", listOf("Stones never pop: drop them or blast them.", "Pop next to a bomb, or hit it,", "and stand back... KA-BOOM!"), IL_STONEBOMB))
                Intro.RAINBOW -> cards.add(Card("THE JAZZ BUBBLE", listOf("A new power for the meter!", "It takes on any colour and pops", "every group it touches."), IL_RAINBOW))
                Intro.SPOOK -> cards.add(Card("SPOOKS!", listOf("Ghost bubbles change colour", "after every single shot.", "Time it right!"), IL_SPOOK))
                Intro.TRUMPET -> cards.add(Card("TRUMPET BLAST", listOf("The third power blows a hole", "straight through everything", "in its path!"), IL_TRUMPET))
                Intro.CAGE -> cards.add(Card("LOCKED UP!", listOf("Caged critters can't be matched.", "Pop a bubble next to the cage", "to break the bars."), IL_CAGE))
                Intro.PRESS -> cards.add(Card("BEAT THE DROP", listOf("Every few shots the scenery drops a row!", "Clear the stage before the bubbles", "reach the footlights."), IL_PRESS))
                Intro.INK -> cards.add(Card("INKY!", listOf("Ink blots spread if you leave them be.", "Pop any bubble beside a blot", "to wipe it out."), IL_INK))
                Intro.CAGE2 -> cards.add(Card("DOUBLE LOCKED", listOf("Chained cages need two pops", "beside them before they open."), IL_CAGE2))
            }
        }
        host.saveInt("seen", seen)
        cardT = 0f
    }

    private fun wrap(text: String, width: Int): List<String> {
        val out = ArrayList<String>(); var line = StringBuilder()
        for (w in text.split(" ")) {
            if (line.isNotEmpty() && line.length + 1 + w.length > width) { out.add(line.toString()); line = StringBuilder() }
            if (line.isNotEmpty()) line.append(' ')
            line.append(w)
        }
        if (line.isNotEmpty()) out.add(line.toString())
        return out
    }

    private fun advanceCard() {
        if (cards.isEmpty()) return
        if (cardT < 0.4f) return
        cards.removeAt(0); cardT = 0f
        sfx(Sfx.TAP, 0.6f)
        if (cards.isEmpty()) { pendingAfterCards?.invoke(); pendingAfterCards = null }
    }

    private fun finishLevel(p: Play) {
        val m = p.m
        val n = level
        if (m.state == Match.WON) {
            val st = m.stars()
            val firstClear = stars[n] == 0
            val oldStars = stars[n]
            newBest = m.score > best[n]
            if (st > stars[n]) { stars[n] = st; host.saveInt("st$n", st) }
            if (newBest) { best[n] = m.score; host.saveInt("sc$n", m.score) }
            var tk = 3 + 3 * max(0, st - oldStars) + (if (firstClear) 8 else 0) + m.tickets * 5 + (if (p.spec.goal == Goal.BOSS && firstClear) 20 else 0)
            tk = max(tk, 2)
            tickets += tk; saveTickets()
            resultStars = st; resultTickets = tk
            if (p.spec.goal == Goal.BOSS && firstClear && n / Reels.PER + 1 < Reels.COUNT) unlockFlash = n / Reels.PER + 1
            openOverlay(if (p.spec.goal == Goal.BOSS && n == Reels.TOTAL - 1 && firstClear) O_FINALE else O_WIN)
        } else {
            openOverlay(O_LOSE)
        }
        resultT = 0f
    }

    // ------------------------------------------------------------------ update

    fun update(dt0: Float) {
        val dt = dt0.coerceIn(0f, 0.05f)
        t += dt; modeT += dt; overlayT += dt; cardT += dt
        pollArt()
        film.update(dt)
        if (transT >= 0f) {
            val before = transT
            transT += dt / 0.9f
            if (before < 0.5f && transT >= 0.5f) { transAction?.invoke(); transAction = null }
            if (transT >= 1f) transT = -1f
        }
        val beat = host.beatPhase().let { if (it >= 0f) it else (t / Synth.BEAT) % 1f }
        when (mode) {
            M_STUDIO -> {
                if (modeT < dt * 1.5f) sfx(Sfx.PROJECTOR, 0.6f)
                if (modeT > 3.2f && art != null) iris(500f, L.vh * 0.45f) { go(M_TITLE) }
                host.setMusicState(Scene.SILENT, 0f)
            }
            M_TITLE -> { titleFizz.t = t; titleFizz.beat = beat; titleFizz.mood = 4; titleFizz.fireT += dt; host.setMusicState(Scene.MENU, 0.5f) }
            M_MAP -> { updateMap(dt); host.setMusicState(Scene.MENU, 0.4f) }
            M_PLAY -> {
                val p = play
                if (p != null) {
                    val paused = overlay == O_PAUSE || overlay == O_SETTINGS || overlay == O_CONFIRM || cards.isNotEmpty()
                    if (!paused) p.update(dt, beat)
                    else p.fx.update(0f)
                    if (p.finished && !p.resultShown) { p.resultShown = true; finishLevel(p) }
                    val inten = clamp01(p.m.applause / Match.APPLAUSE_MAX * 0.7f + min(p.m.streak, 5) * 0.06f)
                    host.setMusicState(if (p.spec.goal == Goal.BOSS && p.m.bossHp > 0) Scene.BOSS else Scene.PLAY, inten)
                    // tutorial progress
                    if (level == 0 && tutorialStep == 0 && p.m.shotsUsed > 0) tutorialStep = 1
                    if (level == 1 && tutorialStep == 0 && (p.m.shotsUsed >= 4 || p.fizz.swapT < 9f)) tutorialStep = 1
                }
            }
        }
        if (overlay == O_WIN || overlay == O_LOSE || overlay == O_FINALE) resultT += dt
    }

    private fun updateMap(dt: Float) {
        if (!mapDragging) {
            mapScroll += mapVel * dt
            mapVel *= (1f - min(1f, dt * 3.5f))
            if (abs(mapVel) < 5f) mapVel = 0f
        }
        val maxS = mapContentH() - L.vh
        if (mapScroll < 0f) { mapScroll = lerp(mapScroll, 0f, min(1f, dt * 10f)); if (!mapDragging) mapVel = 0f }
        if (mapScroll > maxS) { mapScroll = lerp(mapScroll, maxS, min(1f, dt * 10f)); if (!mapDragging) mapVel = 0f }
        if (unlockFlash >= 0) { unlockT += dt; if (unlockT > 3f) { unlockFlash = -1; unlockT = 0f } }
    }

    // ------------------------------------------------------------------ input

    private fun wx(px: Float) = px / L.s
    private fun wy(py: Float) = py / L.s

    fun touchDown(id: Int, px: Float, py: Float) {
        if (pointer != -1 && pointer != id) return
        pointer = id
        val x = wx(px); val y = wy(py)
        if (transT >= 0f) return
        if (cards.isNotEmpty()) return
        if (mode == M_STUDIO) { if (art != null) iris(500f, L.vh * 0.45f) { go(M_TITLE) }; return }
        pressed = btns.lastOrNull { it.r.contains(x, y) && it.enabled }
        if (pressed != null) return
        if (overlay != O_NONE) return
        when (mode) {
            M_MAP -> { mapDragging = true; dragLastY = y; dragStartY = y; dragMoved = false; mapVel = 0f }
            M_PLAY -> play?.let { if (!it.finished) it.touchDown(L.toBx(x), L.toBy(y)) }
        }
    }

    fun touchMove(id: Int, px: Float, py: Float) {
        if (id != pointer) return
        val x = wx(px); val y = wy(py)
        pressed?.let { if (!it.r.contains(x, y)) pressed = null; return }
        if (mode == M_MAP && mapDragging) {
            val dy = y - dragLastY
            if (abs(y - dragStartY) > 18f) dragMoved = true
            mapScroll -= dy
            mapVel = -dy / 0.016f * 0.5f + mapVel * 0.5f
            dragLastY = y
        } else if (mode == M_PLAY && overlay == O_NONE && cards.isEmpty()) play?.touchMove(L.toBx(x), L.toBy(y))
    }

    fun touchUp(id: Int, px: Float, py: Float) {
        if (id != pointer) return
        pointer = -1
        val x = wx(px); val y = wy(py)
        if (transT >= 0f) { pressed = null; play?.cancelTouch(); return }
        if (cards.isNotEmpty()) { advanceCard(); return }
        val b = pressed
        pressed = null
        if (b != null) { if (b.r.contains(x, y)) click(b); return }
        when (mode) {
            M_TITLE -> if (overlay == O_NONE) { sfx(Sfx.TAP); iris(500f, L.vh * 0.6f) { go(M_MAP); focusMap(currentLevel()) } }
            M_MAP -> {
                if (mapDragging) {
                    mapDragging = false
                    if (!dragMoved && overlay == O_NONE) tapMap(x, y)
                }
            }
            M_PLAY -> if (overlay == O_NONE) play?.touchUp(L.toBx(x), L.toBy(y))
        }
    }

    fun touchCancel() { pointer = -1; pressed = null; mapDragging = false; play?.cancelTouch() }

    private fun click(b: Btn) {
        sfx(Sfx.TAP)
        when (b.id) {
            B_PLAY -> iris(500f, L.vh * 0.6f) { go(M_MAP); focusMap(currentLevel()) }
            B_SETTINGS -> openOverlay(O_SETTINGS)
            B_BACK -> if (mode == M_MAP) iris(500f, L.vh / 2f) { go(M_TITLE) }
            B_GO -> { val n = level; overlay = O_NONE; iris(500f, L.vh * 0.5f) { startLevel(n) } }
            B_CLOSE -> overlay = if (mode == M_PLAY && (overlay == O_SETTINGS || overlay == O_CONFIRM)) O_PAUSE else O_NONE
            B_BOOST -> {
                val k = b.arg
                if (k == 1 && !(introSpec?.limited ?: LevelSpec(level).limited)) { sfx(Sfx.NOPE); return }
                if (boostSel[k]) boostSel[k] = false
                else if (boosts[k] > 0) boostSel[k] = true
                else if (tickets >= BOOST_COST[k]) { tickets -= BOOST_COST[k]; boosts[k]++; saveTickets(); saveBoosts(); boostSel[k] = true; sfx(Sfx.TICKET) }
                else sfx(Sfx.NOPE)
            }
            B_PAUSE -> if (overlay == O_NONE && play?.finished == false) openOverlay(O_PAUSE)
            B_RESUME -> overlay = O_NONE
            B_RESTART -> { val n = level; overlay = O_NONE; iris(500f, L.vh * 0.5f) { startLevel(n) } }
            B_MAP -> toMap()
            B_NEXT -> {
                val nx = (level + 1).coerceAtMost(Reels.TOTAL - 1)
                if (level + 1 < Reels.TOTAL && levelOpen(nx)) { overlay = O_NONE; iris(500f, L.vh * 0.5f) { releasePlay(); go(M_MAP); focusMap(nx); openIntro(nx) } }
                else toMap()
            }
            B_CONTINUE -> {
                val cost = CONTINUE_COST + continues * 20
                if (tickets >= cost) { tickets -= cost; saveTickets(); continues++; overlay = O_NONE; play?.continueAfterLoss(5) } else sfx(Sfx.NOPE)
            }
            B_SOUND -> { soundOn = !soundOn; saveOptions() }
            B_MUSIC -> { musicOn = !musicOn; saveOptions() }
            B_FILM -> { filmOn = !filmOn; saveOptions() }
            B_VIB -> { vibOn = !vibOn; saveOptions(); haptic(true) }
            B_CRACKLE -> { crackleOn = !crackleOn; saveOptions() }
            B_RESET -> openOverlay(O_CONFIRM)
            B_YES -> { resetProgress(); openOverlay(O_NONE); if (mode == M_PLAY) toMap() }
            B_NO -> openOverlay(O_SETTINGS)
        }
    }

    private fun resetProgress() {
        for (n in 0 until Reels.TOTAL) { stars[n] = 0; best[n] = 0; host.saveInt("st$n", 0); host.saveInt("sc$n", 0) }
        tickets = 40; saveTickets()
        for (k in 0 until 3) boosts[k] = 2
        saveBoosts()
        seen = 0; host.saveInt("seen", 0); host.saveInt("rseen", 0)
        storyShown = false; host.saveInt("story", 0)
        lastLevel = 0; host.saveInt("cur", 0)
        focusMap(0)
    }

    // ------------------------------------------------------------------ map geometry

    private fun mapContentH() = Reels.COUNT * SEC + 420f
    private fun sectionTop(r: Int) = mapContentH() - (r + 1) * SEC
    private fun nodeX(n: Int): Float { val r = n / Reels.PER; val i = n % Reels.PER; return 500f + sin(i * 0.95f + r * 1.7f) * 250f }
    private fun nodeY(n: Int): Float { val r = n / Reels.PER; val i = n % Reels.PER; return sectionTop(r) + SEC - 640f - i * 114f }

    private fun focusMap(n: Int) { mapScroll = (nodeY(n) - L.vh * 0.58f).coerceIn(0f, mapContentH() - L.vh); mapVel = 0f }

    private fun tapMap(x: Float, y: Float) {
        val cy = y + mapScroll
        for (n in 0 until Reels.TOTAL) {
            val dx = x - nodeX(n); val dy = cy - nodeY(n)
            if (dx * dx + dy * dy < 62f * 62f) {
                if (levelOpen(n)) openIntro(n)
                else { sfx(Sfx.NOPE); }
                return
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    fun draw(c: Canvas) {
        btns.clear()
        c.save()
        c.scale(L.s, L.s)
        if (filmOn && mode != M_STUDIO) c.translate(film.weaveX / L.s, film.weaveY / L.s)
        when (mode) {
            M_STUDIO -> drawStudio(c)
            M_TITLE -> drawTitle(c)
            M_MAP -> drawMap(c)
            M_PLAY -> drawPlay(c)
        }
        when (overlay) {
            O_INTRO -> drawIntro(c)
            O_PAUSE -> drawPause(c)
            O_WIN, O_FINALE -> drawWin(c)
            O_LOSE -> drawLose(c)
            O_SETTINGS -> drawSettings(c)
            O_CONFIRM -> drawConfirm(c)
        }
        if (cards.isNotEmpty()) drawCard(c)
        if (transT >= 0f) {
            val k = if (transT < 0.5f) 1f - transT / 0.5f else (transT - 0.5f) / 0.5f
            val maxR = 2600f
            Film.iris(c, 1000f, L.vh, transX, transY, maxR * easeIn(k) + (if (k > 0f) 2f else 0f))
        }
        c.restore()
        if (mode != M_STUDIO || modeT > 0.2f) film.draw(c, wPx, hPx)
    }

    private fun drawStudio(c: Canvas) {
        f.color = 0xFF0B0704.toInt(); c.drawRect(-10f, -10f, 1010f, L.vh + 10f, f)
        val k = smooth(modeT / 0.8f)
        val cy = L.vh * 0.45f
        val R = 340f
        c.save(); c.translate(500f, cy)
        UI.sunburst(c, 0f, 0f, R * k, t * 0.2f, 0xFF2A1A0E.toInt(), 0xFF3E2814.toInt(), 28)
        s.color = Pal.CREAM; s.strokeWidth = 6f; c.drawCircle(0f, 0f, R * k, s)
        s.strokeWidth = 2f; c.drawCircle(0f, 0f, R * k - 16f, s)
        if (k > 0.6f) {
            val a = smooth((k - 0.6f) / 0.4f)
            // the emblem: a seltzer bottle in a ring of stars
            for (q in 0 until 16) { val ang = q * TAU / 16f; Draw.star(p, cos(ang) * 290f, sin(ang) * 290f, 11f); f.color = alphaF(Pal.CREAM, a); c.drawPath(p, f) }
            if (a > 0.99f) {
                c.save(); c.translate(0f, -150f); c.scale(0.85f, 0.85f)
                titleFizz.t = t; titleFizz.beat = (t / Synth.BEAT) % 1f; titleFizz.mood = 0
                Toon.fizz(c, titleFizz)
                c.restore()
            }
            Draw.toon(c, "FIZZWORKS", 0f, 128f, Draw.fit("FIZZWORKS", 70f, 520f, Fonts.title), alphaF(Pal.CREAM, a), Fonts.title, 6f, 0f, 0xFF0B0704.toInt())
            Draw.text(c, "PICTURES", 0f, 196f, 30f, alphaF(Pal.CREAM, a), Fonts.deco, spacing = 0.4f)
        }
        c.restore()
        Draw.text(c, "A", 500f, cy - R - 60f, 34f, withAlpha(Pal.CREAM, (255 * smooth((modeT - 0.6f) / 0.5f)).toInt()), Fonts.deco)
        Draw.text(c, "PRESENTATION", 500f, cy + R + 60f, 30f, withAlpha(Pal.CREAM, (255 * smooth((modeT - 0.9f) / 0.5f)).toInt()), Fonts.deco, spacing = 0.3f)
        if (art == null && modeT > 2.5f) Draw.text(c, "threading the projector...", 500f, L.vh - 120f, 26f, withAlpha(Pal.CREAM, 160), Fonts.body)
    }

    private fun drawTitle(c: Canvas) {
        val beat = (t / Synth.BEAT) % 1f
        val bounce = abs(sin(beat * PI_F))
        val cy = L.vh * 0.36f
        UI.sunburst(c, 500f, cy, 2600f, t * 0.12f, 0xFFF1D9A2.toInt(), 0xFFE7A35A.toInt(), 28)
        // soft centre glow and a vignette ring
        f.alpha = 255; f.shader = android.graphics.RadialGradient(500f, cy, 900f, intArrayOf(0x50FFF8E0, 0x00FFF8E0, 0x601E0E04), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1000f, L.vh, f); f.shader = null
        val a = art
        // floating critters
        if (a != null) for (k in 0 until 14) {
            val ph = (t * (0.06f + hash01(k, 1) * 0.05f) + hash01(k, 2)) % 1f
            val x = 60f + hash01(k, 3) * 880f + sin(t * 1.3f + k) * 30f
            val y = L.vh + 80f - ph * (L.vh + 200f)
            val col = k % 6
            val sc = 0.7f + hash01(k, 4) * 0.5f
            a.body[col][((t * 7f).toInt() + k) % 3].draw(c, x, y, sc)
            a.face[col][if ((t + k) % 4f < 0.15f) Art.F_BLINK else 4].draw(c, x, y, sc)
        }
        // title
        val ty = L.iT + 250f
        Draw.bouncy(c, "FIZZ", 500f, ty, 210f, Pal.CREAM, t, Fonts.title, 0.05f, 22f)
        Draw.bouncy(c, "FOLLIES", 500f, ty + 190f, 170f, 0xFFE04A3B.toInt(), t + 0.4f, Fonts.title, 0.05f, 18f)
        // ribbon
        rf.set(170f, ty + 300f, 830f, ty + 370f)
        f.color = Pal.INK; c.drawRect(rf.left - 4f, rf.top - 4f, rf.right + 4f, rf.bottom + 4f, f)
        f.color = Pal.VELVET; c.drawRect(rf, f)
        for (side in intArrayOf(-1, 1)) {
            val x = if (side < 0) rf.left else rf.right
            p.reset(); p.moveTo(x, rf.top + 14f); p.lineTo(x + side * 70f, rf.top + 14f); p.lineTo(x + side * 46f, rf.centerY() + 7f); p.lineTo(x + side * 70f, rf.bottom + 14f); p.lineTo(x, rf.bottom + 14f); p.close()
            f.color = Pal.INK; c.drawPath(p, f)
            c.save(); c.scale(0.92f, 0.86f, x + side * 30f, rf.centerY() + 14f); f.color = Pal.VELVET_DK; c.drawPath(p, f); c.restore()
        }
        Draw.text(c, "IN GLORIOUS FIZZICOLOR", 500f, rf.centerY(), 30f, Pal.GOLD, Fonts.deco, spacing = 0.12f)
        // Fizz takes a bow
        c.save(); c.translate(500f, L.vh * 0.64f); c.scale(1.5f, 1.5f)
        Toon.fizz(c, titleFizz)
        if (a != null) { a.body[0][((t * 7f).toInt()) % 3].draw(c, 0f, -Board.SPOUT, 1f); a.face[0][Art.F_HAPPY].draw(c, 0f, -Board.SPOUT, 1f) }
        c.restore()
        // play ticket
        val by = L.vh - L.iB - 240f
        rf.set(270f, by - 70f + bounce * -6f, 730f, by + 70f + bounce * -6f)
        val pb = Btn(B_PLAY, RectF(rf), "PLAY")
        btns.add(pb)
        UI.ticket(c, rf, "PLAY", pressed?.id == B_PLAY, Pal.VELVET, Pal.CREAM, true, UI.I_PLAY, t)
        val gx = 1000f - 90f; val gy = L.iT + 80f
        btns.add(Btn(B_SETTINGS, RectF(gx - 60f, gy - 60f, gx + 60f, gy + 60f)))
        UI.round(c, gx, gy, 48f, UI.I_GEAR, pressed?.id == B_SETTINGS)
        Draw.text(c, "© MCMXXXII FIZZWORKS STUDIOS", 500f, L.vh - L.iB - 70f, 22f, withAlpha(Pal.INK, 170), Fonts.deco, spacing = 0.12f)
    }

    // ------------------------------------------------------------------ map

    private val reelTint = intArrayOf(0xFF6E8A4A.toInt(), 0xFF8A3A40.toInt(), 0xFF3E3266.toInt(), 0xFF2F6684.toInt(), 0xFF9A6A3A.toInt(),
        0xFF2A2840.toInt(), 0xFF1E1A3C.toInt(), 0xFF7A9AB8.toInt(), 0xFF3E6E3A.toInt(), 0xFF5A3420.toInt())

    private fun ensurePoster(r: Int) {
        val pw = (620f * L.s).roundToInt().coerceAtLeast(64)
        if (posterW != pw) { for (k in posters.indices) { posters[k]?.recycle(); posters[k] = null }; posterW = pw }
        if (posters[r] != null) return
        val ph = (pw * 0.56f).roundToInt()
        // paint the reel's flat for a phone-shaped stage, then crop around its horizon
        val fake = Layout(); fake.set(pw, (pw * 2.05f).roundToInt(), 0, 0)
        val full = Bitmap.createBitmap(pw, (pw * 2.05f).roundToInt(), Bitmap.Config.ARGB_8888)
        val fc = Canvas(full)
        fc.save(); fc.scale(fake.s, fake.s); Backdrop.paint(fc, fake, r); fc.restore()
        val horizonPx = (fake.Y(Board.DEAD_Y * 0.62f) * fake.s).roundToInt()
        val top = (horizonPx - ph * 0.62f).roundToInt().coerceIn(0, full.height - ph)
        val out = Bitmap.createBitmap(pw, ph, Bitmap.Config.RGB_565)
        val oc = Canvas(out)
        srcR.set(0, top, pw, top + ph)
        rf.set(0f, 0f, pw.toFloat(), ph.toFloat())
        oc.drawBitmap(full, srcR, rf, bp)
        full.recycle()
        posters[r] = out
    }

    private fun drawMap(c: Canvas) {
        f.alpha = 255; f.shader = LinearGradient(0f, 0f, 0f, L.vh, intArrayOf(0xFF2A0E12.toInt(), 0xFF1A080A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1000f, L.vh, f); f.shader = null
        val top = mapScroll; val bot = mapScroll + L.vh
        var postersMade = 0
        for (r in Reels.COUNT - 1 downTo 0) {
            val st = sectionTop(r)
            if (st > bot || st + SEC < top) continue
            val y0 = st - mapScroll
            // section wash with the reel's colour and deco ribs
            f.alpha = 255; f.shader = LinearGradient(0f, y0, 0f, y0 + SEC, intArrayOf(withAlpha(reelTint[r], 120), withAlpha(reelTint[r], 40), withAlpha(reelTint[r], 140)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, y0, 1000f, y0 + SEC, f); f.shader = null
            s.color = withAlpha(Pal.GOLD, 40); s.strokeWidth = 2f
            for (q in 0 until 9) { val x = 60f + q * 110f; c.drawLine(x, y0, x, y0 + SEC, s) }
            s.color = Pal.GOLD; s.strokeWidth = 4f; c.drawLine(0f, y0, 1000f, y0, s)
            // poster (lobby card)
            val py = y0 + SEC - 520f
            rf.set(190f, py, 810f, py + 347f)
            if (posters[r] == null && postersMade < 1) { ensurePoster(r); postersMade++ }
            Draw.decoPanel(c, RectF(rf.left - 14f, rf.top - 14f, rf.right + 14f, rf.bottom + 14f), 0xFF1A0A0C.toInt(), 14f, Pal.GOLD, 6f)
            val pb = posters[r]
            if (pb != null) c.drawBitmap(pb, null, rf, bp)
            val open = reelOpen(r)
            if (!open) { f.color = 0xB0100808.toInt(); c.drawRect(rf, f) }
            // title banner
            rf2.set(150f, py + 280f, 850f, py + 380f)
            UI.ticket(c, rf2, "", false, Pal.VELVET)
            Draw.text(c, "REEL ${r + 1}", 500f, py + 304f, 22f, Pal.GOLD, Fonts.deco, spacing = 0.3f)
            Draw.toon(c, Reels.NAMES[r], 500f, py + 342f, Draw.fit(Reels.NAMES[r], 40f, 600f, Fonts.title), Pal.CREAM, Fonts.title, 4f, 2f)
            if (!open) {
                UI.icon(c, UI.I_LOCK, 500f, py + 120f, 50f, Pal.CREAM)
                val need = Reels.starsNeeded(r)
                val msg = if (!bossBeaten(r - 1)) "Beat the Baron in Reel $r" else "Collect $need stars (you have ${totalStars()})"
                Draw.text(c, msg, 500f, py + 210f, 30f, Pal.CREAM, Fonts.body)
            }
            if (unlockFlash == r) {
                val k = unlockT
                Draw.burst(p, 500f, py + 160f, 220f + 20f * sin(k * 10f), 16, 3); f.color = withAlpha(0xFFFFE07A.toInt(), (200 * clamp01(3f - k)).toInt()); c.drawPath(p, f)
                Draw.toon(c, "NOW SHOWING!", 500f, py + 160f, 56f, alphaF(Pal.CREAM, clamp01(3f - k)), Fonts.title)
            }
        }
        // path dots between nodes
        for (n in 0 until Reels.TOTAL - 1) {
            val y0 = nodeY(n) - mapScroll; val y1 = nodeY(n + 1) - mapScroll
            if (max(y0, y1) < -80f || min(y0, y1) > L.vh + 80f) continue
            if ((n + 1) % Reels.PER == 0) continue
            val x0 = nodeX(n); val x1 = nodeX(n + 1)
            for (q in 1 until 5) { val k = q / 5f; f.color = if (stars[n] > 0) Pal.GOLD else withAlpha(Pal.CREAM, 90); c.drawCircle(lerp(x0, x1, k), lerp(y0, y1, k), 5f, f) }
        }
        val cur = currentLevel()
        for (n in 0 until Reels.TOTAL) {
            val y = nodeY(n) - mapScroll
            if (y < -100f || y > L.vh + 100f) continue
            drawNode(c, n, nodeX(n), y, cur)
        }
        // top bar
        f.color = 0xF51A080A.toInt(); c.drawRect(0f, 0f, 1000f, L.iT + 130f, f)
        s.color = Pal.GOLD; s.strokeWidth = 3f; c.drawLine(0f, L.iT + 130f, 1000f, L.iT + 130f, s)
        val by = L.iT + 66f
        btns.add(Btn(B_BACK, RectF(20f, by - 50f, 120f, by + 50f)))
        UI.round(c, 70f, by, 42f, UI.I_BACK, pressed?.id == B_BACK)
        UI.star(c, 210f, by, 30f, true)
        Draw.toon(c, "${totalStars()}", 256f, by, 44f, Pal.CREAM, Fonts.chunky, 6f, 2f, align = Paint.Align.LEFT)
        UI.icon(c, UI.I_TICKET, 520f, by, 28f, Pal.GOLD)
        Draw.toon(c, "$tickets", 566f, by, 44f, Pal.CREAM, Fonts.chunky, 6f, 2f, align = Paint.Align.LEFT)
        btns.add(Btn(B_SETTINGS, RectF(880f, by - 50f, 980f, by + 50f)))
        UI.round(c, 930f, by, 42f, UI.I_GEAR, pressed?.id == B_SETTINGS)
    }

    private fun drawNode(c: Canvas, n: Int, x: Float, y: Float, cur: Int) {
        val open = levelOpen(n)
        val boss = n % Reels.PER == Reels.PER - 1
        val r = if (boss) 60f else 46f
        val isCur = n == cur
        val pulse = if (isCur) 1f + 0.06f * sin(t * 6f) else 1f
        c.save(); c.scale(pulse, pulse, x, y)
        f.color = withAlpha(Pal.INK, 120); c.drawCircle(x + 5f, y + 7f, r + 6f, f)
        f.color = Pal.INK; c.drawCircle(x, y, r + 6f, f)
        f.color = if (open) Pal.GOLD else 0xFF6A5A50.toInt(); c.drawCircle(x, y, r + 2f, f)
        f.color = if (!open) 0xFF3A2E2A.toInt() else if (boss) Pal.VELVET else Pal.CREAM; c.drawCircle(x, y, r - 5f, f)
        if (!open) UI.icon(c, UI.I_LOCK, x, y, r * 0.45f, 0xFF8A7A70.toInt())
        else if (boss) { c.save(); c.translate(x, y + 24f); c.scale(0.36f, 0.36f); mapBaron.hat = n / Reels.PER; mapBaron.t = t; Toon.baron(c, mapBaron); c.restore() }
        else Draw.toon(c, "${n % Reels.PER + 1}", x, y + 2f, 40f, Pal.VELVET, Fonts.chunky, 0f, 0f)
        c.restore()
        if (open && stars[n] > 0) for (k in 0 until 3) UI.star(c, x - 34f + k * 34f, y + r + 14f, 13f, k < stars[n])
        if (isCur && open) {
            // Fizz hops on the current scene
            val hop = abs(sin(t * 4f)) * 20f
            c.save(); c.translate(x, y - r - 70f - hop); c.scale(0.55f, 0.55f)
            titleFizz.t = t; titleFizz.beat = (t / Synth.BEAT) % 1f; titleFizz.mood = 1
            Toon.fizz(c, titleFizz)
            c.restore()
        }
    }

    // ------------------------------------------------------------------ play

    private fun ensureStage(p: Play) {
        val key = "${p.spec.reel}:${L.wPx}x${L.hPx}:${L.k}"
        if (key == stageKey && stageBmp != null) return
        stageBmp?.recycle()
        val b = Bitmap.createBitmap(L.wPx, L.hPx, Bitmap.Config.RGB_565)
        val c = Canvas(b)
        c.save(); c.scale(L.s, L.s)
        Backdrop.paint(c, L, p.spec.reel)
        Stage.bake(c, L, p.spec.reel)
        c.restore()
        stageBmp = b; stageKey = key
    }

    private fun drawPlay(c: Canvas) {
        val p = play ?: return
        ensureStage(p)
        val sb = stageBmp
        if (sb != null) { rf.set(0f, 0f, 1000f, L.vh); c.drawBitmap(sb, null, rf, bp) }
        Backdrop.live(c, L, p.spec.reel, t, p.beat)
        Stage.footlights(c, L, p.beat, clamp01(p.m.applause / Match.APPLAUSE_MAX))
        p.draw(c)
        btns.add(Btn(B_PAUSE, RectF(p.pauseRect.left - 14f, p.pauseRect.top - 14f, p.pauseRect.right + 14f, p.pauseRect.bottom + 14f)))
        drawTutorial(c, p)
        // the show opens: curtains part at the start
        if (p.t < 1.1f) curtains(c, 1f - smooth(p.t / 1.1f))
        if ((overlay == O_LOSE) && resultT < 1.2f) curtains(c, smooth(resultT / 1.2f) * 0.55f)
        else if (overlay == O_LOSE) curtains(c, 0.55f)
    }

    private fun curtains(c: Canvas, closed: Float) {
        if (closed <= 0.001f) return
        val w = 520f * closed
        for (side in 0..1) {
            c.save()
            if (side == 1) c.scale(-1f, 1f, 500f, 0f)
            p.reset(); p.moveTo(-10f, -10f); p.lineTo(w, -10f)
            p.quadTo(w - 30f, L.vh * 0.5f, w + 10f * closed, L.vh + 10f); p.lineTo(-10f, L.vh + 10f); p.close()
            f.alpha = 255; f.shader = LinearGradient(0f, 0f, 60f, 0f, intArrayOf(0xFF4A0A10.toInt(), 0xFFB3262E.toInt(), 0xFFD0414A.toInt(), 0xFFB3262E.toInt(), 0xFF4A0A10.toInt()), floatArrayOf(0f, 0.3f, 0.5f, 0.72f, 1f), Shader.TileMode.REPEAT)
            c.drawPath(p, f); f.shader = null
            s.color = Pal.INK; s.strokeWidth = 6f; c.drawPath(p, s)
            s.color = Pal.GOLD; s.strokeWidth = 8f
            c.drawLine(w - 20f, L.vh - 60f, w - 20f, L.vh + 10f, s)
            c.restore()
        }
    }

    private fun drawTutorial(c: Canvas, p: Play) {
        if (overlay != O_NONE || cards.isNotEmpty()) return
        if (level == 0 && tutorialStep == 0 && p.t > 1.2f && !p.busy) {
            // a cartoon glove shows the drag-and-release
            val k = (p.t * 0.6f) % 1f
            val x0 = L.X(560f); val y0 = L.Y(Board.DEAD_Y - 120f)
            val x1 = L.X(360f + 80f * sin(k * PI_F)); val y1 = L.Y(Board.DEAD_Y - 330f)
            val gx = lerp(x0, x1, smooth(k * 1.4f)); val gy = lerp(y0, y1, smooth(k * 1.4f))
            hint(c, "DRAG TO AIM  •  LET GO TO FIRE!", L.Y(Board.DEAD_Y + 40f))
            gloveHand(c, gx, gy, k > 0.75f)
        }
        if (level == 1 && tutorialStep == 0 && p.t > 1.2f) {
            hint(c, "TAP FIZZ TO SWAP BUBBLES!", L.Y(Board.DEAD_Y + 40f))
            gloveHand(c, L.X(500f + 60f), L.Y(Board.PIVOT_Y + 30f) + abs(sin(t * 5f)) * -16f, false)
        }
        if (p.spec.powers != 0 && p.m.powerReady && p.m.armed == Power.NONE && level < 6 && !p.busy) {
            gloveHand(c, L.X(Play.METER_X + 70f), L.Y(Play.METER_Y + 40f) + abs(sin(t * 5f)) * -16f, false)
        }
    }

    private fun hint(c: Canvas, s0: String, y: Float) {
        val w = Draw.width(s0, 34f, Fonts.chunky) + 60f
        rf.set(500f - w / 2f, y - 34f, 500f + w / 2f, y + 34f)
        f.color = 0xD01A0A0C.toInt(); c.drawRoundRect(rf, 30f, 30f, f)
        s.color = Pal.GOLD; s.strokeWidth = 3f; c.drawRoundRect(rf, 30f, 30f, s)
        Draw.toon(c, s0, 500f, y, 34f, Pal.CREAM, Fonts.chunky, 4f, 0f)
    }

    private fun gloveHand(c: Canvas, x: Float, y: Float, press: Boolean) {
        c.save(); c.translate(x, y); c.scale(if (press) 1.6f else 1.8f, if (press) 1.6f else 1.8f); c.rotate(-20f)
        f.color = Pal.INK; rf.set(-15f, -14f, 15f, 13f); c.drawOval(rf, f)
        rf.set(-5f, -40f, 7f, -6f); c.drawRoundRect(rf, 6f, 6f, f)
        rf.set(-12f, 8f, 12f, 22f); c.drawRoundRect(rf, 5f, 5f, f)
        f.color = 0xFFFFFBF0.toInt(); rf.set(-12.5f, -11.5f, 12.5f, 10.5f); c.drawOval(rf, f)
        rf.set(-3f, -38f, 5f, -8f); c.drawRoundRect(rf, 4f, 4f, f)
        rf.set(-10f, 10f, 10f, 20f); c.drawRoundRect(rf, 4f, 4f, f)
        c.restore()
    }

    // ------------------------------------------------------------------ overlays

    private fun dim(c: Canvas, a: Int = 150) { f.color = withAlpha(0xFF0B0704.toInt(), a); c.drawRect(-20f, -20f, 1020f, L.vh + 20f, f) }

    private fun panelRect(h: Float, cy: Float = L.vh * 0.5f): RectF = RectF(70f, cy - h / 2f, 930f, cy + h / 2f)

    private fun pop(k: Float): Float = easeOutBack(clamp01(k / 0.35f))

    private var introSpec: LevelSpec? = null
    private var introNotes = 0

    private fun drawIntro(c: Canvas) {
        dim(c, 140)
        var spec = introSpec
        if (spec == null || spec.n != level) {
            spec = LevelSpec(level); introSpec = spec
            introNotes = if (spec.goal == Goal.NOTES) { val b = Board(); spec.build(b); b.count { it == Kind.NOTE } } else 0
        }
        val r = panelRect(1060f)
        val sc = pop(overlayT)
        c.save(); c.scale(sc, sc, 500f, r.centerY())
        UI.paperPanel(c, r, "SCENE ${level % Reels.PER + 1}", t)
        Draw.text(c, "REEL ${spec.reel + 1}  •  ${Reels.NAMES[spec.reel]}", 500f, r.top + 92f, Draw.fit("REEL ${spec.reel + 1}  •  ${Reels.NAMES[spec.reel]}", 26f, 760f, Fonts.deco, 0.1f), Pal.SEPIA, Fonts.deco, spacing = 0.1f)
        Draw.toon(c, Goal.TITLE[spec.goal], 500f, r.top + 175f, Draw.fit(Goal.TITLE[spec.goal], 66f, 760f, Fonts.title), Pal.VELVET, Fonts.title, 7f, 3f)
        val notes = introNotes
        val desc = when (spec.goal) {
            Goal.NOTES -> listOf("Free all $notes music notes", "in ${spec.shots} shots.")
            Goal.PRESS -> listOf("The scenery drops every ${spec.pressEvery} shots.", "Clear the stage! Par: ${spec.par}")
            Goal.BOSS -> listOf("Knock the steam out of", "Baron Von Boil! Par: ${spec.par}")
            else -> listOf("Pop and drop them all in ${spec.shots} shots.", "(The last ${Match.CURTAIN_CALL} take a bow.)")
        }
        for ((q, line) in desc.withIndex()) Draw.text(c, line, 500f, r.top + 252f + q * 46f, Draw.fit(line, 34f, 760f, Fonts.body), Pal.INK, Fonts.body)
        // the critter line-up for this scene's colours
        val a = art
        if (a != null) for (k in 0 until spec.colors) {
            val x = 500f + (k - (spec.colors - 1) / 2f) * 92f
            a.body[k][((t * 7f).toInt() + k) % 3].draw(c, x, r.top + 400f + sin(t * 5f + k) * 6f, 0.9f)
            a.face[k][4].draw(c, x, r.top + 400f + sin(t * 5f + k) * 6f, 0.9f)
        }
        // stars so far
        for (k in 0 until 3) UI.star(c, 410f + k * 90f, r.top + 510f, 32f, k < stars[level], 1f, k < stars[level])
        if (best[level] > 0) Draw.text(c, fmt("BEST %,d", best[level]), 500f, r.top + 565f, 26f, Pal.SEPIA, Fonts.chunky)
        // boosters
        Draw.text(c, "BOOSTERS", 500f, r.top + 620f, 26f, Pal.SEPIA, Fonts.deco, spacing = 0.2f)
        for (k in 0 until 3) {
            val x = 230f + k * 270f; val y = r.top + 730f
            val usable = !(k == 1 && !spec.limited)
            rf.set(x - 115f, y - 80f, x + 115f, y + 80f)
            btns.add(Btn(B_BOOST, RectF(rf), arg = k, enabled = usable))
            val sel = boostSel[k]
            f.color = Pal.INK; c.drawRoundRect(rf.left - 4f, rf.top - 4f, rf.right + 4f, rf.bottom + 4f, 20f, 20f, f)
            f.color = if (!usable) 0xFFB8A888.toInt() else if (sel) 0xFFFFE9A0.toInt() else 0xFFE8D4A8.toInt(); c.drawRoundRect(rf, 16f, 16f, f)
            if (sel) { s.color = Pal.VELVET; s.strokeWidth = 6f; c.drawRoundRect(rf, 16f, 16f, s) }
            boostIcon(c, k, x, y - 22f)
            Draw.text(c, BOOST_NAMES[k], x, y + 30f, Draw.fit(BOOST_NAMES[k], 22f, 210f, Fonts.chunky), Pal.INK, Fonts.chunky)
            val sub = if (!usable) "n/a here" else if (boosts[k] > 0) "x${boosts[k]}" else "${BOOST_COST[k]} tickets"
            Draw.text(c, sub, x, y + 58f, 20f, if (boosts[k] > 0) Pal.SEPIA else Pal.VELVET, Fonts.body)
        }
        Draw.text(c, "Tickets: $tickets", 500f, r.top + 845f, 24f, Pal.SEPIA, Fonts.body)
        rf.set(250f, r.bottom - 150f, 750f, r.bottom - 40f)
        btns.add(Btn(B_GO, RectF(rf)))
        UI.ticket(c, rf, "LET'S GO!", pressed?.id == B_GO, Pal.VELVET, Pal.CREAM, true, UI.I_PLAY, t)
        btns.add(Btn(B_CLOSE, RectF(r.right - 90f, r.top - 30f, r.right + 30f, r.top + 90f)))
        UI.round(c, r.right - 30f, r.top + 30f, 38f, UI.I_CLOSE, pressed?.id == B_CLOSE)
        c.restore()
    }

    private fun boostIcon(c: Canvas, k: Int, x: Float, y: Float) {
        val a = art ?: return
        when (k) {
            0 -> { s.color = Pal.INK; s.strokeWidth = 6f; c.drawCircle(x - 10f, y - 6f, 24f, s); c.drawLine(x + 8f, y + 12f, x + 30f, y + 34f, s)
                f.color = 0x6080C8FF; c.drawCircle(x - 10f, y - 6f, 21f, f) }
            1 -> { a.body[2][0].draw(c, x - 20f, y, 0.55f); a.face[2][4].draw(c, x - 20f, y, 0.55f); Draw.toon(c, "+5", x + 30f, y, 36f, Pal.GOLD, Fonts.chunky, 5f, 1f) }
            else -> a.firecracker.draw(c, x, y, 0.9f)
        }
    }

    private fun drawPause(c: Canvas) {
        dim(c, 150)
        val r = panelRect(760f)
        val sc = pop(overlayT)
        c.save(); c.scale(sc, sc, 500f, r.centerY())
        UI.paperPanel(c, r, "INTERMISSION", t)
        val spec = play?.spec
        if (spec != null) Draw.text(c, "Reel ${spec.reel + 1}, Scene ${spec.idx + 1}: ${Goal.TITLE[spec.goal]}", 500f, r.top + 100f, Draw.fit("Reel ${spec.reel + 1}, Scene ${spec.idx + 1}: ${Goal.TITLE[spec.goal]}", 28f, 760f, Fonts.body), Pal.SEPIA, Fonts.body)
        val labels = arrayOf("RESUME", "START OVER", "REEL MAP")
        val ids = intArrayOf(B_RESUME, B_RESTART, B_MAP)
        val icons = intArrayOf(UI.I_PLAY, UI.I_REPLAY, UI.I_MAP)
        for (k in 0 until 3) {
            rf.set(230f, r.top + 160f + k * 140f, 770f, r.top + 270f + k * 140f)
            btns.add(Btn(ids[k], RectF(rf)))
            UI.ticket(c, rf, labels[k], pressed?.id == ids[k], if (k == 0) Pal.VELVET else 0xFF7A4A26.toInt(), Pal.CREAM, true, icons[k], t)
        }
        toggles(c, r.bottom - 110f)
        c.restore()
    }

    private fun toggles(c: Canvas, y: Float) {
        val items = arrayOf(Triple(B_SOUND, UI.I_SOUND, soundOn), Triple(B_MUSIC, UI.I_MUSIC, musicOn), Triple(B_VIB, UI.I_VIB, vibOn), Triple(B_FILM, UI.I_FILM, filmOn))
        for ((k, it) in items.withIndex()) {
            val x = 260f + k * 160f
            btns.add(Btn(it.first, RectF(x - 60f, y - 60f, x + 60f, y + 60f)))
            UI.round(c, x, y, 48f, it.second, pressed?.id == it.first, Pal.VELVET, it.third)
        }
    }

    private fun drawSettings(c: Canvas) {
        dim(c, 160)
        val r = panelRect(900f)
        val sc = pop(overlayT)
        c.save(); c.scale(sc, sc, 500f, r.centerY())
        UI.paperPanel(c, r, "PROJECTION BOOTH", t)
        val rows = arrayOf(Triple(B_SOUND, "SOUND EFFECTS", soundOn), Triple(B_MUSIC, "THE BAND", musicOn), Triple(B_CRACKLE, "RECORD CRACKLE", crackleOn),
            Triple(B_FILM, "FILM GRAIN & FLICKER", filmOn), Triple(B_VIB, "VIBRATION", vibOn))
        for ((k, row) in rows.withIndex()) {
            val y = r.top + 140f + k * 110f
            Draw.text(c, row.second, 150f, y, 32f, Pal.INK, Fonts.chunky, Paint.Align.LEFT)
            rf.set(640f, y - 40f, 840f, y + 40f)
            btns.add(Btn(row.first, RectF(rf)))
            UI.ticket(c, rf, if (row.third) "ON" else "OFF", pressed?.id == row.first, if (row.third) Pal.GREEN else 0xFF7A6A5A.toInt(), Pal.CREAM, true)
        }
        rf.set(250f, r.bottom - 240f, 750f, r.bottom - 150f)
        btns.add(Btn(B_RESET, RectF(rf)))
        UI.ticket(c, rf, "RESET PROGRESS", pressed?.id == B_RESET, 0xFF5A4A44.toInt(), Pal.CREAM)
        Draw.text(c, "FIZZ FOLLIES • a Fizzworks picture for Pranvir & Dad", 500f, r.bottom - 80f, 22f, Pal.SEPIA, Fonts.body)
        btns.add(Btn(B_CLOSE, RectF(r.right - 90f, r.top - 30f, r.right + 30f, r.top + 90f)))
        UI.round(c, r.right - 30f, r.top + 30f, 38f, UI.I_CLOSE, pressed?.id == B_CLOSE)
        c.restore()
    }

    private fun drawConfirm(c: Canvas) {
        dim(c, 170)
        val r = panelRect(520f)
        UI.paperPanel(c, r, "ARE YOU SURE?", t)
        Draw.text(c, "This wipes every star, score and ticket.", 500f, r.top + 150f, 30f, Pal.INK, Fonts.body)
        Draw.text(c, "The show starts over from Reel 1.", 500f, r.top + 200f, 30f, Pal.INK, Fonts.body)
        rf.set(130f, r.bottom - 170f, 470f, r.bottom - 60f); btns.add(Btn(B_YES, RectF(rf))); UI.ticket(c, rf, "WIPE IT", pressed?.id == B_YES, 0xFF5A4A44.toInt())
        rf.set(530f, r.bottom - 170f, 870f, r.bottom - 60f); btns.add(Btn(B_NO, RectF(rf))); UI.ticket(c, rf, "KEEP IT", pressed?.id == B_NO, Pal.GREEN)
    }

    private fun drawWin(c: Canvas) {
        val p = play ?: return
        dim(c, (120 * clamp01(resultT / 0.4f)).toInt())
        val finale = overlay == O_FINALE
        val boss = p.spec.goal == Goal.BOSS
        val r = panelRect(900f)
        val sc = pop(resultT)
        c.save(); c.scale(sc, sc, 500f, r.centerY())
        UI.paperPanel(c, r, if (finale) "THE GRAND FINALE!" else if (boss) "THE END" else "BRAVO!", t)
        if (boss) Draw.text(c, if (finale) "...of Baron Von Boil's bubble racket!" else "...of Reel ${p.spec.reel + 1}: ${Reels.NAMES[p.spec.reel]}", 500f, r.top + 100f, Draw.fit("...of Reel ${p.spec.reel + 1}: ${Reels.NAMES[p.spec.reel]}", 30f, 760f, Fonts.deco), Pal.SEPIA, Fonts.deco)
        else Draw.text(c, "Reel ${p.spec.reel + 1}  •  Scene ${p.spec.idx + 1}", 500f, r.top + 100f, 30f, Pal.SEPIA, Fonts.deco)
        // stars stamp in one by one
        for (k in 0 until 3) {
            val at = 0.5f + k * 0.38f
            val got = k < resultStars
            val kk = if (got) clamp01((resultT - at) / 0.3f) else 1f
            if (got && resultT >= at && resultT - 0.016f * 2 < at) sfx(Sfx.STAR, 0.9f, 1f + k * 0.12f)
            val scl = if (got) (if (kk < 1f) 2.2f - 1.2f * easeOutBack(kk) else 1f) else 1f
            val y = r.top + 245f - (if (k == 1) 26f else 0f)
            if (!got || resultT >= at) UI.star(c, 330f + k * 170f, y, 70f, got, scl, got)
        }
        // score counting up
        val k = clamp01((resultT - 0.3f) / 1.2f)
        val shown = (p.m.score * smooth(k)).toInt()
        Draw.toon(c, fmt("%,d", shown), 500f, r.top + 410f, 84f, Pal.GOLD, Fonts.chunky, 10f, 4f)
        if (p.encoreBonus > 0) Draw.text(c, fmt("includes encore bonus +%,d", p.encoreBonus), 500f, r.top + 478f, 26f, Pal.SEPIA, Fonts.body)
        if (newBest && resultT > 1.6f) {
            c.save(); c.rotate(-12f, 790f, r.top + 400f)
            rf.set(700f, r.top + 370f, 880f, r.top + 430f); f.color = Pal.VELVET; c.drawRoundRect(rf, 10f, 10f, f)
            Draw.text(c, "NEW BEST!", 790f, r.top + 400f, 30f, Pal.CREAM, Fonts.chunky)
            c.restore()
        }
        UI.icon(c, UI.I_TICKET, 440f, r.top + 545f, 26f, Pal.GOLD)
        Draw.toon(c, "+$resultTickets", 520f, r.top + 545f, 44f, Pal.CREAM, Fonts.chunky, 6f, 2f)
        if (finale) {
            Draw.text(c, "Starring FIZZ as Himself", 500f, r.top + 610f, 28f, Pal.INK, Fonts.body)
            Draw.text(c, "and BARON VON BOIL as the Steam", 500f, r.top + 650f, 28f, Pal.INK, Fonts.body)
        }
        if (resultT > 1.2f) {
            val nextOpen = level + 1 < Reels.TOTAL && levelOpen(level + 1)
            rf.set(110f, r.bottom - 170f, 380f, r.bottom - 60f); btns.add(Btn(B_MAP, RectF(rf))); UI.ticket(c, rf, "", pressed?.id == B_MAP, 0xFF7A4A26.toInt(), Pal.CREAM, true, UI.I_MAP)
            rf.set(400f, r.bottom - 170f, 600f, r.bottom - 60f); btns.add(Btn(B_RESTART, RectF(rf))); UI.ticket(c, rf, "", pressed?.id == B_RESTART, 0xFF7A4A26.toInt(), Pal.CREAM, true, UI.I_REPLAY)
            rf.set(620f, r.bottom - 170f, 890f, r.bottom - 60f); btns.add(Btn(if (nextOpen) B_NEXT else B_MAP, RectF(rf)))
            UI.ticket(c, rf, if (nextOpen) "NEXT" else "MAP", pressed?.id == B_NEXT, Pal.VELVET, Pal.CREAM, true, if (nextOpen) UI.I_NEXT else UI.I_MAP)
        }
        c.restore()
        if (resultT < 0.1f && finale) p.fx.confetti(0f, 1000f, 0f, 120)
    }

    private fun drawLose(c: Canvas) {
        val p = play ?: return
        if (resultT < 0.9f) return
        val r = panelRect(760f)
        val sc = pop(resultT - 0.9f)
        c.save(); c.scale(sc, sc, 500f, r.centerY())
        UI.paperPanel(c, r, "CURTAINS!", t)
        val why = if (p.m.lostReason == Match.LOST_OVERFLOW) "The bubbles reached the footlights!" else "Fizz ran out of seltzer!"
        Draw.text(c, why, 500f, r.top + 110f, Draw.fit(why, 34f, 760f, Fonts.body), Pal.INK, Fonts.body)
        val m = p.m
        val prog = when (p.spec.goal) {
            Goal.NOTES -> "Notes freed: ${m.notesFreed} of ${m.notesTotal}"
            Goal.BOSS -> "The Baron had ${m.bossHp} steam left"
            else -> "Only ${max(0, m.b.coloredCount() - Match.CURTAIN_CALL)} left to go!"
        }
        Draw.text(c, prog, 500f, r.top + 165f, 30f, Pal.SEPIA, Fonts.body)
        val cost = CONTINUE_COST + continues * 20
        val can = tickets >= cost
        rf.set(170f, r.top + 230f, 830f, r.top + 360f)
        btns.add(Btn(B_CONTINUE, RectF(rf), enabled = can))
        val lbl = if (m.lostReason == Match.LOST_OVERFLOW) "ENCORE: SWEEP" else "ENCORE: +5 SHOTS"
        UI.ticket(c, rf, lbl, pressed?.id == B_CONTINUE, Pal.GREEN, Pal.CREAM, can)
        UI.icon(c, UI.I_TICKET, 440f, r.top + 400f, 22f, Pal.GOLD)
        Draw.text(c, "$cost of your $tickets tickets", 560f, r.top + 400f, 26f, Pal.SEPIA, Fonts.body)
        rf.set(170f, r.bottom - 190f, 480f, r.bottom - 70f); btns.add(Btn(B_MAP, RectF(rf))); UI.ticket(c, rf, "MAP", pressed?.id == B_MAP, 0xFF7A4A26.toInt(), Pal.CREAM, true, UI.I_MAP)
        rf.set(520f, r.bottom - 190f, 830f, r.bottom - 70f); btns.add(Btn(B_RESTART, RectF(rf))); UI.ticket(c, rf, "RETRY", pressed?.id == B_RESTART, Pal.VELVET, Pal.CREAM, true, UI.I_REPLAY)
        c.restore()
    }

    private fun drawCard(c: Canvas) {
        val card = cards[0]
        val k = clamp01(cardT / 0.35f)
        f.color = withAlpha(0xFF000000.toInt(), (200 * k).toInt()); c.drawRect(-20f, -20f, 1020f, L.vh + 20f, f)
        val r = RectF(40f, L.vh * 0.5f - 560f, 960f, L.vh * 0.5f + 560f)
        c.save(); c.scale(lerp(0.9f, 1f, k), lerp(0.9f, 1f, k), 500f, L.vh * 0.5f)
        UI.intertitle(c, r, card.title, card.lines, t)
        illustrate(c, card.illus, 500f, r.bottom - 300f)
        if (cardT > 0.6f) Draw.text(c, "— tap to continue —", 500f, r.bottom - 80f, 26f, withAlpha(Pal.CREAM, (150 + 100 * sin(t * 4f)).toInt()), Fonts.deco, spacing = 0.12f)
        c.restore()
    }

    private fun illustrate(c: Canvas, il: Int, x: Float, y: Float) {
        val a = art ?: return
        val b = ((t * 7f).toInt()) % 3
        fun crit(col: Int, xx: Float, yy: Float, sc: Float = 1.2f, face: Int = 4) { a.body[col][b].draw(c, xx, yy, sc); a.face[col][face].draw(c, xx, yy, sc) }
        when (il) {
            IL_FIZZ -> { c.save(); c.translate(x, y + 40f); Toon.fizz(c, titleFizz); c.restore(); crit(0, x, y - Board.SPOUT * 1f + 40f) ; crit(2, x - 220f, y - 30f); crit(2, x - 140f, y - 60f); crit(2, x + 220f, y - 30f) }
            IL_NOTE -> { for (k in 0 until 3) { a.body[k][b].draw(c, x - 160f + k * 160f, y, 1.3f); a.note.draw(c, x - 160f + k * 160f, y + sin(t * 4f + k) * 5f, 1.3f) } }
            IL_METER -> { Draw.text(c, "APPLAUSE", x, y - 90f, 30f, Pal.GOLD, Fonts.deco); a.firecracker.draw(c, x - 120f, y + 10f, 1.6f); a.rainbow[b].draw(c, x + 120f, y + 10f, 1.1f) }
            IL_BARON -> { c.save(); c.translate(x, y + 20f); c.scale(0.9f, 0.9f); Toon.baron(c, Toon.BaronState().also { it.t = t; it.hat = (level / Reels.PER).coerceIn(0, 9); it.beat = (t / Synth.BEAT) % 1f }); c.restore() }
            IL_STONEBOMB -> { a.stone[b].draw(c, x - 110f, y, 1.5f); a.bomb[b].draw(c, x + 110f, y, 1.5f) }
            IL_RAINBOW -> { c.save(); c.translate(x, y); c.rotate(t * 60f); a.rainbow[b].draw(c, 0f, 0f, 1.7f); c.restore() }
            IL_SPOOK -> { for (k in 0 until 3) a.spook[(k + (t * 1.5f).toInt()) % 6][b].draw(c, x - 170f + k * 170f, y + sin(t * 3f + k) * 8f, 1.4f) }
            IL_TRUMPET -> { c.save(); c.translate(x, y); c.rotate(sin(t * 3f) * 8f); a.trumpet.draw(c, 0f, 0f, 2.4f); c.restore() }
            IL_CAGE -> { crit(0, x, y, 1.6f); a.cage1.draw(c, x, y, 1.6f) }
            IL_CAGE2 -> { crit(3, x, y, 1.6f); a.cage2.draw(c, x, y, 1.6f) }
            IL_INK -> { a.ink[b].draw(c, x - 90f, y, 1.5f); crit(1, x + 90f, y, 1.5f, Art.F_SCARED) }
            IL_POSTER -> {
                val r = (level / Reels.PER).coerceIn(0, Reels.COUNT - 1)
                ensurePoster(r)
                val pb = posters[r]
                rf.set(x - 300f, y - 175f, x + 300f, y + 160f)
                s.color = Pal.CREAM; s.strokeWidth = 5f; c.drawRect(rf.left - 8f, rf.top - 8f, rf.right + 8f, rf.bottom + 8f, s)
                if (pb != null) c.drawBitmap(pb, null, rf, bp)
            }
            IL_PRESS -> { f.color = 0xFF8A5A33.toInt(); c.drawRect(x - 260f, y - 120f + sin(t * 2f) * 20f, x + 260f, y - 90f + sin(t * 2f) * 20f, f)
                for (k in 0 until 5) crit(k % 6, x - 200f + k * 100f, y - 40f + sin(t * 2f) * 20f, 1.1f) }
        }
    }
}
