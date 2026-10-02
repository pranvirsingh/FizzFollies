import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.File

object Dbg4 {
    @JvmStatic fun main(a: Array<String>) {
        val A = Dbg3.paint(0, 540, 1170); val B = Dbg3.paint(0, 540, 1170); val C = Dbg3.paint(0, 540, 1170)
        fun d(x: BufferedImage, y: BufferedImage): Int { var n = 0; for (yy in 0 until 1170) for (xx in 0 until 540) if (x.getRGB(xx, yy) != y.getRGB(xx, yy)) n++; return n }
        println("AB ${d(A, B)} BC ${d(B, C)}")
        // where do A and B differ, and how
        var shown = 0
        for (yy in 0 until 1170 step 37) for (xx in 0 until 540 step 41) if (A.getRGB(xx, yy) != B.getRGB(xx, yy) && shown < 8) { shown++; println("($xx,$yy) A=%08x B=%08x".format(A.getRGB(xx, yy), B.getRGB(xx, yy))) }
    }
}
