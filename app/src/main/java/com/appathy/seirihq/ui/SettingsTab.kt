@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appathy.seirihq.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import com.appathy.seirihq.data.FilePermission
import com.appathy.seirihq.data.Project
import com.appathy.seirihq.data.Store

@Composable
fun SettingsTab(store: Store, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var newProject by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Project?>(null) }
    var granted by remember { mutableStateOf(FilePermission.granted(context)) }
    var askCurrentPin by remember { mutableStateOf(false) }
    var askNewPin by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = FilePermission.granted(context)
    }

    val folderPicker = rememberLauncherForActivityResult(OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            store.setTrashTree(uri.toString())
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(title = { Text("設定") })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("プロジェクト", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "プロンプトと素材で共通に使う単位です。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newProject,
                        onValueChange = { newProject = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("新しいプロジェクト名") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = newProject.isNotBlank(),
                        onClick = {
                            store.addProject(newProject)
                            newProject = ""
                        }
                    ) { Text("追加") }
                    Spacer(Modifier.height(8.dp))
                    store.projects.forEach { project ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(project.name)
                            IconButton(onClick = { confirmDelete = project }) {
                                Icon(Icons.Default.Delete, contentDescription = "削除")
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("セキュリティ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "素材を開くにはパスコードが必要です。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (store.pinSet) "パスコード：設定済み" else "パスコード：未設定（素材タブで設定）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (store.pinSet) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { askCurrentPin = true }) { Text("パスコードを変更") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("指紋認証で解除")
                            if (!biometricAvailable(context)) {
                                Text(
                                    "この端末では指紋認証を利用できません",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Switch(
                            checked = store.biometricEnabled,
                            enabled = biometricAvailable(context) && store.pinSet,
                            onCheckedChange = { store.setBiometric(it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("削除の前に認証する", modifier = Modifier.weight(1f))
                        Switch(
                            checked = store.authOnDelete,
                            onCheckedChange = { store.setAuthOnDelete(it) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { store.lock() }) { Text("今すぐロックする") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ゴミ箱", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "削除した原本は、まずゴミ箱へコピーしてから端末で削除します。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "保存先：" + (
                            if (store.trashTreeUri.isNullOrEmpty()) "アプリ内"
                            else "選択したフォルダ"
                            ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    store.trashTreeUri?.let {
                        Text(Uri.decode(it), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { folderPicker.launch(null) }) {
                            Text("フォルダを選ぶ")
                        }
                        if (!store.trashTreeUri.isNullOrEmpty()) {
                            TextButton(onClick = { store.setTrashTree(null) }) { Text("アプリ内に戻す") }
                        }
                    }
                    Text(
                        "クラウドに置く場合は、フォルダ選択画面でクラウドのアプリが出てくればその中を、出てこない場合は同期対象のローカルフォルダを選んでください。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("保持期間", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = store.retentionDays == days,
                                onClick = { store.setRetentionDays(days) },
                                label = { Text("${days}日") }
                            )
                        }
                    }
                    Text(
                        "期限を過ぎたものはアプリ起動時に完全削除します。現在 ${store.trash.size} 件。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ファイル権限", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (granted) "付与済み：閲覧・取り込みに加えて、端末からの原本削除と一括取り込みができます。"
                        else "未付与：閲覧と取り込みのみできます。原本の削除はできません。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!granted) {
                        OutlinedButton(onClick = {
                            permissionLauncher.launch(FilePermission.required().toTypedArray())
                        }) { Text("権限を付与") }
                    } else if (!FilePermission.canDeleteOriginals(context)) {
                        Text(
                            "この Android バージョンでは、取り込み元によっては原本削除に対応できません。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("この版でできること", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("・①固定30件 / ②個別切替20件 / ③任意入力の合成とコピー")
                    Text("・素材の取り込み、Inbox一覧、状態・タグ・プロジェクト付与、検索")
                    Text("・素材はパスコードと指紋で保護")
                    Text("・権限がある場合は端末からの原本削除（ゴミ箱経由）")
                    Text("・ゴミ箱はアプリ内または選んだフォルダ。期限切れは自動で完全削除")
                    Text("・プロジェクトは両機能で共通")
                    Spacer(Modifier.height(8.dp))
                    Text("AI分類・重複検出・自然言語検索は次の段階で追加します。", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("素材の扱い", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "取り込みはファイル選択（SAF）で行い、権限がなくても利用できます。素材は参照として登録され、原本は移動もコピーもされません。「一覧から外す」は原本を残し、「端末から削除」は原本を消します。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (askCurrentPin) {
        PinDialog(
            title = "現在のパスコード",
            onDismiss = { askCurrentPin = false },
            onSubmit = { pin ->
                if (store.verifyPin(pin)) {
                    askCurrentPin = false
                    askNewPin = true
                    true
                } else {
                    false
                }
            }
        )
    }

    if (askNewPin) {
        NewPinDialog(
            onDismiss = { askNewPin = false },
            onSet = {
                store.setPin(it)
                askNewPin = false
            }
        )
    }

    val target = confirmDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("プロジェクト削除") },
            text = { Text("「${target.name}」を削除します。素材の紐づけも外れます。") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteProject(target.id)
                    confirmDelete = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun NewPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新しいパスコード") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) pin = input
                        error = false
                    },
                    label = { Text("4〜6桁") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) confirm = input
                        error = false
                    },
                    label = { Text("確認") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error) {
                    Text("2回の入力が一致しません", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 4,
                onClick = {
                    if (pin == confirm) onSet(pin) else error = true
                }
            ) { Text("設定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
