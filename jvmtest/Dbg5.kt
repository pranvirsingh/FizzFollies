import com.pranvir.fizz.*
object Dbg5 {
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val g = Game(FakeHost()); g.resize(540, 1170, 1f); g.setInsets(0, 24, 0, 16); UiShots.waitArt(g)
        g.debugStart(38); UiShots.run(g, 1.6f)
        val p = g.play!!
        for (k in 0 until 3) {
            val pick = Bot.choose(p.m)
            if (pick.swap) p.swap()
            val bx = Board.LAUNCH_X + kotlin.math.cos(pick.ang) * 600f; val by = Board.PIVOT_Y - kotlin.math.sin(pick.ang) * 600f
            p.touchDown(bx, by); p.touchMove(bx, by); p.touchUp(bx, by)
            UiShots.run(g, 1.85f)
            println("k$k mood=${p.fizz.mood} blink=${p.fizz.blink} state=${p.m.state} phase=${p.phase} fireT=${p.fizz.fireT} overlay=${g.overlay} cards=${g.cardCount}")
        }
    }
}
