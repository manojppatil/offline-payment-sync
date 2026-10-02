package io.github.manojppatil.offlinepay.sync

/** Whatever actually moves the money: a payment gateway SDK, a REST API, a POS terminal. */
interface PaymentGateway {
    suspend fun submit(request: PaymentRequest): GatewayResult
}

data class PaymentRequest(
    val idempotencyKey: String,
    val amountMinor: Long,
    val currency: String,
    val note: String,
)

sealed interface GatewayResult {
    data class Accepted(val gatewayRef: String) : GatewayResult

    /** A final answer such as "limit exceeded". Retrying would get the same answer. */
    data class Rejected(val reason: String) : GatewayResult

    /** Timeouts, 5xx responses, dropped connections. Worth trying again later. */
    data class RetryableFailure(val reason: String) : GatewayResult
}
