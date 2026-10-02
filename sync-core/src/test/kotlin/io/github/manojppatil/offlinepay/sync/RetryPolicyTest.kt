package io.github.manojppatil.offlinepay.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RetryPolicyTest {

    @Test
    fun `delay doubles after each failure`() {
        val policy = RetryPolicy(baseDelayMs = 1_000, maxDelayMs = 60_000, random = Random(7))
        val ceilings = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)

        ceilings.forEachIndexed { index, ceiling ->
            val delay = policy.delayAfter(index + 1)
            assertTrue("attempt ${index + 1}: $delay", delay in ceiling / 2..ceiling)
        }
    }

    @Test
    fun `delay never goes past the cap`() {
        val policy = RetryPolicy(baseDelayMs = 2_000, maxDelayMs = 300_000, random = Random(7))

        repeat(200) { attempt ->
            val delay = policy.delayAfter(attempt + 1)
            assertTrue("attempt ${attempt + 1}: $delay", delay in 1_000..300_000)
        }
    }

    @Test
    fun `huge attempt counts do not overflow`() {
        val policy = RetryPolicy(baseDelayMs = 2_000, maxDelayMs = 300_000, random = Random(7))

        val delay = policy.delayAfter(Int.MAX_VALUE)

        assertTrue(delay in 150_000..300_000)
    }

    @Test
    fun `gives up only at max attempts`() {
        val policy = RetryPolicy(maxAttempts = 3)

        assertFalse(policy.shouldGiveUp(2))
        assertTrue(policy.shouldGiveUp(3))
    }

    @Test
    fun `same seed gives the same delays`() {
        val first = RetryPolicy(random = Random(99))
        val second = RetryPolicy(random = Random(99))

        assertEquals((1..5).map(first::delayAfter), (1..5).map(second::delayAfter))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cap below base is rejected`() {
        RetryPolicy(baseDelayMs = 10_000, maxDelayMs = 1_000)
    }
}
