package com.winlator.star.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.winlator.star.R
import com.winlator.star.container.Container
import com.winlator.star.container.GameDetails
import com.winlator.star.container.Shortcut
import com.winlator.star.contents.ContentsManager
import com.winlator.star.contents.WrapperManager
import com.winlator.star.core.BannerinXInput
import com.winlator.star.core.ControllerIdentity
import com.winlator.star.core.DInputVibration
import com.winlator.star.core.DInputVisibility
import com.winlator.star.core.RumbleMotors
import com.winlator.star.core.StickPacing
import com.winlator.star.core.DirectAudioSupport
import com.winlator.star.core.MoveGameToContainer
import com.winlator.star.core.SyncMode
import com.winlator.star.core.SyncSupport
import com.winlator.star.core.StringUtils
import com.winlator.star.core.WineInfo
import com.winlator.star.core.WinePath
import com.winlator.star.display.WaylandHdr
import com.winlator.star.midi.MidiManager
import com.winlator.star.store.SteamStoreSearch
import com.winlator.star.ui.components.AUDIO_PRESETS
import com.winlator.star.ui.components.PRESET_CUSTOM
import com.winlator.star.ui.components.audioConfigFromEnv
import com.winlator.star.ui.components.audioConfigToEnv
import com.winlator.star.ui.components.latencyIsDirectBuffer
import com.winlator.star.ui.components.latencyUsedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Phase 1 of the nested XMB: a game's Settings (the section list, General and Controller), Game
// Details, Properties, Clone to container and Remove. Every row reads the shortcut's CURRENT value
// with the same fallbacks as ShortcutSettingsDialogScreen's init (extra → container → default) and
// writes on change with the same encoding as its save(); "" / null clears an extra so the game
// re-inherits the container. Phase 2/3 menus (XmbSettingsPhase2*.kt / XmbGameTools*.kt) plug in here.
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Things only the Games screen can do (its ViewModel / legacy dialogs). */
internal class XmbGameHost(
    val remove: (Shortcut) -> Boolean,
    val containers: () -> List<Container>,
    val openWrapperManager: () -> Unit,
)

/** One shortcut's settings, read/written the way the pop-up editor does. */
internal class XmbPrefs(val context: Context, val shortcut: Shortcut) {
    val res = context.resources!!
    val c: Container get() = shortcut.container
    fun ex(key: String, def: String): String = shortcut.getExtra(key, def) ?: def
    fun put(key: String, value: String?) { shortcut.putExtra(key, value); shortcut.saveData() }
    fun arr(id: Int): List<String> = res.getStringArray(id).toList()
    fun str(id: Int): String = context.getString(id)
    /** Index-matched label for a stored id (StringUtils.parseIdentifier on the display entry). */
    fun labelFor(entries: List<String>, id: String): String =
        entries.firstOrNull { StringUtils.parseIdentifier(it) == id } ?: entries.firstOrNull() ?: id

    // async facts the editor loads in the background
    var arm64ec by mutableStateOf<Boolean?>(null)
    var midiList by mutableStateOf<List<String>>(emptyList())

    /** Why the device's screen can't show HDR10 (the HDR row is greyed with it), null when it can. Read once. */
    val hdrUnavailableReason: String? by lazy { WaylandHdr.unavailableReason(context) }
}

private fun XmbPrefs.loadAsync(xmb: XmbScope) {
    xmb.scope.launch {
        val (a, midi) = withContext(Dispatchers.IO) {
            val arm = runCatching {
                val cm = ContentsManager(context); cm.syncContents()
                WineInfo.fromIdentifier(context, cm, c.wineVersion).isArm64EC()
            }.getOrDefault(false)
            val m = mutableListOf("-- ${context.getString(R.string.disabled)} --", MidiManager.DEFAULT_SF2_FILE)
            File(context.filesDir, MidiManager.SF_DIR).listFiles()?.forEach { m.add(it.name) }
            arm to m
        }
        arm64ec = a; midiList = midi
    }
}

private fun XmbScope.set(p: XmbPrefs, key: String, value: String?) { p.put(key, value); saved(); refresh() }

// ── Settings: the section list (L1/R1 cycles the open section) ─────────────────────────────────────

private val SECTION_TITLES = listOf("General", "Win Components", "Env Vars", "Advanced", "Controller")

internal fun xmbSettingsMenu(xmb: XmbScope, shortcut: Shortcut, host: XmbGameHost): XmbMenu {
    val p = XmbPrefs(xmb.context, shortcut).also { it.loadAsync(xmb) }
    fun section(n: Int): XmbMenu = when (n) {
        0 -> xmbGeneralMenu(xmb, p, host)
        1 -> xmbWinComponentsMenu(xmb, shortcut)
        2 -> xmbEnvVarsMenu(xmb, shortcut)
        3 -> xmbAdvancedMenu(xmb, shortcut)
        else -> xmbControllerMenu(xmb, p)
    }.also { it.siblings = XmbSiblings(n, SECTION_TITLES.size, ::section) { i -> "sec$i" } }
    val icons = listOf(Icons.Filled.Settings, Icons.Filled.Widgets, Icons.Filled.Extension, Icons.Filled.Tune, Icons.Filled.Gamepad)
    val subs = listOf("显示、图形、音频", "DirectX, VC++ runtimes…", "Environment variables", "Box64 / FEXCore, CPU cores, ReShade…", "输入、玩家槽位、体感瞄准")
    return XmbMenu("Settings", Icons.Filled.Settings) {
        SECTION_TITLES.mapIndexed { n, t -> XmbRow.Link("sec$n", t, icons[n], subtitle = subs[n]) { section(n) } }
    }
}

// ── General ─────────────────────────────────────────────────────────────────────────────────────────

internal fun xmbGeneralMenu(xmb: XmbScope, p: XmbPrefs, host: XmbGameHost): XmbMenu =
    XmbMenu("General", Icons.Filled.Settings) { generalRows(this, p, host) }

// Supported bundled driver ids for the Wayland "Compositor driver" row. The probe behind it is
// native + serialized, and XMB rebuilds its rows on every set/refresh, so it runs once per process:
// the first build launches it and refreshes when done; imported ids are re-read on every build.
private var xmbBundledDriverVersions: List<String>? = null
private var xmbBundledDriverVersionsLoading = false
// Same once-per-process pattern for the variant "Auto" resolves to (native renderer probe) — the
// "Auto (by GPU: …)" label of the Wayland game driver row; WaylandGameDriver caches the answer.
private var xmbWaylandAutoLoading = false
// Layers whose Sync capabilities are being probed (SyncSupport caches the answer).
private val xmbSyncProbing = HashSet<String>()

