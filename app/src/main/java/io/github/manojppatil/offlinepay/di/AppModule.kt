package io.github.manojppatil.offlinepay.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.manojppatil.offlinepay.data.PaymentDao
import io.github.manojppatil.offlinepay.data.PaymentDatabase
import io.github.manojppatil.offlinepay.data.RoomPaymentOutbox
import io.github.manojppatil.offlinepay.data.SimulatedGateway
import io.github.manojppatil.offlinepay.sync.AndroidConnectivityObserver
import io.github.manojppatil.offlinepay.sync.Clock
import io.github.manojppatil.offlinepay.sync.ConnectivityObserver
import io.github.manojppatil.offlinepay.sync.PaymentGateway
import io.github.manojppatil.offlinepay.sync.PaymentOutbox
import io.github.manojppatil.offlinepay.sync.PaymentRecorder
import io.github.manojppatil.offlinepay.sync.PaymentSyncEngine
import io.github.manojppatil.offlinepay.sync.RetryPolicy
import io.github.manojppatil.offlinepay.sync.SyncScheduler
import io.github.manojppatil.offlinepay.sync.WorkManagerSyncScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PaymentDatabase =
        Room.databaseBuilder(context, PaymentDatabase::class.java, "payments.db").build()

    @Provides
    fun providePaymentDao(database: PaymentDatabase): PaymentDao = database.paymentDao()

    @Provides
    @Singleton
    fun provideOutbox(dao: PaymentDao): PaymentOutbox = RoomPaymentOutbox(dao)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideRetryPolicy(): RetryPolicy = RetryPolicy()

    @Provides
    @Singleton
    fun provideSyncEngine(
        outbox: PaymentOutbox,
        gateway: PaymentGateway,
        retryPolicy: RetryPolicy,
        clock: Clock,
    ): PaymentSyncEngine = PaymentSyncEngine(outbox, gateway, retryPolicy, clock)

    @Provides
    @Singleton
    fun providePaymentRecorder(outbox: PaymentOutbox, clock: Clock): PaymentRecorder =
        PaymentRecorder(outbox, clock)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindGateway(gateway: SimulatedGateway): PaymentGateway

    @Binds
    abstract fun bindSyncScheduler(scheduler: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindConnectivityObserver(observer: AndroidConnectivityObserver): ConnectivityObserver
}
