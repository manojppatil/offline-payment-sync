package io.github.manojppatil.offlinepay.sync

import java.util.UUID

/** Validates what the agent typed and saves it to the outbox. Never touches the network. */
class PaymentRecorder(
    private val outbox: PaymentOutbox,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun record(amountInput: String, note: String): RecordResult {
        val amountMinor = Money.parseRupees(amountInput)
            ?: return RecordResult.Invalid("Enter an amount like 1250 or 1250.50")
        if (amountMinor <= 0) {
            return RecordResult.Invalid("Amount must be more than ₹0")
        }
        if (amountMinor > MAX_AMOUNT_MINOR) {
            return RecordResult.Invalid("Amount can't be more than ${Money.formatRupees(MAX_AMOUNT_MINOR)}")
        }
        val cleanNote = note.trim()
        if (cleanNote.length > MAX_NOTE_LENGTH) {
            return RecordResult.Invalid("Keep the note under $MAX_NOTE_LENGTH characters")
        }

        val now = clock.now()
        val payment = Payment(
            id = newId(),
            amountMinor = amountMinor,
            currency = CURRENCY,
            note = cleanNote,
            createdAt = now,
            status = PaymentStatus.PENDING,
            nextAttemptAt = now,
        )
        outbox.insert(payment)
        return RecordResult.Recorded(payment)
    }

    companion object {
        const val CURRENCY = "INR"
        const val MAX_AMOUNT_MINOR = 10_00_000_00L // ₹10,00,000
        const val MAX_NOTE_LENGTH = 80
    }
}

sealed interface RecordResult {
    data class Recorded(val payment: Payment) : RecordResult
    data class Invalid(val message: String) : RecordResult
}