private fun generalRows(xmb: XmbScope, p: XmbPrefs, host: XmbGameHost): List<XmbRow> {
    val s = p.shortcut
    val c = p.c
    val rows = mutableListOf<XmbRow>()
    rows += XmbRow.Header("hGame", "Game")
    rows += XmbRow.Text("name", p.str(R.string.name), Icons.Filled.ShortText, s.name, subtitle = "重命名快捷方式") { v ->
        val newBase = v.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        if (newBase.isNotEmpty() && newBase != s.name) renameAndReturn(xmb, s, newBase)
    }
    rows += XmbRow.Text("execArgs", "执行参数", Icons.Filled.ShortText, p.ex("execArgs", ""), subtitle = "额外命令行参数") { v ->
        xmb.set(p, "execArgs", v.ifEmpty { null })
    }
    val onC = runCatching { WinePath.isOnRemovableStorage(c, s.path) }.getOrDefault(false)
    rows += XmbRow.Link("storage", "Storage", Icons.Filled.Folder,
        value = if (onC) "SD card / USB storage" else "当前驱动器", subtitle = "移动到C盘") { xmbCopyToDriveCMenu(xmb, s) }
    rows += XmbRow.Link("exe", "Executable", Icons.Filled.SwapHoriz,
        value = File(s.path ?: "").name.ifEmpty { "Unknown" }, subtitle = "选择另一个.exe") { xmbChangeExeMenu(xmb, s) }
    rows += XmbRow.Link("container", p.str(R.string.container), Icons.Filled.Widgets,
        value = c.name, subtitle = "Move this game to another container") { xmbMoveToContainerMenu(xmb, s, host) }
    rows += XmbRow.Link("icon", "Icon", Icons.Filled.Image, subtitle = "为此游戏选择图像") { iconPickerMenu(xmb, s) }

    if (s.getExtra("storeSource") == "epic") {
        val eos = p.ex("epicEos", "1") != "0"
        rows += XmbRow.Header("hEpic", "Epic")
        rows += XmbRow.Toggle("epicEos", "EOS登录", Icons.Filled.Layers, eos, subtitle = "使用您的Epic账户登录EOS游戏") { xmb.set(p, "epicEos", if (it) "1" else "0") }
        rows += XmbRow.Toggle("epicOvt", "强制Denuvo所有权令牌", Icons.Filled.Layers, p.ex("epicOvtForce", "0") == "1",
            subtitle = "对于我们不自动检测的Denuvo游戏", disabledReason = if (eos) null else "需要EOS登录") { xmb.set(p, "epicOvtForce", if (it) "1" else "0") }
        rows += XmbRow.Toggle("epicOffline", "离线启动", Icons.Filled.Layers, p.ex("epicOffline", "0") == "1",
            subtitle = "跳过此游戏的Epic登录", disabledReason = if (eos) null else "需要EOS登录") { xmb.set(p, "epicOffline", if (it) "1" else "0") }
        if (com.winlator.star.FeatureFlags.EPIC_OVERLAY_ENABLED) {
            rows += XmbRow.Toggle("epicOverlay", "Epic friends overlay", Icons.Filled.Layers, p.ex("epicOverlay", "0") == "1",
                subtitle = "Experimental — Shift+F3 in game", disabledReason = if (eos) null else "需要EOS登录") { xmb.set(p, "epicOverlay", if (it) "1" else "0") }
        }
    }

    if (s.getExtra("storeSource") == "xbox" && s.getExtra("xboxProductId").let { id ->
            !id.isNullOrEmpty() && runCatching { com.winlator.star.store.xbox.XboxRuntime.shipsOwnRuntime(xmb.context, id) }.getOrDefault(false) }) {
        val key = com.winlator.star.store.xbox.XboxRuntime.NEXUS_RUNTIME_EXTRA
        rows += XmbRow.Header("hXbox", "Xbox")
        rows += XmbRow.Toggle(key, "Use the app's Xbox runtime", Icons.Filled.Layers, p.ex(key, "1") != "0",
            subtitle = "The game ships Microsoft's, which needs Windows' Gaming Services") { xmb.set(p, key, if (it) "1" else "0") }
    }

    // Sync (esync / ntsync / fsync / wineserver): same extra and rules as the pop-up editor —
    // "容器默认" clears the per-game override; greyed against the container's layer. The
    // layer probe reads ntdll.so, so a first visit starts it off-main and refreshes when it lands.
    if (!com.winlator.star.linux.LinuxShortcuts.isLinuxEntry(s)) {
        val syncLayer = c.wineVersion ?: ""
        val syncCaps = SyncSupport.peek(syncLayer)
        if (syncCaps == null && xmbSyncProbing.add(syncLayer)) {
            xmb.scope.launch {
                withContext(Dispatchers.IO) { SyncSupport.capsFor(p.context, null, syncLayer) }
                xmbSyncProbing.remove(syncLayer)
                xmb.refresh()
            }
        }
        val cStored = SyncSupport.storedMode(c.getExtra(SyncMode.EXTRA, ""), c.envVars)
        val cEff = syncCaps?.resolve(cStored) ?: cStored ?: SyncMode.ESYNC
        val sEff = SyncSupport.storedMode(p.ex(SyncMode.EXTRA, ""), p.ex("envVars", ""))
            ?.let { syncCaps?.resolve(it) ?: it }
        val syncLabels = listOf("Container default ($cEff)") + SyncMode.ALL
        val overriding = sEff != null && sEff != cEff
        val greyed = com.winlator.star.ui.components.syncGreyedReasons(syncCaps, syncLayer).joinToString("; ")
        rows += XmbRow.Header("hWine", "Wine")
        rows += XmbRow.Choice("syncMode", "Sync", Icons.Filled.Sync, syncLabels,
            if (overriding) sEff!! else syncLabels[0],
            subtitle = (if (overriding) "$sEff for this game only (container: $cEff)." else "Following the container: $cEff.") +
                "  Greyed: $greyed.",
            disabledOptions = SyncMode.ALL.filter { it == SyncMode.FSYNC || syncCaps?.isAvailable(it) == false }.toSet()) { v ->
            // The sync variables leave this game's env string for good (the selector owns them now).
            val env = p.ex("envVars", "")
            val stripped = SyncSupport.stripSyncVars(env)
            if (stripped != env) s.putExtra("envVars", stripped.ifEmpty { null })
            xmb.set(p, SyncMode.EXTRA, (if (v == syncLabels[0]) null else v)?.takeIf { it != cEff })
        }
        // The pop-up editors' "?" (help_sync_mode), as its own column.
        rows += XmbRow.Link("syncHelp", "什么是Sync同步?", Icons.Filled.HelpOutline, subtitle = "esync、ntsync、fsync 和 wineserver 说明") {
            xmbHelpMenu(p.context, xmb.scope, "Sync", R.string.help_sync_mode)
        }
    }

    // Display
    rows += XmbRow.Header("hDisplay", "启动")
    // Display backend: "" inherits the container's, else force X11 / Wayland (same extra as the
    // pop-up editor). Wayland replaces the Renderer group with the embedded compositor.
    // Wayland needs the container's Proton layer to ship winewayland.so + its bundled Wayland Turnip
    // (WineWaylandSupport): otherwise "Wayland" is not pickable, a stored Wayland override displays
    // as the effective backend (X11), and "容器默认" resolves to the container's EFFECTIVE
    // backend (X11 when the container says wayland but its layer can't drive it).
    val waylandCapable = com.winlator.star.core.WineWaylandSupport.isWaylandCapable(p.context, c.wineVersion)
    val containerWaylandDefault = c.isWaylandBackend && waylandCapable
    val dbValues = listOf("", Container.DISPLAY_BACKEND_X11, Container.DISPLAY_BACKEND_WAYLAND)
    val dbLabels = listOf(
        "容器默认 (" + (if (containerWaylandDefault) "Wayland" else "X11") + ")",
        "X11", "Wayland（实验性）")
    val dbOverride = p.ex("displayBackend", "")
    val waylandStoredUnusable = dbOverride == Container.DISPLAY_BACKEND_WAYLAND && !waylandCapable
    val waylandGame = if (dbOverride.isEmpty()) containerWaylandDefault else dbOverride == Container.DISPLAY_BACKEND_WAYLAND && waylandCapable
    rows += XmbRow.Choice("displayBackend", "显示后端", Icons.Filled.DesktopWindows, dbLabels,
        dbLabels[if (waylandStoredUnusable) 1 else dbValues.indexOf(dbOverride).coerceAtLeast(0)],
        // Same help text as the container editor's backend row.
        subtitle = when {
            waylandGame -> "Wayland (experimental): games render through the embedded compositor (winewayland). " +
                "Needs " + com.winlator.star.core.WineWaylandSupport.LAYER_HINT + ". Games render on the Turnip bundled with that Proton — " +
                "the Compositor driver only affects the compositor. DX wrapper (DXVK/VKD3D) settings apply as on X11. " +
                "The Renderer options below don't apply and are disabled."
            waylandStoredUnusable -> "Set to Wayland, but the container's Proton layer is not Wayland-capable: runs on X11. " +
                "Wayland needs " + com.winlator.star.core.WineWaylandSupport.LAYER_HINT + "."
            !waylandCapable && c.isWaylandBackend -> "The container is set to Wayland, but its Proton layer is not Wayland-capable: runs on X11. " +
                "Wayland needs " + com.winlator.star.core.WineWaylandSupport.LAYER_HINT + "."
            !waylandCapable -> "Runs on the X11 server. Wayland needs " + com.winlator.star.core.WineWaylandSupport.LAYER_HINT +
                ". The selected layer does not include winewayland and its Wayland Turnip."
            else -> "在X11服务器上运行"
        },
        disabledOptions = if (waylandCapable) emptySet() else setOf(dbLabels[2]),
        confirm = { v -> if (v == dbLabels[2]) XmbConfirm("Wayland", "Wayland is experimental. Run this game on Wayland?", "使用Wayland") else null }) { v ->
        xmb.set(p, "displayBackend", dbValues[dbLabels.indexOf(v)].ifEmpty { null })
    }
    val sizes = p.arr(R.array.screen_size_entries)
    val rawSize = p.ex("screenSize", c.getScreenSize())
    val sizeLabel = sizes.firstOrNull { StringUtils.parseIdentifier(it).equals(rawSize, ignoreCase = true) } ?: "Custom"
    rows += XmbRow.Choice("screenSize", p.str(R.string.screen_size), Icons.Filled.DesktopWindows, sizes, sizeLabel) { v ->
        if (v == "Custom") {
            val w = if (rawSize.contains("x")) rawSize.substringBefore("x") else "800"
            val h = if (rawSize.contains("x")) rawSize.substringAfter("x") else "600"
            xmb.set(p, "screenSize", "${w}x$h")
        } else xmb.set(p, "screenSize", StringUtils.parseIdentifier(v))
    }
    if (sizeLabel == "Custom") {
        val w = if (rawSize.contains("x")) rawSize.substringBefore("x") else "800"
        val h = if (rawSize.contains("x")) rawSize.substringAfter("x") else "600"
        fun setWH(nw: String, nh: String) {
            val ok = nw.matches(Regex("[0-9]+")) && nh.matches(Regex("[0-9]+")) && nw.toInt() % 2 == 0 && nh.toInt() % 2 == 0
            if (!ok) xmb.toast("Width and height must be even numbers")
            xmb.set(p, "screenSize", if (ok) "${nw}x$nh" else Container.DEFAULT_SCREEN_SIZE)
        }
        rows += XmbRow.Text("customW", "Width", Icons.Filled.AspectRatio, w, subtitle = "仅偶数", numeric = true) { setWH(it, h) }
        rows += XmbRow.Text("customH", "Height", Icons.Filled.AspectRatio, h, subtitle = "仅偶数", numeric = true) { setWH(w, it) }
    }
    val saValues = listOf("", "0", "1", "2")
    val saLabels = listOf(p.str(R.string.use_container_default), p.str(R.string.screen_alignment_center), p.str(R.string.screen_alignment_top), p.str(R.string.screen_alignment_bottom))
    val sa = p.ex("screenAlignment", "")
    // Alignment and fullscreen mode apply on X11 and Wayland alike (the compositor fits the desktop
    // with the same modes), so neither row is gated on the backend.
    rows += XmbRow.Choice("screenAlignment", "屏幕对齐", Icons.Filled.DesktopWindows, saLabels, saLabels[saValues.indexOf(sa).coerceAtLeast(0)]) { v ->
        xmb.set(p, "screenAlignment", saValues[saLabels.indexOf(v)].ifEmpty { null })
    }
    val fsLabels = listOf(p.str(R.string.fullscreen_mode_default), p.str(R.string.fullscreen_mode_off), p.str(R.string.fullscreen_mode_fit),
        p.str(R.string.fullscreen_mode_stretch), p.str(R.string.fullscreen_mode_fill), p.str(R.string.fullscreen_mode_integer))
    val fsRaw = s.getExtra("fullscreenMode") ?: ""
    val fsOverride = when {
        fsRaw.isNotEmpty() -> fsRaw.toIntOrNull() ?: -1
        p.ex("fullscreenStretched", "") == "1" -> Container.FULLSCREEN_STRETCH
        else -> -1
    }
    val fsIdx = if (fsOverride < 0) 0 else (fsOverride + 1).coerceIn(1, fsLabels.size - 1)
    rows += XmbRow.Choice("fullscreen", "全屏模式", Icons.Filled.DesktopWindows, fsLabels, fsLabels[fsIdx]) { v ->
        val idx = fsLabels.indexOf(v)
        s.putExtra("fullscreenStretched", null)
        xmb.set(p, "fullscreenMode", if (idx <= 0) null else (idx - 1).toString())
    }
    val rsValues = listOf("1.0", "1.25", "1.5", "2.0")
    val rsLabels = listOf("Off", "1.25x", "1.5x", "2x")
    val rs = p.ex("renderScale", c.getExtra("renderScale", "1.0") ?: "1.0")
    // Greyed on Wayland (the downscale lives in the X11 Vulkan renderer only) and then DISPLAYS
    // "在Wayland上不使用"; the stored value is left untouched.
    val rsShown = if (waylandGame) "在Wayland上不使用" else rsLabels[rsValues.indexOf(rs).coerceAtLeast(0)]
    rows += XmbRow.Choice("renderScale", "Render scale", Icons.Filled.AspectRatio, if (waylandGame) listOf(rsShown) else rsLabels, rsShown, subtitle = "超采样",
        disabledReason = if (waylandGame) "在Wayland上不使用：合成器没有超采样降分辨率" else null) { v ->
        val nv = rsValues[rsLabels.indexOf(v)]
        xmb.set(p, "renderScale", if (nv == "1.0") null else nv)
    }
    val rates = xmbPanelRates(p.context).filter { it > 60 }
    if (xmbPanelRates(p.context).isNotEmpty()) {
        val rrValues = listOf("", "locked", "0") + rates.map { it.toString() }
        val rrLabels = listOf(p.str(R.string.use_container_default), p.str(R.string.in_game_refresh_locked), p.str(R.string.max_game_refresh_rate_unlimited)) + rates.map { "$it Hz" }
        val unlock = p.ex("unlockGameRefreshRate", "")
        val maxR = p.ex("maxGameRefreshRate", "")
        val cur = when {
            unlock.isEmpty() && maxR.isEmpty() -> ""
            unlock == "0" -> "locked"
            else -> maxR.ifEmpty { "0" }
        }
        rows += XmbRow.Choice("refresh", "In-game refresh rate", Icons.Filled.Speed, rrLabels, rrLabels[rrValues.indexOf(cur).coerceAtLeast(0)]) { v ->
            when (val nv = rrValues[rrLabels.indexOf(v)]) {
                "" -> { s.putExtra("unlockGameRefreshRate", null); xmb.set(p, "maxGameRefreshRate", null) }
                "locked" -> { s.putExtra("unlockGameRefreshRate", "0"); xmb.set(p, "maxGameRefreshRate", "0") }
                else -> { s.putExtra("unlockGameRefreshRate", "1"); xmb.set(p, "maxGameRefreshRate", nv) }
            }
        }
    }

    // TV — the same rows as the pop-up editor's TV tab (display.ExternalDisplay), on the same
    // condition: an external display is plugged in, or this game is already set to launch on one so a
    // leftover setting is never hidden. Per game only, no container default. HDR is reported, not
    // asked: starting the session on the TV is what opens the compositor's HDR gate there.
    // XMB has no display listener of its own — its rows rebuild on refresh(), so a cable plugged in
    // while this column is open shows up on the next change rather than instantly.
    val tvDisplay = com.winlator.star.display.ExternalDisplay.find(p.context)
    if (tvDisplay != null || com.winlator.star.display.ExternalDisplay.launchOnTv(s)) {
        rows += XmbRow.Header("hTv", "TV")
        rows += XmbRow.Info("tvDisplay",
            if (tvDisplay != null) com.winlator.star.display.ExternalDisplay.title(tvDisplay) else "无外部显示器",
            Icons.Filled.Tv,
            value = if (tvDisplay != null) com.winlator.star.display.ExternalDisplay.modeLabel(
                com.winlator.star.display.ExternalDisplay.activeMode(tvDisplay)) else "",
            subtitle = if (tvDisplay != null) com.winlator.star.display.ExternalDisplay.summary(tvDisplay)
                       else "未插入任何设备。此游戏仍设置为在电视上启动。")
        rows += XmbRow.Toggle("tvLaunch", "Launch this game on the TV", Icons.Filled.Tv,
            com.winlator.star.display.ExternalDisplay.launchOnTv(s),
            subtitle = com.winlator.star.display.ExternalDisplay.HELP_LAUNCH) {
            xmb.set(p, com.winlator.star.display.ExternalDisplay.EXTRA_LAUNCH, if (it) "1" else "0")
        }
        val tvModes = com.winlator.star.display.ExternalDisplay.selectableModes(tvDisplay)
        val tvModeId = com.winlator.star.display.ExternalDisplay.modeId(s)
        if (tvModes.size > 1) {
            val tvValues = listOf(0) + tvModes.map { it.modeId }
            val tvLabels = listOf("默认（保持电视原样）") +
                tvModes.map { com.winlator.star.display.ExternalDisplay.modeLabel(it) }
            rows += XmbRow.Choice("tvModeId", "输出模式", Icons.Filled.DesktopWindows, tvLabels,
                tvLabels[tvValues.indexOf(tvModeId).coerceAtLeast(0)],
                subtitle = "What the TV is told to run at") { v ->
                xmb.set(p, com.winlator.star.display.ExternalDisplay.EXTRA_MODE_ID,
                    tvValues[tvLabels.indexOf(v)].toString())
            }
        } else {
            rows += XmbRow.Info("tvModeId", "输出模式", Icons.Filled.DesktopWindows,
                value = com.winlator.star.display.ExternalDisplay.modeLabel(tvModes.firstOrNull()).ifEmpty { "—" },
                subtitle = if (tvDisplay != null) "This screen advertises one output mode"
                           else "Plug a screen in to choose an output mode")
        }
        rows += XmbRow.Toggle("tvMatchRes", "Match the TV's resolution", Icons.Filled.AspectRatio,
            com.winlator.star.display.ExternalDisplay.matchResolution(s),
            subtitle = com.winlator.star.display.ExternalDisplay.HELP_MATCH_RES) {
            xmb.set(p, com.winlator.star.display.ExternalDisplay.EXTRA_MATCH_RES, if (it) "1" else "0")
        }
        val tvHdrReason = if (tvDisplay == null) null
                          else com.winlator.star.display.ExternalDisplay.hdrUnavailableReason(tvDisplay)
        rows += XmbRow.Info("tvHdr", "Use HDR on the TV", Icons.Filled.HdrOn,
            value = if (tvDisplay != null && tvHdrReason == null) "On" else "Off",
            subtitle = tvHdrReason ?: (
                if (tvDisplay != null) "Automatic. This screen accepts HDR10, so HDR output switches on for this game."
                else "插入屏幕以查看它能做什么。"))
        rows += XmbRow.Info("tvNoteUnplug", "如果线缆拔出", Icons.Filled.Info,
            subtitle = com.winlator.star.display.ExternalDisplay.NOTE_UNPLUG)
        rows += XmbRow.Info("tvNoteTouch", "电视上的触摸", Icons.Filled.TouchApp,
            subtitle = com.winlator.star.display.ExternalDisplay.NOTE_TOUCH)
    }

    // Graphics
    rows += XmbRow.Header("hGfx", "Graphics")
    val gfxEntries = WrapperManager.driverEntries(p.context, p.res.getStringArray(R.array.graphics_driver_entries))
    val gfxId = p.ex("graphicsDriver", c.graphicsDriver)
    // Under Wayland the wrapper flavour is irrelevant: the compositor loads the installed Turnip named
    // by the "version" key of graphicsDriverConfig (XServerDisplayActivity's Wayland resolve), and the
    // game renders on the Proton's bundled Wayland Turnip. So the flavour picker is swapped for a
    // "Compositor driver" choice over the installed Turnip ids that writes ONLY the version key back
    // (withGraphicsDriverVersion); the config link stays live for the same key.
    if (waylandGame) {
        val gdc = p.ex("graphicsDriverConfig", c.getGraphicsDriverConfig())
        val compositorVersion = com.winlator.star.contentdialog.GraphicsDriverConfigDialog.getVersion(gdc) ?: ""
        // Same source as the config dialog's "Graphics Driver Version" (minus "System").
        if (xmbBundledDriverVersions == null && !xmbBundledDriverVersionsLoading) {
            xmbBundledDriverVersionsLoading = true
            xmb.scope.launch {
                xmbBundledDriverVersions = supportedBundledDriverVersions(p.context)
                xmbBundledDriverVersionsLoading = false
                xmb.refresh()
            }
        }
        val turnips = ((if (com.winlator.star.core.MaliPanvk.isMaliGpu()) listOf("System") else emptyList()) +
            (xmbBundledDriverVersions ?: emptyList()) + importedDriverVersions(p.context)).distinct()
        val turnipsLoaded = xmbBundledDriverVersions != null
        // One driver pick on Wayland: with the game driver on "adapter" it drives the game too.
        val wgdEffective = p.ex("waylandGameDriver", "").ifEmpty { c.waylandGameDriver }
        val gfxSubtitle = when {
            com.winlator.star.core.WaylandAdapter.isProprietaryBlob(p.context, compositorVersion) ->
                "Qualcomm's own driver cannot put Wayland frames on screen: the screen uses a bundled Turnip automatically."
            com.winlator.star.core.WaylandGameDriver.adapterActive(p.context, wgdEffective, compositorVersion) ->
                "用于游戏（通过Wayland适配器）以及将其帧放到屏幕上。"
            else -> "由Wayland合成器用于将帧放到屏幕上；游戏在下面的Wayland游戏驱动程序上渲染。"
        }
        rows += XmbRow.Choice("gfxDriver", p.str(R.string.graphics_driver), Icons.Filled.Memory, turnips,
            compositorDriverLabel(compositorVersion, turnips, turnipsLoaded),
            subtitle = gfxSubtitle) { v ->
            xmb.set(p, "graphicsDriverConfig", withGraphicsDriverVersion(gdc, v))
        }
        // "System"/empty falls back to the system libvulkan, which can't import the game's dmabufs
        // (black screen) — mirrors XServerDisplayActivity's Wayland driver resolve; so does an id
        // that is no longer installed. Warn only.
        if (compositorDriverUnusable(compositorVersion, turnips, turnipsLoaded)) {
            val isSystem = compositorVersion.isEmpty() || compositorVersion == "System"
            rows += XmbRow.Info("gfxSystemWarn",
                if (isSystem) "Graphics driver is \"System\"" else "Graphics driver is not available", Icons.Filled.Info,
                subtitle = """Wayland needs a Turnip driver here. "System" or a missing driver cannot import the game's frames and shows a black screen.""")
        }
        // Wayland game driver (per-game override of the container's waylandGameDriver; "" = container
        // default): Auto / the bundled Turnip variants / imported Linux ICDs — same options and labels
        // as the pop-up editors (WaylandGameDriver). A stored imported:<id> whose import is gone stays
        // listed (labelled missing); launch uses Auto for it.
        val wgdAuto = com.winlator.star.core.WaylandGameDriver.autoVariantIfKnown()
        if (wgdAuto == null && !xmbWaylandAutoLoading) {
            xmbWaylandAutoLoading = true
            xmb.scope.launch {
                waylandAutoVariant(p.context)
                xmbWaylandAutoLoading = false
                xmb.refresh()
            }
        }
        // A stored auto shows as the adapter it resolves to (WaylandGameDriver.editorChoice), so the list
        // never carries two entries with the adapter's label.
        val wgdOverride = p.ex("waylandGameDriver", "").let {
            if (it.isEmpty()) "" else com.winlator.star.core.WaylandGameDriver.editorChoice(p.context, it)
        }
        val wgdInstalled = com.winlator.star.core.WaylandGameDriver.optionValues(p.context)
        val wgdValues = listOf("") + (if (wgdOverride.isEmpty() || wgdOverride in wgdInstalled) wgdInstalled else wgdInstalled + wgdOverride)
        val wgdLabels = wgdValues.map {
            if (it.isEmpty()) "容器默认 (" + com.winlator.star.core.WaylandGameDriver.optionLabel(p.context, c.waylandGameDriver, wgdAuto) + ")"
            else com.winlator.star.core.WaylandGameDriver.optionLabel(p.context, it, wgdAuto)
        }
        rows += XmbRow.Choice("waylandGameDriver", "Wayland游戏驱动程序（高级）", Icons.Filled.Memory, wgdLabels,
            wgdLabels[wgdValues.indexOf(wgdOverride).coerceAtLeast(0)],
            subtitle = com.winlator.star.core.WaylandGameDriver.HELP_TEXT) { v ->
            xmb.set(p, "waylandGameDriver", wgdValues[wgdLabels.indexOf(v)].ifEmpty { null })
        }
        // The pop-up editors' gear next to the Wayland game driver: GPU name spoof, memory cap, present
        // mode, UBWC hint, in the same graphicsDriverConfig keys as X11's driver configuration.
        val spoof = com.winlator.star.core.GpuSpoof.gpuNameOf(gdc)
        rows += XmbRow.Link("waylandDriverCfg", "Wayland driver settings", Icons.Filled.Tune,
            value = spoof.takeIf { com.winlator.star.core.GpuSpoof.isSpoofing(it) },
            subtitle = "GPU 名称伪装、显存上限、呈现模式…") { xmbWaylandDriverConfigMenu(xmb, s) }
        // HDR output (per-game override of the container's waylandHdr; "" = container default, "1" on,
        // "0" off) — same options as the pop-up editor (WaylandHdr). Greyed with the reason on a screen
        // that doesn't report HDR10, still showing what is stored.
        val hdrValues = listOf("", "1", "0")
        val hdrLabels = listOf("容器默认 (" + (if (c.isWaylandHdr()) "On" else "Off") + ")", "On", "Off")
        rows += XmbRow.Choice(WaylandHdr.EXTRA, WaylandHdr.TITLE, Icons.Filled.HdrOn, hdrLabels,
            hdrLabels[hdrValues.indexOf(WaylandHdr.shortcutChoice(s)).coerceAtLeast(0)],
            subtitle = WaylandHdr.HELP_SHORT, disabledReason = p.hdrUnavailableReason) { v ->
            xmb.set(p, WaylandHdr.EXTRA, hdrValues[hdrLabels.indexOf(v)].ifEmpty { null })
        }
    } else {
        rows += XmbRow.Choice("gfxDriver", p.str(R.string.graphics_driver), Icons.Filled.Memory, gfxEntries, p.labelFor(gfxEntries, gfxId)) { v ->
            xmb.set(p, "graphicsDriver", StringUtils.parseIdentifier(v))
        }
    }
    // Fast OpenGL (per-game override of the container's fastOpenGL; "" = container default, "1" on,
    // "0" off) — same extra and rules as the pop-up editor (core.FastOpenGL). Locked on Wayland
    // (always on there); on X11 greyed with one reason when the driver isn't a Turnip or the APK has
    // no X11 GL set. Any layer works on X11: the launch picks the EGL or GLX route.
    if (!com.winlator.star.linux.LinuxShortcuts.isLinuxEntry(s)) {
        val glDriver = com.winlator.star.contentdialog.GraphicsDriverConfigDialog.getVersion(
            p.ex("graphicsDriverConfig", c.getGraphicsDriverConfig()))
        val glLocked = if (waylandGame) com.winlator.star.core.FastOpenGL.WAYLAND_ALWAYS_ON
            else com.winlator.star.core.FastOpenGL.unavailableReason(
                com.winlator.star.core.FastOpenGL.driverUsable(p.context, glDriver),
                com.winlator.star.core.X11Egl.isBundled(p.context))
        val glValues = listOf("", "1", "0")
        val glLabels = listOf("容器默认 (" + (if (c.isFastOpenGL()) "On" else "Off") + ")", "On", "Off")
        val glOverride = com.winlator.star.core.FastOpenGL.normalize(p.ex(com.winlator.star.core.FastOpenGL.EXTRA, "")) ?: ""
        rows += XmbRow.Choice(com.winlator.star.core.FastOpenGL.EXTRA, com.winlator.star.core.FastOpenGL.TITLE, Icons.Filled.Speed,
            glLabels, glLabels[glValues.indexOf(glOverride).coerceAtLeast(0)],
            subtitle = com.winlator.star.core.FastOpenGL.HINT, disabledReason = glLocked) { v ->
            xmb.set(p, com.winlator.star.core.FastOpenGL.EXTRA, glValues[glLabels.indexOf(v)].ifEmpty { null })
        }
        // Show OpenGL FPS / OpenGL vsync off: the same Container default / On / Off choice, on both
        // backends, never greyed (core.FastOpenGL.FPS_HUD / VSYNC_OFF).
        for (sw in listOf(com.winlator.star.core.FastOpenGL.FPS_HUD, com.winlator.star.core.FastOpenGL.VSYNC_OFF)) {
            val labels = listOf("容器默认 (" + (if (sw.containerOn(c.getExtra(sw.extra))) "On" else "Off") + ")", "On", "Off")
            val stored = com.winlator.star.core.FastOpenGL.normalize(p.ex(sw.extra, "")) ?: ""
            rows += XmbRow.Choice(sw.extra, sw.title, Icons.Filled.Speed, labels,
                labels[glValues.indexOf(stored).coerceAtLeast(0)], subtitle = sw.hint) { v ->
                xmb.set(p, sw.extra, glValues[labels.indexOf(v)].ifEmpty { null })
            }
        }
        // The pop-up editors' one "?" for the OpenGL card (help_opengl_settings), as its own column.
        rows += XmbRow.Link("openglHelp", "这些是什么?", Icons.Filled.HelpOutline,
            subtitle = "快速 OpenGL、显示 OpenGL FPS 和OpenGL vsync off垂直同步关闭说明") {
            xmbHelpMenu(p.context, xmb.scope, "OpenGL", R.string.help_opengl_settings)
        }
    }
    // Driver configuration is X11 tuning; on Wayland its only live field (the Turnip version) is
    // covered by the Compositor driver row above, so the link is left out of the Wayland layout.
    if (!waylandGame) rows += XmbRow.Link("gfxConfig", "驱动程序配置", Icons.Filled.Tune,
        subtitle = "Vulkan版本、BCn、呈现模式…") { xmbDriverConfigMenu(xmb, s) }
    // Wrappers are X11 game-driver shims; nothing on the Wayland path uses them.
    if (!waylandGame) rows += XmbRow.External("wrappers", "管理 wrappers", Icons.Filled.Cloud, subtitle = "导入或移除wrapper驱动程序") { host.openWrapperManager() }
    // Unreal Engine HDR (per-game override of the container's unrealHdr; "" = container default), both
    // backends — same options as the pop-up editors (core.UnrealHdr). DirectX 11 while no NVIDIA GPU
    // name spoof is set gets the optional hint row.
    run {
        val cMode = com.winlator.star.core.UnrealHdr.containerMode(c)
        val uValues = listOf("") + com.winlator.star.core.UnrealHdr.MODES
        val uLabels = uValues.map {
            if (it.isEmpty()) "容器默认 (" + com.winlator.star.core.UnrealHdr.label(cMode) + ")"
            else com.winlator.star.core.UnrealHdr.label(it)
        }
        val uOverride = com.winlator.star.core.UnrealHdr.shortcutChoice(s)
        rows += XmbRow.Choice(com.winlator.star.core.UnrealHdr.EXTRA, com.winlator.star.core.UnrealHdr.TITLE, Icons.Filled.HdrOn,
            uLabels, uLabels[uValues.indexOf(uOverride).coerceAtLeast(0)],
            subtitle = com.winlator.star.core.UnrealHdr.HELP_SHORT) { v ->
            xmb.set(p, com.winlator.star.core.UnrealHdr.EXTRA, uValues[uLabels.indexOf(v)].ifEmpty { null })
        }
        val spoof = com.winlator.star.core.GpuSpoof.gpuNameOf(p.ex("graphicsDriverConfig", c.getGraphicsDriverConfig()))
        if (uOverride.ifEmpty { cMode } == com.winlator.star.core.UnrealHdr.DX11 &&
            !com.winlator.star.core.GpuSpoof.isNvidia(p.context, spoof)) {
            rows += XmbRow.Info("unrealHdrNvidia", "未报告 NVIDIA GPU", Icons.Filled.Info,
                subtitle = com.winlator.star.core.UnrealHdr.nvidiaHint(waylandGame))
        }
    }
    val dxEntries = p.arr(R.array.dxwrapper_entries)
    val dxId = p.ex("dxwrapper", c.getDXWrapper())
    rows += XmbRow.Choice("dxWrapper", "DX wrapper", Icons.Filled.Layers, dxEntries, p.labelFor(dxEntries, dxId)) { v ->
        val nid = StringUtils.parseIdentifier(v)
        // Leaving VEGAS strips its config-file key, as the editor does.
        if (dxId.contains("vegas") && !nid.contains("vegas")) {
            val cfg = p.ex("dxwrapperConfig", c.getDXWrapperConfig())
            s.putExtra("dxwrapperConfig", cfg.split(",").filterNot { it.startsWith("dxvkConfigFile=") }.joinToString(","))
        }
        xmb.set(p, "dxwrapper", nid)
    }
    rows += XmbRow.Link("dxConfig", "DX wrapper配置", Icons.Filled.Tune,
        subtitle = "版本、帧率、滤镜…") { xmbDxConfigMenu(xmb, s) }

    val rendId = p.ex("renderer", c.renderer).lowercase()
    val rend = when (rendId) { "vulkan" -> "Vulkan"; "surfaceflinger" -> "SurfaceFlinger"; else -> "OpenGL" }
    // Display only while disabled on Wayland: the compositor is always Vulkan; `rend` (the stored
    // X11 choice) is untouched and still drives the Vulkan/SurfaceFlinger sub-rows below.
    val rendShown = if (waylandGame) "Vulkan (Wayland compositor)" else rend
    rows += XmbRow.Choice("renderer", "Renderer", Icons.Filled.DesktopWindows, if (waylandGame) listOf(rendShown) else listOf("OpenGL", "Vulkan", "SurfaceFlinger"), rendShown,
        disabledReason = if (waylandGame) "Wayland 通过其自身的合成器绘制" else null,
        confirm = { v -> if (v == "SurfaceFlinger") XmbConfirm("SurfaceFlinger renderer", "SurfaceFlinger renderer — experimental. Use it for this game?", "Use SurfaceFlinger") else null }) { v ->
        xmb.set(p, "renderer", v.lowercase())
    }
    // Renderer sub-options are X11-only; hidden on Wayland like the container/shortcut editors do
    // (their stored values are untouched and come back with the X11 backend).
    if (!waylandGame && rend == "SurfaceFlinger") {
        rows += XmbRow.Toggle("sfCompat", "校正 SurfaceFlinger 颜色", Icons.Filled.Image,
            p.ex("sfCompatMode", if (c.getRendererSfCompatMode()) "1" else "0") == "1",
            subtitle = "修复红/蓝通道互换（BGRA→RGBA）") { xmb.set(p, "sfCompatMode", if (it) "1" else "0") }
    }
    if (!waylandGame && rend == "Vulkan") {
        val native = p.ex("native", if (c.isRendererNative()) "true" else "false") == "true"
        rows += XmbRow.Toggle("vkNative", "原生渲染器", Icons.Filled.DesktopWindows, native) { xmb.set(p, "native", if (it) "true" else "false") }
        val swap = p.ex("swapRB", if (c.getRendererSwapRB()) "true" else "false") == "true"
        rows += XmbRow.Choice("vkColors", "颜色", Icons.Filled.Image, listOf("BGRA", "RGBA"), if (swap) "RGBA" else "BGRA") { v ->
            xmb.set(p, "swapRB", if (v == "RGBA") "true" else "false")
        }
        val pmValues = listOf("fifo", "mailbox", "immediate")
        val pmLabels = listOf(p.str(R.string.renderer_present_mode_fifo), p.str(R.string.renderer_present_mode_mailbox), p.str(R.string.renderer_present_mode_immediate))
        val pm = p.ex("presentMode", c.getRendererPresentMode())
        rows += XmbRow.Choice("vkPresent", "呈现模式", Icons.Filled.Speed, pmLabels, pmLabels[pmValues.indexOf(pm).coerceAtLeast(0)],
            disabledReason = if (native) "由原生渲染器设置" else null) { v -> xmb.set(p, "presentMode", pmValues[pmLabels.indexOf(v)]) }
        if (native) {
            val nbValues = listOf("auto", "asr", "flip")
            val nbLabels = listOf(p.str(R.string.renderer_native_backend_auto), p.str(R.string.renderer_native_backend_asr), p.str(R.string.renderer_native_backend_flip))
            val nb = p.ex("nativeBackend", c.getRendererNativeBackend())
            rows += XmbRow.Choice("vkBackend", "Native backend", Icons.Filled.Layers, nbLabels, nbLabels[nbValues.indexOf(nb).coerceAtLeast(0)]) { v ->
                xmb.set(p, "nativeBackend", nbValues[nbLabels.indexOf(v)])
            }
        }
        val drivers = (listOf("system") + runCatching { com.winlator.star.contents.AdrenotoolsManager(p.context).enumarateInstalledDrivers().toList() }.getOrDefault(emptyList())).distinct()
        val rd = p.ex("rendererDriverId", c.getRendererDriverId())
        rows += XmbRow.Choice("vkDriver", "渲染器驱动程序", Icons.Filled.Memory, drivers, if (rd in drivers) rd else "system",
            subtitle = "合成器运行的驱动程序") { v -> xmb.set(p, "rendererDriverId", v) }
    }
    val fgEngines = listOf("off", "bionic", "lsfg-native")
    val fgLabels = listOf(p.str(R.string.frame_generation_off), p.str(R.string.frame_generation_bionic), p.str(R.string.frame_generation_lsfg_native))
    val fg = p.ex("frameGenEngine", c.frameGenEngine).let { if (it == "lsfg") "lsfg-native" else it }
    val lsfgDll = File(p.context.filesDir, "lsfg-vk/Lossless.dll").isFile
    // On Wayland the X11 renderer gate does not apply: FG runs inside the compositor (always Vulkan)
    // and the in-game drawer arms the engine picked here.
    val fgShown = fgLabels[fgEngines.indexOf(fg).coerceAtLeast(0)]
    rows += XmbRow.Choice("frameGen", "Frame generation", Icons.Filled.Speed, fgLabels, fgShown,
        subtitle = if (!lsfgDll) "在设置中导入Lossless.dll以启用LSFG" else null,
        disabledReason = when {
            !waylandGame && rend != "Vulkan" -> "Frame generation requires the Vulkan renderer"
            else -> null
        },
        disabledOptions = if (lsfgDll) emptySet() else setOf(fgLabels[2])) { v -> xmb.set(p, "frameGenEngine", fgEngines[fgLabels.indexOf(v)]) }
    rows += XmbRow.Toggle("fpsLimiter", "FPS 限制器", Icons.Filled.Speed,
        p.ex("fpsLimiterEnabled", if (c.isFpsLimiterEnabled) "1" else "0") == "1") { xmb.set(p, "fpsLimiterEnabled", if (it) "1" else "0") }
    rows += XmbRow.Link("perf", "Performance", Icons.Filled.Speed, subtitle = "持续性能、优先级、大核") { xmbPerformanceMenu(xmb, s) }

    // Audio & system
    rows += XmbRow.Header("hAudio", "音频与系统")
    val audioEntries = p.arr(R.array.audio_driver_entries)
    val daSupported = DirectAudioSupport.isSupported(c.wineVersion)
    var audioId = p.ex("audioDriver", c.audioDriver)
    if (audioId == "directaudio" && !daSupported) audioId = Container.DEFAULT_AUDIO_DRIVER
    val daEntry = audioEntries.firstOrNull { StringUtils.parseIdentifier(it) == "directaudio" }
    rows += XmbRow.Choice("audio", p.str(R.string.audio_driver), Icons.Filled.VolumeUp, audioEntries, p.labelFor(audioEntries, audioId),
        subtitle = if (!daSupported && daEntry != null) DirectAudioSupport.UNSUPPORTED_NOTE else null,
        disabledOptions = if (!daSupported && daEntry != null) setOf(daEntry) else emptySet(),
        confirm = { v -> if (StringUtils.parseIdentifier(v) == "directaudio") XmbConfirm("DirectAudio", "DirectAudio is experimental. Use it for this game?", "DirectAudio 是实验性的") else null }) { v ->
        xmb.set(p, "audioDriver", StringUtils.parseIdentifier(v))
    }
    if (audioId == "pulseaudio" || audioId == "alsa" || audioId == "directaudio") {
        rows += XmbRow.Link("audioSettings", "音频设置", Icons.Filled.Tune, subtitle = "延迟预设与微调") { audioMenu(xmb, p, audioId) }
    }
    val micActive = audioId == "directaudio" && daSupported
    val env = p.ex("envVars", "")
    rows += XmbRow.Toggle("mic", "麦克风", Icons.Filled.Mic, micActive && DirectAudioSupport.isMicEnabledInEnv(env),
        subtitle = "让游戏使用麦克风（DirectAudio捕获输入）",
        disabledReason = if (micActive) null else "可在DirectAudio驱动程序上使用") { want ->
        if (want && ContextCompat.checkSelfPermission(p.context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(p.context, "请先允许Bannerlator的麦克风权限（Android设置）。", Toast.LENGTH_LONG).show()
        } else {
            xmb.set(p, "envVars", DirectAudioSupport.withMicEnabled(p.ex("envVars", ""), want).ifEmpty { null })
        }
    }
    val emuEntries = p.arr(R.array.emulator_entries)
    val emuId = p.ex("emulator", c.emulator)
    val arm = p.arm64ec
    rows += XmbRow.Choice("emulator", "转译器", Icons.Filled.Memory,
        emuEntries.map { if (arm == true && StringUtils.parseIdentifier(it) == "box64") "WOWBox64" else it }.let { it },
        p.labelFor(emuEntries, emuId).let { if (arm == true && StringUtils.parseIdentifier(it) == "box64") "WOWBox64" else it },
        disabledReason = when (arm) { null -> "Checking the Wine build…"; false -> "Only arm64ec containers can switch"; else -> null }) { v ->
        val idx = emuEntries.indexOfFirst { it == v || (v == "WOWBox64" && StringUtils.parseIdentifier(it) == "box64") }
        if (idx >= 0) xmb.set(p, "emulator", StringUtils.parseIdentifier(emuEntries[idx]))
    }
    val midi = p.midiList
    if (midi.isNotEmpty()) {
        val curMidi = p.ex("midiSoundFont", c.getMIDISoundFont())
        rows += XmbRow.Choice("midi", "MIDI sound font", Icons.Filled.VolumeUp, midi, if (curMidi.isEmpty()) midi.first() else (midi.firstOrNull { it == curMidi } ?: midi.first())) { v ->
            xmb.set(p, "midiSoundFont", if (v == midi.first()) null else v)
        }
    }
    rows += XmbRow.Text("lcAll", "LC_ALL", Icons.Filled.ShortText, p.ex("lc_all", c.getLC_ALL()), subtitle = "游戏运行使用的区域设置") { xmb.set(p, "lc_all", it) }
    rows += XmbRow.Toggle("autoClose", "游戏退出时关闭", Icons.Filled.Close,
        p.ex("autoCloseOnExit", c.getExtra("autoCloseOnExit", "1") ?: "1") == "1") { xmb.set(p, "autoCloseOnExit", if (it) "1" else "0") }
    return rows
}

/** Panel refresh rates, same source as the editor (drives whether the refresh row exists). */
private fun xmbPanelRates(context: Context): List<Int> = runCatching {
    val display = if (android.os.Build.VERSION.SDK_INT >= 30) context.display
        else (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
    com.winlator.star.widget.XServerView.getSupportedRefreshRates(display).toList()
}.getOrDefault(emptyList())

/** Rename = the Game Details path (moves .desktop/.lnk + icon + cover). The shortcut object is stale
 *  afterwards, so go back to the games bar and reload the list. */
private fun renameAndReturn(xmb: XmbScope, s: Shortcut, newBase: String) {
    val container = s.container
    val oldBase = s.name
    xmb.scope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { ExeShortcutImporter.renameShortcutFiles(container, oldBase, newBase) }.getOrDefault(false) }
        xmb.toast(if (ok) "Renamed to “$newBase”" else "Couldn't rename — a game with that name may already exist")
        if (ok) { xmb.popToRoot(); xmb.reloadGames() }
    }
}

