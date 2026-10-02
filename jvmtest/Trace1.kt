import com.pranvir.fizz.*

object Trace1 {
    @JvmStatic fun main(a: Array<String>) {
        val n = a[0].toInt(); val skill = a[1].toFloat(); val seed = a[2].toLong()
        val s = LevelSpec(n)
        val m = Match(s, seed)
        val noise = if (skill < 1f) Rng(seed * 31 + 7) else null
        println(GenTest.ascii(m.b))
        var turn = 0
        while (m.state == Match.PLAYING && turn < 80) {
            val p = Bot.choose(m, skill, noise)
            if (p.swap) m.swap(); if (p.power) m.arm()
            val c = m.cur
            val sh = m.fire(p.ang)!!
            println("t$turn col=$c pow=${sh.power} ang=${"%.3f".format(p.ang)} land=${sh.trace.land} pop=${sh.popped} drop=${sh.dropped} left=${m.shotsLeft} colored=${m.b.coloredCount()} cur=${m.cur} next=${m.next} app=${m.applause}")
            turn++
        }
        println(GenTest.ascii(m.b))
        println("state ${m.state} reason ${m.lostReason}")
    }
}
