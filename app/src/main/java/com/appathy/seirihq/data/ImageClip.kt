package com.appathy.seirihq.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ImageClip {

    /**
     * 素材をクリップボードへ入れる。他アプリに貼り付けられるよう、
     * いったんキャッシュへ複製して FileProvider のURIとして渡す。
     */
    fun copy(context: Context, item: MediaItem): Result<Unit> = runCatching {
        val resolver = context.contentResolver
        val dir = File(context.cacheDir, "share")
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { old -> old.delete() }

        val ext = when {
            item.name.contains('.') -> item.name.substringAfterLast('.')
            item.kind == MEDIA_VIDEO -> "mp4"
            else -> "jpg"
        }
        val out = File(dir, "clip_${System.currentTimeMillis()}.$ext")
        val input = resolver.openInputStream(Uri.parse(item.uri)) ?: error("素材を読み込めません")
        input.use { stream ->
            FileOutputStream(out).use { stream.copyTo(it) }
        }

        val shared = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            out
        )
        val clip = ClipData.newUri(resolver, item.name, shared)
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
    }
}
