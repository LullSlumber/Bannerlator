package com.winlator.star.ui.deck.settings

import com.winlator.star.ui.simple.L
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import com.winlator.star.container.Container
import com.winlator.star.ui.deck.settings.SettingLevel.ADVANCED
import com.winlator.star.ui.deck.settings.SettingLevel.EXPERT
import com.winlator.star.ui.deck.settings.SettingLevel.SIMPLE
import com.winlator.star.widget.exportHudDiagnostics
import com.winlator.star.widget.fusionhud.FusionSize
import com.winlator.star.widget.perfhud.parseHudOutline
import kotlinx.coroutines.CoroutineScope

// The Performance overlay page's style rows: every choice of the classic "FPS计数器设置"
// dialog (FpsCounterConfigDialog), as ordinary rows. The dialog stores one packed string on the
// container, "fpsCounterConfig" ("k=v,k=v"). A row changes only its own key (and the twin key the
// dialog writes beside it, e.g. showCPUUsage / showCPULoad) and keeps every other entry byte for byte,
// so the classic dialog and the in-game side menu read what is written here and the other way round.
// An absent key reads as what the classic dialog shows for it.
//
// The game editor has no overlay settings: the launch path reads a game's own "fpsCounterConfig" only
// once the in-game side menu has written one, so these rows are container and All containers only.

internal object OverlayRows {

    const val ID_STYLE = "hud.style"
    const val ID_SIZE = "hud.size"
    private const val ID_TEMP = "hud.metric.temp"
    private const val ID_GPU_TEMP = "hud.metric.gpuTemp"
    private const val ID_BATTERY = "hud.metric.battery"
    private const val ID_BANDS = "hud.temp.bands"

    // ── The packed string ────────────────────────────────────────────────────────────────────────

    /** A key's raw text, or null when absent. The last entry wins, as the dialog's parser reads it. */
    private fun raw(config: String, key: String): String? {
        var found: String? = null
        for (part in config.split(",")) {
            val eq = part.indexOf('=')
            if (eq >= 0 && part.substring(0, eq) == key) found = part.substring(eq + 1)
        }
        return found
    }

    /** The config with each key replaced in place (or appended when absent); every other entry is kept. */
    private fun withKeys(config: String, changes: Map<String, String>): String {
        val parts = config.split(",").filter { it.isNotEmpty() }.toMutableList()
        for ((k, v) in changes) {
            val idx = parts.indexOfFirst { it.substringBefore('=') == k }
            if (idx >= 0) {
                parts[idx] = "$k=$v"
                // A duplicate later entry would win on read: keep the edit visible.
                for (j in parts.indices.reversed()) if (j > idx && parts[j].substringBefore('=') == k) parts.removeAt(j)
            } else parts.add("$k=$v")
        }
        return parts.joinToString(",")
    }

    /**
     * One setting of the overlay config. [decode] turns the whole config into the row's value;
     * [encode] turns a picked value into the keys it writes.
     */
    private fun hudRow(
        id: String, group: String, label: String, level: SettingLevel,
        control: (SettingsEnv, (String) -> String) -> Control,
        decode: (String) -> String,
        encode: (String) -> Map<String, String>,
        tech: String,
        hint: String? = null,
        visible: ((String) -> String) -> Boolean = { true },
    ) = SettingDef(
        id = id, category = "hud", group = group, label = label, hint = hint,
        tech = "fpsCounterConfig › $tech", level = level, control = control,
        vm = VmBinding(
            get = { _, vm -> decode(vm.fpsCounterConfig) },
            set = { _, vm, v -> vm.fpsCounterConfig = withKeys(vm.fpsCounterConfig, encode(v)) },
        ),
        showInGame = false,
        visible = { _, values -> visible(values) },
    )

    /** An on/off key read as the dialog reads it ([fallback] when absent), written with its [twins]. */
    private fun hudToggle(
        id: String, group: String, label: String, level: SettingLevel,
        key: String, default: (String) -> Boolean,
        fallback: String = key,
        twins: List<String> = emptyList(),
        hint: String? = null,
        visible: ((String) -> String) -> Boolean = { true },
    ) = hudRow(
        id, group, label, level, { _, _ -> Control.Toggle },
        decode = { cfg -> if ((raw(cfg, key) ?: raw(cfg, fallback) ?: (if (default(cfg)) "1" else "0")) == "1") "1" else "0" },
        encode = { v -> (listOf(key) + twins).associateWith { v } },
        tech = (listOf(key) + twins).joinToString(" / "), hint = hint, visible = visible,
    )

