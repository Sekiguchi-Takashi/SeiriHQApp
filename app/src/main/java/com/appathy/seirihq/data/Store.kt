package com.appathy.seirihq.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val LOCK_GRACE_MS = 30000L

class Store(context: Context) {

    private val repo = Repository(context)
    private val appContext = context.applicationContext

    var fixedPrompts by mutableStateOf<List<PromptItem>>(emptyList())
        private set
    var tempPrompts by mutableStateOf<List<PromptItem>>(emptyList())
        private set
    var activeFixedId by mutableStateOf<Long?>(null)
        private set
    var activeTempId by mutableStateOf<Long?>(null)
        private set
    var customPrompt by mutableStateOf("")

    var pinSet by mutableStateOf(false)
        private set
    var biometricEnabled by mutableStateOf(false)
        private set
    var authOnDelete by mutableStateOf(true)
        private set
    var unlocked by mutableStateOf(false)
        private set
    private var backgroundAt = 0L

    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var media by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var trash by mutableStateOf<List<TrashItem>>(emptyList())
        private set
    var trashTreeUri by mutableStateOf<String?>(null)
        private set
    var retentionDays by mutableStateOf(DEFAULT_RETENTION_DAYS)
        private set
    var cleanTreeUri by mutableStateOf<String?>(null)
        private set
    var useTrash by mutableStateOf(true)
        private set
    var lastPromptId by mutableStateOf(0L)
        private set
    var linkImports by mutableStateOf(true)
        private set

    init {
        activeFixedId = repo.state(KEY_ACTIVE_FIXED)?.toLongOrNull()
        activeTempId = repo.state(KEY_ACTIVE_TEMP)?.toLongOrNull()
        reloadPrompts()
        pinSet = !repo.state(KEY_PIN_HASH).isNullOrEmpty()
        biometricEnabled = repo.state(KEY_BIOMETRIC) == "1"
        authOnDelete = repo.state(KEY_AUTH_ON_DELETE) != "0"
        trashTreeUri = repo.state(KEY_TRASH_TREE)?.ifEmpty { null }
        retentionDays = repo.state(KEY_RETENTION)?.toIntOrNull() ?: DEFAULT_RETENTION_DAYS
        cleanTreeUri = repo.state(KEY_CLEAN_TREE)?.ifEmpty { null }
        useTrash = repo.state(KEY_USE_TRASH) != "0"
        lastPromptId = repo.state(KEY_LAST_PROMPT)?.toLongOrNull() ?: 0L
        linkImports = repo.state(KEY_LINK_IMPORTS) != "0"
        reloadProjects()
        reloadMedia()
        reloadTrash()
        purgeExpired()
    }

    fun reloadTrash() {
        trash = repo.trash()
    }

    fun setTrashTree(uri: String?) {
        trashTreeUri = uri
        repo.setState(KEY_TRASH_TREE, uri ?: "")
    }

    fun updateCleanTree(uri: String?) {
        cleanTreeUri = uri
        repo.setState(KEY_CLEAN_TREE, uri ?: "")
    }

    fun updateUseTrash(on: Boolean) {
        useTrash = on
        repo.setState(KEY_USE_TRASH, if (on) "1" else "0")
    }

    fun updateRetentionDays(days: Int) {
        retentionDays = days
        repo.setState(KEY_RETENTION, days.toString())
    }

    /** 原本をゴミ箱へ移したあと、素材一覧から取り除く。 */
    fun moveToTrash(item: MediaItem, trashUri: String) {
        val expire = System.currentTimeMillis() + retentionDays * 24L * 60L * 60L * 1000L
        repo.insertTrash(item, trashUri, expire)
        repo.deleteMedia(item.id)
        reloadMedia()
        reloadTrash()
    }

    /** ゴミ箱の実ファイルは残したまま、素材一覧へ戻す。 */
    fun restoreFromTrash(item: TrashItem) {
        val source = if (Uri.parse(item.uri).scheme == "file") SOURCE_TRASH else SOURCE_SAF
        repo.insertMedia(item.uri, item.kind, item.name, source, true)
        repo.deleteTrashRow(item.id)
        reloadMedia()
        reloadTrash()
    }

