package io.github.manojppatil.offlinepay.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity)

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query(
        "SELECT * FROM payments WHERE status = 'PENDING' AND nextAttemptAt <= :now " +
            "ORDER BY createdAt ASC LIMIT :limit",
    )
    suspend fun due(now: Long, limit: Int): List<PaymentEntity>

    @Query("UPDATE payments SET status = 'PENDING' WHERE status = 'SYNCING'")
    suspend fun resetInFlight(): Int

    @Query("SELECT COUNT(*) FROM payments WHERE status = 'PENDING'")
    suspend fun countPending(): Int
}
