package com.winlator.star.ui.simple

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winlator.star.XServerDisplayActivity
import com.winlator.star.XrActivity
import com.winlator.star.container.Container
import com.winlator.star.container.ContainerLayerUpdater
import com.winlator.star.container.ContainerManager
import com.winlator.star.container.Shortcut
import com.winlator.star.core.GameSaveBackup
import com.winlator.star.core.SaveLocator
import com.winlator.star.store.download.InstallProgressDialog
import com.winlator.star.ui.components.EmuAccountConflictDialog
import com.winlator.star.ui.deck.settings.DeckSettingsRoutes
import com.winlator.star.ui.screens.ContainersViewModel
import com.winlator.star.ui.screens.GameSavesDialog
import com.winlator.star.ui.screens.SaveFlow
import com.winlator.star.ui.screens.SaveFlowProgressDialog
import com.winlator.star.ui.screens.runScopedBackup
import com.winlator.star.util.InAppFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Containers: each container (A = its settings, X = start its desktop, update or revert its layer, duplicate, back up or
 * restore saves, export, storage, remove), backups to import, and the defaults. Classic's Containers screen as Lab lists:
 * the same view model, layer updater, save flow steps and dialogs.
 */
@Composable
internal fun SimpleContainersPage(onNavigate: (String) -> Unit, onBack: () -> Unit, vm: ContainersViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val containers by vm.containers.collectAsState()
    val message by vm.message.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val layerUpdates by vm.layerUpdates.collectAsState()
    val layerUpdateRemote by vm.layerUpdateRemote.collectAsState()
    val layerDownload by vm.layerDownload.collectAsState()
    val layerSnapshots by vm.layerSnapshots.collectAsState()
    val layerCurrentMissing by vm.layerCurrentMissing.collectAsState()
    val layerBusy by vm.layerBusy.collectAsState()
    var backups by remember { mutableStateOf(emptyList<File>()) }
    fun reloadBackups() {
        scope.launch { backups = withContext(Dispatchers.IO) { runCatching { vm.availableBackups() }.getOrDefault(emptyList()) } }
    }
    OnResume { vm.refresh(); reloadBackups() }
    LaunchedEffect(message) { message?.let { toast(context, it); vm.messageShown() } }

    // A duplicate, export, import or remove (vm.isLoading), a layer job, or a save backup / restore: one at a time.
    var working by remember { mutableStateOf<String?>(null) }
    var saveBusy by remember { mutableStateOf<String?>(null) }
    val busy by rememberUpdatedState(isLoading || layerBusy != null || layerDownload != null || saveBusy != null)
    fun guarded(label: String? = null, job: () -> Unit): () -> Unit = {
        if (busy) toast(context, L("等待当前任务完成"))
        else { working = label; job() }
    }

    var saveFlow by remember { mutableStateOf<SaveFlow?>(null) }
    var emuConflicts by remember { mutableStateOf<List<GameSaveBackup.EmuIdConflict>>(emptyList()) }
    var restoreTarget by remember { mutableStateOf<Container?>(null) }
    var storageFor by remember { mutableStateOf<Container?>(null) }
    fun restoreFrom(uri: Uri?) {
        val target = restoreTarget
        restoreTarget = null
        if (uri != null && target != null) saveFlow = SaveFlow.Confirm(target, uri, GameSaveBackup.gameNameFromUri(context, uri))
    }
    val restoreSystem = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> restoreFrom(uri) }
    val restoreInApp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        restoreFrom(if (r.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedUri(r.data) else null)
    }

    fun startDesktop(c: Container) {
        val activity = context as? Activity ?: return
        if (!XrActivity.isEnabled(context)) {
            val intent = Intent(context, XServerDisplayActivity::class.java).putExtra("container_id", c.id)
            if (c.displayBackend == Container.DISPLAY_BACKEND_WAYLAND) intent.putExtra("wayland_mode", true)
            activity.startActivity(intent)
        } else XrActivity.openIntent(activity, c.id, null)
    }

    // The games of every container, read once off the main thread.
    var gameCounts by remember { mutableStateOf<Map<Int, Int>?>(null) }
    LaunchedEffect(containers) {
        gameCounts = withContext(Dispatchers.IO) {
            runCatching { ContainerManager(context).loadShortcuts().groupingBy { it.container.id }.eachCount() }.getOrNull()
        }
    }

    val entries = containers.map { c ->
        val games = gameCounts?.let { it[c.id] ?: 0 }
        // An installed newer layer wins over a catalog one (as Classic).
        val installed = layerUpdates[c.id]
        val remote = if (installed == null) layerUpdateRemote[c.id] else null
        val target = installed ?: remote?.entryName
        val snapshot = layerSnapshots[c.id]
        MenuEntry(
            key = "c${c.id}",
            title = c.name,
            subtitle = listOfNotNull(c.wineVersion, games?.let { if (it == 1) L("1 game") else L("%1\$s games", it) }).joinToString(" · "),
            trailing = if (c.displayBackend == Container.DISPLAY_BACKEND_WAYLAND) "Wayland" else "X11",
            pills = if (target != null) listOf(L("Layer update")) else emptyList(),
            icon = Icons.Filled.Inventory2,
            onA = { onNavigate(DeckSettingsRoutes.container(c.id)) },
            play = { startDesktop(c) },
            actions = buildList {
                add(MenuAction(L("Settings"), { onNavigate(DeckSettingsRoutes.container(c.id)) }))
                add(MenuAction(L("Start its Windows desktop"), { startDesktop(c) }))
                if (target != null) {
                    val newLabel = ContainerLayerUpdater.layerLabel(target, c.wineVersion)
                    val oldLabel = ContainerLayerUpdater.layerLabel(c.wineVersion, target)
                    val body = listOfNotNull(
                        L("%1\$s 从 %2\$s 升级到 %3\$s。游戏、存档、快捷方式和设置全部保留。", c.name, c.wineVersion, target),
                        remote?.let { r -> r.sizeBytes?.let { L("先下载层（%1\$s）。", ContainerLayerUpdater.formatSize(it)) } ?: L("先下载层。") },
                        if (c.id in layerCurrentMissing) L("%1\$s 已不再安装，因此无法回退。", oldLabel)
                        else L("%1\$s 保持已安装，随时可以回退。", oldLabel),
                        L("请先关闭其中正在运行的内容。"),
                    ).joinToString(" ")
                    add(
                        MenuAction(
                            if (remote != null) L("Download and update layer to %1\$s", newLabel) else L("更新层到 %1\$s", newLabel),
                            guarded { if (remote != null) vm.downloadAndUpdateLayer(c, remote) else vm.updateLayer(c, target) },
                            confirm = body,
                        ),
                    )
                }
                if (snapshot != null) {
                    val oldLabel = ContainerLayerUpdater.layerLabel(snapshot.oldEntry, c.wineVersion)
                    add(
                        MenuAction(
                            L("将层回退到 %1\$s", oldLabel),
                            guarded { vm.revertLayer(c, snapshot) },
                            confirm = L(
                                "%1\$s 从 %2\$s 回退到 %3\$s。注册表会恢复为更新前备份的内容；游戏、存档、快捷方式和设置全部保留。%4\$s 仍需保持已安装。",
                                c.name, c.wineVersion, snapshot.oldEntry, oldLabel,
                            ),
                        ),
                    )
                }
                // The view model posts "Container duplicated" or "Couldn't duplicate container" itself.
                add(MenuAction(L("复制"), guarded(L("正在复制 %1\$s…", c.name)) { vm.duplicate(c) {} }, confirm = L("Make a copy of %1\$s, with its games and settings?", c.name)))
                add(MenuAction(L("备份存档…"), guarded { saveFlow = SaveFlow.BackupScope(c) }))
                add(MenuAction(L("恢复存档…"), guarded { saveFlow = SaveFlow.RestoreSource(c) }))
                add(
                    MenuAction(L("导出"), guarded(L("正在导出 %1\$s…", c.name)) {
                        vm.exportContainer(c) { path ->
                            (context as? Activity)?.runOnUiThread {
                                toast(context, when {
                                    path != null -> L("Exported to %1\$s", path)
                                    vm.availableBackups().any { it.name == c.rootDir.name } -> L("A backup named %1\$s is already in Backups", c.rootDir.name)
                                    else -> L("Export failed")
                                })
                                reloadBackups()
                            }
                        }
                    }),
                )
                add(MenuAction(L("存储信息"), { storageFor = c }))
                add(
                    MenuAction(
                        L("移除"), guarded(L("正在移除 %1\$s…", c.name)) { vm.remove(c, context) { toast(context, L("Removed %1\$s", c.name)) } },
                        danger = true, confirm = L("Remove %1\$s and everything in it? Its games' shortcuts go too.", c.name),
                    ),
                )
            },
        )
    }
    SimpleMenuPage(
        overline = L("Manage"),
        title = L("容器管理"),
        sections = listOf(
            MenuSection(
                "containers", L("容器管理"), Icons.Filled.Inventory2, entries,
                empty = L("No containers yet."),
                primary = MenuAction(L("New container"), {
                    if (com.winlator.star.xenvironment.ImageFs.find(context).isValid()) onNavigate(DeckSettingsRoutes.container(DeckSettingsRoutes.NEW_CONTAINER_ID))
                    else toast(context, L("The Windows system files are not installed yet."))
                }),
            ),
            MenuSection(
                "import", L("备份"), Icons.Filled.Restore,
                backups.map { f ->
                    MenuEntry(
                        "b${f.absolutePath}", f.name, f.parent, icon = Icons.Filled.Restore,
                        actions = listOf(
                            MenuAction(
                                L("Import this container"),
                                // The view model calls back only when the import worked; a failure shows its message.
                                guarded(L("正在导入 %1\$s…", f.name)) { vm.importContainer(f) { (context as? Activity)?.runOnUiThread { toast(context, L("Imported %1\$s", f.name)) } } },
                                confirm = L("Import %1\$s as a new container?", f.name),
                            ),
                        ),
                    )
                },
                empty = L("No container backups in Downloads/Winlator/Backups/Containers."),
            ),
            MenuSection(
                "defaults", L("Defaults"), Icons.Filled.Tune,
                listOf(MenuEntry("defaults", L("新容器默认值"), L("What every new container starts with"), icon = Icons.Filled.Tune, onA = { onNavigate(DeckSettingsRoutes.DEFAULTS) })),
            ),
        ),
        onBack = onBack,
        banner = if (isLoading) working ?: L("正在处理…") else null,
    )

    // ── Backup / Restore save: Classic's steps (SaveFlow), as Lab lists where it is a choice ──
    when (val flow = saveFlow) {
        null, is SaveFlow.Fork -> {}
        is SaveFlow.BackupScope -> SimpleActionSheet(
            title = L("备份存档"),
            message = L("有些游戏把存档放在别处（安装文件夹、注册表、Steam 云）。删除任何东西前先检查备份。"),
            actions = listOf(
                MenuAction(L("全部游戏存档（整个配置）"), { saveFlow = SaveFlow.BackupFormat(flow.container, null, flow.container.name) }),
                MenuAction(L("指定游戏…"), { saveFlow = SaveFlow.GamePicker(flow.container) }),
            ),
            onDismiss = { saveFlow = null },
        ) { it.run() }
        is SaveFlow.GamePicker -> {
            val c = flow.container
            val shortcuts by produceState<List<Shortcut>?>(null, c.id) {
                value = withContext(Dispatchers.IO) { runCatching { vm.shortcutsFor(c) }.getOrDefault(emptyList()) }
            }
            val list = shortcuts
            key(list?.size ?: -1) {
                SimpleActionSheet(
                    title = L("选择一个游戏"),
                    actions = list.orEmpty().map { sc ->
                        MenuAction(sc.name, {
                            saveFlow = SaveFlow.GameSaves(
                                container = c,
                                gameName = sc.name,
                                gameKey = SaveLocator.gameKey(sc.wmClass, sc.file.name),
                                exePath = sc.path ?: "",
                                wmClass = sc.wmClass ?: "",
                                appIdExtra = sc.getExtra("steamAppId"),
                            )
                        })
                    },
                    message = when {
                        list == null -> L("Loading the list…")
                        list.isEmpty() -> L("此容器中未找到游戏。")
                        else -> null
                    },
                    onDismiss = { saveFlow = null },
                ) { it.run() }
            }
        }
        is SaveFlow.GameSaves -> GameSavesDialog(
            container = flow.container,
            gameName = flow.gameName,
            gameKey = flow.gameKey,
            exePath = flow.exePath,
            wmClass = flow.wmClass,
            appIdExtra = flow.appIdExtra,
            onBack = { saveFlow = SaveFlow.GamePicker(flow.container) },
            onCancel = { saveFlow = null },
            onNext = { roots, appId -> saveFlow = SaveFlow.BackupFormat(flow.container, roots, flow.gameName, appId) },
        )
        is SaveFlow.BackupFormat -> {
            fun backup(layout: GameSaveBackup.BackupLayout) =
                runScopedBackup(context, flow, layout, { saveFlow = null }, { saveBusy = it }, { r -> r?.let { toast(context, it) } })
            SimpleActionSheet(
                title = L("备份格式"),
                message = L("此备份用于哪个工具?"),
                actions = listOf(
                    MenuAction(L("GameHub-compatible .zip (default)"), { backup(GameSaveBackup.BackupLayout.GAMEHUB) }),
                    MenuAction(L("Winlator-native .zip"), { backup(GameSaveBackup.BackupLayout.WINLATOR) }),
                ),
                onDismiss = { saveFlow = null },
            ) { it.run() }
        }
        is SaveFlow.RestoreSource -> SimpleActionSheet(
            title = L("Restore a save"),
            actions = listOf(
                MenuAction(L("GameHub backup (.zip)"), {
                    restoreTarget = flow.container
                    saveFlow = null
                    restoreInApp.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.SAVE, L("Select backup (.zip)")))
                }),
                MenuAction(L("从系统选取…"), {
                    restoreTarget = flow.container
                    saveFlow = null
                    restoreSystem.launch("application/zip")
                }),
            ),
            onDismiss = { saveFlow = null },
        ) { it.run() }
        is SaveFlow.Confirm -> SimpleConfirm(
            title = L("恢复"),
            body = L("将 %1\$s 的备份恢复到 %2\$s？这可能覆盖那里的存档数据。", flow.gameName, flow.container.name),
            danger = false,
            onDismiss = { saveFlow = null },
        ) {
            val c = flow.container
            saveFlow = null
            saveBusy = L("正在恢复 %1\$s…", flow.gameName)
            GameSaveBackup.restore(context, flow.uri, c) { r ->
                saveBusy = null
                toast(context, if (r.ok) L("已将 %1\$s 个文件恢复到 %2\$s", r.filesWritten, c.name) else L("恢复失败：%1\$s", r.error ?: L("unknown")))
                emuConflicts = r.emuConflicts
            }
        }
    }

    saveBusy?.let { SaveFlowProgressDialog(message = it) }
    layerBusy?.let { SaveFlowProgressDialog(message = it) }
    // The catalog download before a remote layer update; on success the view model closes it and updates.
    layerDownload?.let { st -> InstallProgressDialog(state = st, onCancel = { vm.cancelLayerDownload() }, onDismiss = { vm.layerDownloadDismissed() }) }
    storageFor?.let { c -> LabStorageDialog(container = c, onDismiss = { storageFor = null }) }
    EmuAccountConflictDialog(conflicts = emuConflicts) { applied, _ ->
        emuConflicts = emptyList()
        if (applied > 0) toast(context, L("模拟器账号已切换为备份中的账号。请重新启动游戏。"))
    }
}
