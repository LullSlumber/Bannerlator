package com.winlator.star.ui.simple

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.winlator.star.linux.ChildProcessRestrictions
import com.winlator.star.linux.LinuxDeviceSupport
import com.winlator.star.linux.LinuxProtons
import com.winlator.star.linux.LinuxRuntimeInstaller
import com.winlator.star.linux.LinuxRuntimeUpdate
import com.winlator.star.linux.LinuxShortcuts
import com.winlator.star.store.download.DownloadScope
import com.winlator.star.ui.screens.contents.addSteamEntry
import com.winlator.star.ui.screens.contents.removeSteamEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MB = 1024L * 1024L

/**
 * Contents › Linux runtime in Lab: Classic's LinuxRuntimeTab as Lab rows, on the same calls. The two warnings (a GPU the
 * runtime cannot draw on, Android's child process restrictions with the way to Developer options), the runtime with
 * Install / Update / Remove and its progress, the Steam entry it puts in the library, and the Proton builds with Download
 * (Request for Valve's) / Remove. Nothing is fetched until the tab is first shown ([active]), as in Classic. The runtime
 * install runs on the download scope, so it finishes (and its state is cleared) even when the page is left. [refresh]
 * changes when the page is shown again (the restrictions switch may have been flipped); [onNote] shows a help text.
 */
