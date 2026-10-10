package com.jeremy.flaghours.data.db

import androidx.room.TypeConverter
import com.jeremy.flaghours.domain.JobStatus
import com.jeremy.flaghours.domain.LaborType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let { LocalDate.ofEpochDay(it) }

    /** Wall-clock time stored as seconds, treating local time as UTC so it never shifts with time zones. */
    @TypeConverter fun dateTimeToLong(d: LocalDateTime?): Long? = d?.toEpochSecond(ZoneOffset.UTC)
    @TypeConverter fun longToDateTime(v: Long?): LocalDateTime? = v?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    @TypeConverter fun laborTypeToString(t: LaborType): String = t.name
    @TypeConverter fun stringToLaborType(s: String): LaborType = LaborType.valueOf(s)

    @TypeConverter fun statusToString(s: JobStatus): String = s.name
    @TypeConverter fun stringToStatus(s: String): JobStatus = JobStatus.valueOf(s)
}