/** The file browser on the usual picture folders, showing images only; [onPick] gets the chosen file. */
private fun imageBrowserMenu(xmb: XmbScope, title: String, onPick: (File) -> Unit): XmbMenu {
    val start = File(android.os.Environment.getExternalStorageDirectory(), "Pictures").takeIf { it.isDirectory }
        ?: android.os.Environment.getExternalStorageDirectory()
    val roots = listOf(
        "Pictures" to File(android.os.Environment.getExternalStorageDirectory(), "Pictures"),
        "Download" to File(android.os.Environment.getExternalStorageDirectory(), "Download"),
        "Internal storage" to android.os.Environment.getExternalStorageDirectory(),
    ).filter { it.second.isDirectory }
    val imageExt = setOf("png", "jpg", "jpeg", "webp", "bmp")
    return xmbFileBrowserMenu(xmb, title, start, roots, { it.extension.lowercase() in imageExt }, onPick)
}

private fun iconPickerMenu(xmb: XmbScope, s: Shortcut): XmbMenu =
    imageBrowserMenu(xmb, "选择图标图像") { f ->
        xmb.scope.launch {
            val ok = withContext(Dispatchers.IO) { xmbApplyIconFile(s, f) }
            xmb.toast(if (ok) "Icon updated." else "Couldn't read that image.")
            if (ok) { xmb.pop(); xmb.reloadGames() }
        }
    }

