import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage

object Dbg2 {
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val host = FakeHost()
        val g = Game(host)
        g.resize(540, 1170, 1f); g.setInsets(0, 24, 0, 16)
        UiShots.waitArt(g)
        g.filmOn = false
        val mode = a[0]
        if (mode.contains("m")) { g.debugMap(0); UiShots.run(g, 0.5f); val img = BufferedImage(540, 1170, BufferedImage.TYPE_INT_ARGB); g.draw(Canvas(img)); g.draw(Canvas(img)) }
        if (mode.contains("i")) { g.debugIntro(4); UiShots.run(g, 0.2f); val img = BufferedImage(540, 1170, BufferedImage.TYPE_INT_ARGB); g.draw(Canvas(img)) }
        if (mode.contains("c")) { g.debugCards(2); UiShots.run(g, 0.8f); val img = BufferedImage(540, 1170, BufferedImage.TYPE_INT_ARGB); g.draw(Canvas(img)) }
        g.debugStart(0); UiShots.run(g, 2f)
        UiShots.snap(g, "dbg_$mode")
    }
}
