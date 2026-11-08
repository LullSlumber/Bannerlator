package com.winlator.star.ui.deck.settings

import com.winlator.star.ui.simple.T
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.preference.PreferenceManager
import com.winlator.star.box64.Box64Preset
import com.winlator.star.container.Container
import com.winlator.star.contents.ContentsManager
import com.winlator.star.core.UpdateManager
import com.winlator.star.fexcore.FEXCorePreset
import com.winlator.star.inputcontrols.SteamControllerBackend
import com.winlator.star.midi.MidiManager
import com.winlator.star.perf.PerfGpuTurbo
import com.winlator.star.perf.PerfRevertRegistry
import com.winlator.star.perf.PerfRootApplier
import com.winlator.star.perf.PerformanceSettings
import com.winlator.star.perf.RootManager
import com.winlator.star.store.SteamFriendsStore
import com.winlator.star.store.SteamPrefs
import com.winlator.star.ui.components.GlobalControllerPrefs
import com.winlator.star.ui.deck.settings.SettingLevel.ADVANCED
import com.winlator.star.ui.deck.settings.SettingLevel.EXPERT
import com.winlator.star.ui.deck.settings.SettingLevel.SIMPLE
import com.winlator.star.ui.screens.contents.ComponentLibrary
import com.winlator.star.ui.screens.contents.RemoteSourceRepository
import java.io.File
import java.util.Locale

// App-wide settings for the Deck Settings tab. Each one reads and writes the same preference the
// classic screen that owns it uses (Settings, Performance, Input Controls › Device, the Save Manager,
// Contents, Steam Friends), with the same side effects, and applies the moment it changes (there is
// nothing to save). What is not ported (storage folders, logs, backups, Steam region, Lossless.dll,
// the menu button) is one hand-off away.

internal object AppSettingsRegistry {

    val categories: List<SettingsCategory> = listOf(
        SettingsCategory("app.general", T("General"), T("Start-up screen and updates."), Icons.Filled.Tune),
        SettingsCategory("app.appearance", T("Appearance"), T("Theme, accent colour, size and orientation."), Icons.Filled.Palette),
        SettingsCategory("app.library", T("外观"), T("主题、强调色、大小和方向。"), Icons.AutoMirrored.Filled.LibraryBooks),
        SettingsCategory("app.performance", T("Performance"), T("Power defaults for every game and translator presets."), Icons.Filled.Speed),
        SettingsCategory("app.network", T("Downloads & network"), T("Store download engines, speed and Steam connection."), Icons.Filled.CloudDownload),
        SettingsCategory("app.steam", T("Steam好友"), T("商店下载引擎、速度和 Steam 连接。"), Icons.Filled.Groups),
        SettingsCategory("app.saves", T("Steam好友"), T("好友和聊天、通知和游戏内在线状态。"), Icons.Filled.Save),
        SettingsCategory("app.contents", T("存档与云"), T("Steam、GOG 和您自己游戏的自动存档备份和云同步。"), Icons.Filled.Inventory2),
        SettingsCategory("app.storage", T("Storage"), T("Space used, app folders and the base system."), Icons.Filled.Storage),
        SettingsCategory("app.logs", T("Logs & diagnostics"), T("What gets recorded, where it goes, how long it's kept."), Icons.Filled.Description),
        SettingsCategory("app.backup", T("日志与诊断"), T("记录什么、保存在哪里、保留多久。"), Icons.Filled.Archive),
        SettingsCategory("app.controllers", T("备份与恢复"), T("所有应用数据、游戏存档和容器。"), Icons.Filled.Gamepad),
        SettingsCategory("app.about", T("控制器"), T("Steam 控制器、屏幕控制默认值，以及 Wine 游戏的鼠标和键盘。"), Icons.Filled.Info),
        SettingsCategory("app.advanced", T("Advanced"), T("Experimental switches and display server internals."), Icons.Filled.Science),
    )

