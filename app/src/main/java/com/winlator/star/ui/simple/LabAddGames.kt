package com.winlator.star.ui.simple

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.winlator.star.androidgames.AndroidGames
import com.winlator.star.container.Container
import com.winlator.star.core.GameFolderScanner
import com.winlator.star.store.SteamSdInstall
import com.winlator.star.store.SteamStoreSearch
import com.winlator.star.store.WindowsSteamClient
import com.winlator.star.ui.screens.SteamResultThumbnail
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Lab's versions of the "添加游戏" popups (GameActionsHost, AndroidGamePicker): the same state and handlers as the stock
// and XMB dialogs they stand in for, passed as plain parameters, drawn with the Lab dialog kit (LabGameActions.kt).

/** "添加游戏": what to add (one .exe, a games folder, Android games, Windows Steam x64). */
@Composable
internal fun LabImportMethodDialog(onDismiss: () -> Unit, onExe: () -> Unit, onFolder: () -> Unit, onAndroid: () -> Unit, onWindowsSteam: () -> Unit) {
    val first = rememberLabFirstFocus()
    LabActionDialog(
        title = L("添加游戏"),
        onDismiss = onDismiss,
        buttons = { LabActionButton(L("Cancel"), onClick = onDismiss) },
    ) {
        LabActionRow(L("添加游戏EXE"), subtitle = L("选择一个游戏的.exe文件"), icon = Icons.Filled.InsertDriveFile, requester = first, onClick = onExe)
        LabActionRow(L("添加游戏文件夹"), subtitle = L("选择存放所有游戏的文件夹——每个游戏都会扫描其.exe文件"), icon = Icons.Filled.Folder, onClick = onFolder)
        LabActionRow(L("添加 Android 游戏"), subtitle = L("选择安装在此手机上的游戏 — 它们会作为普通 Android 应用打开"), icon = Icons.Filled.Android, onClick = onAndroid)
        LabActionRow(L("添加 Windows Steam 客户端 x64"), subtitle = L("Valve 的 64 位 Windows 版 Steam，从 Steam 下载"), icon = Icons.Filled.DesktopWindows, onClick = onWindowsSteam)
    }
}

/** The container a Wine import goes into. [systemPickerChecked] non-null (the .exe import): the system picker switch. */
@Composable
internal fun LabImportContainerDialog(
    containers: List<Container>,
    subtitle: String?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    systemPickerChecked: Boolean?,
    onSystemPickerChange: (Boolean) -> Unit,
) {
    LabContainerPickerDialog(
        title = L("选择容器"),
        subtitle = subtitle,
        containers = containers,
        emptyText = L("未找到容器。"),
        onPick = onPick,
        onDismiss = onDismiss,
        systemPickerChecked = systemPickerChecked,
        onSystemPickerChange = onSystemPickerChange,
    )
}

/** "正在扫描游戏": the folder being scanned; Cancel stops waiting for it. */
@Composable
internal fun LabScanProgressDialog(root: String, onCancel: () -> Unit) {
    val first = rememberLabFirstFocus()
    LabActionDialog(
        title = L("正在扫描游戏"),
        onDismiss = onCancel,
        buttons = { LabActionButton(L("Cancel"), requester = first, onClick = onCancel) },
    ) {
        LabProgressLine(L("Looking for each game's .exe"), detail = root)
    }
}

/** A scanned game's cover (its first letter while it loads or when it has none). */
@Composable
private fun LabScanCover(url: String?, name: String) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).memoryCachePolicy(CachePolicy.ENABLED).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = 36.dp, height = 50.dp).clip(SimpleShape),
        loading = { Box(Modifier.fillMaxSize().background(SimpleCard)) },
        error = {
            Box(Modifier.fillMaxSize().background(SimpleCard), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), color = SimpleMuted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
    )
}

/**
 * The review of a scanned games folder ("Found N games"): a tick row per game (its cover, name, folder and .exe; a
 * flagged pick says "Check this one") with Change beside it, [componentsFor] under it. While the import runs the list
 * gives way to its progress and nothing can be closed.
 */
