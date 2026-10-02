import com.pranvir.fizz.*
import java.util.concurrent.Executors

object Report {
    @JvmStatic fun main(a: Array<String>) {
        val skill = if (a.isNotEmpty()) a[0].toFloat() else 0.55f
        val runs = if (a.size > 1) a[1].toInt() else 6
        val ex = Executors.newFixedThreadPool(2)
        val futs = (0 until Reels.TOTAL).map { n -> ex.submit<String> {
            val s = LevelSpec(n)
            val res = (0 until runs).map { Bot.play(s, 90000L + n * 13 + it * 101, skill) }
            val wins = res.count { it.won }
            val stars = res.filter { it.won }.map { r -> if (r.score >= s.s3) 3 else if (r.score >= s.s2) 2 else 1 }
            val used = res.filter { it.won }.map { it.shots }.average()
            "L%3d r%d %-15s c%d shots %3d press %2d  win %d/%d  used %.0f  stars %s".format(n + 1, s.reel, Goal.TITLE[s.goal], s.colors,
                s.shots, if (s.goal == Goal.BOSS) s.bossPress else s.pressEvery, wins, runs, used, stars.sorted().joinToString(""))
        } }
        var tot = 0; var won = 0
        for (f in futs) { val line = f.get(); println(line); val m = Regex("win (\\d)/(\\d)").find(line)!!; won += m.groupValues[1].toInt(); tot += m.groupValues[2].toInt() }
        ex.shutdown()
        println("OVERALL win $won/$tot at skill $skill")
    }
}
