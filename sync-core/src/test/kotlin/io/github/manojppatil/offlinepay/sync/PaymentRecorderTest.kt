package io.github.manojppatil.offlinepay.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentRecorderTest {
    private val outbox = InMemoryPaymentOutbox()
    private val recorder = PaymentRecorder(outbox, Clock { 42_000L }, newId = { "fixed-id" })

    @Test
    fun `valid payment is saved as pending and due now`() = runTest {
        val result = recorder.record("1,250.50", "  EMI October  ")

        val payment = (result as RecordResult.Recorded).payment
        assertEquals(
            Payment(
                id = "fixed-id",
                amountMinor = 125_050,
                currency = "INR",
                note = "EMI October",
                createdAt = 42_000L,
                status = PaymentStatus.PENDING,
                nextAttemptAt = 42_000L,
            ),
            payment,
        )
        assertEquals(payment, outbox.snapshot.value["fixed-id"])
    }

    @Test
    fun `unreadable amount is refused and nothing is saved`() = runTest {
        val result = recorder.record("12.345", "")

        assertEquals(RecordResult.Invalid("Enter an amount like 1250 or 1250.50"), result)
        assertTrue(outbox.snapshot.value.isEmpty())
    }

    @Test
    fun `zero is refused`() = runTest {
        assertEquals(RecordResult.Invalid("Amount must be more than ₹0"), recorder.record("0", ""))
    }

    @Test
    fun `amount over the limit is refused`() = runTest {
        assertEquals(
            RecordResult.Invalid("Amount can't be more than ₹10,00,000.00"),
            recorder.record("1000000.01", ""),
        )
    }

    @Test
    fun `long note is refused`() = runTest {
        val result = recorder.record("100", "x".repeat(81))

        assertEquals(RecordResult.Invalid("Keep the note under 80 characters"), result)
    }
}
