import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object LevelSheet {
    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val tw = 200; val th = 280; val cols = 12
        val img = BufferedImage(tw * cols, th * 10, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        c.drawColor(0xFF201410.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (n in 0 until Reels.TOTAL) {
            val spec = LevelSpec(n); val b = Board(); spec.build(b)
            val ox = (n % cols) * tw.toFloat(); val oy = (n / cols) * th.toFloat()
            c.save(); c.translate(ox + 4f, oy + 22f); val k = (tw - 8f) / 1000f; c.scale(k, k)
            p.color = 0xFF3A2A22.toInt(); c.drawRect(0f, 0f, 1000f, Board.DEAD_Y, p)
            if (b.hasBoss) { p.color = 0xFF555060.toInt(); c.drawCircle(b.bossX, b.bossY, b.bossR, p) }
            for (i in 0 until Board.N) {
                val kk = b.kind[i]; if (kk == Kind.EMPTY) continue
                p.color = when (kk) { Kind.STONE -> 0xFF9D9384.toInt(); Kind.BOMB -> 0xFF101010.toInt(); Kind.INK -> 0xFF2A1A3A.toInt(); else -> Pal.BUB[b.col[i]] }
                c.drawCircle(Board.cxi(i), b.cyi(i), Board.R - 4f, p)
                if (kk == Kind.NOTE) { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(Board.cxi(i), b.cyi(i), 18f, p) }
                if (kk == Kind.CAGE) { p.color = 0xFF000000.toInt(); c.drawRect(Board.cxi(i) - 6f, b.cyi(i) - 40f, Board.cxi(i) + 6f, b.cyi(i) + 40f, p) }
                if (kk == Kind.SPOOK) { p.color = 0x90FFFFFF.toInt(); c.drawCircle(Board.cxi(i), b.cyi(i) - 10f, 22f, p) }
            }
            c.restore()
            val lbl = "${n + 1} ${"CNPB"[spec.goal]} c${spec.colors} ${if (spec.limited) "s${spec.shots}" else "p${spec.par}/${if (spec.goal == Goal.PRESS) spec.pressEvery else spec.bossPress}"}"
            Draw.text(c, lbl, ox + tw / 2f, oy + 12f, 15f, Pal.CREAM, Fonts.body)
        }
        ImageIO.write(img, "png", File("/home/claude/fizz/shots/levels.png"))
        println("LEVELS OK")
    }
}
