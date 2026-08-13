package com.appathy.seirihq.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

const val KIND_FIXED = "fixed"
const val KIND_TEMP = "temp"

const val MAX_FIXED = 30
const val MAX_TEMP = 20

const val KEY_ACTIVE_FIXED = "active_fixed"
const val KEY_ACTIVE_TEMP = "active_temp"
const val KEY_PIN_HASH = "pin_hash"
const val KEY_PIN_SALT = "pin_salt"
const val KEY_BIOMETRIC = "biometric_enabled"
const val KEY_AUTH_ON_DELETE = "auth_on_delete"

const val MEDIA_IMAGE = "image"
const val MEDIA_VIDEO = "video"

const val SOURCE_SAF = "saf"
const val SOURCE_STORE = "mediastore"
const val SOURCE_TRASH = "trash"

const val TAG_USER = "user"
const val TAG_AI = "ai"
const val TAG_SYSTEM = "system"

const val KEY_TRASH_TREE = "trash_tree"
const val KEY_CLEAN_TREE = "clean_tree"
const val KEY_LAST_PROMPT = "last_prompt"
const val KEY_LINK_IMPORTS = "link_imports"
const val KEY_PINNED_ONLY = "pinned_only"
const val KEY_USE_TRASH = "use_trash"
const val KEY_RETENTION = "retention_days"
const val DEFAULT_RETENTION_DAYS = 14

val MEDIA_STATUSES = listOf("未整理", "整理済み", "保留", "アーカイブ", "削除候補")

data class PromptItem(
    val id: Long,
    val kind: String,
    val name: String,
    val description: String,
    val body: String,
    val updatedAt: Long
)

data class Project(
    val id: Long,
    val name: String
)

data class MediaTag(
    val tagId: Long,
    val name: String,
    val kind: String,
    val confirmed: Boolean
)

data class MediaItem(
    val id: Long,
    val uri: String,
    val kind: String,
    val name: String,
    val status: String,
    val tags: List<MediaTag>,
    val addedAt: Long,
    val projectIds: List<Long>,
    val source: String,
    val writable: Boolean,
    val sourcePromptId: Long,
    val pinned: Boolean
)

data class TrashItem(
    val id: Long,
    val name: String,
    val kind: String,
    val uri: String,
    val tags: String,
    val deletedAt: Long,
    val expireAt: Long
)

class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "seirihq.db", null, 6) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE prompt(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "kind TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "description TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE project(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE media(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uri TEXT NOT NULL UNIQUE," +
                "kind TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "status TEXT NOT NULL," +
                "tags TEXT NOT NULL DEFAULT ''," +
                "added_at INTEGER NOT NULL," +
                "source TEXT NOT NULL DEFAULT 'saf'," +
                "writable INTEGER NOT NULL DEFAULT 0," +
                "source_prompt_id INTEGER NOT NULL DEFAULT 0," +
                "pinned INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE trash(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "kind TEXT NOT NULL," +
                "uri TEXT NOT NULL," +
                "tags TEXT NOT NULL DEFAULT ''," +
                "deleted_at INTEGER NOT NULL," +
                "expire_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE tag(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "kind TEXT NOT NULL," +
                "UNIQUE(name, kind))"
        )
        db.execSQL(
            "CREATE TABLE media_tag(" +
                "media_id INTEGER NOT NULL," +
                "tag_id INTEGER NOT NULL," +
                "confirmed INTEGER NOT NULL DEFAULT 1," +
                "PRIMARY KEY(media_id, tag_id))"
        )
        db.execSQL(
            "CREATE TABLE media_project(" +
                "media_id INTEGER NOT NULL," +
                "project_id INTEGER NOT NULL," +
                "PRIMARY KEY(media_id, project_id))"
        )
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tag(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "kind TEXT NOT NULL," +
                "UNIQUE(name, kind))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS media_tag(" +
                "media_id INTEGER NOT NULL," +
                "tag_id INTEGER NOT NULL," +
                "confirmed INTEGER NOT NULL DEFAULT 1," +
                "PRIMARY KEY(media_id, tag_id))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE media ADD COLUMN source TEXT NOT NULL DEFAULT 'saf'")
            db.execSQL("ALTER TABLE media ADD COLUMN writable INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE media ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tag(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "kind TEXT NOT NULL," +
                    "UNIQUE(name, kind))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS media_tag(" +
                    "media_id INTEGER NOT NULL," +
                    "tag_id INTEGER NOT NULL," +
                    "confirmed INTEGER NOT NULL DEFAULT 1," +
                    "PRIMARY KEY(media_id, tag_id))"
            )
            migrateTags(db)
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE media ADD COLUMN source_prompt_id INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS trash(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "kind TEXT NOT NULL," +
                    "uri TEXT NOT NULL," +
                    "tags TEXT NOT NULL DEFAULT ''," +
                    "deleted_at INTEGER NOT NULL," +
                    "expire_at INTEGER NOT NULL)"
            )
        }
    }
}

