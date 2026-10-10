package com.jeremy.flaghours.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RepairOrder::class, Job::class, Shift::class, HourlyEntry::class, JobTemplate::class, PayStub::class, LaborRefCache::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FlagHoursDatabase : RoomDatabase() {
    abstract fun repairOrders(): RepairOrderDao
    abstract fun jobs(): JobDao
    abstract fun shifts(): ShiftDao
    abstract fun hourly(): HourlyDao
    abstract fun templates(): JobTemplateDao
    abstract fun stubs(): PayStubDao
    abstract fun laborRefs(): LaborRefDao

    companion object {
        fun build(context: Context): FlagHoursDatabase =
            Room.databaseBuilder(context, FlagHoursDatabase::class.java, "flag-hours.db").build()
    }
}
