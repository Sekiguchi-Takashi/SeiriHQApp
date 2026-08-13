@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.appathy.seirihq.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.appathy.seirihq.data.DeleteOutcome
import com.appathy.seirihq.data.FilePermission
import com.appathy.seirihq.data.MEDIA_IMAGE
import com.appathy.seirihq.data.MEDIA_STATUSES
import com.appathy.seirihq.data.MEDIA_VIDEO
import com.appathy.seirihq.data.MediaFiles
import com.appathy.seirihq.data.MediaItem
import com.appathy.seirihq.data.SOURCE_SAF
import com.appathy.seirihq.data.TAG_AI
import com.appathy.seirihq.data.TAG_SYSTEM
import com.appathy.seirihq.data.SOURCE_STORE
import com.appathy.seirihq.data.Store
import com.appathy.seirihq.data.FolderCleaner
import com.appathy.seirihq.data.ImageClip
import com.appathy.seirihq.data.TrashFiles
import com.appathy.seirihq.data.TrashItem
import com.appathy.seirihq.data.ZipMaker
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private sealed class MediaRoute {
    data object Inbox : MediaRoute()
    data object Trash : MediaRoute()
    data object Cleanup : MediaRoute()
    data object Sort : MediaRoute()
    data object Projects : MediaRoute()
    data class Project(val id: Long) : MediaRoute()
    data class Detail(val id: Long) : MediaRoute()
}

private fun shareUri(context: Context, uriString: String, kind: String) {
    val raw = Uri.parse(uriString)
    val shareable = if (raw.scheme == "file") {
        val path = raw.path ?: return
        runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
        }.getOrNull() ?: return
    } else {
        raw
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (kind == MEDIA_VIDEO) "video/*" else "image/*"
        putExtra(Intent.EXTRA_STREAM, shareable)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "アップロード先を選択"))
}

private fun daysLeft(expireAt: Long): Long {
    val diff = expireAt - System.currentTimeMillis()
    if (diff <= 0) return 0
    return TimeUnit.MILLISECONDS.toDays(diff) + 1
}

private class OpenMediaDocuments : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
}

private fun displayName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
        ?: return uri.lastPathSegment ?: "media"
    cursor.use {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) return it.getString(index) ?: "media"
    }
    return uri.lastPathSegment ?: "media"
}

private fun importUris(context: Context, store: Store, uris: List<Uri>) {
    val resolver = context.contentResolver
    uris.forEach { uri ->
        val writable = runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.isSuccess
        if (!writable) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val type = resolver.getType(uri) ?: ""
        val kind = if (type.startsWith("video")) MEDIA_VIDEO else MEDIA_IMAGE
        store.addMedia(uri.toString(), kind, displayName(context, uri), SOURCE_SAF, writable)
    }
    store.reloadMedia()
}

private fun canDeleteOriginal(context: Context, item: MediaItem): Boolean = when (item.source) {
    SOURCE_SAF -> item.writable
    else -> FilePermission.canDeleteOriginals(context)
}

@Composable
fun MediaTab(store: Store, modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<MediaRoute>(MediaRoute.Inbox) }

    BackHandler(enabled = route !is MediaRoute.Inbox) { route = MediaRoute.Inbox }

    if (!store.unlocked) {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("素材") },
                actions = { Icon(Icons.Default.Lock, contentDescription = null) }
            )
            LockScreen(store)
        }
        return
    }

    when (val r = route) {
        is MediaRoute.Inbox -> InboxScreen(
            store = store,
            modifier = modifier,
            onOpen = { route = MediaRoute.Detail(it) },
            onOpenTrash = { route = MediaRoute.Trash },
            onOpenCleanup = { route = MediaRoute.Cleanup },
            onOpenSort = { route = MediaRoute.Sort },
            onOpenProjects = { route = MediaRoute.Projects }
        )

        is MediaRoute.Sort -> SortScreen(
            store = store,
            modifier = modifier,
            onBack = { route = MediaRoute.Inbox }
        )

        is MediaRoute.Projects -> ProjectListScreen(
            store = store,
            modifier = modifier,
            onBack = { route = MediaRoute.Inbox },
            onOpen = { route = MediaRoute.Project(it) }
        )

        is MediaRoute.Project -> ProjectDetailScreen(
            store = store,
            projectId = r.id,
            modifier = modifier,
            onBack = { route = MediaRoute.Projects },
            onOpenMedia = { route = MediaRoute.Detail(it) }
        )

        is MediaRoute.Cleanup -> CleanupScreen(
            store = store,
            modifier = modifier,
            onBack = { route = MediaRoute.Inbox }
        )

        is MediaRoute.Trash -> TrashScreen(
            store = store,
            modifier = modifier,
            onBack = { route = MediaRoute.Inbox }
        )

        is MediaRoute.Detail -> MediaDetailScreen(
            store = store,
            id = r.id,
            modifier = modifier,
            onBack = { route = MediaRoute.Inbox }
        )
    }
}

