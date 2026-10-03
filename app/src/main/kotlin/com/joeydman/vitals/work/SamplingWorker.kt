package com.joeydman.vitals.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.joeydman.vitals.container
import com.joeydman.vitals.widget.VitalsWidget
import java.util.concurrent.TimeUnit

/**
 * Captures one snapshot of every metric and refreshes widgets. 15 minutes is the
 * WorkManager minimum. Samsung may still defer runs unless the app's battery setting
 * is "Unrestricted"; the Settings screen walks the user through that.
 */
class SamplingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        applicationContext.container.repository.captureSnapshot()
        VitalsWidget().updateAll(applicationContext)
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object {
        private const val NAME = "vitals-sampling"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SamplingWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
