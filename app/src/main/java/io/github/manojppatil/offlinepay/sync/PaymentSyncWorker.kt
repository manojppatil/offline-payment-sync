package io.github.manojppatil.offlinepay.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs whenever the phone has a network connection and there is work queued.
 *
 * The engine decides *which* payments are due (per-payment backoff); WorkManager decides *when*
 * this worker wakes up again (its own backoff, plus the network constraint).
 */
@HiltWorker
class PaymentSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: PaymentSyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val report = engine.syncDue()
        return if (report.pendingRemaining > 0) Result.retry() else Result.success()
    }
}
