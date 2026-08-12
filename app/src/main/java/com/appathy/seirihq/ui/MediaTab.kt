@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appathy.seirihq.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.appathy.seirihq.data.MEDIA_IMAGE
import com.appathy.seirihq.data.MEDIA_STATUSES
import com.appathy.seirihq.data.MEDIA_VIDEO
import com.appathy.seirihq.data.MediaItem
import com.appathy.seirihq.data.Store

private sealed class MediaRoute {
    data object Inbox : MediaRoute()
    data class Detail(val id: Long) : MediaRoute()
}

private fun displayName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return uri.lastPathSegment ?: "media"
    cursor.use {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) return it.getString(index) ?: "media"
    }
    return uri.lastPathSegment ?: "media"
}

private fun importUris(context: Context, store: Store, uris: List<Uri>) {
    uris.forEach { uri ->
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val type = context.contentResolver.getType(uri) ?: ""
        val kind = if (type.startsWith("video")) MEDIA_VIDEO else MEDIA_IMAGE
        store.addMedia(uri.toString(), kind, displayName(context, uri))
    }
    store.reloadMedia()
}

@Composable
fun MediaTab(store: Store, modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<MediaRoute>(MediaRoute.Inbox) }

    BackHandler(enabled = route !is MediaRoute.Inbox) { route = MediaRoute.Inbox }

    when (val r = route) {
        is MediaRoute.Inbox -> InboxScreen(
            store = store,
            modifier = modifier,
            onOpen = { route = MediaRoute.Detail(it) }
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
private fun InboxScreen(store: Store, modifier: Modifier, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) importUris(context, store, uris)
    }

    val items = store.filteredMedia(query, status)

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("素材 Inbox") },
            actions = {
                IconButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) {
                    Icon(Icons.Default.Add, contentDescription = "取り込み")
                }
            }
        )
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
    val item = store.mediaItem(id)
    if (item == null) {
        LaunchedEffect(id) { onBack() }
        return
    }
    var tags by remember(item.id) { mutableStateOf(item.tags) }
    var confirmDelete by remember { mutableStateOf(false) }

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
                onClick = { confirmDelete = true }
            ) { Text("一覧から削除") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("一覧から削除") },
            text = { Text("このアプリの一覧から外します。端末内の元ファイルは削除されません。") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteMedia(item.id)
                    confirmDelete = false
                    onBack()
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") }
            }
        )
    }
}