@Composable
private fun InboxScreen(
    store: Store,
    modifier: Modifier,
    onOpen: (Long) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenCleanup: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenProjects: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(FilePermission.granted(context)) }

    val picker = rememberLauncherForActivityResult(OpenMediaDocuments()) { uris ->
        if (uris.isNotEmpty()) importUris(context, store, uris)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = FilePermission.granted(context)
    }

    val items = store.filteredMedia(query, status, store.pinnedOnly)

    val scope = rememberCoroutineScope()
    var bulkBusy by remember { mutableStateOf(false) }
    var confirmBulk by remember { mutableStateOf(false) }
    var askBulkPin by remember { mutableStateOf(false) }
    var pendingBulk by remember { mutableStateOf<List<Pair<MediaItem, String?>>>(emptyList()) }

    fun commit(entry: Pair<MediaItem, String?>) {
        val trashUri = entry.second
        if (trashUri != null) store.moveToTrash(entry.first, trashUri)
        else store.deleteMedia(entry.first.id)
    }

    val bulkDelete = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingBulk
        pendingBulk = emptyList()
        if (result.resultCode == Activity.RESULT_OK) {
            pending.forEach { commit(it) }
            Toast.makeText(context, "${pending.size}件を処理しました", Toast.LENGTH_SHORT).show()
        } else {
            pending.forEach { entry ->
                entry.second?.let { TrashFiles.deleteFile(context, it) }
            }
            Toast.makeText(context, "中止しました", Toast.LENGTH_SHORT).show()
        }
    }

    fun runBulk() {
        bulkBusy = true
        val targets = items.filter { canDeleteOriginal(context, it) }
        val skipped = items.size - targets.size
        scope.launch {
            val copies = HashMap<Long, String>()
            if (store.useTrash) {
                withContext(Dispatchers.IO) {
                    targets.forEach { target ->
                        TrashFiles.copyToTrash(context, target, store.trashTreeUri)
                            .getOrNull()?.let { copies[target.id] = it }
                    }
                }
            }
            val usable = if (store.useTrash) targets.filter { copies.containsKey(it.id) } else targets
            val storeGroup = usable.filter {
                it.source == SOURCE_STORE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            }
            val others = usable.filterNot { storeGroup.contains(it) }

            var done = 0
            others.forEach { other ->
                val outcome = withContext(Dispatchers.IO) {
                    MediaFiles.deleteOriginal(context, other)
                }
                if (outcome is DeleteOutcome.Done) {
                    commit(other to copies[other.id])
                    done++
                } else {
                    copies[other.id]?.let { withContext(Dispatchers.IO) { TrashFiles.deleteFile(context, it) } }
                }
            }

            val request = MediaFiles.bulkDeleteRequest(context, storeGroup)
            bulkBusy = false
            if (storeGroup.isNotEmpty() && request != null) {
                pendingBulk = storeGroup.map { it to copies[it.id] }
                bulkDelete.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                val note = if (skipped > 0) "（${skipped}件は削除できないため残しました）" else ""
                Toast.makeText(context, "${done}件を処理しました$note", Toast.LENGTH_LONG).show()
            }
        }
    }

    var selectMode by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var workBusy by remember { mutableStateOf(false) }
    var zipProgress by remember { mutableStateOf("") }

    val folderImport = rememberLauncherForActivityResult(OpenDocumentTree()) { treeUri: Uri? ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            workBusy = true
            scope.launch {
                val found = withContext(Dispatchers.IO) {
                    FolderCleaner.list(context, treeUri.toString())
                        .filter { it.isImage || it.isVideo }
                }
                withContext(Dispatchers.IO) {
                    found.forEach { entry ->
                        store.addMedia(
                            entry.uri,
                            if (entry.isVideo) MEDIA_VIDEO else MEDIA_IMAGE,
                            entry.name,
                            SOURCE_SAF,
                            true
                        )
                    }
                }
                store.reloadMedia()
                workBusy = false
                Toast.makeText(context, "${found.size}件を取り込みました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val zipCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { destination: Uri? ->
        if (destination != null) {
            val targets = store.media.filter { selectedIds.contains(it.id) }
            workBusy = true
            zipProgress = "0 / ${targets.size}"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    ZipMaker.zip(context, targets, destination) { done ->
                        zipProgress = "$done / ${targets.size}"
                    }
                }
                workBusy = false
                zipProgress = ""
                val message = result.getOrNull()?.let { "${it}件をZIPにしました" }
                    ?: (result.exceptionOrNull()?.message ?: "ZIPを作成できませんでした")
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun requestAuthThenBulk() {
        if (!store.authOnDelete) {
            runBulk()
            return
        }
        if (store.biometricEnabled && biometricAvailable(context)) {
            promptBiometric(
                context = context,
                title = "まとめて削除",
                subtitle = "${items.size}件を処理します",
                onSuccess = { runBulk() },
                onFail = { askBulkPin = true }
            )
        } else {
            askBulkPin = true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("素材 Inbox") },
            actions = {
                IconButton(onClick = {
                    selectMode = !selectMode
                    if (!selectMode) selectedIds = emptySet()
                }) {
                    Icon(Icons.Default.Check, contentDescription = "選択")
                }
                IconButton(onClick = onOpenTrash) {
                    Icon(Icons.Default.Delete, contentDescription = "ゴミ箱")
                }
                IconButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) {
                    Icon(Icons.Default.Add, contentDescription = "取り込み")
                }
            }
        )

        if (selectMode) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = selectedIds.isNotEmpty() && !workBusy,
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        zipCreator.launch("seirihq_$stamp.zip")
                    }
                ) {
                    Text(
                        if (zipProgress.isNotEmpty()) "ZIP作成中 $zipProgress"
                        else "ZIPにする ${selectedIds.size}"
                    )
                }
                OutlinedButton(onClick = { selectedIds = items.map { it.id }.toSet() }) {
                    Text("全選択")
                }
                OutlinedButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        store.setPinnedFor(selectedIds, true)
                        Toast.makeText(context, "${selectedIds.size}件を常用にしました", Toast.LENGTH_SHORT)
                            .show()
                        selectedIds = emptySet()
                    }
                ) { Text("★ 常用にする") }
                OutlinedButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        store.setPinnedFor(selectedIds, false)
                        selectedIds = emptySet()
                    }
                ) { Text("常用から外す") }
                OutlinedButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = { confirmArchive = true }
                ) { Text("アーカイブ") }
                TextButton(onClick = { selectedIds = emptySet() }) { Text("解除") }
                TextButton(onClick = {
                    selectMode = false
                    selectedIds = emptySet()
                }) { Text("選択終了") }
            }
        } else {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onOpenSort) { Text("交通整理 ${store.sortQueue().size}") }
                OutlinedButton(onClick = onOpenProjects) { Text("プロジェクト") }
                OutlinedButton(onClick = onOpenCleanup) { Text("ダウンロード整理") }
                OutlinedButton(
                    enabled = !workBusy,
                    onClick = { folderImport.launch(null) }
                ) { Text(if (workBusy) "処理中…" else "フォルダから取り込み") }
            }
        }

        if (!granted && !selectMode) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("権限なしモード", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "閲覧と取り込みのみ利用できます。端末の原本削除と一括取り込みにはファイル権限が必要です。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        permissionLauncher.launch(FilePermission.required().toTypedArray())
                    }) { Text("ファイル権限を付与") }
                }
            }
        } else if (!selectMode) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = {
                    val found = MediaFiles.scanRecent(context, 200)
                    found.forEach { entry ->
                        store.addMedia(entry.first, entry.second, entry.third, SOURCE_STORE, false)
                    }
                    store.reloadMedia()
                    Toast.makeText(context, "${found.size}件を確認しました", Toast.LENGTH_SHORT).show()
                }) { Text("端末から一括取り込み") }
            }
        }

        if (!selectMode) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = store.pinnedOnly,
                    onClick = {
                        store.updatePinnedOnly(true)
                        status = null
                    },
                    label = { Text("常用 ${store.pinnedCount()}") }
                )
                FilterChip(
                    selected = !store.pinnedOnly,
                    onClick = { store.updatePinnedOnly(false) },
                    label = { Text("すべて") }
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text("検索（名前・タグ・プロジェクト）") },
                singleLine = true
            )
            if (!store.pinnedOnly) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = status == null,
                        onClick = { status = null },
                        label = { Text("状態すべて") }
                    )
                    listOf(MEDIA_STATUSES[0], MEDIA_STATUSES[3], MEDIA_STATUSES[4]).forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = if (status == s) null else s },
                            label = { Text(s) }
                        )
                    }
                }
            }
        }
        Text(
            if (selectMode) "${items.size} 件 / 選択 ${selectedIds.size}" else "${items.size} 件",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall
        )
        if (status == MEDIA_STATUSES[4] && items.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !bulkBusy,
                    onClick = { confirmBulk = true }
                ) {
                    Text(
                        if (bulkBusy) "処理中…"
                        else if (store.useTrash) "${items.size}件をまとめてゴミ箱へ"
                        else "${items.size}件をまとめて削除"
                    )
                }
            }
        }
        if (items.isEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (store.pinnedOnly) {
                    Text("常用の素材がありません。")
                    Text(
                        "「すべて」に切り替え、右上のチェックで選択して「★ 常用にする」を押すと、ここに並びます。素材詳細の★でも登録できます。",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("素材がありません。右上の＋から取り込んでください。")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    MediaThumb(
                        item = item,
                        selected = selectedIds.contains(item.id),
                        showCopy = !selectMode,
                        onCopy = {
                            val result = ImageClip.copy(context, item)
                            val message = if (result.isSuccess) "コピーしました"
                            else (result.exceptionOrNull()?.message ?: "コピーできませんでした")
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        },
                        onClick = {
                            if (selectMode) {
                                selectedIds = if (selectedIds.contains(item.id)) {
                                    selectedIds - item.id
                                } else {
                                    selectedIds + item.id
                                }
                            } else {
                                onOpen(item.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("アーカイブ") },
            text = {
                Text(
                    "${selectedIds.size}件をアーカイブします。ファイルは消えません。" +
                        "「すべて」でアーカイブを選べば、いつでも戻せます。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.archive(selectedIds)
                    Toast.makeText(context, "${selectedIds.size}件をアーカイブしました", Toast.LENGTH_SHORT)
                        .show()
                    selectedIds = emptySet()
                    confirmArchive = false
                }) { Text("アーカイブ") }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text("キャンセル") }
            }
        )
    }

    if (confirmBulk) {
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            title = { Text(if (store.useTrash) "まとめてゴミ箱へ" else "まとめて削除") },
            text = {
                Text(
                    if (store.useTrash) {
                        "${items.size}件をゴミ箱へ移し、端末の原本を削除します。" +
                            "ゴミ箱からは${store.retentionDays}日以内なら戻せます。"
                    } else {
                        "${items.size}件を端末から直接削除します。取り消せません。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulk = false
                    requestAuthThenBulk()
                }) { Text("実行する") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulk = false }) { Text("キャンセル") }
            }
        )
    }

    if (askBulkPin) {
        PinDialog(
            title = "まとめて削除の確認",
            onDismiss = { askBulkPin = false },
            onSubmit = { pin ->
                if (store.verifyPin(pin)) {
                    askBulkPin = false
                    runBulk()
                    true
                } else {
                    false
                }
            }
        )
    }
}

