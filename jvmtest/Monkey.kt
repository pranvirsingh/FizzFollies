import android.graphics.Canvas
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import java.util.Random

object Monkey {
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val steps = if (a.isNotEmpty()) a[0].toInt() else 30000
        val rnd = Random(if (a.size > 1) a[1].toLong() else 42L)
        val host = FakeHost()
        val W = 360; val H = 780
        var g = Game(host)
        g.resize(W, H, 1f); g.setInsets(0, 24, 0, 16)
        UiShots.waitArt(g)
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        var down = false; var px = 0f; var py = 0f
        var frames = 0; var restores = 0; var plays = 0; var wins = 0; var losses = 0
        val modes = IntArray(4)
        var lastPlay: Play? = null
        for (i in 0 until steps) {
            val r = rnd.nextFloat()
            when {
                r < 0.04f && !down -> { px = rnd.nextFloat() * W; py = rnd.nextFloat() * H; g.touchDown(0, px, py); down = true }
                r < 0.09f && down -> { g.touchUp(0, px, py); down = false }
                r < 0.2f && down -> { px += (rnd.nextFloat() - 0.5f) * 80f; py += (rnd.nextFloat() - 0.5f) * 80f; g.touchMove(0, px, py) }
                r < 0.202f -> { g.touchCancel(); down = false }
                r < 0.204f -> g.onBack()
                r < 0.2045f -> g.onPause()
                r < 0.2047f -> { g.onPause(); g.release(); g = Game(host); g.resize(W, H, 1f); g.setInsets(0, 24, 0, 16); UiShots.waitArt(g); down = false; restores++ }
                r < 0.2049f -> g.resize(W, H, 1f)
            }
            if (rnd.nextFloat() < 0.012f && !down) {
                val tg = g.tapTargets()
                if (tg.isNotEmpty()) { val q = tg[rnd.nextInt(tg.size)]; g.touchDown(0, q[0], q[1]); g.update(0.016f); g.touchUp(0, q[0], q[1]) }
            }
            // let the bot play stretches of levels so we see wins, losses and every overlay
            val p = g.play
            if (p != null && p !== lastPlay) { plays++; lastPlay = p }
            if (p != null && g.overlay == Game.O_NONE && g.cardCount == 0 && !p.busy && p.m.state == Match.PLAYING && (i / 3000) % 2 == 1 && rnd.nextFloat() < 0.1f) {
                val pick = Bot.choose(p.m, 0.6f, Rng(i.toLong()))
                if (pick.swap) p.swap(); if (pick.power) p.tapMeter()
                val bx = Board.LAUNCH_X + kotlin.math.cos(pick.ang) * 600f; val by = Board.PIVOT_Y - kotlin.math.sin(pick.ang) * 600f
                p.touchDown(bx, by); p.touchUp(bx, by)
            }
            val dt = if (rnd.nextFloat() < 0.01f) rnd.nextFloat() * 0.3f else 1f / 60f
            g.update(dt)
            if (i % 7 == 0) { g.draw(c); check(c.saveCount == 1) { "save leak in mode ${g.mode}" }; frames++ }
            modes[g.mode]++
            if (g.overlay == Game.O_WIN) wins++
            if (g.overlay == Game.O_LOSE) losses++
            val pp = g.play
            if (pp != null) {
                val m = pp.m
                check(m.score >= 0); check(m.applause in 0f..Match.APPLAUSE_MAX)
                check(m.shotsLeft >= -1)
                check(pp.fx.count <= Fx.MAX)
            }
        }
        println("MONKEY OK steps=$steps frames=$frames restores=$restores plays=$plays winFrames=$wins loseFrames=$losses modes=${modes.toList()} sounds=${host.sounds} stars=${g.totalStars()}")
    }
}
