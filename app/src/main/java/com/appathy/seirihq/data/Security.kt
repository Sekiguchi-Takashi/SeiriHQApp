package com.appathy.seirihq.data

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Passcode {

    private const val ITERATIONS = 20000
    private const val KEY_LENGTH = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            Base64.getDecoder().decode(salt),
            ITERATIONS,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.getEncoder().encodeToString(factory.generateSecret(spec).encoded)
    }
}

object FilePermission {

    fun required(): List<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )

            else -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    fun granted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** 端末の原本を削除できる環境かどうか。Android 10 のみ非対応。 */
    fun canDeleteOriginals(context: Context): Boolean {
        if (!granted(context)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return true
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    }
}

sealed class DeleteOutcome {
    data object Done : DeleteOutcome()
    data class NeedsConfirm(val intent: PendingIntent) : DeleteOutcome()
    data class Unsupported(val reason: String) : DeleteOutcome()
}

object MediaFiles {

    /** 端末から原本を削除する。API 30 以上ではシステムの確認画面が必要。 */
    fun deleteOriginal(context: Context, item: MediaItem): DeleteOutcome {
        val uri = Uri.parse(item.uri)
        val resolver = context.contentResolver

        if (uri.scheme == "file") {
            val path = uri.path
            val ok = path != null && java.io.File(path).delete()
            return if (ok) DeleteOutcome.Done
            else DeleteOutcome.Unsupported("削除できませんでした")
        }

        if (item.source == SOURCE_SAF || item.source == SOURCE_TRASH) {
            if (!item.writable) {
                return DeleteOutcome.Unsupported("この素材には書き込み権限がありません")
            }
            val ok = runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                .getOrDefault(false)
            return if (ok) DeleteOutcome.Done
            else DeleteOutcome.Unsupported("提供元が削除に対応していません")
        }

        if (!FilePermission.granted(context)) {
            return DeleteOutcome.Unsupported("ファイル権限がありません")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = MediaStore.createDeleteRequest(resolver, listOf(uri))
            return DeleteOutcome.NeedsConfirm(pending)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val count = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
            return if (count > 0) DeleteOutcome.Done
            else DeleteOutcome.Unsupported("削除できませんでした")
        }

        return DeleteOutcome.Unsupported("Android 10 では原本削除に対応していません")
    }

    /** Android 11以上で、複数のMediaStore素材をまとめて削除する確認画面を作る。 */
    fun bulkDeleteRequest(context: Context, items: List<MediaItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (!FilePermission.granted(context)) return null
        val uris = items.map { Uri.parse(it.uri) }
        if (uris.isEmpty()) return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        }.getOrNull()
    }

    /** 権限がある場合に、端末内の画像・動画を新しい順に取り込む。 */
    fun scanRecent(context: Context, limit: Int): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        if (!FilePermission.granted(context)) return out

        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val order = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val cursor = runCatching {
            context.contentResolver.query(collection, projection, selection, args, order)
        }.getOrNull() ?: return out

        cursor.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val typeIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (it.moveToNext() && out.size < limit) {
                val id = it.getLong(idIndex)
                val isVideo = it.getInt(typeIndex) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val uri = ContentUris.withAppendedId(base, id)
                val name = it.getString(nameIndex) ?: "media"
                out.add(Triple(uri.toString(), if (isVideo) MEDIA_VIDEO else MEDIA_IMAGE, name))
            }
        }
        return out
    }
}
