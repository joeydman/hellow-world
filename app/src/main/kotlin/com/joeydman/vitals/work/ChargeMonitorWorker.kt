package com.joeydman.vitals.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.joeydman.vitals.container
import com.joeydman.vitals.widget.VitalsWidget
import java.util.concurrent.TimeUnit

/**
 * Samples the battery once a minute while the phone is charging, by re-enqueueing itself.
 *
 * The "requires charging" constraint makes the pending link in the chain double as a plug-in
 * detector: it waits while unplugged and fires as soon as charging starts. Android lifts
 * Doze and app-standby limits while charging, so this runs reliably and costs no battery.
 * When charging is paused (e.g. One UI "Protect battery" holding at 80%), the 15-minute
 * SamplingWorker keeps the session going.
 */
class ChargeMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching {
            applicationContext.container.repository.captureBattery()
            VitalsWidget().updateAll(applicationContext)
        }
        enqueue(applicationContext, delaySeconds = INTERVAL_SECONDS, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val NAME = "vitals-charge-monitor"
        private const val INTERVAL_SECONDS = 60L

        /** Safe to call often: keeps an existing pending link instead of starting a second chain. */
        fun ensureScheduled(context: Context) = enqueue(context, delaySeconds = 0, policy = ExistingWorkPolicy.KEEP)

        private fun enqueue(context: Context, delaySeconds: Long, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<ChargeMonitorWorker>()
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }
    }
}
