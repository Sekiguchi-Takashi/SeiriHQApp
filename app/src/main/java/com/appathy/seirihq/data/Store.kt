package com.appathy.seirihq.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Store(context: Context) {

    private val repo = Repository(context)

    var fixedPrompts by mutableStateOf<List<PromptItem>>(emptyList())
        private set
    var tempPrompts by mutableStateOf<List<PromptItem>>(emptyList())
        private set
    var activeFixedId by mutableStateOf<Long?>(null)
        private set
    var activeTempId by mutableStateOf<Long?>(null)
        private set
    var customPrompt by mutableStateOf("")

    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var media by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    init {
        activeFixedId = repo.state(KEY_ACTIVE_FIXED)?.toLongOrNull()
        activeTempId = repo.state(KEY_ACTIVE_TEMP)?.toLongOrNull()
        reloadPrompts()
        reloadProjects()
        reloadMedia()
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
    }

    fun projectName(id: Long): String = projects.firstOrNull { it.id == id }?.name ?: "-"

    fun addMedia(uri: String, kind: String, name: String) {
        repo.insertMedia(uri, kind, name)
    }

    fun setStatus(id: Long, status: String) {
        repo.setMediaStatus(id, status)
        reloadMedia()
    }

    fun setTags(id: Long, tags: String) {
        repo.setMediaTags(id, tags)
        reloadMedia()
    }

    fun toggleProject(mediaId: Long, projectId: Long, on: Boolean) {
        if (on) repo.linkProject(mediaId, projectId) else repo.unlinkProject(mediaId, projectId)
        reloadMedia()
    }

    fun deleteMedia(id: Long) {
        repo.deleteMedia(id)
        reloadMedia()
    }

    fun mediaItem(id: Long): MediaItem? = media.firstOrNull { it.id == id }

    fun filteredMedia(query: String, status: String?): List<MediaItem> {
        val q = query.trim().lowercase()
        return media.filter { m ->
            (status == null || m.status == status) &&
                (q.isEmpty() ||
                    m.name.lowercase().contains(q) ||
                    m.tags.lowercase().contains(q) ||
                    m.projectIds.any { projectName(it).lowercase().contains(q) })
        }
    }
}
