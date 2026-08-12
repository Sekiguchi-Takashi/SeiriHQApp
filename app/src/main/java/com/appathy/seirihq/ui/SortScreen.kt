@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appathy.seirihq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.appathy.seirihq.data.MEDIA_IMAGE
import com.appathy.seirihq.data.MEDIA_STATUSES
import com.appathy.seirihq.data.MediaItem
import com.appathy.seirihq.data.Store
import kotlin.math.abs

@Composable
private fun Preview(item: MediaItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFE0E0E0)),
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
            AsyncImage(
                model = item.uri,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Icon(Icons.Default.PlayArrow, contentDescription = "動画")
        }
    }
}

@Composable
fun SortScreen(store: Store, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val queue = store.sortQueue()
    val item = queue.firstOrNull()
    var askProject by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("交通整理") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                }
            }
        )

        if (item == null) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("未整理の素材はありません。")
            }
        } else {
            Text(
                "残り ${queue.size} 件",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Preview(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(16.dp)
                    .pointerInput(item.id) {
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragEnd = {
                                val threshold = 100f
                                if (abs(dx) > abs(dy)) {
                                    if (dx > threshold) store.sortInto(item, MEDIA_STATUSES[2])
                                    if (dx < -threshold) store.sortInto(item, MEDIA_STATUSES[3])
                                } else {
                                    if (dy < -threshold) askProject = true
                                    if (dy > threshold) store.sortInto(item, MEDIA_STATUSES[4])
                                }
                                dx = 0f
                                dy = 0f
                            },
                            onDrag = { _, amount ->
                                dx += amount.x
                                dy += amount.y
                            }
                        )
                    }
            )
            Text(
                item.name,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "上：プロジェクト / 右：保留 / 左：アーカイブ / 下：削除候補",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { askProject = true }) { Text("プロジェクト") }
                OutlinedButton(onClick = { store.sortInto(item, MEDIA_STATUSES[2]) }) {
                    Text("保留")
                }
                OutlinedButton(onClick = { store.sortInto(item, MEDIA_STATUSES[3]) }) {
                    Text("アーカイブ")
                }
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = { store.sortInto(item, MEDIA_STATUSES[4]) }) {
                    Text("削除候補にする")
                }
            }
        }
    }

    if (askProject && item != null) {
        AlertDialog(
            onDismissRequest = { askProject = false },
            title = { Text("プロジェクトへ追加") },
            text = {
                Column {
                    if (store.projects.isEmpty()) {
                        Text("設定タブでプロジェクトを作成してください。")
                    }
                    store.projects.forEach { project ->
                        TextButton(onClick = {
                            store.sortInto(item, MEDIA_STATUSES[1], project.id)
                            askProject = false
                        }) { Text(project.name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { askProject = false }) { Text("閉じる") }
            }
        )
    }
}

@Composable
fun ProjectListScreen(
    store: Store,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("プロジェクト") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                }
            }
        )
        if (store.projects.isEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("設定タブでプロジェクトを作成してください。")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            store.projects.forEach { project ->
                val items = store.mediaOfProject(project.id)
                val unsorted = items.count { it.status == MEDIA_STATUSES[0] }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(project.id) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${items.size} 素材 / 未整理 $unsorted",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectDetailScreen(
    store: Store,
    projectId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenMedia: (Long) -> Unit
) {
    val items = store.mediaOfProject(projectId)
    var filter by remember { mutableStateOf<String?>(null) }
    val visible = items.filter { filter == null || it.status == filter }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(store.projectName(projectId)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                }
            }
        )
        Text(
            "${items.size} 素材 / 表示 ${visible.size}",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { filter = null }) { Text("すべて") }
            TextButton(onClick = { filter = MEDIA_STATUSES[0] }) { Text("未整理") }
            TextButton(onClick = { filter = MEDIA_STATUSES[1] }) { Text("整理済み") }
        }
        Spacer(Modifier.height(4.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(visible, key = { it.id }) { media ->
                Preview(
                    item = media,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onOpenMedia(media.id) }
                )
            }
        }
    }
}
