package com.winlator.star.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.star.R
import com.winlator.star.container.Container
import com.winlator.star.ui.XServerDialogState
import com.winlator.star.ui.XServerDrawerState
import com.winlator.star.ui.simple.L
import com.winlator.star.ui.simple.T
import com.winlator.star.widget.exportHudDiagnostics
import com.winlator.star.widget.fusionhud.FusionSize
import com.winlator.star.widget.perfhud.parseHudOutline
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Lab-drawn versions of the Classic drawer's frame generation section (FrameGenSection), HUD tab (HudContent,
// embedded) and TV tab (TvContent). Same state flows, setters and callbacks in the same order as Classic.

// ───────────────────────────── Frame generation ─────────────────────────────

/**
 * Lab replacement for the Classic FrameGenSection: one card, placed under the page's "帧率" heading.
 * The multiplier writes this session only; model, flow scale, capture resolution and performance mode the container.
 */
@Composable
internal fun LabFrameGenPanel(state: XServerDrawerState) {
    val cs = MaterialTheme.colorScheme
    val frameGenEnabled by state.frameGenEnabled.collectAsState()
    val initFgMult by state.frameGenMultiplier.collectAsState()
    val initFgFlow by state.frameGenFlowScale.collectAsState()
    val initFgModel by state.frameGenModel.collectAsState()
    val initFgPreset by state.frameGenPerfPreset.collectAsState()
    val engine by state.frameGenEngine.collectAsState()
    val layerActive by state.bionicFgActive.collectAsState()
    val initLsfgPerf by state.lsfgPerformanceMode.collectAsState()
    val winFgNative by state.winFgNative.collectAsState()
    val nativeFgLocks by state.nativeFgLocks.collectAsState()
    val fpsCap by state.fpsLimit.collectAsState()
    val displayTargetHz by state.displayTargetHz.collectAsState()
    val supportedRates by state.supportedRefreshRates.collectAsState()
    val liveRate by state.currentRefreshRate.collectAsState()
    val matchRefresh by state.matchRefreshRate.collectAsState()
    val vrrOk by state.vrrSupported.collectAsState()
    val fgUnavailable by state.fgUnavailableReason.collectAsState()
    val fgUnavailableDetail by state.fgUnavailableDetail.collectAsState()
    val isWaylandSession by state.isWaylandMode.collectAsState()
    val waylandFgOk by state.waylandFrameGenAvailable.collectAsState()
    val waylandFgBlocked = isWaylandSession && !waylandFgOk
    val readout by state.frameGenReadout.collectAsState()

    val engineLabel = when (engine) {
        "lsfg" -> "lsfg-vk"
        "lsfg-native" -> L("LSFG Native")
        "bionic" -> if (winFgNative) L("Win-FG Native") else "win-fg"
        "wfg" -> "WFG"
        else -> L("Off")
    }
    // Green dot = the engine is multiplying frames right now (frame gen starts at Off every launch).
    val isRunning = layerActive && engine != "off" && initFgMult > 0

    IgCard {
        IgRow(L("Engine"), hint = readout.ifEmpty { null }) {
            IgBadge(engineLabel, dot = if (isRunning) Color(0xFF4CAF50) else cs.onSurface.copy(alpha = 0.35f))
        }

        if (frameGenEnabled) {
            var fgMult by remember(initFgMult) { mutableIntStateOf(initFgMult) }
            var fgFlow by remember(initFgFlow) { mutableFloatStateOf(initFgFlow) }
            var fgModel by remember(initFgModel) { mutableIntStateOf(initFgModel) }
            var fgPreset by remember(initFgPreset) { mutableIntStateOf(initFgPreset) }
            fun applyFg() {
                state.setFrameGenMultiplier(fgMult)
                state.setFrameGenFlowScale(fgFlow)
                state.setFrameGenModel(fgModel)
                state.setFrameGenPerfPreset(fgPreset)
                state.onBionicFgConfigChange?.run()
            }
            val multEnabled = fgUnavailable.isEmpty() && !waylandFgBlocked

            IgDivider()
            if (waylandFgBlocked) IgNote(L("此版本尚未在 Wayland 上支持。"), modifier = Modifier.padding(top = 8.dp))
            if (fgUnavailable.isNotEmpty()) {
                IgNote("⚠ $fgUnavailable", error = true, modifier = Modifier.padding(top = 8.dp))
                if (fgUnavailableDetail.isNotEmpty()) IgNote(L("Driver check: %1\$s", fgUnavailableDetail))
            }
            val multOptions = if (engine == "bionic") listOf(0 to L("Off"), 2 to L("On"))
            else listOf(0 to L("Off"), 2 to "2×", 3 to "3×", 4 to "4×")
            IgRow(
                L("帧率"),
                note = L("Starts off each launch."),
                scopes = listOf(SaveScope.SESSION),
                wideControl = true,
                enabled = multEnabled,
            ) {
                IgSegmented(multOptions, if (multEnabled) fgMult else null, enabled = multEnabled) { m ->
                    fgMult = m
                    applyFg()
                }
            }

            // Fit advice while LSFG Native / Win-FG Native generate (Classic FgFitAdvice under the multiplier).
            if (nativeFgLocks && !waylandFgBlocked) {
                LabPerfFgFitAdvice(
                    cap = fpsCap,
                    mult = fgMult,
                    screen = labPerfRememberFgScreen(displayTargetHz, supportedRates, liveRate),
                    supported = supportedRates,
                    autoOn = matchRefresh && vrrOk,
                    canChangeMult = engine == "lsfg-native",
                ) { fix ->
                    state.setFpsLimit(fix)
                    state.onFpsLimitChange?.run()
                }
            }

            // win-fg only, while on. Legacy stored models 0-2 show as Optical flow (3), as in Classic.
            if (engine == "bionic" && fgMult > 0 && !waylandFgBlocked) {
                IgDivider()
                IgRow(L("模型"), scopes = listOf(SaveScope.CONTAINER), wideControl = true) {
                    IgSegmented(
                        listOf(3 to L("光流"), 4 to L("双向")),
                        if (fgModel < 3) 3 else fgModel,
                    ) { m ->
                        fgModel = m
                        applyFg()
                    }
                }
            }

            // WFG has no flow scale.
            if (fgMult > 0 && engine != "wfg" && !waylandFgBlocked) {
                IgDivider()
                IgSliderRow(
                    title = L("Flow scale"),
                    value = fgFlow,
                    range = 0.2f..1.0f,
                    step = 0.04f,
                    format = { "%.2f".format(it) },
                    onChange = { fgFlow = it },
                    onFinished = { applyFg() },
                    hint = L("光流精度越高 = 运动估计越平滑，GPU开销越大。"),
                    scopes = listOf(SaveScope.CONTAINER),
                )
            }

            if (engine == "lsfg-native" && com.winlator.star.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED && !waylandFgBlocked) {
                val capture by state.fgCaptureResolution.collectAsState()
                val panelHeight by state.fgPanelHeight.collectAsState()
                IgDivider()
                LabPerfFgCaptureRow(capture, panelHeight) { newValue ->
                    state.setFgCaptureResolution(newValue)
                    applyFg()
                }
            }

            if (engine == "lsfg" && !waylandFgBlocked) {
                var lsfgPerf by remember(initLsfgPerf) { mutableStateOf(initLsfgPerf) }
                IgDivider()
                IgToggleRow(
                    L("性能模式"),
                    lsfgPerf,
                    hint = L("降低质量以提高 FPS — 有助于低端设备。"),
                    scopes = listOf(SaveScope.CONTAINER),
                ) {
                    lsfgPerf = it
                    state.setLsfgPerformanceMode(it)
                    applyFg()
                }
            }
        } else if (waylandFgBlocked) {
            IgDivider()
            IgNote(L("此版本尚未在 Wayland 上支持。"), modifier = Modifier.padding(vertical = 6.dp))
        } else {
            IgDivider()
            IgNote(L("此容器已关闭。在此容器的设置中将其开启以在此使用。"), modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

// What the display is really doing, for the fit advice (copy of XServerDrawer's private FgScreen / rememberFgScreen).
private data class LabPerfFgScreen(val hz: Int, val asked: Int, val held: Boolean)

@Composable
private fun labPerfRememberFgScreen(target: Int, supported: List<Int>, current: Int): LabPerfFgScreen {
    val asked = if (target > 0) target else supported.maxOrNull() ?: current
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(asked, current) {
        held = false
        if (asked > 0 && current in 1 until asked - 1) {
            delay(2500)
            held = true
        }
    }
    return LabPerfFgScreen(if (held) current else asked, asked, held)
}

/** Classic FgFitAdvice: over the display's rate the over-limit warning; under it with Auto on, an exact-fit cap. */
@Composable
private fun LabPerfFgFitAdvice(
    cap: Int,
    mult: Int,
    screen: LabPerfFgScreen,
    supported: List<Int>,
    autoOn: Boolean,
    canChangeMult: Boolean,
    onSetCap: (Int) -> Unit,
) {
    if (cap <= 0 || mult < 2 || screen.hz <= 0) return
    val made = cap * mult
    if (made > screen.hz) {
        LabPerfFgOverLimitWarning(cap, mult, screen, canChangeMult, onSetCap)
        return
    }
    if (!autoOn || screen.held || made == screen.hz) return
    val fits = supported.filter { it % mult == 0 && it / mult >= 10 }.map { (it / mult) to it }
    val lower = fits.lastOrNull { it.first <= cap }
    val higher = fits.firstOrNull { it.first > cap }
    val pick = lower ?: higher ?: return
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IgNote(
            L(
                "Screen set to %1\$s Hz, the closest speed above %2\$s × %3\$s = %4\$s. A few refreshes repeat a picture. For a perfect fit:",
                screen.hz, cap, mult, made,
            )
        )
        LabPerfFixLine(
            label = L("Set frame rate limit to %1\$s → %2\$s Hz", pick.first, pick.second),
            danger = false,
            alt = if (lower != null && higher != null) L("or %1\$s → %2\$s Hz if the game holds it", higher.first, higher.second) else null,
        ) { onSetCap(pick.first) }
    }
}

/** Classic FgOverLimitWarning: cap × multiplier is more than the screen shows. Win-FG Native can't change multiplier. */
@Composable
private fun LabPerfFgOverLimitWarning(
    cap: Int,
    mult: Int,
    screen: LabPerfFgScreen,
    canChangeMult: Boolean,
    onSetCap: (Int) -> Unit,
) {
    val fixCap = maxOf(10, screen.hz / mult)
    val fitMult = if (canChangeMult) (mult - 1 downTo 2).firstOrNull { cap * it <= screen.hz } else null
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IgNote(
            L(
                "⚠ %1\$s × %2\$s = %3\$s: more than your %4\$s Hz screen can show. The extra frames pile up, so expect stutter and laggy controls.",
                cap, mult, cap * mult, screen.hz,
            ),
            error = true,
        )
        if (screen.held) {
            IgNote(
                L(
                    "Your device is keeping the screen at %1\$s Hz (Bannerlator asked for %2\$s Hz). Battery saver or your device's own refresh-rate setting may be holding it.",
                    screen.hz, screen.asked,
                )
            )
        }
        LabPerfFixLine(
            label = L("Set frame rate limit to %1\$s", fixCap),
            danger = true,
            alt = fitMult?.let { L("or pick %1\$s×", it) },
        ) { onSetCap(fixCap) }
    }
}

/** A fix button (Classic FgFixButton) with its optional "or …" alternative beside it. */
@Composable
private fun LabPerfFixLine(label: String, danger: Boolean, alt: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        IgButton(label, null, onClick, danger = danger)
        if (alt != null) {
            Spacer(Modifier.width(10.dp))
            Text(alt, color = cs.onSurfaceVariant, fontFamily = NunitoSansFamily, fontSize = 12.5.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

/**
 * Classic FgCaptureChips: Panel, Game, then every distinct height of the container Screen Size list that the renderer
 * would not clamp ([panel/4, panel)). Stored value "panel" / "game" / a bare height.
 */
@Composable
private fun LabPerfFgCaptureRow(selected: String, panelHeight: Int, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val heights = remember(panelHeight) {
        val minH = maxOf(16, panelHeight / 4)
        context.resources.getStringArray(R.array.screen_size_entries)
            .filterNot { it.equals("custom", ignoreCase = true) }
            .map { Container.fgCaptureHeightFor(it.substringBefore(" ")) }
            .filter { it > 0 && (panelHeight <= 0 || (it >= minH && it < panelHeight)) }
            .distinct()
            .sorted()
    }
    val options = listOf(Container.FG_CAPTURE_PANEL to L("Panel"), Container.FG_CAPTURE_GAME to L("Game")) +
        heights.map { it.toString() to "${it}p" }
    // Panel / Game match by value; a height matches by its resolved height (so "1280x720" lights 720p).
    val selectedKey = if (selected == Container.FG_CAPTURE_PANEL || selected == Container.FG_CAPTURE_GAME) selected
    else Container.fgCaptureHeightFor(selected).toString()
    IgRow(
        L("采集分辨率"),
        hint = L("帧生成链运行的高度（宽度跟随屏幕）。更低 = GPU开销小得多，画面更柔和。Panel = 全分辨率。"),
        note = L("实验性模式"),
        scopes = listOf(SaveScope.CONTAINER),
        wideControl = true,
    ) {
        IgChips(options, selectedKey) { onSelect(it) }
    }
}

// ───────────────────────────── Performance overlay (HUD) ─────────────────────────────

/** Where the overlay settings save: the shortcut when the HUD config lives on it, else the container. */
@Composable
private fun labPerfHudScope(): SaveScope {
    val onShortcut by DeckInGameState.hudSavedToShortcut.collectAsState()
    return if (onShortcut) SaveScope.GAME else SaveScope.CONTAINER
}

/** Parses the HUD config string the way Classic HudContent's parseConfig does. */
private fun labPerfParseHudConfig(s: String): Map<String, String> {
    if (s.isEmpty()) return emptyMap()
    val map = mutableMapOf<String, String>()
    s.split(",").forEach { part ->
        val eq = part.indexOf('=')
        if (eq >= 0) map[part.substring(0, eq)] = part.substring(eq + 1)
    }
    return map
}

/**
 * Lab replacement for Classic `HudContent(state, embedded = true)`: the Show switch card, then (while it is on) Style &
 * size, Metrics, Appearance, Alerts & temperature and Tools, each under its own heading. Every change rebuilds the
 * full config with Classic's key set and order and sends it through onFpsConfigApply.
 */
@Composable
internal fun LabHudPanel(state: XServerDrawerState) {
    val fpsConfig by state.fpsConfig.collectAsState()
    // Classic re-reads the live display refresh rate whenever this tab opens.
    LaunchedEffect(Unit) { state.onRefreshRatePoll?.run() }
    val scopes = listOf(labPerfHudScope())

    val cfg = remember(fpsConfig) { labPerfParseHudConfig(fpsConfig) }
    fun b(k: String, fb: String, d: String) = (cfg[k] ?: cfg[fb] ?: d) == "1"

    val hudMode = remember(cfg) { cfg.getOrDefault("hudMode", "vertical") }
    var hudEnabled by remember(cfg) { mutableStateOf(b("hudEnabled", "hudEnabled", "1")) }
    val styles = listOf("classic", "gamehub", "gamenative", "fusion")
    var hudStyle by remember(cfg) { mutableStateOf(cfg.getOrDefault("hudStyle", "fusion")) }
    val gameHub = hudStyle == "gamehub"
    val gameNative = hudStyle == "gamenative"
    val fusion = hudStyle == "fusion"
    val rich = gameHub || gameNative || fusion
    val fusionSizes = listOf("full", "tiles", "pill", "minimal", "mega")
    var fusionSize by remember(cfg) { mutableStateOf(cfg.getOrDefault("hudSize", "pill")) }
    val fusionChips = FusionSize.from(fusionSize).supportedChips()
    val gpuModelDefault = if (cfg.getOrDefault("hudStyle", "fusion") == "fusion") "1" else "0"
    val clockDefault = if (cfg.getOrDefault("hudStyle", "fusion") == "fusion") "1" else "0"
    var showFPS by remember(cfg) { mutableStateOf(b("showFPS", "showFPS", "1")) }
    var showGraph by remember(cfg) { mutableStateOf(b("showFPSGraph", "showFPSGraph", "0")) }
    var showCPU by remember(cfg) { mutableStateOf(b("showCPUUsage", "showCPULoad", "1")) }
    var showGPU by remember(cfg) { mutableStateOf(b("showGPULoad", "showGPULoad", "1")) }
    var showRAM by remember(cfg) { mutableStateOf(b("showRAM", "showRAM", "1")) }
    var showPower by remember(cfg) { mutableStateOf(b("showPower", "showPower", "1")) }
    var showTemp by remember(cfg) { mutableStateOf(b("showTemp", "showBatteryTemp", "1")) }
    var showEngine by remember(cfg) { mutableStateOf(b("showEngine", "showRenderer", "1")) }
    var showGpuModel by remember(cfg) { mutableStateOf(b("showGpuModel", "showGpuModel", gpuModelDefault)) }
    var dualBattery by remember(cfg) { mutableStateOf(b("hudDualBattery", "hudDualBattery", "0")) }
    var showGpuTemp by remember(cfg) { mutableStateOf(b("showGpuTemp", "showGpuTemp", "0")) }
    var showBattery by remember(cfg) { mutableStateOf(b("showBattery", "showBattery", "0")) }
    var showRuntime by remember(cfg) { mutableStateOf(b("showRuntime", "showRuntime", "0")) }
    var showClock by remember(cfg) { mutableStateOf(b("showClock", "showClock", clockDefault)) }
    var showCpuGraph by remember(cfg) { mutableStateOf(b("showCPUGraph", "showCPUGraph", "0")) }
    var showGpuGraph by remember(cfg) { mutableStateOf(b("showGPUGraph", "showGPUGraph", "0")) }
    var showVram by remember(cfg) { mutableStateOf(b("showVram", "showVram", "1")) }
    var showLow001 by remember(cfg) { mutableStateOf(b("showLow001", "showLow001", "1")) }
    var fpsDecimal by remember(cfg) { mutableStateOf(b("fpsDecimal", "fpsDecimal", "1")) }
    var hudLocked by remember(cfg) { mutableStateOf(b("hudLocked", "hudLocked", "0")) }
    var showPerCore by remember(cfg) { mutableStateOf(b("showPerCore", "showPerCore", "1")) }
    var showSwap by remember(cfg) { mutableStateOf(b("showSwap", "showSwap", "1")) }
    var showNet by remember(cfg) { mutableStateOf(b("showNet", "showNet", "1")) }
    var showResolution by remember(cfg) { mutableStateOf(b("showResolution", "showResolution", "1")) }
    var showProton by remember(cfg) { mutableStateOf(b("showProton", "showProton", "1")) }
    var showWrapper by remember(cfg) { mutableStateOf(b("showWrapper", "showWrapper", "1")) }
    var showDxVer by remember(cfg) { mutableStateOf(b("showDxVer", "showDxVer", "1")) }
    var showSession by remember(cfg) { mutableStateOf(b("showSession", "showSession", "1")) }
    var tempUnitF by remember(cfg) { mutableStateOf(cfg.getOrDefault("tempUnit", "c").equals("f", true)) }
    var tempBands by remember(cfg) { mutableStateOf(cfg.getOrDefault("tempBands", "1") != "0") }
    var tempAuto by remember(cfg) { mutableStateOf(cfg.getOrDefault("tempAuto", "1") != "0") }
    var tempRedCpu by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("tempRedCpu", "90").toFloatOrNull() ?: 90f) }
    var tempRedGpu by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("tempRedGpu", "90").toFloatOrNull() ?: 90f) }
    var tempRedBat by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("tempRedBat", "48").toFloatOrNull() ?: 48f) }

    var scaleValue by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("hudScale", Container.DEFAULT_HUD_SCALE.toString()).toFloatOrNull() ?: Container.DEFAULT_HUD_SCALE.toFloat()) }
    var opacityValue by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("hudOpacity", "80").toFloatOrNull() ?: 80f) }
    var transValue by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("hudTransparency", "0").toFloatOrNull() ?: 0f) }

    val skins = listOf("classic", "neon", "mono")
    val colors = listOf("soft", "mid", "vivid")
    var skin by remember(cfg) { mutableStateOf(cfg.getOrDefault("hudSkin", "classic")) }
    var color by remember(cfg) { mutableStateOf(cfg.getOrDefault("hudColor", "mid")) }
    var outlineValue by remember(cfg) { mutableFloatStateOf(parseHudOutline(cfg.getOrDefault("hudOutline", "40")).toFloat()) }
    var outlineAccent by remember(cfg) { mutableStateOf(cfg.getOrDefault("hudOutlineAccent", "1") == "1") }

    fun i(v: Boolean) = if (v) "1" else "0"
    // Same key set and order as Classic HudContent.buildConfig (and the pre-launch FpsCounterConfigDialog).
    fun buildConfig(): String = listOf(
        "hudStyle=$hudStyle",
        "hudEnabled=${i(hudEnabled)}",
        "hudSize=$fusionSize",
        "hudLocked=${i(hudLocked)}",
        "showVram=${i(showVram)}",
        "showLow001=${i(showLow001)}",
        "fpsDecimal=${i(fpsDecimal)}",
        "showPerCore=${i(showPerCore)}",
        "showSwap=${i(showSwap)}",
        "showNet=${i(showNet)}",
        "showResolution=${i(showResolution)}",
        "showProton=${i(showProton)}",
        "showWrapper=${i(showWrapper)}",
        "showDxVer=${i(showDxVer)}",
        "showSession=${i(showSession)}",
        "hudMode=$hudMode",
        "showFPS=${i(showFPS)}",
        "showFPSGraph=${i(showGraph)}",
        "showCPUUsage=${i(showCPU)}",
        "showCPULoad=${i(showCPU)}",
        "showGPULoad=${i(showGPU)}",
        "showRAM=${i(showRAM)}",
        "showPower=${i(showPower)}",
        "showTemp=${i(showTemp)}",
        "showBatteryTemp=${i(showTemp)}",
        "showEngine=${i(showEngine)}",
        "showRenderer=${i(showEngine)}",
        "showGpuModel=${i(showGpuModel)}",
        "hudDualBattery=${i(dualBattery)}",
        "showGpuTemp=${i(showGpuTemp)}",
        "showBattery=${i(showBattery)}",
        "showRuntime=${i(showRuntime)}",
        "showClock=${i(showClock)}",
        "showCPUGraph=${i(showCpuGraph)}",
        "showGPUGraph=${i(showGpuGraph)}",
        "tempUnit=${if (tempUnitF) "f" else "c"}",
        "tempBands=${i(tempBands)}",
        "tempAuto=${i(tempAuto)}",
        "tempRedCpu=${tempRedCpu.toInt()}",
        "tempRedGpu=${tempRedGpu.toInt()}",
        "tempRedBat=${tempRedBat.toInt()}",
        "hudSkin=$skin",
        "hudColor=$color",
        "hudOutline=${outlineValue.toInt()}",
        "hudOutlineAccent=${if (outlineAccent) 1 else 0}",
        "hudScale=${scaleValue.toInt()}",
        "hudOpacity=${opacityValue.toInt()}",
        "hudTransparency=${transValue.toInt()}",
    ).joinToString(",")

    fun applyHud() { state.onFpsConfigApply?.invoke(buildConfig()) }

    IgCard {
        IgToggleRow(
            L("显示性能覆盖层"),
            hudEnabled,
            scopes = scopes,
            note = L("Turning the overlay on when it wasn't built this launch also switches it on in the container"),
        ) { hudEnabled = it; applyHud() }
    }

    if (!hudEnabled) return

    // ── Style & size ──
    IgSectionLabel(L("Style & size"))
    IgCard {
        val styleHint = when (hudStyle) {
            "gamehub" -> L("丰富覆盖层：皮肤、彩色字段、实时FPS图表。样式更改将在下次启动时生效。")
            "gamenative" -> L("GameNative风格覆盖层：紧凑药丸形或堆叠列表，带实时图表。样式更改将在下次启动时生效。")
            "fusion" -> L("Fusion 覆盖层：5 种尺寸的配色方案，含百分位低值、显存以及 Mega 综合视图。点击 HUD 可循环切换尺寸。")
            else -> L("经典Bannerlator覆盖层。")
        }
        LabPerfHudChoiceRow(
            L("Overlay style"),
            listOf("Classic", "GameHub", "GameNative", "Fusion"),
            styles.indexOf(hudStyle).coerceAtLeast(0),
            scopes,
            hint = styleHint,
        ) { hudStyle = styles[it]; applyHud() }
        if (fusion) {
            IgDivider()
            LabPerfHudChoiceRow(
                L("Size"),
                listOf(L("Full"), L("平铺"), L("药丸形"), L("简洁"), L("超大")),
                fusionSizes.indexOf(fusionSize).coerceAtLeast(0),
                scopes,
            ) { fusionSize = fusionSizes[it]; applyHud() }
        }
    }

    // ── Metrics ── Chip ids are the English labels FusionSize.supportedChips() returns; only the display is translated.
    fun show(label: String, styleOk: Boolean): Boolean = if (fusion) label in fusionChips else styleOk
    val metricChips = buildList<Triple<String, Boolean, () -> Unit>> {
        if (show("FPS", true)) add(Triple(T("FPS"), showFPS) { showFPS = !showFPS; applyHud() })
        if (show("FPS graph", rich)) add(Triple(T("FPS graph"), showGraph) { showGraph = !showGraph; applyHud() })
        if (show("CPU", true)) add(Triple(T("CPU"), showCPU) { showCPU = !showCPU; applyHud() })
        if (!fusion && gameNative) add(Triple(T("CPU 图表"), showCpuGraph) { showCpuGraph = !showCpuGraph; applyHud() })
        if (show("GPU", true)) add(Triple(T("GPU"), showGPU) { showGPU = !showGPU; applyHud() })
        if (!fusion && gameNative) add(Triple(T("GPU 图表"), showGpuGraph) { showGpuGraph = !showGpuGraph; applyHud() })
        if (show("VRAM", false)) add(Triple(T("VRAM"), showVram) { showVram = !showVram; applyHud() })
        if (show("RAM", true)) add(Triple(T("RAM"), showRAM) { showRAM = !showRAM; applyHud() })
        if (show("Power", true)) add(Triple(T("Power"), showPower) { showPower = !showPower; applyHud() })
        if (show("Temp", true)) add(Triple(T("Temp"), showTemp) { showTemp = !showTemp; applyHud() })
        if (show("GPU temp", gameNative)) add(Triple(T("GPU temp"), showGpuTemp) { showGpuTemp = !showGpuTemp; applyHud() })
        if (show("Battery", gameNative)) add(Triple(T("Battery"), showBattery) { showBattery = !showBattery; applyHud() })
        if (!fusion && gameNative) add(Triple(T("运行时间"), showRuntime) { showRuntime = !showRuntime; applyHud() })
        if (show("0.01% low", false)) add(Triple(T("0.01% low"), showLow001) { showLow001 = !showLow001; applyHud() })
        if (show("FPS .1", false)) add(Triple(T("FPS .1"), fpsDecimal) { fpsDecimal = !fpsDecimal; applyHud() })
        if (show("Per-core", false)) add(Triple(T("Per-core"), showPerCore) { showPerCore = !showPerCore; applyHud() })
        if (show("Swap", false)) add(Triple(T("Swap"), showSwap) { showSwap = !showSwap; applyHud() })
        if (show("Network", false)) add(Triple(T("Network"), showNet) { showNet = !showNet; applyHud() })
        if (show("Resolution", false)) add(Triple(T("Resolution"), showResolution) { showResolution = !showResolution; applyHud() })
        if (show("Proton", false)) add(Triple(T("Proton"), showProton) { showProton = !showProton; applyHud() })
        if (show("Wrapper", false)) add(Triple(T("Wrapper"), showWrapper) { showWrapper = !showWrapper; applyHud() })
        if (show("DX ver", false)) add(Triple(T("DX ver"), showDxVer) { showDxVer = !showDxVer; applyHud() })
        if (show("Session", false)) add(Triple(T("Session"), showSession) { showSession = !showSession; applyHud() })
        if (show("Clock", gameNative)) add(Triple(T("Clock"), showClock) { showClock = !showClock; applyHud() })
        if (show("Engine", true)) add(Triple(T("Engine"), showEngine) { showEngine = !showEngine; applyHud() })
        if (show("GPU model", rich)) add(Triple(T("GPU model"), showGpuModel) { showGpuModel = !showGpuModel; applyHud() })
        if (!fusion && gameHub) add(Triple(T("双电池电量"), dualBattery) { dualBattery = !dualBattery; applyHud() })
        add(Triple(T("锁定位置"), hudLocked) { hudLocked = !hudLocked; applyHud() })
    }
    IgSectionLabel(L("指标"))
    IgCard {
        // Stacked even on wide windows: the chip grid needs the full card width.
        CompositionLocalProvider(LocalIgCompact provides true) {
            IgRow(L("On the overlay"), hint = L("%1\$s on", metricChips.count { it.second }), scopes = scopes) {
                LabPerfToggleChips(metricChips)
            }
        }
    }

    // ── Appearance ──
    IgSectionLabel(L("外观"))
    IgCard {
        IgSliderRow(
            title = L("Scale"), value = scaleValue, range = 50f..150f, step = 5f,
            format = { "${it.toInt()}%" }, onChange = { scaleValue = it }, onFinished = { applyHud() }, scopes = scopes,
        )
        IgDivider()
        if (rich) {
            IgSliderRow(
                title = L("不透明度"), value = opacityValue, range = 0f..100f, step = 5f,
                format = { "${it.toInt()}%" }, onChange = { opacityValue = it }, onFinished = { applyHud() }, scopes = scopes,
            )
        } else {
            IgSliderRow(
                title = L("透明度"), value = transValue, range = 0f..50f, step = 2.5f,
                format = { "${it.toInt()}" }, onChange = { transValue = it }, onFinished = { applyHud() }, scopes = scopes,
            )
        }
        if (gameHub) {
            IgDivider()
            LabPerfHudChoiceRow(L("皮肤"), listOf(L("Classic"), L("霓虹"), L("黑白")), skins.indexOf(skin), scopes) { skin = skins[it]; applyHud() }
        }
        if (gameHub || gameNative || fusion) {
            IgDivider()
            LabPerfHudChoiceRow(L("颜色"), listOf(L("柔和"), L("适中"), L("鲜艳")), colors.indexOf(color), scopes) { color = colors[it]; applyHud() }
            IgDivider()
            IgSliderRow(
                title = L("轮廓"), value = outlineValue, range = 0f..100f, step = 5f,
                format = { "${it.toInt()}" }, onChange = { outlineValue = it }, onFinished = { applyHud() }, scopes = scopes,
            )
            IgDivider()
            LabPerfHudChoiceRow(L("轮廓颜色"), listOf(L("灰色"), L("强调色")), if (outlineAccent) 1 else 0, scopes) { outlineAccent = it == 1; applyHud() }
        }
    }

    // ── Alerts & temperature ── only while a temperature is on screen.
    if (showTemp || ((gameNative || fusion) && (showGpuTemp || showBattery))) {
        IgSectionLabel(L("Alerts & temperature"))
        IgCard {
            LabPerfHudChoiceRow(L("Temperature unit"), listOf("°C", "°F"), if (tempUnitF) 1 else 0, scopes) { tempUnitF = it == 1; applyHud() }
            IgDivider()
            val bandMode = if (!tempBands) 0 else if (tempAuto) 1 else 2
            LabPerfHudChoiceRow(
                L("危险颜色"),
                listOf(L("Off"), L("Auto"), L("手动")),
                bandMode,
                scopes,
                hint = when (bandMode) {
                    0 -> L("温度使用正常颜色显示。")
                    1 -> L("阈值从您设备自身的温度触发点读取，若无则回退到安全默认值。")
                    else -> L("为每个传感器设置红色阈值点，琥珀色阈值位于其下方。")
                },
            ) {
                tempBands = it != 0
                tempAuto = it != 2
                applyHud()
            }
            if (bandMode == 2) {
                IgDivider()
                IgSliderRow(
                    title = L("CPU 红色阈值"), value = tempRedCpu, range = 50f..110f, step = 3f,
                    format = { "${it.toInt()}°C" }, onChange = { tempRedCpu = it }, onFinished = { applyHud() }, scopes = scopes,
                )
                if ((gameNative || fusion) && showGpuTemp) {
                    IgDivider()
                    IgSliderRow(
                        title = L("GPU red at"), value = tempRedGpu, range = 50f..110f, step = 3f,
                        format = { "${it.toInt()}°C" }, onChange = { tempRedGpu = it }, onFinished = { applyHud() }, scopes = scopes,
                    )
                }
                IgDivider()
                IgSliderRow(
                    title = L("电池红色阈值"), value = tempRedBat, range = 35f..60f, step = 1.25f,
                    format = { "${it.toInt()}°C" }, onChange = { tempRedBat = it }, onFinished = { applyHud() }, scopes = scopes,
                )
            }
        }
    }

    // ── Tools ──
    IgSectionLabel(L("Tools"))
    IgCard {
        val diagContext = LocalContext.current
        IgRow(
            L("导出 HUD 诊断信息"),
            hint = L("将传感器报告（CPU/GPU/温度/显存…）直接保存到您的下载文件夹。"),
        ) { IgButton(L("导出"), Icons.Outlined.Description, { exportHudDiagnostics(diagContext) }) }
    }
}

