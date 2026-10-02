package io.github.manojppatil.offlinepay.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface SyncScheduler {
    /** Ask for a sync as soon as there is a network connection. */
    fun requestSync()
}

class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<PaymentSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        // REPLACE so a new payment never waits behind a sync that is sitting in backoff.
        // If a sync is mid-flight it is cancelled; the engine puts that payment back to PENDING,
        // and the idempotency key makes resending it safe.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "payment-sync"
        const val BACKOFF_SECONDS = 30L
    }
}
