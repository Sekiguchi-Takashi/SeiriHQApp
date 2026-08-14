@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.appathy.seirihq.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.appathy.seirihq.data.KIND_FIXED
import com.appathy.seirihq.data.KIND_TEMP
import com.appathy.seirihq.data.MAX_FIXED
import com.appathy.seirihq.data.MAX_TEMP
import com.appathy.seirihq.data.PromptItem
import com.appathy.seirihq.data.PromptSet
import com.appathy.seirihq.data.Store

private sealed class PromptRoute {
    data object Top : PromptRoute()
    data class ListScreen(val kind: String) : PromptRoute()
    data class Edit(val kind: String, val id: Long?) : PromptRoute()
}

@Composable
fun PromptTab(store: Store, modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<PromptRoute>(PromptRoute.Top) }

    BackHandler(enabled = route !is PromptRoute.Top) {
        route = when (val r = route) {
            is PromptRoute.Edit -> PromptRoute.ListScreen(r.kind)
            else -> PromptRoute.Top
        }
    }

    when (val r = route) {
        is PromptRoute.Top -> PromptTopScreen(
            store = store,
            modifier = modifier,
            onOpenList = { route = PromptRoute.ListScreen(it) }
        )

        is PromptRoute.ListScreen -> PromptListScreen(
            store = store,
            kind = r.kind,
            modifier = modifier,
            onBack = { route = PromptRoute.Top },
            onAdd = { route = PromptRoute.Edit(r.kind, null) },
            onEdit = { route = PromptRoute.Edit(r.kind, it) },
            onUse = { route = PromptRoute.Top }
        )

        is PromptRoute.Edit -> PromptEditScreen(
            store = store,
            kind = r.kind,
            id = r.id,
            modifier = modifier,
            onDone = { route = PromptRoute.ListScreen(r.kind) }
        )
    }
}

