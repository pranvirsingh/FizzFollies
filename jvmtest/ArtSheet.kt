import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ArtSheet {
    fun fonts() {
        Fonts.title = Typeface.createFromFile("/home/claude/fizz/assets/fonts/fascinate.ttf")
        Fonts.chunky = Typeface.createFromFile("/home/claude/fizz/assets/fonts/ultra.ttf")
        Fonts.body = Typeface.createFromFile("/home/claude/fizz/assets/fonts/josefin_bold.ttf")
        Fonts.deco = Typeface.createFromFile("/home/claude/fizz/assets/fonts/limelight.ttf")
    }
    @JvmStatic fun main(a: Array<String>) {
        fonts()
        val u = 1.6f
        val art = Art(u)
        val cell = 120f
        val img = BufferedImage((cell * 13 * u).toInt(), (cell * 10 * u).toInt(), BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        c.drawColor(0xFFD9C7A0.toInt())
        c.save(); c.scale(u, u)
        fun at(col: Int, row: Int) = Pair(cell * col + cell / 2, cell * row + cell / 2)
        for (k in 0 until 6) for (f in 0 until 12) {
            val (x, y) = at(f, k)
            art.body[k][f % 3].draw(c, x, y); art.face[k][f].draw(c, x, y)
        }
        var col = 0
        for (b in 0 until 3) { val (x, y) = at(col++, 6); art.stone[b].draw(c, x, y) }
        for (b in 0 until 3) { val (x, y) = at(col++, 6); art.bomb[b].draw(c, x, y) }
        for (b in 0 until 3) { val (x, y) = at(col++, 6); art.ink[b].draw(c, x, y) }
        col = 0
        for (k in 0 until 6) { val (x, y) = at(col++, 7); art.spook[k][0].draw(c, x, y) }
        run { val (x, y) = at(col++, 7); art.body[2][0].draw(c, x, y); art.note.draw(c, x, y) }
        run { val (x, y) = at(col++, 7); art.body[1][0].draw(c, x, y); art.face[1][4].draw(c, x, y); art.ticket.draw(c, x, y) }
        run { val (x, y) = at(col++, 7); art.body[0][0].draw(c, x, y); art.face[0][4].draw(c, x, y); art.cage1.draw(c, x, y) }
        run { val (x, y) = at(col++, 7); art.body[3][0].draw(c, x, y); art.face[3][4].draw(c, x, y); art.cage2.draw(c, x, y) }
        col = 0
        for (b in 0 until 3) { val (x, y) = at(col++, 8); art.rainbow[b].draw(c, x, y) }
        run { val (x, y) = at(col++, 8); art.firecracker.draw(c, x, y) }
        run { val (x, y) = at(col++, 8); art.trumpet.draw(c, x, y) }
        Draw.toon(c, "FIZZ FOLLIES", 900f, cell * 8.5f, 70f, Pal.CREAM, Fonts.title)
        c.restore()
        ImageIO.write(img, "png", File("/home/claude/fizz/shots/art.png"))
        println("ART OK")
    }
}
