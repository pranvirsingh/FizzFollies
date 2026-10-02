import android.graphics.*
import com.pranvir.fizz.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object IconGen {
    fun art(c: Canvas, s: Float, safe: Float, bg: Boolean, art: Art, mono: Boolean = false) {
        if (bg) {
            UI.sunburst(c, s / 2f, s * 0.42f, s * 1.2f, 0.1f, 0xFFF1D9A2.toInt(), 0xFFE7A35A.toInt(), 20)
            val v = Paint(); v.shader = RadialGradient(s / 2f, s / 2f, s * 0.75f, intArrayOf(0x00000000, 0x10000000, 0x701E0E04), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, s, s, v)
        }
        val k = safe / 370f
        c.save(); c.translate(s / 2f + 10f * k, s / 2f + 10f * k); c.scale(k, k)
        val st = Toon.FizzState(); st.mood = 1; st.ang = 1.5708f; st.gazeY = -1f
        if (mono) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(0f, -Board.SPOUT - 6f, 52f, p)
            val r = RectF(-72f, -40f, 72f, 170f); c.drawRoundRect(r, 30f, 30f, p)
        } else {
            c.translate(0f, -20f)
            Toon.fizz(c, st)
            art.body[0][0].draw(c, 0f, -Board.SPOUT - 4f, 1.15f); art.face[0][Art.F_HAPPY].draw(c, 0f, -Board.SPOUT - 4f, 1.15f)
        }
        c.restore()
    }
    fun save(img: BufferedImage, path: String) { val f = File(path); f.parentFile.mkdirs(); ImageIO.write(img, "png", f) }

    @JvmStatic fun main(a: Array<String>) {
        ArtSheet.fonts()
        val res = "/home/claude/fizz/res"
        for ((dn, k) in linkedMapOf("mdpi" to 1f, "hdpi" to 1.5f, "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f)) {
            val fs = (108 * k).toInt()
            val art = Art(fs * 0.6f / 300f * 1.0f)
            val fg = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
            art(Canvas(fg), fs.toFloat(), fs * 0.6f, true, art)
            save(fg, "$res/mipmap-$dn/ic_launcher_fg.png")
            val mono = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
            art(Canvas(mono), fs.toFloat(), fs * 0.6f, false, art, true)
            save(mono, "$res/mipmap-$dn/ic_launcher_mono.png")
            val ls = (48 * k).toInt()
            for (round in listOf(false, true)) {
                val big = ls * 4
                val art2 = Art(big * 0.78f / 300f)
                val img = BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB)
                val c = Canvas(img)
                val clip = Path(); val inset = big * 0.04f
                if (round) clip.addCircle(big / 2f, big / 2f, big / 2f - inset, Path.Direction.CW)
                else clip.addRoundRect(RectF(inset, inset, big - inset, big - inset), big * 0.18f, big * 0.18f, Path.Direction.CW)
                c.save(); c.clipPath(clip); art(c, big.toFloat(), big * 0.78f, true, art2); c.restore()
                val out = BufferedImage(ls, ls, BufferedImage.TYPE_INT_ARGB)
                out.createGraphics().drawImage(img.getScaledInstance(ls, ls, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
                save(out, "$res/mipmap-$dn/" + (if (round) "ic_launcher_round.png" else "ic_launcher.png"))
            }
        }
        val prev = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
        art(Canvas(prev), 512f, 512f * 0.75f, true, Art(512f * 0.75f / 300f))
        save(prev, "/home/claude/fizz/shots/icon512.png")
        println("ICONS OK")
    }
}