@Composable
internal fun rememberLabLinuxRuntime(active: Boolean, refresh: Int, onNote: (LabHelpText) -> Unit): List<MenuEntry> {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val rt by LinuxRuntimeUpdate.state.collectAsState()
    var release by remember { mutableStateOf<LinuxRuntimeInstaller.Release?>(null) }
    var checking by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // The GPU check asks the driver: only once the tab is shown. Null = drawable (or not checked yet), else the GPU's name.
    var undrawableGpu by remember { mutableStateOf<String?>(null) }
    val restricted = remember(refresh) { runCatching { ChildProcessRestrictions.active(context) }.getOrDefault(false) }

    LaunchedEffect(active) {
        if (!active || started) return@LaunchedEffect
        started = true
        if (!runCatching { LinuxDeviceSupport.drawable(context) }.getOrDefault(true)) {
            undrawableGpu = runCatching { LinuxDeviceSupport.gpuName(context) }.getOrDefault("this GPU")
        }
        LinuxRuntimeUpdate.refreshInstalled(context)
        release = withContext(Dispatchers.IO) { runCatching { LinuxRuntimeInstaller.fetchRelease() }.getOrNull() }
        LinuxRuntimeUpdate.setAvailable(release)
        checking = false
    }
    val installed = rt.installed
    // As Classic on opening the tab: an installed runtime gets its Steam entry where it belongs (idempotent).
    LaunchedEffect(installed, started) {
        if (started && installed != null) withContext(Dispatchers.IO) { addSteamEntry(context) }
    }

    // ── Proton builds (only once the runtime is there) ──
    var builds by remember { mutableStateOf<List<LinuxProtons.Build>>(emptyList()) }
    var states by remember { mutableStateOf<Map<String, LinuxProtons.State>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var protonsStarted by remember { mutableStateOf(false) }
    var workingOn by remember { mutableStateOf<String?>(null) }
    var stage by remember { mutableStateOf("") }
    var percent by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorFor by remember { mutableStateOf<String?>(null) }
    var used by remember { mutableLongStateOf(0L) }
    // Reads the runtime's filesystem, which may be half-built or in use by a running session: never throws.
    suspend fun refreshProtons() {
        val read = withContext(Dispatchers.IO) {
            runCatching { builds.associate { it.name to LinuxProtons.stateOf(context, it) } to LinuxProtons.installedBytes(context) }
                .onFailure { Log.w("LabLinuxRuntime", "proton state", it) }.getOrNull()
        }
        if (read != null) { states = read.first; used = read.second }
    }
    LaunchedEffect(started, installed != null) {
        if (!started || installed == null || protonsStarted) return@LaunchedEffect
        protonsStarted = true
        builds = withContext(Dispatchers.IO) { runCatching { LinuxProtons.fetchCatalog() }.getOrDefault(emptyList()) }
        refreshProtons()
        loading = false
    }

    val busy = rt.busy || removing
    val target = release
    val canInstall = !busy && target != null && (installed == null || installed != target.version)
    fun install() {
        val r = release ?: return
        if (rt.busy || removing) return
        message = null
        LinuxRuntimeUpdate.begin()
        DownloadScope.io.launch {
            val ok = runCatching { LinuxRuntimeInstaller.install(app, r) { s, p -> LinuxRuntimeUpdate.progress(s, p) } }.getOrDefault(false)
            // The runtime is only useful with something to launch: the Steam entry appears the moment it lands.
            if (ok) runCatching { addSteamEntry(app) }
            LinuxRuntimeUpdate.finish(app)
            if (!ok) message = L("安装失败 — 现有runtime保持不变。")
        }
    }
    fun remove() {
        if (busy) return
        removing = true
        message = null
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { LinuxRuntimeInstaller.uninstall(app) }
                removeSteamEntry(app)
            }
            LinuxRuntimeUpdate.refreshInstalled(app)
            removing = false
        }
    }
    fun installProton(b: LinuxProtons.Build) {
        if (workingOn != null) return
        workingOn = b.name; error = null; errorFor = null
        stage = L("正在启动…"); percent = -1
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching { LinuxProtons.install(context, b) { s, p -> stage = s; percent = p } }.getOrElse { it.message ?: it.toString() }
            }
            error = failure
            errorFor = if (failure != null) b.name else null
            refreshProtons()
            workingOn = null
        }
    }
    fun removeProton(b: LinuxProtons.Build) {
        if (workingOn != null) return
        workingOn = b.name; error = null; errorFor = null
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { LinuxProtons.remove(context, b) } }
            refreshProtons()
            workingOn = null
        }
    }

    val gpuNote = LabHelpText(
        L("此设备无法绘制 Linux 会话"),
        L(
            "The runtime draws with Turnip, an Adreno driver, and this device reports %1\$s. A session on it comes up as sound over a black screen. You can still download the runtime, but nothing will render — Mali, Xclipse and PowerVR are not supported yet.",
            undrawableGpu ?: "",
        ),
    )
    val restrictionsText = L("一个 Linux 会话包含数十个进程 — gamescope、Steam 客户端、其 Web 辅助进程和游戏 — 而 Android 会终止应用的额外子进程。当它这样做时，会话会在没有任何错误且日志中没有任何内容的情况下结束。请在首次启动前，在开发者选项中打开 \"禁用子进程限制\"。") +
        if (ChildProcessRestrictions.hasSwitch()) "" else "\n\n" + L("This Android version has no switch for it. From a computer:") + "\n" + ChildProcessRestrictions.adbCommand()
    val restrictionsNote = LabHelpText(L("请先关闭子进程限制"), restrictionsText)
    fun openDeveloperOptions() {
        if (!ChildProcessRestrictions.openDeveloperOptions(context)) {
            onNote(LabHelpText(L("打开开发者选项"), L("Could not open Developer options. Settings → About phone → tap Build number seven times, then Developer options.")))
        }
    }
    val aboutRuntime = LabHelpText(
        L("Linux runtime"),
        L("运行Linux程序而非Windows程序：一个带有gamescope作为会话合成器的glibc rootfs，以及Valve为ARM构建的Steam客户端。游戏通过Steam自带的Proton运行，而不是通过此应用的Wine。可选——如果关闭它，其他内容不会改变。"),
    )
    val aboutProtons = LabHelpText(
        L("Proton 构建"),
        L("Steam 客户端用什么来运行 Windows 游戏。在此添加一个，它会出现在该游戏的兼容性列表中，这样您就可以为每个游戏选择不同的版本。下次打开 Steam 客户端时会解压该构建。") +
            "\n\n" + L("Steam 自己的列表还会提供一些无法在此启动游戏的 Proton，包括一些标记为 ARM64 的。能用的版本是下面列出的构建以及 \"Bannerlator Proton (ARM64)\"。"),
    )

    return buildList {
        val group = L("Linux runtime")
        if (undrawableGpu != null) add(
            MenuEntry(
                "lx-gpu", gpuNote.title, gpuNote.text, icon = Icons.Filled.Warning, group = group,
                onA = { onNote(gpuNote) },
            ),
        )
        if (restricted) add(
            MenuEntry(
                "lx-cpr", restrictionsNote.title,
                if (ChildProcessRestrictions.hasSwitch()) L("Turn on \"Disable child process restrictions\" in Developer options before the first launch.")
                else L("This Android version has no switch for it. From a computer:") + " " + ChildProcessRestrictions.adbCommand(),
                icon = Icons.Filled.Warning, group = group,
                onA = { if (ChildProcessRestrictions.hasSwitch()) openDeveloperOptions() else onNote(restrictionsNote) },
                actions = buildList {
                    if (ChildProcessRestrictions.hasSwitch()) add(MenuAction(L("打开开发者选项"), { openDeveloperOptions() }))
                    add(MenuAction(L("Why"), { onNote(restrictionsNote) }))
                },
            ),
        )
        val available = target?.let { r -> L("Available: %1\$s", r.version) + if (r.size > 0) " · " + L("%1\$s MB download", r.size / MB) else "" }
        add(
            MenuEntry(
                "lx-runtime", L("Linux runtime"),
                subtitle = when {
                    rt.busy -> LinuxRuntimeUpdate.line(rt)
                    removing -> L("Removing…")
                    message != null -> message
                    installed != null -> listOfNotNull(L("Installed: %1\$s", installed), available?.takeIf { target?.version != installed }).joinToString(" · ")
                    checking -> L("正在检查构建…")
                    target == null -> L("No build available (could not reach the catalog)")
                    else -> listOfNotNull(L("Not installed"), available).joinToString(" · ")
                },
                trailing = when {
                    rt.busy && rt.percent in 0..100 -> "${rt.percent}%"
                    installed != null -> L("Installed")
                    else -> null
                },
                current = installed != null,
                progress = if (rt.busy) (if (rt.percent in 0..100) rt.percent / 100f else 0f) else null,
                icon = Icons.Filled.Terminal, group = group,
                onA = if (canInstall) ({ install() }) else null,
                play = if (canInstall) ({ install() }) else null,
                playIcon = Icons.Filled.Download, playLabel = if (installed == null) L("Install") else L("Update"),
                actions = buildList {
                    if (canInstall) add(MenuAction(if (installed == null) L("Install") else L("Update"), { install() }))
                    if (!busy && installed != null) add(
                        MenuAction(L("移除"), { remove() }, danger = true, confirm = L("Remove the Linux runtime? Its Steam entry goes with it.")),
                    )
                    add(MenuAction(L("About the Linux runtime"), { onNote(aboutRuntime) }))
                },
            ),
        )
        if (installed == null) return@buildList
        add(
            MenuEntry(
                "lx-steam", L("\"%1\$s\" is in your library", LinuxShortcuts.STEAM_NAME),
                L("Sign in there with your Steam account; installs and updates are Steam's own."),
                icon = Icons.Filled.SportsEsports, group = group,
            ),
        )

        val pGroup = L("Proton 构建")
        add(
            MenuEntry(
                "px-about", L("Proton 构建"),
                subtitle = when {
                    loading -> L("正在检查构建…")
                    builds.isEmpty() -> L("没有可用的构建（无法访问目录）")
                    used > 0 -> L("Using %1\$s MB", used / MB)
                    else -> L("What the Steam client runs a Windows game with")
                },
                icon = Icons.Filled.Info, group = pGroup,
                onA = { onNote(aboutProtons) },
            ),
        )
        builds.forEach { b ->
            val state = states[b.name] ?: LinuxProtons.State.ABSENT
            val working = workingOn == b.name
            val stateText = when (state) {
                LinuxProtons.State.INSTALLED -> L("已添加")
                LinuxProtons.State.PENDING ->
                    if (b.isDepot) L("已请求 — 客户端将在下次会话中获取它") else L("就绪 — 下次您打开 Steam 客户端时解压")
                else -> if (b.isDepot) L("由 Steam 客户端下载") else L("%1\$s MB download", b.size / MB)
            }
            val canGet = workingOn == null && state == LinuxProtons.State.ABSENT
            val getLabel = if (b.isDepot) L("请求") else L("Download")
            add(
                MenuEntry(
                    "px-${b.name}", b.display,
                    subtitle = when {
                        working -> if (percent in 0..100) "$stage $percent%" else stage
                        errorFor == b.name && error != null -> error
                        else -> listOfNotNull(b.notes.takeIf { it.isNotBlank() }, stateText).joinToString(" · ")
                    },
                    trailing = if (working && percent in 0..100) "$percent%" else null,
                    current = state == LinuxProtons.State.INSTALLED,
                    progress = if (working) (if (percent in 0..100) percent / 100f else 0f) else null,
                    icon = Icons.Filled.Layers, group = pGroup,
                    onA = if (canGet) ({ installProton(b) }) else null,
                    play = if (canGet) ({ installProton(b) }) else null,
                    playIcon = Icons.Filled.Download, playLabel = getLabel,
                    actions = buildList {
                        if (canGet) add(MenuAction(getLabel, { installProton(b) }))
                        // Valve's depots belong to the client, which installs and removes them itself.
                        if (!b.isDepot && workingOn == null && state != LinuxProtons.State.ABSENT) {
                            add(MenuAction(L("移除"), { removeProton(b) }, danger = true, confirm = L("Remove %1\$s?", b.display)))
                        }
                    },
                ),
            )
        }
    }
}
