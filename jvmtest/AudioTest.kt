import com.pranvir.fizz.*
import java.io.File

object AudioTest {
    @JvmStatic fun main(a: Array<String>) {
        val t0 = System.currentTimeMillis()
        for (id in 0 until Sfx.COUNT) {
            val p = Synth.render(id)
            var peak = 0; for (v in p) peak = maxOf(peak, kotlin.math.abs(v.toInt()))
            check(p.size > 100 && peak > 2000) { "sfx $id silent" }
        }
        val t1 = System.currentTimeMillis()
        val stems = Array(Synth.SONGS) { s -> Array(Synth.STEMS) { st -> Synth.renderStem(s, st) } }
        val t2 = System.currentTimeMillis()
        for (s in 0 until Synth.SONGS) for (st in 0 until Synth.STEMS) {
            val p = stems[s][st]; var peak = 0; var sum = 0.0
            for (v in p) { peak = maxOf(peak, kotlin.math.abs(v.toInt())); sum += v.toDouble() * v }
            println("song $s stem $st peak $peak rms ${"%.0f".format(Math.sqrt(sum / p.size))}")
        }
        val mix = Mixer(stems)
        for ((name, scene) in listOf("menu" to Scene.MENU, "play" to Scene.PLAY, "boss" to Scene.BOSS)) {
            val out = ShortArray(Synth.RATE * 20)
            val chunk = ShortArray(1024)
            var i = 0
            while (i < out.size) { mix.render(chunk, scene, 0.7f, true, true); System.arraycopy(chunk, 0, out, i, minOf(1024, out.size - i)); i += 1024 }
            var peak = 0; var clip = 0; for (v in out) { val av = kotlin.math.abs(v.toInt()); peak = maxOf(peak, av); if (av >= 32767) clip++ }
            println("mix $name peak $peak clipped $clip")
            File("/home/claude/fizz/shots/music_$name.wav").writeBytes(Synth.wav(out))
        }
        val sfx = ArrayList<Short>()
        for (id in 0 until Sfx.COUNT) { for (v in Synth.render(id)) sfx.add(v); repeat(Synth.RATE / 4) { sfx.add(0) } }
        File("/home/claude/fizz/shots/sfx_all.wav").writeBytes(Synth.wav(sfx.toShortArray()))
        println("AUDIO OK sfx ${t1 - t0}ms stems ${t2 - t1}ms")
    }
}
