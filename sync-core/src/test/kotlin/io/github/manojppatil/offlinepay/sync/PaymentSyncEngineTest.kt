package io.github.manojppatil.offlinepay.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PaymentSyncEngineTest {
    private val clock = FakeClock()

    // Seeded so the jittered delays are repeatable.
    private val retryPolicy = RetryPolicy(baseDelayMs = 1_000, maxDelayMs = 60_000, maxAttempts = 3, random = Random(42))

    private fun engine(outbox: PaymentOutbox, gateway: PaymentGateway) =
        PaymentSyncEngine(outbox, gateway, retryPolicy, clock)

    @Test
    fun `accepted payment is marked synced with the gateway reference`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1")))
        val gateway = ScriptedGateway(GatewayResult.Accepted("TXN-1"))

        val report = engine(outbox, gateway).syncDue()

        val saved = outbox.snapshot.value.getValue("p1")
        assertEquals(PaymentStatus.SYNCED, saved.status)
        assertEquals("TXN-1", saved.gatewayRef)
        assertEquals(SyncReport(synced = 1, rescheduled = 0, failed = 0, recoveredInFlight = 0, pendingRemaining = 0), report)
    }

    @Test
    fun `payment id is sent as the idempotency key`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1", amountMinor = 50_000)))
        val gateway = ScriptedGateway(GatewayResult.Accepted("TXN-1"))

        engine(outbox, gateway).syncDue()

        assertEquals(PaymentRequest("p1", 50_000, "INR", "EMI for p1"), gateway.requests.single())
    }

    @Test
    fun `retryable failure keeps the payment pending and schedules the next try`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1")))
        val gateway = ScriptedGateway(GatewayResult.RetryableFailure("timeout"))

        val report = engine(outbox, gateway).syncDue()

        val saved = outbox.snapshot.value.getValue("p1")
        assertEquals(PaymentStatus.PENDING, saved.status)
        assertEquals(1, saved.attempts)
        assertEquals("timeout", saved.lastError)
        // First retry waits between half and all of the 1 s base delay.
        val wait = saved.nextAttemptAt - clock.time
        assertTrue("wait was $wait", wait in 500..1_000)
        assertEquals(1, report.rescheduled)
        assertEquals(1, report.pendingRemaining)
    }

    @Test
    fun `payment is not retried before its next attempt time`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1", nextAttemptAt = clock.time + 5_000)))
        val gateway = ScriptedGateway()

        val report = engine(outbox, gateway).syncDue()

        assertTrue(gateway.requests.isEmpty())
        assertEquals(1, report.pendingRemaining)
    }

    @Test
    fun `every retry reuses the same idempotency key`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1")))
        val gateway = ScriptedGateway(
            GatewayResult.RetryableFailure("timeout"),
            GatewayResult.Accepted("TXN-1"),
        )
        val engine = engine(outbox, gateway)

        engine.syncDue()
        clock.time += 60_000
        engine.syncDue()

        assertEquals(listOf("p1", "p1"), gateway.requests.map { it.idempotencyKey })
        assertEquals(PaymentStatus.SYNCED, outbox.snapshot.value.getValue("p1").status)
    }

    @Test
    fun `payment fails once retries run out`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1", attempts = 2)))
        val gateway = ScriptedGateway(GatewayResult.RetryableFailure("timeout"))

        val report = engine(outbox, gateway).syncDue()

        val saved = outbox.snapshot.value.getValue("p1")
        assertEquals(PaymentStatus.FAILED, saved.status)
        assertEquals(3, saved.attempts)
        assertEquals("Gave up after 3 attempts: timeout", saved.lastError)
        assertEquals(1, report.failed)
    }

    @Test
    fun `rejected payment fails straight away`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1")))
        val gateway = ScriptedGateway(GatewayResult.Rejected("Above single-payment limit"))

        engine(outbox, gateway).syncDue()

        val saved = outbox.snapshot.value.getValue("p1")
        assertEquals(PaymentStatus.FAILED, saved.status)
        assertEquals(0, saved.attempts)
        assertEquals("Above single-payment limit", saved.lastError)
    }

    @Test
    fun `gateway exception is treated as retryable`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1")))
        val gateway = object : PaymentGateway {
            override suspend fun submit(request: PaymentRequest): GatewayResult =
                throw java.io.IOException("Connection reset")
        }

        engine(outbox, gateway).syncDue()

        val saved = outbox.snapshot.value.getValue("p1")
        assertEquals(PaymentStatus.PENDING, saved.status)
        assertEquals("Connection reset", saved.lastError)
    }

    @Test
    fun `payment stuck in syncing after process death is sent again`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1", status = PaymentStatus.SYNCING)))
        val gateway = ScriptedGateway(GatewayResult.Accepted("TXN-1"))

        val report = engine(outbox, gateway).syncDue()

        assertEquals(1, report.recoveredInFlight)
        assertEquals(PaymentStatus.SYNCED, outbox.snapshot.value.getValue("p1").status)
    }

    @Test
    fun `cancelled sync puts the payment back to pending`() = runTest {
        val outbox = InMemoryPaymentOutbox(listOf(pendingPayment("p1")))
        val submitted = CompletableDeferred<Unit>()
        val gateway = object : PaymentGateway {
            override suspend fun submit(request: PaymentRequest): GatewayResult {
                submitted.complete(Unit)
                CompletableDeferred<Nothing>().await() // never answers
            }
        }

        val job = launch { engine(outbox, gateway).syncDue() }
        submitted.await()
        assertEquals(PaymentStatus.SYNCING, outbox.snapshot.value.getValue("p1").status)
        job.cancelAndJoin()

        val saved = outbox.snapshot.value.getValue("p1")
        assertEquals(PaymentStatus.PENDING, saved.status)
        assertEquals(0, saved.attempts)
    }

    @Test
    fun `oldest payments are sent first and synced ones are left alone`() = runTest {
        val outbox = InMemoryPaymentOutbox(
            listOf(
                pendingPayment("newer", createdAt = 2_000),
                pendingPayment("older", createdAt = 1_000),
                pendingPayment("done", createdAt = 500, status = PaymentStatus.SYNCED),
            ),
        )
        val gateway = ScriptedGateway(GatewayResult.Accepted("A"), GatewayResult.Accepted("B"))

        engine(outbox, gateway).syncDue()

        assertEquals(listOf("older", "newer"), gateway.requests.map { it.idempotencyKey })
        assertNull(outbox.snapshot.value.getValue("done").gatewayRef)
    }
}
