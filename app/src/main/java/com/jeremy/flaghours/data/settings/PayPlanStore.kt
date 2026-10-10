package com.jeremy.flaghours.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jeremy.flaghours.domain.PayPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

val PlanJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object PayPlanSerializer : Serializer<PayPlan> {
    override val defaultValue: PayPlan = PayPlan()

    override suspend fun readFrom(input: InputStream): PayPlan = try {
        PlanJson.decodeFromString(PayPlan.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        defaultValue
    }

    override suspend fun writeTo(t: PayPlan, output: OutputStream) {
        output.write(PlanJson.encodeToString(PayPlan.serializer(), t).encodeToByteArray())
    }
}

class PayPlanStore(private val store: DataStore<PayPlan>) {
    val plan: Flow<PayPlan> = store.data

    suspend fun current(): PayPlan = store.data.first()

    suspend fun save(plan: PayPlan) {
        store.updateData { plan }
    }

    companion object {
        fun create(context: Context): PayPlanStore = PayPlanStore(
            DataStoreFactory.create(
                serializer = PayPlanSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { PayPlan() },
                produceFile = { File(context.filesDir, "datastore/pay_plan.json") },
            ),
        )
    }
}
