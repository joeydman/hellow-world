package com.joeydman.vitals

import android.app.Application
import android.content.Context
import com.joeydman.vitals.data.MetricsRepository
import com.joeydman.vitals.data.SettingsStore
import com.joeydman.vitals.data.VitalsDatabase
import com.joeydman.vitals.sensors.BatteryReader
import com.joeydman.vitals.sensors.CpuFreqReader
import com.joeydman.vitals.sensors.MemoryReader
import com.joeydman.vitals.sensors.ThermalReader
import com.joeydman.vitals.sensors.UsageReader
import com.joeydman.vitals.work.SamplingWorker

class VitalsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SamplingWorker.schedule(this)
    }
}

/** Manual dependency container. Small enough that a DI framework would add more than it saves. */
class AppContainer(context: Context) {
    private val db = VitalsDatabase.create(context)
    val repository = MetricsRepository(
        db = db,
        settings = SettingsStore(context),
        battery = BatteryReader(context),
        memory = MemoryReader(context),
        thermal = ThermalReader(context),
        cpu = CpuFreqReader(),
        usage = UsageReader(context),
    )
}

val Context.container: AppContainer get() = (applicationContext as VitalsApp).container
