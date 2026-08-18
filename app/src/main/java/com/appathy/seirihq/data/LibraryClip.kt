package com.appathy.seirihq.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

object LibraryClip {

    /** アプリ内に保存した写真をクリップボードへ入れる。 */
    fun copy(context: Context, photo: Photo): Result<Unit> = runCatching {
        val source = File(photo.path)
        if (!source.exists()) error("ファイルが見つかりません")
        val dir = File(context.cacheDir, "share")
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { old -> old.delete() }
        val copy = File(dir, source.name)
        source.copyTo(copy, overwrite = true)

        val shared = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            copy
        )
        val clip = ClipData.newUri(context.contentResolver, photo.name, shared)
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
    }
}
