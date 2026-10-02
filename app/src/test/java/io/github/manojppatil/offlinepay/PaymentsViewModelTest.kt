package io.github.manojppatil.offlinepay

import io.github.manojppatil.offlinepay.sync.Clock
import io.github.manojppatil.offlinepay.sync.ConnectivityObserver
import io.github.manojppatil.offlinepay.sync.InMemoryPaymentOutbox
import io.github.manojppatil.offlinepay.sync.PaymentRecorder
import io.github.manojppatil.offlinepay.sync.PaymentStatus
import io.github.manojppatil.offlinepay.sync.SyncScheduler
import io.github.manojppatil.offlinepay.ui.PaymentsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val outbox = InMemoryPaymentOutbox()
    private val scheduler = CountingScheduler()
    private val connectivity = FakeConnectivity()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PaymentsViewModel(
        outbox = outbox,
        recorder = PaymentRecorder(outbox, Clock { 1_000L }),
        scheduler = scheduler,
        connectivity = connectivity,
    )

    @Test
    fun `opening the screen asks for a sync of anything left over`() {
        viewModel()

        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `recording a valid payment saves it, clears the form and asks for a sync`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainDispatcher) { viewModel.uiState.collect {} }

        viewModel.onAmountChange("1,250.50")
        viewModel.onNoteChange("Loan 4471")
        viewModel.recordPayment()

        val state = viewModel.uiState.value
        assertEquals("", state.amount)
        assertEquals("", state.note)
        assertNull(state.error)
        assertEquals(125_050L, state.payments.single().amountMinor)
        assertEquals(PaymentStatus.PENDING, state.payments.single().status)
        assertEquals(2, scheduler.requests)
    }

    @Test
    fun `invalid amount shows an error and saves nothing`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainDispatcher) { viewModel.uiState.collect {} }

        viewModel.onAmountChange("12.345")
        viewModel.recordPayment()

        val state = viewModel.uiState.value
        assertEquals("Enter an amount like 1250 or 1250.50", state.error)
        assertEquals("12.345", state.amount)
        assertFalse(state.isSaving)
        assertTrue(state.payments.isEmpty())
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `offline state comes from the connectivity observer`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainDispatcher) { viewModel.uiState.collect {} }

        connectivity.online.value = false

        assertFalse(viewModel.uiState.value.isOnline)
    }

    private class CountingScheduler : SyncScheduler {
        var requests = 0
        override fun requestSync() {
            requests++
        }
    }

    private class FakeConnectivity : ConnectivityObserver {
        val online = MutableStateFlow(true)
        override val isOnline: Flow<Boolean> = online
    }
}