@Composable
internal fun LabFolderReviewDialog(
    candidates: List<GameFolderScanner.Candidate>,
    selected: Set<String>,
    importing: Boolean,
    importProgress: Pair<Int, Int>?,
    onToggle: (GameFolderScanner.Candidate) -> Unit,
    onChangeExe: (GameFolderScanner.Candidate) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    componentsFor: @Composable (GameFolderScanner.Candidate) -> Unit,
) {
    val importable = candidates.count { !it.alreadyAdded }
    val skipped = candidates.size - importable
    val first = rememberLabFirstFocus()
    val firstPick = candidates.firstOrNull { !it.alreadyAdded }
    LabActionDialog(
        title = L("Found %1\$s", labCount(importable, "1 game", "%1\$s games")),
        subtitle = if (skipped > 0) L("%1\$s already added", skipped) else null,
        onDismiss = onCancel,
        dismissible = !importing,
        wide = true,
        scroll = false,
        buttons = {
            LabActionButton(L("Cancel"), enabled = !importing, onClick = onCancel)
            LabActionButton(
                if (importing) L("正在添加…") else L("Add %1\$s", selected.size),
                primary = true,
                enabled = selected.isNotEmpty() && !importing,
                onClick = onAdd,
            )
        },
    ) {
        if (importing) {
            val done = importProgress?.first ?: 0
            val total = importProgress?.second ?: selected.size
            LabProgressLine(
                L("Adding %1\$s…", labCount(total, "1 game", "%1\$s games")),
                fraction = if (total > 0) done.toFloat() / total else null,
                trailing = if (total > 0) "$done / $total" else null,
                detail = L("正在写入快捷方式并获取封面图"),
            )
        } else {
            val warn = MaterialTheme.colorScheme.tertiary
            LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(candidates, key = { it.exe.absolutePath }) { c ->
                    val flagged = c.uncertain && !c.alreadyAdded
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabActionRow(
                                c.name,
                                modifier = Modifier.weight(1f),
                                subtitle = c.folder.name,
                                note = when {
                                    c.alreadyAdded -> L("Already added")
                                    flagged -> L("Check this one: %1\$s", c.exe.name)
                                    else -> c.exe.name
                                },
                                noteColor = if (flagged) warn else Color.Unspecified,
                                box = c.exe.absolutePath in selected,
                                enabled = !c.alreadyAdded,
                                requester = if (c === firstPick) first else null,
                                leading = { LabScanCover(c.coverUrl, c.name) },
                            ) { onToggle(c) }
                            // Every row, flagged or not: a confident pick can still be the wrong one.
                            if (!c.alreadyAdded) LabActionButton(L("Change")) { onChangeExe(c) }
                        }
                        componentsFor(c)
                    }
                }
            }
        }
    }
}

/** The "Components (n)" row under a scanned game, and the section itself once it is open. */
@Composable
internal fun LabScanComponents(count: Int, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 48.dp)) {
        LabActionRow((if (open) "▾ " else "▸ ") + L("Components (%1\$s)", count), onClick = onToggle)
        if (open) Box(Modifier.padding(start = 8.dp, end = 4.dp, bottom = 8.dp)) { content() }
    }
}

/** Every exe found in a scanned game's folder (where it sits in the folder under its name), and Browse… for another. */
@Composable
internal fun LabExePickerDialog(
    target: GameFolderScanner.Candidate,
    options: List<File>,
    onPick: (File) -> Unit,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberLabFirstFocus()
    LabActionDialog(
        title = L("Choose the game's .exe"),
        subtitle = target.name,
        onDismiss = onDismiss,
        wide = true,
        scroll = false,
        buttons = {
            LabActionButton(L("Cancel"), onClick = onDismiss)
            // The real exe can be missing from the list if it was filtered as an installer / helper or sits deeper than the scan goes.
            LabActionButton(L("Browse…"), icon = Icons.Filled.Folder, onClick = onBrowse)
        },
    ) {
        LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(options, key = { it.absolutePath }) { exe ->
                val current = exe.absolutePath == target.exe.absolutePath
                val rel = exe.absolutePath.removePrefix(target.folder.absolutePath).removePrefix("/")
                LabActionRow(
                    exe.name,
                    subtitle = if (rel.contains('/')) rel.substringBeforeLast('/') else L("(folder root)"),
                    selected = current,
                    requester = if (current) first else null,
                ) { onPick(exe) }
            }
        }
    }
}

/**
 * "确认游戏": the game's name with Search Steam, the Steam results (the linked one first, marked), [components] under
 * them, Skip and Save.
 */