/** A one-of-a-few choice row (Classic HudChipRow): [selected] is an index into [labels]; -1 lights none, as in Classic. */
@Composable
private fun LabPerfHudChoiceRow(
    title: String,
    labels: List<String>,
    selected: Int,
    scopes: List<SaveScope>,
    hint: String? = null,
    onSelect: (Int) -> Unit,
) {
    IgRow(title, hint = hint, scopes = scopes, wideControl = true) {
        IgChips(labels.mapIndexed { idx, l -> idx to l }, selected) { onSelect(it) }
    }
}

/** Independent on/off chips (Classic ModeChipGrid of metric toggles), drawn like IgChips; an on chip has the accent outline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabPerfToggleChips(items: List<Triple<String, Boolean, () -> Unit>>) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (label, on, onTap) ->
            val shape = deckShape(4.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .deckFocusRing(shape)
                    .clip(shape)
                    .background(if (on) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerHigh.copy(alpha = igRaised(1f)))
                    .border(if (on) 2.dp else 1.dp, if (on) cs.primary else deckLineAt(0.4f), shape)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onTap() })
                    .heightIn(min = 34.dp)
                    .widthIn(min = 44.dp)
                    .padding(horizontal = 11.dp),
            ) {
                Text(
                    L(label),
                    color = if (on) cs.primary else cs.onSurface,
                    fontFamily = NunitoSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

// ───────────────────────────── TV ─────────────────────────────

/**
 * Lab replacement for Classic TvContent: cast, the wired display (while one is connected), reset audio, picture,
 * latency, frame generation, audio & power, advanced and the not-yet-available streaming block.
 */
