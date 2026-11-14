package com.winlator.star.ui.deck.settings

import com.winlator.star.ui.simple.T
import android.content.Context
import androidx.preference.PreferenceManager
import com.winlator.star.core.AppOrientation
import com.winlator.star.store.SteamRegion
import com.winlator.star.store.StoreDownloadTier
import com.winlator.star.store.blsteam.BlSteamEngineFlag
import com.winlator.star.store.blsteam.BlStoreEngineFlag
import com.winlator.star.ui.deck.settings.SettingLevel.ADVANCED
import com.winlator.star.ui.deck.settings.SettingLevel.SIMPLE
import com.winlator.star.ui.screens.LogCopy
import com.winlator.star.ui.theme.AppThemeState
import com.winlator.star.ui.theme.themePresets
import java.util.Locale
import kotlin.math.roundToInt

// The app-settings rows that only the XMB Settings tab has (the Classic Settings screen keeps its own controls for the
// same preferences; the plain Deck and Lab never see these rows). They sit in category ids that are NOT in AppSettingsRegistry.categories
// ("app.x.*"), so SimpleOneMenuSettings, which walks that list, ignores them; AppSettingsLayout files them into the tabs.
// Each reads and writes the same preference the classic screen that owns it uses and applies the moment it changes.

internal object AppSettingsRows {

    const val CAT_APPEARANCE = "app.x.appearance"
    const val CAT_STEAM = "app.x.steam"
    const val CAT_DOWNLOADS = "app.x.downloads"

    // The values of the interface-style row, in the order of the segmented control.
    const val STYLE_CLASSIC = "classic"
    const val STYLE_DECK = "deck"
    const val STYLE_XMB = "xmb"
    const val STYLE_LAB = "lab"

    const val ID_STYLE = "app.appearance.style"

    private fun prefs(c: Context) = PreferenceManager.getDefaultSharedPreferences(c)
    private fun onOff(b: Boolean) = if (b) "1" else "0"

    private fun prefBool(key: String, default: Boolean) = AppBinding(
        get = { c -> onOff(prefs(c).getBoolean(key, default)) },
        set = { c, v -> prefs(c).edit().putBoolean(key, v == "1").apply() },
    )

    private fun row(
        id: String, category: String, group: String, label: String, level: SettingLevel,
        hint: String? = null, help: String? = null, tech: String? = null,
        control: (SettingsEnv) -> Control, binding: AppBinding,
        visible: (SettingsEnv, (String) -> String) -> Boolean = { _, _ -> true },
        disabled: (SettingsEnv, (String) -> String) -> String? = { _, _ -> null },
    ) = SettingDef(
        id = id, category = category, group = group, label = label, hint = hint, help = help, tech = tech, level = level,
        control = { env, _ -> control(env) }, app = binding, visible = visible, disabled = disabled,
    )

    private fun switch(
        id: String, category: String, group: String, label: String, level: SettingLevel, key: String, default: Boolean,
        hint: String? = null, help: String? = null,
        disabled: (SettingsEnv, (String) -> String) -> String? = { _, _ -> null },
    ) = row(
        id, category, group, label, level, hint = hint, help = help, tech = key,
        control = { Control.Toggle }, binding = prefBool(key, default), disabled = disabled,
    )

    // ── Interface style: the same calls, in the same order, as AppearanceScreen ──
    private fun styleNow(): String {
        return when {
            AppThemeState.uiStyle.value != AppThemeState.UI_STYLE_DECK -> STYLE_CLASSIC
            AppThemeState.deckXmbHome.value -> STYLE_XMB
            AppThemeState.simpleHome.value -> STYLE_LAB
            else -> STYLE_DECK
        }
    }

    private fun setStyle(style: String) {
        when (style) {
            STYLE_CLASSIC -> AppThemeState.setUiStyle(AppThemeState.UI_STYLE_CLASSIC)
            STYLE_DECK -> {
                AppThemeState.setDeckXmbHome(false)
                AppThemeState.setSimpleHome(false)
                AppThemeState.setUiStyle(AppThemeState.UI_STYLE_DECK)
            }
            STYLE_XMB -> {
                AppThemeState.setDeckXmbHome(true)
                AppThemeState.setUiStyle(AppThemeState.UI_STYLE_DECK)
            }
            else -> {
                AppThemeState.setDeckXmbHome(false)
                AppThemeState.setSimpleHome(true)
                AppThemeState.setUiStyle(AppThemeState.UI_STYLE_DECK)
            }
        }
    }

    private fun scale(
        id: String, label: String, hint: String, get: () -> Float, set: (Float) -> Unit,
        visible: (SettingsEnv, (String) -> String) -> Boolean = { _, _ -> true },
    ) = row(
        id, CAT_APPEARANCE, T("大小与方向"), label, ADVANCED, hint = hint,
        control = { Control.Slider(0.5f, 1.5f, 0.05f, { "${(it * 100).roundToInt()}%" }, { String.format(Locale.ENGLISH, "%.2f", it) }) },
        binding = AppBinding(
            get = { _ -> String.format(Locale.ENGLISH, "%.2f", get()) },
            set = { _, v -> v.toFloatOrNull()?.let { set(it) } },
        ),
        visible = visible,
    )

