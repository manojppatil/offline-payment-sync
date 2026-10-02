package io.github.manojppatil.offlinepay.sync

/** Returns the queued results in order and remembers every request it saw. */
class ScriptedGateway(vararg results: GatewayResult) : PaymentGateway {
    private val queue = ArrayDeque(results.toList())
    val requests = mutableListOf<PaymentRequest>()

    override suspend fun submit(request: PaymentRequest): GatewayResult {
        requests += request
        return queue.removeFirstOrNull() ?: error("ScriptedGateway ran out of results")
    }
}

class FakeClock(var time: Long = 1_000_000L) : Clock {
    override fun now(): Long = time
}

fun pendingPayment(
    id: String,
    amountMinor: Long = 125_050,
    createdAt: Long = 1_000_000L,
    attempts: Int = 0,
    nextAttemptAt: Long = createdAt,
    status: PaymentStatus = PaymentStatus.PENDING,
) = Payment(
    id = id,
    amountMinor = amountMinor,
    currency = "INR",
    note = "EMI for $id",
    createdAt = createdAt,
    status = status,
    attempts = attempts,
    nextAttemptAt = nextAttemptAt,
)