    private fun on(d: Boolean): (String) -> Boolean = { d }
    /** GPU model and the clock are on by default for Fusion, off for the other styles (by the stored style). */
    private val fusionDefault: (String) -> Boolean = { cfg -> (raw(cfg, "hudStyle") ?: "fusion") == "fusion" }

    private fun intRow(
        id: String, group: String, label: String, level: SettingLevel, key: String,
        min: Int, max: Int, default: Int, suffix: String,
        hint: String? = null,
        parse: (String?) -> Int = { it?.toIntOrNull() ?: default },
        visible: ((String) -> String) -> Boolean = { true },
    ) = hudRow(
        id, group, label, level,
        { _, _ -> Control.Slider(min.toFloat(), max.toFloat(), 1f, { "${it.toInt()}$suffix" }, { "${it.toInt()}" }) },
        decode = { cfg -> parse(raw(cfg, key)).toString() },
        encode = { v -> mapOf(key to v) },
        tech = key, hint = hint, visible = visible,
    )

    private fun seg(vararg o: Pair<String, String>) = Control.Segmented(o.map { Opt(it.first, it.second) })

    // ── What the chosen style shows ─────────────────────────────────────────────────────────────

    private fun style(values: (String) -> String) = values(ID_STYLE)
    private fun fusion(values: (String) -> String) = style(values) == "fusion"
    private fun gameHub(values: (String) -> String) = style(values) == "gamehub"
    private fun gameNative(values: (String) -> String) = style(values) == "gamenative"
    /** GameHub, GameNative and Fusion: opacity, FPS graph, GPU model, colour and outline. */
    private fun rich(values: (String) -> String) = gameHub(values) || gameNative(values) || fusion(values)

    /** A metric's switch is shown for the chips the chosen Fusion size draws, else by its style rule (as the dialog does). */
    private fun chip(label: String, styleOk: ((String) -> String) -> Boolean): ((String) -> String) -> Boolean = { values ->
        if (fusion(values)) label in FusionSize.from(values(ID_SIZE)).supportedChips() else styleOk(values)
    }
    private val always: ((String) -> String) -> Boolean = { true }
    private val never: ((String) -> String) -> Boolean = { false }

    /** The temperature rows matter only while a temperature is on screen. */
    private fun tempShown(values: (String) -> String) =
        values(ID_TEMP) == "1" || ((gameNative(values) || fusion(values)) && (values(ID_GPU_TEMP) == "1" || values(ID_BATTERY) == "1"))

    private fun metric(
        id: String, label: String, key: String, default: (String) -> Boolean,
        show: ((String) -> String) -> Boolean, fallback: String = key, twins: List<String> = emptyList(),
    ) = hudToggle("hud.metric.$id", L("指标"), label, ADVANCED, key, default, fallback, twins, visible = show)

