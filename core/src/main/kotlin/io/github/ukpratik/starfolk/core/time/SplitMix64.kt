// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.time

/**
 * A tiny, fast, well-mixed PRNG with a fixed algorithm, so seeded results never change across JVMs or Kotlin
 * versions (ADR-005). Not for security.
 */
class SplitMix64(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1L shl 24).toFloat()

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
    }
}
