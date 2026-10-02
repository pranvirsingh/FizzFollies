import com.pranvir.fizz.*
object MapShot {
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val host = FakeHost()
        for (n in 0 until 30) host.ints["st$n"] = 1 + n % 3
        val g = Game(host); g.resize(540, 1170, 1f); g.setInsets(0, 24, 0, 16); UiShots.waitArt(g)
        g.debugMap(26); for (k in 0 until 12) { g.update(0.05f); UiShots.snap(g, "tmp") }
        UiShots.snap(g, "map_progress")
        g.debugMap(11); for (k in 0 until 6) { g.update(0.05f); UiShots.snap(g, "tmp") }
        UiShots.snap(g, "map_boss")
    }
}
