package com.winlator.star.ui.deck.settings

import com.winlator.star.xenvironment.components.YServerComponent
import com.winlator.star.ui.simple.L
import com.winlator.star.ui.simple.T
import androidx.compose.material.icons.Icons
import androidx.preference.PreferenceManager
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material.icons.filled.Widgets
import com.winlator.star.FeatureFlags
import com.winlator.star.container.Container
import com.winlator.star.contentdialog.GraphicsDriverConfigDialog
import com.winlator.star.core.BannerinXInput
import com.winlator.star.core.ControllerIdentity
import com.winlator.star.core.DInputVibration
import com.winlator.star.core.DInputVisibility
import com.winlator.star.core.DirectAudioSupport
import com.winlator.star.core.FastOpenGL
import com.winlator.star.core.KeyValueSet
import com.winlator.star.core.LinuxVulkanDriver
import com.winlator.star.core.MediaDecoder
import com.winlator.star.core.RumbleMotors
import com.winlator.star.core.TouchAimSettings
import com.winlator.star.core.StickPacing
import com.winlator.star.core.StringUtils
import com.winlator.star.core.SyncMode
import com.winlator.star.core.SyncSupport
import com.winlator.star.core.UnrealHdr
import com.winlator.star.display.WaylandHdr
import com.winlator.star.linux.LinuxTuning
import com.winlator.star.midi.MidiManager
import com.winlator.star.perf.PerformanceSettings
import com.winlator.star.ui.deck.settings.SettingLevel.ADVANCED
import com.winlator.star.ui.deck.settings.SettingLevel.EXPERT
import com.winlator.star.ui.deck.settings.SettingLevel.SIMPLE
import com.winlator.star.ui.screens.ContainerDetailViewModel
import com.winlator.star.ui.screens.MALI_SYSTEM_DRIVER_NOTE
import com.winlator.star.ui.screens.compositorDriverLabel
import com.winlator.star.ui.screens.compositorDriverMaliSystem
import com.winlator.star.ui.screens.compositorDriverUnusable
import com.winlator.star.ui.screens.withGraphicsDriverVersion
import java.util.Locale

// Every setting the Deck editor edits itself, for a game, a container and All containers. Labels,
// hints and grouping follow the Deck design (proto/data/settings.js). The storage is today's: the
// container form writes exactly what the classic container editor writes, and a game writes the same
// shortcut extras the classic game editor writes, but only the ones the user actually changed.
//
// Per-game rows are only offered where the launch path reads the shortcut extra with the container as
// the fallback (XServerDisplayActivity, GuestProgramLauncherComponent). A container field the launch
// never reads per game (MIDI, the overlay switch, gyro deadzone and smoothing) is shown read-only in a
// game's editor as a container setting.
//
// Each row names its worlds (World). The Linux Steam client entry gets exactly the rows the classic game
// editor shows it (its isLinuxEntry gates), an Android game only its name, and a Wine game never a
// Linux runtime row.

internal object SettingsRegistry {

    val categories: List<SettingsCategory> = listOf(
        SettingsCategory("game", T("Game"), T("仅此一个游戏的名称和启动选项。"), Icons.Filled.SportsEsports),
        SettingsCategory("launch", T("Launch"), T("此游戏如何启动：是否使用 Steam、控制器交接、反作弊、Epic 登录。"), Icons.Filled.RocketLaunch),
        SettingsCategory("display", T("显示"), T("分辨率、画面如何填满屏幕、刷新率和 HDR。"), Icons.Filled.Monitor),
        SettingsCategory("graphics", T("Graphics"), T("DirectX 转换、GPU 驱动程序、OpenGL 以及画面如何呈现到屏幕上。"), Icons.Filled.Layers),
        SettingsCategory("performance", T("Performance"), T("帧率限制、帧生成、同步、x86 翻译器、CPU 核心、后台服务。"), Icons.Filled.Speed),
        SettingsCategory("visuals", T("视觉效果"), T("锐化和其他后期效果。"), Icons.Filled.AutoAwesome),
        SettingsCategory("audio", T("Audio"), T("声音驱动程序和 MIDI 音乐。"), Icons.AutoMirrored.Filled.VolumeUp),
        SettingsCategory("input", T("Controls"), T("控制器、屏幕控制、鼠标和触摸、震动和体感瞄准。"), Icons.Filled.Gamepad),
        SettingsCategory("hud", T("Performance overlay"), T("屏幕上的 FPS 和温度读数。"), Icons.Filled.MonitorHeart),
        SettingsCategory("windows", "Windows", T("容器名称、语言、桌面和会话行为。"), Icons.Filled.WebAsset),
        SettingsCategory(CAT_COMPONENTS, T("Windows 组件"), T("将运行时和库安装到容器中，并选择 Wine 或 Microsoft 的 DLL。"), Icons.Filled.Widgets),
        SettingsCategory("linux", T("Linux runtime"), T("仅用于 Linux Steam 客户端条目：其驱动程序、Steam 客户端选项和速度调整。"), Icons.Filled.Terminal),
        SettingsCategory("expert", T("专家"), T("原始环境变量和翻译器变量。"), Icons.Filled.Build),
    )

    val defs: List<SettingDef> by lazy { buildDefs() }

    /** The classic game editor. */
    const val TARGET_CLASSIC = "classic"
    /** A game's Game Details sheet (name, cover art, Steam link, genres), run by the Games screen. */
    const val TARGET_DETAILS = "details"
    /** Change the program a game runs, over the current Deck page (the shell's game-actions host). */
    const val TARGET_CHANGE_EXE = "change_exe"
    /** Copy a game to drive C, over the current Deck page. */
    const val TARGET_DRIVE_C = "drive_c"
    /** Move a game to another container (or copy it there), over the current Deck page. */
    const val TARGET_MOVE_CONTAINER = "move_container"
    /** The Edit container page of the game's container (the XMB game page's link to it). */
    const val TARGET_CONTAINER = "container"
    /** Information only, nothing to open. */
    const val TARGET_NONE = "none"
    /** Another category of this editor (see [categoryTarget]). */
    const val TARGET_CATEGORY = "category:"

    /** The Windows components page: install lists (DeckComponentsPage) above and below its DLL rows. */
    const val CAT_COMPONENTS = "components"

    fun categoryTarget(id: String) = TARGET_CATEGORY + id

