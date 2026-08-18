package com.appathy.seirihq.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class LibraryStore(context: Context) {

    private val appContext = context.applicationContext
    private val repo = LibraryRepository(context)
    private val settings = Repository(context)

    var groups by mutableStateOf<List<PhotoGroup>>(emptyList())
        private set
    var charas by mutableStateOf<List<Chara>>(emptyList())
        private set
    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set

    var backupTreeUri by mutableStateOf<String?>(null)
        private set
    var maxEdge by mutableStateOf(DEFAULT_MAX_EDGE)
        private set
    var quality by mutableStateOf(DEFAULT_QUALITY)
        private set

    init {
        backupTreeUri = settings.state(KEY_BACKUP_TREE)?.ifEmpty { null }
        maxEdge = settings.state(KEY_MAX_EDGE)?.toIntOrNull() ?: DEFAULT_MAX_EDGE
        quality = settings.state(KEY_QUALITY)?.toIntOrNull() ?: DEFAULT_QUALITY
        reloadGroups()
    }

    fun updateBackupTree(uri: String?) {
        backupTreeUri = uri
        settings.setState(KEY_BACKUP_TREE, uri ?: "")
    }

    fun updateMaxEdge(value: Int) {
        maxEdge = value
        settings.setState(KEY_MAX_EDGE, value.toString())
    }

    fun updateQuality(value: Int) {
        quality = value
        settings.setState(KEY_QUALITY, value.toString())
    }

    fun reloadGroups() {
        groups = repo.groups()
    }

    fun loadCharas(groupId: Long) {
        charas = repo.charas(groupId)
    }

    fun loadPhotos(charaId: Long) {
        photos = repo.photos(charaId)
    }

    fun group(id: Long): PhotoGroup? = groups.firstOrNull { it.id == id }

    fun chara(id: Long): Chara? = charas.firstOrNull { it.id == id } ?: repo.chara(id)

    fun addGroup(name: String) {
        if (name.isBlank()) return
        repo.insertGroup(name.trim())
        reloadGroups()
    }

    fun renameGroup(id: Long, name: String) {
        if (name.isBlank()) return
        repo.renameGroup(id, name.trim())
        reloadGroups()
    }

    fun deleteGroup(id: Long) {
        repo.charas(id).forEach { deleteChara(it.id) }
        repo.deleteGroup(id)
        reloadGroups()
    }

    fun groupTagNames(): List<String> = repo.groupTagNames()

    fun addGroupTag(groupId: Long, name: String) {
        if (name.isBlank()) return
        repo.addGroupTag(groupId, name.trim())
        reloadGroups()
    }

    fun removeGroupTag(groupId: Long, name: String) {
        repo.removeGroupTag(groupId, name)
        reloadGroups()
    }

    fun addChara(groupId: Long, name: String) {
        if (name.isBlank()) return
        repo.insertChara(groupId, name.trim())
        loadCharas(groupId)
        reloadGroups()
    }

    fun updateChara(id: Long, groupId: Long, name: String, memo: String) {
        repo.updateChara(id, name.trim(), memo.take(30))
        loadCharas(groupId)
    }

    fun deleteChara(id: Long) {
        val target = repo.chara(id)
        repo.photos(id).forEach { PhotoStore.deleteFile(it.path) }
        repo.photos(id).forEach { repo.deletePhoto(it.id) }
        PhotoStore.charaDir(appContext, id).deleteRecursively()
        repo.deleteChara(id)
        if (target != null) loadCharas(target.groupId)
        reloadGroups()
    }

    /** 取り込みは圧縮してアプリ内へ保存する。原本は触らない。 */
    fun importPhoto(charaId: Long, source: Uri, displayName: String): Result<Unit> {
        val saved = PhotoStore.importPhoto(
            context = appContext,
            charaId = charaId,
            source = source,
            displayName = displayName,
            maxEdge = maxEdge,
            quality = quality
        )
        val value = saved.getOrElse { return Result.failure(it) }
        repo.insertPhoto(charaId, value.first, displayName, value.second)
        return Result.success(Unit)
    }

    fun updatePhoto(id: Long, charaId: Long, memo: String, status: String) {
        repo.updatePhoto(id, memo.take(30), status)
        loadPhotos(charaId)
    }

    fun deletePhoto(photo: Photo) {
        PhotoStore.deleteFile(photo.path)
        repo.deletePhoto(photo.id)
        loadPhotos(photo.charaId)
        reloadGroups()
    }

    fun photosOfGroup(groupId: Long): List<Pair<String, Photo>> = repo.photosOfGroup(groupId)

    fun usedBytes(): Long = groups.sumOf { group ->
        repo.photosOfGroup(group.id).sumOf { it.second.bytes }
    }
}
