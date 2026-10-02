package io.github.manojppatil.offlinepay.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Durable local queue of payments (the "outbox"). The app backs it with Room;
 * [InMemoryPaymentOutbox] backs tests and previews.
 */
interface PaymentOutbox {
    /** Every payment, newest first. */
    fun observeAll(): Flow<List<Payment>>

    suspend fun insert(payment: Payment)

    suspend fun update(payment: Payment)

    /** PENDING payments whose [Payment.nextAttemptAt] has passed, oldest first. */
    suspend fun due(now: Long, limit: Int): List<Payment>

    /** Moves SYNCING payments back to PENDING. Returns how many were moved. */
    suspend fun resetInFlight(): Int

    suspend fun countPending(): Int
}

class InMemoryPaymentOutbox(initial: List<Payment> = emptyList()) : PaymentOutbox {
    private val rows = MutableStateFlow(initial.associateBy { it.id })

    val snapshot: StateFlow<Map<String, Payment>> get() = rows

    override fun observeAll(): Flow<List<Payment>> =
        rows.map { all -> all.values.sortedByDescending { it.createdAt } }

    override suspend fun insert(payment: Payment) {
        rows.update { current ->
            require(payment.id !in current) { "Payment ${payment.id} already exists" }
            current + (payment.id to payment)
        }
    }

    override suspend fun update(payment: Payment) {
        rows.update { current ->
            require(payment.id in current) { "Payment ${payment.id} not found" }
            current + (payment.id to payment)
        }
    }

    override suspend fun due(now: Long, limit: Int): List<Payment> =
        rows.value.values
            .filter { it.status == PaymentStatus.PENDING && it.nextAttemptAt <= now }
            .sortedBy { it.createdAt }
            .take(limit)

    override suspend fun resetInFlight(): Int {
        var moved = 0
        rows.update { current ->
            moved = 0
            current.mapValues { (_, payment) ->
                if (payment.status == PaymentStatus.SYNCING) {
                    moved++
                    payment.copy(status = PaymentStatus.PENDING)
                } else {
                    payment
                }
            }
        }
        return moved
    }

    override suspend fun countPending(): Int =
        rows.value.values.count { it.status == PaymentStatus.PENDING }
}