    const val TARGET_CLASSIC = "classicSettings"
    const val TARGET_APPEARANCE = "appearance"
    const val TARGET_LOGS = "logs"
    const val TARGET_ABOUT = "about"
    const val TARGET_INPUT_CONTROLS = "inputControls"
    const val TARGET_DEFAULTS = "containerDefaults"
    const val TARGET_CONTENTS = "contents"

    val handoffs: Map<String, List<Handoff>> = mapOf(
        "app.general" to listOf(Handoff(T("检查更新"), T("Version, release notes and Download & install (classic Settings)."), TARGET_CLASSIC)),
        "app.appearance" to listOf(Handoff(T("主题和颜色"), T("主题预设、自定义强调色、侧边菜单、方向、界面和文字大小。"), TARGET_APPEARANCE)),
        "app.library" to listOf(Handoff(T("已安装的音色库和导出文件夹"), T("添加 .sf2 文件并选择快捷方式导出的位置（经典设置）。"), TARGET_CLASSIC)),
        "app.performance" to listOf(
            Handoff(T("Root 控制、温度安全和 Lossless.dll"), T("CPU 调度器和频率锁定、释放内存、温度看门狗（经典设置 › 性能），以及为帧生成导入 Lossless.dll。"), TARGET_CLASSIC),
            Handoff(T("编辑翻译器预设"), T("添加、复制、删除、导出或导入 Box64 和 FEX 预设（经典设置）。"), TARGET_CLASSIC),
            Handoff(T("新容器默认值"), T("新容器起始的“所有容器”配置文件。它先于上面的默认预设。"), TARGET_DEFAULTS),
        ),
        "app.network" to listOf(
            Handoff(T("下载引擎和速度"), T("原生 Steam 引擎、Rust Epic / GOG / Amazon 引擎和商店下载速度（日志管理器）。"), TARGET_LOGS),
            Handoff(T("Steam 连接区域和网络测试"), T("使用哪个 Steam 数据中心、测试区域，以及 SteamLite 网络检查（经典设置）。"), TARGET_CLASSIC),
        ),
        "app.storage" to listOf(Handoff(T("存储和文件夹"), T("应用数据文件夹、基础系统 (ImageFS) 和已用空间（经典设置）。"), TARGET_CLASSIC)),
        "app.logs" to listOf(Handoff(T("打开日志管理器"), T("选择记录什么（Wine、Box64/FEX、DXVK、SteamLite、无头 Steam、logcat）、日志保存在哪里以及保留多少；浏览并分享它们。"), TARGET_LOGS)),
        "app.backup" to listOf(Handoff(T("备份或恢复所有应用数据"), T("容器、设置和前缀在一个文件中（经典设置）。"), TARGET_CLASSIC)),
        "app.controllers" to listOf(Handoff(T("菜单按钮、额外按钮和陀螺仪校准"), T("打开游戏内侧边菜单的手柄按钮、Steam 控制器 L4/L5/R4/R5/QAM 按钮、玩家槽位固定和校准陀螺仪（输入控制 › 设备）。"), TARGET_INPUT_CONTROLS)),
        "app.contents" to listOf(Handoff(T("保存位置和仓库"), T("组件保存的位置、添加和移除仓库，以及 \"设为默认…\"（内容）。"), TARGET_CONTENTS)),
        "app.about" to listOf(Handoff(T("关于 Bannerlator"), T("版本、其驱动技术和鸣谢。"), TARGET_ABOUT)),
        "app.advanced" to listOf(Handoff(T("帧生成训练捕获"), T("贡献捕获和 win-fg 诊断日志（经典设置）。"), TARGET_CLASSIC)),
    )

    val defs: List<SettingDef> by lazy { buildDefs() }

    private fun prefs(c: Context) = PreferenceManager.getDefaultSharedPreferences(c)
    private fun onOff(b: Boolean) = if (b) "1" else "0"

