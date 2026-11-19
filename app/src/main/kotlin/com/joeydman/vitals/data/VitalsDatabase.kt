package com.joeydman.vitals.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BatterySampleEntity::class,
        SystemSampleEntity::class,
        AppUsageDayEntity::class,
        CapacityEstimateEntity::class,
        ChargeSessionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class VitalsDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao
    abstract fun systemDao(): SystemDao
    abstract fun usageDao(): UsageDao
    abstract fun capacityDao(): CapacityDao
    abstract fun chargeSessionDao(): ChargeSessionDao

    companion object {
        fun create(context: Context): VitalsDatabase =
            Room.databaseBuilder(context, VitalsDatabase::class.java, "vitals.db")
                .addMigrations(Migrations.MIGRATION_1_2)
                .build()
    }
}

/**
 * Hand-written migrations so existing history survives upgrades. SchemaMigrationTest checks each
 * CREATE statement against the schema Room generates, so a typo here fails CI, not the phone.
 */
object Migrations {
    const val CREATE_CHARGE_SESSION =
        "CREATE TABLE IF NOT EXISTS `charge_session` (`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, " +
            "`ongoing` INTEGER NOT NULL, `plugType` TEXT NOT NULL, `startLevel` INTEGER NOT NULL, " +
            "`endLevel` INTEGER NOT NULL, `addedMah` REAL, `avgCurrentMa` REAL, `peakCurrentMa` REAL, " +
            "`sustainedPowerW` REAL, `avgTempC` REAL, `peakTempC` REAL, `timeTo80Ms` INTEGER, " +
            "`heldAtFullMs` INTEGER NOT NULL, `sampleCount` INTEGER NOT NULL, `speed` TEXT NOT NULL, " +
            "PRIMARY KEY(`startMs`))"

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_CHARGE_SESSION)
        }
    }
}
