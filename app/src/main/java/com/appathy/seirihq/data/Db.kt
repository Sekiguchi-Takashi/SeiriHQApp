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

const val MEDIA_IMAGE = "image"
const val MEDIA_VIDEO = "video"

val MEDIA_STATUSES = listOf("未整理", "整理済み", "保留", "アーカイブ")

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

data class MediaItem(
    val id: Long,
    val uri: String,
    val kind: String,
    val name: String,
    val status: String,
    val tags: String,
    val addedAt: Long,
    val projectIds: List<Long>
)

class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "seirihq.db", null, 1) {

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
                "added_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE media_project(" +
                "media_id INTEGER NOT NULL," +
                "project_id INTEGER NOT NULL," +
                "PRIMARY KEY(media_id, project_id))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
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

    fun insertMedia(uri: String, kind: String, name: String): Long {
        val v = ContentValues()
        v.put("uri", uri)
        v.put("kind", kind)
        v.put("name", name)
        v.put("status", MEDIA_STATUSES[0])
        v.put("tags", "")
        v.put("added_at", System.currentTimeMillis())
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
        val out = ArrayList<MediaItem>()
        val c = helper.readableDatabase.rawQuery(
            "SELECT id,uri,kind,name,status,tags,added_at FROM media ORDER BY added_at DESC", null
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
                        tags = it.getString(5),
                        addedAt = it.getLong(6),
                        projectIds = links[id] ?: emptyList()
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

    fun setMediaTags(id: Long, tags: String) {
        val v = ContentValues()
        v.put("tags", tags)
        helper.writableDatabase.update("media", v, "id=?", arrayOf(id.toString()))
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

    fun deleteMedia(id: Long) {
        val db = helper.writableDatabase
        db.delete("media_project", "media_id=?", arrayOf(id.toString()))
        db.delete("media", "id=?", arrayOf(id.toString()))
    }
}
