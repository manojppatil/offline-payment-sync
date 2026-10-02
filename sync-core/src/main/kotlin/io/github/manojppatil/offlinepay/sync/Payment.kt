package io.github.manojppatil.offlinepay.sync

/**
 * A payment recorded on the device.
 *
 * [id] doubles as the idempotency key sent to the gateway, so every retry of the same
 * payment is recognised as the same request and can never charge twice.
 * Amounts are kept in minor units (paise) as [Long]; floating point never touches money.
 */
data class Payment(
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val note: String,
    val createdAt: Long,
    val status: PaymentStatus,
    val attempts: Int = 0,
    val nextAttemptAt: Long = createdAt,
    val gatewayRef: String? = null,
    val lastError: String? = null,
)

enum class PaymentStatus {
    /** Saved locally, waiting to be sent (first try or a scheduled retry). */
    PENDING,

    /** Handed to the gateway; the outcome is not known yet. */
    SYNCING,

    /** The gateway accepted it; [Payment.gatewayRef] holds its reference. */
    SYNCED,

    /** Rejected by the gateway, or retries ran out. Needs a person to look at it. */
    FAILED,
}

fun interface Clock {
    fun now(): Long
}