/**
 * "Add your own cover": the picked image becomes the game's cover everywhere (CustomCover cuts it to the
 * 2:3 cover shape and writes both the full cover and the XMB icon). A bad pick leaves the browser open.
 */
private fun coverPickerMenu(xmb: XmbScope, s: Shortcut): XmbMenu {
    val ctx = xmb.context
    return imageBrowserMenu(xmb, ctx.getString(R.string.xmb_cover_browser_title)) { f ->
        xmb.scope.launch {
            val ok = withContext(Dispatchers.IO) { CustomCover.apply(s, f) != null }
            xmb.toast(ctx.getString(if (ok) R.string.xmb_cover_applied else R.string.xmb_cover_unreadable))
            if (ok) { xmb.pop(); xmb.saved(); xmb.refresh(); xmb.reloadGames() }
        }
    }
}

/** "Remove your cover": the cover the game had before comes back (CustomCover.remove). */
private fun removeCustomCover(xmb: XmbScope, s: Shortcut) {
    val ctx = xmb.context
    xmb.scope.launch {
        val ok = withContext(Dispatchers.IO) { CustomCover.remove(ctx, s) }
        xmb.toast(ctx.getString(if (ok) R.string.xmb_cover_removed else R.string.xmb_cover_remove_failed))
        xmb.refresh()
        if (ok) { xmb.saved(); xmb.reloadGames() }
    }
}

