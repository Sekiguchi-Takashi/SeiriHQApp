package com.appathy.seirihq.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object PhotoStore {

    fun charaDir(context: Context, charaId: Long): File {
        val dir = File(File(context.filesDir, "library"), charaId.toString())
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (maxEdge <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxEdge && h / 2 >= maxEdge) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun rotate(context: Context, source: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(source)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * 端末の画像を圧縮してアプリ内へ取り込む。原本には触れない。
     * maxEdge が 0 なら縮小せず、品質だけ落とす。
     */
    fun importPhoto(
        context: Context,
        charaId: Long,
        source: Uri,
        displayName: String,
        maxEdge: Int,
        quality: Int
    ): Result<Pair<String, Long>> = runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("画像を読み込めません")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("画像を復号できません")

        val turned = rotate(context, source, decoded)

        val longEdge = maxOf(turned.width, turned.height)
        val scaled = if (maxEdge > 0 && longEdge > maxEdge) {
            val ratio = maxEdge.toFloat() / longEdge
            Bitmap.createScaledBitmap(
                turned,
                (turned.width * ratio).toInt().coerceAtLeast(1),
                (turned.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            turned
        }

        val stem = displayName.substringBeforeLast('.', displayName).take(40)
        val file = File(charaDir(context, charaId), "${System.currentTimeMillis()}_$stem.jpg")
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

        if (scaled !== turned) scaled.recycle()
        if (turned !== decoded) turned.recycle()
        decoded.recycle()

        file.absolutePath to file.length()
    }

    fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    /** アプリ内の写真を、選んだフォルダ（クラウドの同期先など）へ複製する。 */
    fun backup(
        context: Context,
        treeUri: String?,
        groupName: String,
        items: List<Pair<String, Photo>>,
        onProgress: (Int) -> Unit = {}
    ): Result<Int> = runCatching {
        if (treeUri.isNullOrEmpty()) error("バックアップ先が未設定です")
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: error("バックアップ先を開けません")
        if (!root.canWrite()) error("バックアップ先に書き込めません")

        val groupDir = root.findFile(groupName)?.takeIf { it.isDirectory }
            ?: root.createDirectory(groupName)
            ?: error("フォルダを作成できません")

        var copied = 0
        val dirs = HashMap<String, DocumentFile>()
        items.forEach { entry ->
            val charaName = entry.first
            val photo = entry.second
            val dir = dirs.getOrPut(charaName) {
                groupDir.findFile(charaName)?.takeIf { it.isDirectory }
                    ?: groupDir.createDirectory(charaName)
                    ?: error("フォルダを作成できません")
            }
            val fileName = File(photo.path).name
            if (dir.findFile(fileName) == null) {
                val target = dir.createFile("image/jpeg", fileName)
                    ?: error("ファイルを作成できません")
                File(photo.path).inputStream().use { input ->
                    context.contentResolver.openOutputStream(target.uri)?.use { output ->
                        input.copyTo(output)
                    } ?: error("書き込めません")
                }
                copied++
                onProgress(copied)
            }
        }
        copied
    }

    fun formatSize(bytes: Long): String = FolderCleaner.formatSize(bytes)
}
