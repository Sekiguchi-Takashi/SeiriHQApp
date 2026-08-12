package com.appathy.seirihq.data

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipMaker {

    private fun uniqueName(raw: String, used: MutableSet<String>): String {
        val base = raw.ifBlank { "file" }
        if (used.add(base)) return base
        val stem = base.substringBeforeLast('.', base)
        val ext = base.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "${stem}_$index" else "${stem}_$index.$ext"
            if (used.add(candidate)) return candidate
            index++
        }
    }

    /**
     * 選んだ素材を1つのZIPにまとめて、指定された出力先へ書き出す。
     * 出力先はSAFで選ぶため、クラウドのフォルダも指定できる。
     */
    fun zip(context: Context, items: List<MediaItem>, destination: Uri): Result<Int> {
        val resolver = context.contentResolver
        return runCatching {
            var count = 0
            val output = resolver.openOutputStream(destination) ?: error("出力先を開けません")
            output.use { raw ->
                ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                    val used = HashSet<String>()
                    items.forEach { item ->
                        val input = runCatching {
                            resolver.openInputStream(Uri.parse(item.uri))
                        }.getOrNull()
                        if (input != null) {
                            input.use { stream ->
                                zip.putNextEntry(ZipEntry(uniqueName(item.name, used)))
                                stream.copyTo(zip)
                                zip.closeEntry()
                            }
                            count++
                        }
                    }
                }
            }
            if (count == 0) error("読み込める素材がありませんでした")
            count
        }
    }
}