/** Decodes an image file and writes it as the game's icon PNG, the way the editor's icon picker does. */
internal fun xmbApplyIconFile(s: Shortcut, f: File): Boolean = runCatching {
    val bitmap = com.winlator.star.core.ImageUtils.decodeCoverArt(f.absolutePath) ?: return false
    s.iconFile?.let { out ->
        out.parentFile?.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    s.icon = bitmap
    true
}.getOrDefault(false)

// ── Controller ──────────────────────────────────────────────────────────────────────────────────────

internal fun xmbControllerMenu(xmb: XmbScope, p: XmbPrefs): XmbMenu =
    XmbMenu("Controller", Icons.Filled.Gamepad) { controllerRows(this, p) }

private fun controllerRows(xmb: XmbScope, p: XmbPrefs): List<XmbRow> {
    val s = p.shortcut
    val c = p.c
    val rows = mutableListOf<XmbRow>()
    // Game controller and Vibration: the container editor's settings in its order, each a per-game
    // override whose first choice, "Use container default (<its value>)", clears the game's own value
    // (the shortcut editor's Controller tab does the same). The Game controller rows are Wine-only.
    fun inherit(containerLabel: String) = "Use container default ($containerLabel)"
    fun onOff(on: Boolean) = if (on) "On" else "Off"
    // A "1" / "0" extra: Use container default / On / Off.
    fun onOffRow(key: String, extra: String, label: String, containerOn: Boolean, subtitle: String) {
        val labels = listOf(inherit(onOff(containerOn)), "On", "Off")
        val values = listOf("", "1", "0")
        val own = p.ex(extra, "").takeIf { it == "1" || it == "0" } ?: ""
        rows += XmbRow.Choice(key, label, Icons.Filled.Gamepad, labels, labels[values.indexOf(own)], subtitle = subtitle) { v ->
            xmb.set(p, extra, values[labels.indexOf(v).coerceAtLeast(0)].ifEmpty { null })
        }
    }
    fun helpRow(key: String, label: String, title: String, res: Int) {
        rows += XmbRow.Link(key, label, Icons.Filled.HelpOutline) { xmbHelpMenu(p.context, xmb.scope, title, res) }
    }
    if (!com.winlator.star.linux.LinuxShortcuts.isLinuxEntry(s)) {
        rows += XmbRow.Header("hController", "游戏控制器")
        // DirectInput visibility (core.DInputVisibility): written to this game's inputType / exclusiveXInput
        // extras, the values older builds read.
        run {
            val labels = listOf(inherit(onOff(DInputVisibility.forContainer(c))), "On", "Off")
            val own = if (!DInputVisibility.gameOwns(s)) 0 else if (DInputVisibility.forGame(c, s)) 1 else 2
            rows += XmbRow.Choice("dinputVisible", "DirectInput games can see the controller", Icons.Filled.Gamepad, labels, labels[own],
                subtitle = "DirectInput 游戏和控制器") { v ->
                DInputVisibility.writeTo(s, when (labels.indexOf(v)) { 1 -> true; 2 -> false; else -> null })
                xmb.saved(); s.saveData(); xmb.refresh()
            }
            helpRow("inputHelp", "DirectInput 开关有什么作用？", "DirectInput（直接输入）", R.string.help_dinput_visible_game)
        }
        // DirectInput vibration (core.DInputVibration). BANNERIN_DINPUT_FF in the env vars decides
        // instead: the row shows its value and is greyed.
        run {
            val labels = listOf(inherit(onOff(DInputVibration.forContainer(c))), "On", "Off")
            val values = listOf("", "1", "0")
            val env = DInputVibration.envOverride(c, s)
            val own = env?.let { if (it) "1" else "0" } ?: (DInputVibration.normalize(p.ex(DInputVibration.EXTRA, "")) ?: "")
            rows += XmbRow.Choice("dinputVibration", "DirectInput vibration", Icons.Filled.Vibration, labels, labels[values.indexOf(own)],
                subtitle = "DirectInput 游戏可以使控制器震动",
                disabledReason = DInputVibration.lockedReason(c, s)) { v ->
                xmb.set(p, DInputVibration.EXTRA, values[labels.indexOf(v).coerceAtLeast(0)].ifEmpty { null })
            }
            helpRow("dinputVibrationHelp", "什么是 DirectInput 震动？", "DirectInput vibration", R.string.help_dinput_vibration_game)
        }
        onOffRow("bannerinXInput", BannerinXInput.EXTRA_KEY, "Direct XInput (BANNERIN)", c.isBannerinXInput(), "XInput 游戏直接读取控制器")
        helpRow("bannerinXInputHelp", "什么是 Direct XInput？", "Direct XInput", R.string.help_bannerin_xinput_game)
        onOffRow("bannerinXInputSwitching", BannerinXInput.EXTRA_SWITCHING, "Allow in-game switching", c.isBannerinXInputSwitching(),
            "在游戏内菜单中切换 Direct XInput")
        helpRow("bannerinXInputSwitchingHelp", "什么是游戏内切换？", "Allow in-game switching", R.string.help_bannerin_xinput_switching_game)
        // Stick updates (core.StickPacing): the Deck's presets, plus this game's own value if it is another.
        run {
            val presets = listOf("auto:60", "auto:120", "auto:30", "fixed:60", "fixed:125", "fixed:250", "off")
            val own = StickPacing.parse(p.ex(StickPacing.EXTRA, ""))?.format() ?: ""
            val values = listOf("") + presets + (if (own.isNotEmpty() && own !in presets) listOf(own) else emptyList())
            val labels = values.map { v -> if (v.isEmpty()) inherit(StickPacing.forContainer(c).describe()) else StickPacing.parse(v)!!.describe() }
            rows += XmbRow.Choice("stickPacing", "Stick updates", Icons.Filled.Gamepad, labels, labels[values.indexOf(own)],
                subtitle = "摇杆移动到达游戏的频率") { v ->
                xmb.set(p, StickPacing.EXTRA, values[labels.indexOf(v).coerceAtLeast(0)].ifEmpty { null })
            }
            helpRow("stickPacingHelp", "什么是摇杆更新？", "Stick updates", R.string.help_stick_pacing_game)
        }
        // Controller identity: this game's own value, else the container's.
        run {
            val containerIdentity = ControllerIdentity.forWine(c.getControllerIdentity())
            val own = ControllerIdentity.normalize(p.ex(ControllerIdentity.EXTRA, ""))?.let { ControllerIdentity.forWine(it) } ?: ""
            val choices = ControllerIdentity.wineChoices()
            val values = listOf("") + choices.map { it.value }
            val labels = listOf(inherit(ControllerIdentity.label(containerIdentity))) + choices.map { it.label }
            rows += XmbRow.Choice("controllerIdentity", "Controller identity", Icons.Filled.Gamepad, labels,
                labels[values.indexOf(own).coerceAtLeast(0)], subtitle = "游戏看到哪个控制器") { v ->
                xmb.set(p, ControllerIdentity.EXTRA, values[labels.indexOf(v).coerceAtLeast(0)].ifEmpty { null })
            }
            rows += XmbRow.Link("controllerIdentityHelp", "什么是控制器身份？", Icons.Filled.HelpOutline, subtitle = "通用、Xbox、DirectInput 和实验性类型") {
                xmbHelpMenu(p.context, xmb.scope, "Controller identity", R.string.help_controller_identity_game)
            }
        }
        rows += XmbRow.Toggle("disableX", "禁用XInput", Icons.Filled.Gamepad, p.ex("disableXinput", "0") == "1") { xmb.set(p, "disableXinput", if (it) "1" else null) }
    }

    rows += XmbRow.Header("hVibration", p.str(R.string.vibration))
    run {
        val modeLabels = listOf(p.str(R.string.vibration_mode_off), p.str(R.string.vibration_mode_controller),
            p.str(R.string.vibration_mode_device), p.str(R.string.vibration_mode_both))
        val ownMode = p.ex("vibrationMode", "").toIntOrNull()?.takeIf { it in Container.VIBRATION_MODE_OFF..Container.VIBRATION_MODE_BOTH }
        val labels = listOf(inherit(modeLabels.getOrElse(c.getVibrationMode()) { modeLabels[Container.VIBRATION_MODE_DEFAULT] })) + modeLabels
        rows += XmbRow.Choice("vibrationMode", p.str(R.string.vibration_mode_label), Icons.Filled.Vibration, labels,
            labels[(ownMode ?: -1) + 1]) { v ->
            val idx = labels.indexOf(v)
            xmb.set(p, "vibrationMode", if (idx <= 0) null else (idx - 1).toString())
        }
        helpRow("vibrationModeHelp", "什么是震动目标？", p.str(R.string.vibration_mode_label), R.string.help_vibration_mode_game)
        val mode = ownMode ?: c.getVibrationMode()
        if (mode != Container.VIBRATION_MODE_OFF) {
            val own = p.ex("vibrationIntensity", "").toIntOrNull()?.takeIf { it in 0..100 }
            val steps = (0..100 step 10).toList() + (if (own != null && own % 10 != 0) listOf(own) else emptyList())
            val values = listOf<Int?>(null) + steps.sorted()
            val labels = values.map { v -> if (v == null) inherit("${c.getVibrationIntensity()}%") else "$v%" }
            rows += XmbRow.Choice("vibrationIntensity", p.str(R.string.vibration_intensity_label), Icons.Filled.Vibration, labels,
                labels[values.indexOf(own).coerceAtLeast(0)]) { v ->
                xmb.set(p, "vibrationIntensity", values.getOrNull(labels.indexOf(v))?.toString())
            }
            helpRow("vibrationIntensityHelp", "什么是强度？", p.str(R.string.vibration_intensity_label), R.string.help_vibration_intensity_game)
        }
        // Rumble motors: only a controller has two motors (the phone's vibrator is one).
        if (mode == Container.VIBRATION_MODE_CONTROLLER || mode == Container.VIBRATION_MODE_BOTH) {
            val motorLabels = listOf(p.str(R.string.rumble_motors_both), p.str(R.string.rumble_motors_single))
            val own = RumbleMotors.parse(p.ex(RumbleMotors.EXTRA, ""))
            val labels = listOf(inherit(motorLabels[RumbleMotors.sanitize(c.getRumbleMotors())])) + motorLabels
            rows += XmbRow.Choice("rumbleMotors", p.str(R.string.rumble_motors_label), Icons.Filled.Vibration, labels,
                labels[(own ?: -1) + 1]) { v ->
                val idx = labels.indexOf(v)
                xmb.set(p, RumbleMotors.EXTRA, if (idx <= 0) null else (idx - 1).toString())
            }
            helpRow("rumbleMotorsHelp", "什么是震动马达？", p.str(R.string.rumble_motors_label), R.string.help_rumble_motors_game)
        }
    }

    rows += XmbRow.Header("hInput", "Input")
    rows += XmbRow.Toggle("simTouch", "触摸屏模式", Icons.Filled.TouchApp, p.ex("simTouchScreen", "0") == "1") { xmb.set(p, "simTouchScreen", if (it) "1" else "0") }
    val nc = p.arr(R.array.num_controllers_entries)
    val ncIdx = ((p.ex("numControllers", "1").toIntOrNull() ?: 1).coerceIn(1, nc.size)) - 1
    rows += XmbRow.Choice("numControllers", "控制器数量", Icons.Filled.Gamepad, nc, nc.getOrElse(ncIdx) { nc.first() }) { v ->
        xmb.set(p, "numControllers", (nc.indexOf(v) + 1).coerceAtLeast(1).toString())
    }
    val ahValues = listOf("", "1", "0")
    val ahLabels = listOf("使用容器默认", "On", "Off")
    val ah = p.ex("autoHideControlsOnPad", "")
    rows += XmbRow.Choice("autoHide", "使用手柄时隐藏屏幕控制", Icons.Filled.TouchApp, ahLabels, ahLabels[ahValues.indexOf(ah).coerceAtLeast(0)],
        subtitle = "当控制器连接时") { v -> xmb.set(p, "autoHideControlsOnPad", ahValues[ahLabels.indexOf(v)].ifEmpty { null }) }
    rows += XmbRow.Link("playerSlots", "玩家槽位", Icons.Filled.Gamepad,
        value = if ((s.getExtra("controllerSlotOverrides") ?: "").isEmpty()) "容器默认" else "Custom",
        subtitle = "两个在一个玩家上共享它。 ") { xmbPlayerSlotsMenu(xmb, s) }

    // Motion aim (all eight extras are independent overrides; no inherit state, as in the editor)
    rows += XmbRow.Header("hGyro", "体感瞄准")
    val gyroOn = p.ex("gyroEnabled", if (c.isGyroEnabled) "1" else "0") == "1"
    rows += XmbRow.Toggle("gyro", "启用体感瞄准", Icons.Filled.ScreenRotation, gyroOn) { xmb.set(p, "gyroEnabled", if (it) "1" else "0") }
    if (gyroOn) {
        val modeL = listOf(p.str(R.string.gyro_mode_rate), p.str(R.string.gyro_mode_orientation))
        val targetL = listOf(p.str(R.string.gyro_target_right_stick), p.str(R.string.gyro_target_left_stick), p.str(R.string.gyro_target_mouse))
        val actL = listOf(p.str(R.string.gyro_activator_l1), p.str(R.string.gyro_activator_l2), p.str(R.string.gyro_activator_r1), p.str(R.string.gyro_activator_r3), p.str(R.string.gyro_activator_always))
        val actModeL = listOf(p.str(R.string.gyro_activation_hold), p.str(R.string.gyro_activation_toggle))
        fun gi(key: String, cv: Int, def: Int) = p.ex(key, cv.toString()).toIntOrNull() ?: def
        val mode = gi("gyroMode", c.gyroMode, Container.GYRO_MODE_DEFAULT)
        val target = gi("gyroTarget", c.gyroTarget, Container.GYRO_TARGET_DEFAULT)
        val act = gi("gyroActivator", c.gyroActivator, Container.GYRO_ACTIVATOR_DEFAULT)
        val actMode = gi("gyroActivationMode", c.gyroActivationMode, Container.GYRO_ACTIVATION_MODE_DEFAULT)
        rows += XmbRow.Choice("gyroMode", "体感模式", Icons.Filled.ScreenRotation, modeL, modeL.getOrElse(mode) { modeL[0] }) { v ->
            val nm = modeL.indexOf(v).coerceAtLeast(0)
            if (nm == Container.GYRO_MODE_ORIENTATION && target == Container.GYRO_TARGET_MOUSE) s.putExtra("gyroTarget", Container.GYRO_TARGET_RIGHT_STICK.toString())
            xmb.set(p, "gyroMode", nm.toString())
        }
        rows += XmbRow.Choice("gyroTarget", "体感目标", Icons.Filled.ScreenRotation, targetL, targetL.getOrElse(target) { targetL[0] }) { v ->
            val nt = targetL.indexOf(v).coerceAtLeast(0)
            if (nt == Container.GYRO_TARGET_MOUSE) s.putExtra("gyroMode", Container.GYRO_MODE_RATE.toString())
            xmb.set(p, "gyroTarget", nt.toString())
        }
        rows += XmbRow.Choice("gyroActivator", "激活按钮", Icons.Filled.Gamepad, actL, actL.getOrElse(act) { actL[0] }) { v ->
            xmb.set(p, "gyroActivator", actL.indexOf(v).coerceAtLeast(0).toString())
        }
        if (act != Container.GYRO_ACTIVATOR_ALWAYS) {
            rows += XmbRow.Choice("gyroActMode", "激活", Icons.Filled.Gamepad, actModeL, actModeL.getOrElse(actMode) { actModeL[0] }) { v ->
                xmb.set(p, "gyroActivationMode", actModeL.indexOf(v).coerceAtLeast(0).toString())
            }
        }
        val sens = p.ex("gyroSensitivity", c.gyroSensitivity.toString()).toFloatOrNull() ?: Container.GYRO_SENSITIVITY_DEFAULT
        rows += XmbRow.Slider("gyroSens", "灵敏度", Icons.Filled.Tune, sens, 0.1f, 10f, 0.5f) { xmb.set(p, "gyroSensitivity", it.toString()) }
        rows += XmbRow.Toggle("gyroInvX", p.str(R.string.gyro_invert_x), Icons.Filled.ScreenRotation,
            p.ex("gyroInvertX", if (c.isGyroInvertX) "1" else "0") == "1") { xmb.set(p, "gyroInvertX", if (it) "1" else "0") }
        rows += XmbRow.Toggle("gyroInvY", p.str(R.string.gyro_invert_y), Icons.Filled.ScreenRotation,
            p.ex("gyroInvertY", if (c.isGyroInvertY) "1" else "0") == "1") { xmb.set(p, "gyroInvertY", if (it) "1" else "0") }
    }
    return rows
}

// ── Audio settings (the editor's per-game AudioSettingsDialog, as XMB rows; stored in envVars) ──────

private fun audioMenu(xmb: XmbScope, p: XmbPrefs, driverId: String): XmbMenu = XmbMenu("音频设置", Icons.Filled.VolumeUp) {
    val env = p.ex("envVars", "")
    val cfg = audioConfigFromEnv(env, driverId)
    val direct = latencyIsDirectBuffer(driverId)
    val used = latencyUsedBy(driverId)
    val custom = cfg.preset == PRESET_CUSTOM
    fun write(nc: com.winlator.star.ui.components.AudioConfig) =
        xmb.set(p, "envVars", audioConfigToEnv(p.ex("envVars", ""), nc, driverId).ifEmpty { null })
    val presetNames = AUDIO_PRESETS.map { it.name }
    val cur = AUDIO_PRESETS.firstOrNull { it.id == cfg.preset } ?: AUDIO_PRESETS.first()
    val rows = mutableListOf<XmbRow>()
    rows += XmbRow.Choice("preset", "预设", Icons.Filled.Tune, presetNames, cur.name, subtitle = cur.desc) { v ->
        val pr = AUDIO_PRESETS.first { it.name == v }
        write(if (direct) pr.cfg.copy(latencyMsec = cfg.latencyMsec) else pr.cfg)
    }
    val lockReason = if (custom) null else "选择自定义预设以更改此设置"
    val perfOpts = if (driverId == "directaudio") listOf("None" to 0, "Low latency" to 1) else listOf("None" to 0, "Low latency" to 1, "Power saving" to 2)
    rows += XmbRow.Choice("perf", "输出模式", Icons.Filled.Speed, perfOpts.map { it.first },
        perfOpts.firstOrNull { it.second == cfg.perfMode }?.first ?: perfOpts.first().first, subtitle = "AAudio性能模式", disabledReason = lockReason) { v ->
        write(cfg.copy(perfMode = perfOpts.first { it.first == v }.second))
    }
    rows += XmbRow.Toggle("adaptive", "自适应缓冲区", Icons.Filled.Tune, cfg.adaptive, subtitle = "仅在检测到爆音时自动增长", disabledReason = lockReason) { write(cfg.copy(adaptive = it)) }
    rows += XmbRow.Slider("latency", if (direct) "音频缓冲区" else "客户缓冲区", Icons.Filled.Timer, cfg.latencyMsec.toFloat(),
        if (direct) 4f else 20f, if (direct) 120f else 200f, if (direct) 4f else 10f, format = { "${it.toInt()} ms" },
        subtitle = if (!used) "ALSA不使用 — 在下方设置缓冲区行数" else null,
        disabledReason = lockReason ?: if (!used) "ALSA不使用" else null) { write(cfg.copy(latencyMsec = it.toInt())) }
    rows += XmbRow.Slider("bf", "初始sink缓冲区", Icons.Filled.Tune, cfg.bufferFrames.toFloat(), 0f, 8192f, 256f,
        format = { if (it <= 0f) "auto" else "${it.toInt()} frames" }, disabledReason = lockReason) { write(cfg.copy(bufferFrames = it.toInt())) }
    rows += XmbRow.Slider("mbf", "最大sink缓冲区", Icons.Filled.Tune, cfg.maxBufferFrames.toFloat(), 0f, 16384f, 256f,
        format = { if (it <= 0f) "device" else "${it.toInt()} frames" }, disabledReason = lockReason) { write(cfg.copy(maxBufferFrames = it.toInt())) }
    rows
}

// ── Game Details ────────────────────────────────────────────────────────────────────────────────────

internal fun xmbGameDetailsMenu(xmb: XmbScope, s: Shortcut): XmbMenu {
    var results by mutableStateOf<List<SteamStoreSearch.SteamSuggestion>>(emptyList())
    var searching by mutableStateOf(false)
    fun details(): GameDetails = GameDetails.from(s)
    fun write(d: GameDetails) { runCatching { d.writeTo(s) }; xmb.saved(); xmb.refresh() }
    // Opens on the name, not on "从Steam解除关联" (a stray A must not unlink).
    return XmbMenu("游戏详情", Icons.Filled.Edit, initialKey = "name") {
        val d = details()
        val rows = mutableListOf<XmbRow>()
        d.steamAppId?.takeIf { it > 0 }?.let { id ->
            rows += XmbRow.Info("app", "Steam App ID", Icons.Filled.Star, id.toString())
            rows += XmbRow.Action("unlink", "从Steam解除关联", Icons.Filled.LinkOff) { write(d.copy(steamAppId = null)) }
        }
        rows += XmbRow.Text("name", "游戏名称", Icons.Filled.ShortText, s.name) { v ->
            val nb = v.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
            if (nb.isNotEmpty() && nb != s.name) renameAndReturn(xmb, s, nb)
        }
        // Next to the cover a game gets from Scrape cover: the user's own image, and the way back from it.
        val coverCtx = xmb.context
        rows += XmbRow.Link("coverAdd", coverCtx.getString(R.string.xmb_cover_add), Icons.Filled.Image,
            subtitle = coverCtx.getString(R.string.xmb_cover_add_hint)) { coverPickerMenu(xmb, s) }
        if (CustomCover.isCustom(s)) {
            rows += XmbRow.Action("coverRemove", coverCtx.getString(R.string.xmb_cover_remove), Icons.Filled.Delete,
                subtitle = coverCtx.getString(R.string.xmb_cover_remove_hint)) { removeCustomCover(xmb, s) }
        }
        rows += XmbRow.Link("search", "搜索 Steam", Icons.Filled.Search, subtitle = "选择一个结果以填充所有字段") {
            results = emptyList(); searching = true
            val q = s.name
            xmb.scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { SteamStoreSearch.searchByName(q) }.getOrDefault(emptyList()) }
                results = r; searching = false
            }
            XmbMenu("搜索 Steam", Icons.Filled.Search) {
                when {
                    searching -> listOf(XmbRow.Info("busy", "正在搜索Steam…", Icons.Filled.Search))
                    results.isEmpty() -> listOf(XmbRow.Info("none", "No results found for “$q”", Icons.Filled.Search))
                    else -> results.mapIndexed { i, r ->
                        XmbRow.Action("r$i", r.name, Icons.Filled.Search, subtitle = "App ID: ${r.appId}") {
                            xmb.scope.launch {
                                xmb.toast("Filling from Steam…")
                                val info = withContext(Dispatchers.IO) { runCatching { SteamStoreSearch.fetchDetails(r.appId) }.getOrNull() }
                                val base = details()
                                val nd = if (info != null) GameDetails(
                                    steamAppId = r.appId, genres = info.genres, description = info.shortDescription,
                                    releaseYear = info.releaseYear, metacritic = info.metacritic,
                                ) else base.copy(steamAppId = r.appId)
                                withContext(Dispatchers.IO) { runCatching { nd.writeTo(s) }; runCatching { applySteamCover(s.container, s.name, r.appId) } }
                                xmb.saved(); xmb.pop(); xmb.reloadGames()
                                if (info != null && info.name.isNotBlank() && info.name != s.name) {
                                    val nb = info.name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
                                    if (nb.isNotEmpty()) renameAndReturn(xmb, s, nb)
                                }
                            }
                        }
                    }
                }
            }
        }
        rows += XmbRow.Text("genres", "类型", Icons.Filled.Label, d.genres.joinToString(", "), subtitle = "用逗号分隔", placeholder = "例如：动作, 角色扮演, 策略") { v ->
            write(d.copy(genres = v.split(",").map { it.trim() }.filter { it.isNotBlank() }))
        }
        rows += XmbRow.Text("desc", "Description", Icons.Filled.Description, d.description ?: "", subtitle = "显示在启动屏幕上") { v ->
            write(d.copy(description = v.trim().takeIf { it.isNotBlank() }))
        }
        rows += XmbRow.Text("year", "发行年份", Icons.Filled.Event, d.releaseYear ?: "", numeric = true, placeholder = "例如 2023") { v ->
            write(d.copy(releaseYear = v.trim().take(4).takeIf { it.isNotBlank() }))
        }
        rows += XmbRow.Text("mc", "Metacritic评分", Icons.Filled.Star, d.metacritic?.toString() ?: "", subtitle = "1–100，留空则隐藏", numeric = true) { v ->
            write(d.copy(metacritic = v.trim().toIntOrNull()?.takeIf { it in 1..100 }))
        }
        rows
    }
}

