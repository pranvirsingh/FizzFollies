import android.graphics.*
import com.pranvir.fizz.*

object Dbg {
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val host = FakeHost()
        val g = Game(host)
        g.resize(540, 1170, 1f); g.setInsets(0, 24, 0, 16)
        UiShots.waitArt(g)
        g.filmOn = false
        g.debugStart(0); UiShots.run(g, 2f)
        val p = g.play!!
        for (k in 0 until 2) {
            val pick = Bot.choose(p.m)
            val bx = Board.LAUNCH_X + kotlin.math.cos(pick.ang) * 600f; val by = Board.PIVOT_Y - kotlin.math.sin(pick.ang) * 600f
            p.touchDown(bx, by); p.touchUp(bx, by)
            for (q in 0 until (if (k == 1) 3 else 12)) { UiShots.run(g, 0.1f); val types = p.fx.parts.filter { it.alive }.groupBy { it.type }.mapValues { it.value.size }; println("k$k t${q}: $types phase ${p.phase}") }
        }
        UiShots.snap(g, "dbg1")
        g.filmOn = true
        UiShots.snap(g, "dbg2")
    }
}
