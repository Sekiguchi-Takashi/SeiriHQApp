@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appathy.seirihq.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import com.appathy.seirihq.data.SOURCE_STORE
import com.appathy.seirihq.data.Store
import com.appathy.seirihq.data.TrashFiles
import com.appathy.seirihq.data.TrashItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private sealed class MediaRoute {
    data object Inbox : MediaRoute()
    data object Trash : MediaRoute()
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
            onOpenTrash = { route = MediaRoute.Trash }
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
    onOpenTrash: () -> Unit
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

    val items = store.filteredMedia(query, status)

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("素材 Inbox") },
            actions = {
                IconButton(onClick = onOpenTrash) {
                    Icon(Icons.Default.Delete, contentDescription = "ゴミ箱")
                }
                IconButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) {
                    Icon(Icons.Default.Add, contentDescription = "取り込み")
                }
            }
        )

        if (!granted) {
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
        } else {
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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            label = { Text("検索（名前・タグ・プロジェクト）") },
            singleLine = true
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = status == null,
                onClick = { status = null },
                label = { Text("すべて") }
            )
            MEDIA_STATUSES.take(2).forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = { status = if (status == s) null else s },
                    label = { Text(s) }
                )
            }
        }
        Text(
            "${items.size} 件",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall
        )
        if (items.isEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("素材がありません。右上の＋から取り込んでください。")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    MediaThumb(item = item, onClick = { onOpen(item.id) })
                }
            }
        }
    }
}

@Composable
private fun MediaThumb(item: MediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFFE0E0E0))
            .clickable { onClick() },
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
    var tags by remember(item.id) { mutableStateOf(item.tags) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmOriginal by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingTrashUri by remember { mutableStateOf<String?>(null) }

    val systemDelete = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val trashUri = pendingTrashUri
        pendingTrashUri = null
        if (result.resultCode == Activity.RESULT_OK && trashUri != null) {
            store.moveToTrash(item, trashUri)
            Toast.makeText(context, "ゴミ箱へ移動しました", Toast.LENGTH_SHORT).show()
            onBack()
        } else if (trashUri != null) {
            TrashFiles.deleteFile(context, trashUri)
            Toast.makeText(context, "中止しました", Toast.LENGTH_SHORT).show()
        }
    }

    fun runDelete() {
        busy = true
        scope.launch {
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

            Text("状態", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MEDIA_STATUSES.take(2).forEach { s ->
                    FilterChip(
                        selected = item.status == s,
                        onClick = { store.setStatus(item.id, s) },
                        label = { Text(s) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MEDIA_STATUSES.drop(2).forEach { s ->
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
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("カンマ区切り 例: 京都,犬,旅行") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { store.setTags(item.id, tags.trim()) }) { Text("タグを保存") }
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
                ) { Text(if (busy) "移動中…" else "ゴミ箱へ移動（端末から削除）") }
                if (busy) {
                    CircularProgressIndicator()
                }
                Text(
                    "原本はゴミ箱へコピーしてから端末で削除します。ゴミ箱の保存先は" +
                        (if (store.trashTreeUri.isNullOrEmpty()) "アプリ内" else "選択したフォルダ") +
                        "、保持期間は${store.retentionDays}日です。",
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
            title = { Text("ゴミ箱へ移動") },
            text = {
                Text(
                    "「${item.name}」をゴミ箱へ移し、端末の原本を削除します。" +
                        "ゴミ箱からは${store.retentionDays}日以内なら戻せます。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOriginal = false
                    requestAuthThenDelete()
                }) { Text("移動する") }
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
            return@Column
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