@Composable
internal fun LabConfirmGameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onNameClear: () -> Unit,
    searching: Boolean,
    onSearch: () -> Unit,
    results: List<SteamStoreSearch.SteamSuggestion>,
    linkedAppId: Int?,
    onPickResult: (SteamStoreSearch.SteamSuggestion) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    components: @Composable () -> Unit = {},
) {
    val save = rememberLabFirstFocus()
    LabActionDialog(
        title = L("确认游戏"),
        onDismiss = onDismiss,
        wide = true,
        buttons = {
            LabActionButton(L("跳过"), onClick = onSkip)
            LabActionButton(L("Save"), primary = true, requester = save, onClick = onSave)
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabActionField(name, onNameChange, placeholder = L("游戏名称"), modifier = Modifier.weight(1f), onClear = onNameClear)
            LabActionButton(if (searching) L("Searching…") else L("搜索 Steam"), enabled = !searching && name.isNotBlank(), icon = Icons.Filled.Search, onClick = onSearch)
        }
        if (results.isNotEmpty()) {
            LabActionText(L("点击结果以设置名称+封面图:"), small = true)
            results.sortedByDescending { it.appId == linkedAppId }.forEach { hit ->
                val linked = hit.appId == linkedAppId
                LabActionRow(
                    hit.name,
                    subtitle = L("App ID: %1\$s", hit.appId),
                    note = if (linked) L("✓ Linked") else null,
                    noteColor = MaterialTheme.colorScheme.primary,
                    selected = linked,
                    leading = { SteamResultThumbnail(hit.appId) },
                ) { onPickResult(hit) }
            }
        } else if (searching) {
            LabProgressLine(L("正在搜索Steam…"))
        } else {
            LabActionText(L("不是正确的游戏？编辑名称并点击搜索Steam以选择正确的游戏。"), small = true)
        }
        components()
    }
}

/**
 * Windows Steam client x64: the storage to put it on (a full one greyed out) and Download / Add, or the download's
 * progress, which can't be cancelled.
 */
@Composable
internal fun LabWindowsSteamDialog(
    title: String,
    places: List<WindowsSteamClient.Place>,
    pick: Int,
    onPick: (Int) -> Unit,
    progress: Int?,
    confirmText: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val running = progress != null
    val first = rememberLabFirstFocus(places.size)
    val buttons: @Composable RowScope.() -> Unit = {
        LabActionButton(L("Cancel"), onClick = onCancel)
        LabActionButton(confirmText, primary = true, enabled = confirmEnabled, onClick = onConfirm)
    }
    LabActionDialog(
        title = title,
        subtitle = if (running) null else L("Steam 装到哪里？"),
        onDismiss = onCancel,
        dismissible = !running,
        buttons = if (running) null else buttons,
    ) {
        if (progress != null) {
            LabProgressLine(L("Downloading Steam… %1\$s%%", progress), fraction = progress / 100f, detail = places.getOrNull(pick)?.let { L("Saving to %1\$s", L(it.label)) })
        } else if (places.isEmpty()) {
            LabProgressLine(L("正在检查存储…"))
        } else {
            places.forEachIndexed { i, place ->
                LabActionRow(
                    L(place.label),
                    subtitle = when {
                        place.hasClient -> L("Steam 已存在于此")
                        place.fits -> L("%1\$s free", SteamSdInstall.fmtBytes(place.freeBytes))
                        else -> L("Not enough space (%1\$s free)", SteamSdInstall.fmtBytes(place.freeBytes))
                    },
                    selected = pick == i,
                    enabled = place.fits,
                    requester = if (i == pick.coerceAtLeast(0)) first else null,
                ) { onPick(i) }
            }
        }
    }
}

/** "同时卸载 Steam？": what would go, or its progress. Keeping is the default. */
@Composable
internal fun LabSteamUninstallDialog(plan: WindowsSteamClient.UninstallPlan?, running: Boolean, onUninstall: (WindowsSteamClient.UninstallPlan) -> Unit, onKeep: () -> Unit) {
    val keep = rememberLabFirstFocus(plan != null)
    val buttons: @Composable RowScope.() -> Unit = {
        LabActionButton(L("Uninstall"), danger = true, enabled = plan != null) { plan?.let(onUninstall) }
        LabActionButton(L("保留 Steam"), primary = true, requester = keep, onClick = onKeep)
    }
    LabActionDialog(
        title = L("同时卸载 Steam？"),
        onDismiss = onKeep,
        dismissible = !running,
        buttons = if (running) null else buttons,
    ) {
        if (running || plan == null) {
            LabProgressLine(if (running) L("正在卸载 Steam…") else L("正在检查 Steam 文件夹…"))
        } else {
            LabActionText(L("Deletes Steam from %1\$s (%2\$s).", plan.where, SteamSdInstall.fmtBytes(plan.bytes)), muted = false)
            if (plan.games > 0) LabActionText(labCount(plan.games, "Includes 1 game installed in Steam's folder.", "Includes %1\$s games installed in Steam's folder."))
            if (plan.keptGames > 0) LabActionText(labCount(plan.keptGames, "Keeps 1 game from Bannerlator's Steam store.", "Keeps %1\$s games from Bannerlator's Steam store."))
            if (plan.keptLibraries > 0) LabActionText(labCount(plan.keptLibraries, "1 library folder with games is kept.", "%1\$s library folders with games are kept."))
        }
    }
}

