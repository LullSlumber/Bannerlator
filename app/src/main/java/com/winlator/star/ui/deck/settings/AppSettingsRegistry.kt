package com.winlator.star.ui.deck.settings

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
        SettingsCategory("app.general", "General", "启动屏幕和更新。", Icons.Filled.Tune),
        SettingsCategory("app.appearance", "外观", "主题、强调色、大小和方向。", Icons.Filled.Palette),
        SettingsCategory("app.library", "游戏与库", "封面图和默认 MIDI 音色库。", Icons.AutoMirrored.Filled.LibraryBooks),
        SettingsCategory("app.performance", "Performance", "每个游戏的电源默认值和翻译器预设。", Icons.Filled.Speed),
        SettingsCategory("app.network", "下载与网络", "商店下载引擎、速度和 Steam 连接。", Icons.Filled.CloudDownload),
        SettingsCategory("app.steam", "Steam好友", "好友和聊天、通知和游戏内在线状态。", Icons.Filled.Groups),
        SettingsCategory("app.saves", "存档与云", "Steam、GOG 和您自己游戏的自动存档备份和云同步。", Icons.Filled.Save),
        SettingsCategory("app.contents", "内容管理", "已下载的组件：压缩包和目录列表。", Icons.Filled.Inventory2),
        SettingsCategory("app.storage", "Storage", "已用空间、应用文件夹和基础系统。", Icons.Filled.Storage),
        SettingsCategory("app.logs", "日志与诊断", "记录什么、保存在哪里、保留多久。", Icons.Filled.Description),
        SettingsCategory("app.backup", "备份与恢复", "所有应用数据、游戏存档和容器。", Icons.Filled.Archive),
        SettingsCategory("app.controllers", "控制器", "Steam 控制器、屏幕控制默认值，以及 Wine 游戏的鼠标和键盘。", Icons.Filled.Gamepad),
        SettingsCategory("app.about", "关于与帮助", "版本、鸣谢、帮助和社区链接。", Icons.Filled.Info),
        SettingsCategory("app.advanced", "Advanced", "实验性开关和显示服务器内部设置。", Icons.Filled.Science),
    )

    const val TARGET_CLASSIC = "classicSettings"
    const val TARGET_APPEARANCE = "appearance"
    const val TARGET_LOGS = "logs"
    const val TARGET_ABOUT = "about"
    const val TARGET_INPUT_CONTROLS = "inputControls"
    const val TARGET_DEFAULTS = "containerDefaults"
    const val TARGET_CONTENTS = "contents"

    val handoffs: Map<String, List<Handoff>> = mapOf(
        "app.general" to listOf(Handoff("检查更新", "版本、发行说明和下载与安装（经典设置）。", TARGET_CLASSIC)),
        "app.appearance" to listOf(Handoff("主题和颜色", "主题预设、自定义强调色、侧边菜单、方向、界面和文字大小。", TARGET_APPEARANCE)),
        "app.library" to listOf(Handoff("已安装的音色库和导出文件夹", "添加 .sf2 文件并选择快捷方式导出的位置（经典设置）。", TARGET_CLASSIC)),
        "app.performance" to listOf(
            Handoff("Root 控制、温度安全和 Lossless.dll", "CPU 调度器和频率锁定、释放内存、温度看门狗（经典设置 › 性能），以及为帧生成导入 Lossless.dll。", TARGET_CLASSIC),
            Handoff("编辑翻译器预设", "添加、复制、删除、导出或导入 Box64 和 FEX 预设（经典设置）。", TARGET_CLASSIC),
            Handoff("新容器默认值", "新容器起始的“所有容器”配置文件。它先于上面的默认预设。", TARGET_DEFAULTS),
        ),
        "app.network" to listOf(
            Handoff("下载引擎和速度", "原生 Steam 引擎、Rust Epic / GOG / Amazon 引擎和商店下载速度（日志管理器）。", TARGET_LOGS),
            Handoff("Steam 连接区域和网络测试", "使用哪个 Steam 数据中心、测试区域，以及 SteamLite 网络检查（经典设置）。", TARGET_CLASSIC),
        ),
        "app.storage" to listOf(Handoff("存储和文件夹", "应用数据文件夹、基础系统 (ImageFS) 和已用空间（经典设置）。", TARGET_CLASSIC)),
        "app.logs" to listOf(Handoff("打开日志管理器", "选择记录什么（Wine、Box64/FEX、DXVK、SteamLite、无头 Steam、logcat）、日志保存在哪里以及保留多少；浏览并分享它们。", TARGET_LOGS)),
        "app.backup" to listOf(Handoff("备份或恢复所有应用数据", "容器、设置和前缀在一个文件中（经典设置）。", TARGET_CLASSIC)),
        "app.controllers" to listOf(Handoff("菜单按钮、额外按钮和陀螺仪校准", "打开游戏内侧边菜单的手柄按钮、Steam 控制器 L4/L5/R4/R5/QAM 按钮、玩家槽位固定和校准陀螺仪（输入控制 › 设备）。", TARGET_INPUT_CONTROLS)),
        "app.contents" to listOf(Handoff("保存位置和仓库", "组件保存的位置、添加和移除仓库，以及 \"设为默认…\"（内容）。", TARGET_CONTENTS)),
        "app.about" to listOf(Handoff("关于 Bannerlator", "版本、其驱动技术和鸣谢。", TARGET_ABOUT)),
        "app.advanced" to listOf(Handoff("帧生成训练捕获", "贡献捕获和 win-fg 诊断日志（经典设置）。", TARGET_CLASSIC)),
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
            "app.general.landing", "app.general", "启动", "打开应用时", SIMPLE,
            hint = "在 Deck 界面中，游戏和大屏幕模式都会在主页打开。",
            tech = "default_landing_screen · enable_big_picture_mode",
            control = { Control.Segmented(listOf(Opt("games", "游戏"), Opt("containers", "容器管理"), Opt("bigpicture", "大屏幕模式"))) },
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
            "app.general.updateNotify", "app.general", "Updates", "告知我新版本", SIMPLE,
            hint = "新版本发布时显示更新通知。", tech = "有更新时通知我",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(UpdateManager.isNotifyEnabled(c)) }, { c, v -> UpdateManager.setNotifyEnabled(c, v == "1") }),
        ),
        app(
            "app.general.updatePrerelease", "app.general", "Updates", "包含测试版构建", ADVANCED,
            hint = "预发布版本可能不太稳定。", tech = "包含预发布版本",
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
            "app.library.steamGridKey", "app.library", "封面图", "SteamGridDB API 密钥", ADVANCED,
            hint = "可选。为非商店来源的游戏获取封面图。留空则关闭。",
            control = { Control.TextField("API key") },
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
            "app.library.midiDefault", "app.library", "Sound (MIDI)", "默认 MIDI 音色库", ADVANCED,
            hint = "供新容器使用。每个容器可以在音频下选择自己的。", tech = MidiManager.PREF_DEFAULT_SOUND_FONT,
            control = { env ->
                val files = MidiManager.getSoundFontDir(env.context).listFiles()
                    ?.map { it.name }?.filter { it.endsWith(".sf2") && it != MidiManager.DEFAULT_SF2_FILE }?.sorted() ?: emptyList()
                Control.Select(listOf(Opt("", "Off"), Opt(MidiManager.DEFAULT_SF2_FILE, "Built-in General MIDI bank")) + files.map { Opt(it, it) })
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

        perf("app.performance.sustained", "持续性能模式", "为长时间会话保持时钟稳定，而不是短时爆发。",
            { PerformanceSettings.sustainedPerfMode.value }, { PerformanceSettings.setSustainedPerfMode(it) }),
        perf("app.performance.threadBoost", "提升游戏线程优先级", null,
            { PerformanceSettings.perfPriorityBoost.value }, { PerformanceSettings.setPerfPriorityBoost(it) }),
        perf("app.performance.bigCores", "优先使用大核", null,
            { PerformanceSettings.preferBigCores.value }, { PerformanceSettings.setPreferBigCores(it) }),
        app(
            "app.performance.box64Preset", "app.performance", "翻译器预设", "默认 Box64 预设", ADVANCED,
            hint = "供新容器使用。", tech = "box64_preset",
            control = { env -> Control.Select(env.box64Presets.map { Opt(it.id, it.name) }) },
            binding = AppBinding(
                get = { c -> prefs(c).getString("box64_preset", Box64Preset.COMPATIBILITY) ?: Box64Preset.COMPATIBILITY },
                set = { c, v -> prefs(c).edit().putString("box64_preset", v).apply() },
            ),
        ),
        app(
            "app.performance.fexPreset", "app.performance", "翻译器预设", "默认 FEX 预设", ADVANCED,
            hint = "供新容器使用。", tech = "fexcore_preset",
            control = { env -> Control.Select(env.fexPresets.map { Opt(it.id, it.name) }) },
            binding = AppBinding(
                get = { c -> prefs(c).getString("fexcore_preset", FEXCorePreset.COMPATIBILITY) ?: FEXCorePreset.COMPATIBILITY },
                set = { c, v -> prefs(c).edit().putString("fexcore_preset", v).apply() },
            ),
        ),

        app(
            "app.controllers.cursorSpeed", "app.controllers", "鼠标与键盘（Wine 游戏）", "光标速度", ADVANCED, tech = "cursor_speed",
            control = { Control.Slider(0.1f, 2.0f, 0.05f, { "${(it * 100).toInt()}%" }, { String.format(Locale.ENGLISH, "%.2f", it) }) },
            binding = AppBinding(
                get = { c -> String.format(Locale.ENGLISH, "%.2f", prefs(c).getFloat("cursor_speed", 1.0f)) },
                set = { c, v -> v.toFloatOrNull()?.let { prefs(c).edit().putFloat("cursor_speed", it).apply() } },
            ),
        ),
        toggle("app.controllers.trueMouse", "app.controllers", "鼠标与键盘（Wine 游戏）", "真实鼠标控制", ADVANCED, "cursor_lock", false,
            hint = "将指针锁定到游戏。按下音量减键释放。"),
        toggle("app.controllers.disableXinput", "app.controllers", "鼠标与键盘（Wine 游戏）", "独占鼠标与键盘", ADVANCED, "xinput_toggle", false,
            hint = "关闭 XInput，使游戏只看到真实的鼠标和键盘。"),

        toggle("app.advanced.fileProvider", "app.advanced", "实验性模式", "与其他应用共享文件 (File Provider)", ADVANCED, "enable_file_provider", true,
            hint = "应用重启后生效。"),
        toggle("app.advanced.androidBrowser", "app.advanced", "实验性模式", "在 Android 浏览器中打开链接", ADVANCED, "open_with_android_browser", false),
        toggle("app.advanced.clipboard", "app.advanced", "实验性模式", "与游戏共享 Android 剪贴板", ADVANCED, "share_android_clipboard", false),
        app(
            "app.advanced.contentsUrl", "app.advanced", "实验性模式", "组件目录 URL", EXPERT,
            hint = "组件屏幕查找下载的位置。", tech = "downloadable_contents_url",
            control = { Control.TextField(ContentsManager.REMOTE_PROFILES) { if (it.isBlank() || it.trim().startsWith("http")) null else "Use an http(s) address." } },
            binding = AppBinding(
                get = { c -> prefs(c).getString("downloadable_contents_url", ContentsManager.REMOTE_PROFILES) ?: ContentsManager.REMOTE_PROFILES },
                set = { c, v -> prefs(c).edit().putString("downloadable_contents_url", v.trim().ifEmpty { ContentsManager.REMOTE_PROFILES }).apply() },
            ),
        ),
        toggle("app.advanced.dri3", "app.advanced", "显示服务器 (X11)", "使用 DRI3", ADVANCED, "use_dri3", true),
        toggle("app.advanced.xr", "app.advanced", "显示服务器 (X11)", "允许 XR / VR 头显", ADVANCED, "use_xr", true),

        // The GPU pin is a non-root default on Adreno (KGSL turbo); elsewhere it needs root, as in classic Settings › Performance.
        SettingDef(
            id = "app.performance.gpuClockLock", category = "app.performance", group = "电源（每个游戏的默认值）",
            label = "锁定 GPU 最高频率", level = ADVANCED, tech = "global_${PerfRootApplier.KEY_GPU_CLOCK_LOCK}",
            hint = "在游戏运行时将 GPU 保持在最高频率。更热、更耗电；游戏可以覆盖它。",
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
                    PerfRootApplier.isHarnessGated(key) && !PerfRevertRegistry.harnessProven.value -> "正在等待此设备通过安全检查。"
                    RootManager.isGranted || PerfGpuTurbo.isSupported -> null
                    else -> "需要 Adreno (Qualcomm) GPU，或其他 GPU 上的 root 权限。"
                }
            },
        ),

        // Steam friends (the same switches as the Friends screen's settings and classic Settings › Steam).
        app(
            "app.steam.social", "app.steam", "好友与聊天", "Steam好友与聊天", SIMPLE,
            hint = "您的好友列表、在线状态和聊天。关闭：不共享在线状态。", tech = "steam_social_enabled",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(SteamPrefs.isSocialEnabled(c)) }, { c, v -> SteamFriendsStore.setSocialEnabled(c, v == "1") }),
        ),
        app(
            "app.steam.chatNotifications", "app.steam", "好友与聊天", "聊天通知", SIMPLE,
            hint = "当好友给您发消息且其聊天未打开时显示通知。", tech = "steam_chat_notifications",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(SteamPrefs.isChatNotificationsEnabled(c)) }, { c, v -> SteamPrefs.setChatNotificationsEnabled(c, v == "1") }),
        ),
        SettingDef(
            id = "app.steam.chatSound", category = "app.steam", group = "好友与聊天", label = "通知声音", level = ADVANCED,
            hint = "新消息的声音和提示。", tech = "steam_chat_sound",
            control = { _, _ -> Control.Toggle },
            app = AppBinding({ c -> onOff(SteamPrefs.isChatSoundEnabled(c)) }, { c, v -> SteamPrefs.setChatSoundEnabled(c, v == "1") }),
            disabled = { _, values ->
                if (values("app.steam.social") == "1" && values("app.steam.chatNotifications") == "1") null else "Needs friends & chat and chat notifications on."
            },
        ),
        app(
            "app.steam.offlinePresence", "app.steam", "在线状态", "Show me as in-game for offline launches", ADVANCED,
            hint = "当 Steam 游戏使用 Goldberg 或 Raw .exe（Wine 游戏）启动时，告诉 Steam 您正在玩它，以便好友看到且游戏时间会计入。SteamLite 启动始终显示。",
            tech = "steam_offline_presence",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(SteamPrefs.isOfflinePresenceEnabled(c)) }, { c, v -> SteamPrefs.setOfflinePresenceEnabled(c, v == "1") }),
        ),

        // Save Manager › Settings: turning a backup off asks first, as there.
        saveToggle("app.saves.steamCollect", "Steam 游戏", "退出时自动收集", "游戏退出时将 Steam 库存档快照保存到您的本地库。",
            "auto_collect_steam_on_exit",
            "对于您的 Steam 库游戏，退出时自动备份存档将处于关闭状态。游戏关闭时不会捕获其存档——您需要通过容器的备份选项或存档管理器自行备份。继续？"),
        saveToggle("app.saves.steamUpload", "Steam 游戏", "退出时自动上传到 Steam 云", "仅在您接受第三方云免责声明后；永远不会从云中删除任何内容。",
            "auto_upload_steam_on_exit",
            "您的Steam库游戏退出时自动云端上传将关闭。游戏关闭时存档不会推送到Steam云——您需要从游戏详情页的云存档部分自行上传。继续？"),
        saveToggle("app.saves.steamDownload", "Steam 游戏", "启动时自动从 Steam 云下载", "最新优先：永远不会覆盖较新的本地存档。",
            "auto_download_steam_on_launch",
            "您的Steam库游戏启动时自动云端下载将关闭。游戏启动前不会拉取最新云存档——您需要从游戏详情页的云存档部分自行下载。继续？"),
        // Not a saveToggle: that asks "Turn off auto-backup?", and this switch leaves local backups alone.
        SettingDef(
            id = "app.saves.goldbergCloud", category = "app.saves", group = "Steam 游戏",
            label = "在 Goldberg 启动期间与 Steam 云同步", level = ADVANCED, tech = "goldberg_cloud_sync",
            hint = "关闭：Goldberg 完全离线，不进行云拉取、推送或成就上传。开启：Goldberg 启动也会执行这些操作。",
            control = { _, _ -> Control.Toggle },
            app = AppBinding(
                get = { c -> onOff(saveManagerPrefs(c).getBoolean("goldberg_cloud_sync", true)) },
                set = { c, v -> saveManagerPrefs(c).edit().putBoolean("goldberg_cloud_sync", v == "1").apply() },
            ),
        ),
        saveToggle("app.saves.gogUpload","GOG 游戏", "退出时自动上传到 GOG 云", "最新优先：永远不会覆盖较新的云存档。",
            "auto_upload_gog_on_exit",
            "您的GOG库游戏的退出时自动云上传将关闭。游戏关闭时不会将其存档推送到GOG云 — 您需要从GOG存档管理器选项卡或游戏详情页面自行上传。继续？"),
        saveToggle("app.saves.gogDownload", "GOG 游戏", "启动时自动从 GOG 云下载", "最新优先：永远不会覆盖较新的本地存档。",
            "auto_download_gog_on_launch",
            "您的GOG库游戏的启动时自动云下载将关闭。游戏启动前不会拉取最新的云存档 — 您需要从GOG存档管理器选项卡或游戏详情页面自行下载。继续？"),
        saveToggle("app.saves.customBackup", "您自己的游戏", "退出时自动备份", "游戏退出时将自行导入的存档快照保存到本地保险库。",
            "auto_backup_custom_on_exit",
            "对于您自行导入的游戏，退出时自动备份存档将处于关闭状态。游戏关闭时不会捕获其存档——您需要通过容器的备份选项或游戏的 ⋮ 菜单 →“备份存档”自行备份。继续？"),
        // Writes to the real Steam account, so turning it ON is the guarded step.
        SettingDef(
            id = "app.saves.achievementSync", category = "app.saves", group = "Steam 成就",
            label = "将解锁同步到您的 Steam 个人资料", level = ADVANCED, tech = "achievement_syncback_enabled",
            hint = "您在游戏中解锁的成就会写回您真正的 Steam 账户。关闭：解锁保留在此设备上。",
            control = { _, _ -> Control.Toggle },
            app = AppBinding({ c -> onOff(SteamPrefs.isAchievementSyncBackEnabled(c)) }, { c, v -> SteamPrefs.setAchievementSyncBackEnabled(c, v == "1") }),
            confirm = { v ->
                if (v == "1") Confirm(
                    "将成就同步到Steam？",
                    "开启此功能会将您在游戏中解锁的成就写回您的真实Steam账号和个人资料。这是第三方同步——在Steam上无法撤销，并且可能与官方Steam记录不匹配。保持关闭可将解锁保留在此设备本地。仍然开启？",
                    "开启",
                ) else null
            },
        ),

        // Contents › Contents settings
        app(
            "app.contents.keepRaw", "app.contents", "Downloads", "默认保留原始压缩包", ADVANCED,
            hint = "将每次安装的下载保存到我的文件。", tech = "keep_raw",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(ComponentLibrary(c).keepRaw()) }, { c, v -> ComponentLibrary(c).setKeepRaw(v == "1") }),
        ),
        app(
            "app.contents.showOfficial", "app.contents", "仓库", "在仓库中显示官方目录", ADVANCED,
            hint = "关闭会在内容中隐藏它；容器和游戏面板会保留它。", tech = "show_official",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(RemoteSourceRepository(c).showOfficial()) }, { c, v -> RemoteSourceRepository(c).setShowOfficial(v == "1") }),
        ),

        // Input Controls › Device and › Assign
        app(
            "app.controllers.steamController", "app.controllers", "Steam Controller", "Steam控制器支持", SIMPLE,
            hint = "直接读取 Steam 控制器（其触控板和额外按钮）。关闭：正常输入路径不变。", tech = "steam_controller_sdl_enabled",
            control = { Control.Toggle },
            binding = AppBinding({ c -> onOff(GlobalControllerPrefs.isSteamControllerEnabled(c)) }, { c, v -> GlobalControllerPrefs.setSteamControllerEnabled(c, v == "1") }),
        ),
        SettingDef(
            id = "app.controllers.trackpadMouse", category = "app.controllers", group = "Steam Controller", label = "触控板鼠标", level = ADVANCED,
            hint = "哪个触控板移动鼠标。点击它相当于左键单击。", tech = "steam_controller_trackpad_mode",
            control = { _, _ ->
                Control.Segmented(listOf(
                    Opt("${SteamControllerBackend.TRACKPAD_MOUSE_RIGHT}", "Right"),
                    Opt("${SteamControllerBackend.TRACKPAD_MOUSE_LEFT}", "Left"),
                    Opt("${SteamControllerBackend.TRACKPAD_MOUSE_BOTH}", "Both"),
                    Opt("${SteamControllerBackend.TRACKPAD_MOUSE_OFF}", "Off"),
                ))
            },
            app = AppBinding(
                get = { c -> GlobalControllerPrefs.getSteamTrackpadMouseMode(c).toString() },
                set = { c, v -> v.toIntOrNull()?.let { GlobalControllerPrefs.setSteamTrackpadMouseMode(c, it) } },
            ),
            visible = { _, values -> values("app.controllers.steamController") == "1" },
        ),
        app(
            "app.controllers.onScreenPriority", "app.controllers", "新容器启动时使用", "屏幕控制与真实手柄", ADVANCED,
            hint = "复制到每个新容器中；现有容器保留自己的。", tech = "global_on_screen_controller_mode",
            control = {
                Control.Segmented(listOf(
                    Opt("${Container.ON_SCREEN_MODE_KEEP}", "屏幕控制保持为玩家 1"),
                    Opt("${Container.ON_SCREEN_MODE_YIELD}", "手柄成为玩家 1"),
                    Opt("${Container.ON_SCREEN_MODE_SHARE}", "两者都控制玩家 1"),
                ))
            },
            binding = AppBinding(
                get = { c -> GlobalControllerPrefs.getOnScreenMode(c).toString() },
                set = { c, v -> v.toIntOrNull()?.let { GlobalControllerPrefs.setOnScreenMode(c, it) } },
            ),
        ),
        app(
            "app.controllers.autoHide", "app.controllers", "新容器启动时使用", "当控制器连接时隐藏屏幕控制", ADVANCED,
            hint = "复制到每个新容器中；现有容器保留自己的。", tech = "global_auto_hide_controls_on_pad",
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
        confirm = { v -> if (v == "0") Confirm("Turn off auto-backup?", offWarning, "Continue") else null },
    )

    private fun saveManagerPrefs(c: Context) = c.getSharedPreferences("save_manager_prefs", Context.MODE_PRIVATE)

    /** An app-wide power default. Games can override it in their own editor (Performance › Power). */
    private fun perf(id: String, label: String, hint: String?, get: () -> Boolean, set: (Boolean) -> Unit) = app(
        id, "app.performance", "电源（每个游戏的默认值）", label, ADVANCED, hint = hint,
        control = { Control.Toggle },
        binding = AppBinding(get = { _ -> onOff(get()) }, set = { _, v -> set(v == "1") }),
    )
}
