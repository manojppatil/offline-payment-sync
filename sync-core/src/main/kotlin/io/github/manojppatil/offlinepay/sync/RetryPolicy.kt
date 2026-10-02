package io.github.manojppatil.offlinepay.sync

import kotlin.random.Random

/**
 * Exponential backoff with "equal jitter": the wait doubles after every failure, is capped at
 * [maxDelayMs], and a random half of it is shaved off so a hundred phones that lost signal at
 * the same moment don't all hit the gateway again at the same moment.
 */
class RetryPolicy(
    val baseDelayMs: Long = 2_000,
    val maxDelayMs: Long = 5 * 60_000,
    val maxAttempts: Int = 8,
    private val random: Random = Random.Default,
) {
    init {
        require(baseDelayMs > 0) { "baseDelayMs must be positive" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be at least baseDelayMs" }
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
    }

    /** How long to wait before the next try, given how many tries have failed so far (1-based). */
    fun delayAfter(failedAttempts: Int): Long {
        require(failedAttempts >= 1) { "failedAttempts starts at 1" }
        val ceiling = uncappedDelay(failedAttempts).coerceAtMost(maxDelayMs)
        val half = ceiling / 2
        return half + random.nextLong(ceiling - half + 1)
    }

    fun shouldGiveUp(failedAttempts: Int): Boolean = failedAttempts >= maxAttempts

    private fun uncappedDelay(failedAttempts: Int): Long {
        val exponent = failedAttempts - 1
        // Stop doubling once we are past the cap; also keeps the shift from overflowing.
        if (exponent >= 62 || baseDelayMs > (maxDelayMs shr exponent)) return maxDelayMs
        return baseDelayMs shl exponent
    }
}
