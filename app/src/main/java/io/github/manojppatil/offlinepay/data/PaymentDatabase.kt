package io.github.manojppatil.offlinepay.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PaymentEntity::class], version = 1, exportSchema = false)
abstract class PaymentDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao
}
