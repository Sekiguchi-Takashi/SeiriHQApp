package com.appathy.seirihq.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class FileEntry(
    val uri: String,
    val name: String,
    val mime: String,
    val size: Long,
    val modifiedAt: Long
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val isInstaller: Boolean
        get() = extension == "apk" || extension == "apks" || extension == "xapk"

    val isArchive: Boolean
        get() = extension in setOf("zip", "rar", "7z", "tar", "gz")

    val isImage: Boolean
        get() = mime.startsWith("image")

    val isVideo: Boolean
        get() = mime.startsWith("video")
}

object FolderCleaner {

    /** 選んだフォルダ直下のファイルを、サイズの大きい順に返す。 */
    fun list(context: Context, treeUri: String?): List<FileEntry> {
        if (treeUri.isNullOrEmpty()) return emptyList()
        val tree = Uri.parse(treeUri)
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val out = ArrayList<FileEntry>()
        val cursor = runCatching {
            context.contentResolver.query(children, projection, null, null, null)
        }.getOrNull() ?: return out

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val mime = it.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                out.add(
                    FileEntry(
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                        name = it.getString(1) ?: id,
                        mime = mime,
                        size = if (it.isNull(3)) 0L else it.getLong(3),
                        modifiedAt = if (it.isNull(4)) 0L else it.getLong(4)
                    )
                )
            }
        }
        return out.sortedByDescending { entry -> entry.size }
    }

    /** ゴミ箱を経由せず、その場で削除する。 */
    fun deleteNow(context: Context, uris: List<String>): Int {
        var deleted = 0
        uris.forEach { uri ->
            val ok = runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri))
            }.getOrDefault(false)
            if (ok) deleted++
        }
        return deleted
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) "${value.toInt()} ${units[index]}"
        else String.format("%.1f %s", value, units[index])
    }
}
