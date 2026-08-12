package com.appathy.seirihq

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.appathy.seirihq.data.TrashPurgeWorker
import java.util.concurrent.TimeUnit

class SeiriApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "trash-purge",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
