import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.File

object Dbg3 {
    fun paint(reel: Int, w: Int, h: Int): BufferedImage {
        val L = Layout(); L.set(w, h, 0, 0)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img); c.save(); c.scale(L.s, L.s); Backdrop.paint(c, L, reel); c.restore()
        return img
    }
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val A = paint(0, 540, 1170)
        for (r in a[0].split(",").map { it.toInt() }) if (r >= 0) paint(r, 300, 615)
        val B = paint(0, 540, 1170)
        var diff = 0
        for (y in 0 until 1170) for (x in 0 until 540) if (A.getRGB(x, y) != B.getRGB(x, y)) diff++
        println("diff after ${a[0]}: $diff")
        ImageIO.write(B, "png", File("/tmp/claude-0/B.png"))
    }
}
