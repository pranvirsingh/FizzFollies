import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class FakeHost : Host {
    val ints = HashMap<String, Int>()
    var sounds = 0
    var scene = -2
    override fun loadInt(key: String, def: Int) = ints[key] ?: def
    override fun saveInt(key: String, v: Int) { ints[key] = v }
    override fun sound(id: Int, vol: Float, rate: Float) { check(id in 0 until Sfx.COUNT); check(!vol.isNaN() && !rate.isNaN()); sounds++ }
    override fun setAudio(sound: Boolean, music: Boolean, crackle: Boolean) {}
    override fun setMusicState(scene: Int, intensity: Float) { this.scene = scene; check(!intensity.isNaN()) }
    override fun beatPhase(): Float = -1f
    override fun haptic(strong: Boolean) {}
}

object UiShots {
    const val W = 540; const val H = 1170
    var shot = 0
    fun snap(g: Game, name: String) {
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        g.draw(c)
        check(c.saveCount == 1) { "save leak in $name" }
        ImageIO.write(img, "png", File("/home/claude/fizz/shots/ui_%02d_%s.png".format(++shot, name)))
    }
    fun run(g: Game, sec: Float) { var t = 0f; while (t < sec) { g.update(1f / 60f); t += 1f / 60f } }
    fun waitArt(g: Game) { var k = 0; while (!g.ready && k++ < 2000) { Thread.sleep(10); g.update(0.001f) }; check(g.ready) { "art never arrived" } }
    fun tap(g: Game, x: Float, y: Float) { g.touchDown(0, x, y); g.update(0.016f); g.touchUp(0, x, y); g.update(0.016f) }

    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val host = FakeHost()
        val g = Game(host)
        g.resize(W, H, 1f); g.setInsets(0, 24, 0, 16)
        run(g, 1.2f); snap(g, "studio")
        waitArt(g)
        run(g, 3.0f); run(g, 1.0f); snap(g, "title")
        g.debugMap(0); run(g, 0.5f); snap(g, "map")
        g.debugIntro(4); run(g, 0.2f); snap(g, "intro")
        g.debugCards(2); run(g, 0.8f); snap(g, "card")
        for (n in listOf(0, 13, 26, 38, 47, 59)) {
            g.debugStart(n); run(g, 1.6f)
            val p = g.play!!
            // fire a few shots with the bot
            for (k in 0 until 3) {
                val pick = Bot.choose(p.m)
                if (pick.swap) p.swap()
                val bx = Board.LAUNCH_X + kotlin.math.cos(pick.ang) * 600f; val by = Board.PIVOT_Y - kotlin.math.sin(pick.ang) * 600f
                p.touchDown(bx, by); p.touchMove(bx, by); p.touchUp(bx, by)
                run(g, 0.25f)
                if (k == 1) snap(g, "play_${n}_mid")
                run(g, 1.6f)
            }
            // and aim
            p.touchDown(380f, 600f); p.touchMove(380f, 600f); run(g, 0.3f)
            snap(g, "play_${n}_aim")
            p.cancelTouch()
        }
        g.debugOverlay(Game.O_PAUSE); run(g, 0.5f); snap(g, "pause")
        g.debugOverlay(Game.O_SETTINGS); run(g, 0.5f); snap(g, "settings")
        // win by autopilot on level 0
        g.debugStart(0); run(g, 1.3f)
        var guard = 0
        while (g.play!!.m.state == Match.PLAYING && guard++ < 60) {
            val p = g.play!!
            if (!p.busy) { val pick = Bot.choose(p.m); if (pick.swap) p.swap(); if (pick.power) p.tapMeter()
                val bx = Board.LAUNCH_X + kotlin.math.cos(pick.ang) * 600f; val by = Board.PIVOT_Y - kotlin.math.sin(pick.ang) * 600f
                p.touchDown(bx, by); p.touchUp(bx, by) }
            run(g, 0.3f)
        }
        run(g, 1.0f); snap(g, "encore")
        run(g, 6f); snap(g, "win")
        // lose: level with a shot budget, waste shots straight up
        g.debugStart(30); run(g, 1.3f)
        guard = 0
        while (g.play!!.m.state == Match.PLAYING && guard++ < 200) { val p = g.play!!; if (!p.busy) { p.touchDown(30f, 900f); p.touchUp(30f, 900f) }; run(g, 0.2f) }
        run(g, 4f); snap(g, "lose")
        println("UI OK sounds=${host.sounds}")
    }
}
