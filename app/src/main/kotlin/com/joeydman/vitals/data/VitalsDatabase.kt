package com.joeydman.vitals.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BatterySampleEntity::class,
        SystemSampleEntity::class,
        AppUsageDayEntity::class,
        CapacityEstimateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VitalsDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao
    abstract fun systemDao(): SystemDao
    abstract fun usageDao(): UsageDao
    abstract fun capacityDao(): CapacityDao

    companion object {
        fun create(context: Context): VitalsDatabase =
            Room.databaseBuilder(context, VitalsDatabase::class.java, "vitals.db").build()
    }
}
