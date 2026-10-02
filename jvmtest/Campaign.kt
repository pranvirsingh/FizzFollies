import android.graphics.Canvas
import com.pranvir.fizz.*
import java.awt.image.BufferedImage

object Campaign {
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val skill = if (a.isNotEmpty()) a[0].toFloat() else 0.6f
        val maxFlights = if (a.size > 1) a[1].toInt() else 400
        val host = FakeHost()
        val g = Game(host)
        g.resize(360, 780, 1f); g.setInsets(0, 24, 0, 16)
        UiShots.waitArt(g)
        val img = BufferedImage(360, 780, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        fun frame(n: Int = 1) { repeat(n) { g.update(1f / 30f); g.draw(c) } }
        val rng = Rng(99L)
        var flights = 0; var wins = 0; var losses = 0; var continues = 0
        val t0 = System.currentTimeMillis()
        g.debugMap(0); frame(30)
        while (flights < maxFlights) {
            val n = g.currentLevel()
            if (g.mode != Game.M_MAP) { g.debugMap(n); frame(10) }
            g.debugIntro(n); frame(2)
            check(g.debugTap(Game.B_GO)) { "no GO button" }
            frame(40)
            g.debugSkipCards(); frame(40)
            val p = g.play ?: error("no play")
            var guard = 0
            while (g.overlay != Game.O_WIN && g.overlay != Game.O_LOSE && g.overlay != Game.O_FINALE && guard++ < 4000) {
                if (g.cardCount > 0) g.debugSkipCards()
                if (!p.busy && p.m.state == Match.PLAYING && g.overlay == Game.O_NONE) {
                    val pick = Bot.choose(p.m, skill, rng)
                    if (pick.swap) p.swap(); if (pick.power) p.tapMeter()
                    val bx = Board.LAUNCH_X + kotlin.math.cos(pick.ang) * 600f; val by = Board.PIVOT_Y - kotlin.math.sin(pick.ang) * 600f
                    p.touchDown(bx, by); p.touchUp(bx, by)
                }
                frame(3)
            }
            flights++
            frame(60)
            if (g.overlay == Game.O_WIN || g.overlay == Game.O_FINALE) {
                wins++
                println("flight $flights: L${n + 1} ${Goal.TITLE[p.spec.goal]} WON stars=${p.m.stars()} score=${p.m.score} shots=${p.m.shotsUsed} total*=${g.totalStars()}")
                if (n == Reels.TOTAL - 1) break
                g.debugTap(Game.B_MAP); frame(40)
            } else if (g.overlay == Game.O_LOSE) {
                losses++
                println("flight $flights: L${n + 1} ${Goal.TITLE[p.spec.goal]} lost (${if (p.m.lostReason == Match.LOST_OVERFLOW) "overflow" else "shots"})")
                g.debugTap(Game.B_MAP); frame(40)
            } else error("stuck on L$n overlay=${g.overlay}")
            // stuck behind a star gate? replay the weakest level of the open reels
        }
        println("CAMPAIGN ${if (g.currentLevel() == Reels.TOTAL - 1 && wins > 0) "DONE" else "PARTIAL"} flights=$flights wins=$wins losses=$losses stars=${g.totalStars()} cur=${g.currentLevel() + 1} in ${(System.currentTimeMillis() - t0) / 1000}s")
    }
}
