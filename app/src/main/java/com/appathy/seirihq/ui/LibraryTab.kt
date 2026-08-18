@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.appathy.seirihq.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.appathy.seirihq.data.Chara
import com.appathy.seirihq.data.LibraryClip
import com.appathy.seirihq.data.PHOTO_STATUSES
import com.appathy.seirihq.data.Photo
import com.appathy.seirihq.data.PhotoGroup
import com.appathy.seirihq.data.PhotoStore
import com.appathy.seirihq.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed class LibraryRoute {
    data object Groups : LibraryRoute()
    data class GroupDetail(val id: Long) : LibraryRoute()
    data class CharaDetail(val id: Long, val groupId: Long) : LibraryRoute()
    data class PhotoDetail(val id: Long, val charaId: Long, val groupId: Long) : LibraryRoute()
}

private val SUGGESTED_TAGS = listOf("人", "景色", "リアル", "イラスト", "使用中", "未使用")

private fun pickedName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
        ?: return uri.lastPathSegment ?: "photo"
    cursor.use {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) return it.getString(index) ?: "photo"
    }
    return uri.lastPathSegment ?: "photo"
}

@Composable
fun LibraryTab(store: Store, modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<LibraryRoute>(LibraryRoute.Groups) }
    val library = store.library

    BackHandler(enabled = route !is LibraryRoute.Groups) {
        route = when (val current = route) {
            is LibraryRoute.PhotoDetail -> LibraryRoute.CharaDetail(current.charaId, current.groupId)
            is LibraryRoute.CharaDetail -> LibraryRoute.GroupDetail(current.groupId)
            else -> LibraryRoute.Groups
        }
    }

    when (val current = route) {
        is LibraryRoute.Groups -> GroupListScreen(
            store = store,
            modifier = modifier,
            onOpen = { route = LibraryRoute.GroupDetail(it) }
        )

        is LibraryRoute.GroupDetail -> GroupDetailScreen(
            store = store,
            groupId = current.id,
            modifier = modifier,
            onBack = { route = LibraryRoute.Groups },
            onOpenChara = { route = LibraryRoute.CharaDetail(it, current.id) }
        )

        is LibraryRoute.CharaDetail -> CharaDetailScreen(
            store = store,
            charaId = current.id,
            groupId = current.groupId,
            modifier = modifier,
            onBack = { route = LibraryRoute.GroupDetail(current.groupId) },
            onOpenPhoto = {
                route = LibraryRoute.PhotoDetail(it, current.id, current.groupId)
            }
        )

        is LibraryRoute.PhotoDetail -> PhotoDetailScreen(
            store = store,
            photoId = current.id,
            charaId = current.charaId,
            modifier = modifier,
            onBack = { route = LibraryRoute.CharaDetail(current.charaId, current.groupId) }
        )
    }

    LaunchedEffect(route) {
        when (val current = route) {
            is LibraryRoute.Groups -> library.reloadGroups()
            is LibraryRoute.GroupDetail -> library.loadCharas(current.id)
            is LibraryRoute.CharaDetail -> library.loadPhotos(current.id)
            is LibraryRoute.PhotoDetail -> library.loadPhotos(current.charaId)
        }
    }
}

