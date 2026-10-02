import com.pranvir.fizz.*
object Mel { @JvmStatic fun main(a: Array<String>) { for (s in 0 until 3) { val names = arrayOf("C","C#","D","Eb","E","F","F#","G","Ab","A","Bb","B")
  println("song $s: " + Synth.melodyDebug(s).joinToString(" ") { n -> "${names[n[2].toInt() % 12]}${n[2].toInt() / 12 - 1}@${n[0]}" }) } } }
