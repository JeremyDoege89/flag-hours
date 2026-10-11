package com.jeremy.flaghours.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RepairOrder::class, Job::class, Shift::class, HourlyEntry::class, JobTemplate::class, PayStub::class,
        OlpVehicle::class, OlpVehicleAlias::class, LaborRowEntity::class, OlpCatalog::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = FlagHoursDatabase.V1ToV2::class)],
)
@TypeConverters(Converters::class)
abstract class FlagHoursDatabase : RoomDatabase() {
    abstract fun repairOrders(): RepairOrderDao
    abstract fun jobs(): JobDao
    abstract fun shifts(): ShiftDao
    abstract fun hourly(): HourlyDao
    abstract fun templates(): JobTemplateDao
    abstract fun stubs(): PayStubDao
    abstract fun olp(): OlpDao

    /** v2: labor lookup rework. The old per-job cache table is only a cache (never user data), so it is dropped. */
    @DeleteTable(tableName = "labor_ref_cache")
    class V1ToV2 : AutoMigrationSpec

    companion object {
        fun build(context: Context): FlagHoursDatabase =
            Room.databaseBuilder(context, FlagHoursDatabase::class.java, "flag-hours.db").build()
    }
}
