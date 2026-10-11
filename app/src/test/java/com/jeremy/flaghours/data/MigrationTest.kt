package com.jeremy.flaghours.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.jeremy.flaghours.data.db.FlagHoursDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FlagHoursDatabase::class.java)

    @Test fun v1ToV2KeepsUserData() {
        helper.createDatabase("mig.db", 1).use { db ->
            db.execSQL("INSERT INTO repair_orders (id, roNumber, openedDate, make, model, year) VALUES (1, '4471', 20367, 'Ford', 'F-150', 2017)")
            db.execSQL(
                "INSERT INTO jobs (id, roId, description, laborType, flaggedHundredths, mySplitPct, workDate, status, refLowHundredths) " +
                    "VALUES (10, 1, 'Radiator', 'CUSTOMER', 210, 100, 20367, 'PAID', 180)",
            )
            db.execSQL("INSERT INTO job_templates (id, description, flaggedHundredths, timesUsed) VALUES (1, 'Radiator', 210, 3)")
            db.execSQL("INSERT INTO shifts (id, start, `end`) VALUES (1, 1000, 2000)")
            db.execSQL("INSERT INTO labor_ref_cache (make, model, year, engine, jobSlug, job, fetchedAt) VALUES ('ford','f-150',2017,'','radiator','Radiator',0)")
        }
        helper.runMigrationsAndValidate("mig.db", 2, true).use { db ->
            db.query("SELECT roNumber FROM repair_orders").use { c -> c.moveToFirst(); assertEquals("4471", c.getString(0)) }
            db.query("SELECT flaggedHundredths, refLowHundredths, refJob FROM jobs WHERE id = 10").use { c ->
                c.moveToFirst()
                assertEquals(210, c.getInt(0))
                assertEquals(180, c.getInt(1))
                assertEquals(true, c.isNull(2))
            }
            db.query("SELECT timesUsed, refJobSlug FROM job_templates").use { c -> c.moveToFirst(); assertEquals(3, c.getInt(0)); assertEquals(true, c.isNull(1)) }
            db.query("SELECT COUNT(*) FROM shifts").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM labor_rows").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT name FROM sqlite_master WHERE name = 'labor_ref_cache'").use { c -> assertEquals(0, c.count) }
        }
    }
}