// ── Properties ──────────────────────────────────────────────────────────────────────────────────────

// Opens on the play count, not on "重置属性".
internal fun xmbPropertiesMenu(xmb: XmbScope, s: Shortcut): XmbMenu = XmbMenu("属性", Icons.Filled.Info, initialKey = "plays") {
    val prefs = xmb.context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
    val tKey = "${s.name}_playtime"
    val cKey = "${s.name}_play_count"
    val totalMs = prefs.getLong(tKey, 0L)
    val sec = (totalMs / 1000) % 60
    val min = (totalMs / 60000) % 60
    val hr = (totalMs / 3600000) % 24
    val days = totalMs / 86400000
    listOf(
        XmbRow.Info("plays", "游玩次数", Icons.Filled.Star, prefs.getInt(cKey, 0).toString()),
        XmbRow.Info("time", "游玩时间", Icons.Filled.Timer, String.format("%dd %02dh %02dm %02ds", days, hr, min, sec)),
        XmbRow.Action("reset", "重置属性", Icons.Filled.Refresh, danger = true) {
            xmb.confirm(XmbConfirm("重置属性", "Clear times played and playtime for “${s.name}”?", "重置", danger = true)) {
                prefs.edit().remove(tKey).remove(cKey).apply()
                xmb.saved(); xmb.refresh()
            }
        },
    )
}