    /**
     * Rows that open another screen (or say where something lives) for what is not edited here. Each
     * names its worlds. None opens the classic editor any more ([TARGET_CLASSIC] stays handled by the
     * screens for a hand-off that may need it again). [xmb]: a game's page also links to its container's Edit page (the chain of
     * links over the other interfaces' header does not exist there).
     */
    fun handoffs(scope: EditorScope, env: SettingsEnv, xmb: Boolean = false): Map<String, List<Handoff>> {
        val game = scope == EditorScope.GAME
        val m = linkedMapOf<String, List<Handoff>>()
        if (game) {
            val containerName = env.shortcut?.container?.name.orEmpty()
            m["game"] = listOf(
                Handoff(T("名称、封面图和详情"), T("来自您的文件、Steam 或 SteamGridDB 的封面图，Steam 链接、类型、年份和描述（游戏详情）。"), TARGET_DETAILS, ALL_WORLDS),
                // The icon is picked on this page now (DeckIconSection); the file operations stay with the Games screen.
                Handoff(T("更改要运行的程序"), T("为此条目选择游戏文件夹中的另一个 .exe。"), TARGET_CHANGE_EXE, WINE_ONLY),
                Handoff(T("将游戏复制到 C 盘"), T("将游戏文件夹复制到容器的 C 盘并从那里运行。"), TARGET_DRIVE_C, WINE_ONLY),
                Handoff(T("移动到另一个容器"), "Now in \"$containerName\". Move this game to another container, or copy it there.", TARGET_MOVE_CONTAINER, WINE_ONLY),
            ) + listOfNotNull(
                if (xmb) Handoff(T("容器设置"), "Open the settings of \"$containerName\", the container this game runs in.", TARGET_CONTAINER, WINE_ONLY) else null,
            )
            m["launch"] = listOf(Handoff(T("离线 Steam 模式、文件检查和更新"), T("Goldberg 模式、验证文件和检查更新位于启动菜单中。关闭 “跳过启动菜单” 以在下次启动时看到它。"), TARGET_NONE))
        }
        // Display, Graphics, Performance, Visual effects, Audio, Controls and the overlay have no hand-offs any
        // more: the TV rows, the Wayland driver gear, an imported wrapper's detected settings, Custom services,
        // preset values, power and root, ReShade, sound latency and the microphone, player slots, the XR
        // mapping and the overlay style are rows and sections of their pages (the Deck*Settings files).
        m["windows"] = listOfNotNull(
            // Its own page now: the install lists and the builtin / native DLL rows (CAT_COMPONENTS).
            Handoff(
                T("Windows 组件"),
                if (scope == EditorScope.DEFAULTS) T("Builtin or native DLLs for DirectX, audio and runtimes, and the media codecs.")
                else T("将运行时和库安装到容器中，并为 DirectX、音频和运行时选择内置或原生 DLL。"),
                categoryTarget(CAT_COMPONENTS),
            ),
            // The Wine layer, wallpaper and colours, the drives and Reset to app defaults are this page's own (DeckWindowsSettings).
        )
        // The Linux entry's cores, Games folders and component swaps are the Linux runtime page's own
        // (LinuxRuntimeRows, DeckLinuxSection), and the environment variables the Expert page's
        // (DeckEnvVarsSection), for every scope and world that had the classic Env Vars tab.
        return m
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    private fun onOff(b: Boolean) = if (b) "1" else "0"
    private fun idOf(s: String): String = StringUtils.parseIdentifier(s)
    private fun displayFor(id: String, entries: List<String>): String =
        entries.firstOrNull { idOf(it) == id } ?: entries.firstOrNull() ?: id
    private fun fmt1(f: Float) = String.format(Locale.ENGLISH, "%.1f", f)
    private fun fmt2(f: Float) = String.format(Locale.ENGLISH, "%.2f", f)

    /** A plain per-game extra: absent or empty means the game follows the container. */
    private fun key(k: String, normalize: (String) -> String? = { it }) = GameBinding(
        keys = listOf(k),
        read = { e -> e.get(k)?.takeIf { it.isNotEmpty() }?.let(normalize) },
        write = { e, v -> e.put(k, v) },
    )

    /** A per-game on/off extra stored as "1" / "0". */
    private fun key10(k: String) = key(k) { if (it == "1") "1" else "0" }

    /** A per-game on/off extra with no container level: absent means [default]. */
    private fun builtIn10(k: String, default: Boolean) = GameBinding(
        keys = listOf(k),
        read = { e -> e.get(k)?.takeIf { it.isNotEmpty() }?.let { if (it == "1") "1" else "0" } },
        write = { e, v -> e.put(k, v) },
        inherit = Inherit.BUILT_IN, inherited = { onOff(default) },
    )

    /** A per-game on/off extra stored as "true" / "false" (the Vulkan renderer options). */
    private fun keyTF(k: String) = GameBinding(
        keys = listOf(k),
        read = { e -> e.get(k)?.takeIf { it.isNotEmpty() }?.let { if (it == "true") "1" else "0" } },
        write = { e, v -> e.put(k, if (v == "1") "true" else "false") },
    )

    private fun intKey(k: String) = key(k) { it.trim().toIntOrNull()?.toString() }

    private fun vmBool(get: (ContainerDetailViewModel) -> Boolean, set: (ContainerDetailViewModel, Boolean) -> Unit) =
        VmBinding(get = { _, vm -> onOff(get(vm)) }, set = { _, vm, v -> set(vm, v == "1") })

    private fun vmInt(get: (ContainerDetailViewModel) -> Int, set: (ContainerDetailViewModel, Int) -> Unit) =
        VmBinding(get = { _, vm -> get(vm).toString() }, set = { _, vm, v -> v.toIntOrNull()?.let { set(vm, it) } })

    private fun vmString(get: (ContainerDetailViewModel) -> String, set: (ContainerDetailViewModel, String) -> Unit) =
        VmBinding(get = { _, vm -> get(vm) }, set = { _, vm, v -> set(vm, v) })

    private fun seg(vararg o: Pair<String, String>) = Control.Segmented(o.map { Opt(it.first, it.second) })

    /** "1280x720 (16:9)" → "1280 × 720 (16:9)". */
    fun prettyResolution(entry: String): String = entry.replaceFirst(Regex("""^(\d+)x(\d+)"""), "$1 × $2")

    private val RESOLUTION = Regex("""^\s*(\d{2,5})\s*[xX×]\s*(\d{2,5})\s*$""")

    private fun validateResolution(text: String): String? {
        val m = RESOLUTION.find(text) ?: return T("Type it as width x height, e.g. 1600x900.")
        val w = m.groupValues[1].toInt()
        val h = m.groupValues[2].toInt()
        if (w % 2 != 0 || h % 2 != 0) return T("Both numbers must be even.")
        if (w < 320 || h < 200) return T("That is too small for a Windows desktop.")
        if (w > 7680 || h > 4320) return T("That is larger than 8K.")
        return null
    }

    private fun normalizeResolution(text: String): String =
        RESOLUTION.find(text)?.let { "${it.groupValues[1]}x${it.groupValues[2]}" } ?: text.trim()

    private val LOCALE = Regex("""^[a-z]{2,3}_[A-Z]{2}(\.[A-Za-z0-9-]+)?$""")

    private fun x11(env: SettingsEnv, values: (String) -> String) = !env.isLinuxEntry && !env.wayland(values)
    private fun vulkanRenderer(env: SettingsEnv, values: (String) -> String) = x11(env, values) && values("graphics.renderer") == "vulkan"
    private fun gameScope(env: SettingsEnv) = env.scope == EditorScope.GAME

    // ── Driver config ("k=v;k=v"): only its "version" key is edited here ────────────────────────

    private const val GDC = "graphicsDriverConfig"
    private const val ID_DRIVER_VERSION = "graphics.compositorDriver"

    private fun gdcVersion(config: String?): String =
        config?.takeIf { it.isNotEmpty() }?.let { runCatching { GraphicsDriverConfigDialog.getVersion(it) }.getOrNull() } ?: ""

    /** The game's driver config with [version] in place of its own, or null when that equals the container's. */
    private fun gdcWith(e: GameExtras, version: String): String? {
        val containerCfg = e.container.getGraphicsDriverConfig() ?: ""
        val next = withGraphicsDriverVersion(e.get(GDC)?.takeIf { it.isNotEmpty() } ?: containerCfg, version)
        return next.takeIf { it != containerCfg }
    }

    /** The Wayland compositor driver (the "version" key of the driver config), for a container and a game. */
    private val compositorVm = VmBinding(
        get = { _, vm -> gdcVersion(vm.graphicsDriverConfig) },
        set = { _, vm, v -> vm.onCompositorDriverPicked(v) },
    )
    private val compositorGame = GameBinding(
        keys = listOf(GDC),
        read = { e ->
            e.get(GDC)?.takeIf { it.isNotEmpty() }?.let { gdcVersion(it) }
                ?.takeIf { it != gdcVersion(e.container.getGraphicsDriverConfig()) }
        },
        write = { e, v -> e.put(GDC, gdcWith(e, v)) },
        reset = { e -> if (e.get(GDC) != null) e.put(GDC, gdcWith(e, gdcVersion(e.container.getGraphicsDriverConfig()))) },
    )

    private fun compositorControl(env: SettingsEnv, current: String): Control {
        val loaded = env.compositorChoices
        val choices = loaded ?: emptyList()
        val shown = choices.map { Opt(it, it) }
        // What is stored is always listed, so the field never reads blank ("" behaves as "System").
        return Control.Select(
            if (current in choices) shown
            else listOf(Opt(current, compositorDriverLabel(current, choices, loaded != null))) + shown,
        )
    }

    private fun compositorNote(env: SettingsEnv, version: String): String? {
        val loaded = env.compositorChoices ?: return T("正在检查哪些已安装的驱动程序可以显示游戏…")
        // The auto-pick notes belong to the container form being edited, not to the container a game follows.
        val vm = if (env.scope != EditorScope.GAME) env.vm else null
        return when {
            vm != null && vm.compositorDriverSearching -> T("Looking for an installed Turnip that can import the game's frames…")
            compositorDriverMaliSystem(version, loaded) -> MALI_SYSTEM_DRIVER_NOTE
            compositorDriverUnusable(version, loaded, true) ->
                if (env.isLinuxEntry) T("此驱动程序无法显示会话。\"System\" 或不再安装的驱动程序会使合成器停留在系统 Vulkan 上，从而显示黑屏。")
                // The XMB editor has a "Get more" link on this row; the plain Deck, Lab and Classic still list the download below.
                else if (!com.winlator.star.ui.theme.AppThemeState.deckXmbHome.value) T("Wayland 在此需要 Turnip 驱动程序。“System” 或缺失的驱动程序无法导入游戏的帧并显示黑屏。请在下方下载一个（下载 Turnip 驱动程序）。")
                else T("Wayland 在此设备需要 Turnip 驱动。「系统」或缺失驱动无法导入游戏画面并显示黑屏。使用「获取更多」下载一个。")
            vm != null && vm.compositorDriverAutoPicked == version ->
                if (vm.compositorDriverPickedFromDefaults) T("为您选择：您的新容器默认值中的驱动程序。")
                else T("为您选择：可导入游戏帧的最新已安装 Turnip。")
            else -> null
        }
    }

    // ── Win Components ("key=index,key=index"): the codec rows edit one key each ─────────────────

    private const val WINCOMPONENTS = "wincomponents"

    private fun wcIndex(components: String?, key: String): Int? {
        if (components.isNullOrEmpty()) return null
        for (entry in KeyValueSet(components)) if (entry[0] == key) return entry[1].toIntOrNull()
        return null
    }

    private fun wcWith(components: String, key: String, index: Int): String =
        components.split(",").joinToString(",") { if (it.substringBefore('=') == key) "$key=$index" else it }

    /** The game's components string with [key] set, or null when that equals the container's. */
    private fun wcGameWith(e: GameExtras, key: String, index: Int): String? {
        val containerWc = e.container.getWinComponents() ?: ""
        val next = wcWith(e.get(WINCOMPONENTS)?.takeIf { it.isNotEmpty() } ?: containerWc, key, index)
        return next.takeIf { it != containerWc }
    }

    // ── DX wrapper: leaving VEGAS drops its config file, as the classic editors do ─────────────

    private fun stripVegasConfig(config: String): String =
        if (config.split(",").none { it.startsWith("dxvkConfigFile=") && it.length > "dxvkConfigFile=".length }) config
        else config.split(",").filter { !it.startsWith("dxvkConfigFile=") }.joinToString(",")

    // ── Display server (X server / yserver) ────────────────────────────────────────────────────

    /** The display server row (Advanced): the Java X server or yserver, stored as BANNER_YSERVER in the env string. */
    const val ID_DISPLAY_SERVER = "display.server"
    private const val DS_X = "x"
    private const val DS_Y = "yserver"
    private val YSERVER_TOKEN = Regex("""(^|\s+)BANNER_YSERVER=\S*""")

    /** What [env] picks, as YServerComponent.requestedMode reads it (empty, 0 or off = X server); null when it does not say. */
    private fun displayServerOf(env: String?): String? {
        val m = YSERVER_TOKEN.findAll(env ?: return null).lastOrNull() ?: return null
        val v = m.value.trim().substringAfter('=').trim()
        return if (v.isEmpty() || v == "0" || v.equals("off", ignoreCase = true)) DS_X else DS_Y
    }

    /**
     * [env] with BANNER_YSERVER set for [server]: 1 for yserver, 0 for the X server when [keepOff] (a game turning its
     * container's yserver off), removed otherwise. The other variables stay exactly as they were.
     */
    private fun withDisplayServer(env: String?, server: String?, keepOff: Boolean): String {
        val base = YSERVER_TOKEN.replace(env.orEmpty(), "").trimStart()
        val add = when {
            server == DS_Y -> "BANNER_YSERVER=1"
            server == DS_X && keepOff -> "BANNER_YSERVER=0"
            else -> null
        }
        return listOfNotNull(base.ifEmpty { null }, add).joinToString(" ")
    }

    // ── Sync ────────────────────────────────────────────────────────────────────────────────────

    private fun syncReason(env: SettingsEnv, mode: String): String? {
        val caps = env.vm?.syncCaps
        val layer = env.vm?.selectedWineVersion.orEmpty()
        return when {
            mode == SyncMode.FSYNC -> T("Android blocks fsync")
            caps == null -> if (mode == SyncMode.NTSYNC) "Checking what $layer supports…" else null
            !caps.isAvailable(mode) -> "$mode isn't in $layer"
            else -> null
        }
    }

    /** The sync variables a game's own env string carried before the selector existed go when it is set. */
    private fun stripGameSyncVars(e: GameExtras) {
        val own = e.get("envVars") ?: return
        val stripped = SyncSupport.stripSyncVars(own)
        if (stripped != own) e.put("envVars", stripped.ifEmpty { null })
    }

    // ── Linux runtime ───────────────────────────────────────────────────────────────────────────

    /** A Linux entry switch: one "1" / "0" extra, LinuxTuning's default when absent. */
    private fun linuxToggle(
        id: String, extra: String, group: String, label: String, level: SettingLevel,
        hint: String? = null,
        visible: (SettingsEnv, (String) -> String) -> Boolean = { env, _ -> gameScope(env) },
        confirm: ((String) -> Confirm?)? = null,
        note: (SettingsEnv, (String) -> String) -> String? = { _, _ -> null },
    ) = SettingDef(
        id = id, category = "linux", group = group, label = label, hint = hint, tech = extra, level = level,
        control = { _, _ -> Control.Toggle },
        game = builtIn10(extra, LinuxTuning.defaultOn(extra)),
        worlds = LINUX_ONLY, visible = visible, confirm = confirm, note = note,
    )

    /** A Linux entry choice stored as one of [allowed] ("" = unset, which is removed). */
    private fun linuxChoice(extra: String, allowed: List<String>) = GameBinding(
        keys = listOf(extra),
        read = { e -> e.get(extra)?.takeIf { it.isNotEmpty() && it in allowed } },
        write = { e, v -> e.put(extra, v.ifEmpty { null }) },
        inherit = Inherit.BUILT_IN, inherited = { "" },
    )

    // ── The settings ────────────────────────────────────────────────────────────────────────────

    private fun buildDefs(): List<SettingDef> = listOf(

        // Game (per game only)
        SettingDef(
            id = "game.name", category = "game", group = T("Name"), level = SIMPLE,
            label = T("Name"), hint = T("显示在您的库中。重命名会保留封面和图标。"), tech = T("Name / Game Name"),
            control = { _, _ -> Control.TextField(T("游戏名称")) { t ->
                when {
                    t.isBlank() -> T("The name can't be empty.")
                    t.any { it in "\\/:*?\"<>|" } -> T("A name can't contain \\ / : * ? \" < > |")
                    else -> null
                }
            } },
            game = GameBinding(listOf(GameSource.NAME_KEY), read = { e -> e.get(GameSource.NAME_KEY) }, write = { e, v -> e.put(GameSource.NAME_KEY, v) }, inherit = Inherit.NONE),
            visible = { env, _ -> gameScope(env) },
            worlds = ALL_WORLDS,
        ),
        SettingDef(
            id = "game.arguments", category = "game", group = T("Name"), level = ADVANCED,
            label = T("启动选项"), hint = T("在程序名称后添加的额外参数，例如 -windowed -dx11。"), tech = T("执行参数"),
            control = { _, _ -> Control.TextField("-windowed") },
            game = GameBinding(listOf("execArgs"), read = { e -> e.get("execArgs") ?: "" }, write = { e, v -> e.put("execArgs", v.ifBlank { null }) }, inherit = Inherit.NONE),
            visible = { env, _ -> gameScope(env) },
        ),

        // Launch (per game only)
        SettingDef(
            id = "launch.method", category = "launch", group = T("启动方式"), level = SIMPLE,
            label = T("启动游戏使用"), tech = T("启动方式：SteamLite / 无头 Steam / Goldberg / Raw .exe"),
            help = T("SteamLite 运行真正的 Steam 客户端，因此成就、云存档和在线游玩均可工作。无头 Steam 在应用自身的 Steam 会话上运行游戏，因此好友和聊天保持在线（实验性）。Goldberg 在不登录的情况下假装是 Steam。Raw .exe 启动游戏自身的程序。"),
            control = { _, _ -> seg("RealSteam" to "SteamLite", "AppSteam" to T("Headless Steam"), "Goldberg" to T("Goldberg (offline)"), "Raw" to T("Raw .exe")) },
            game = GameBinding(
                listOf("launchMode"),
                read = { e -> e.get("launchMode")?.takeIf { it in LAUNCH_MODES } },
                write = { e, v -> e.put("launchMode", v) },
                inherit = Inherit.BUILT_IN, inherited = { "RealSteam" },
            ),
            visible = { env, _ -> gameScope(env) && env.isSteam },
        ),
        SettingDef(
            id = "launch.rememberChoice", category = "launch", group = T("启动方式"), level = SIMPLE,
            label = T("跳过启动菜单"), hint = T("直接使用已保存的选择启动。关闭以再次显示启动菜单。"), tech = T("记住我的选择"),
            control = { _, _ -> Control.Toggle },
            game = builtIn10("launchModeRemembered", false),
            visible = { env, _ -> gameScope(env) },
        ),
        SettingDef(
            id = "launch.controllerPassthrough", category = "launch", group = T("Steam 选项"), level = ADVANCED,
            label = T("让游戏处理控制器"), hint = T("将手柄直接交给游戏，而不是 Steam 输入。适用于经典游戏（半条命 2、CS:S）。会关闭 Steam 输入。"), tech = T("控制器直通"),
            control = { _, _ -> Control.Toggle },
            // The opposite of Steam Input, so turning one on turns the other off, as the launch menu does.
            game = GameBinding(
                listOf("controllerPassthrough"),
                read = { e -> e.get("controllerPassthrough")?.takeIf { it.isNotEmpty() }?.let { if (it == "1") "1" else "0" } },
                write = { e, v ->
                    e.put("controllerPassthrough", v)
                    if (v == "1" && e.get("steamInput") == "1") e.put("steamInput", "0")
                },
                inherit = Inherit.BUILT_IN, inherited = { "0" },
            ),
            visible = { env, values -> gameScope(env) && env.isSteam && values("launch.method") == "RealSteam" },
        ),
        SettingDef(
            id = "launch.steamInput", category = "launch", group = T("Steam 选项"), level = ADVANCED,
            label = T("Steam 输入"), hint = T("适用于仅通过 Steam 看到手柄的游戏（无人深空）。对于自己读取手柄的游戏，请保持关闭。会关闭直通。"), tech = "steamInput",
            help = T("有些游戏从不自己读取控制器：它们向 Steam 请求。在 SteamLite 中没有人应答，因此那些游戏看不到手柄。开启：SteamLite 代理会为此游戏打开 Steam 的控制器层并使用合适的布局。对于已经自己读取手柄的游戏，请保持关闭，否则它们会收到每次按键两次。与控制器直通相反，因此只能开启一个。仅限 SteamLite。"),
            control = { _, _ -> Control.Toggle },
            game = GameBinding(
                listOf("steamInput"),
                read = { e -> e.get("steamInput")?.takeIf { it.isNotEmpty() }?.let { if (it == "1") "1" else "0" } },
                write = { e, v ->
                    e.put("steamInput", v)
                    if (v == "1" && e.get("controllerPassthrough") == "1") e.put("controllerPassthrough", "0")
                },
                inherit = Inherit.BUILT_IN, inherited = { "0" },
            ),
            visible = { env, values -> gameScope(env) && env.isSteam && values("launch.method") == "RealSteam" },
        ),
        SettingDef(
            id = "launch.vacSecure", category = "launch", group = T("Steam 选项"), level = ADVANCED,
            label = T("反作弊（VAC）安全启动"), hint = T("加入受 VAC 保护的服务器所需。检测会遵循 Steam 的应用信息。"), tech = T("需要安全（VAC）启动"),
            control = { _, _ -> seg("auto" to T("Detect"), "1" to T("Always"), "0" to T("从未")) },
            game = GameBinding(
                listOf("steamVacLaunch"),
                read = { e -> e.get("steamVacLaunch")?.trim()?.takeIf { it == "1" || it == "0" } },
                write = { e, v -> e.put("steamVacLaunch", if (v == "auto") null else v) },
                inherit = Inherit.BUILT_IN, inherited = { "auto" },
            ),
            visible = { env, values -> gameScope(env) && env.isSteam && values("launch.method") == "RealSteam" },
        ),
        SettingDef(
            id = "launch.epicEos", category = "launch", group = T("Epic Games"), level = SIMPLE,
            label = T("Epic 在线服务（EOS）登录"), hint = T("使用您的 Epic 账户登录 EOS 游戏。"), tech = "epicEos",
            control = { _, _ -> Control.Toggle },
            game = GameBinding(
                listOf("epicEos"),
                read = { e -> e.get("epicEos")?.takeIf { it.isNotEmpty() }?.let { if (it == "0") "0" else "1" } },
                write = { e, v -> e.put("epicEos", v) },
                inherit = Inherit.BUILT_IN, inherited = { "1" },
            ),
            visible = { env, _ -> gameScope(env) && env.isEpic },
        ),
        SettingDef(
            id = "launch.epicOvtForce", category = "launch", group = T("Epic Games"), level = ADVANCED,
            label = T("强制Denuvo所有权令牌"), hint = T("适用于应用无法自行检测的 Denuvo 游戏。"), tech = "epicOvtForce",
            control = { _, _ -> Control.Toggle },
            game = builtIn10("epicOvtForce", false),
            visible = { env, _ -> gameScope(env) && env.isEpic },
            disabled = { _, values -> if (values("launch.epicEos") == "1") null else T("Needs EOS sign-in on.") },
        ),
        SettingDef(
            id = "launch.epicOffline", category = "launch", group = T("Epic Games"), level = ADVANCED,
            label = T("离线启动"), hint = T("跳过此游戏的 Epic 登录（无在线功能）。"), tech = "epicOffline",
            control = { _, _ -> Control.Toggle },
            game = builtIn10("epicOffline", false),
            visible = { env, _ -> gameScope(env) && env.isEpic },
            disabled = { _, values -> if (values("launch.epicEos") == "1") null else T("Needs EOS sign-in on.") },
        ),
        SettingDef(
            id = "launch.epicOverlay", category = "launch", group = T("Epic Games"), level = EXPERT,
            label = T("Epic 好友覆盖层"), hint = T("实验性：在游戏中按 Shift+F3 显示 Epic 的覆盖层。需要容器允许完整服务。"), tech = "epicOverlay",
            control = { _, _ -> Control.Toggle },
            game = builtIn10("epicOverlay", false),
            visible = { env, _ -> gameScope(env) && env.isEpic && FeatureFlags.EPIC_OVERLAY_ENABLED },
        ),
        SettingDef(
            id = "launch.steamBigPicture", category = "launch", group = T("Windows Steam"), level = SIMPLE,
            label = T("Start in Big Picture"), hint = T("以大屏幕模式打开 Steam。"), tech = "steamBigPicture",
            control = { _, _ -> Control.Toggle },
            game = builtIn10(com.winlator.star.store.WindowsSteamClient.EXTRA_BIG_PICTURE, false),
            visible = { env, _ -> gameScope(env) && env.isWindowsSteam },
        ),
        SettingDef(
            id = "launch.xboxNexusRuntime", category = "launch", group = "Xbox", level = SIMPLE,
            label = T("使用应用的 Xbox 运行时"), hint = T("此游戏附带 Microsoft 自己的 Xbox 运行时，它需要 Windows 的 Gaming Services。开启：改用应用的运行时。仅在尝试游戏自带的运行时时关闭。"), tech = "xboxNexusRuntime",
            help = T("Xbox 游戏通过 xgameruntime.dll 与 Xbox 服务通信。此游戏在其文件夹中带了 Microsoft 的副本，Wine 会在应用的副本（Xodus Nexus）之前加载它。Microsoft 的副本需要 Windows 的 Gaming Services，而 Wine 没有，因此游戏通常会在启动时停止（Cyberpunk 2077 就是如此）。开启：每次启动使用应用的副本；游戏的文件不会被更改。关闭：使用游戏自己的副本。"),
            control = { _, _ -> Control.Toggle },
            game = builtIn10("xboxNexusRuntime", true),
            visible = { env, _ -> gameScope(env) && env.xboxShipsRuntime },
        ),

        // Display
        SettingDef(
            id = "display.resolution", category = "display", group = T("屏幕"), level = SIMPLE,
            label = T("游戏分辨率"), hint = T("游戏所看到的屏幕尺寸。更低 = 更快。"), tech = T("屏幕尺寸"),
            help = T("游戏以此尺寸渲染，然后画面缩放到您的屏幕。较低分辨率更快、更凉；较高分辨率更清晰。"),
            control = { env, _ ->
                Control.Select(
                    options = (env.vm?.screenSizeEntries ?: emptyList())
                        .filterNot { it.equals("custom", ignoreCase = true) }
                        .map { Opt(idOf(it), prettyResolution(it)) },
                    customLabel = T("自定义…"),
                    customHint = T("Width x height, both even, e.g. 1600x900"),
                    validateCustom = { validateResolution(it) },
                    normalizeCustom = { normalizeResolution(it) },
                )
            },
            vm = VmBinding(
                get = { _, vm ->
                    if (vm.selectedScreenSize.equals("custom", ignoreCase = true)) "${vm.customWidth.trim()}x${vm.customHeight.trim()}"
                    else idOf(vm.selectedScreenSize)
                },
                set = { _, vm, v ->
                    if (vm.screenSizeEntries.any { !it.equals("custom", ignoreCase = true) && idOf(it) == v }) {
                        vm.selectedScreenSize = v
                    } else {
                        vm.selectedScreenSize = "custom"
                        vm.customWidth = v.substringBefore('x')
                        vm.customHeight = v.substringAfter('x', "")
                    }
                },
            ),
            game = key("screenSize"),
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "display.fullscreenMode", category = "display", group = T("屏幕"), level = SIMPLE,
            label = T("填满屏幕"), tech = T("全屏模式"),
            control = { _, _ ->
                Control.Chips(listOf(
                    Opt("${Container.FULLSCREEN_OFF}", T("窗口化")),
                    Opt("${Container.FULLSCREEN_FIT}", T("适应（黑边）")),
                    Opt("${Container.FULLSCREEN_STRETCH}", T("拉伸")),
                    Opt("${Container.FULLSCREEN_FILL}", T("填充（裁剪）")),
                    Opt("${Container.FULLSCREEN_INTEGER}", T("像素完美")),
                ))
            },
            vm = vmInt({ it.fullscreenMode }, { vm, v -> vm.fullscreenMode = v }),
            // The launch path still honours the legacy per-game "fullscreenStretched" when no mode is stored, so it is part of this setting.
            game = GameBinding(
                keys = listOf("fullscreenMode", "fullscreenStretched"),
                read = { e ->
                    val mode = e.get("fullscreenMode")?.trim()?.takeIf { it.isNotEmpty() }
                    val legacy = e.get("fullscreenStretched")?.takeIf { it.isNotEmpty() }
                    mode?.toIntOrNull()?.toString()
                        ?: legacy?.let { if (it == "1") "${Container.FULLSCREEN_STRETCH}" else "${Container.FULLSCREEN_OFF}" }
                },
                write = { e, v -> e.put("fullscreenMode", v); e.put("fullscreenStretched", null) },
            ),
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "display.alignment", category = "display", group = T("屏幕"), level = ADVANCED,
            label = T("画面位置"), hint = T("在折叠屏上有用：将游戏保持在一半屏幕上。"), tech = T("屏幕对齐"),
            control = { _, _ -> seg("${Container.ALIGN_CENTER}" to T("居中"), "${Container.ALIGN_TOP}" to T("顶部"), "${Container.ALIGN_BOTTOM}" to T("底部")) },
            vm = vmInt({ it.screenAlignment }, { vm, v -> vm.screenAlignment = v }),
            game = intKey("screenAlignment"),
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = SettingsEnv.ID_BACKEND, category = "display", group = T("屏幕"), level = ADVANCED,
            label = T("显示系统"), hint = T("Wayland 需要为其构建的 Proton 层。"), tech = T("显示后端"),
            help = T("X11 是经典路径，在任何地方都能工作。Wayland 在为其构建的层上更直接地将游戏帧交给屏幕。"),
            control = { env, _ ->
                Control.Segmented(listOf(
                    Opt(Container.DISPLAY_BACKEND_X11, "X11"),
                    Opt(Container.DISPLAY_BACKEND_WAYLAND, "Wayland",
                        disabledReason = if (env.waylandCapable) null else T("此容器的 Wine 层无法驱动 Wayland。")),
                ))
            },
            vm = vmString({ it.displayBackend }, { vm, v -> vm.onDisplayBackendChanged(v) }),
            game = key("displayBackend") { if (it == Container.DISPLAY_BACKEND_WAYLAND) it else Container.DISPLAY_BACKEND_X11 },
        ),
        SettingDef(
            id = ID_DISPLAY_SERVER, category = "display", group = T("屏幕"), level = ADVANCED,
            label = T("显示服务器"), hint = T("yserver 是新的 X 服务器，仍在测试中。"), tech = "BANNER_YSERVER",
            control = { env, _ ->
                Control.Segmented(listOf(
                    Opt(DS_X, T("X 服务器")),
                    Opt(DS_Y, "yserver", disabledReason = if (YServerComponent.binary(env.context) != null) null else T("yserver isn't in this build.")),
                ))
            },
            vm = VmBinding(
                get = { _, vm -> displayServerOf(vm.envVarsStr) ?: DS_X },
                // Unchanged picks leave the env alone (it may say BANNER_YSERVER=record).
                set = { _, vm, v -> if ((displayServerOf(vm.envVarsStr) ?: DS_X) != v) vm.envVarsStr = withDisplayServer(vm.envVarsStr, v, keepOff = false) },
            ),
            // A game's own env wins at launch; picking its container's choice follows the container again.
            game = GameBinding(
                keys = listOf("envVars"),
                read = { e -> displayServerOf(e.get("envVars")) },
                write = { e, v ->
                    val inherited = displayServerOf(e.env.vm?.envVarsStr) ?: DS_X
                    e.put("envVars", withDisplayServer(e.get("envVars"), v.takeIf { it != inherited }, keepOff = true).ifEmpty { null })
                },
                reset = { e -> e.get("envVars")?.let { own -> e.put("envVars", withDisplayServer(own, null, keepOff = false).ifEmpty { null }) } },
            ),
        ),
        SettingDef(
            id = "display.renderScale", category = "display", group = T("屏幕"), level = ADVANCED,
            label = T("超采样"), hint = T("以更大尺寸渲染，然后缩小以获得更清晰的图像。会消耗性能。"), tech = T("渲染缩放（超采样）"),
            control = { _, _ -> seg("1.0" to T("Off"), "1.25" to "1.25×", "1.5" to "1.5×", "2.0" to "2×") },
            vm = vmString({ it.renderScale }, { vm, v -> vm.renderScale = v }),
            game = key("renderScale") { v -> v.toFloatOrNull()?.let { f -> listOf("1.0", "1.25", "1.5", "2.0").firstOrNull { it.toFloat() == f } } },
            visible = { env, values -> x11(env, values) },
        ),
        SettingDef(
            id = "display.refreshInGame", category = "display", group = T("刷新率"), level = ADVANCED,
            label = T("游戏可选择的刷新率"), hint = T("告诉游戏屏幕能做什么。需要带 xrandr 的 Wine 构建。"), tech = T("游戏内刷新率"),
            control = { env, _ ->
                Control.Select(listOf(Opt("-1", T("锁定为 60 Hz")), Opt("0", T("任意（无限制）"))) +
                    env.panelRates.filter { it > 60 }.map { Opt("$it", "$it Hz") })
            },
            vm = VmBinding(
                get = { _, vm -> if (!vm.unlockGameRefreshRate) "-1" else vm.maxGameRefreshRate.toString() },
                set = { _, vm, v ->
                    if (v == "-1") { vm.unlockGameRefreshRate = false; vm.maxGameRefreshRate = 0 }
                    else { vm.unlockGameRefreshRate = true; vm.maxGameRefreshRate = v.toIntOrNull() ?: 0 }
                },
            ),
            game = GameBinding(
                keys = listOf("unlockGameRefreshRate", "maxGameRefreshRate"),
                read = { e ->
                    val unlock = e.get("unlockGameRefreshRate")?.takeIf { it.isNotEmpty() }
                    val max = e.get("maxGameRefreshRate")?.takeIf { it.isNotEmpty() }
                    when {
                        unlock == null && max == null -> null
                        unlock == "0" -> "-1"
                        else -> (max?.toIntOrNull() ?: 0).toString()
                    }
                },
                write = { e, v ->
                    if (v == "-1") { e.put("unlockGameRefreshRate", "0"); e.put("maxGameRefreshRate", "0") }
                    else { e.put("unlockGameRefreshRate", "1"); e.put("maxGameRefreshRate", v) }
                },
            ),
            visible = { env, _ -> env.panelRates.isNotEmpty() },
        ),
        SettingDef(
            id = "display.refreshAuto", category = "display", group = T("刷新率"), level = ADVANCED,
            label = T("将屏幕刷新率与游戏的 FPS 匹配"), hint = T("在支持可变刷新的屏幕上实现更流畅的运动和更少的电池消耗。"), tech = T("自动（匹配帧率）"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.matchRefreshRate }, { vm, v -> vm.matchRefreshRate = v }),
            game = key10("matchRefreshRate"),
            disabled = { env, _ -> if (env.vrrCapable) null else T("此屏幕无法更改其刷新率。") },
        ),
        SettingDef(
            id = "display.refreshManual", category = "display", group = T("刷新率"), level = ADVANCED,
            label = T("固定屏幕刷新率"), hint = T("在匹配关闭时使用。"), tech = T("手动刷新率"),
            control = { env, _ -> Control.Chips(listOf(Opt("0", T("Off"))) + env.panelRates.map { Opt("$it", "$it Hz") }) },
            vm = vmInt({ it.manualRefreshRate }, { vm, v -> vm.manualRefreshRate = v }),
            game = intKey("manualRefreshRate"),
            visible = { env, values -> env.vrrCapable && env.panelRates.isNotEmpty() && values("display.refreshAuto") == "0" },
        ),
        SettingDef(
            id = "display.hdrOutput", category = "display", group = "HDR", level = ADVANCED,
            label = T("HDR 输出"), hint = T("向屏幕发送真正的 HDR10。"), tech = T("HDR 输出 (HDR10)"),
            help = WaylandHdr.HELP_TEXT,
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.waylandHdr }, { vm, v -> vm.waylandHdr = v }),
            game = key10(WaylandHdr.EXTRA),
            visible = { env, values -> env.wayland(values) },
            disabled = { env, _ -> runCatching { WaylandHdr.unavailableReason(env.context) }.getOrNull() },
            worlds = WINE_LINUX,
        ),
        // A game's TV and external display (the classic TV tab).
        *TvRows.defs().toTypedArray(),

        // Graphics
        SettingDef(
            id = "graphics.dxWrapper", category = "graphics", group = T("DirectX 转换"), level = SIMPLE,
            label = T("DirectX 转换"), hint = T("Windows DirectX 调用如何为您的 GPU 转换为 Vulkan。"), tech = T("DX Wrapper"),
            help = T("DXVK + VKD3D 适合大多数 DirectX 9–12 游戏。VEGAS 是经过调校的 DXVK 分支。WineD3D 是适用于非常老游戏的缓慢但广泛的回退方案。"),
            control = { env, _ ->
                val labels = mapOf("dxvk+vkd3d" to T("DXVK + VKD3D"), "vegas+vkd3d" to T("VEGAS + VKD3D"), "wined3d" to T("WineD3D"))
                val ids = (env.vm?.dxWrapperEntries ?: emptyList()).map { idOf(it) }
                val order = listOf("dxvk+vkd3d", "vegas+vkd3d", "wined3d")
                Control.Segmented(ids.sortedBy { order.indexOf(it).let { i -> if (i < 0) 99 else i } }.map { Opt(it, labels[it] ?: it) })
            },
            // Leaving VEGAS drops the VEGAS config file from the wrapper config, as the classic editors do.
            vm = VmBinding(
                get = { _, vm -> idOf(vm.selectedDXWrapper) },
                set = { _, vm, v ->
                    val wasVegas = idOf(vm.selectedDXWrapper).contains("vegas")
                    vm.selectedDXWrapper = displayFor(v, vm.dxWrapperEntries)
                    if (wasVegas && !v.contains("vegas")) vm.dxWrapperConfig = stripVegasConfig(vm.dxWrapperConfig)
                },
            ),
            game = GameBinding(
                keys = listOf("dxwrapper"),
                read = { e -> e.get("dxwrapper")?.takeIf { it.isNotEmpty() } },
                write = { e, v ->
                    val wasVegas = (e.get("dxwrapper")?.takeIf { it.isNotEmpty() } ?: e.container.getDXWrapper() ?: "").contains("vegas")
                    e.put("dxwrapper", v)
                    if (wasVegas && !v.contains("vegas")) {
                        val config = e.get("dxwrapperConfig")?.takeIf { it.isNotEmpty() } ?: e.container.getDXWrapperConfig() ?: ""
                        val stripped = stripVegasConfig(config)
                        if (stripped != config) e.put("dxwrapperConfig", stripped)
                    }
                },
            ),
        ),
        // Every choice of the DX Wrapper Config dialogs (DXVK, VEGAS, WineD3D), for the wrapper picked above.
        *GraphicsRows.dxDefs().toTypedArray(),
        SettingDef(
            id = "graphics.unrealHdr", category = "graphics", group = T("虚幻引擎"), level = ADVANCED,
            label = T("虚幻引擎 HDR 修复"), hint = T("DirectX 11 修复会假装是 NVIDIA GPU。"), tech = T("虚幻引擎 HDR"),
            help = UnrealHdr.HELP_SHORT,
            control = { _, _ -> seg(UnrealHdr.OFF to T("Off"), UnrealHdr.DX12 to T("DirectX 12 fix"), UnrealHdr.DX11 to T("DirectX 11 (NVAPI)")) },
            vm = vmString({ it.unrealHdr }, { vm, v -> vm.unrealHdr = v }),
            game = key(UnrealHdr.EXTRA) { UnrealHdr.normalize(it).ifEmpty { null } },
        ),
        SettingDef(
            id = "graphics.driver", category = "graphics", group = T("虚幻引擎 HDR 修复"), level = SIMPLE,
            label = T("显卡驱动"), hint = T("游戏在 X11 上使用的 Vulkan 包装器。导入的包装器也会出现在此处。"), tech = T("图形驱动程序"),
            control = { env, _ -> Control.Select((env.vm?.graphicsDriverEntries ?: emptyList()).map { Opt(idOf(it), it) }) },
            vm = VmBinding(get = { _, vm -> idOf(vm.selectedGraphicsDriver) }, set = { _, vm, v -> vm.selectedGraphicsDriver = displayFor(v, vm.graphicsDriverEntries) }),
            game = key("graphicsDriver"),
            visible = { env, values -> x11(env, values) },
            getMore = "wrappers",
        ),
        SettingDef(
            id = ID_DRIVER_VERSION, category = "graphics", group = T("虚幻引擎 HDR 修复"), level = SIMPLE,
            label = T("显卡驱动"), hint = T("Wayland 合成器用于将游戏帧放到屏幕上的已安装 Turnip。"), tech = T("合成器驱动程序（graphicsDriverConfig 版本）"),
            help = T("在 Wayland 上，游戏通过嵌入式合成器渲染，该合成器需要已安装的 Turnip 来导入其帧。\"System\" 仅在 Mali GPU 上提供；在其他地方会显示黑屏。游戏自身渲染所使用的驱动程序是下面的 Wayland 游戏驱动程序。"),
            control = { env, values -> compositorControl(env, values(ID_DRIVER_VERSION)) },
            vm = compositorVm,
            game = compositorGame,
            visible = { env, values -> !env.isLinuxEntry && env.wayland(values) },
            note = { env, values -> compositorNote(env, values(ID_DRIVER_VERSION)) },
            getMore = "turnip",
        ),
        // The Wayland driver gear: the Wayland game driver and the Mali (PanVK) switches.
        *GraphicsRows.waylandDefs().toTypedArray(),
        // The X11 driver configuration: driver version, Vulkan, extensions, GPU name, BCn, Turnip (GPU name,
        // memory, present mode and the OneUI fix also on Wayland).
        *GraphicsRows.driverDefs().toTypedArray(),
        // The whole driver config, for the switches an imported wrapper carries (DeckWrapperDetectedSection).
        *WrapperDetectedRows.defs().toTypedArray(),
        SettingDef(
            id = "graphics.fastOpenGL", category = "graphics", group = "OpenGL", level = ADVANCED,
            label = T("快速 OpenGL"), hint = FastOpenGL.HINT, tech = FastOpenGL.EXTRA,
            help = T("OpenGL 游戏（以及通过 WineD3D 的 DirectDraw / 较旧 Direct3D）在捆绑的 Mesa 和 Zink 上运行，并由 Vulkan 呈现，而不是每帧缓慢复制。在 X11 上需要 Turnip 图形驱动程序。在 Wayland 下始终开启。"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.fastOpenGL }, { vm, v -> vm.fastOpenGL = v }),
            game = key10(FastOpenGL.EXTRA),
            // Locked on with Wayland and greyed on X11 when the driver or the build can't run it; the stored choice is kept.
            disabled = { env, values ->
                if (env.wayland(values)) FastOpenGL.WAYLAND_ALWAYS_ON else env.fastOpenGLReason(values(ID_DRIVER_VERSION))
            },
        ),
        SettingDef(
            id = "graphics.glFpsHud", category = "graphics", group = "OpenGL", level = ADVANCED,
            label = FastOpenGL.FPS_HUD.title, hint = FastOpenGL.FPS_HUD.hint, tech = FastOpenGL.FPS_HUD.extra,
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.glFpsHud }, { vm, v -> vm.glFpsHud = v }),
            game = key10(FastOpenGL.FPS_HUD.extra),
        ),
        SettingDef(
            id = "graphics.glVsyncOff", category = "graphics", group = "OpenGL", level = ADVANCED,
            label = FastOpenGL.VSYNC_OFF.title, hint = FastOpenGL.VSYNC_OFF.hint, tech = FastOpenGL.VSYNC_OFF.extra,
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.glVsyncOff }, { vm, v -> vm.glVsyncOff = v }),
            game = key10(FastOpenGL.VSYNC_OFF.extra),
        ),
        SettingDef(
            id = "graphics.renderer", category = "graphics", group = T("画面如何到达屏幕"), level = ADVANCED,
            label = T("屏幕渲染器"), hint = T("将 Windows 桌面绘制到 Android 上。Wayland 始终使用 Vulkan。"), tech = T("Renderer"),
            control = { _, _ -> seg("opengl" to "OpenGL", "vulkan" to "Vulkan", "surfaceflinger" to T("SurfaceFlinger")) },
            vm = VmBinding(get = { _, vm -> idOf(vm.selectedRenderer) }, set = { _, vm, v -> vm.selectedRenderer = displayFor(v, RENDERERS) }),
            game = key("renderer"),
            visible = { env, values -> x11(env, values) },
            confirm = { v ->
                if (v == "surfaceflinger") Confirm(
                    T("SurfaceFlinger渲染器 — 实验性"),
                    T("SurfaceFlinger 将游戏帧直接交给 Android 的合成器。它可能更快，但在某些设备上会显示黑屏或颜色错误。如果发生这种情况，请切换回 Vulkan 或 OpenGL。"),
                    T("仍要使用"),
                ) else null
            },
        ),
        SettingDef(
            id = "graphics.sfColours", category = "graphics", group = T("画面如何到达屏幕"), level = ADVANCED,
            label = T("修复颜色通道互换"), hint = T("将红蓝交换回来（BGRA 到 RGBA）。"), tech = T("校正 SurfaceFlinger 颜色"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.rendererSfCompatMode }, { vm, v -> vm.rendererSfCompatMode = v }),
            game = key10("sfCompatMode"),
            visible = { env, values -> x11(env, values) && values("graphics.renderer") == "surfaceflinger" },
        ),
        SettingDef(
            id = "graphics.nativeRenderer", category = "graphics", group = T("画面如何到达屏幕"), level = ADVANCED,
            label = T("直接到屏幕（原生渲染器）"), hint = T("跳过合成器以降低延迟。会关闭屏幕效果。"), tech = T("原生渲染器"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.rendererNative }, { vm, v -> vm.rendererNative = v }),
            game = keyTF("native"),
            visible = { env, values -> vulkanRenderer(env, values) },
        ),
        SettingDef(
            id = "graphics.nativeBackend", category = "graphics", group = T("画面如何到达屏幕"), level = EXPERT,
            label = T("直接到屏幕方法"), tech = T("原生后端"),
            control = { _, _ -> seg("auto" to T("Auto"), "asr" to T("SurfaceFlinger"), "flip" to T("Vulkan scan-out")) },
            vm = vmString({ it.rendererNativeBackend }, { vm, v -> vm.rendererNativeBackend = v }),
            game = key("nativeBackend"),
            visible = { env, values -> vulkanRenderer(env, values) && values("graphics.nativeRenderer") == "1" },
        ),
        SettingDef(
            id = "graphics.presentMode", category = "graphics", group = T("画面如何到达屏幕"), level = ADVANCED,
            label = T("帧呈现"), hint = T("在帧生成运行时锁定为快速垂直同步。"), tech = T("呈现模式"),
            control = { _, _ -> seg("fifo" to T("V-Sync"), "mailbox" to T("Fast V-Sync"), "immediate" to T("Off")) },
            vm = vmString({ it.rendererPresentMode }, { vm, v -> vm.rendererPresentMode = v }),
            game = key("presentMode"),
            visible = { env, values -> vulkanRenderer(env, values) },
            disabled = { _, values -> if (values("graphics.nativeRenderer") == "1") T("Ignored while the native renderer is on.") else null },
        ),
        SettingDef(
            id = "graphics.swapColours", category = "graphics", group = T("画面如何到达屏幕"), level = EXPERT,
            label = T("颜色字节顺序"), tech = T("颜色"),
            control = { _, _ -> seg("0" to T("BGRA (normal)"), "1" to T("RGBA (swap red/blue)")) },
            vm = vmBool({ it.rendererSwapRB }, { vm, v -> vm.rendererSwapRB = v }),
            game = keyTF("swapRB"),
            visible = { env, values -> vulkanRenderer(env, values) },
        ),
        SettingDef(
            id = "graphics.rendererDriver", category = "graphics", group = T("画面如何到达屏幕"), level = EXPERT,
            label = T("屏幕渲染器的驱动程序"), hint = T("不是游戏的驱动程序，只是绘制桌面的那个。"), tech = T("渲染器驱动程序"),
            control = { env, _ -> Control.Select(listOf(Opt("system", T("System driver"))) + env.rendererDrivers.map { Opt(it, it) }) },
            vm = vmString({ it.rendererDriverId }, { vm, v -> vm.rendererDriverId = v }),
            game = key("rendererDriverId"),
            visible = { env, values -> vulkanRenderer(env, values) },
        ),

        // Performance
        SettingDef(
            id = "performance.fpsLimiter", category = "performance", group = T("帧率"), level = SIMPLE,
            label = T("帧率限制器"), hint = T("更稳定的帧节奏和更凉的设备。在游戏中从侧边菜单设置限制。"), tech = T("FPS限制器"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.fpsLimiterEnabled }, { vm, v -> vm.fpsLimiterEnabled = v }),
            game = key10("fpsLimiterEnabled"),
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "performance.frameGen", category = "performance", group = T("帧率"), level = SIMPLE,
            label = T("帧率"), hint = T("插入额外帧以实现更流畅的运动。"), tech = T("插帧生成"),
            help = T("内置 (Win-FG) 在应用内部进行插值。Lossless Scaling 使用您自己的 Lossless.dll。两者都需要 Vulkan 屏幕渲染器、Wayland 或 Linux 条目。倍数在游戏中设置。"),
            control = { env, _ ->
                Control.Segmented(listOf(
                    Opt("off", T("Off")),
                    Opt("bionic", T("Built-in (Win-FG)")),
                    Opt("lsfg-native", T("Lossless Scaling"),
                        disabledReason = if (env.lsfgDllAvailable) null else T("请先在 设置 › 性能 中导入 Lossless.dll。")),
                    Opt("wfg", T("WFG")),
                ))
            },
            vm = vmString({ if (it.frameGenEngine == "lsfg") "lsfg-native" else it.frameGenEngine }, { vm, v -> vm.frameGenEngine = v }),
            game = key("frameGenEngine") { if (it == "lsfg") "lsfg-native" else it },
            disabled = { env, values ->
                if (env.wayland(values) || values("graphics.renderer") == "vulkan") null
                else T("需要 Vulkan 屏幕渲染器（图形）或 Wayland。")
            },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "performance.frameGenModel", category = "performance", group = T("帧率"), level = ADVANCED,
            label = T("运动模型"), tech = T("插值模型"),
            control = { _, _ -> seg("3" to T("光流"), "4" to T("双向")) },
            vm = vmInt({ if (it.frameGenModel == 4) 4 else 3 }, { vm, v -> vm.frameGenModel = v }),
            game = key("frameGenModel") { if (it.trim() == "4") "4" else "3" },
            visible = { _, values -> values("performance.frameGen") == "bionic" },
        ),
        SettingDef(
            id = "performance.fgCapture", category = "performance", group = T("帧率"), level = EXPERT,
            label = T("采集分辨率"), hint = T("帧生成链运行的高度；宽度跟随您的屏幕。更低则开销小得多，画面更柔和。"), tech = "fgCaptureResolution",
            control = { env, _ ->
                val heights = (env.vm?.screenSizeEntries ?: emptyList())
                    .filterNot { it.equals("custom", ignoreCase = true) }
                    .mapNotNull { entry -> Container.fgCaptureHeightFor(idOf(entry)).takeIf { it > 0 }?.let { Opt("$it", prettyResolution(entry)) } }
                    .distinctBy { it.value }
                Control.Select(
                    options = listOf(Opt(Container.FG_CAPTURE_PANEL, T("Panel (default)")), Opt(Container.FG_CAPTURE_GAME, T("游戏分辨率"))) + heights,
                    customLabel = T("Custom height…"),
                    customHint = T("A height in pixels, e.g. 540"),
                    validateCustom = { t -> if ((t.trim().toIntOrNull() ?: 0) > 0) null else T("Type a height in pixels.") },
                    normalizeCustom = { it.trim() },
                )
            },
            vm = VmBinding(
                get = { _, vm -> vm.resolvedFgCaptureResolution() },
                set = { _, vm, v ->
                    if (v == Container.FG_CAPTURE_PANEL || v == Container.FG_CAPTURE_GAME) {
                        vm.fgCaptureSelection = v
                    } else v.toIntOrNull()?.takeIf { it > 0 }?.let { h ->
                        val match = vm.screenSizeEntries.firstOrNull {
                            !it.equals("custom", ignoreCase = true) && Container.fgCaptureHeightFor(idOf(it)) == h
                        }
                        if (match != null) {
                            vm.fgCaptureSelection = match
                        } else {
                            vm.fgCaptureSelection = vm.screenSizeEntries.firstOrNull { it.equals("custom", ignoreCase = true) } ?: "Custom"
                            vm.fgCaptureCustomHeight = h.toString()
                        }
                    }
                },
            ),
            showInGame = false,
            visible = { _, values -> FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED && values("performance.frameGen") == "lsfg-native" },
        ),
        SettingDef(
            id = "performance.lsfgVk11", category = "performance", group = T("帧率"), level = EXPERT,
            label = T("在 Vulkan 1.1 驱动程序上使用 LSFG（兼容）"), hint = T("允许 LSFG 原生在仅报告 Vulkan 1.1 但具有所需扩展的驱动程序上运行。也适用于每个游戏的覆盖设置。"), tech = "lsfgVk11Compat",
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.lsfgVk11Compat }, { vm, v -> vm.lsfgVk11Compat = v }),
            showInGame = false,
            visible = { _, _ -> FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED },
            disabled = { env, values -> if (env.wayland(values)) T("在 Wayland 上不使用：帧生成在合成器中运行在您的 Turnip 驱动程序上。") else null },
        ),
        SettingDef(
            id = "performance.syncMode", category = "performance", group = T("Wine sync"), level = ADVANCED,
            label = T("Sync"), hint = T("Wine 如何在线程之间等待。esync 快速且经过充分测试；ntsync 最接近 Windows；wineserver 最慢且兼容性最好。"), tech = "syncMode",
            help = T("esync 是层默认。ntsync 是新功能：可按游戏尝试。wineserver 将每次等待都通过 wineserver 发送，适用于出现异常的游戏。fsync 无法在 Android 上运行。层无法运行的选择会回退到其默认值。"),
            control = { env, _ -> Control.Segmented(SyncMode.ALL.map { m -> Opt(m, m, disabledReason = syncReason(env, m)) }) },
            vm = vmString({ it.syncMode }, { vm, v -> vm.onSyncModePicked(v) }),
            // A game stores a mode only when it differs from the container's; picking the container's follows it again.
            game = GameBinding(
                keys = listOf(SyncMode.EXTRA),
                read = { e -> SyncSupport.storedMode(e.get(SyncMode.EXTRA), e.get("envVars")) },
                write = { e, v ->
                    val vm = e.env.vm
                    val resolved = vm?.syncCaps?.resolve(v) ?: v
                    e.put(SyncMode.EXTRA, resolved.takeIf { it != vm?.syncMode })
                    stripGameSyncVars(e)
                },
                reset = { e -> e.put(SyncMode.EXTRA, null); stripGameSyncVars(e) },
            ),
            onDefaults = false,
            note = { env, values ->
                val vm = env.vm
                if (env.scope == EditorScope.GAME) {
                    val picked = values("performance.syncMode")
                    val caps = vm?.syncCaps
                    if (caps != null && !caps.isAvailable(picked)) "$picked isn't in ${vm?.selectedWineVersion.orEmpty()}: this game runs ${caps.resolve(picked)}." else null
                } else vm?.syncNotice
            },
        ),
        SettingDef(
            id = "performance.translator", category = "performance", group = T("x86 翻译器"), level = ADVANCED,
            label = T("用于 32 位代码的 x86 翻译器"), hint = T("在您的 ARM CPU 上运行游戏的 32 位 x86 代码。"), tech = T("转译器 (32位)"),
            control = { env, _ ->
                val labels = mapOf("fexcore" to "FEXCore", "box64" to "WOWBox64")
                Control.Segmented((env.vm?.emulatorEntries ?: emptyList()).map { Opt(idOf(it), labels[idOf(it)] ?: it) })
            },
            vm = VmBinding(get = { _, vm -> idOf(vm.selectedEmulator) }, set = { _, vm, v -> vm.selectedEmulator = displayFor(v, vm.emulatorEntries) }),
            game = key("emulator") { it.lowercase(Locale.ENGLISH) },
            visible = { env, _ -> env.arm64ec },
        ),
        SettingDef(
            id = "performance.translator64", category = "performance", group = T("x86 翻译器"), level = ADVANCED,
            label = T("用于 64 位代码的 x86 翻译器"), hint = T("运行游戏的 64 位 x86 代码。Box64EC 需要安装 Box64EC 组件。"), tech = T("转译器 (64位)"),
            control = { env, _ ->
                Control.Segmented(listOf(
                    Opt("fexcore", "FEXCore"),
                    Opt("box64ec", "Box64EC", disabledReason = if (env.box64ECInstalled) null else T("请先安装 Box64EC 组件。")),
                ))
            },
            vm = VmBinding(get = { _, vm -> idOf(vm.selectedEmulator64) }, set = { _, vm, v -> vm.selectedEmulator64 = displayFor(v, vm.emulator64Entries) }),
            game = key("emulator64") { it.lowercase(Locale.ENGLISH) },
            // A game is offered the choice once a Box64EC is installed; until then it follows the container (FEXCore).
            visible = { env, _ -> env.arm64ec && (env.scope != EditorScope.GAME || env.box64ECInstalled) },
        ),
        SettingDef(
            id = "performance.fexVersion", category = "performance", group = "FEXCore", level = ADVANCED,
            label = T("FEX 版本"), tech = T("FEXCore 版本"),
            control = { env, _ -> Control.Select((env.vm?.fexCoreVersionEntries ?: emptyList()).map { Opt(it, it) }) },
            vm = vmString({ it.selectedFEXCoreVersion }, { vm, v -> vm.selectedFEXCoreVersion = v }),
            game = key("fexcoreVersion"),
            visible = { env, _ -> env.arm64ec },
            getMore = "fex",
        ),
        // The Linux runtime brings its own FEX, but hands this preset to the games the Steam client launches.
        SettingDef(
            id = "performance.fexPreset", category = "performance", group = "FEXCore", level = ADVANCED,
            label = T("FEX 预设"), hint = T("以速度换取兼容性。在下面的翻译器预设值下编辑其值。"), tech = T("FEXCore 预设"),
            control = { env, _ -> Control.Select(env.fexPresets.map { Opt(it.id, it.name) }) },
            vm = VmBinding(get = { _, vm -> vm.selectedFEXCorePresetId }, set = { _, vm, v -> vm.selectFEXCorePresetById(v) }),
            game = key("fexcorePreset"),
            visible = { env, _ -> env.isLinuxEntry || env.arm64ec },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "performance.forceSsbs", category = "performance", group = "FEXCore", level = ADVANCED,
            label = T("强制 SSBS（CPU 速度修复）"), hint = T("为游戏线程保持 CPU 的推测存储快速路径开启（仅限 arm64ec Proton）。默认开启。"), tech = "forceSsbs",
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.forceSsbs }, { vm, v -> vm.forceSsbs = v }),
            game = key10("forceSsbs"),
            visible = { env, _ -> env.arm64ec },
        ),
        SettingDef(
            id = "performance.box64Version", category = "performance", group = T("Box64 / WOWBox64"), level = ADVANCED,
            label = T("Box64 版本"), hint = T("在 ARM64EC 容器中称为 WOWBox64，当上面选择它时，它会运行 32 位代码。"), tech = T("Box64 版本 / WOWBox64 版本"),
            control = { env, _ -> Control.Select((env.vm?.box64VersionEntries ?: emptyList()).map { Opt(it, it) }) },
            vm = vmString({ it.selectedBox64Version }, { vm, v -> vm.selectedBox64Version = v }),
            game = key("box64Version"),
            getMore = "box64",
        ),
        SettingDef(
            id = "performance.box64Preset", category = "performance", group = T("Box64 / WOWBox64"), level = ADVANCED,
            label = T("Box64 预设"), hint = T("其 BOX64_* 变量也适用于 Box64EC。"), tech = T("Box64 预设 / WOWBox64 预设"),
            control = { env, _ -> Control.Select(env.box64Presets.map { Opt(it.id, it.name) }) },
            vm = VmBinding(get = { _, vm -> vm.selectedBox64PresetId }, set = { _, vm, v -> vm.selectBox64PresetById(v) }),
            game = key("box64Preset"),
        ),
        SettingDef(
            id = "performance.box64ecVersion", category = "performance", group = "Box64EC", level = ADVANCED,
            label = T("Box64EC 版本"), hint = T("当 64 位翻译器为 Box64EC 时运行 64 位游戏。"), tech = T("Box64EC 版本"),
            control = { env, _ -> Control.Select((env.vm?.box64ECVersionEntries ?: emptyList()).map { Opt(it, it) }) },
            vm = vmString({ it.selectedBox64ECVersion }, { vm, v -> vm.selectedBox64ECVersion = v }),
            game = key("box64ecVersion"),
            visible = { env, _ -> env.arm64ec },
            disabled = { env, _ ->
                if (env.box64ECInstalled) null
                else if (!com.winlator.star.ui.theme.AppThemeState.deckXmbHome.value) T("未安装 Box64EC。请在下方下载一个（翻译器下载）。")
                else T("未安装 Box64EC。使用「获取更多」下载一个。")
            },
            getMore = "box64ec",
        ),
        SettingDef(
            id = "performance.startupServices", category = "performance", group = T("Windows 后台服务"), level = ADVANCED,
            label = T("随 Windows 启动的服务"), hint = T("更少的服务 = 更快的启动和更多的可用 RAM。"), tech = T("启动选择"),
            control = { _, _ -> seg("0" to T("All"), "1" to T("仅必要"), "2" to T("启动时停止"), "3" to T("Custom")) },
            vm = vmInt({ it.selectedStartupSelection }, { vm, v -> vm.selectedStartupSelection = v }),
            game = intKey("startupSelection"),
        ),
        SettingDef(
            id = "performance.cpuAffinity", category = "performance", group = T("CPU 核心"), level = ADVANCED,
            label = T("游戏可使用的核心"), hint = T("至少保持一个核心开启。"), tech = T("处理器关联 (cpuList)"),
            control = { _, _ -> Control.Cores },
            vm = vmString({ it.cpuList }, { vm, v -> vm.cpuList = v }),
            game = key("cpuList"),
        ),
        SettingDef(
            id = "performance.cpuAffinity32", category = "performance", group = T("CPU 核心"), level = EXPERT,
            label = T("32 位程序可使用的核心"), hint = T("对 32 位 (WOW64) 程序使用相同的选择。"), tech = T("处理器关联（32 位应用）(cpuListWoW64)"),
            control = { _, _ -> Control.Cores },
            vm = vmString({ it.cpuListWoW64 }, { vm, v -> vm.cpuListWoW64 = v }),
            showInGame = false,
            visible = { env, _ -> env.arm64ec },
        ),
        perfToggle("performance.power.sustained", "sustainedPerfMode", T("持续性能模式"), T("为长时间会话保持时钟稳定，而不是短时爆发。")),
        perfToggle("performance.power.priorityBoost", "perfPriorityBoost", T("提升游戏线程优先级"), null),
        perfToggle("performance.power.bigCores", "preferBigCores", T("优先使用大核"), null),
        // Custom services, the GPU clock lock and root switches, and the preset values' hidden rows.
        *PerformanceRows.defs().toTypedArray(),

        // Visual effects (per game only: the container has no vkBasalt setting)
        SettingDef(
            id = "visuals.vkbasalt", category = "visuals", group = T("锐化"), level = ADVANCED,
            label = T("Vulkan 后期效果 (vkBasalt)"), hint = T("在图像绘制后对其进行锐化。"), tech = T("锐度 (VKBasalt) › 效果"),
            control = { _, _ -> seg("None" to T("None"), "CAS" to T("CAS sharpen"), "DLS" to T("DLS sharpen + denoise")) },
            game = GameBinding(listOf("sharpnessEffect"), read = { e -> e.get("sharpnessEffect")?.takeIf { it.isNotEmpty() } },
                write = { e, v -> e.put("sharpnessEffect", v) }, inherit = Inherit.BUILT_IN, inherited = { "None" }),
            visible = { env, _ -> gameScope(env) },
        ),
        percentSlider("visuals.vkbasaltLevel", "sharpnessLevel", T("vkBasalt 强度"), T("Level")) { values -> values("visuals.vkbasalt") != "None" },
        percentSlider("visuals.vkbasaltDenoise", "sharpnessDenoise", T("vkBasalt 降噪"), T("降噪")) { values -> values("visuals.vkbasalt") == "DLS" },
        // ReShade: Solo / Stack and the loadout the section edits.
        *ReshadeRows.defs().toTypedArray(),

        // Audio
        SettingDef(
            id = "audio.driver", category = "audio", group = T("声音"), level = SIMPLE,
            label = T("声音系统"), hint = T("PulseAudio 适合大多数游戏。DirectAudio 在附带它的构建上具有最低延迟。"), tech = T("Audio Driver"),
            control = { env, _ ->
                val labels = mapOf("alsa" to "ALSA", "pulseaudio" to T("PulseAudio"), "directaudio" to T("DirectAudio"))
                // A Linux entry's DirectAudio is the runtime's own relay driver, so the container's layer says nothing about it.
                val supported = env.scope == EditorScope.DEFAULTS || env.isLinuxEntry ||
                    env.vm?.let { DirectAudioSupport.isSupported(it.selectedWineVersion) } == true
                Control.Segmented((env.vm?.audioDriverEntries ?: emptyList()).map { entry ->
                    val id = idOf(entry)
                    Opt(id, labels[id] ?: entry,
                        disabledReason = if (id == "directaudio" && !supported) "${DirectAudioSupport.UNSUPPORTED_NOTE}." else null)
                })
            },
            vm = VmBinding(get = { _, vm -> idOf(vm.selectedAudioDriver) }, set = { _, vm, v -> vm.selectedAudioDriver = displayFor(v, vm.audioDriverEntries) }),
            game = key("audioDriver"),
            confirm = { v ->
                if (v == "directaudio") Confirm(
                    T("DirectAudio 是实验性的"),
                    T("它将游戏的声音直接发送到 Android 的音频系统，中间没有 PulseAudio 或 ALSA 服务器：最低延迟路径。由于它是新功能，某些游戏可能偶尔跳音，或者预设可能不生效；自动预设是最流畅的。如果遇到音频问题，请切换回 PulseAudio 或 ALSA。"),
                    T("使用 DirectAudio"),
                ) else null
            },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "audio.midiSoundfont", category = "audio", group = T("声音"), level = ADVANCED,
            label = T("MIDI 音乐音色库"), hint = T("播放 MIDI 音乐的旧游戏需要音色库。"), tech = T("MIDI SoundFont"),
            control = { env, _ ->
                Control.Select((env.vm?.midiEntries ?: emptyList()).mapIndexed { i, name ->
                    Opt("$i", when {
                        i == 0 -> T("Off")
                        name == MidiManager.DEFAULT_SF2_FILE -> T("Built-in General MIDI bank")
                        else -> name
                    })
                })
            },
            vm = vmInt({ it.selectedMidiIndex }, { vm, v -> vm.selectedMidiIndex = v }),
        ),
        // Sound latency preset, the microphone, and the env string the fine-tune dialog edits.
        *AudioRows.defs().toTypedArray(),
        // Expert: the same env string, every variable of it (DeckEnvVarsSection).
        *EnvVarRows.defs().toTypedArray(),

        // Controls
        SettingDef(
            id = "input.controlsProfile", category = "input", group = T("控制器"), level = SIMPLE,
            label = T("屏幕控制"), hint = T("游戏上方显示哪种触摸布局。"), tech = T("控制器配置"),
            control = { env, _ -> Control.Select(listOf(Opt("0", T("None"))) + env.controlsProfiles.map { Opt("${it.id}", it.name) }) },
            game = GameBinding(
                listOf("controlsProfile"),
                read = { e -> e.get("controlsProfile")?.trim()?.takeIf { it.isNotEmpty() && it != "0" } },
                write = { e, v -> e.put("controlsProfile", if (v == "0") null else v) },
                inherit = Inherit.BUILT_IN, inherited = { "0" },
            ),
            visible = { env, _ -> gameScope(env) },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.dinputVisible", category = "input", group = T("控制器"), level = ADVANCED,
            label = T("DirectInput 游戏可以看到控制器"), hint = T("默认关闭，因此同时读取 XInput 和 DirectInput 的游戏只会看到一个手柄。"),
            help = T("允许 DirectInput 游戏（许多较旧游戏、赛车游戏、方向盘）看到控制器。关闭会将其从 DirectInput 中隐藏，以便同时支持 XInput 和 DirectInput 的游戏不会看到两个。经典 DirectInput 手柄身份始终会开启此选项。"),
            tech = T("Exclusive Input独占输入 / 启用 DInput"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.dinputVisible }, { vm, v -> vm.dinputVisible = v }),
            // Stored in the game's inputType / exclusiveXInput extras, which older builds read too
            // (DInputVisibility maps their old combinations); Reset removes both.
            game = GameBinding(
                listOf(DInputVisibility.EXTRA_EXCLUSIVE, DInputVisibility.EXTRA_INPUT_TYPE),
                read = { e ->
                    val exclusive = e.get(DInputVisibility.EXTRA_EXCLUSIVE)
                    val inputType = e.get(DInputVisibility.EXTRA_INPUT_TYPE)
                    if (exclusive.isNullOrEmpty() && inputType.isNullOrBlank()) null
                    else onOff(DInputVisibility.fromExtras(exclusive, inputType, e.container))
                },
                write = { e, v ->
                    e.put(DInputVisibility.EXTRA_EXCLUSIVE, "1")
                    e.put(DInputVisibility.EXTRA_INPUT_TYPE, "${DInputVisibility.inputTypeFor(v == "1")}")
                },
            ),
        ),
        SettingDef(
            id = "input.dinputVibration", category = "input", group = T("控制器"), level = ADVANCED,
            label = T("DirectInput 震动"), hint = T("DirectInput 游戏（赛车、飞行、许多较旧游戏）可以使控制器震动。默认开启。"),
            help = T("允许 DirectInput 游戏使控制器震动：普通震动，以及赛车和飞行游戏发送到方向盘或操纵杆的力反馈，通过控制器的两个马达感受到。它遵循震动设置（震动目标和强度）。稳定的力（例如赛车游戏保持方向盘直行）会在几秒后消退，因此控制器不会持续嗡嗡作响。DirectInput 游戏仅在可以看到控制器时才能感受到。更改在控制器重新连接时（在游戏中，控制 › 手柄可执行此操作）或下次启动时应用。环境变量中的 BANNERIN_DINPUT_FF 优先于此设置。"),
            tech = DInputVibration.EXTRA,
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.dinputVibration }, { vm, v -> vm.dinputVibration = v }),
            game = key10(DInputVibration.EXTRA),
            disabled = { env, _ -> DInputVibration.lockedReason(env.vm?.container, env.shortcut) },
        ),
        SettingDef(
            id = "input.bannerinXInput", category = "input", group = T("控制器"), level = ADVANCED,
            label = T("Direct XInput (BANNERIN)"), hint = T("XInput 游戏直接读取控制器，没有输入队列（释放后没有过时的摇杆值）。新容器默认开启。"), tech = "bannerinXInput",
            help = T("XInput 游戏直接从 Bannerlator 读取控制器，而不是通过 Wine 的控制器队列：始终是实时的摇杆位置，无额外延迟，包含震动。关闭：XInput 像以前一样通过 Wine。如果游戏出现异常，请为该游戏关闭它。"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.bannerinXInput }, { vm, v -> vm.bannerinXInput = v }),
            game = key10("bannerinXInput"),
        ),
        SettingDef(
            id = "input.bannerinXInputSwitching", category = "input", group = T("控制器"), level = ADVANCED,
            label = T("允许游戏内切换"), hint = T("在游戏内菜单中切换 Direct XInput 的开启或关闭，无需重启。"),
            help = T("无论 Direct XInput 是开启还是关闭，都会安装 Bannerlator 的 XInput DLL，并将 Wine 自己的 XInput 放在旁边，以便游戏内菜单中的控制 › 手柄可以在游戏运行时在它们之间切换（控制器会重新连接）。默认开启。关闭：Direct XInput 保持游戏启动时的状态，更改它需要重启。"),
            tech = BannerinXInput.EXTRA_SWITCHING,
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.bannerinXInputSwitching }, { vm, v -> vm.bannerinXInputSwitching = v }),
            game = key10(BannerinXInput.EXTRA_SWITCHING),
        ),
        SettingDef(
            id = "input.stickPacing", category = "input", group = T("控制器"), level = ADVANCED,
            label = T("摇杆更新"), hint = T("摇杆移动到达游戏的频率。也可在游戏中更改。"),
            help = T("自动（默认）：游戏绘制的每一帧更新一次摇杆，直到设定的限制（默认每秒 60 次），因此绘制速度快于读取输入速度的游戏永远不会落后。固定：每秒那么多次更新。无节奏限制：每次变化立即发送。立即应用，也可在游戏中应用（控制 › 手柄）。环境变量中的 BANNERIN_AXIS_HZ / BANNERIN_AXIS_AUTO_MAX 优先于此设置。"),
            tech = StickPacing.EXTRA,
            control = { _, _ ->
                Control.Select(
                    listOf(
                        Opt("auto:60", T("自动（最高 60/秒）")), Opt("auto:120", T("自动（最高 120/秒）")), Opt("auto:30", T("自动（最高 30/秒）")),
                        Opt("fixed:60", T("固定 60/秒")), Opt("fixed:125", T("固定 125/秒")), Opt("fixed:250", T("固定 250/秒")),
                        Opt("off", T("Unpaced")),
                    ),
                    customLabel = T("自定义…"), customHint = T("auto:N (20–240)、fixed:N (30–1000) 或关闭"),
                    validateCustom = { v -> if (StickPacing.parse(v) == null) T("Use auto:N, fixed:N or off") else null },
                    normalizeCustom = { v -> StickPacing.parse(v)?.format() ?: v.trim() },
                )
            },
            vm = vmString({ it.stickPacing }, { vm, v -> vm.stickPacing = StickPacing.parse(v)?.format() ?: StickPacing.DEFAULT.format() }),
            game = key(StickPacing.EXTRA) { v -> StickPacing.parse(v)?.format() },
            disabled = { env, _ ->
                if (StickPacing.envOverrides(env.vm?.container, env.shortcut)) T("Set by BANNERIN_AXIS_HZ / BANNERIN_AXIS_AUTO_MAX in the env vars.") else null
            },
        ),
        SettingDef(
            id = "input.controllerIdentity", category = "input", group = T("控制器"), level = ADVANCED,
            label = T("控制器身份"), hint = T("游戏看到哪个控制器。也可在游戏中更改（控制器会重新连接）。"),
            help = T("通用（默认）：与所有内容兼容，与以前相同。Xbox 360 / One / Series：游戏将手柄视为 Xbox 控制器；最好与 Direct XInput 配合使用以实现实时输入和震动。经典 DirectInput 手柄：适用于不接受 Xbox 手柄的旧游戏；它始终允许 DirectInput 游戏看到控制器。PlayStation / Nintendo / Joy-Con / Steam Deck（实验性）：游戏可能会显示那些按钮图标；无陀螺仪、触控板、灯条或触控板，少数游戏可能无法检测到手柄。如果是这样，请切换回通用或 Xbox。\n\n如果控制器在游戏中消失，请切换回通用。"),
            tech = ControllerIdentity.ENV,
            control = { _, _ -> Control.Select(ControllerIdentity.wineChoices().map { Opt(it.value, it.label) }) },
            vm = vmString({ ControllerIdentity.forWine(it.controllerIdentity) }, { vm, v -> vm.controllerIdentity = ControllerIdentity.forWine(v) }),
            // Only values a Wine game can use: a Linux-only one (e.g. from an imported config) reads as unset.
            game = key(ControllerIdentity.EXTRA) { v -> ControllerIdentity.normalize(v)?.takeIf { it == ControllerIdentity.forWine(it) } },
        ),
        SettingDef(
            id = "input.disableXInput", category = "input", group = T("控制器"), level = EXPERT,
            label = T("禁用XInput"), hint = T("对此游戏隐藏 Xbox 控制器。"), tech = "disableXinput",
            control = { _, _ -> Control.Toggle },
            game = GameBinding(
                listOf("disableXinput"),
                read = { e -> e.get("disableXinput")?.takeIf { it.isNotEmpty() }?.let { if (it == "1") "1" else "0" } },
                write = { e, v -> e.put("disableXinput", if (v == "1") "1" else null) },
                inherit = Inherit.BUILT_IN, inherited = { "0" },
            ),
            visible = { env, _ -> gameScope(env) },
        ),
        SettingDef(
            id = "input.numControllers", category = "input", group = T("控制器"), level = ADVANCED,
            label = T("控制器数量"), hint = T("向游戏提供多少个手柄。"), tech = T("控制器数量"),
            control = { _, _ -> seg("1" to "1", "2" to "2", "3" to "3", "4" to "4") },
            game = GameBinding(
                listOf("numControllers"),
                read = { e -> e.get("numControllers")?.trim()?.toIntOrNull()?.coerceIn(1, 4)?.toString() },
                write = { e, v -> e.put("numControllers", v) },
                inherit = Inherit.BUILT_IN, inherited = { "1" },
            ),
            visible = { env, _ -> gameScope(env) },
        ),
        SettingDef(
            id = "input.hideOnController", category = "input", group = T("玩家槽位"), level = SIMPLE,
            label = T("当控制器连接时隐藏屏幕控制"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.autoHideControlsOnPad }, { vm, v -> vm.autoHideControlsOnPad = v }),
            game = key10("autoHideControlsOnPad"),
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.onScreenPriority", category = "input", group = T("玩家槽位"), level = ADVANCED,
            label = T("屏幕控制与真实手柄"), tech = T("屏幕优先级"),
            control = { _, _ ->
                seg("${Container.ON_SCREEN_MODE_KEEP}" to T("屏幕控制保持为玩家 1"),
                    "${Container.ON_SCREEN_MODE_YIELD}" to T("手柄成为玩家 1"),
                    "${Container.ON_SCREEN_MODE_SHARE}" to T("两者都控制玩家 1"))
            },
            vm = vmInt({ it.onScreenControllerMode }, { vm, v -> vm.onScreenControllerMode = v }),
            game = intKey("onScreenControllerMode"),
        ),
        // Touch and Aim mode (TouchAimSettings): the in-game menu's Controls › Mouse choices, saved for the
        // game. An unset game follows Settings › Mouse & keyboard. Touchscreen is the old "Touch acts as".
        SettingDef(
            id = "input.touchMode", category = "input", group = T("鼠标与触摸"), level = ADVANCED,
            label = T("触摸"), tech = "touchMode · simTouchScreen",
            control = { _, _ -> seg(TouchAimSettings.TOUCHPAD to T("Touchpad"), TouchAimSettings.DIRECT to T("Direct"), TouchAimSettings.TOUCHSCREEN to T("Touchscreen")) },
            game = GameBinding(
                keys = listOf(TouchAimSettings.EXTRA_TOUCH, TouchAimSettings.EXTRA_SIM_TOUCH),
                read = { e -> TouchAimSettings.gameTouch { k: String -> e.get(k) } },
                write = { e, v ->
                    val screen = v == TouchAimSettings.TOUCHSCREEN
                    e.put(TouchAimSettings.EXTRA_SIM_TOUCH, if (screen) "1" else null)
                    e.put(TouchAimSettings.EXTRA_TOUCH, if (screen) null else v)
                },
                inherit = Inherit.APP,
                inherited = { env -> TouchAimSettings.appTouch(PreferenceManager.getDefaultSharedPreferences(env.context)) },
            ),
            visible = { env, _ -> gameScope(env) },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.aimMode", category = "input", group = T("鼠标与触摸"), level = ADVANCED,
            label = T("瞄准模式"), tech = "aimMode · relativeMouse",
            control = { _, _ -> seg(TouchAimSettings.AUTO to T("Auto"), TouchAimSettings.ON to T("On"), TouchAimSettings.OFF to T("Off")) },
            game = GameBinding(
                keys = listOf(TouchAimSettings.EXTRA_AIM, TouchAimSettings.EXTRA_RELATIVE_MOUSE),
                read = { e -> TouchAimSettings.ownAim { k: String -> e.get(k) } },
                write = { e, v ->
                    e.put(TouchAimSettings.EXTRA_AIM, v)
                    e.put(TouchAimSettings.EXTRA_RELATIVE_MOUSE, if (v == TouchAimSettings.ON) "1" else "0")
                },
                inherit = Inherit.APP,
                inherited = { env ->
                    val c = env.vm?.container
                    TouchAimSettings.resolveAim(null, c?.let { ct -> java.util.function.Function { k: String -> ct.getExtra(k) } },
                        TouchAimSettings.appAim(PreferenceManager.getDefaultSharedPreferences(env.context)))
                },
            ),
            visible = { env, _ -> gameScope(env) },
        ),
        SettingDef(
            id = "input.mouseWarp", category = "input", group = T("鼠标与触摸"), level = EXPERT,
            label = T("鼠标捕获 (DirectInput)"), hint = T("修复某些较旧游戏中的相机旋转或卡住问题。"), tech = T("鼠标偏移覆盖"),
            control = { _, _ -> seg("0" to T("Off"), "1" to T("On"), "2" to T("强制")) },
            vm = vmInt({ it.selectedMouseWarpIndex }, { vm, v -> vm.selectedMouseWarpIndex = v }),
            onDefaults = false, showInGame = false,
            visible = { env, _ -> env.scope == EditorScope.CONTAINER },
        ),
        SettingDef(
            id = "input.vibrationTarget", category = "input", group = T("震动"), level = SIMPLE,
            label = T("震动开启"), tech = T("振动模式"),
            help = T("控制器：使游戏手柄震动。设备：使掌机/手机震动（在某些设备上，例如 AYANEO，系统无论如何都会将其路由到游戏手柄）。两者 / 关闭：如名称所示。"),
            control = { _, _ ->
                seg("${Container.VIBRATION_MODE_OFF}" to T("Off"), "${Container.VIBRATION_MODE_CONTROLLER}" to T("Controller"),
                    "${Container.VIBRATION_MODE_DEVICE}" to T("此设备"), "${Container.VIBRATION_MODE_BOTH}" to T("Both"))
            },
            vm = vmInt({ it.vibrationMode }, { vm, v -> vm.vibrationMode = v }),
            game = intKey("vibrationMode"),
        ),
        SettingDef(
            id = "input.vibrationIntensity", category = "input", group = T("震动"), level = ADVANCED,
            label = T("震动强度"), tech = T("振动强度"),
            help = T("缩放震动强度。注意：在某些掌机上，较低的强度不会减少摇杆变慢。为此请使用震动马达：单马达。"),
            control = { _, _ -> Control.Slider(0f, 100f, 5f, { "${it.toInt()}%" }, { "${it.toInt()}" }) },
            vm = vmInt({ it.vibrationIntensity }, { vm, v -> vm.vibrationIntensity = v }),
            game = intKey("vibrationIntensity"),
            visible = { _, values -> values("input.vibrationTarget") != "${Container.VIBRATION_MODE_OFF}" },
        ),
        SettingDef(
            id = "input.rumbleMotors", category = "input", group = T("震动"), level = ADVANCED,
            label = T("震动马达"), hint = T("对于两个马达同时运行时摇杆会变慢的掌机，单马达可保持摇杆流畅。"),
            help = T("两者（PC 精确）：像 PC 控制器一样分别驱动大马达和小马达。单马达（更流畅的摇杆）：使用一个马达。某些掌机控制器（例如 AYANEO Pocket FIT）在两个马达运行时会使摇杆更新变慢；单马达将这种变慢减半，使瞄准保持流畅。默认：Pocket FIT 上为单马达，其他地方为两者。"),
            tech = RumbleMotors.EXTRA,
            control = { _, _ -> seg("${RumbleMotors.BOTH}" to T("两者（PC 精确）"), "${RumbleMotors.SINGLE}" to T("单马达（更流畅的摇杆）")) },
            vm = vmInt({ it.rumbleMotors }, { vm, v -> vm.rumbleMotors = RumbleMotors.sanitize(v) }),
            game = key(RumbleMotors.EXTRA) { v -> RumbleMotors.parse(v)?.toString() },
            // Only a controller has two motors (the phone's vibrator is one).
            visible = { _, values ->
                values("input.vibrationTarget") == "${Container.VIBRATION_MODE_CONTROLLER}" ||
                    values("input.vibrationTarget") == "${Container.VIBRATION_MODE_BOTH}"
            },
        ),
        SettingDef(
            id = "input.gyro.enabled", category = "input", group = T("体感瞄准（陀螺仪）"), level = SIMPLE,
            label = T("体感瞄准"), hint = T("倾斜设备以瞄准。"), tech = T("启用体感瞄准"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.gyroEnabled }, { vm, v -> vm.gyroEnabled = v }),
            game = key10("gyroEnabled"),
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.gyro.mode", category = "input", group = T("体感瞄准（陀螺仪）"), level = ADVANCED,
            label = T("体感风格"), tech = T("体感模式"),
            control = { _, _ -> seg("${Container.GYRO_MODE_RATE}" to T("Turn speed"), "${Container.GYRO_MODE_ORIENTATION}" to T("Tilt to aim")) },
            // Tilt to aim can't drive the mouse, so it moves a mouse target to the right stick, as the classic editors do.
            vm = VmBinding(
                get = { _, vm -> vm.gyroMode.toString() },
                set = { _, vm, v ->
                    vm.gyroMode = v.toIntOrNull() ?: Container.GYRO_MODE_RATE
                    if (vm.gyroMode == Container.GYRO_MODE_ORIENTATION && vm.gyroTarget == Container.GYRO_TARGET_MOUSE) vm.gyroTarget = Container.GYRO_TARGET_RIGHT_STICK
                },
            ),
            game = GameBinding(
                listOf("gyroMode"),
                read = { e -> e.get("gyroMode")?.trim()?.toIntOrNull()?.toString() },
                write = { e, v ->
                    e.put("gyroMode", v)
                    if (v == "${Container.GYRO_MODE_ORIENTATION}" && e.get("gyroTarget") == "${Container.GYRO_TARGET_MOUSE}") e.put("gyroTarget", "${Container.GYRO_TARGET_RIGHT_STICK}")
                },
            ),
            visible = { _, values -> values("input.gyro.enabled") == "1" },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.gyro.target", category = "input", group = T("体感瞄准（陀螺仪）"), level = ADVANCED,
            label = T("体感移动"), tech = T("体感目标"),
            control = { _, values ->
                Control.Segmented(listOf(
                    Opt("${Container.GYRO_TARGET_RIGHT_STICK}", T("右摇杆")),
                    Opt("${Container.GYRO_TARGET_LEFT_STICK}", T("左摇杆")),
                    Opt("${Container.GYRO_TARGET_MOUSE}", T("Mouse"),
                        disabledReason = if (values("input.gyro.mode") == "${Container.GYRO_MODE_ORIENTATION}") T("Not with Tilt to aim.") else null),
                ))
            },
            vm = vmInt({ it.gyroTarget }, { vm, v -> vm.gyroTarget = v }),
            game = intKey("gyroTarget"),
            visible = { _, values -> values("input.gyro.enabled") == "1" },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.gyro.activator", category = "input", group = T("体感瞄准（陀螺仪）"), level = ADVANCED,
            label = T("按住此键以瞄准"), tech = T("激活按钮"),
            control = { _, _ ->
                seg("${Container.GYRO_ACTIVATOR_L1}" to "L1", "${Container.GYRO_ACTIVATOR_L2}" to "L2", "${Container.GYRO_ACTIVATOR_R1}" to "R1",
                    "${Container.GYRO_ACTIVATOR_R3}" to "R3", "${Container.GYRO_ACTIVATOR_ALWAYS}" to T("Always on"))
            },
            vm = vmInt({ it.gyroActivator }, { vm, v -> vm.gyroActivator = v }),
            game = intKey("gyroActivator"),
            visible = { _, values -> values("input.gyro.enabled") == "1" },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.gyro.activation", category = "input", group = T("体感瞄准（陀螺仪）"), level = ADVANCED,
            label = T("按钮行为"), tech = T("激活"),
            control = { _, _ -> seg("${Container.GYRO_ACTIVATION_HOLD}" to T("Hold"), "${Container.GYRO_ACTIVATION_TOGGLE}" to T("Toggle")) },
            vm = vmInt({ it.gyroActivationMode }, { vm, v -> vm.gyroActivationMode = v }),
            game = intKey("gyroActivationMode"),
            visible = { _, values -> values("input.gyro.enabled") == "1" && values("input.gyro.activator") != "${Container.GYRO_ACTIVATOR_ALWAYS}" },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.gyro.sensitivity", category = "input", group = T("体感瞄准（陀螺仪）"), level = ADVANCED,
            label = T("灵敏度"), tech = T("灵敏度"),
            control = { _, _ -> Control.Slider(0.1f, 10f, 0.1f, { fmt1(it) }, { fmt1(it) }) },
            vm = VmBinding(get = { _, vm -> fmt1(vm.gyroSensitivity) }, set = { _, vm, v -> v.toFloatOrNull()?.let { vm.gyroSensitivity = it } }),
            game = key("gyroSensitivity") { v -> v.toFloatOrNull()?.let { fmt1(it) } },
            visible = { _, values -> values("input.gyro.enabled") == "1" },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.gyro.deadzone", category = "input", group = T("体感瞄准（陀螺仪）"), level = ADVANCED,
            label = T("忽略小幅移动"), hint = T("按容器设置：它描述您的手，而非游戏。"), tech = T("死区"),
            control = { _, _ -> Control.Slider(0f, 0.5f, 0.01f, { fmt2(it) }, { fmt2(it) }) },
            vm = VmBinding(get = { _, vm -> fmt2(vm.gyroDeadzone) }, set = { _, vm, v -> v.toFloatOrNull()?.let { vm.gyroDeadzone = it } }),
            visible = { _, values -> values("input.gyro.enabled") == "1" },
        ),
        SettingDef(
            id = "input.gyro.smoothing", category = "input", group = T("体感瞄准（陀螺仪）"), level = ADVANCED,
            label = T("平滑"), hint = T("更少抖动，略微更多延迟。按容器设置。"), tech = T("平滑"),
            control = { _, _ -> Control.Slider(0f, 0.95f, 0.05f, { fmt2(it) }, { fmt2(it) }) },
            vm = VmBinding(get = { _, vm -> fmt2(vm.gyroSmoothing) }, set = { _, vm, v -> v.toFloatOrNull()?.let { vm.gyroSmoothing = it } }),
            visible = { _, values -> values("input.gyro.enabled") == "1" },
        ),
        SettingDef(
            id = "input.gyro.invertX", category = "input", group = T("体感瞄准（陀螺仪）"), level = ADVANCED,
            label = T("反转左/右"), tech = T("反转 X"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.gyroInvertX }, { vm, v -> vm.gyroInvertX = v }),
            game = key10("gyroInvertX"),
            visible = { _, values -> values("input.gyro.enabled") == "1" },
            worlds = WINE_LINUX,
        ),
        SettingDef(
            id = "input.gyro.invertY", category = "input", group = T("体感瞄准（陀螺仪）"), level = ADVANCED,
            label = T("反转上/下"), tech = T("反转 Y"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.gyroInvertY }, { vm, v -> vm.gyroInvertY = v }),
            game = key10("gyroInvertY"),
            visible = { _, values -> values("input.gyro.enabled") == "1" },
            worlds = WINE_LINUX,
        ),
        // Player slot pins (edited by DeckInputSection) and the XR controller mapping.
        *InputRows.defs().toTypedArray(),

        // Performance overlay: the launch path reads the on/off switch from the container only.
        SettingDef(
            id = "hud.show", category = "hud", group = T("Overlay"), level = SIMPLE,
            label = T("显示性能覆盖层"), hint = T("在游戏上方显示 FPS、CPU/GPU 负载和温度。其样式在下面，也可在游戏中从侧边菜单设置。"), tech = T("显示 FPS"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.showFPS }, { vm, v -> vm.showFPS = v }),
        ),
        // Every choice of the classic FPS counter dialog.
        *OverlayRows.defs().toTypedArray(),

        // Windows
        SettingDef(
            id = "windows.containerName", category = "windows", group = T("Container"), level = SIMPLE,
            label = T("容器名称"), hint = T("显示在容器屏幕上以及每个游戏的 \"来自容器\" 标签中。"), tech = T("Name"),
            control = { _, _ -> Control.TextField(T("容器名称")) { if (it.isBlank()) T("The name can't be empty.") else null } },
            vm = vmString({ it.containerName }, { vm, v -> vm.containerName = v }),
            onDefaults = false, showInGame = false,
            visible = { env, _ -> env.scope == EditorScope.CONTAINER },
        ),
        SettingDef(
            id = "windows.locale", category = "windows", group = T("语言与区域"), level = ADVANCED,
            label = T("语言与区域"), hint = T("修复日语、中文、韩语和俄语游戏中的乱码。"), tech = "LC_ALL",
            control = { env, _ ->
                Control.Select(
                    options = (env.vm?.lcAllEntries ?: emptyList()).map { lc ->
                        val loc = Locale.forLanguageTag(lc.replace('_', '-'))
                        Opt("$lc.UTF-8", "${loc.getDisplayLanguage(Locale.ENGLISH)} (${loc.getDisplayCountry(Locale.ENGLISH)})")
                    },
                    customLabel = T("其他区域设置…"),
                    customHint = T("例如 pl_PL.UTF-8"),
                    validateCustom = { if (LOCALE.matches(it.trim())) null else T("输入类似 pl_PL 或 pl_PL.UTF-8 的区域设置。") },
                    normalizeCustom = { t -> t.trim().let { if ('.' in it) it else "$it.UTF-8" } },
                )
            },
            vm = vmString({ it.lcAll }, { vm, v -> vm.lcAll = v }),
            game = key("lc_all"),
        ),
        SettingDef(
            id = "windows.closeOnExit", category = "windows", group = T("System"), level = ADVANCED,
            label = T("游戏退出时关闭会话"), tech = T("游戏退出时关闭"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.autoCloseOnExit }, { vm, v -> vm.autoCloseOnExit = v }),
            game = key10("autoCloseOnExit"),
        ),
        SettingDef(
            id = "windows.runAsAdmin", category = "windows", group = T("System"), level = ADVANCED,
            label = T("以管理员身份运行程序"), hint = T("避免 Windows 内的权限提示（关闭 UAC）。"), tech = T("以管理员身份运行 (EnableLUA)"),
            control = { _, _ -> Control.Toggle },
            vm = vmBool({ it.runAsAdmin }, { vm, v -> vm.runAsAdmin = v }),
            showInGame = false,
        ),
        SettingDef(
            id = "windows.theme", category = "windows", group = T("Windows 桌面"), level = ADVANCED,
            label = T("桌面主题"), tech = T("主题"),
            control = { _, _ -> seg("0" to T("Light"), "1" to T("Dark")) },
            vm = vmInt({ it.desktopThemeIndex }, { vm, v -> vm.desktopThemeIndex = v }),
            showInGame = false,
        ),
        // The Wine layer, the desktop background and colour, the wallpaper's scope.
        *WindowsRows.defs().toTypedArray(),

        // Windows components: the classic Win Components tab's choices. The install lists around them are
        // not settings; the editor's host adds them (DeckComponentsPage).
        dllRow("components.direct3d", "direct3d", T("Direct3D"), T("DirectX")),
        dllRow("components.directsound", "directsound", T("DirectSound"), T("DirectX")),
        dllRow("components.directmusic", "directmusic", T("DirectMusic"), T("DirectX")),
        dllRow("components.directshow", "directshow", T("DirectShow"), T("DirectX")),
        dllRow("components.directplay", "directplay", T("DirectPlay"), T("DirectX")),
        dllRow("components.xaudio", "xaudio", T("XAudio"), T("Audio and runtimes")),
        dllRow("components.vcrun2010", "vcrun2010", T("Visual C++ 2010"), T("Audio and runtimes")),
        codecRow("windows.codecBackend", MediaDecoder.KEY_BACKEND, T("视频解码器"),
            T("媒体基础使用哪个后端播放过场动画。FFmpeg 需要随其构建的 arm64ec 层（Proton 11.0-2 vc12+）；在其他地方会回退到 GStreamer。")),
        codecRow("windows.softwareDecoding", MediaDecoder.KEY_SOFTWARE_DECODING, T("软件视频解码"),
            T("使视频帧不经过 GPU。对于有声音但黑屏的视频，可以尝试此选项。")),

        // Linux runtime: the Linux Steam client entry only (the classic game editor's isLinuxEntry rows).
        SettingDef(
            id = "linux.displayDriver", category = "linux", group = T("Drivers"), level = SIMPLE,
            label = T("显示驱动程序（Android 端）"), hint = T("应用的合成器用于将会话放到屏幕上的驱动程序。不是客户端绘制所使用的。"), tech = T("合成器驱动程序（graphicsDriverConfig 版本）"),
            control = { env, values -> compositorControl(env, values(ID_DRIVER_VERSION)) },
            vm = compositorVm,
            game = compositorGame,
            visible = { env, _ -> gameScope(env) },
            note = { env, values -> compositorNote(env, values(ID_DRIVER_VERSION)) },
            worlds = LINUX_ONLY,
        ),
        SettingDef(
            id = "linux.drawDriver", category = "linux", group = T("Drivers"), level = SIMPLE,
            label = T("Draw 驱动（Linux runtime）"), hint = LinuxVulkanDriver.HELP_TEXT, tech = LinuxVulkanDriver.EXTRA,
            control = { env, values ->
                val current = values("linux.drawDriver")
                val known = env.linuxDrawDrivers
                Control.Select(
                    known.map { (v, label) -> Opt(v, label) } +
                        (if (known.none { it.first == current }) listOf(Opt(current, LinuxVulkanDriver.optionLabel(env.context, current))) else emptyList()),
                )
            },
            game = GameBinding(
                listOf(LinuxVulkanDriver.EXTRA),
                read = { e -> e.get(LinuxVulkanDriver.EXTRA)?.takeIf { it.isNotEmpty() } },
                write = { e, v -> e.put(LinuxVulkanDriver.EXTRA, v.ifEmpty { null }) },
                inherit = Inherit.BUILT_IN, inherited = { "" },
            ),
            visible = { env, _ -> gameScope(env) },
            note = { env, _ -> if (env.linuxDrawDrivers.size > 1) null else T("Nothing imported yet: Contents › Installed › Linux runtime drivers.") },
            worlds = LINUX_ONLY,
        ),
        linuxToggle("linux.glThread", LinuxTuning.EXTRA_GLTHREAD, T("性能（实验性）"), T("线程化 GL (mesa_glthread)"), ADVANCED,
            hint = T("降低绘制客户端界面的链路开销。尚未在每台设备上验证：请一次更改一项。")),
        linuxToggle("linux.lazyDescriptors", LinuxTuning.EXTRA_LAZY_DESCRIPTORS, T("性能（实验性）"), T("Lazy descriptors (ZINK_DESCRIPTORS)"), ADVANCED),
        linuxToggle("linux.noGlError", LinuxTuning.EXTRA_NO_GL_ERROR, T("性能（实验性）"), T("Skip GL error checks (MESA_NO_ERROR)"), ADVANCED),
        linuxToggle("linux.steamDeckMode", LinuxTuning.EXTRA_STEAMDECK, T("性能（实验性）"), T("Steam Deck 模式 (-steamdeck)"), EXPERT,
            hint = T("使客户端行为如同在 Steam Deck 上，带有快速访问菜单。游戏停止接收控制器。"),
            confirm = { v ->
                if (v == "1") Confirm(
                    T("Turn on Steam Deck mode?"),
                    L("Deck mode makes the Steam client behave as it does on a Steam Deck and adds the Quick Access Menu.\n\n") +
                        L("Known problems, found on device:\n") +
                        L("• Games stop receiving the controller. Steam's own menus still work, but in a game the pad does nothing.\n") +
                        L("• The client may show a Steam Client update under System that will not install. Deck mode now uses the Steam Deck beta channel to avoid it, but if it appears, do not press Apply.\n") +
                        L("• The Quick Access Menu's performance overlay is new and not yet tried on a device.\n\n") +
                        L("The scaling controls do the useful part without it, and the in-game drawer's FPS limit caps the frame rate. Turn Deck mode off again if a game stops responding to the controller."),
                    T("DX Wrapper 配置"),
                ) else null
            },
            note = { _, values ->
                if (values("linux.steamDeckMode") == "1") T("Deck mode is on: games do not receive the controller. If a Steam Client update shows under System that will not install, do not press Apply on it.") else null
            },
        ),
        SettingDef(
            id = "linux.steamChannel", category = "linux", group = T("性能（实验性）"), level = ADVANCED,
            label = T("Steam 客户端更新通道"), tech = LinuxTuning.EXTRA_STEAM_CHANNEL,
            control = { _, values ->
                Control.Select(LinuxTuning.STEAM_CHANNELS.map {
                    Opt(it, when (it) {
                        "" -> if (values("linux.steamDeckMode") == "1") T("Automatic (Steam Deck beta, for Deck mode)") else T("自动（公开测试版）")
                        "publicbeta" -> T("公开测试版")
                        else -> T("Steam Deck 测试版")
                    })
                })
            },
            game = linuxChoice(LinuxTuning.EXTRA_STEAM_CHANNEL, LinuxTuning.STEAM_CHANNELS.toList()),
            visible = { env, _ -> gameScope(env) },
            worlds = LINUX_ONLY,
        ),
        SettingDef(
            id = "linux.scaler", category = "linux", group = T("性能（实验性）"), level = ADVANCED,
            label = T("Scaling mode"), hint = T("仅在游戏渲染分辨率低于会话分辨率时才有意义。"), tech = LinuxTuning.EXTRA_SCALER,
            control = { _, _ -> Control.Select(LinuxTuning.SCALERS.map { Opt(it, if (it.isEmpty()) T("Default") else it.replaceFirstChar(Char::uppercase)) }) },
            game = linuxChoice(LinuxTuning.EXTRA_SCALER, LinuxTuning.SCALERS.toList()),
            visible = { env, _ -> gameScope(env) },
            worlds = LINUX_ONLY,
        ),
        SettingDef(
            id = "linux.filter", category = "linux", group = T("性能（实验性）"), level = ADVANCED,
            label = T("缩放滤镜"), hint = T("FSR、NIS 和 SGSR 可放大并锐化；SGSR 是高通的，专为 Adreno 打造。"), tech = LinuxTuning.EXTRA_FILTER,
            control = { _, _ ->
                Control.Select(LinuxTuning.FILTERS.map {
                    Opt(it, when (it) {
                        "" -> T("Default")
                        "fsr", "nis", "sgsr" -> it.uppercase(Locale.ENGLISH)
                        else -> it.replaceFirstChar(Char::uppercase)
                    })
                })
            },
            game = linuxChoice(LinuxTuning.EXTRA_FILTER, LinuxTuning.FILTERS.toList()),
            visible = { env, _ -> gameScope(env) },
            worlds = LINUX_ONLY,
        ),
        SettingDef(
            id = "linux.touch", category = "linux", group = T("Steam 客户端"), level = SIMPLE,
            label = T("触摸"), hint = T("触摸屏将手指作为真实触摸发送给 Steam；触控板通过拖动移动指针，点击即为单击。"), tech = LinuxTuning.EXTRA_TOUCH,
            control = { _, _ ->
                Control.Segmented(LinuxTuning.TOUCH_CHOICES.map {
                    Opt(it, when (it) { "" -> T("应用设置"); "1" -> T("Touchscreen"); else -> T("Touchpad") })
                })
            },
            game = linuxChoice(LinuxTuning.EXTRA_TOUCH, LinuxTuning.TOUCH_CHOICES.toList()),
            visible = { env, _ -> gameScope(env) },
            worlds = LINUX_ONLY,
        ),
        linuxToggle("linux.steamButtons", LinuxTuning.EXTRA_STEAM_BUTTONS, T("Steam 客户端"), T("屏幕上的 Steam 和快速访问按钮"), SIMPLE,
            hint = T("Steam 的菜单在左上角，其快速访问菜单在右上角。")),
        linuxToggle("linux.doubleBackQam", LinuxTuning.EXTRA_DOUBLE_BACK_QAM, T("Steam 客户端"), T("双击返回打开快速访问"), ADVANCED,
            hint = T("按一次返回仍会打开游戏内抽屉。")),
        linuxToggle("linux.fillScreen", LinuxTuning.EXTRA_FILL_SCREEN, T("Steam 客户端"), T("拉伸游戏以填满屏幕"), ADVANCED,
            hint = T("保持缩小窗口的游戏填满屏幕。")),
        linuxToggle("linux.idTech3", LinuxTuning.EXTRA_IDTECH3, T("Steam 客户端"), T("Quake 引擎游戏窗口化"), ADVANCED,
            hint = T("Quake III、Team Arena、Return to Castle Wolfenstein 和 Jedi Academy 以会话大小的窗口化模式运行。")),
        linuxToggle("linux.offline", LinuxTuning.EXTRA_OFFLINE, T("Steam 客户端"), T("离线模式（不登录 Valve）"), ADVANCED,
            hint = T("已安装的游戏仍可启动。需要先在此设备上进行一次在线登录。在下次会话时应用。")),
        linuxToggle("linux.noXalia", LinuxTuning.EXTRA_NO_XALIA, T("故障排除"), T("离线模式（不登录 Valve）"), EXPERT,
            hint = T("默认开启：在 FEX 下，xalia 会消耗每个游戏的 CPU 时间。仅对需要其手柄导航的游戏关闭。")),
        linuxToggle("linux.prootNoSeccomp", LinuxTuning.EXTRA_PROOT_NO_SECCOMP, T("故障排除"), T("proot without seccomp (PROOT_NO_SECCOMP)"), EXPERT,
            hint = T("较慢。仅适用于 seccomp 造成妨碍的设备。")),
        SettingDef(
            id = "linux.turnipSysmem", category = "linux", group = T("故障排除"), level = EXPERT,
            label = T("Turnip sysmem rendering (TU_DEBUG=sysmem)"), hint = T("A710/A720/A722 驱动程序构建所需的；自动会为这些导入开启它。环境变量中的 TU_DEBUG 优先。"), tech = LinuxTuning.EXTRA_TU_SYSMEM,
            control = { _, _ ->
                Control.Segmented(LinuxTuning.TU_SYSMEM_CHOICES.map {
                    Opt(it, when (it) { "" -> T("Automatic"); "1" -> T("On"); else -> T("Off") })
                })
            },
            game = linuxChoice(LinuxTuning.EXTRA_TU_SYSMEM, LinuxTuning.TU_SYSMEM_CHOICES.toList()),
            visible = { env, _ -> gameScope(env) },
            worlds = LINUX_ONLY,
        ),
        // The Steam client and game core lists, and the Games folders' hidden row (DeckLinuxSettings).
        *LinuxRuntimeRows.defs().toTypedArray(),
        linuxToggle("linux.appGames", LinuxTuning.EXTRA_APP_GAMES, T("您在 Steam 中的游戏"), T("在 Steam 中显示我游戏选项卡中的游戏"), ADVANCED,
            hint = T("您在游戏选项卡中添加的游戏会显示在 Steam 库的非 Steam 游戏下，并使用 Steam 的 Proton 运行。")),
        linuxToggle("linux.shareSaves", LinuxTuning.EXTRA_SHARE_SAVES, T("您在 Steam 中的游戏"), T("与应用共享存档"), ADVANCED,
            hint = T("游戏的 Documents、AppData 和 Saved Games 文件夹与其容器使用的是同一批。请勿同时在两者中运行同一款游戏。"),
            visible = { env, values -> gameScope(env) && values("linux.appGames") == "1" }),
    )

    private val LAUNCH_MODES = setOf("RealSteam", "AppSteam", "Goldberg", "Raw")
    private val RENDERERS = listOf("OpenGL", "Vulkan", "SurfaceFlinger")

    /** A power toggle: per game it overrides the app-wide default (Settings › Performance); there is no container level. */
    private fun perfToggle(id: String, key: String, label: String, hint: String?) = SettingDef(
        id = id, category = "performance", group = T("Power"), level = ADVANCED,
        label = label, hint = hint,
        control = { _, _ -> Control.Toggle },
        game = GameBinding(
            listOf(key),
            read = { e -> e.get(key)?.takeIf { it.isNotEmpty() }?.let { if (it == "1") "1" else "0" } },
            write = { e, v -> e.put(key, v) },
            inherit = Inherit.APP,
            inherited = { onOff(runCatching { PerformanceSettings.globalDefault(key) }.getOrDefault(false)) },
        ),
        visible = { env, _ -> gameScope(env) },
        worlds = WINE_LINUX,
    )

    /** A 0–100 % vkBasalt slider stored on the game. The launch path parses it as a number; 100 when unset. */
    private fun percentSlider(id: String, key: String, label: String, tech: String, show: ((String) -> String) -> Boolean) = SettingDef(
        id = id, category = "visuals", group = T("锐化"), level = ADVANCED,
        label = label, tech = tech,
        control = { _, _ -> Control.Slider(0f, 100f, 5f, { "${it.toInt()}%" }, { "${it.toInt()}" }) },
        game = GameBinding(
            listOf(key),
            read = { e -> e.get(key)?.toFloatOrNull()?.toInt()?.toString() },
            write = { e, v -> e.put(key, v) },
            inherit = Inherit.BUILT_IN, inherited = { "100" },
        ),
        visible = { env, values -> gameScope(env) && show(values) },
    )

    /** A builtin-or-native DLL row of the Win Components string (index 0 = Wine's, 1 = Microsoft's), as the classic tab offers it. */
    private fun dllRow(id: String, key: String, label: String, group: String) = wcRow(
        id, key, label, group, hint = null,
        help = "Builtin (Wine) uses Wine's own $label. Native (Windows) puts Microsoft's DLLs in the container and uses those instead. Change it only for a game that needs it.",
        options = listOf(T("Builtin (Wine)"), T("Native (Windows)")),
    )

    /**
     * A Codecs row. Codecs are two keys of the Win Components string, but media backends rather than
     * DLL overrides, so they get their own options (MediaDecoder).
     */
    private fun codecRow(id: String, key: String, label: String, hint: String) =
        wcRow(id, key, label, T("Media codecs"), hint = hint, help = null, options = MediaDecoder.optionsFor(key).toList())

    /**
     * One key of the Win Components string ("key=index,..."). A game keeps a components string of its
     * own only while it differs from the container's.
     */
    private fun wcRow(id: String, key: String, label: String, group: String, hint: String?, help: String?, options: List<String>) = SettingDef(
        id = id, category = CAT_COMPONENTS, group = group, level = ADVANCED,
        label = label, hint = hint, help = help, tech = "$WINCOMPONENTS › $key",
        control = { _, _ -> Control.Segmented(options.mapIndexed { i, name -> Opt("$i", name) }) },
        vm = VmBinding(
            get = { _, vm -> (vm.winComponents.firstOrNull { it.key == key }?.selectedIndex ?: 0).toString() },
            set = { _, vm, v ->
                val i = vm.winComponents.indexOfFirst { it.key == key }
                val index = v.toIntOrNull()
                if (i >= 0 && index != null) vm.winComponents[i] = vm.winComponents[i].copy(selectedIndex = index)
            },
        ),
        game = GameBinding(
            keys = listOf(WINCOMPONENTS),
            read = { e ->
                val own = wcIndex(e.get(WINCOMPONENTS), key)
                own?.takeIf { it != wcIndex(e.container.getWinComponents(), key) }?.toString()
            },
            write = { e, v -> v.toIntOrNull()?.let { e.put(WINCOMPONENTS, wcGameWith(e, key, it)) } },
            reset = { e ->
                if (e.get(WINCOMPONENTS) != null) e.put(WINCOMPONENTS, wcGameWith(e, key, wcIndex(e.container.getWinComponents(), key) ?: 0))
            },
        ),
        // Shown where the components list carries the key, as the classic Win Components tab shows it.
        visible = { env, _ ->
            env.vm?.winComponents?.any { it.key == key } == true ||
                wcIndex(env.shortcut?.getExtra(WINCOMPONENTS), key) != null
        },
    )
}
