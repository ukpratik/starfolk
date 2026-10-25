// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitMix64Test {
    @Test fun `the sequence is frozen`() {
        // Golden values: changing the algorithm would change every user's sky and daily picks (ADR-005).
        val rng = SplitMix64(0L)
        assertThat(List(3) { rng.nextLong() })
            .containsExactly(-2152535657050944081L, 7960286522194355700L, 487617019471545679L).inOrder()
    }

    @Test fun `bounded values stay in range and cover it`() {
        val rng = SplitMix64(42L)
        val seen = List(10_000) { rng.nextInt(7) }
        assertThat(seen.toSet()).containsExactly(0, 1, 2, 3, 4, 5, 6)
        val floats = List(10_000) { rng.nextFloat() }
        assertThat(floats.all { it >= 0f && it < 1f }).isTrue()
    }
}