// ── Clone / Remove ──────────────────────────────────────────────────────────────────────────────────

internal fun xmbCloneMenu(xmb: XmbScope, s: Shortcut, host: XmbGameHost): XmbMenu = XmbMenu("选择容器", Icons.Filled.ContentCopy) {
    val others = host.containers().filter { it.id != s.container.id }
    if (others.isEmpty()) XmbTools.para("none", NO_OTHER_CONTAINER_XMB, Icons.Filled.Info, context)
    else others.mapIndexed { i, c ->
        XmbRow.Action("c${c.id}_$i", c.name, Icons.Filled.Folder) { xmbMoveGame(xmb, s, c, keepCopy = true) }
    }
}

private const val NO_OTHER_CONTAINER_XMB = "There's no other container. Create one in the Containers screen first."

/** "Container": pick another container, then Move (default) or Copy. MoveGameToContainer does the work. */
internal fun xmbMoveToContainerMenu(xmb: XmbScope, s: Shortcut, host: XmbGameHost): XmbMenu =
    XmbMenu("Move to container", Icons.Filled.Widgets) {
        val others = host.containers().filter { it.id != s.container.id }
        if (others.isEmpty()) XmbTools.para("none", NO_OTHER_CONTAINER_XMB, Icons.Filled.Info, context)
        else others.map { c ->
            XmbRow.Link("c${c.id}", c.name, Icons.Filled.Widgets, subtitle = c.wineVersion) { xmbMoveChoiceMenu(xmb, s, c) }
        }
    }

