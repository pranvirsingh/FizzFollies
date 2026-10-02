import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object SceneShot {
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val W = 540; val H = 1170
        val reels = if (a.isNotEmpty()) a[0].split(",").map { it.toInt() } else (0 until 10).toList()
        for (reel in reels) {
            val L = Layout(); L.set(W, H, 40, 30)
            val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
            val c = Canvas(img)
            c.save(); c.scale(L.s, L.s)
            Backdrop.paint(c, L, reel)
            Stage.bake(c, L, reel)
            Backdrop.live(c, L, reel, 1.3f, 0.3f)
            val spec = LevelSpec(reel * 12 + 4)
            val m = Match(spec, 5L)
            val art = Art(L.s * L.k)
            Stage.footlights(c, L, 0.3f, 0.5f)
            Stage.deadLine(c, L, 0, 0f, 0f)
            c.save(); c.translate(L.bx, L.by); c.scale(L.k, L.k)
            for (i in 0 until Board.N) {
                val k = m.b.kind[i]; if (k == Kind.EMPTY) continue
                val x = Board.cxi(i); val y = m.b.cyi(i); val col = m.b.col[i]
                when (k) {
                    Kind.STONE -> art.stone[i % 3].draw(c, x, y)
                    Kind.BOMB -> art.bomb[i % 3].draw(c, x, y)
                    Kind.INK -> art.ink[i % 3].draw(c, x, y)
                    Kind.SPOOK -> art.spook[col][i % 3].draw(c, x, y)
                    else -> { art.body[col][i % 3].draw(c, x, y); if (k == Kind.NOTE) art.note.draw(c, x, y) else art.face[col][4].draw(c, x, y)
                        if (k == Kind.CAGE) (if (m.b.hp[i] > 1) art.cage2 else art.cage1).draw(c, x, y); if (k == Kind.TICKET) art.ticket.draw(c, x, y) }
                }
            }
            val st = Toon.FizzState(); st.ang = 1.3f; st.gazeX = 0.3f; st.gazeY = -1f
            c.save(); c.translate(Board.LAUNCH_X, Board.PIVOT_Y); Toon.fizz(c, st)
            val bx = kotlin.math.cos(st.ang) * Board.SPOUT; val by = -kotlin.math.sin(st.ang) * Board.SPOUT
            art.body[m.cur][0].draw(c, bx, by); art.face[m.cur][4].draw(c, bx, by)
            art.body[m.next][0].draw(c, Toon.gloveX(st), Toon.gloveY(st), 0.72f); art.face[m.next][4].draw(c, Toon.gloveX(st), Toon.gloveY(st), 0.72f)
            c.restore()
            c.restore()
            c.restore()
            val film = Film(); film.update(0.5f); film.draw(c, W, H)
            ImageIO.write(img, "png", File("/home/claude/fizz/shots/scene_$reel.png"))
        }
        println("SCENES OK")
    }
}