    fun purgeOne(item: TrashItem) {
        TrashFiles.deleteFile(appContext, item.uri)
        repo.deleteTrashRow(item.id)
        reloadTrash()
    }

    fun emptyTrash() {
        repo.trash().forEach { item ->
            TrashFiles.deleteFile(appContext, item.uri)
            repo.deleteTrashRow(item.id)
        }
        reloadTrash()
    }

    /** 保持期間を過ぎたものを完全削除する。起動時に実行される。 */
    fun purgeExpired(): Int {
        val now = System.currentTimeMillis()
        var count = 0
        repo.trash().filter { it.expireAt <= now }.forEach { item ->
            TrashFiles.deleteFile(appContext, item.uri)
            repo.deleteTrashRow(item.id)
            count++
        }
        if (count > 0) reloadTrash()
        return count
    }

    fun reloadPrompts() {
        fixedPrompts = repo.prompts(KIND_FIXED)
        tempPrompts = repo.prompts(KIND_TEMP)
        if (fixedPrompts.none { it.id == activeFixedId }) setActiveFixed(null)
        if (tempPrompts.none { it.id == activeTempId }) setActiveTemp(null)
    }

    fun reloadProjects() {
        projects = repo.projects()
    }

    fun reloadMedia() {
        media = repo.media()
    }

    fun promptsOf(kind: String): List<PromptItem> =
        if (kind == KIND_FIXED) fixedPrompts else tempPrompts

    fun prompt(id: Long?): PromptItem? {
        if (id == null) return null
        return fixedPrompts.firstOrNull { it.id == id } ?: tempPrompts.firstOrNull { it.id == id }
    }

    fun activeFixed(): PromptItem? = prompt(activeFixedId)

    fun activeTemp(): PromptItem? = prompt(activeTempId)

    fun setActiveFixed(id: Long?) {
        activeFixedId = id
        repo.setState(KEY_ACTIVE_FIXED, id?.toString() ?: "")
    }

    fun setActiveTemp(id: Long?) {
        activeTempId = id
        repo.setState(KEY_ACTIVE_TEMP, id?.toString() ?: "")
    }

    fun stepTemp(delta: Int) {
        val list = tempPrompts
        if (list.isEmpty()) return
        val current = list.indexOfFirst { it.id == activeTempId }
        val next = if (current < 0) {
            if (delta > 0) 0 else list.lastIndex
        } else {
            ((current + delta) % list.size + list.size) % list.size
        }
        setActiveTemp(list[next].id)
    }

    fun tempPosition(): Int = tempPrompts.indexOfFirst { it.id == activeTempId } + 1

    fun savePrompt(kind: String, id: Long?, name: String, description: String, body: String): Boolean {
        val ok: Boolean
        if (id == null) {
            ok = repo.insertPrompt(kind, name, description, body) > 0
        } else {
            repo.updatePrompt(id, name, description, body)
            ok = true
        }
        reloadPrompts()
        return ok
    }

    fun deletePrompt(id: Long) {
        repo.deletePrompt(id)
        reloadPrompts()
    }

    fun deleteAllTemp() {
        repo.deleteAllTemp()
        setActiveTemp(null)
        reloadPrompts()
    }

    fun composed(): String {
        val parts = ArrayList<String>()
        activeFixed()?.body?.trim()?.let { if (it.isNotEmpty()) parts.add(it) }
        activeTemp()?.body?.trim()?.let { if (it.isNotEmpty()) parts.add(it) }
        customPrompt.trim().let { if (it.isNotEmpty()) parts.add(it) }
        return parts.joinToString("\n\n")
    }

    fun addProject(name: String) {
        if (name.isBlank()) return
        repo.insertProject(name.trim())
        reloadProjects()
    }

    fun deleteProject(id: Long) {
        repo.deleteProject(id)
        reloadProjects()
        reloadMedia()
        reloadTrash()
    }

    fun projectName(id: Long): String = projects.firstOrNull { it.id == id }?.name ?: "-"

    fun addMedia(uri: String, kind: String, name: String, source: String, writable: Boolean) {
        val promptId = if (linkImports) lastPromptId else 0L
        repo.insertMedia(uri, kind, name, source, writable, promptId)
    }

