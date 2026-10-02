package com.pranvir.fizz

/** Small deterministic generator (SplitMix64) whose state can be copied, so the bot can fork a match. */
class Rng(var s: Long) {
    fun nextLong(): Long {
        s += -0x61c8864680b583ebL
        var z = s
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
    fun nextInt(n: Int): Int = if (n <= 1) 0 else ((nextLong() ushr 33) % n).toInt()
    fun nextFloat(): Float = (nextLong() ushr 40) / 16777216f
    fun range(a: Float, b: Float) = a + (b - a) * nextFloat()
    fun chance(p: Float) = nextFloat() < p
    fun copy() = Rng(s)
}