    fun defs(): List<SettingDef> = listOf(
        // ── Appearance (Settings > Appearance; AppearanceScreen keeps serving the Classic shell) ──
        row(
            ID_STYLE, CAT_APPEARANCE, T("界面风格"), T("界面风格"), SIMPLE,
            hint = T("Classic 保留侧边菜单，Deck 是主机风格标签布局，XMB 增加全屏主页，Lab 是全屏启动器。"),
            help = T("Classic 保留侧边菜单。Deck 是顶部标签的主机风格布局，为手柄设计。XMB 是 Deck 加全屏 XMB 主页：标签悬浮于所有页面之上（用 L1/R1 或触屏切换）。Lab 是为手柄设计的全屏启动器，以你的游戏艺术图为背景。主题和强调色应用于全部四种。更改立即生效。"),
            control = { Control.Segmented(listOf(Opt(STYLE_CLASSIC, T("Classic")), Opt(STYLE_DECK, T("Deck")), Opt(STYLE_XMB, T("XMB")), Opt(STYLE_LAB, T("Lab")))) },
            binding = AppBinding(get = { _ -> styleNow() }, set = { _, v -> setStyle(v) }),
        ),
        row(
            "app.appearance.labLook", CAT_APPEARANCE, T("界面风格"), T("Lab look"), ADVANCED,
            hint = T("Lab 启动器的深色或浅色外观。"),
            control = { Control.Segmented(listOf(Opt("dark", T("Dark")), Opt("light", T("Light")))) },
            binding = AppBinding(
                get = { _ -> if (AppThemeState.labLight) "light" else "dark" },
                set = { _, v -> AppThemeState.setLabLight(v == "light") },
            ),
            visible = { _, values -> values(ID_STYLE) == STYLE_LAB },
        ),
        row(
            "app.appearance.preset", CAT_APPEARANCE, T("主题"), T("主题预设"), SIMPLE,
            hint = T("所有界面的配色。在自定义强调色下选择颜色会切换到自定义预设。"),
            control = { Control.Select(themePresets.map { Opt(it.name, it.name) }) },
            binding = AppBinding(
                get = { _ -> themePresets.getOrElse(AppThemeState.presetIndex.value) { themePresets.first() }.name },
                set = { _, v ->
                    val i = themePresets.indexOfFirst { it.name == v }
                    if (i >= 0) AppThemeState.setPreset(i)
                },
            ),
        ),
        row(
            "app.appearance.orientation", CAT_APPEARANCE, T("大小与方向"), T("应用方向"), ADVANCED,
            hint = T("仅对应用自身界面生效：游戏保持各自的方向。自动会跟随设备。"),
            control = { Control.Segmented(listOf(Opt(AppOrientation.AUTO, T("Auto")), Opt(AppOrientation.PORTRAIT, T("竖屏")), Opt(AppOrientation.LANDSCAPE, T("横屏")))) },
            binding = AppBinding(
                get = { c -> AppOrientation.mode(c) },
                set = { c, v ->
                    AppOrientation.setMode(c, v)
                    (c as? android.app.Activity)?.let { AppOrientation.apply(it) }
                },
            ),
            // The XMB home keeps its own lock (landscape, or portrait from the rotate button in its bars).
            visible = { _, values -> values(ID_STYLE) != STYLE_XMB },
        ),
        // The XMB is drawn on a fixed scale of its own (XmbScale.kt) with its own saved sliders; the other styles keep the shared pair.
        scale("app.appearance.uiScale", T("界面缩放"), T("缩放应用自身界面：游戏保持各自大小。"),
            { AppThemeState.uiScale.value }, { AppThemeState.setUiScale(it) },
            visible = { _, values -> values(ID_STYLE) != STYLE_XMB }),
        scale("app.appearance.fontScale", T("字体大小"), T("缩放应用自身文字。"),
            { AppThemeState.fontScale.value }, { AppThemeState.setFontScale(it) },
            visible = { _, values -> values(ID_STYLE) != STYLE_XMB }),
        scale("app.appearance.xmbUiScale", T("界面缩放"), T("缩放应用自身界面：游戏保持各自大小。100% 在所有设备上占屏幕比例相同。"),
            { AppThemeState.xmbUiScale.value }, { AppThemeState.setXmbUiScale(it) },
            visible = { _, values -> values(ID_STYLE) == STYLE_XMB }),
        scale("app.appearance.xmbFontScale", T("字体大小"), T("缩放应用自身文字。手机系统的字体大小在此不生效。"),
            { AppThemeState.xmbFontScale.value }, { AppThemeState.setXmbFontScale(it) },
            visible = { _, values -> values(ID_STYLE) == STYLE_XMB }),
        row(
            "app.appearance.showStores", CAT_APPEARANCE, T("侧边菜单与商店"), T("显示游戏商店"), ADVANCED,
            hint = T("GOG、Epic、Amazon 和 Steam 的快捷方式（侧边菜单、商店标签）。"),
            control = { Control.Toggle },
            binding = AppBinding({ _ -> onOff(AppThemeState.showStores.value) }, { _, v -> AppThemeState.setShowStores(v == "1") }),
        ),
        row(
            "app.appearance.showInternal", CAT_APPEARANCE, T("侧边菜单与商店"), T("显示内部存储"), ADVANCED,
            hint = T("侧边菜单底部的存储条。"),
            control = { Control.Toggle },
            binding = AppBinding({ _ -> onOff(AppThemeState.showInternalStorage.value) }, { _, v -> AppThemeState.setShowInternalStorage(v == "1") }),
        ),
        row(
            "app.appearance.showSd", CAT_APPEARANCE, T("侧边菜单与商店"), T("显示 SD card 存储"), ADVANCED,
            hint = T("仅当设备中插有存储卡时显示。"),
            control = { Control.Toggle },
            binding = AppBinding({ _ -> onOff(AppThemeState.showSdStorage.value) }, { _, v -> AppThemeState.setShowSdStorage(v == "1") }),
        ),

        // ── Steam connection region (the test actions are page extras: AppSettingsExtras) ──
        row(
            "app.steam.region", CAT_STEAM, T("Steam 连接"), T("Steam连接区域"), ADVANCED,
            hint = T("商店、下载和游戏内 Steam 会话连接的 Steam 数据中心。自动按延迟选最近。"),
            help = T("自动测试每个数据中心的延迟，保留一天内最快的，连接失败后会重新测试。只有当自动一直选得不理想时才需要手动指定。商店会在下次连接时应用。"),
            control = { Control.Select(listOf(Opt(SteamRegion.AUTO, T("自动（按延迟选最近）"))) + SteamRegion.CATALOG.map { Opt(it.code, it.name + " (" + it.code + ")") }) },
            binding = AppBinding(get = { c -> SteamRegion.mode(c) }, set = { c, v -> SteamRegion.setMode(c, v) }),
        ),

        // ── Download engines and speed (were inside the Log Manager) ──
        switch("app.downloads.steamEngine", CAT_DOWNLOADS, T("下载引擎"), T("原生Steam引擎（需要重启）"), ADVANCED,
            BlSteamEngineFlag.PREF_KEY, BlSteamEngineFlag.DEFAULT,
            hint = T("默认开启：整个 Steam 会话（登录、游戏库、下载、云、成就、好友）都跑在原生引擎上。关闭则使用旧版 Java 引擎。"),
            help = LogCopy.RUST_ENGINE),
        switch("app.downloads.epicEngine", CAT_DOWNLOADS, T("下载引擎"), T("原生引擎：Epic 下载"), ADVANCED,
            BlStoreEngineFlag.PREF_KEY_EPIC, BlStoreEngineFlag.DEFAULT,
            hint = T("默认开启，对下次下载生效。关闭则使用 Java 下载器。"), help = LogCopy.RUST_STORE_ENGINES),
        switch("app.downloads.gogEngine", CAT_DOWNLOADS, T("下载引擎"), T("原生引擎：GOG 下载"), ADVANCED,
            BlStoreEngineFlag.PREF_KEY_GOG, BlStoreEngineFlag.DEFAULT,
            hint = T("默认开启，对下次下载生效。关闭则使用 Java 下载器。"), help = LogCopy.RUST_STORE_ENGINES),
        switch("app.downloads.amazonEngine", CAT_DOWNLOADS, T("下载引擎"), T("原生引擎：Amazon 下载"), ADVANCED,
            BlStoreEngineFlag.PREF_KEY_AMAZON, BlStoreEngineFlag.DEFAULT,
            hint = T("默认开启，对下次下载生效。关闭则使用 Java 下载器。"), help = LogCopy.RUST_STORE_ENGINES),
        row(
            "app.downloads.speedTier", CAT_DOWNLOADS, T("下载引擎"), T("商店下载速度"), ADVANCED,
            hint = T("Epic、GOG 和 Amazon 原生引擎的上限。连接较弱时会低于此值。对下次下载生效。"),
            help = LogCopy.RUST_STORE_ENGINES, tech = StoreDownloadTier.PREF_KEY,
            control = { Control.Select(StoreDownloadTier.ALL.map { Opt(it.toString(), StoreDownloadTier.label(it)) }) },
            binding = AppBinding(
                get = { c -> StoreDownloadTier.get(c).toString() },
                set = { c, v -> v.toIntOrNull()?.let { StoreDownloadTier.set(c, it) } },
            ),
        ),
    )
}
