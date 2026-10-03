package com.joeydman.vitals.data

import android.os.Build
import com.joeydman.vitals.core.battery.BatteryHealth
import com.joeydman.vitals.core.battery.CapacityEstimator
import com.joeydman.vitals.core.battery.DeviceProfiles
import com.joeydman.vitals.core.retention.Downsampler
import com.joeydman.vitals.core.usage.ForegroundTimeCalculator
import com.joeydman.vitals.sensors.BatteryReader
import com.joeydman.vitals.sensors.CpuFreqReader
import com.joeydman.vitals.sensors.MemoryReader
import com.joeydman.vitals.sensors.ThermalReader
import com.joeydman.vitals.sensors.UsageReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.Writer
import java.time.ZoneId

class MetricsRepository(
    private val db: VitalsDatabase,
    private val settings: SettingsStore,
    val battery: BatteryReader,
    val memory: MemoryReader,
    val thermal: ThermalReader,
    val cpu: CpuFreqReader,
    val usage: UsageReader,
) {
    private val estimator = CapacityEstimator()

    /** Called from the periodic worker. Everything here is local I/O only. */
    suspend fun captureSnapshot(nowMs: Long = System.currentTimeMillis()) {
        battery.read(nowMs)?.let { db.batteryDao().insert(BatterySampleEntity.from(it)) }

        val mem = memory.read()
        val cpuFreq = cpu.read()
        db.systemDao().insert(
            SystemSampleEntity(
                timestampMs = nowMs,
                availMemBytes = mem.availBytes,
                totalMemBytes = mem.totalBytes,
                lowMemory = mem.lowMemory,
                thermalStatus = thermal.status().platformValue,
                thermalHeadroom = thermal.headroom(),
                avgCpuFreqKhz = cpuFreq?.averageCurrentKhz,
                maxCpuFreqKhz = cpuFreq?.maxCurrentKhz,
            ),
        )

        refreshUsageToday(nowMs)
        updateDailyCapacityEstimate(nowMs)
        prune(nowMs)
    }

    /** Recomputes today's per-app totals from the raw event stream. Safe to call any time. */
    suspend fun refreshUsageToday(nowMs: Long = System.currentTimeMillis()) {
        if (!usage.hasPermission()) return
        val dayStart = startOfDay(nowMs)
        val rows = ForegroundTimeCalculator.compute(usage.events(dayStart, nowMs), dayStart, nowMs)
            .map { AppUsageDayEntity(dayStart, it.packageName, it.foregroundMs, it.launches) }
        if (rows.isNotEmpty()) db.usageDao().upsertAll(rows)
    }

    private suspend fun updateDailyCapacityEstimate(nowMs: Long) {
        // A week of samples gives several charge runs; the median is stable day to day.
        val samples = db.batteryDao().since(nowMs - 7 * Downsampler.DAY_MS).map { it.toModel() }
        val mah = estimator.bestEstimateMah(samples) ?: return
        db.capacityDao().upsert(
            CapacityEstimateEntity(
                dayStartMs = startOfDay(nowMs),
                capacityMah = mah,
                sampleCount = samples.size,
                cycleCount = samples.lastOrNull()?.cycleCount,
            ),
        )
    }

    private suspend fun prune(nowMs: Long) {
        val cutoff = nowMs - RAW_RETENTION_DAYS * Downsampler.DAY_MS
        db.batteryDao().deleteBefore(cutoff)
        db.systemDao().deleteBefore(cutoff)
    }

    suspend fun designCapacityMah(): Int? =
        settings.designCapacityOverrideNow() ?: DeviceProfiles.designCapacityFor(Build.MODEL)

    val designCapacityFlow: Flow<Int?> = settings.designCapacityOverride

    suspend fun setDesignCapacity(mah: Int?) = settings.setDesignCapacityOverride(mah)

    fun batteryHistory(sinceMs: Long) = db.batteryDao().observeSince(sinceMs)
    fun systemHistory(sinceMs: Long) = db.systemDao().observeSince(sinceMs)
    fun usageToday() = db.usageDao().observeDay(startOfDay(System.currentTimeMillis()))
    fun capacityHistory() = db.capacityDao().observeAll()

    fun healthFlow(): Flow<BatteryHealth?> =
        combine(capacityHistory(), designCapacityFlow) { history, override ->
            val design = override ?: DeviceProfiles.designCapacityFor(Build.MODEL)
            val latest = history.lastOrNull()
            if (design == null || latest == null) null else BatteryHealth(latest.capacityMah, design)
        }

    suspend fun latestHealth(): BatteryHealth? {
        val design = designCapacityMah() ?: return null
        val latest = db.capacityDao().latest() ?: return null
        return BatteryHealth(latest.capacityMah, design)
    }

    /** Plain CSV via the Storage Access Framework. The user chooses where it goes. */
    suspend fun exportBatteryCsv(out: Writer) {
        out.appendLine("timestamp_ms,level_pct,charge_counter_raw,current_now_raw,voltage_mv,temp_deci_c,status,plug,cycle_count")
        db.batteryDao().all().forEach { s ->
            out.appendLine(
                listOf(
                    s.timestampMs, s.levelPercent, s.chargeCounterRaw ?: "", s.currentNowRaw ?: "",
                    s.voltageMv ?: "", s.temperatureDeciC ?: "", s.status, s.plugType, s.cycleCount ?: "",
                ).joinToString(","),
            )
        }
        out.flush()
    }

    companion object {
        const val RAW_RETENTION_DAYS = 30L

        fun startOfDay(ms: Long): Long {
            val zone = ZoneId.systemDefault()
            val date = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            return date.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
}