@Composable
private fun GroupListScreen(store: Store, modifier: Modifier, onOpen: (Long) -> Unit) {
    val library = store.library
    var addGroup by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf<String?>(null) }

    val tags = library.groupTagNames()
    val visible = library.groups.filter { group ->
        tagFilter == null || group.tags.contains(tagFilter)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("ライブラリ") },
            actions = {
                IconButton(onClick = { addGroup = true }) {
                    Icon(Icons.Default.Add, contentDescription = "グループを追加")
                }
            }
        )
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = tagFilter == null,
                    onClick = { tagFilter = null },
                    label = { Text("すべて") }
                )
                tags.forEach { tag ->
                    FilterChip(
                        selected = tagFilter == tag,
                        onClick = { tagFilter = if (tagFilter == tag) null else tag },
                        label = { Text(tag) }
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("グループがありません。")
                Text(
                    "右上の＋でグループ（ゲームAなど）を作り、その中にキャラクターを追加します。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visible.forEach { group ->
                GroupCard(group = group, onClick = { onOpen(group.id) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (addGroup) {
        NameDialog(
            title = "グループを追加",
            label = "グループ名",
            placeholder = "例: ゲームA",
            onDismiss = { addGroup = false },
            onConfirm = {
                library.addGroup(it)
                addGroup = false
            }
        )
    }
}

@Composable
private fun GroupCard(group: PhotoGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${group.charaCount} キャラ / ${group.photoCount} 枚",
                style = MaterialTheme.typography.bodySmall
            )
            if (group.tags.isNotEmpty()) {
                Text(
                    group.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun GroupDetailScreen(
    store: Store,
    groupId: Long,
    modifier: Modifier,
    onBack: () -> Unit,
    onOpenChara: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = store.library
    val group = library.group(groupId)
    var addChara by remember { mutableStateOf(false) }
    var addTag by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(group?.name ?: "グループ") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                }
            },
            actions = {
                IconButton(onClick = { addChara = true }) {
                    Icon(Icons.Default.Add, contentDescription = "キャラクターを追加")
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("タグ", style = MaterialTheme.typography.labelLarge)
                    if (group?.tags.isNullOrEmpty()) {
                        Text("タグはまだありません。", style = MaterialTheme.typography.bodySmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group?.tags?.forEach { tag ->
                            TextButton(onClick = { library.removeGroupTag(groupId, tag) }) {
                                Text("#$tag ×")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SUGGESTED_TAGS.filter { group?.tags?.contains(it) != true }.forEach { tag ->
                            OutlinedButton(onClick = { library.addGroupTag(groupId, tag) }) {
                                Text(tag)
                            }
                        }
                        OutlinedButton(onClick = { addTag = true }) { Text("＋ 自由入力") }
                    }
                }
            }

            Text("キャラクター", style = MaterialTheme.typography.labelLarge)
            if (library.charas.isEmpty()) {
                Text(
                    "右上の＋でキャラクターを追加し、その中に画像を取り込みます。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            library.charas.forEach { chara ->
                CharaCard(chara = chara, onClick = { onOpenChara(chara.id) })
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("バックアップ", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (library.backupTreeUri.isNullOrEmpty()) {
                            "設定でバックアップ先フォルダを選ぶと、ここから書き出せます。"
                        } else {
                            "グループ名／キャラクター名のフォルダを作って複製します。既にある同名ファイルは飛ばします。"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !library.backupTreeUri.isNullOrEmpty() && busy.isEmpty(),
                        onClick = {
                            busy = "0"
                            scope.launch {
                                val items = withContext(Dispatchers.IO) {
                                    library.photosOfGroup(groupId)
                                }
                                val result = withContext(Dispatchers.IO) {
                                    PhotoStore.backup(
                                        context,
                                        library.backupTreeUri,
                                        group?.name ?: "group",
                                        items
                                    ) { done -> busy = done.toString() }
                                }
                                busy = ""
                                val message = result.getOrNull()?.let { "${it}件を書き出しました" }
                                    ?: (result.exceptionOrNull()?.message ?: "書き出せませんでした")
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    ) { Text(if (busy.isEmpty()) "このグループを書き出す" else "書き出し中 $busy") }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { renaming = true }) { Text("グループ名を変更") }
                TextButton(onClick = { confirmDelete = true }) { Text("グループを削除") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (addChara) {
        NameDialog(
            title = "キャラクターを追加",
            label = "名前",
            placeholder = "例: キャラクターA",
            onDismiss = { addChara = false },
            onConfirm = {
                library.addChara(groupId, it)
                addChara = false
            }
        )
    }

    if (addTag) {
        NameDialog(
            title = "タグを追加",
            label = "タグ名",
            placeholder = "例: 差分あり",
            onDismiss = { addTag = false },
            onConfirm = {
                library.addGroupTag(groupId, it)
                addTag = false
            }
        )
    }

    if (renaming) {
        NameDialog(
            title = "グループ名を変更",
            label = "グループ名",
            placeholder = group?.name ?: "",
            onDismiss = { renaming = false },
            onConfirm = {
                library.renameGroup(groupId, it)
                renaming = false
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("グループを削除") },
            text = {
                Text(
                    "「${group?.name}」と、その中のキャラクター・アプリ内の画像をすべて削除します。" +
                        "取り消せません。バックアップ済みのファイルは残ります。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    library.deleteGroup(groupId)
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

@Composable
private fun CharaCard(chara: Chara, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(chara.name, style = MaterialTheme.typography.titleMedium)
            Text("${chara.photoCount} 枚", style = MaterialTheme.typography.bodySmall)
            if (chara.memo.isNotEmpty()) {
                Text(chara.memo, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CharaDetailScreen(
    store: Store,
    charaId: Long,
    groupId: Long,
    modifier: Modifier,
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = store.library
    val chara = library.chara(charaId)
    var memo by remember(charaId) { mutableStateOf(chara?.memo ?: "") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var done = 0
                var failed = 0
                var firstError = ""
                uris.forEach { uri ->
                    progress = "${done + failed + 1} / ${uris.size}"
                    val name = withContext(Dispatchers.IO) {
                        runCatching { pickedName(context, uri) }.getOrDefault("photo")
                    }
                    val result = withContext(Dispatchers.IO) {
                        library.importPhoto(charaId, uri, name)
                    }
                    if (result.isSuccess) {
                        done++
                    } else {
                        failed++
                        if (firstError.isEmpty()) {
                            firstError = result.exceptionOrNull()?.message ?: "原因不明"
                        }
                    }
                    library.loadPhotos(charaId)
                }
                library.reloadGroups()
                progress = ""
                val message = if (failed == 0) {
                    "${done}件を取り込みました"
                } else {
                    "${done}件成功 / ${failed}件失敗: $firstError"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val visible = library.photos.filter { statusFilter == null || it.status == statusFilter }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(chara?.name ?: "キャラクター") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                }
            },
            actions = {
                IconButton(
                    enabled = progress.isEmpty(),
                    onClick = { picker.launch(arrayOf("image/*")) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "取り込み")
                }
            }
        )
        OutlinedTextField(
            value = memo,
            onValueChange = { if (it.length <= 30) memo = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            label = { Text("メモ") },
            supportingText = { Text("${memo.length} / 30") },
            singleLine = true
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {
                library.updateChara(charaId, groupId, chara?.name ?: "", memo)
                Toast.makeText(context, "メモを保存しました", Toast.LENGTH_SHORT).show()
            }) { Text("メモを保存") }
            TextButton(onClick = { confirmDelete = true }) { Text("キャラクターを削除") }
        }
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text("すべて ${library.photos.size}") }
            )
            PHOTO_STATUSES.forEach { status ->
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { statusFilter = if (statusFilter == status) null else status },
                    label = { Text(status) }
                )
            }
        }
        if (progress.isNotEmpty()) {
            Text(
                "取り込み中 $progress",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (visible.isEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("画像がありません。右上の＋から取り込んでください。")
                Text(
                    "取り込んだ画像は圧縮してアプリ内に保存されます。端末の元ファイルは変更しません。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visible, key = { it.id }) { photo ->
                    PhotoThumb(photo = photo, onClick = { onOpenPhoto(photo.id) })
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("キャラクターを削除") },
            text = {
                Text(
                    "「${chara?.name}」とアプリ内の画像 ${library.photos.size} 枚を削除します。" +
                        "取り消せません。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    library.deleteChara(charaId)
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

@Composable
private fun PhotoThumb(photo: Photo, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFFE0E0E0))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = File(photo.path),
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (photo.status != PHOTO_STATUSES[0]) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color(0xCC000000))
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    photo.status,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (photo.memo.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    photo.memo,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PhotoDetailScreen(
    store: Store,
    photoId: Long,
    charaId: Long,
    modifier: Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val library = store.library
    val photo = library.photos.firstOrNull { it.id == photoId }
    if (photo == null) {
        LaunchedEffect(photoId) { onBack() }
        return
    }
    var memo by remember(photo.id) { mutableStateOf(photo.memo) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("画像") },
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
            AsyncImage(
                model = File(photo.path),
                contentDescription = photo.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .background(Color(0xFFE0E0E0))
            )
            Text(photo.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                PhotoStore.formatSize(photo.bytes),
                style = MaterialTheme.typography.bodySmall
            )

            Text("ステータス", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PHOTO_STATUSES.forEach { status ->
                    FilterChip(
                        selected = photo.status == status,
                        onClick = { library.updatePhoto(photo.id, charaId, memo, status) },
                        label = { Text(status) }
                    )
                }
            }

            OutlinedTextField(
                value = memo,
                onValueChange = { if (it.length <= 30) memo = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("メモ") },
                supportingText = { Text("${memo.length} / 30") },
                singleLine = true
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    library.updatePhoto(photo.id, charaId, memo, photo.status)
                    Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
                }) { Text("メモを保存") }
                OutlinedButton(onClick = {
                    val result = LibraryClip.copy(context, photo)
                    val message = if (result.isSuccess) "コピーしました"
                    else (result.exceptionOrNull()?.message ?: "コピーできませんでした")
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }) { Text("画像をコピー") }
                TextButton(onClick = { confirmDelete = true }) { Text("削除") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("画像を削除") },
            text = { Text("アプリ内から削除します。取り消せません。") },
            confirmButton = {
                TextButton(onClick = {
                    library.deletePhoto(photo)
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

@Composable
private fun NameDialog(
    title: String,
    label: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) }
            ) { Text("決定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
