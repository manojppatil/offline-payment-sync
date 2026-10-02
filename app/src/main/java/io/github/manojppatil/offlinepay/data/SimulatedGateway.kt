package io.github.manojppatil.offlinepay.data

import io.github.manojppatil.offlinepay.sync.GatewayResult
import io.github.manojppatil.offlinepay.sync.Money
import io.github.manojppatil.offlinepay.sync.PaymentGateway
import io.github.manojppatil.offlinepay.sync.PaymentRequest
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Stand-in for a real payment gateway so the app runs without a backend.
 *
 * It behaves like a well-built gateway: it remembers idempotency keys (a repeated request gets the
 * original reference back instead of a second charge), it times out now and then, and it refuses
 * amounts above its single-payment limit.
 */
@Singleton
class SimulatedGateway @Inject constructor() : PaymentGateway {
    private val processed = ConcurrentHashMap<String, String>()
    private val random = Random(System.nanoTime())

    override suspend fun submit(request: PaymentRequest): GatewayResult {
        delay(LATENCY_MS)

        processed[request.idempotencyKey]?.let { existingRef -> return GatewayResult.Accepted(existingRef) }

        if (request.amountMinor > LIMIT_MINOR) {
            return GatewayResult.Rejected("Above the ${Money.formatRupees(LIMIT_MINOR)} single-payment limit")
        }
        if (random.nextInt(100) < TIMEOUT_PERCENT) {
            return GatewayResult.RetryableFailure("Gateway timed out")
        }

        val ref = "TXN" + request.idempotencyKey.replace("-", "").take(10).uppercase()
        processed[request.idempotencyKey] = ref
        return GatewayResult.Accepted(ref)
    }

    private companion object {
        const val LATENCY_MS = 700L
        const val TIMEOUT_PERCENT = 30
        const val LIMIT_MINOR = 50_000_00L // ₹50,000
    }
}
