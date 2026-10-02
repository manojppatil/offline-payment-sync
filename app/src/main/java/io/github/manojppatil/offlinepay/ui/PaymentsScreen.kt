package io.github.manojppatil.offlinepay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.manojppatil.offlinepay.sync.Money
import io.github.manojppatil.offlinepay.sync.Payment
import io.github.manojppatil.offlinepay.sync.PaymentStatus

@Composable
fun PaymentsRoute(viewModel: PaymentsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PaymentsScreen(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onNoteChange = viewModel::onNoteChange,
        onRecord = viewModel::recordPayment,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    state: PaymentsUiState,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Collections") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.isOnline) {
                item { OfflineBanner(state) }
            }
            item {
                PaymentForm(
                    state = state,
                    onAmountChange = onAmountChange,
                    onNoteChange = onNoteChange,
                    onRecord = onRecord,
                )
            }
            item { Summary(state) }
            if (state.payments.isEmpty()) {
                item {
                    Text(
                        text = "No payments yet. Each one is saved on the phone first and sent when there is signal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.payments, key = { it.id }) { payment ->
                PaymentRow(payment)
            }
        }
    }
}

@Composable
private fun OfflineBanner(state: PaymentsUiState) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "No signal. ${state.waitingCount} payment(s), ${Money.formatRupees(state.waitingAmountMinor)}, " +
                "will be sent automatically when you are back online.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun PaymentForm(
    state: PaymentsUiState,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRecord: () -> Unit,
) {
    val error = state.error
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text("Note (loan or customer reference)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onRecord,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Record payment")
            }
        }
    }
}

@Composable
private fun Summary(state: PaymentsUiState) {
    Text(
        text = "Waiting ${state.waitingCount} · Sent ${state.syncedCount} · Failed ${state.failedCount}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PaymentRow(payment: Payment) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(Money.formatRupees(payment.amountMinor), style = MaterialTheme.typography.titleMedium)
                if (payment.note.isNotEmpty()) {
                    Text(payment.note, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = detailFor(payment),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(payment.status)
        }
    }
}

private fun detailFor(payment: Payment): String = when (payment.status) {
    PaymentStatus.SYNCED -> "Ref ${payment.gatewayRef}"
    PaymentStatus.SYNCING -> "Sending…"
    PaymentStatus.FAILED -> payment.lastError ?: "Failed"
    PaymentStatus.PENDING ->
        if (payment.attempts == 0) {
            "Saved on phone, waiting to send"
        } else {
            "Try ${payment.attempts} failed: ${payment.lastError}. Retrying automatically."
        }
}

@Composable
private fun StatusPill(status: PaymentStatus) {
    val (label, background, content) = when (status) {
        PaymentStatus.PENDING -> Triple("Waiting", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        PaymentStatus.SYNCING -> Triple("Sending", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        PaymentStatus.SYNCED -> Triple("Sent", SentGreen, Color.White)
        PaymentStatus.FAILED -> Triple("Failed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val SentGreen = Color(0xFF2E7D32)

@Preview(showBackground = true)
@Composable
private fun PaymentsScreenPreview() {
    val now = 1_700_000_000_000L
    CollectTheme {
        PaymentsScreen(
            state = PaymentsUiState(
                isOnline = false,
                payments = listOf(
                    Payment("a", 250_000, "INR", "Loan 4471, October EMI", now, PaymentStatus.PENDING),
                    Payment(
                        "b", 125_050, "INR", "Loan 3920", now - 1, PaymentStatus.PENDING,
                        attempts = 2, lastError = "Gateway timed out",
                    ),
                    Payment("c", 98_000, "INR", "Loan 2210", now - 2, PaymentStatus.SYNCED, gatewayRef = "TXN8F21C0A9B4"),
                ),
            ),
            onAmountChange = {},
            onNoteChange = {},
            onRecord = {},
        )
    }
}
