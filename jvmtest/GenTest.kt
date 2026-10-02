import com.pranvir.fizz.*

object GenTest {
    fun ascii(b: Board): String {
        val sb = StringBuilder()
        for (r in 0 until 16) {
            if (r % 2 == 1) sb.append(' ')
            for (c in 0 until Board.cols(r)) {
                val i = Board.idx(r, c)
                val ch = when (b.kind[i]) {
                    Kind.EMPTY -> if (b.boss[i]) '#' else '.'
                    Kind.BUBBLE -> ('a' + b.col[i])
                    Kind.STONE -> 'S'; Kind.BOMB -> 'B'; Kind.CAGE -> ('A' + b.col[i]); Kind.SPOOK -> '?'; Kind.INK -> '%'; Kind.NOTE -> '*'; Kind.TICKET -> '$'
                    else -> 'x'
                }
                sb.append(ch).append(' ')
            }
            sb.append('\n')
        }
        return sb.toString()
    }
    @JvmStatic fun main(a: Array<String>) {
        val list = if (a.isNotEmpty()) a[0].split(",").map { it.toInt() } else listOf(0, 5, 13, 30, 47, 70, 119)
        for (n in list) {
            val s = LevelSpec(n)
            val b = Board(); s.build(b)
            println("L$n reel ${s.reel} goal ${Goal.TITLE[s.goal]} colors ${s.colors} diff ${"%.2f".format(s.diff)} cells ${b.occupied()}")
            print(ascii(b))
            val t0 = System.nanoTime()
            s.shots = 0
            val r = Bot.play(s, 11L)
            println("bot: won=${r.won} shots=${r.shots} score=${r.score} reason=${r.reason} in ${(System.nanoTime() - t0) / 1000000} ms")
        }
    }
}
