package com.appathy.seirihq.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

object TrashFiles {

    fun appTrashDir(context: Context): File {
        val dir = File(context.filesDir, "trash")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._\\-]"), "_")
        return "${System.currentTimeMillis()}_$cleaned"
    }

    /**
     * 原本をゴミ箱へコピーする。treeUri があればそのフォルダ（クラウド連携先を含む）、
     * なければアプリ内領域へ保存する。
     */
    fun copyToTrash(context: Context, item: MediaItem, treeUri: String?): Result<String> {
        val resolver = context.contentResolver
        val src = Uri.parse(item.uri)
        val name = safeName(item.name)

        return runCatching {
            if (treeUri.isNullOrEmpty()) {
                val out = File(appTrashDir(context), name)
                val input = resolver.openInputStream(src) ?: error("素材を読み込めません")
                input.use { stream ->
                    FileOutputStream(out).use { stream.copyTo(it) }
                }
                Uri.fromFile(out).toString()
            } else {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                    ?: error("ゴミ箱フォルダを開けません")
                if (!tree.canWrite()) error("ゴミ箱フォルダに書き込めません")
                val mime = resolver.getType(src)
                    ?: if (item.kind == MEDIA_VIDEO) "video/*" else "image/*"
                val doc = tree.createFile(mime, name) ?: error("ゴミ箱に作成できません")
                val input = resolver.openInputStream(src) ?: error("素材を読み込めません")
                input.use { stream ->
                    val output = resolver.openOutputStream(doc.uri) ?: error("書き込めません")
                    output.use { stream.copyTo(it) }
                }
                doc.uri.toString()
            }
        }
    }

    /** ゴミ箱の実ファイルを完全に削除する。 */
    fun deleteFile(context: Context, uri: String): Boolean {
        val target = Uri.parse(uri)
        if (target.scheme == "file") {
            val path = target.path ?: return false
            return File(path).delete()
        }
        return runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, target)
        }.getOrDefault(false)
    }
}
