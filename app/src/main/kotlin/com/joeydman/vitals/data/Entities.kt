package com.joeydman.vitals.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.joeydman.vitals.core.battery.ChargeSession
import com.joeydman.vitals.core.battery.ChargeSpeed
import com.joeydman.vitals.core.model.BatterySample
import com.joeydman.vitals.core.model.ChargeStatus
import com.joeydman.vitals.core.model.PlugType

@Entity(tableName = "battery_sample", indices = [Index("timestampMs")])
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val levelPercent: Int,
    val chargeCounterRaw: Long?,
    val currentNowRaw: Long?,
    val voltageMv: Int?,
    val temperatureDeciC: Int?,
    val status: String,
    val plugType: String,
    val cycleCount: Int?,
) {
    fun toModel() = BatterySample(
        timestampMs = timestampMs,
        levelPercent = levelPercent,
        chargeCounterRaw = chargeCounterRaw,
        currentNowRaw = currentNowRaw,
        voltageMv = voltageMv,
        temperatureDeciC = temperatureDeciC,
        status = runCatching { ChargeStatus.valueOf(status) }.getOrDefault(ChargeStatus.UNKNOWN),
        plugType = runCatching { PlugType.valueOf(plugType) }.getOrDefault(PlugType.NONE),
        cycleCount = cycleCount,
    )

    companion object {
        fun from(s: BatterySample) = BatterySampleEntity(
            timestampMs = s.timestampMs,
            levelPercent = s.levelPercent,
            chargeCounterRaw = s.chargeCounterRaw,
            currentNowRaw = s.currentNowRaw,
            voltageMv = s.voltageMv,
            temperatureDeciC = s.temperatureDeciC,
            status = s.status.name,
            plugType = s.plugType.name,
            cycleCount = s.cycleCount,
        )
    }
}

@Entity(tableName = "system_sample", indices = [Index("timestampMs")])
data class SystemSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val availMemBytes: Long,
    val totalMemBytes: Long,
    val lowMemory: Boolean,
    val thermalStatus: Int,
    /** 0.0 to 1.0+, null when the device does not report headroom. */
    val thermalHeadroom: Float?,
    /** Mean current frequency across readable cores, null when sysfs is blocked. */
    val avgCpuFreqKhz: Long?,
    val maxCpuFreqKhz: Long?,
)

/** Foreground time per app per local calendar day. Upserted on every sampling run. */
@Entity(tableName = "app_usage_day", primaryKeys = ["dayStartMs", "packageName"])
data class AppUsageDayEntity(
    val dayStartMs: Long,
    val packageName: String,
    val foregroundMs: Long,
    val launches: Int,
)

/** One capacity estimate per day. Kept forever: this is the degradation history. */
@Entity(tableName = "capacity_estimate")
data class CapacityEstimateEntity(
    @PrimaryKey val dayStartMs: Long,
    val capacityMah: Double,
    val sampleCount: Int,
    val cycleCount: Int?,
)

/** Summary of one plug-in. Kept forever; raw samples behind it are pruned after 30 days. */
@Entity(tableName = "charge_session")
data class ChargeSessionEntity(
    @PrimaryKey val startMs: Long,
    val endMs: Long,
    val ongoing: Boolean,
    val plugType: String,
    val startLevel: Int,
    val endLevel: Int,
    val addedMah: Double?,
    val avgCurrentMa: Double?,
    val peakCurrentMa: Double?,
    val sustainedPowerW: Double?,
    val avgTempC: Double?,
    val peakTempC: Double?,
    val timeTo80Ms: Long?,
    val heldAtFullMs: Long,
    val sampleCount: Int,
    val speed: String,
) {
    fun toModel() = ChargeSession(
        startMs = startMs,
        endMs = endMs,
        ongoing = ongoing,
        plugType = runCatching { PlugType.valueOf(plugType) }.getOrDefault(PlugType.NONE),
        startLevel = startLevel,
        endLevel = endLevel,
        addedMah = addedMah,
        avgCurrentMa = avgCurrentMa,
        peakCurrentMa = peakCurrentMa,
        sustainedPowerW = sustainedPowerW,
        avgTempC = avgTempC,
        peakTempC = peakTempC,
        timeTo80Ms = timeTo80Ms,
        heldAtFullMs = heldAtFullMs,
        sampleCount = sampleCount,
        speed = runCatching { ChargeSpeed.valueOf(speed) }.getOrDefault(ChargeSpeed.UNKNOWN),
    )

    companion object {
        fun from(s: ChargeSession) = ChargeSessionEntity(
            startMs = s.startMs,
            endMs = s.endMs,
            ongoing = s.ongoing,
            plugType = s.plugType.name,
            startLevel = s.startLevel,
            endLevel = s.endLevel,
            addedMah = s.addedMah,
            avgCurrentMa = s.avgCurrentMa,
            peakCurrentMa = s.peakCurrentMa,
            sustainedPowerW = s.sustainedPowerW,
            avgTempC = s.avgTempC,
            peakTempC = s.peakTempC,
            timeTo80Ms = s.timeTo80Ms,
            heldAtFullMs = s.heldAtFullMs,
            sampleCount = s.sampleCount,
            speed = s.speed.name,
        )
    }
}
