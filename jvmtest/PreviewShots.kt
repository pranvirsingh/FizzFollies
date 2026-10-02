import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object PreviewShots {
    const val W = 720; const val H = 1560
    fun snap(g: Game, name: String) {
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img); g.draw(c)
        ImageIO.write(img, "png", File("/home/claude/fizz/shots/pv_$name.png"))
    }
    fun botShots(g: Game, n: Int, skill: Float = 1f) {
        val p = g.play!!
        var fired = 0; var guard = 0
        while (fired < n && guard++ < 2000 && p.m.state == Match.PLAYING) {
            if (!p.busy) {
                val pick = Bot.choose(p.m, skill, Rng(fired.toLong()))
                if (pick.swap) p.swap(); if (pick.power) p.tapMeter()
                val bx = Board.LAUNCH_X + kotlin.math.cos(pick.ang) * 600f; val by = Board.PIVOT_Y - kotlin.math.sin(pick.ang) * 600f
                p.touchDown(bx, by); p.touchUp(bx, by); fired++
            }
            UiShots.run(g, 0.05f)
        }
    }
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val host = FakeHost()
        for (n in 0 until 40) host.ints["st$n"] = if (n % 4 == 0) 2 else 3
        host.ints["story"] = 1; host.ints["seen"] = -1; host.ints["rseen"] = -1
        val g = Game(host); g.resize(W, H, 1f); g.setInsets(0, 30, 0, 20); UiShots.waitArt(g)
        UiShots.run(g, 4.2f); UiShots.run(g, 1.3f); snap(g, "1_title")
        g.debugMap(40); for (k in 0 until 14) { g.update(0.05f); snap(g, "tmp") }; snap(g, "2_map")
        g.debugIntro(40); UiShots.run(g, 0.3f); snap(g, "3_intro")
        // spooky notes level mid-action
        g.debugStart(28); UiShots.run(g, 2.8f); botShots(g, 4); UiShots.run(g, 0.12f); snap(g, "4_spooky")
        // circus with an aimed shot
        g.debugStart(16); UiShots.run(g, 2.8f); botShots(g, 2); UiShots.run(g, 1.5f)
        val p = g.play!!; p.touchDown(330f, 520f); p.touchMove(330f, 520f); UiShots.run(g, 0.2f); snap(g, "5_aim"); p.cancelTouch()
        // the Baron
        g.debugStart(59); UiShots.run(g, 2.8f); botShots(g, 9); UiShots.run(g, 0.15f); snap(g, "6_boss")
        // city
        g.debugStart(66); UiShots.run(g, 2.8f); botShots(g, 3); UiShots.run(g, 0.1f); snap(g, "7_city")
        // boilerworks
        g.debugStart(110); UiShots.run(g, 2.8f); botShots(g, 3); UiShots.run(g, 0.1f); snap(g, "8_boiler")
        // win
        g.debugStart(1); UiShots.run(g, 2.8f); botShots(g, 60); UiShots.run(g, 9f); snap(g, "9_win")
        File("/home/claude/fizz/shots/pv_tmp.png").delete()
        println("PREVIEW OK")
    }
}