    fun defs(): List<SettingDef> = listOf(
        // Overlay
        hudToggle("hud.enabled", L("覆盖层"), L("显示 HUD"), SIMPLE, "hudEnabled", on(true),
            hint = L("侧边菜单的显示 HUD 开关。关闭时，即使上面已开启，覆盖层也保持隐藏。")),
        hudRow(
            ID_STYLE, L("覆盖层"), L("Style"), SIMPLE,
            { _, _ -> seg("classic" to "Classic", "gamehub" to "GameHub", "gamenative" to "GameNative", "fusion" to "Fusion") },
            decode = { cfg -> raw(cfg, "hudStyle") ?: "fusion" },
            encode = { v -> mapOf("hudStyle" to v) },
            tech = "hudStyle",
            hint = L("Fusion：五种尺寸的单一颜色编码外观。在游戏中点击 HUD 可切换垂直和水平。"),
        ),
        hudRow(
            ID_SIZE, L("覆盖层"), L("Size"), SIMPLE,
            { _, _ -> Control.Segmented(FusionSize.values().map { Opt(it.token, it.label) }) },
            decode = { cfg -> raw(cfg, "hudSize") ?: "pill" },
            encode = { v -> mapOf("hudSize" to v) },
            tech = "hudSize", hint = L("也可在游戏中通过点击 Fusion HUD 来更改。"),
            visible = { fusion(it) },
        ),
        hudToggle("hud.locked", L("覆盖层"), L("锁定位置"), ADVANCED, "hudLocked", on(false),
            hint = L("HUD 在游戏中无法拖动。")),

        // Look
        intRow("hud.scale", L("外观"), L("屏幕上的大小"), ADVANCED, "hudScale", 50, 150, Container.DEFAULT_HUD_SCALE, "%",
            parse = { (it?.toIntOrNull() ?: Container.DEFAULT_HUD_SCALE).coerceAtLeast(50) }),
        intRow("hud.opacity", L("外观"), L("不透明度"), ADVANCED, "hudOpacity", 0, 100, 80, "%", visible = { rich(it) }),
        intRow("hud.transparency", L("外观"), L("透明度"), ADVANCED, "hudTransparency", 0, 50, 0, "", visible = { !rich(it) }),
        hudRow(
            "hud.skin", L("外观"), L("皮肤"), ADVANCED,
            { _, _ -> seg("classic" to "Classic", "neon" to L("霓虹"), "mono" to L("黑白")) },
            decode = { cfg -> raw(cfg, "hudSkin") ?: "classic" },
            encode = { v -> mapOf("hudSkin" to v) },
            tech = "hudSkin", visible = { gameHub(it) },
        ),
        hudRow(
            "hud.color", L("外观"), L("颜色"), ADVANCED,
            { _, _ -> seg("soft" to L("柔和"), "mid" to L("适中"), "vivid" to L("鲜艳")) },
            decode = { cfg -> raw(cfg, "hudColor") ?: "mid" },
            encode = { v -> mapOf("hudColor" to v) },
            tech = "hudColor", visible = { rich(it) },
        ),
        intRow("hud.outline", L("外观"), L("轮廓"), EXPERT, "hudOutline", 0, 100, 40, "",
            parse = { parseHudOutline(it) }, visible = { rich(it) }),
        hudRow(
            "hud.outlineAccent", L("外观"), L("轮廓颜色"), EXPERT,
            { _, _ -> seg("0" to L("灰色"), "1" to L("强调色")) },
            decode = { cfg -> if ((raw(cfg, "hudOutlineAccent") ?: "1") == "1") "1" else "0" },
            encode = { v -> mapOf("hudOutlineAccent" to v) },
            tech = "hudOutlineAccent", visible = { rich(it) },
        ),

        // Metrics: the dialog's chips, each its own switch, shown for the style and size that draw it.
        metric("fps", "FPS", "showFPS", on(true), chip("FPS", always)),
        metric("fpsGraph", L("FPS图表"), "showFPSGraph", on(false), chip(L("FPS图表")) { rich(it) }),
        metric("cpu", "CPU", "showCPUUsage", on(true), chip("CPU", always), fallback = "showCPULoad", twins = listOf("showCPULoad")),
        metric("cpuGraph", L("CPU 图表"), "showCPUGraph", on(false), { !fusion(it) && gameNative(it) }),
        metric("gpu", "GPU", "showGPULoad", on(true), chip("GPU", always)),
        metric("gpuGraph", L("GPU 图表"), "showGPUGraph", on(false), { !fusion(it) && gameNative(it) }),
        metric("vram", "VRAM", "showVram", on(true), chip("VRAM", never)),
        metric("ram", "RAM", "showRAM", on(true), chip("RAM", always)),
        metric("power", L("Power"), "showPower", on(true), chip(L("Power"), always)),
        metric("temp", L("Temperature"), "showTemp", on(true), chip(L("温度"), always), fallback = "showBatteryTemp", twins = listOf("showBatteryTemp")),
        metric("gpuTemp", L("GPU 温度"), "showGpuTemp", on(false), chip(L("GPU 温度")) { gameNative(it) }),
        metric("battery", L("Battery"), "showBattery", on(false), chip(L("Battery")) { gameNative(it) }),
        metric("runtime", L("Runtime"), "showRuntime", on(false), { !fusion(it) && gameNative(it) }),
        metric("low001", L("0.01% low"), "showLow001", on(true), chip(L("0.01% low"), never)),
        metric("fpsDecimal", L("带小数的 FPS"), "fpsDecimal", on(true), chip(L("FPS .1"), never)),
        metric("perCore", L("Per-core CPU"), "showPerCore", on(true), chip(L("Per-core"), never)),
        metric("swap", L("Swap"), "showSwap", on(true), chip(L("Swap"), never)),
        metric("net", L("Network"), "showNet", on(true), chip(L("Network"), never)),
        metric("resolution", L("Resolution"), "showResolution", on(true), chip(L("Resolution"), never)),
        metric("proton", "Proton", "showProton", on(true), chip("Proton", never)),
        metric("wrapper", L("Wrapper"), "showWrapper", on(true), chip(L("Wrapper"), never)),
        metric("dxVer", L("DirectX 版本"), "showDxVer", on(true), chip(L("DX 版本"), never)),
        metric("session", L("会话"), "showSession", on(true), chip(L("会话"), never)),
        metric("clock", L("时钟"), "showClock", fusionDefault, chip(L("时钟")) { gameNative(it) }),
        metric("engine", L("引擎"), "showEngine", on(true), chip(L("引擎"), always), fallback = "showRenderer", twins = listOf("showRenderer")),
        metric("gpuModel", L("GPU型号"), "showGpuModel", fusionDefault, chip(L("GPU型号")) { rich(it) }),
        metric("dualBattery", L("双电池电量"), "hudDualBattery", on(false), { !fusion(it) && gameHub(it) }),

        // Temperatures (the same keys as the in-game side menu)
        hudRow(
            "hud.temp.unit", L("温度"), L("单位"), ADVANCED,
            { _, _ -> seg("c" to L("°C"), "f" to L("°F")) },
            decode = { cfg -> if ((raw(cfg, "tempUnit") ?: "c").equals("f", true)) "f" else "c" },
            encode = { v -> mapOf("tempUnit" to v) },
            tech = "tempUnit", visible = { tempShown(it) },
        ),
        // One three-way over two keys, as the dialog has it: bands off with auto on isn't worth its own state.
        hudRow(
            ID_BANDS, L("温度"), L("危险颜色"), ADVANCED,
            { _, _ -> seg("0" to L("Off"), "1" to L("Auto"), "2" to L("手动")) },
            decode = { cfg ->
                when {
                    (raw(cfg, "tempBands") ?: "1") == "0" -> "0"
                    (raw(cfg, "tempAuto") ?: "1") != "0" -> "1"
                    else -> "2"
                }
            },
            encode = { v -> mapOf("tempBands" to if (v != "0") "1" else "0", "tempAuto" to if (v != "2") "1" else "0") },
            tech = L("tempBands / tempAuto"),
            hint = L("自动读取您设备自身的温度触发点。手动为每个传感器设置红色点；琥珀色点略低于它。"),
            visible = { tempShown(it) },
        ),
        intRow("hud.temp.redCpu", L("温度"), L("CPU 红色阈值"), EXPERT, "tempRedCpu", 50, 110, 90, L("°C"),
            visible = { tempShown(it) && it(ID_BANDS) == "2" }),
        intRow("hud.temp.redGpu", L("温度"), L("GPU red at"), EXPERT, "tempRedGpu", 50, 110, 90, L("°C"),
            visible = { tempShown(it) && it(ID_BANDS) == "2" && (gameNative(it) || fusion(it)) && it(ID_GPU_TEMP) == "1" }),
        intRow("hud.temp.redBat", L("温度"), L("电池红色阈值"), EXPERT, "tempRedBat", 35, 60, 48, L("°C"),
            visible = { tempShown(it) && it(ID_BANDS) == "2" }),
    )
}

/**
 * The Performance overlay page's one action that is not a setting: the classic dialog's "Export HUD
 * diagnostics", a sensor report saved to Downloads. Container and All containers; nothing in a game.
 */
internal class DeckOverlaySection(
    val env: SettingsEnv,
    val source: SettingsSource,
    private val scope: CoroutineScope,
) : DeckSection {

    override fun extras(): Map<String, CategoryExtra> =
        if (env.scope == EditorScope.GAME || env.scope == EditorScope.APP || env.world != World.WINE) emptyMap()
        else mapOf("hud" to CategoryExtra(bottom = {
            actionItems(
                "hud-tools", L("诊断"),
                listOf(
                    DeckAction("export", Icons.Filled.Share, L("导出 HUD 诊断信息"),
                        L("将传感器报告（CPU、GPU、温度、VRAM…）保存到您的下载文件夹。")) {
                        runCatching { exportHudDiagnostics(env.context) }
                    },
                ),
            )
        }))

    @Composable
    override fun Dialogs() {}
}