@Composable
private fun MediaThumb(
    item: MediaItem,
    selected: Boolean = false,
    showCopy: Boolean = false,
    onCopy: () -> Unit = {},
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFFE0E0E0))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.kind != MEDIA_IMAGE) {
            Icon(Icons.Default.PlayArrow, contentDescription = "動画")
        }
        if (item.pinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color(0xCCFFFFFF))
                    .padding(horizontal = 4.dp)
            ) {
                Text("★", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (showCopy) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color(0xCC000000))
                    .clickable { onCopy() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("コピー", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x804CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "選択済み")
            }
        }
    }
}

@Composable
private fun MediaDetailScreen(store: Store, id: Long, modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val item = store.mediaItem(id)
    if (item == null) {
        LaunchedEffect(id) { onBack() }
        return
    }
    val scope = rememberCoroutineScope()
    var newTag by remember(item.id) { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmOriginal by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var askPrompt by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingTrashUri by remember { mutableStateOf<String?>(null) }

    val systemDelete = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val trashUri = pendingTrashUri
        pendingTrashUri = null
        if (result.resultCode == Activity.RESULT_OK) {
            if (trashUri != null) {
                store.moveToTrash(item, trashUri)
                Toast.makeText(context, "ゴミ箱へ移動しました", Toast.LENGTH_SHORT).show()
            } else {
                store.deleteMedia(item.id)
                Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show()
            }
            onBack()
        } else if (trashUri != null) {
            TrashFiles.deleteFile(context, trashUri)
            Toast.makeText(context, "中止しました", Toast.LENGTH_SHORT).show()
        }
    }

    fun runDelete() {
        busy = true
        scope.launch {
            if (!store.useTrash) {
                val direct = withContext(Dispatchers.IO) { MediaFiles.deleteOriginal(context, item) }
                busy = false
                when (direct) {
                    is DeleteOutcome.Done -> {
                        store.deleteMedia(item.id)
                        Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show()
                        onBack()
                    }

                    is DeleteOutcome.NeedsConfirm -> {
                        pendingTrashUri = null
                        systemDelete.launch(
                            IntentSenderRequest.Builder(direct.intent.intentSender).build()
                        )
                    }

                    is DeleteOutcome.Unsupported -> {
                        Toast.makeText(context, direct.reason, Toast.LENGTH_LONG).show()
                    }
                }
                return@launch
            }
            val copied = withContext(Dispatchers.IO) {
                TrashFiles.copyToTrash(context, item, store.trashTreeUri)
            }
            val trashUri = copied.getOrNull()
            if (trashUri == null) {
                busy = false
                val reason = copied.exceptionOrNull()?.message ?: "ゴミ箱へ保存できませんでした"
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                return@launch
            }
            val outcome = withContext(Dispatchers.IO) { MediaFiles.deleteOriginal(context, item) }
            busy = false
            when (outcome) {
                is DeleteOutcome.Done -> {
                    store.moveToTrash(item, trashUri)
                    Toast.makeText(context, "ゴミ箱へ移動しました", Toast.LENGTH_SHORT).show()
                    onBack()
                }

                is DeleteOutcome.NeedsConfirm -> {
                    pendingTrashUri = trashUri
                    systemDelete.launch(
                        IntentSenderRequest.Builder(outcome.intent.intentSender).build()
                    )
                }

                is DeleteOutcome.Unsupported -> {
                    withContext(Dispatchers.IO) { TrashFiles.deleteFile(context, trashUri) }
                    Toast.makeText(context, outcome.reason, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun requestAuthThenDelete() {
        if (!store.authOnDelete) {
            runDelete()
            return
        }
        if (store.biometricEnabled && biometricAvailable(context)) {
            promptBiometric(
                context = context,
                title = "削除の確認",
                subtitle = "端末から原本を削除します",
                onSuccess = { runDelete() },
                onFail = { askPin = true }
            )
        } else {
            askPin = true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("素材") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                if (item.kind == MEDIA_IMAGE) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "動画")
                }
            }
            Text(item.name, style = MaterialTheme.typography.titleMedium)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val result = ImageClip.copy(context, item)
                    val message = if (result.isSuccess) {
                        "コピーしました。プロンプト入力欄に貼り付けられます"
                    } else {
                        result.exceptionOrNull()?.message ?: "コピーできませんでした"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }) { Text("画像をコピー") }
                OutlinedButton(onClick = { shareUri(context, item.uri, item.kind) }) {
                    Text("送る")
                }
                OutlinedButton(onClick = {
                    store.togglePinned(item)
                    Toast.makeText(
                        context,
                        if (item.pinned) "常用から外しました" else "常用にしました",
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text(if (item.pinned) "★ 常用を解除" else "☆ 常用にする") }
            }
            Text(
                "コピーしたあと、生成AIアプリの入力欄で長押しして貼り付けてください。貼り付けに対応していないアプリでは「送る」を使ってください。",
                style = MaterialTheme.typography.bodySmall
            )

            Text("状態", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MEDIA_STATUSES.take(3).forEach { s ->
                    FilterChip(
                        selected = item.status == s,
                        onClick = { store.setStatus(item.id, s) },
                        label = { Text(s) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MEDIA_STATUSES.drop(3).forEach { s ->
                    FilterChip(
                        selected = item.status == s,
                        onClick = { store.setStatus(item.id, s) },
                        label = { Text(s) }
                    )
                }
            }

            Text("プロジェクト", style = MaterialTheme.typography.labelLarge)
            if (store.projects.isEmpty()) {
                Text("設定タブでプロジェクトを作成してください。", style = MaterialTheme.typography.bodySmall)
            }
            store.projects.forEach { project ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.projectIds.contains(project.id),
                        onCheckedChange = { store.toggleProject(item.id, project.id, it) }
                    )
                    Text(project.name)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("タグ", style = MaterialTheme.typography.labelLarge)
                    if (item.tags.isEmpty()) {
                        Text("タグはまだありません。", style = MaterialTheme.typography.bodySmall)
                    }
                    item.tags.forEach { tag ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (tag.kind) {
                                    TAG_AI -> if (tag.confirmed) "AI ${tag.name}" else "AI? ${tag.name}"
                                    TAG_SYSTEM -> "自動 ${tag.name}"
                                    else -> tag.name
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (tag.kind == TAG_AI && !tag.confirmed) {
                                TextButton(onClick = { store.confirmTag(item.id, tag.tagId) }) {
                                    Text("確定")
                                }
                            }
                            TextButton(onClick = { store.removeTag(item.id, tag.tagId) }) {
                                Text("削除")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    fun submitTag() {
                        val name = newTag.trim()
                        if (name.isEmpty()) {
                            Toast.makeText(context, "タグ名を入力してください", Toast.LENGTH_SHORT).show()
                            return
                        }
                        if (item.tags.any { it.name == name }) {
                            Toast.makeText(context, "すでに付いています", Toast.LENGTH_SHORT).show()
                            newTag = ""
                            return
                        }
                        val result = store.addTag(item.id, name)
                        if (result.isSuccess) {
                            newTag = ""
                            Toast.makeText(context, "「$name」を追加しました", Toast.LENGTH_SHORT).show()
                        } else {
                            val reason = result.exceptionOrNull()?.message ?: "不明なエラー"
                            Toast.makeText(context, "追加できません: $reason", Toast.LENGTH_LONG).show()
                        }
                    }
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("タグ名") },
                        placeholder = { Text("例: 京都") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitTag() })
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { submitTag() }) { Text("＋ タグを追加") }
                    val suggestions = store.userTagNames()
                        .filter { name -> item.tags.none { it.name == name } }
                        .take(8)
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("よく使うタグ", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.take(3).forEach { name ->
                                TextButton(onClick = {
                                    val result = store.addTag(item.id, name)
                                    val message = if (result.isSuccess) "「$name」を追加しました"
                                    else "追加できません: ${result.exceptionOrNull()?.message}"
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }) { Text(name) }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("生成元プロンプト", style = MaterialTheme.typography.labelLarge)
                    val sourcePrompt = store.prompt(item.sourcePromptId.takeIf { it > 0L })
                    Text(
                        sourcePrompt?.name ?: "未設定",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { askPrompt = true }) { Text("選ぶ") }
                        if (sourcePrompt != null) {
                            OutlinedButton(onClick = {
                                if (store.fixedPrompts.any { it.id == sourcePrompt.id }) {
                                    store.setActiveFixed(sourcePrompt.id)
                                } else {
                                    store.setActiveTemp(sourcePrompt.id)
                                }
                                Toast.makeText(context, "プロンプトタブに設定しました", Toast.LENGTH_SHORT)
                                    .show()
                            }) { Text("このプロンプトを使う") }
                            TextButton(onClick = { store.updateSourcePrompt(item.id, 0L) }) {
                                Text("外す")
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { confirmRemove = true }
            ) { Text("一覧から外す") }

            if (canDeleteOriginal(context, item)) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = { confirmOriginal = true }
                ) {
                    Text(
                        if (busy) "処理中…"
                        else if (store.useTrash) "ゴミ箱へ移動（端末から削除）"
                        else "端末から直接削除"
                    )
                }
                if (busy) {
                    CircularProgressIndicator()
                }
                Text(
                    if (store.useTrash) {
                        "原本はゴミ箱へコピーしてから端末で削除します。ゴミ箱の保存先は" +
                            (if (store.trashTreeUri.isNullOrEmpty()) "アプリ内" else "選択したフォルダ") +
                            "、保持期間は${store.retentionDays}日です。"
                    } else {
                        "設定でゴミ箱を使わない指定になっています。原本は復元できません。"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "この素材は端末から削除できません。設定でファイル権限を付与するか、書き込みを許可できる取り込み元を選んでください。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("一覧から外す") },
            text = { Text("このアプリの一覧から外します。端末内の原本は残ります。") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteMedia(item.id)
                    confirmRemove = false
                    onBack()
                }) { Text("外す") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("キャンセル") }
            }
        )
    }

    if (confirmOriginal) {
        AlertDialog(
            onDismissRequest = { confirmOriginal = false },
            title = { Text(if (store.useTrash) "ゴミ箱へ移動" else "直接削除") },
            text = {
                Text(
                    if (store.useTrash) {
                        "「${item.name}」をゴミ箱へ移し、端末の原本を削除します。" +
                            "ゴミ箱からは${store.retentionDays}日以内なら戻せます。"
                    } else {
                        "「${item.name}」を端末から直接削除します。取り消せません。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOriginal = false
                    requestAuthThenDelete()
                }) { Text(if (store.useTrash) "移動する" else "削除する") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOriginal = false }) { Text("キャンセル") }
            }
        )
    }

    if (askPin) {
        PinDialog(
            title = "削除の確認",
            onDismiss = { askPin = false },
            onSubmit = { pin ->
                if (store.verifyPin(pin)) {
                    askPin = false
                    runDelete()
                    true
                } else {
                    false
                }
            }
        )
    }

    if (askPrompt) {
        AlertDialog(
            onDismissRequest = { askPrompt = false },
            title = { Text("生成元プロンプト") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (store.allPrompts().isEmpty()) {
                        Text("プロンプトが登録されていません。")
                    }
                    store.allPrompts().forEach { prompt ->
                        TextButton(onClick = {
                            store.updateSourcePrompt(item.id, prompt.id)
                            askPrompt = false
                        }) { Text(prompt.name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { askPrompt = false }) { Text("閉じる") }
            }
        )
    }
}

@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("パスコードを入力してください。")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) pin = input
                        error = false
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error) {
                    Text("パスコードが違います", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 4,
                onClick = {
                    if (!onSubmit(pin)) {
                        error = true
                        pin = ""
                    }
                }
            ) { Text("確認") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun TrashScreen(store: Store, modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf<TrashItem?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("ゴミ箱") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                }
            },
            actions = {
                if (store.trash.isNotEmpty()) {
                    TextButton(onClick = { confirmEmpty = true }) { Text("空にする") }
                }
            }
        )
        Text(
            "保存先：" + (if (store.trashTreeUri.isNullOrEmpty()) "アプリ内" else "選択したフォルダ") +
                " / 保持期間：${store.retentionDays}日",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall
        )
        if (store.trash.isEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("ゴミ箱は空です。")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            store.trash.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .height(64.dp)
                                .aspectRatio(1f)
                                .background(Color(0xFFE0E0E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.kind == MEDIA_IMAGE) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = "動画")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "あと${daysLeft(item.expireAt)}日で完全削除",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { store.restoreFromTrash(item) }) {
                                    Text("復元")
                                }
                                OutlinedButton(onClick = { shareUri(context, item.uri, item.kind) }) {
                                    Text("送る")
                                }
                                TextButton(onClick = { confirmPurge = item }) { Text("完全削除") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("ゴミ箱を空にする") },
            text = { Text("ゴミ箱のファイルをすべて完全に削除します。取り消せません。") },
            confirmButton = {
                TextButton(onClick = {
                    store.emptyTrash()
                    confirmEmpty = false
                }) { Text("空にする") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("キャンセル") }
            }
        )
    }

    val purgeTarget = confirmPurge
    if (purgeTarget != null) {
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("完全削除") },
            text = { Text("「${purgeTarget.name}」を完全に削除します。取り消せません。") },
            confirmButton = {
                TextButton(onClick = {
                    store.purgeOne(purgeTarget)
                    confirmPurge = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = null }) { Text("キャンセル") }
            }
        )
    }
}
