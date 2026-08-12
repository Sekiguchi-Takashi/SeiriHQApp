@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appathy.seirihq.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.appathy.seirihq.data.Project
import com.appathy.seirihq.data.Store

@Composable
fun SettingsTab(store: Store, modifier: Modifier = Modifier) {
    var newProject by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Project?>(null) }

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
                    Text("この版でできること", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("・①固定30件 / ②個別切替20件 / ③任意入力の合成とコピー")
                    Text("・素材の取り込み、Inbox一覧、状態・タグ・プロジェクト付与、検索")
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
                        "取り込みはファイル選択（SAF）で行うため、写真・動画の読み取り権限を要求しません。素材は参照として登録され、原本は移動もコピーもされません。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
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
