package io.github.manojppatil.offlinepay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.manojppatil.offlinepay.sync.ConnectivityObserver
import io.github.manojppatil.offlinepay.sync.Payment
import io.github.manojppatil.offlinepay.sync.PaymentOutbox
import io.github.manojppatil.offlinepay.sync.PaymentRecorder
import io.github.manojppatil.offlinepay.sync.PaymentStatus
import io.github.manojppatil.offlinepay.sync.RecordResult
import io.github.manojppatil.offlinepay.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentsUiState(
    val payments: List<Payment> = emptyList(),
    val isOnline: Boolean = true,
    val amount: String = "",
    val note: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
) {
    private val waiting: List<Payment>
        get() = payments.filter { it.status == PaymentStatus.PENDING || it.status == PaymentStatus.SYNCING }

    val waitingCount: Int get() = waiting.size
    val waitingAmountMinor: Long get() = waiting.sumOf { it.amountMinor }
    val syncedCount: Int get() = payments.count { it.status == PaymentStatus.SYNCED }
    val failedCount: Int get() = payments.count { it.status == PaymentStatus.FAILED }
}

private data class FormState(
    val amount: String = "",
    val note: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    outbox: PaymentOutbox,
    private val recorder: PaymentRecorder,
    private val scheduler: SyncScheduler,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val form = MutableStateFlow(FormState())

    val uiState: StateFlow<PaymentsUiState> =
        combine(outbox.observeAll(), connectivity.isOnline, form) { payments, online, f ->
            PaymentsUiState(
                payments = payments,
                isOnline = online,
                amount = f.amount,
                note = f.note,
                error = f.error,
                isSaving = f.isSaving,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentsUiState())

    init {
        // Pick up anything left unsent from the last session.
        scheduler.requestSync()
    }

    fun onAmountChange(value: String) = form.update { it.copy(amount = value, error = null) }

    fun onNoteChange(value: String) = form.update { it.copy(note = value, error = null) }

    fun recordPayment() {
        val current = form.value
        if (current.isSaving) return
        form.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            when (val result = recorder.record(current.amount, current.note)) {
                is RecordResult.Recorded -> {
                    form.value = FormState()
                    scheduler.requestSync()
                }

                is RecordResult.Invalid -> form.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }
}
