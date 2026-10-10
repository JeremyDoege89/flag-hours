package com.jeremy.flaghours

import android.app.Application
import com.jeremy.flaghours.data.FlagHoursRepository
import com.jeremy.flaghours.data.db.FlagHoursDatabase
import com.jeremy.flaghours.data.settings.PayPlanStore
import com.jeremy.flaghours.data.settings.SecretStore

/** Manual dependency container. */
class FlagHoursApp : Application() {
    val database by lazy { FlagHoursDatabase.build(this) }
    val planStore by lazy { PayPlanStore.create(this) }
    val secrets by lazy { SecretStore(this) }
    val repository by lazy { FlagHoursRepository(database, planStore) }
}
