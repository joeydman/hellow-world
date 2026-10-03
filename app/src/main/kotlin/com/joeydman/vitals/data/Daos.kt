package com.joeydman.vitals.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Insert suspend fun insert(sample: BatterySampleEntity)

    @Query("SELECT * FROM battery_sample WHERE timestampMs >= :sinceMs ORDER BY timestampMs")
    suspend fun since(sinceMs: Long): List<BatterySampleEntity>

    @Query("SELECT * FROM battery_sample WHERE timestampMs >= :sinceMs ORDER BY timestampMs")
    fun observeSince(sinceMs: Long): Flow<List<BatterySampleEntity>>

    @Query("SELECT * FROM battery_sample ORDER BY timestampMs")
    suspend fun all(): List<BatterySampleEntity>

    @Query("DELETE FROM battery_sample WHERE timestampMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long)
}

@Dao
interface SystemDao {
    @Insert suspend fun insert(sample: SystemSampleEntity)

    @Query("SELECT * FROM system_sample WHERE timestampMs >= :sinceMs ORDER BY timestampMs")
    fun observeSince(sinceMs: Long): Flow<List<SystemSampleEntity>>

    @Query("DELETE FROM system_sample WHERE timestampMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long)
}

@Dao
interface UsageDao {
    @Upsert suspend fun upsertAll(rows: List<AppUsageDayEntity>)

    @Query("SELECT * FROM app_usage_day WHERE dayStartMs = :dayStartMs ORDER BY foregroundMs DESC")
    fun observeDay(dayStartMs: Long): Flow<List<AppUsageDayEntity>>
}

@Dao
interface CapacityDao {
    @Upsert suspend fun upsert(row: CapacityEstimateEntity)

    @Query("SELECT * FROM capacity_estimate ORDER BY dayStartMs")
    fun observeAll(): Flow<List<CapacityEstimateEntity>>

    @Query("SELECT * FROM capacity_estimate ORDER BY dayStartMs DESC LIMIT 1")
    suspend fun latest(): CapacityEstimateEntity?
}