/** Move here / Copy here, with what comes along ([MoveGameToContainer.plan]) as toggles below. */
private fun xmbMoveChoiceMenu(xmb: XmbScope, s: Shortcut, c: Container): XmbMenu {
    var plan by mutableStateOf<MoveGameToContainer.Plan?>(null)
    var saves by mutableStateOf(false)
    var folder by mutableStateOf(false)
    var extras by mutableStateOf(emptySet<String>())
    val job = xmb.scope.launch {
        val p = withContext(Dispatchers.IO) { MoveGameToContainer.plan(xmb.context, s, c) }
        saves = p.saves != null
        folder = p.gameFolder != null && p.gameFolder.problem == null
        extras = p.extras.filter { it.suggested && it.source != MoveGameToContainer.ExtraSource.UNAVAILABLE }.map { it.name }.toSet()
        plan = p
    }
    fun options(p: MoveGameToContainer.Plan) =
        MoveGameToContainer.Options(saves && p.saves != null, folder && p.gameFolder != null, extras)

    return XmbMenu("Move to \"${c.name}\"", Icons.Filled.Widgets, initialKey = "move", onClose = { job.cancel() }) {
        val p = plan
        if (p == null) return@XmbMenu listOf(XmbRow.Info("loading", "Checking what this game uses…", Icons.Filled.HourglassEmpty))
        buildList<XmbRow> {
            add(XmbRow.Action("move", "Move here", Icons.Filled.DriveFileMove) { xmbMoveGame(xmb, s, c, keepCopy = false, options(p), p) })
            add(XmbRow.Action("copy", "Copy here, keep it in \"${s.container.name}\"", Icons.Filled.ContentCopy) {
                xmbMoveGame(xmb, s, c, keepCopy = true, options(p), p)
            })
            add(XmbRow.Header("hBring", "Bring along"))
            add(XmbRow.Toggle("saves", "Saves", Icons.Filled.Save, saves && p.saves != null, subtitle = p.saves?.label,
                disabledReason = if (p.saves == null) "No save folders found for this game" else null) { saves = it })
            p.gameFolder?.let { gf ->
                add(XmbRow.Toggle("folder", "游戏文件", Icons.Filled.Folder, folder && gf.problem == null,
                    subtitle = "C:\\" + gf.relPath.replace('/', '\\'), disabledReason = gf.problem) { folder = it })
            }
            if (p.extras.isEmpty()) add(XmbRow.Info("noExtras", "Extras: nothing missing in \"${c.name}\"", Icons.Filled.Info))
            p.extras.forEach { e ->
                add(XmbRow.Toggle("x:${e.name}", e.label, Icons.Filled.Widgets, e.name in extras, subtitle = e.note,
                    disabledReason = if (e.source == MoveGameToContainer.ExtraSource.UNAVAILABLE) e.note else null) { on ->
                    extras = if (on) extras + e.name else extras - e.name
                })
            }
        }
    }
}

private fun xmbMoveGame(
    xmb: XmbScope, s: Shortcut, c: Container, keepCopy: Boolean,
    options: MoveGameToContainer.Options = MoveGameToContainer.Options(), plan: MoveGameToContainer.Plan? = null,
) {
    xmb.toast(if (keepCopy) "Copying \"${s.name}\"…" else "Moving \"${s.name}\"…")
    xmb.scope.launch {
        val r = withContext(Dispatchers.IO) { MoveGameToContainer.run(xmb.context, s, c, keepCopy, options, plan) }
        xmb.toast(MoveGameToContainer.message(r, s.name, c.name, keepCopy))
        if (r is MoveGameToContainer.Result.Done) {
            xmb.popToRoot()
            xmb.reloadGames()
            if (r.emuConflicts.isNotEmpty()) xmbEmuConflicts(xmb, r.emuConflicts)
        }
    }
}

internal fun xmbRemoveMenu(xmb: XmbScope, s: Shortcut, host: XmbGameHost): XmbMenu =
    XmbMenu("移除快捷方式?", Icons.Filled.Delete, initialKey = "cancel") {
        listOf(
            XmbRow.Info("msg", "Remove “${s.name}”?", Icons.Filled.Info),
            XmbRow.Action("remove", "移除", Icons.Filled.Delete, danger = true) {
                val ok = host.remove(s)
                xmb.toast(if (ok) "快捷方式已删除。" else "移除快捷方式失败。")
                xmb.popToRoot()
                xmb.reloadGames()
            },
            XmbRow.Action("cancel", "Cancel", Icons.Filled.Close) { xmb.pop() },
        )
    }
