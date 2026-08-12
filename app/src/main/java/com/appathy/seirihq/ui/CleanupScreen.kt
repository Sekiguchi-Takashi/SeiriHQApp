@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appathy.seirihq.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appathy.seirihq.data.FileEntry
import com.appathy.seirihq.data.FolderCleaner
import com.appathy.seirihq.data.Store

private enum class CleanFilter { ALL, INSTALLER, ARCHIVE, MEDIA }

@Composable
fun CleanupScreen(store: Store, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf(CleanFilter.ALL) }
    var confirmDelete by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }

    fun reload() {
        entries = FolderCleaner.list(context, store.cleanTreeUri)
        selected = emptySet()
    }

    val folderPicker = rememberLauncherForActivityResult(OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            store.updateCleanTree(uri.toString())
        }
    }

    LaunchedEffect(store.cleanTreeUri) { reload() }

    val visible = entries.filter { entry ->
        when (filter) {
            CleanFilter.ALL -> true
            CleanFilter.INSTALLER -> entry.isInstaller
            CleanFilter.ARCHIVE -> entry.isArchive
            CleanFilter.MEDIA -> entry.isImage || entry.isVideo
        }
    }
    val selectedSize = entries.filter { selected.contains(it.uri) }.sumOf { it.size }

    fun runDelete() {
        val targets = selected.toList()
        val deleted = FolderCleaner.deleteNow(context, targets)
        Toast.makeText(context, "${deleted}件を削除しました", Toast.LENGTH_SHORT).show()
        reload()
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
                subtitle = "選んだファイルを直接削除します",
                onSuccess = { runDelete() },
                onFail = { askPin = true }
            )
        } else {
            askPin = true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("ダウンロード整理") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                }
            },
            actions = {
                TextButton(onClick = { reload() }) { Text("更新") }
            }
        )

        if (store.cleanTreeUri.isNullOrEmpty()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("整理するフォルダを選んでください。")
                Text(
                    "ダウンロードフォルダを選ぶと、インストーラーや圧縮ファイルをその場で削除できます。ここでの削除はゴミ箱を経由しません。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { folderPicker.launch(null) }) { Text("フォルダを選ぶ") }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("フォルダ変更") }
                OutlinedButton(onClick = {
                    selected = entries.filter { it.isInstaller || it.isArchive }
                        .map { it.uri }.toSet()
                }) { Text("インストーラーとzipを選択") }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == CleanFilter.ALL,
                    onClick = { filter = CleanFilter.ALL },
                    label = { Text("すべて") }
                )
                FilterChip(
                    selected = filter == CleanFilter.INSTALLER,
                    onClick = { filter = CleanFilter.INSTALLER },
                    label = { Text("apk") }
                )
                FilterChip(
                    selected = filter == CleanFilter.ARCHIVE,
                    onClick = { filter = CleanFilter.ARCHIVE },
                    label = { Text("zip") }
                )
                FilterChip(
                    selected = filter == CleanFilter.MEDIA,
                    onClick = { filter = CleanFilter.MEDIA },
                    label = { Text("画像・動画") }
                )
            }

            Text(
                "${visible.size} 件表示 / 選択 ${selected.size} 件 " +
                    FolderCleaner.formatSize(selectedSize),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall
            )

            if (selected.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { confirmDelete = true }) { Text("選択を直接削除") }
                    TextButton(onClick = { selected = emptySet() }) { Text("選択解除") }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visible, key = { it.uri }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(entry.uri),
                                onCheckedChange = { on ->
                                    selected = if (on) selected + entry.uri
                                    else selected - entry.uri
                                }
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    FolderCleaner.formatSize(entry.size) +
                                        if (entry.extension.isEmpty()) "" else " / ${entry.extension}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("直接削除") },
            text = {
                Text(
                    "${selected.size}件（${FolderCleaner.formatSize(selectedSize)}）を" +
                        "ゴミ箱を経由せずに削除します。取り消せません。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    requestAuthThenDelete()
                }) { Text("削除する") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") }
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
