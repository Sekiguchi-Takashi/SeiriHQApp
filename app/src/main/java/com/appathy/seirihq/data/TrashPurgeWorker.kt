package com.appathy.seirihq.data

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** 保持期間を過ぎたゴミ箱の中身を、アプリを開かなくても消すための定期処理。 */
class TrashPurgeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val repo = Repository(applicationContext)
        val now = System.currentTimeMillis()
        repo.trash().filter { it.expireAt <= now }.forEach { item ->
            TrashFiles.deleteFile(applicationContext, item.uri)
            repo.deleteTrashRow(item.id)
        }
        return Result.success()
    }
}