@Composable
internal fun LabTvPanel(state: XServerDrawerState) {
    val tvConnected by state.tvConnected.collectAsState()
    val displayName by state.tvDisplayName.collectAsState()
    val playOnTv by state.tvPlayOnTv.collectAsState()
    val autoSwap by state.tvAutoSwap.collectAsState()
    val onExternal by state.tvGameOnExternal.collectAsState()
    val modes by state.tvModes.collectAsState()
    val currentModeId by state.tvCurrentModeId.collectAsState()
    val hdr by state.tvHdr.collectAsState()
    val rendererIsVulkan by state.rendererIsVulkan.collectAsState()
    val game = igGameScope()

    // ── Wireless cast ──
    IgSectionLabel(L("投屏到电视TV （无线）"))
    IgCard {
        IgNote(
            L("⚠ 实验性 · 仅视频 — 无线流式传输是新功能：画面会延迟几秒，暂无电视TV 声音（游戏音频保留在此设备上），启动可能需要一点时间或需要再试一次。"),
            error = true,
            modifier = Modifier.padding(top = 8.dp),
        )
        IgRow(
            L("投屏到电视TV "),
            hint = L("将游戏画面通过 Wi‑Fi 流式传输到 Google TV / Chromecast投屏器 — 在应用中选择一个，电视TV 上无需安装任何内容。点击内部的“？”了解工作原理及其取舍。"),
        ) { IgButton(L("Cast"), Icons.Outlined.Cast, { state.onOpenCastPicker?.run() }) }
        IgNote(L("要获得最低延迟，有线 USB‑C→HDMI 线缆仍然是最佳选择。"), modifier = Modifier.padding(bottom = 8.dp))
    }

    // ── Wired / external display, only while one is connected ──
    if (tvConnected) {
        IgSectionLabel(L("TV / external display"))
        IgCard {
            IgRow(
                if (displayName.isNotBlank()) L("Connected: %1\$s", displayName) else L("External display connected"),
                hint = if (hdr.isNotBlank()) L("HDR supported by display: %1\$s", hdr) else null,
            )
            if (modes.isNotEmpty()) {
                val labels = modes.map { it.label }
                val selectedIdx = modes.indexOfFirst { it.id == currentModeId }.let { if (it >= 0) it else 0 }
                IgDivider()
                IgPickRow(
                    L("Display mode"),
                    labels,
                    selectedIdx,
                    hint = L("Resolution & refresh"),
                    scopes = listOf(SaveScope.SESSION),
                ) { i ->
                    val id = modes[i].id
                    state.setTvCurrentModeId(id)
                    state.onTvModeChange?.accept(id)
                }
            }
            IgDivider()
            IgToggleRow(L("Play on TV"), playOnTv, scopes = listOf(SaveScope.CONTAINER)) {
                state.setTvPlayOnTv(it)
                state.onTvPlayOnTvChange?.accept(it)
            }
            IgDivider()
            IgToggleRow(L("Auto-switch on connect"), autoSwap, scopes = listOf(SaveScope.CONTAINER), enabled = playOnTv) {
                state.setTvAutoSwap(it)
                state.onTvAutoSwapChange?.accept(it)
            }
            IgDivider()
            if (onExternal) {
                IgRow(L("Bring game back to handheld")) { IgButton(L("Bring back"), null, { state.onBringBackFromTv?.run() }) }
            } else {
                IgRow(L("Move game to TV")) { IgButton(L("Move"), Icons.Outlined.Tv, { state.onMoveToTv?.run() }) }
            }
        }
    }

    // ── Reset audio (sound can drop after backgrounding or an HDMI route change) ──
    Spacer(Modifier.height(10.dp))
    IgCard {
        IgRow(L("重置音频"), hint = L("修复切换应用后声音丢失的问题。")) {
            IgButton(L("重置"), null, { state.onResetAudio?.run() })
        }
    }

    // ── Picture ──
    IgSectionLabel(L("Picture"))
    IgCard {
        val fullscreenMode by state.fullscreenMode.collectAsState()
        IgRow(L("比例"), scopes = listOf(game), wideControl = true) {
            IgSegmented(
                listOf(
                    0 to stringResource(R.string.fullscreen_mode_off_short),
                    1 to stringResource(R.string.fullscreen_mode_fit_short),
                    2 to stringResource(R.string.fullscreen_mode_stretch_short),
                    3 to stringResource(R.string.fullscreen_mode_fill_short),
                    4 to stringResource(R.string.fullscreen_mode_integer_short),
                ),
                fullscreenMode,
            ) { mode ->
                state.setFullscreenMode(mode)
                state.onSetFullscreenMode?.accept(mode)
            }
        }
        IgDivider()
        val overscan by state.tvOverscan.collectAsState()
        var overscanVal by remember(overscan) { mutableIntStateOf(overscan) }
        IgSliderRow(
            title = L("过扫描 / 安全区域"),
            value = overscanVal.toFloat(),
            range = 0f..8f,
            step = 1f,
            format = { "${it.roundToInt()}" },
            onChange = { overscanVal = it.roundToInt() },
            onFinished = {
                state.setTvOverscan(overscanVal)
                state.onTvOverscanChange?.accept(overscanVal)
            },
            hint = L("如果您的TV裁切了边缘，则将画面向内收缩。"),
            scopes = listOf(SaveScope.CONTAINER),
        )
        IgDivider()
        if (rendererIsVulkan) {
            IgNote(L("缩放滤镜在 OpenGL 渲染器上可用。"), modifier = Modifier.padding(vertical = 6.dp))
        } else {
            val initGlUpscalerMode by XServerDialogState.glUpscalerMode.collectAsState()
            var glUpscalerMode by remember(initGlUpscalerMode) { mutableIntStateOf(initGlUpscalerMode) }
            val compact = LocalIgCompact.current
            IgRow(L("缩放滤镜"), scopes = listOf(game), wideControl = true) {
                Column(horizontalAlignment = if (compact) Alignment.Start else Alignment.End) {
                    IgChips(
                        listOf(
                            0 to L("None"), 1 to L("线性过滤"), 2 to L("邻近采样"),
                            3 to "SGSR", 8 to "SGSR HQ", 4 to "FSR",
                            5 to "FSR (适应)", 6 to L("画面锐化"), 7 to "NIS",
                        ),
                        glUpscalerMode,
                    ) {
                        glUpscalerMode = it
                        XServerDialogState.onGlUpscalerApply?.invoke(it)
                    }
                    Spacer(Modifier.height(8.dp))
                    IgHelpButton(L("缩放滤镜"), R.string.help_scaling_mode)
                }
            }
        }
    }

    // ── Latency ──
    IgSectionLabel(L("延迟"))
    IgCard {
        if (rendererIsVulkan) {
            LabPerfPresentModeRow(state)
        } else {
            IgNote(L("延迟模式（垂直同步/低延迟）在Vulkan渲染器上可用。"), modifier = Modifier.padding(vertical = 6.dp))
        }
        IgDivider()
        val fpsLimiterEnabled by state.fpsLimiterEnabled.collectAsState()
        val fpsLimit by state.fpsLimit.collectAsState()
        val capValues = listOf(0, 30, 60, 90, 120)
        val capOptions = capValues.map { if (it == 0) L("Off") else L("%1\$s fps", it) }
        val capIdx = if (!fpsLimiterEnabled) 0 else capValues.indexOf(fpsLimit).let { if (it >= 0) it else 0 }
        IgPickRow(L("帧率上限"), capOptions, capIdx, scopes = listOf(game)) { i ->
            val v = capValues[i]
            state.setFpsLimiterEnabled(v > 0)
            if (v > 0) state.setFpsLimit(v)
            state.onFpsLimitChange?.run()
        }
    }

    // ── Frame generation ──
    IgSectionLabel(L("帧率"))
    LabFrameGenPanel(state)
    IgInfo(L("提示：在TV上启用游戏模式以获得最低输入延迟。"))

    // ── Audio & power ──
    IgSectionLabel(L("音频与电源"))
    IgCard {
        val audioOut by state.tvAudioOut.collectAsState()
        IgPickRow(
            L("音频输出"),
            listOf(L("跟随系统"), L("TV / HDMI"), L("手持设备")),
            audioOut,
            hint = L("实验性 — 客户音频路由可能并非始终跟随。"),
            scopes = listOf(SaveScope.CONTAINER),
        ) { i ->
            state.setTvAudioOut(i)
            state.onTvAudioOutChange?.accept(i)
        }
        IgDivider()
        val dimHandheld by state.tvDimHandheld.collectAsState()
        IgToggleRow(
            L("在TV投屏时调暗手持设备屏幕"),
            dimHandheld,
            hint = L("通过调暗手机屏幕来节省电量和减少发热。"),
            scopes = listOf(SaveScope.CONTAINER),
        ) {
            state.setTvDimHandheld(it)
            state.onTvDimHandheldChange?.accept(it)
        }
    }

    // ── Advanced ──
    IgSectionLabel(L("Advanced"))
    IgCard {
        val renderRes by state.tvRenderRes.collectAsState()
        IgPickRow(
            L("TV渲染分辨率"),
            listOf(L("匹配TV"), L("匹配手持设备"), "1080p", "1440p"),
            renderRes,
            hint = L("在下次游戏启动时应用（渲染分辨率在启动时固定）。"),
            scopes = listOf(SaveScope.CONTAINER),
        ) { i ->
            state.setTvRenderRes(i)
            state.onTvRenderResChange?.accept(i)
        }
    }

    // ── Streaming (not available yet) ──
    IgSectionLabel(L("流式传输"))
    IgCard {
        IgRow(
            L("Wireless streaming"),
            hint = L("无需线缆的无线投屏到TV（比特率、编解码器、传输协议）将在未来更新中提供。有线 HDMI / DeX 投屏目前已可通过上述控制使用。"),
            enabled = false,
        )
        listOf(T("比特率"), T("编解码器 (H.264 / HEVC)"), T("传输协议 (WebRTC / DLNA)"), T("流分辨率和帧率")).forEach {
            IgDivider()
            IgRow(L(it), hint = L("Requires WiFi streaming"), enabled = false)
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * Classic PresentModeSection (Vulkan only): while frame generation forces Mailbox the chips stay live, a FIFO /
 * Immediate pick is refused without touching the saved mode and the "Mailbox is required" note shows for 2 s.
 */
@Composable
private fun LabPerfPresentModeRow(state: XServerDrawerState) {
    val rendererIsVulkan by state.rendererIsVulkan.collectAsState()
    if (!rendererIsVulkan) return
    val presentMode by state.presentMode.collectAsState()
    val locked by state.presentModeLocked.collectAsState()
    val modes = listOf("fifo", "mailbox", "immediate")
    val labels = listOf(
        stringResource(R.string.renderer_present_mode_fifo),
        stringResource(R.string.renderer_present_mode_mailbox),
        stringResource(R.string.renderer_present_mode_immediate),
    )
    val selectedIdx = modes.indexOf(presentMode).coerceAtLeast(0)
    var blockedFlash by remember { mutableIntStateOf(0) }
    var showBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(blockedFlash) {
        if (blockedFlash > 0) {
            showBlocked = true
            delay(2000)
            showBlocked = false
        }
    }
    IgRow(
        stringResource(R.string.renderer_present_mode),
        hint = L("How finished frames reach the screen: V-Sync (FIFO) never tears, Smooth (Mailbox) adds less delay, Fastest (Immediate) is lowest latency but may tear."),
        note = if (showBlocked) stringResource(R.string.present_mode_fg_locked) else null,
        scopes = listOf(igGameScope()),
        wideControl = true,
    ) {
        IgSegmented(labels.mapIndexed { idx, l -> idx to l }, selectedIdx) { idx ->
            val mode = modes[idx]
            if (locked) {
                if (mode != "mailbox") blockedFlash++
            } else {
                state.onPresentModeChange?.accept(mode)
            }
        }
    }
}
