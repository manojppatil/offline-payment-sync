package io.github.manojppatil.offlinepay.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sends due payments from the outbox to the gateway, one at a time, and records the outcome.
 *
 * Safety rules it keeps:
 * - Only one sync runs at a time ([mutex]), so any payment still SYNCING when a sync starts was
 *   interrupted (process death, cancellation) and is safe to send again.
 * - Every retry reuses the payment's id as the idempotency key, so "sent twice" can never mean
 *   "charged twice".
 * - A cancelled sync puts the payment back to PENDING before it lets the cancellation through.
 */
class PaymentSyncEngine(
    private val outbox: PaymentOutbox,
    private val gateway: PaymentGateway,
    private val retryPolicy: RetryPolicy,
    private val clock: Clock,
    private val batchSize: Int = 25,
) {
    private val mutex = Mutex()

    suspend fun syncDue(): SyncReport = mutex.withLock {
        val recovered = outbox.resetInFlight()
        var synced = 0
        var rescheduled = 0
        var failed = 0

        for (payment in outbox.due(clock.now(), batchSize)) {
            when (syncOne(payment)) {
                Outcome.SYNCED -> synced++
                Outcome.RESCHEDULED -> rescheduled++
                Outcome.FAILED -> failed++
            }
        }

        SyncReport(
            synced = synced,
            rescheduled = rescheduled,
            failed = failed,
            recoveredInFlight = recovered,
            pendingRemaining = outbox.countPending(),
        )
    }

    private suspend fun syncOne(payment: Payment): Outcome {
        val inFlight = payment.copy(status = PaymentStatus.SYNCING)
        outbox.update(inFlight)

        val result = try {
            gateway.submit(payment.toRequest())
        } catch (e: CancellationException) {
            withContext(NonCancellable) { outbox.update(payment) }
            throw e
        } catch (e: Exception) {
            GatewayResult.RetryableFailure(e.message ?: e::class.simpleName ?: "Unknown error")
        }

        return when (result) {
            is GatewayResult.Accepted -> {
                outbox.update(
                    inFlight.copy(
                        status = PaymentStatus.SYNCED,
                        gatewayRef = result.gatewayRef,
                        lastError = null,
                    ),
                )
                Outcome.SYNCED
            }

            is GatewayResult.Rejected -> {
                outbox.update(inFlight.copy(status = PaymentStatus.FAILED, lastError = result.reason))
                Outcome.FAILED
            }

            is GatewayResult.RetryableFailure -> scheduleRetry(inFlight, result.reason)
        }
    }

    private suspend fun scheduleRetry(payment: Payment, reason: String): Outcome {
        val attempts = payment.attempts + 1
        return if (retryPolicy.shouldGiveUp(attempts)) {
            outbox.update(
                payment.copy(
                    status = PaymentStatus.FAILED,
                    attempts = attempts,
                    lastError = "Gave up after $attempts attempts: $reason",
                ),
            )
            Outcome.FAILED
        } else {
            outbox.update(
                payment.copy(
                    status = PaymentStatus.PENDING,
                    attempts = attempts,
                    nextAttemptAt = clock.now() + retryPolicy.delayAfter(attempts),
                    lastError = reason,
                ),
            )
            Outcome.RESCHEDULED
        }
    }

    private fun Payment.toRequest() = PaymentRequest(
        idempotencyKey = id,
        amountMinor = amountMinor,
        currency = currency,
        note = note,
    )

    private enum class Outcome { SYNCED, RESCHEDULED, FAILED }
}

data class SyncReport(
    val synced: Int,
    val rescheduled: Int,
    val failed: Int,
    val recoveredInFlight: Int,
    val pendingRemaining: Int,
)
