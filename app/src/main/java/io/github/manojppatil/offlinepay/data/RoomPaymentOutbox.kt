package io.github.manojppatil.offlinepay.data

import io.github.manojppatil.offlinepay.sync.Payment
import io.github.manojppatil.offlinepay.sync.PaymentOutbox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The outbox, stored in SQLite so recorded payments survive the app being killed. */
class RoomPaymentOutbox(private val dao: PaymentDao) : PaymentOutbox {
    override fun observeAll(): Flow<List<Payment>> =
        dao.observeAll().map { rows -> rows.map(PaymentEntity::toDomain) }

    override suspend fun insert(payment: Payment) = dao.insert(payment.toEntity())

    override suspend fun update(payment: Payment) = dao.update(payment.toEntity())

    override suspend fun due(now: Long, limit: Int): List<Payment> =
        dao.due(now, limit).map(PaymentEntity::toDomain)

    override suspend fun resetInFlight(): Int = dao.resetInFlight()

    override suspend fun countPending(): Int = dao.countPending()
}
