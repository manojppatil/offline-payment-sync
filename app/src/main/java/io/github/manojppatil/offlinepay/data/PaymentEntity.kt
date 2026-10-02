package io.github.manojppatil.offlinepay.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.manojppatil.offlinepay.sync.Payment
import io.github.manojppatil.offlinepay.sync.PaymentStatus

@Entity(
    tableName = "payments",
    indices = [Index(value = ["status", "nextAttemptAt"])],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val amountMinor: Long,
    val currency: String,
    val note: String,
    val createdAt: Long,
    val status: String,
    val attempts: Int,
    val nextAttemptAt: Long,
    val gatewayRef: String?,
    val lastError: String?,
)

fun PaymentEntity.toDomain() = Payment(
    id = id,
    amountMinor = amountMinor,
    currency = currency,
    note = note,
    createdAt = createdAt,
    status = PaymentStatus.valueOf(status),
    attempts = attempts,
    nextAttemptAt = nextAttemptAt,
    gatewayRef = gatewayRef,
    lastError = lastError,
)

fun Payment.toEntity() = PaymentEntity(
    id = id,
    amountMinor = amountMinor,
    currency = currency,
    note = note,
    createdAt = createdAt,
    status = status.name,
    attempts = attempts,
    nextAttemptAt = nextAttemptAt,
    gatewayRef = gatewayRef,
    lastError = lastError,
)
