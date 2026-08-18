package com.appathy.seirihq.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

class LibraryRepository(context: Context) {

    private val helper = Db(context)

    // ---- グループ ----

    fun groups(): List<PhotoGroup> {
        val db = helper.readableDatabase
        val tagMap = HashMap<Long, MutableList<String>>()
        val tc = db.rawQuery(
            "SELECT gt.group_id, t.name FROM group_tag gt JOIN tag t ON t.id = gt.tag_id " +
                "ORDER BY t.name ASC",
            null
        )
        tc.use {
            while (it.moveToNext()) {
                tagMap.getOrPut(it.getLong(0)) { ArrayList() }.add(it.getString(1))
            }
        }

        val charaCount = HashMap<Long, Int>()
        val photoCount = HashMap<Long, Int>()
        val cc = db.rawQuery(
            "SELECT c.group_id, COUNT(DISTINCT c.id), COUNT(p.id) FROM chara c " +
                "LEFT JOIN photo p ON p.chara_id = c.id GROUP BY c.group_id",
            null
        )
        cc.use {
            while (it.moveToNext()) {
                charaCount[it.getLong(0)] = it.getInt(1)
                photoCount[it.getLong(0)] = it.getInt(2)
            }
        }

        val out = ArrayList<PhotoGroup>()
        val c = db.rawQuery("SELECT id,name FROM photo_group ORDER BY name ASC", null)
        c.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                out.add(
                    PhotoGroup(
                        id = id,
                        name = it.getString(1),
                        tags = tagMap[id] ?: emptyList(),
                        charaCount = charaCount[id] ?: 0,
                        photoCount = photoCount[id] ?: 0
                    )
                )
            }
        }
        return out
    }

    fun insertGroup(name: String): Long {
        val v = ContentValues()
        v.put("name", name)
        v.put("created_at", System.currentTimeMillis())
        return helper.writableDatabase.insert("photo_group", null, v)
    }

    fun renameGroup(id: Long, name: String) {
        val v = ContentValues()
        v.put("name", name)
        helper.writableDatabase.update("photo_group", v, "id=?", arrayOf(id.toString()))
    }

    fun deleteGroup(id: Long) {
        val db = helper.writableDatabase
        db.delete("group_tag", "group_id=?", arrayOf(id.toString()))
        db.delete("photo_group", "id=?", arrayOf(id.toString()))
    }

    private fun ensureGroupTag(name: String): Long {
        val db = helper.writableDatabase
        val v = ContentValues()
        v.put("name", name)
        v.put("kind", TAG_GROUP)
        db.insertWithOnConflict("tag", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        val c = db.rawQuery(
            "SELECT id FROM tag WHERE name=? AND kind=?",
            arrayOf(name, TAG_GROUP)
        )
        c.use { return if (it.moveToFirst()) it.getLong(0) else -1L }
    }

    fun addGroupTag(groupId: Long, name: String) {
        val tagId = ensureGroupTag(name)
        if (tagId <= 0) return
        val v = ContentValues()
        v.put("group_id", groupId)
        v.put("tag_id", tagId)
        helper.writableDatabase.insertWithOnConflict(
            "group_tag", null, v, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun removeGroupTag(groupId: Long, name: String) {
        val db = helper.writableDatabase
        val c = db.rawQuery(
            "SELECT id FROM tag WHERE name=? AND kind=?",
            arrayOf(name, TAG_GROUP)
        )
        var tagId = -1L
        c.use { if (it.moveToFirst()) tagId = it.getLong(0) }
        if (tagId <= 0) return
        db.delete(
            "group_tag",
            "group_id=? AND tag_id=?",
            arrayOf(groupId.toString(), tagId.toString())
        )
    }

    fun groupTagNames(): List<String> {
        val out = ArrayList<String>()
        val c = helper.readableDatabase.rawQuery(
            "SELECT name FROM tag WHERE kind=? ORDER BY name ASC",
            arrayOf(TAG_GROUP)
        )
        c.use { while (it.moveToNext()) out.add(it.getString(0)) }
        return out
    }

    // ---- キャラクター ----

    fun charas(groupId: Long): List<Chara> {
        val counts = HashMap<Long, Int>()
        val cc = helper.readableDatabase.rawQuery(
            "SELECT chara_id, COUNT(*) FROM photo GROUP BY chara_id", null
        )
        cc.use { while (it.moveToNext()) counts[it.getLong(0)] = it.getInt(1) }

        val out = ArrayList<Chara>()
        val c = helper.readableDatabase.rawQuery(
            "SELECT id,group_id,name,memo FROM chara WHERE group_id=? ORDER BY name ASC",
            arrayOf(groupId.toString())
        )
        c.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                out.add(
                    Chara(
                        id = id,
                        groupId = it.getLong(1),
                        name = it.getString(2),
                        memo = it.getString(3),
                        photoCount = counts[id] ?: 0
                    )
                )
            }
        }
        return out
    }

    fun insertChara(groupId: Long, name: String): Long {
        val v = ContentValues()
        v.put("group_id", groupId)
        v.put("name", name)
        v.put("memo", "")
        v.put("created_at", System.currentTimeMillis())
        return helper.writableDatabase.insert("chara", null, v)
    }

    fun updateChara(id: Long, name: String, memo: String) {
        val v = ContentValues()
        v.put("name", name)
        v.put("memo", memo)
        helper.writableDatabase.update("chara", v, "id=?", arrayOf(id.toString()))
    }

    fun deleteChara(id: Long) {
        helper.writableDatabase.delete("chara", "id=?", arrayOf(id.toString()))
    }

    fun chara(id: Long): Chara? {
        val c = helper.readableDatabase.rawQuery(
            "SELECT id,group_id,name,memo FROM chara WHERE id=?",
            arrayOf(id.toString())
        )
        c.use {
            if (it.moveToFirst()) {
                return Chara(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), 0)
            }
        }
        return null
    }

    // ---- 写真 ----

    fun photos(charaId: Long): List<Photo> {
        val out = ArrayList<Photo>()
        val c = helper.readableDatabase.rawQuery(
            "SELECT id,chara_id,path,name,memo,status,bytes,added_at FROM photo " +
                "WHERE chara_id=? ORDER BY added_at DESC",
            arrayOf(charaId.toString())
        )
        c.use {
            while (it.moveToNext()) {
                out.add(
                    Photo(
                        id = it.getLong(0),
                        charaId = it.getLong(1),
                        path = it.getString(2),
                        name = it.getString(3),
                        memo = it.getString(4),
                        status = it.getString(5),
                        bytes = it.getLong(6),
                        addedAt = it.getLong(7)
                    )
                )
            }
        }
        return out
    }

    fun insertPhoto(charaId: Long, path: String, name: String, bytes: Long): Long {
        val v = ContentValues()
        v.put("chara_id", charaId)
        v.put("path", path)
        v.put("name", name)
        v.put("memo", "")
        v.put("status", PHOTO_STATUSES[0])
        v.put("bytes", bytes)
        v.put("added_at", System.currentTimeMillis())
        return helper.writableDatabase.insert("photo", null, v)
    }

    fun updatePhoto(id: Long, memo: String, status: String) {
        val v = ContentValues()
        v.put("memo", memo)
        v.put("status", status)
        helper.writableDatabase.update("photo", v, "id=?", arrayOf(id.toString()))
    }

    fun deletePhoto(id: Long) {
        helper.writableDatabase.delete("photo", "id=?", arrayOf(id.toString()))
    }

    fun photosOfGroup(groupId: Long): List<Pair<String, Photo>> {
        val out = ArrayList<Pair<String, Photo>>()
        val c = helper.readableDatabase.rawQuery(
            "SELECT c.name, p.id, p.chara_id, p.path, p.name, p.memo, p.status, p.bytes, p.added_at " +
                "FROM photo p JOIN chara c ON c.id = p.chara_id WHERE c.group_id=?",
            arrayOf(groupId.toString())
        )
        c.use {
            while (it.moveToNext()) {
                out.add(
                    it.getString(0) to Photo(
                        id = it.getLong(1),
                        charaId = it.getLong(2),
                        path = it.getString(3),
                        name = it.getString(4),
                        memo = it.getString(5),
                        status = it.getString(6),
                        bytes = it.getLong(7),
                        addedAt = it.getLong(8)
                    )
                )
            }
        }
        return out
    }
}
