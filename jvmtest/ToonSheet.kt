import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ToonSheet {
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val u = 1.2f
        val art = Art(u)
        val img = BufferedImage((2000 * u).toInt(), (1300 * u).toInt(), BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        c.drawColor(0xFFD9C7A0.toInt())
        c.save(); c.scale(u, u)
        for (k in 0 until 5) {
            val st = Toon.FizzState()
            st.mood = intArrayOf(0, 1, 2, 3, 0)[k]; st.moodT = 1f
            st.ang = floatArrayOf(1.57f, 0.9f, 2.3f, 1.4f, 0.5f)[k]
            st.gazeX = kotlin.math.cos(st.ang); st.gazeY = -kotlin.math.sin(st.ang)
            st.t = k * 0.7f; st.beat = k * 0.2f
            if (k == 4) st.fireT = 0.1f
            c.save(); c.translate(200f + k * 340f, 260f)
            Toon.fizz(c, st)
            val bx = kotlin.math.cos(st.ang) * Board.SPOUT; val by = -kotlin.math.sin(st.ang) * Board.SPOUT
            art.body[k][0].draw(c, bx, by); art.face[k][4].draw(c, bx, by)
            art.body[(k + 2) % 6][0].draw(c, Toon.gloveX(st), Toon.gloveY(st), 0.72f); art.face[(k + 2) % 6][4].draw(c, Toon.gloveX(st), Toon.gloveY(st), 0.72f)
            c.restore()
        }
        for (h in 0 until 10) {
            val st = Toon.BaronState(); st.hat = h; st.t = h * 0.3f; st.angry = h == 3; if (h == 5) st.hurtT = 0.1f
            c.save(); c.translate(200f + (h % 5) * 400f, 640f + (h / 5) * 420f); c.scale(0.8f, 0.8f)
            Toon.baron(c, st)
            c.restore()
        }
        c.restore()
        ImageIO.write(img, "png", File("/home/claude/fizz/shots/toons.png"))
        println("TOONS OK")
    }
}