    private fun prefBool(key: String, default: Boolean) = AppBinding(
        get = { c -> onOff(prefs(c).getBoolean(key, default)) },
        set = { c, v -> prefs(c).edit().putBoolean(key, v == "1").apply() },
    )

    private fun app(
        id: String, category: String, group: String, label: String, level: SettingLevel,
        hint: String? = null, tech: String? = null,
        control: (SettingsEnv) -> Control, binding: AppBinding,
    ) = SettingDef(
        id = id, category = category, group = group, label = label, hint = hint, tech = tech, level = level,
        control = { env, _ -> control(env) }, app = binding,
    )

    private fun toggle(id: String, category: String, group: String, label: String, level: SettingLevel, key: String, default: Boolean, hint: String? = null) =
        app(id, category, group, label, level, hint = hint, tech = key, control = { Control.Toggle }, binding = prefBool(key, default))

    private fun buildDefs(): List<SettingDef> = listOf(
        app(
            "app.general.landing", "app.general", T("Start-up"), T("Open the app on"), SIMPLE,
            hint = T("In the Deck interface, Games and Big Picture both open on Home."),
            tech = T("default_landing_screen · enable_big_picture_mode"),
            control = { Control.Segmented(listOf(Opt("games", T("游戏")), Opt("containers", T("容器管理")), Opt("bigpicture", T("容器管理")))) },
            binding = AppBinding(
                get = { c ->
                    val p = prefs(c)
                    if (p.getBoolean("enable_big_picture_mode", false)) "bigpicture"
                    else p.getString("default_landing_screen", "games") ?: "games"
                },
                set = { c, v ->
                    val e = prefs(c).edit()
                    if (v == "bigpicture") e.putBoolean("enable_big_picture_mode", true)
                    else e.putBoolean("enable_big_picture_mode", false).putString("default_landing_screen", v)
                    e.apply()
                },
            ),
        ),
        app(
            "app.general.updateNotify", "app.general", T("更新s"), T("打开应用时"), SIMPLE,
            hint = T("在 Deck 界面中，游戏和大屏幕模式都会在主页打开。"), tech = T("有更新时通知我"),
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(UpdateManager.isNotifyEnabled(c)) }, { c, v -> UpdateManager.setNotifyEnabled(c, v == "1") }),
        ),
        app(
            "app.general.updatePrerelease", "app.general", T("更新s"), T("Include beta builds"), ADVANCED,
            hint = T("Pre-releases may be less stable."), tech = T("Include pre-releases"),
            control = { Control.Toggle },
            // A channel switch re-checks at once, as classic Settings does: an unforced check would answer from the 6 h cache.
            binding = AppBinding(
                get = { c -> onOff(UpdateManager.isIncludePrereleases(c)) },
                set = { c, v ->
                    UpdateManager.setIncludePrereleases(c, v == "1")
                    UpdateManager.check(c, force = true) { }
                },
            ),
        ),

        app(
            "app.library.steamGridKey", "app.library", T("Cover art"), T("SteamGridDB API key"), ADVANCED,
            hint = T("Optional. Fetches cover art for games that aren't from a store. Leave empty to turn it off."),
            control = { Control.TextField(T("API key")) },
            binding = AppBinding(
                get = { c ->
                    val p = prefs(c)
                    if (p.getBoolean("enable_custom_api_key", false)) p.getString("custom_api_key", "") ?: "" else ""
                },
                set = { c, v ->
                    val key = v.trim()
                    val e = prefs(c).edit()
                    if (key.isEmpty()) e.putBoolean("enable_custom_api_key", false).remove("custom_api_key")
                    else e.putBoolean("enable_custom_api_key", true).putString("custom_api_key", key)
                    e.apply()
                },
            ),
        ),
        app(
            "app.library.midiDefault", "app.library", T("封面图"), T("SteamGridDB API 密钥"), ADVANCED,
            hint = T("可选。为非商店来源的游戏获取封面图。留空则关闭。"), tech = MidiManager.PREF_DEFAULT_SOUND_FONT,
            control = { env ->
                val files = MidiManager.getSoundFontDir(env.context).listFiles()
                    ?.map { it.name }?.filter { it.endsWith(".sf2") && it != MidiManager.DEFAULT_SF2_FILE }?.sorted() ?: emptyList()
                Control.Select(listOf(Opt("", T("Off")), Opt(MidiManager.DEFAULT_SF2_FILE, T("Built-in General MIDI bank"))) + files.map { Opt(it, it) })
            },
            binding = AppBinding(
                // A default that points at a removed SoundFont falls back to Off, as classic Settings shows it.
                get = { c ->
                    val name = prefs(c).getString(MidiManager.PREF_DEFAULT_SOUND_FONT, "") ?: ""
                    if (name.isEmpty() || name == MidiManager.DEFAULT_SF2_FILE || File(MidiManager.getSoundFontDir(c), name).isFile) name else ""
                },
                set = { c, v -> prefs(c).edit().putString(MidiManager.PREF_DEFAULT_SOUND_FONT, v).apply() },
            ),
        ),

        perf("app.performance.sustained", T("Sustained performance mode"), T("Keeps clocks steady for long sessions instead of short bursts."),
            { PerformanceSettings.sustainedPerfMode.value }, { PerformanceSettings.setSustainedPerfMode(it) }),
        perf("app.performance.threadBoost", T("Boost game thread priority"), null,
            { PerformanceSettings.perfPriorityBoost.value }, { PerformanceSettings.setPerfPriorityBoost(it) }),
        perf("app.performance.bigCores", T("提升游戏线程优先级"), null,
            { PerformanceSettings.preferBigCores.value }, { PerformanceSettings.setPreferBigCores(it) }),
        app(
            "app.performance.box64Preset", "app.performance", T("Translator presets"), T("Default Box64 preset"), ADVANCED,
            hint = T("Used by new containers."), tech = "box64_preset",
            control = { env -> Control.Select(env.box64Presets.map { Opt(it.id, it.name) }) },
            binding = AppBinding(
                get = { c -> prefs(c).getString("box64_preset", Box64Preset.COMPATIBILITY) ?: Box64Preset.COMPATIBILITY },
                set = { c, v -> prefs(c).edit().putString("box64_preset", v).apply() },
            ),
        ),
        app(
            "app.performance.fexPreset", "app.performance", T("Translator presets"), T("Default FEX preset"), ADVANCED,
            hint = T("Used by new containers."), tech = "fexcore_preset",
            control = { env -> Control.Select(env.fexPresets.map { Opt(it.id, it.name) }) },
            binding = AppBinding(
                get = { c -> prefs(c).getString("fexcore_preset", FEXCorePreset.COMPATIBILITY) ?: FEXCorePreset.COMPATIBILITY },
                set = { c, v -> prefs(c).edit().putString("fexcore_preset", v).apply() },
            ),
        ),

        app(
            "app.controllers.cursorSpeed", "app.controllers", T("Mouse & keyboard (Wine games)"), T("Cursor speed"), ADVANCED, tech = "cursor_speed",
            control = { Control.Slider(0.1f, 2.0f, 0.05f, { "${(it * 100).toInt()}%" }, { String.format(Locale.ENGLISH, "%.2f", it) }) },
            binding = AppBinding(
                get = { c -> String.format(Locale.ENGLISH, "%.2f", prefs(c).getFloat("cursor_speed", 1.0f)) },
                set = { c, v -> v.toFloatOrNull()?.let { prefs(c).edit().putFloat("cursor_speed", it).apply() } },
            ),
        ),
        toggle("app.controllers.trueMouse", "app.controllers", T("Mouse & keyboard (Wine games)"), T("True mouse control"), ADVANCED, "cursor_lock", false,
            hint = T("Locks the pointer to the game. Press Volume Down to release.")),
        toggle("app.controllers.disableXinput", "app.controllers", T("Mouse & keyboard (Wine games)"), T("Exclusive mouse & keyboard"), ADVANCED, "xinput_toggle", false,
            hint = T("Turns off XInput so games see only the real mouse and keyboard.")),

        toggle("app.advanced.fileProvider", "app.advanced", T("实验性模式"), T("Share files with other apps (File Provider)"), ADVANCED, "enable_file_provider", true,
            hint = T("Takes effect after the app restarts.")),
        toggle("app.advanced.androidBrowser", "app.advanced", T("实验性模式"), T("Open links in the Android browser"), ADVANCED, "open_with_android_browser", false),
        toggle("app.advanced.clipboard", "app.advanced", T("实验性模式"), T("Share the Android clipboard with games"), ADVANCED, "share_android_clipboard", false),
        app(
            "app.advanced.contentsUrl", "app.advanced", T("实验性模式"), T("Components catalog URL"), EXPERT,
            hint = T("Where the Components screen looks for downloads."), tech = "downloadable_contents_url",
            control = { Control.TextField(ContentsManager.REMOTE_PROFILES) { if (it.isBlank() || it.trim().startsWith("http")) null else T("Use an http(s) address.") } },
            binding = AppBinding(
                get = { c -> prefs(c).getString("downloadable_contents_url", ContentsManager.REMOTE_PROFILES) ?: ContentsManager.REMOTE_PROFILES },
                set = { c, v -> prefs(c).edit().putString("downloadable_contents_url", v.trim().ifEmpty { ContentsManager.REMOTE_PROFILES }).apply() },
            ),
        ),
        toggle("app.advanced.dri3", "app.advanced", T("Display server (X11)"), T("Use DRI3"), ADVANCED, "use_dri3", true),
        toggle("app.advanced.xr", "app.advanced", T("Display server (X11)"), T("Allow XR / VR headsets"), ADVANCED, "use_xr", true),

        // The FPS saver (XServerDisplayActivity.saveSessionFps): counts frames with the overlay off and keeps each session's FPS.
        toggle("app.performance.saveSessionFps", "app.performance", T("FPS history"), T("Save FPS of each session"), SIMPLE, "save_session_fps", true),
        // The GPU pin is a non-root default on Adreno (KGSL turbo); elsewhere it needs root, as in classic Settings › Performance.
        SettingDef(
            id = "app.performance.gpuClockLock", category = "app.performance", group = T("Power (defaults for every game)"),
            label = T("锁定 GPU 最高频率"), level = ADVANCED, tech = "global_${PerfRootApplier.KEY_GPU_CLOCK_LOCK}",
            hint = T("Holds the GPU at its top clock while a game runs. Hotter and hungrier; games can override it."),
            control = { _, _ -> Control.Toggle },
            app = AppBinding(
                get = { _ -> onOff(PerformanceSettings.rootDefaultValue(PerfRootApplier.KEY_GPU_CLOCK_LOCK)) },
                set = { _, v ->
                    PerformanceSettings.setRootDefault(PerfRootApplier.KEY_GPU_CLOCK_LOCK, v == "1")
                    PerfRootApplier.apply(PerfRootApplier.KEY_GPU_CLOCK_LOCK, v == "1")
                },
            ),
            disabled = { _, _ ->
                val key = PerfRootApplier.KEY_GPU_CLOCK_LOCK
                when {
                    PerfRootApplier.isHarnessGated(key) && !PerfRevertRegistry.harnessProven.value -> T("正在等待此设备通过安全检查。")
                    RootManager.isGranted || PerfGpuTurbo.isSupported -> null
                    else -> T("需要 Adreno (Qualcomm) GPU，或其他 GPU 上的 root 权限。")
                }
            },
        ),

        // Steam friends (the same switches as the Friends screen's settings and classic Settings › Steam).
        app(
            "app.steam.social", "app.steam", T("Friends & chat"), T("Steam好友与聊天"), SIMPLE,
            hint = T("Your friends list, presence and chat. Off: no online status is shared."), tech = "steam_social_enabled",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(SteamPrefs.isSocialEnabled(c)) }, { c, v -> SteamFriendsStore.setSocialEnabled(c, v == "1") }),
        ),
        app(
            "app.steam.chatNotifications", "app.steam", T("Friends & chat"), T("聊天通知"), SIMPLE,
            hint = T("A notification when a friend messages you and their chat isn't open."), tech = "steam_chat_notifications",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(SteamPrefs.isChatNotificationsEnabled(c)) }, { c, v -> SteamPrefs.setChatNotificationsEnabled(c, v == "1") }),
        ),
        SettingDef(
            id = "app.steam.chatSound", category = "app.steam", group = T("Friends & chat"), label = T("通知声音"), level = ADVANCED,
            hint = T("A sound and a heads-up for new messages."), tech = "steam_chat_sound",
            control = { _, _ -> Control.Toggle },
            app = AppBinding({ c -> onOff(SteamPrefs.isChatSoundEnabled(c)) }, { c, v -> SteamPrefs.setChatSoundEnabled(c, v == "1") }),
            disabled = { _, values ->
                if (values("app.steam.social") == "1" && values("app.steam.chatNotifications") == "1") null else T("Needs friends & chat and chat notifications on.")
            },
        ),
        app(
            "app.steam.offlinePresence", "app.steam", T("Presence"), T("Show me as in-game for offline launches"), ADVANCED,
            hint = T("When a Steam game starts with Goldberg or Raw .exe (Wine games), tell Steam you're playing it so friends see it and playtime counts. SteamLite launches always show."),
            tech = "steam_offline_presence",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(SteamPrefs.isOfflinePresenceEnabled(c)) }, { c, v -> SteamPrefs.setOfflinePresenceEnabled(c, v == "1") }),
        ),

        // Save Manager › Settings: turning a backup off asks first, as there.
        saveToggle("app.saves.steamCollect", T("Steam games"), T("Auto-collect on exit"), T("游戏退出时将 Steam 库存档快照保存到您的本地库。"),
            "auto_collect_steam_on_exit",
            T("对于您的 Steam 库游戏，退出时自动备份存档将处于关闭状态。游戏关闭时不会捕获其存档——您需要通过容器的备份选项或存档管理器自行备份。继续？")),
        saveToggle("app.saves.steamUpload", T("Steam games"), T("Auto-upload to Steam Cloud on exit"), T("Only after you accept the third-party cloud disclaimer; never deletes anything from the cloud."),
            "auto_upload_steam_on_exit",
            T("您的Steam库游戏退出时自动云端上传将关闭。游戏关闭时存档不会推送到Steam云——您需要从游戏详情页的云存档部分自行上传。继续？")),
        saveToggle("app.saves.steamDownload", T("Steam games"), T("Auto-download from Steam Cloud on launch"), T("Newest wins: never overwrites a newer local save."),
            "auto_download_steam_on_launch",
            T("您的Steam库游戏启动时自动云端下载将关闭。游戏启动前不会拉取最新云存档——您需要从游戏详情页的云存档部分自行下载。继续？")),
        // Not a saveToggle: that asks "Turn off auto-backup?", and this switch leaves local backups alone.
        SettingDef(
            id = "app.saves.goldbergCloud", category = "app.saves", group = T("Steam games"),
            label = T("启动时自动从 Steam 云下载"), level = ADVANCED, tech = "goldberg_cloud_sync",
            hint = T("最新优先：永远不会覆盖较新的本地存档。"),
            control = { _, _ -> Control.Toggle },
            app = AppBinding(
                get = { c -> onOff(saveManagerPrefs(c).getBoolean("goldberg_cloud_sync", true)) },
                set = { c, v -> saveManagerPrefs(c).edit().putBoolean("goldberg_cloud_sync", v == "1").apply() },
            ),
        ),
        saveToggle("app.saves.gogUpload",T("GOG games"), T("Auto-upload to GOG cloud on exit"), T("Newest wins: never overwrites a newer cloud save."),
            "auto_upload_gog_on_exit",
            T("您的GOG库游戏的退出时自动云上传将关闭。游戏关闭时不会将其存档推送到GOG云 — 您需要从GOG存档管理器选项卡或游戏详情页面自行上传。继续？")),
        saveToggle("app.saves.gogDownload", T("GOG games"), T("Auto-download from GOG cloud on launch"), T("Newest wins: never overwrites a newer local save."),
            "auto_download_gog_on_launch",
            T("您的GOG库游戏的启动时自动云下载将关闭。游戏启动前不会拉取最新的云存档 — 您需要从GOG存档管理器选项卡或游戏详情页面自行下载。继续？")),
        saveToggle("app.saves.customBackup", T("启动时自动从 GOG 云下载"), T("最新优先：永远不会覆盖较新的本地存档。"), T("游戏退出时将自行导入的存档快照保存到本地保险库。"),
            "auto_backup_custom_on_exit",
            T("对于您自行导入的游戏，退出时自动备份存档将处于关闭状态。游戏关闭时不会捕获其存档——您需要通过容器的备份选项或游戏的 ⋮ 菜单 →“备份存档”自行备份。继续？")),
        // Writes to the real Steam account, so turning it ON is the guarded step.
        SettingDef(
            id = "app.saves.achievementSync", category = "app.saves", group = T("Steam achievements"),
            label = T("Sync unlocks to your Steam profile"), level = ADVANCED, tech = "achievement_syncback_enabled",
            hint = T("Achievements you unlock in-game are written back to your real Steam account. Off: unlocks stay on this device."),
            control = { _, _ -> Control.Toggle },
            app = AppBinding({ c -> onOff(SteamPrefs.isAchievementSyncBackEnabled(c)) }, { c, v -> SteamPrefs.setAchievementSyncBackEnabled(c, v == "1") }),
            confirm = { v ->
                if (v == "1") Confirm(
                    T("将成就同步到Steam？"),
                    T("开启此功能会将您在游戏中解锁的成就写回您的真实Steam账号和个人资料。这是第三方同步——在Steam上无法撤销，并且可能与官方Steam记录不匹配。保持关闭可将解锁保留在此设备本地。仍然开启？"),
                    T("开启"),
                ) else null
            },
        ),

        // Contents › Contents settings
        app(
            "app.contents.keepRaw", "app.contents", T("Downloads"), T("默认保留原始压缩包"), ADVANCED,
            hint = T("Save every install's download to My Files."), tech = "keep_raw",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(ComponentLibrary(c).keepRaw()) }, { c, v -> ComponentLibrary(c).setKeepRaw(v == "1") }),
        ),
        app(
            "app.contents.showOfficial", "app.contents", T("Repositories"), T("在仓库中显示官方目录"), ADVANCED,
            hint = T("Off hides it in Contents; the container and game sheets keep it."), tech = "show_official",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(RemoteSourceRepository(c).showOfficial()) }, { c, v -> RemoteSourceRepository(c).setShowOfficial(v == "1") }),
        ),

        // Input Controls › Device and › Assign
        app(
            "app.controllers.steamController", "app.controllers", T("Steam Controller"), T("Steam控制器支持"), SIMPLE,
            hint = T("Reads a Steam Controller directly (its trackpads and extra buttons). Off: the normal input path is unchanged."), tech = "steam_controller_sdl_enabled",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(GlobalControllerPrefs.isSteamControllerEnabled(c)) }, { c, v -> GlobalControllerPrefs.setSteamControllerEnabled(c, v == "1") }),
        ),
        SettingDef(
            id = "app.controllers.trackpadMouse", category = "app.controllers", group = T("Steam Controller"), label = T("触控板鼠标"), level = ADVANCED,
            hint = T("Which trackpad moves the mouse. Clicking it is a left click."), tech = "steam_controller_trackpad_mode",
            control = { _, _ ->
                Control.Segmented(listOf(
                    Opt("${SteamControllerBackend.TRACKPAD_MOUSE_RIGHT}", T("Right")),
                    Opt("${SteamControllerBackend.TRACKPAD_MOUSE_LEFT}", T("Left")),
                    Opt("${SteamControllerBackend.TRACKPAD_MOUSE_BOTH}", T("Both")),
                    Opt("${SteamControllerBackend.TRACKPAD_MOUSE_OFF}", T("Off")),
                ))
            },
            app = AppBinding(
                get = { c -> GlobalControllerPrefs.getSteamTrackpadMouseMode(c).toString() },
                set = { c, v -> v.toIntOrNull()?.let { GlobalControllerPrefs.setSteamTrackpadMouseMode(c, it) } },
            ),
            visible = { _, values -> values("app.controllers.steamController") == "1" },
        ),
        app(
            "app.controllers.onScreenPriority", "app.controllers", T("New containers start with"), T("On-screen controls vs. a real pad"), ADVANCED,
            hint = T("Copied into each new container; existing containers keep their own."), tech = "global_on_screen_controller_mode",
            control = {
                Control.Segmented(listOf(
                    Opt("${Container.ON_SCREEN_MODE_KEEP}", T("On-screen stays Player 1")),
                    Opt("${Container.ON_SCREEN_MODE_YIELD}", T("屏幕控制保持为玩家 1")),
                    Opt("${Container.ON_SCREEN_MODE_SHARE}", T("手柄成为玩家 1")),
                ))
            },
            binding = AppBinding(
                get = { c -> GlobalControllerPrefs.getOnScreenMode(c).toString() },
                set = { c, v -> v.toIntOrNull()?.let { GlobalControllerPrefs.setOnScreenMode(c, it) } },
            ),
        ),
        app(
            "app.controllers.autoHide", "app.controllers", T("New containers start with"), T("当控制器连接时隐藏屏幕控制"), ADVANCED,
            hint = T("Copied into each new container; existing containers keep their own."), tech = "global_auto_hide_controls_on_pad",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(GlobalControllerPrefs.getAutoHideControlsOnPad(c)) }, { c, v -> GlobalControllerPrefs.setAutoHideControlsOnPad(c, v == "1") }),
        ),
    )

    /** A Save Manager auto-sync switch (default on). Turning it off asks first with the Save Manager's own warning. */
    private fun saveToggle(id: String, group: String, label: String, hint: String, key: String, offWarning: String) = SettingDef(
        id = id, category = "app.saves", group = group, label = label, hint = hint, tech = key, level = ADVANCED,
        control = { _, _ -> Control.Toggle },
        app = AppBinding(
            get = { c -> onOff(saveManagerPrefs(c).getBoolean(key, true)) },
            set = { c, v -> saveManagerPrefs(c).edit().putBoolean(key, v == "1").apply() },
        ),
        confirm = { v -> if (v == "0") Confirm(T("Turn off auto-backup?"), offWarning, T("Continue")) else null },
    )

    private fun saveManagerPrefs(c: Context) = c.getSharedPreferences("save_manager_prefs", Context.MODE_PRIVATE)

    /** An app-wide power default. Games can override it in their own editor (Performance › Power). */
    private fun perf(id: String, label: String, hint: String?, get: () -> Boolean, set: (Boolean) -> Unit) = app(
        id, "app.performance", T("Power (defaults for every game)"), label, ADVANCED, hint = hint,
        control = { Control.Toggle },
        binding = AppBinding(get = { _ -> onOff(get()) }, set = { _, v -> set(v == "1") }),
    )
}