/** 旧仕様のカンマ区切りタグを、正規化したテーブルへ移す。 */
private fun migrateTags(db: SQLiteDatabase) {
    val pairs = ArrayList<Pair<Long, String>>()
    val c = db.rawQuery("SELECT id, tags FROM media", null)
    c.use {
        while (it.moveToNext()) {
            val raw = it.getString(1) ?: ""
            if (raw.isNotBlank()) pairs.add(it.getLong(0) to raw)
        }
    }
    pairs.forEach { (mediaId, raw) ->
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
            val tag = ContentValues()
            tag.put("name", name)
            tag.put("kind", TAG_USER)
            db.insertWithOnConflict("tag", null, tag, SQLiteDatabase.CONFLICT_IGNORE)
            val idCursor = db.rawQuery(
                "SELECT id FROM tag WHERE name=? AND kind=?",
                arrayOf(name, TAG_USER)
            )
            var tagId = -1L
            idCursor.use { if (it.moveToFirst()) tagId = it.getLong(0) }
            if (tagId > 0) {
                val link = ContentValues()
                link.put("media_id", mediaId)
                link.put("tag_id", tagId)
                link.put("confirmed", 1)
                db.insertWithOnConflict("media_tag", null, link, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }
}

class Repository(context: Context) {

    private val helper = Db(context)

    private fun readPrompt(c: Cursor): PromptItem = PromptItem(
        id = c.getLong(0),
        kind = c.getString(1),
        name = c.getString(2),
        description = c.getString(3),
        body = c.getString(4),
        updatedAt = c.getLong(5)
    )

    fun prompts(kind: String): List<PromptItem> {
        val out = ArrayList<PromptItem>()
        val db = helper.readableDatabase
        val c = db.rawQuery(
            "SELECT id,kind,name,description,body,updated_at FROM prompt WHERE kind=? ORDER BY id ASC",
            arrayOf(kind)
        )
        c.use { while (it.moveToNext()) out.add(readPrompt(it)) }
        return out
    }

    fun promptCount(kind: String): Int {
        val db = helper.readableDatabase
        val c = db.rawQuery("SELECT COUNT(*) FROM prompt WHERE kind=?", arrayOf(kind))
        c.use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun insertPrompt(kind: String, name: String, description: String, body: String): Long {
        val limit = if (kind == KIND_FIXED) MAX_FIXED else MAX_TEMP
        if (promptCount(kind) >= limit) return -1L
        val now = System.currentTimeMillis()
        val v = ContentValues()
        v.put("kind", kind)
        v.put("name", name)
        v.put("description", description)
        v.put("body", body)
        v.put("created_at", now)
        v.put("updated_at", now)
        return helper.writableDatabase.insert("prompt", null, v)
    }

    fun updatePrompt(id: Long, name: String, description: String, body: String) {
        val v = ContentValues()
        v.put("name", name)
        v.put("description", description)
        v.put("body", body)
        v.put("updated_at", System.currentTimeMillis())
        helper.writableDatabase.update("prompt", v, "id=?", arrayOf(id.toString()))
    }

    fun deletePrompt(id: Long) {
        helper.writableDatabase.delete("prompt", "id=?", arrayOf(id.toString()))
    }

    fun deleteAllTemp() {
        helper.writableDatabase.delete("prompt", "kind=?", arrayOf(KIND_TEMP))
    }

    fun state(key: String): String? {
        val c = helper.readableDatabase.rawQuery("SELECT value FROM state WHERE key=?", arrayOf(key))
        c.use { return if (it.moveToFirst()) it.getString(0) else null }
    }

    fun setState(key: String, value: String) {
        val v = ContentValues()
        v.put("key", key)
        v.put("value", value)
        helper.writableDatabase.insertWithOnConflict(
            "state", null, v, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun projects(): List<Project> {
        val out = ArrayList<Project>()
        val c = helper.readableDatabase.rawQuery("SELECT id,name FROM project ORDER BY id ASC", null)
        c.use { while (it.moveToNext()) out.add(Project(it.getLong(0), it.getString(1))) }
        return out
    }

    fun insertProject(name: String): Long {
        val v = ContentValues()
        v.put("name", name)
        v.put("created_at", System.currentTimeMillis())
        return helper.writableDatabase.insert("project", null, v)
    }

    fun deleteProject(id: Long) {
        val db = helper.writableDatabase
        db.delete("media_project", "project_id=?", arrayOf(id.toString()))
        db.delete("project", "id=?", arrayOf(id.toString()))
    }

    fun insertMedia(
        uri: String,
        kind: String,
        name: String,
        source: String,
        writable: Boolean,
        sourcePromptId: Long = 0L
    ): Long {
        val v = ContentValues()
        v.put("uri", uri)
        v.put("kind", kind)
        v.put("name", name)
        v.put("status", MEDIA_STATUSES[0])
        v.put("tags", "")
        v.put("added_at", System.currentTimeMillis())
        v.put("source", source)
        v.put("writable", if (writable) 1 else 0)
        v.put("source_prompt_id", sourcePromptId)
        return helper.writableDatabase.insertWithOnConflict(
            "media", null, v, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun media(): List<MediaItem> {
        val links = HashMap<Long, MutableList<Long>>()
        val lc = helper.readableDatabase.rawQuery("SELECT media_id,project_id FROM media_project", null)
        lc.use {
            while (it.moveToNext()) {
                val m = it.getLong(0)
                links.getOrPut(m) { ArrayList() }.add(it.getLong(1))
            }
        }
        val tagMap = HashMap<Long, MutableList<MediaTag>>()
        val tc = helper.readableDatabase.rawQuery(
            "SELECT mt.media_id, t.id, t.name, t.kind, mt.confirmed " +
                "FROM media_tag mt JOIN tag t ON t.id = mt.tag_id",
            null
        )
        tc.use {
            while (it.moveToNext()) {
                val mediaId = it.getLong(0)
                tagMap.getOrPut(mediaId) { ArrayList() }.add(
                    MediaTag(
                        tagId = it.getLong(1),
                        name = it.getString(2),
                        kind = it.getString(3),
                        confirmed = it.getInt(4) == 1
                    )
                )
            }
        }
        val out = ArrayList<MediaItem>()
        val c = helper.readableDatabase.rawQuery(
            "SELECT id,uri,kind,name,status,added_at,source,writable,source_prompt_id,pinned " +
                "FROM media " +
                "ORDER BY added_at DESC",
            null
        )
        c.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                out.add(
                    MediaItem(
                        id = id,
                        uri = it.getString(1),
                        kind = it.getString(2),
                        name = it.getString(3),
                        status = it.getString(4),
                        tags = tagMap[id] ?: emptyList(),
                        addedAt = it.getLong(5),
                        projectIds = links[id] ?: emptyList(),
                        source = it.getString(6),
                        writable = it.getInt(7) == 1,
                        sourcePromptId = it.getLong(8),
                        pinned = it.getInt(9) == 1
                    )
                )
            }
        }
        return out
    }

    fun setMediaStatus(id: Long, status: String) {
        val v = ContentValues()
        v.put("status", status)
        helper.writableDatabase.update("media", v, "id=?", arrayOf(id.toString()))
    }

    fun setPinned(id: Long, pinned: Boolean) {
        val v = ContentValues()
        v.put("pinned", if (pinned) 1 else 0)
        helper.writableDatabase.update("media", v, "id=?", arrayOf(id.toString()))
    }

    fun setSourcePrompt(id: Long, promptId: Long) {
        val v = ContentValues()
        v.put("source_prompt_id", promptId)
        helper.writableDatabase.update("media", v, "id=?", arrayOf(id.toString()))
    }

    fun ensureTag(name: String, kind: String): Long {
        val db = helper.writableDatabase
        val v = ContentValues()
        v.put("name", name)
        v.put("kind", kind)
        db.insertWithOnConflict("tag", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        val c = db.rawQuery("SELECT id FROM tag WHERE name=? AND kind=?", arrayOf(name, kind))
        c.use { return if (it.moveToFirst()) it.getLong(0) else -1L }
    }

    fun addTag(mediaId: Long, name: String, kind: String, confirmed: Boolean): Result<Unit> =
        runCatching {
            val tagId = ensureTag(name, kind)
            if (tagId <= 0) error("タグを作成できませんでした")
            val v = ContentValues()
            v.put("media_id", mediaId)
            v.put("tag_id", tagId)
            v.put("confirmed", if (confirmed) 1 else 0)
            helper.writableDatabase.insertWithOnConflict(
                "media_tag", null, v, SQLiteDatabase.CONFLICT_REPLACE
            )
            val check = helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM media_tag WHERE media_id=? AND tag_id=?",
                arrayOf(mediaId.toString(), tagId.toString())
            )
            val saved = check.use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (saved == 0) error("保存を確認できませんでした")
        }

    fun removeTag(mediaId: Long, tagId: Long) {
        helper.writableDatabase.delete(
            "media_tag",
            "media_id=? AND tag_id=?",
            arrayOf(mediaId.toString(), tagId.toString())
        )
    }

    fun confirmTag(mediaId: Long, tagId: Long) {
        val v = ContentValues()
        v.put("confirmed", 1)
        helper.writableDatabase.update(
            "media_tag",
            v,
            "media_id=? AND tag_id=?",
            arrayOf(mediaId.toString(), tagId.toString())
        )
    }

    fun tagNames(kind: String): List<String> {
        val out = ArrayList<String>()
        val c = helper.readableDatabase.rawQuery(
            "SELECT name FROM tag WHERE kind=? ORDER BY name ASC",
            arrayOf(kind)
        )
        c.use { while (it.moveToNext()) out.add(it.getString(0)) }
        return out
    }

    fun linkProject(mediaId: Long, projectId: Long) {
        val v = ContentValues()
        v.put("media_id", mediaId)
        v.put("project_id", projectId)
        helper.writableDatabase.insertWithOnConflict(
            "media_project", null, v, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun unlinkProject(mediaId: Long, projectId: Long) {
        helper.writableDatabase.delete(
            "media_project",
            "media_id=? AND project_id=?",
            arrayOf(mediaId.toString(), projectId.toString())
        )
    }

    fun insertTrash(item: MediaItem, uri: String, expireAt: Long): Long {
        val v = ContentValues()
        v.put("name", item.name)
        v.put("kind", item.kind)
        v.put("uri", uri)
        v.put("tags", item.tags.joinToString(",") { tag -> tag.name })
        v.put("deleted_at", System.currentTimeMillis())
        v.put("expire_at", expireAt)
        return helper.writableDatabase.insert("trash", null, v)
    }

    fun trash(): List<TrashItem> {
        val out = ArrayList<TrashItem>()
        val c = helper.readableDatabase.rawQuery(
            "SELECT id,name,kind,uri,tags,deleted_at,expire_at FROM trash ORDER BY deleted_at DESC",
            null
        )
        c.use {
            while (it.moveToNext()) {
                out.add(
                    TrashItem(
                        id = it.getLong(0),
                        name = it.getString(1),
                        kind = it.getString(2),
                        uri = it.getString(3),
                        tags = it.getString(4),
                        deletedAt = it.getLong(5),
                        expireAt = it.getLong(6)
                    )
                )
            }
        }
        return out
    }

    fun deleteTrashRow(id: Long) {
        helper.writableDatabase.delete("trash", "id=?", arrayOf(id.toString()))
    }

    fun deleteMedia(id: Long) {
        val db = helper.writableDatabase
        db.delete("media_project", "media_id=?", arrayOf(id.toString()))
        db.delete("media_tag", "media_id=?", arrayOf(id.toString()))
        db.delete("media", "id=?", arrayOf(id.toString()))
    }
}