/** A Lab choice chip (Games / All apps): the accent fill when it is the current one. */
@Composable
private fun LabChoiceChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(40.dp)
            .background(if (selected) accent.copy(alpha = 0.3f) else SimpleCard, SimpleShape)
            .labTile(onClick = { if (enabled) onClick() })
            .padding(horizontal = 14.dp),
    ) { Text(text, color = if (selected) SimpleStrong else SimpleInk, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

/** An installed app's icon (read off the main thread), its first letter until it is there. */
@Composable
private fun LabAppIcon(app: AndroidGames.InstalledApp) {
    val context = LocalContext.current
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<android.graphics.Bitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { AndroidGames.iconBitmap(context.applicationContext, app.packageName, iconPx) }
    }
    Box(Modifier.size(40.dp).clip(SimpleShape), contentAlignment = Alignment.Center) {
        val bmp = icon
        if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(SimpleCard), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), color = SimpleMuted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/**
 * "添加 Android 游戏": search, Games / All apps, the apps as tick rows with their icon, Add N / Cancel. [loading] while
 * the installed apps are read; [adding] while the entries are written. [countLine] is the count under the title.
 */
@Composable
internal fun LabAndroidGamePickerDialog(
    loading: Boolean,
    countLine: String,
    shown: List<AndroidGames.InstalledApp>,
    query: String,
    onQueryChange: (String) -> Unit,
    gamesOnly: Boolean,
    onGamesOnlyChange: (Boolean) -> Unit,
    selected: Set<String>,
    alreadyAdded: Set<String>,
    adding: Boolean,
    chosenCount: Int,
    onToggle: (AndroidGames.InstalledApp) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstApp = shown.firstOrNull { it.packageName !in alreadyAdded }
    LabActionDialog(
        title = L("添加 Android 游戏"),
        subtitle = if (loading) L("正在读取已安装的应用…") else countLine,
        onDismiss = onDismiss,
        dismissible = !adding,
        wide = true,
        scroll = false,
        buttons = {
            LabActionButton(L("Cancel"), enabled = !adding, onClick = onDismiss)
            LabActionButton(
                when {
                    adding -> L("正在添加…")
                    chosenCount == 0 -> L("Add")
                    else -> L("Add %1\$s", chosenCount)
                },
                primary = true,
                enabled = chosenCount > 0 && !adding,
                onClick = onAdd,
            )
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabActionField(query, onQueryChange, placeholder = L("搜索已安装的应用"), modifier = Modifier.weight(1f), onClear = { onQueryChange("") })
            LabChoiceChip(L("游戏"), selected = gamesOnly, enabled = !adding) { onGamesOnlyChange(true) }
            LabChoiceChip(L("所有应用"), selected = !gamesOnly, enabled = !adding) { onGamesOnlyChange(false) }
        }
        when {
            loading -> LabProgressLine(L("正在读取已安装的应用…"))
            adding -> LabProgressLine(L("Adding %1\$s…", labCount(chosenCount, "1 game", "%1\$s games")), detail = L("正在写入快捷方式"))
            else -> {
                if (shown.isEmpty() && gamesOnly && query.isBlank()) LabActionText(L("有些游戏不会告诉 Android 它们是游戏 — 请尝试所有应用。"), small = true)
                LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(min = 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        val added = app.packageName in alreadyAdded
                        LabActionRow(
                            app.label,
                            subtitle = if (added) L("Already added") else app.packageName,
                            box = added || app.packageName in selected,
                            enabled = !added,
                            requester = if (app === firstApp) first else null,
                            leading = { LabAppIcon(app) },
                        ) { onToggle(app) }
                    }
                }
            }
        }
    }
    // The first app once the list is there (the search field would open the keyboard).
    androidx.compose.runtime.LaunchedEffect(loading, firstApp?.packageName) {
        if (loading || firstApp == null) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        runCatching { first.requestFocus() }
    }
}