@Composable
private fun PromptTopScreen(
    store: Store,
    modifier: Modifier,
    onOpenList: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showSets by remember { mutableStateOf(false) }
    var saveSet by remember { mutableStateOf(false) }
    val fixed = store.activeFixed()
    val temp = store.activeTemp()
    val composed = store.composed()

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(title = { Text("プロンプト交通整理") })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("① 固定", style = MaterialTheme.typography.labelLarge)
                    Text(fixed?.name ?: "未設定", style = MaterialTheme.typography.titleMedium)
                    Text(
                        fixed?.description ?: "一覧から選んでください",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onOpenList(KIND_FIXED) }) { Text("一覧・変更") }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (drag > 80f) store.stepTemp(-1)
                                if (drag < -80f) store.stepTemp(1)
                                drag = 0f
                            },
                            onHorizontalDrag = { _, amount -> drag += amount }
                        )
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("② 個別切替", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { store.stepTemp(-1) }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "前へ")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(temp?.name ?: "未設定", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "%02d / %d".format(store.tempPosition(), MAX_TEMP),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { store.stepTemp(1) }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "次へ")
                        }
                    }
                    Text(
                        temp?.description ?: "左右スワイプで高速切替",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onOpenList(KIND_TEMP) }) { Text("一覧・変更") }
                        if (temp != null) {
                            TextButton(onClick = { store.setActiveTemp(null) }) { Text("外す") }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("③ 任意入力", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = store.customPrompt,
                        onValueChange = { store.customPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        placeholder = { Text("今回だけの追加指示") }
                    )
                    if (store.customPrompt.isNotEmpty()) {
                        TextButton(onClick = { store.customPrompt = "" }) { Text("クリア") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("セット", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "①②とプロジェクトの組み合わせを用途別に残せます。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "作業中のプロジェクト：" + (
                            if (store.activeProjectId > 0L) store.projectName(store.activeProjectId)
                            else "なし"
                            ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (store.activeProjectId > 0L) {
                        Text(
                            "取り込んだ素材はこのプロジェクトへ自動で入ります。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = store.promptSets.isNotEmpty(),
                            onClick = { showSets = true }
                        ) { Text("セットを呼び出す ${store.promptSets.size}") }
                        OutlinedButton(onClick = { saveSet = true }) { Text("いまの状態を保存") }
                        if (store.activeProjectId > 0L) {
                            TextButton(onClick = { store.updateActiveProject(0L) }) {
                                Text("プロジェクト解除")
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("完成プロンプト", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (composed.isEmpty()) "（未入力）" else composed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = composed.isNotEmpty(),
                        onClick = {
                            clipboard.setText(AnnotatedString(composed))
                            store.noteCopiedPrompt()
                            Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("コピー") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSets) {
        AlertDialog(
            onDismissRequest = { showSets = false },
            title = { Text("セットを呼び出す") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    store.promptSets.forEach { set ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(set.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    describeSet(store, set),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = {
                                store.applyPromptSet(set)
                                showSets = false
                                Toast.makeText(context, "「${set.name}」を適用しました", Toast.LENGTH_SHORT)
                                    .show()
                            }) { Text("適用") }
                            TextButton(onClick = { store.deletePromptSet(set.id) }) { Text("削除") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSets = false }) { Text("閉じる") }
            }
        )
    }

    if (saveSet) {
        SaveSetDialog(
            store = store,
            onDismiss = { saveSet = false },
            onSave = { name, projectId ->
                store.savePromptSet(name, projectId)
                store.updateActiveProject(projectId)
                saveSet = false
                Toast.makeText(context, "「$name」を保存しました", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun describeSet(store: Store, set: PromptSet): String {
    val fixed = store.prompt(set.fixedId.takeIf { it > 0L })?.name ?: "①なし"
    val temp = store.prompt(set.tempId.takeIf { it > 0L })?.name ?: "②なし"
    val project = if (set.projectId > 0L) store.projectName(set.projectId) else "プロジェクトなし"
    return "$fixed / $temp / $project"
}

@Composable
private fun SaveSetDialog(
    store: Store,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf(store.activeProjectId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("セットとして保存") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("セット名") },
                    placeholder = { Text("例: 商品写真の生成") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text("プロジェクト（任意）", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { projectId = 0L }) {
                    Text(if (projectId == 0L) "・なし" else "なし")
                }
                store.projects.forEach { project ->
                    TextButton(onClick = { projectId = project.id }) {
                        Text(if (projectId == project.id) "・${project.name}" else project.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), projectId) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun PromptListScreen(
    store: Store,
    kind: String,
    modifier: Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onUse: () -> Unit
) {
    val items = store.promptsOf(kind)
    val limit = if (kind == KIND_FIXED) MAX_FIXED else MAX_TEMP
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<PromptItem?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(if (kind == KIND_FIXED) "① 固定プロンプト" else "② 個別切替プロンプト") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                }
            },
            actions = {
                if (kind == KIND_TEMP && items.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("全削除") }
                }
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "追加")
                }
            }
        )
        Text(
            "${items.size} / $limit 件",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(item.description, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (kind == KIND_FIXED) store.setActiveFixed(item.id)
                                else store.setActiveTemp(item.id)
                                onUse()
                            }) { Text("使用") }
                            OutlinedButton(onClick = { onEdit(item.id) }) { Text("編集") }
                            IconButton(onClick = { confirmDelete = item }) {
                                Icon(Icons.Default.Delete, contentDescription = "削除")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("すべて削除") },
            text = { Text("一時保存されている②個別切替プロンプトをすべて削除します。") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteAllTemp()
                    confirmClear = false
                }) { Text("すべて削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("キャンセル") }
            }
        )
    }

    val target = confirmDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("削除") },
            text = { Text("「${target.name}」を削除します。") },
            confirmButton = {
                TextButton(onClick = {
                    store.deletePrompt(target.id)
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
private fun PromptEditScreen(
    store: Store,
    kind: String,
    id: Long?,
    modifier: Modifier,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val existing = store.prompt(id)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(if (id == null) "新規登録" else "編集") },
            navigationIcon = {
                IconButton(onClick = onDone) {
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名前") },
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 50) description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("機能説明") },
                supportingText = { Text("${description.length} / 50文字") }
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                label = { Text("プロンプト本文") }
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && body.isNotBlank(),
                onClick = {
                    val ok = store.savePrompt(kind, id, name.trim(), description.trim(), body)
                    if (!ok) {
                        Toast.makeText(context, "上限に達しています", Toast.LENGTH_SHORT).show()
                    } else {
                        onDone()
                    }
                }
            ) { Text("保存") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