    /** コピーした時点のプロンプトを覚えておき、取り込んだ素材の生成元にする。 */
    fun noteCopiedPrompt() {
        val id = activeTempId ?: activeFixedId ?: 0L
        lastPromptId = id
        repo.setState(KEY_LAST_PROMPT, id.toString())
    }

    fun updateLinkImports(on: Boolean) {
        linkImports = on
        repo.setState(KEY_LINK_IMPORTS, if (on) "1" else "0")
    }

    fun updateSourcePrompt(mediaId: Long, promptId: Long) {
        repo.setSourcePrompt(mediaId, promptId)
        reloadMedia()
    }

    fun allPrompts(): List<PromptItem> = fixedPrompts + tempPrompts

    fun setPin(pin: String) {
        val salt = Passcode.newSalt()
        repo.setState(KEY_PIN_SALT, salt)
        repo.setState(KEY_PIN_HASH, Passcode.hash(pin, salt))
        pinSet = true
        unlocked = true
    }

    fun verifyPin(pin: String): Boolean {
        val salt = repo.state(KEY_PIN_SALT) ?: return false
        val hash = repo.state(KEY_PIN_HASH) ?: return false
        val ok = Passcode.hash(pin, salt) == hash
        if (ok) unlocked = true
        return ok
    }

    fun unlock() {
        unlocked = true
    }

    /** 画面遷移や取り込みで一時的に離れただけならロックしない。 */
    fun markBackground() {
        backgroundAt = System.currentTimeMillis()
    }

    fun applyLockTimeout() {
        if (!unlocked) return
        if (backgroundAt == 0L) return
        if (System.currentTimeMillis() - backgroundAt > LOCK_GRACE_MS) unlocked = false
        backgroundAt = 0L
    }

    fun lock() {
        unlocked = false
    }

    fun setBiometric(on: Boolean) {
        biometricEnabled = on
        repo.setState(KEY_BIOMETRIC, if (on) "1" else "0")
    }

    fun updateAuthOnDelete(on: Boolean) {
        authOnDelete = on
        repo.setState(KEY_AUTH_ON_DELETE, if (on) "1" else "0")
    }

    fun setStatus(id: Long, status: String) {
        repo.setMediaStatus(id, status)
        reloadMedia()
    }

    fun addTag(mediaId: Long, name: String, kind: String = TAG_USER): Result<Unit> {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return Result.failure(IllegalArgumentException("タグ名が空です"))
        val result = repo.addTag(mediaId, cleaned, kind, kind != TAG_AI)
        reloadMedia()
        return result
    }

    fun removeTag(mediaId: Long, tagId: Long) {
        repo.removeTag(mediaId, tagId)
        reloadMedia()
    }

    fun confirmTag(mediaId: Long, tagId: Long) {
        repo.confirmTag(mediaId, tagId)
        reloadMedia()
    }

    fun userTagNames(): List<String> = repo.tagNames(TAG_USER)

    fun toggleProject(mediaId: Long, projectId: Long, on: Boolean) {
        if (on) repo.linkProject(mediaId, projectId) else repo.unlinkProject(mediaId, projectId)
        reloadMedia()
    }

    fun deleteMedia(id: Long) {
        repo.deleteMedia(id)
        reloadMedia()
    }

    fun mediaItem(id: Long): MediaItem? = media.firstOrNull { it.id == id }

    fun mediaOfProject(projectId: Long): List<MediaItem> =
        media.filter { it.projectIds.contains(projectId) }

    /** まだ交通整理していない素材の待ち行列。 */
    fun sortQueue(): List<MediaItem> = media.filter { it.status == MEDIA_STATUSES[0] }

    /** 交通整理で1件を処理する。プロジェクトを渡した場合は紐づけて整理済みにする。 */
    fun sortInto(item: MediaItem, status: String, projectId: Long? = null) {
        if (projectId != null) repo.linkProject(item.id, projectId)
        repo.setMediaStatus(item.id, status)
        reloadMedia()
    }

    fun filteredMedia(query: String, status: String?): List<MediaItem> {
        val q = query.trim().lowercase()
        return media.filter { m ->
            (status == null || m.status == status) &&
                (q.isEmpty() ||
                    m.name.lowercase().contains(q) ||
                    m.tags.any { tag -> tag.name.lowercase().contains(q) } ||
                    m.projectIds.any { projectName(it).lowercase().contains(q) })
        }
    }
}
