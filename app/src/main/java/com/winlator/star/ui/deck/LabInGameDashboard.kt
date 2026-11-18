package com.winlator.star.ui.deck

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.winlator.star.R
import com.winlator.star.perf.PerfGpuTurbo
import com.winlator.star.perf.PerfRevertRegistry
import com.winlator.star.perf.PerfRootApplier
import com.winlator.star.perf.PerformanceSettings
import com.winlator.star.perf.RootManager
import com.winlator.star.perf.TempWatchdog
import com.winlator.star.perf.TempWatchdog.ThresholdMode
import com.winlator.star.perf.galaxy.GalaxyPerfManager
import com.winlator.star.perf.galaxy.GalaxyPowerProfile
import com.winlator.star.ui.XServerDrawerState
import com.winlator.star.ui.components.ANDROID_OUTPUT_MS
import com.winlator.star.ui.components.AUDIO_PRESETS
import com.winlator.star.ui.components.AudioConfig
import com.winlator.star.ui.components.AudioPreset
import com.winlator.star.ui.components.PRESET_CUSTOM
import com.winlator.star.ui.components.effectiveConfigFor
import com.winlator.star.ui.components.latencyIsDirectBuffer
import com.winlator.star.ui.components.latencyUsedBy
import com.winlator.star.ui.screens.PerfDisclaimerCopy
import com.winlator.star.ui.simple.L
import com.winlator.star.ui.simple.MenuAction
import com.winlator.star.ui.simple.SimpleActionSheet
import com.winlator.star.ui.simple.SimpleMuted
import com.winlator.star.ui.simple.SimpleStrong
import com.winlator.star.widget.HudMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Lab-drawn in-game Performance dashboard and Audio settings: the same flows, setters and callbacks as the Classic
// PerformanceDashboardDialog (ui/PerformanceDashboardDialog.kt) and AudioSettingsDialog (ui/components/AudioSettingsDialog.kt),
// drawn with the Lab in-game kit.

/** A dangerous toggle waiting on the user's acknowledgement before it flips on. */
private class LabDashPendingDanger(val label: String, val enable: () -> Unit)

/**
 * Lab variant of [com.winlator.star.ui.PerformanceDashboardDialog]: live gauges, power profiles, Galaxy Performance,
 * root status, the no-root and root toggles with their per-game override lines, free memory, the temperature
 * watchdog and the panel opacity, with the same grant / danger / watchdog-off gates.
 */
@Composable
internal fun LabPerformanceDashboardDialog(state: XServerDrawerState, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val opacity by PerformanceSettings.dialogOpacity.collectAsState()

    val rootState by RootManager.state.collectAsState()
    val granted = rootState == RootManager.RootState.GRANTED
    val harnessProven by PerfRevertRegistry.harnessProven.collectAsState()
    val overridden by state.overriddenKeys.collectAsState()
    val toggles by state.rootToggles.collectAsState()
    val readouts by state.rootReadouts.collectAsState()
    val ceilingC by TempWatchdog.ceilingC.collectAsState()

    val sustained by state.sustainedPerfMode.collectAsState()
    val priority by state.perfPriorityBoost.collectAsState()
    val bigCores by state.preferBigCores.collectAsState()

    // Same save note the Lab Performance page shows for the dashboard.
    val fromShortcut by DeckInGameState.launchedFromShortcut.collectAsState()
    val perfScopes = if (fromShortcut) listOf(SaveScope.GAME, SaveScope.ALL) else listOf(SaveScope.ALL)

    var pendingDanger by remember { mutableStateOf<LabDashPendingDanger?>(null) }
    var showGrantGate by remember { mutableStateOf(false) }
    var showGalaxyOffPrompt by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<String?>(null) }

    // Poll the cheap sysfs/HudMetrics readouts while the dialog is open (same cadence as Classic).
    LaunchedEffect(Unit) {
        while (true) {
            state.onRootReadoutPoll?.run()
            delay(1500)
        }
    }

    fun setRoot(key: String, on: Boolean) {
        state.setRootToggle(key, on)
        state.onRootToggleChange?.accept(key, on)
    }
    fun setSustained(on: Boolean) { state.setSustainedPerfMode(on); state.onSustainedPerfModeChange?.run() }
    fun setPriority(on: Boolean) { state.setPerfPriorityBoost(on); state.onPerfPriorityBoostChange?.run() }
    fun setBig(on: Boolean) { state.setPreferBigCores(on); state.onPreferBigCoresChange?.run() }

    // Power profiles: the same macro over the existing toggles as Classic. Thermal / fan are never auto-enabled.
    fun applyProfile(p: String) {
        profile = p
        when (p) {
            "Off" -> {
                setSustained(false); setPriority(false); setBig(false)
                setRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, false)
                if (granted) {
                    setRoot(PerfRootApplier.KEY_CPU_GOVERNOR, false)
                    setRoot(PerfRootApplier.KEY_CPU_FREQ_LOCK, false)
                    setRoot(PerfRootApplier.KEY_CORES_ONLINE, false)
                }
            }
            "Battery" -> {
                setSustained(false); setPriority(false); setBig(false)
                setRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, false)
                if (granted) {
                    setRoot(PerfRootApplier.KEY_CPU_GOVERNOR, false)
                    setRoot(PerfRootApplier.KEY_CPU_FREQ_LOCK, false)
                    setRoot(PerfRootApplier.KEY_CORES_ONLINE, false)
                }
            }
            "Balanced" -> {
                setSustained(true); setPriority(false); setBig(false)
                setRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, false)
            }
            "Performance", "Turbo" -> {
                setSustained(true); setPriority(true); setBig(true)
                setRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, true)
                if (granted) {
                    setRoot(PerfRootApplier.KEY_CPU_GOVERNOR, true)
                    setRoot(PerfRootApplier.KEY_CPU_FREQ_LOCK, true)
                    setRoot(PerfRootApplier.KEY_CORES_ONLINE, true)
                }
            }
        }
    }

    LabInGameDialog(
        title = L("Performance"),
        onDismiss = onDismiss,
        subtitle = L("实时应用于本次会话 · 退出时自动恢复"),
        maxWidth = 900.dp,
        tall = true,
        // Panel opacity stays on screen (outside the scroll), as in Classic.
        footer = {
            Text(L("面板不透明度"), color = SimpleMuted, fontFamily = NunitoSansFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Box(Modifier.weight(1f)) {
                IgSlider(
                    value = opacity,
                    range = PerformanceSettings.OPACITY_MIN..1f,
                    step = 0.05f,
                    format = { "${(it * 100).toInt()}%" },
                    onChange = { PerformanceSettings.setDialogOpacity(it) },
                    onFinished = {},
                )
            }
        },
    ) {
        LabDashWindowAlpha()

        // ── Gauges ──
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            val gpu = labDashLeadingInt(readouts["gpuMhz"])
            val temp = labDashLeadingInt(readouts["socTemp"])
            LabDashGauge(Modifier.weight(1f), L("GPU clock"), readouts["gpuMhz"] ?: "—", "MHz",
                frac = gpu?.let { (it / 1000f).coerceIn(0f, 1f) } ?: 0f, hot = false)
            LabDashGauge(Modifier.weight(1f), L("SoC temp"), readouts["socTemp"] ?: "—", "°C",
                frac = temp?.let { ((it - 30f) / ((ceilingC - 30).coerceAtLeast(1))).coerceIn(0f, 1f) } ?: 0f,
                hot = temp != null && temp >= ceilingC)
            LabDashGauge(Modifier.weight(1f), L("Governor"), readouts["governor"] ?: "—", "",
                frac = 0f, hot = false, textOnly = true)
        }

        // ── Power profile ──
        IgSectionLabel(L("电源配置"))
        IgCard {
            IgRow(L("配置"), scopes = perfScopes, wideControl = true) {
                IgChips(
                    options = listOf(
                        "Off" to L("Off"),
                        "Battery" to L("Battery"),
                        "Balanced" to L("Balanced"),
                        "Performance" to L("Performance"),
                        "Turbo" to L("Turbo"),
                    ),
                    selected = profile,
                ) { applyProfile(it) }
            }
        }

        // ── Galaxy Performance (Samsung, no root): only on Galaxy devices with the SDK, else hidden ──
        val galaxySupported = remember { GalaxyPerfManager.isSupported() }
        var galaxyEnabled by remember { mutableStateOf(GalaxyPerfManager.isEnabled()) }
        var galaxyProfileName by remember {
            mutableStateOf(GalaxyPerfManager.currentProfile?.name ?: GalaxyPowerProfile.DEFAULT.name)
        }
        if (galaxySupported) {
            IgSectionLabel(L("Galaxy Performance · Samsung"))
            IgCard {
                // The switch persists device-wide (GalaxyPerfManager prefs); the preset goes to the running container.
                IgToggleRow(
                    L("启用Galaxy性能"),
                    checked = galaxyEnabled,
                    hint = L("No-root CPU/GPU/RAM control via Samsung's SDK. Off by default."),
                    scopes = listOf(SaveScope.ALL),
                ) {
                    galaxyEnabled = it
                    GalaxyPerfManager.setEnabled(it)
                    // Recommend handing clocks to Galaxy alone, unless already Off.
                    if (it && profile != "Off") showGalaxyOffPrompt = true
                }
                if (galaxyEnabled) {
                    IgDivider()
                    IgRow(
                        L("Galaxy preset"),
                        hint = L("提示：将上述电源配置设置为“关闭”，以便仅由Galaxy控制频率（否则它们会冲突）。"),
                        scopes = listOf(SaveScope.CONTAINER),
                        wideControl = true,
                    ) {
                        IgChips(
                            options = GalaxyPowerProfile.PRESETS.map { it.name to L(it.name) },
                            selected = galaxyProfileName,
                        ) { name ->
                            val preset = GalaxyPowerProfile.PRESETS.firstOrNull { it.name == name }
                            if (preset != null) {
                                galaxyProfileName = preset.name
                                GalaxyPerfManager.setProfile(preset)
                            }
                        }
                    }
                }
            }
        }

        // ── Root status ──
        Spacer(Modifier.height(12.dp))
        IgCard {
            IgRow(
                if (granted) L("已授予root") else L("无 root"),
                hint = if (granted) L("Full CPU/GPU control unlocked.") else L("No-root controls still available."),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).background(
                            if (granted) Color(0xFF37C26A) else cs.onSurface.copy(alpha = 0.35f),
                            CircleShape,
                        )
                    )
                    if (!granted && rootState != RootManager.RootState.UNAVAILABLE) {
                        IgButton(
                            if (rootState == RootManager.RootState.DENIED) L("Grant Root (retry)") else L("Grant Root"),
                            null,
                            { showGrantGate = true },
                            primary = true,
                        )
                    }
                }
            }
        }

        // ── Quick controls (no root) ──
        IgSectionLabel(L("快速控制 · 无需root"))
        IgCard {
            LabDashToggleRow(
                L("持续性能模式"),
                L("保持稳定时钟以避免热调节降频。"),
                info = L("保持稳定时钟，使SoC不会突增和降频。无需root。"),
                checked = sustained, enabled = true,
                overridden = "sustainedPerfMode" in overridden,
                scopes = perfScopes,
                onReset = { state.onResetPerfKey?.accept("sustainedPerfMode") },
            ) { setSustained(it) }
            IgDivider()
            LabDashToggleRow(
                L("线程优先级提升"),
                L("提高模拟器线程优先级，使调度器优先处理游戏。"),
                info = L("提高模拟器的线程优先级，使CPU调度器优先处理游戏而非后台任务。"),
                checked = priority, enabled = true,
                overridden = "perfPriorityBoost" in overridden,
                scopes = perfScopes,
                onReset = { state.onResetPerfKey?.accept("perfPriorityBoost") },
            ) { setPriority(it) }
            IgDivider()
            LabDashToggleRow(
                L("优先使用大核"),
                L("将模拟器线程路由到大核/首要核心集群。"),
                info = L("将模拟器的热线程固定到大核/首要CPU集群，而非能效核心。"),
                checked = bigCores, enabled = true,
                overridden = "preferBigCores" in overridden,
                scopes = perfScopes,
                onReset = { state.onResetPerfKey?.accept("preferBigCores") },
            ) { setBig(it) }
            IgDivider()
            // GPU pin: usable without root on Adreno (KGSL turbo), upgrades to sysfs on root.
            val gpuKey = PerfRootApplier.KEY_GPU_CLOCK_LOCK
            val gpuEnabled = granted || PerfGpuTurbo.isSupported
            LabDashToggleRow(
                L("锁定 GPU 最高频率"),
                if (gpuEnabled) L("Adreno：通过/dev/kgsl-3d0固定 — 无需root即可工作。")
                else L("需要Adreno GPU，或其他GPU上的root权限。"),
                info = L("将GPU固定到其最高功率级别。在Adreno上，这使用应用拥有的KGSL加速属性（无需root）；其他GPU使用root sysfs固定。"),
                checked = toggles[gpuKey] ?: false, enabled = gpuEnabled,
                overridden = gpuKey in overridden,
                scopes = perfScopes,
                onReset = { state.onResetPerfKey?.accept(gpuKey) },
            ) { setRoot(gpuKey, it) }
        }

        // ── Root & danger ──
        IgSectionLabel(L("Root与危险"))
        IgCard {
            if (!granted) {
                IgNote(
                    "🔒 " + when (rootState) {
                        RootManager.RootState.UNAVAILABLE -> L("未检测到root管理器（Magisk / KernelSU / APatch）。")
                        else -> L("Grant root above to unlock CPU governor, frequency, cores, thermal & fan.")
                    },
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            LabDashRootRow(
                L("CPU 调度器 → 性能模式"), PerfRootApplier.KEY_CPU_GOVERNOR,
                info = L("强制每个CPU集群在本次会话中使用性能调度器。"),
                toggles = toggles, overridden = overridden, granted = granted, harnessProven = harnessProven,
                scopes = perfScopes,
                onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_CPU_GOVERNOR) },
                onEnableDanger = null, setRoot = { k, v -> setRoot(k, v) },
            )
            IgDivider()
            LabDashRootRow(
                L("锁定 CPU 最高频率"), PerfRootApplier.KEY_CPU_FREQ_LOCK,
                info = L("在每个集群上设置scaling_min_freq = scaling_max_freq。"),
                toggles = toggles, overridden = overridden, granted = granted, harnessProven = harnessProven,
                scopes = perfScopes,
                onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_CPU_FREQ_LOCK) },
                onEnableDanger = null, setRoot = { k, v -> setRoot(k, v) },
            )
            IgDivider()
            LabDashRootRow(
                L("保持所有核心在线"), PerfRootApplier.KEY_CORES_ONLINE,
                info = L("阻止内核热拔插CPU核心离线。"),
                toggles = toggles, overridden = overridden, granted = granted, harnessProven = harnessProven,
                scopes = perfScopes,
                onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_CORES_ONLINE) },
                onEnableDanger = null, setRoot = { k, v -> setRoot(k, v) },
            )
            IgDivider()
            val thermalLabel = L("Disable thermal throttling")
            LabDashRootRow(
                thermalLabel, PerfRootApplier.KEY_THERMAL_DISABLE,
                info = L("阻止热引擎限制频率。温度看门狗保持启用并可以恢复它。"),
                toggles = toggles, overridden = overridden, granted = granted, harnessProven = harnessProven,
                danger = true,
                scopes = perfScopes,
                onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_THERMAL_DISABLE) },
                onEnableDanger = { pendingDanger = LabDashPendingDanger(thermalLabel) { setRoot(PerfRootApplier.KEY_THERMAL_DISABLE, true) } },
                setRoot = { k, v -> setRoot(k, v) },
            )
            IgDivider()
            val fanLabel = L("风扇最大转速")
            LabDashRootRow(
                fanLabel, PerfRootApplier.KEY_FAN_MAX,
                info = L("强制主动散热在本次会话中全速运行。"),
                toggles = toggles, overridden = overridden, granted = granted, harnessProven = harnessProven,
                danger = true,
                scopes = perfScopes,
                onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_FAN_MAX) },
                onEnableDanger = { pendingDanger = LabDashPendingDanger(fanLabel) { setRoot(PerfRootApplier.KEY_FAN_MAX, true) } },
                setRoot = { k, v -> setRoot(k, v) },
            )
            if (granted && !harnessProven) {
                IgNote(
                    L("Thermal / fan stay locked until safety-revert is verified on this device."),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        // ── Free memory (root) ──
        if (granted) {
            IgSectionLabel(L("释放内存"))
            IgCard {
                IgRow(L("清除文件缓存"), hint = L("Frees cached files. The system reclaims cache automatically.")) {
                    IgButton(L("运行"), null, { state.onFreeMemory?.run() }, primary = true)
                }
                IgDivider()
                IgRow(L("深度清理（释放应用内存）"), hint = L("Force-closes background apps. Won't touch your game or system.")) {
                    IgButton(L("运行"), null, { state.onDeepClean?.run() }, primary = true)
                }
            }
        }

        // ── Temperature watchdog (device-wide, the same TempWatchdog singleton App Settings uses) ──
        IgSectionLabel(L("温度看门狗"))
        LabDashWatchdog()

        // ── Always-on auto-revert note ──
        Spacer(Modifier.height(10.dp))
        IgCard {
            IgInfo("🛡 " + L("退出/后台/崩溃时自动恢复 — 始终开启，无法禁用。"))
        }

        // ── Reset all per-game overrides ──
        if (overridden.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            IgButton(L("↺ 将所有游戏覆盖重置为全局"), null, { state.onResetAllPerf?.run() })
        }
        Spacer(Modifier.height(6.dp))
    }

    // ── acknowledgement dialogs ──
    pendingDanger?.let { pd ->
        LabDashDisclaimerDialog(
            title = pd.label,
            body = PerfDisclaimerCopy.ROOT_RISK,
            confirmLabel = L("启用"),
            onConfirm = { pd.enable(); pendingDanger = null },
            onDismiss = { pendingDanger = null },
        )
    }
    if (showGrantGate) {
        LabDashDisclaimerDialog(
            title = L("高级性能设置 — 请先阅读"),
            body = PerfDisclaimerCopy.ROOT_RISK,
            confirmLabel = L("Grant Root"),
            onConfirm = { showGrantGate = false; scope.launch { RootManager.requestGrant() } },
            onDismiss = { showGrantGate = false },
        )
    }

    if (showGalaxyOffPrompt) {
        SimpleActionSheet(
            title = L("Let Galaxy control performance?"),
            actions = listOf(
                MenuAction(L("Set Power profile to Off"), { applyProfile("Off"); showGalaxyOffPrompt = false }),
                MenuAction(L("保留两者"), { showGalaxyOffPrompt = false }),
            ),
            onDismiss = { showGalaxyOffPrompt = false },
            message = L("Galaxy Performance and the Power profile above both drive the CPU/GPU clocks, by different mechanisms (Samsung's SDK vs. the root perf tier). Running both at once lets them fight, so clock behaviour can be inconsistent.") +
                "\n\n" +
                L("Recommended: set the Power profile to \"Off\" so the Samsung SDK alone manages the clocks. Keep both only if you know what you're doing."),
        ) { it.run() }
    }
}

/**
 * The dashboard's window: no platform scrim and the whole panel at the panel-opacity value, so the slider shows the
 * game behind it (Classic does the same with setDimAmount(0) and a graphicsLayer alpha on its panel). Its own
 * composable so only it recomposes while the slider moves.
 */
@Composable
private fun LabDashWindowAlpha() {
    val opacity by PerformanceSettings.dialogOpacity.collectAsState()
    val view = LocalView.current
    val alpha = opacity
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        view.alpha = alpha
    }
}

/** One live readout tile: label, value + unit, and a fill bar (accent, error colour when [hot]). */
@Composable
private fun LabDashGauge(
    modifier: Modifier, label: String, value: String, unit: String,
    frac: Float, hot: Boolean, textOnly: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val bar = if (hot) cs.error else cs.primary
    IgCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label.uppercase(), color = cs.onSurfaceVariant, fontFamily = NunitoSansFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value, color = if (hot) cs.error else cs.onSurface, fontFamily = SoraFamily, fontWeight = FontWeight.Bold,
                    fontSize = if (textOnly) 15.sp else 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (unit.isNotEmpty() && value != "—") {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, color = cs.onSurfaceVariant, fontFamily = NunitoSansFamily, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            if (!textOnly) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(deckShape(5.dp)).background(cs.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).height(5.dp).clip(deckShape(5.dp)).background(bar))
                }
            }
        }
    }
}

/**
 * One perf toggle: label (⚠ when [danger]), hint, the per-game override line, "重置为全局默认值" while overridden, the
 * "?" explainer and the switch. Disabled rows grey out and drop the override line, as in Classic.
 */
@Composable
private fun LabDashToggleRow(
    label: String, sub: String, info: String, checked: Boolean, enabled: Boolean,
    overridden: Boolean, danger: Boolean = false, scopes: List<SaveScope>,
    onReset: () -> Unit, onToggle: (Boolean) -> Unit,
) {
    IgRow(
        if (danger) "⚠ $label" else label,
        hint = sub,
        note = when {
            !enabled -> null
            overridden -> L("● 每个游戏的覆盖设置")
            else -> L("○ 使用全局默认值")
        },
        scopes = scopes,
        enabled = enabled,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (enabled && overridden) IgButton(L("重置为全局默认值"), null, onReset)
            IgHelpButton(label, info)
            IgSwitch(checked, enabled = enabled, onChange = onToggle)
        }
    }
}

/** Root-tier row: gated on grant + (for thermal / fan) the revert harness; dangerous keys enable through [onEnableDanger]. */
@Composable
private fun LabDashRootRow(
    label: String, key: String, info: String,
    toggles: Map<String, Boolean>, overridden: Set<String>,
    granted: Boolean, harnessProven: Boolean, danger: Boolean = false,
    scopes: List<SaveScope>,
    onReset: () -> Unit,
    onEnableDanger: (() -> Unit)?, setRoot: (String, Boolean) -> Unit,
) {
    val gated = PerfRootApplier.isHarnessGated(key) && !harnessProven
    val enabled = granted && !gated
    LabDashToggleRow(
        label = label,
        sub = if (danger) L("Root · 危险 — 退出时自动恢复。") else L("Root控制。"),
        info = info,
        checked = toggles[key] ?: false, enabled = enabled,
        overridden = key in overridden, danger = danger,
        scopes = scopes,
        onReset = onReset,
    ) { on ->
        if (on && onEnableDanger != null) onEnableDanger() // acknowledgement gate for thermal/fan
        else setRoot(key, on)
    }
}

/**
 * Lab variant of [com.winlator.star.ui.screens.WatchdogSection]: the same TempWatchdog flows and setters, the live
 * CPU / GPU temperatures, the device trip points, the threshold presets (each explains itself), the Manual slider and
 * the hard gate before the watchdog can be turned off.
 */
@Composable
private fun LabDashWatchdog() {
    val enabled by TempWatchdog.enabled.collectAsState()
    val mode by TempWatchdog.mode.collectAsState()
    val ceiling by TempWatchdog.ceilingC.collectAsState()

    var showOffDisclaimer by remember { mutableStateOf(false) }
    var infoMode by remember { mutableStateOf<ThresholdMode?>(null) }

    // Live device temperatures, polled ~1.5 s while visible (same HudMetrics reads as Classic).
    val context = LocalContext.current
    var cpuTemp by remember { mutableStateOf<Int?>(null) }
    var gpuTemp by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val metrics = HudMetrics(context.applicationContext)
        while (true) {
            cpuTemp = metrics.cpuTempC
            gpuTemp = metrics.gpuTempC
            delay(1500)
        }
    }

    IgCard {
        IgRow(
            L("Thermal auto-revert (~%1\$s °C · %2\$s)", ceiling, labDashModeLabel(mode)),
            hint = if (TempWatchdog.hasDeviceTrips)
                L("Your device: throttles ~%1\$s °C · hard limit %2\$s °C", TempWatchdog.firstTripC, TempWatchdog.topTripC)
            else
                L("Your device didn't expose thermal limits — using a safe %1\$s °C fallback.", TempWatchdog.FALLBACK_CEILING_C),
            note = L("CPU %1\$s · GPU %2\$s", cpuTemp?.let { "$it °C" } ?: "—", gpuTemp?.let { "$it °C" } ?: "—"),
            scopes = listOf(SaveScope.ALL),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IgHelpButton(L("什么是温度看门狗?"), com.winlator.star.ui.screens.WATCHDOG_WHATS_THIS)
                IgSwitch(enabled) { on -> if (on) TempWatchdog.setWatchdogEnabled(true) else showOffDisclaimer = true }
            }
        }
        IgDivider()
        IgRow(L("Threshold"), scopes = listOf(SaveScope.ALL), wideControl = true) {
            IgChips(
                options = ThresholdMode.values().map { m ->
                    m to L("%1\$s · ~%2\$s °C", labDashModeLabel(m), TempWatchdog.resolvedCeilingFor(m))
                },
                selected = mode,
            ) { m ->
                TempWatchdog.setMode(m)
                if (m != ThresholdMode.MANUAL) infoMode = m // presets explain themselves
            }
        }
        // Manual slider (only in MANUAL mode).
        if (mode == ThresholdMode.MANUAL) {
            val manual by TempWatchdog.manualCeilingC.collectAsState()
            val top = TempWatchdog.topTripC ?: TempWatchdog.MANUAL_MAX_C
            val sliderMax = minOf(TempWatchdog.MANUAL_MAX_C, top).coerceAtLeast(TempWatchdog.MANUAL_MIN_C + 1)
            var sliderVal by remember(manual) { mutableStateOf(manual.toFloat()) }
            val warn = sliderVal.roundToInt() > (top - 5)
            IgDivider()
            IgSliderRow(
                title = L("Manual: %1\$s °C", sliderVal.roundToInt()),
                value = sliderVal,
                range = TempWatchdog.MANUAL_MIN_C.toFloat()..sliderMax.toFloat(),
                step = 1f,
                format = { L("%1\$s °C", it.roundToInt()) },
                onChange = { sliderVal = it },
                onFinished = { TempWatchdog.setManualCeilingC(sliderVal.roundToInt()) },
                scopes = listOf(SaveScope.ALL),
            )
            if (warn) {
                IgNote(L("⚠ close to the hard limit (%1\$s °C)", top), error = true, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }

    // Info sheet for the picked preset.
    infoMode?.let { m ->
        LabInfoSheet(
            L("%1\$s · ~%2\$s °C", labDashModeLabel(m), TempWatchdog.resolvedCeilingFor(m)),
            com.winlator.star.ui.screens.presetInfo(m),
        ) { infoMode = null }
    }

    // Hard disclaimer to disarm the watchdog.
    if (showOffDisclaimer) {
        LabDashDisclaimerDialog(
            title = L("禁用温度保护?"),
            body = PerfDisclaimerCopy.WATCHDOG_OFF,
            confirmLabel = L("关闭看门狗"),
            onDismiss = { showOffDisclaimer = false },
            onConfirm = { showOffDisclaimer = false; TempWatchdog.setWatchdogEnabled(false) },
        )
    }
}

private fun labDashModeLabel(mode: ThresholdMode): String = when (mode) {
    ThresholdMode.CONSERVATIVE -> L("保守")
    ThresholdMode.BALANCED -> L("Balanced")
    ThresholdMode.AGGRESSIVE -> L("激进")
    ThresholdMode.MANUAL -> L("手动")
}

/**
 * Lab variant of [com.winlator.star.ui.screens.PerfDisclaimerDialog]: the risk text must be scrolled to its end AND
 * "我理解并承担所有风险" switched on before [confirmLabel] works. With a controller the text box takes
 * focus and d-pad up / down scrolls it; at the end, down moves on to the switch.
 */
@Composable
private fun LabDashDisclaimerDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var accepted by remember { mutableStateOf(false) }
    // Content that fits without scrolling (maxValue 0) counts as seen in full; otherwise the scroll must reach the end.
    val reachedBottom by remember {
        derivedStateOf { scroll.maxValue == 0 || scroll.value >= scroll.maxValue - 8 }
    }
    val canConfirm = reachedBottom && accepted

    LabInGameDialog(
        title = title,
        onDismiss = onDismiss,
        maxWidth = 560.dp,
        scroll = false,
        footer = {
            IgButton(L("Cancel"), null, onDismiss)
            IgButton(confirmLabel, null, { if (canConfirm) onConfirm() }, danger = true, enabled = canConfirm)
        },
    ) {
        val shape = deckShape(10.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionDown -> if (scroll.value < scroll.maxValue) {
                            scope.launch { scroll.animateScrollTo(scroll.value + 160) }
                            true
                        } else false
                        Key.DirectionUp -> if (scroll.value > 0) {
                            scope.launch { scroll.animateScrollTo((scroll.value - 160).coerceAtLeast(0)) }
                            true
                        } else false
                        else -> false
                    }
                }
                .deckFocusRing(shape, scaleTo = 1f)
                .focusable()
                .verticalScroll(scroll)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(body, color = SimpleStrong, fontFamily = NunitoSansFamily, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (!reachedBottom) {
            IgNote(L("滚动到底部以继续。"), modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        IgCard {
            IgRow(L("我理解并承担所有风险"), enabled = reachedBottom) {
                IgSwitch(accepted, enabled = reachedBottom) { accepted = it }
            }
        }
    }
}

/** Parse a leading integer out of a readout like "650", "650 MHz", "47°C". Null when none. */
private fun labDashLeadingInt(s: String?): Int? =
    s?.trim()?.takeWhile { it.isDigit() }?.takeIf { it.isNotEmpty() }?.toIntOrNull()

/** A Classic "?" help string (small HTML: <b>, <br/>) as plain text for a Lab help sheet. */
@Composable
private fun labDashHelpText(@StringRes res: Int): String {
    val ctx = LocalContext.current
    return remember(res) { com.winlator.star.ui.screens.htmlToAnnotated(ctx.getString(res)).text }
}

/* ═══════════════════ Audio ═══════════════════ */

/**
 * Lab variant of [com.winlator.star.ui.components.AudioSettingsDialog], same parameters and semantics: the tradeoff
 * meter, the five presets (each with its "?"), the fine-tune rows (editable on Custom, otherwise greyed and showing
 * what the engine really applies), the "next launch" tag on the latency row, and Save → [onSave] with the edited config.
 */
@Composable
internal fun LabAudioSettingsDialog(
    initial: AudioConfig,
    scopeLabel: String,
    latencyLive: Boolean,
    driverLabel: String = "",
    driverId: String = "",
    onDismiss: () -> Unit,
    onSave: (AudioConfig) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var cfg by remember { mutableStateOf(initial) }
    val custom = cfg.preset == PRESET_CUSTOM
    val curPreset = AUDIO_PRESETS.first { it.id == cfg.preset }
    // On DirectAudio the latency field sets the driver's buffer, so a preset must not stamp its Pulse-shaped value over it.
    val directBuffer = latencyIsDirectBuffer(driverId)
    fun applyPreset(p: AudioPreset) {
        cfg = if (directBuffer) p.cfg.copy(latencyMsec = cfg.latencyMsec) else p.cfg
    }

    LabInGameDialog(
        title = L("音频设置"),
        onDismiss = onDismiss,
        subtitle = L("Balance crackle-free vs low delay") + "  ·  " + scopeLabel,
        maxWidth = 680.dp,
        tall = true,
        // Engine badge: which audio engine these settings hit.
        actions = { if (driverLabel.isNotBlank()) IgBadge("🔊 $driverLabel") },
        footer = {
            IgButton(L("Cancel"), null, onDismiss)
            IgButton(L("Save"), null, { onSave(cfg) }, primary = true)
        },
    ) {
        // ── Tradeoff meter ──
        IgCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(L("◀ 降低延迟"), color = cs.onSurfaceVariant, fontFamily = NunitoSansFamily, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(L("减少爆音 ▶"), color = cs.onSurfaceVariant, fontFamily = NunitoSansFamily, fontSize = 12.sp)
                }
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(deckShape(6.dp)).background(cs.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(curPreset.pos).height(8.dp).clip(deckShape(6.dp)).background(cs.primary))
                }
                Text(L(curPreset.note), color = cs.onSurfaceVariant, fontFamily = NunitoSansFamily, fontSize = 12.5.sp, modifier = Modifier.padding(top = 9.dp))
            }
        }

        // ── Presets ──
        IgSectionLabel(L("预设"))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AUDIO_PRESETS.forEach { p ->
                LabAudioPresetTile(p, selected = p.id == cfg.preset, help = labDashHelpText(p.helpRes)) { applyPreset(p) }
            }
        }

        // ── Fine-tune ──
        IgSectionLabel(if (custom) L("微调") else L("Fine-tune — what this preset applies (Custom to edit)"))
        // Greyed rows show what the engine will really use (effectiveConfigFor); edits still go to cfg, and the two are
        // equal whenever the rows are editable.
        val shown = effectiveConfigFor(cfg, driverId)
        IgCard {
            // Output mode. DirectAudio offers no Power: its AAudio power-saving stream reopens ~1/s under box64/FEX.
            val perfOpts = if (driverId == "directaudio")
                listOf(0 to L("None"), 1 to L("低延迟。"))
            else listOf(0 to L("None"), 1 to L("低延迟。"), 2 to L("Power"))
            IgRow(L("输出模式"), hint = L("AAudio性能模式"), enabled = custom, wideControl = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IgHelpButton(L("输出模式"), labDashHelpText(R.string.help_audio_perf_mode))
                    IgSegmented(perfOpts, shown.perfMode, enabled = custom) { v -> cfg = cfg.copy(perfMode = v) }
                }
            }
            IgDivider()
            IgRow(L("自适应缓冲区"), hint = L("仅在检测到爆音时自动增长"), enabled = custom) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IgHelpButton(L("自适应缓冲区"), labDashHelpText(R.string.help_audio_adaptive))
                    IgSwitch(shown.adaptive, enabled = custom) { cfg = cfg.copy(adaptive = it) }
                }
            }
            IgDivider()
            // DirectAudio: the driver's own buffer (reaches the one-burst floor, label states the total with Android's
            // ~21 ms). PulseAudio: the guest winepulse buffer. ALSA: not used.
            val latencyUsed = latencyUsedBy(driverId)
            val latTitle = if (directBuffer) L("音频缓冲区") else L("客户缓冲区")
            val latHint = if (!latencyUsed) L("ALSA不使用 — 请在下方设置缓冲区行数")
            else (if (directBuffer) L("≈%1\$s ms total · %2\$s ms buffer", shown.latencyMsec + ANDROID_OUTPUT_MS, shown.latencyMsec)
                  else L("winepulse · %1\$s ms", shown.latencyMsec)) +
                if (!latencyLive) "  " + L("· next launch") else ""
            val latHelp = when {
                !latencyUsed -> R.string.help_audio_latency_unused
                directBuffer -> R.string.help_audio_direct_buffer
                else -> R.string.help_audio_guest_buffer
            }
            IgRow(latTitle, hint = latHint, enabled = custom, wideControl = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IgHelpButton(latTitle, labDashHelpText(latHelp))
                    Box(Modifier.weight(1f)) {
                        if (directBuffer) {
                            IgSlider(
                                value = shown.latencyMsec.toFloat(),
                                range = 4f..120f,
                                step = 4f,
                                enabled = custom && latencyUsed,
                                format = { L("%1\$s ms", it.toInt()) },
                                onChange = { cfg = cfg.copy(latencyMsec = (it / 4).toInt() * 4) },
                                onFinished = {},
                            )
                        } else {
                            IgSlider(
                                value = shown.latencyMsec.toFloat(),
                                range = 20f..200f,
                                step = 10f,
                                enabled = custom && latencyUsed,
                                format = { L("%1\$s ms", it.toInt()) },
                                onChange = { cfg = cfg.copy(latencyMsec = (it / 10).toInt() * 10) },
                                onFinished = {},
                            )
                        }
                    }
                }
            }
            IgDivider()
            LabAudioStepperRow(
                L("初始sink缓冲区"), L("帧数 · 0 = 自动"),
                if (shown.bufferFrames > 0) "${shown.bufferFrames}" else L("auto"), custom,
                help = labDashHelpText(R.string.help_audio_buffer_frames),
            ) { cfg = cfg.copy(bufferFrames = (cfg.bufferFrames + it * 256).coerceAtLeast(0)) }
            IgDivider()
            LabAudioStepperRow(
                L("最大sink缓冲区"), L("增长上限 · 0 = 设备最大值"),
                if (shown.maxBufferFrames > 0) "${shown.maxBufferFrames}" else L("设备最大值"), custom,
                help = labDashHelpText(R.string.help_audio_max_buffer_frames),
            ) { cfg = cfg.copy(maxBufferFrames = (cfg.maxBufferFrames + it * 256).coerceAtLeast(0)) }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * One preset: a radio mark, the emoji, name + badge, the one-line description, and its own "?" (which explains the
 * preset without picking it). A or a tap on the tile picks it. The "?" sits beside the pickable part, not inside it,
 * so the d-pad reaches it (right from the tile).
 */
@Composable
private fun LabAudioPresetTile(p: AudioPreset, selected: Boolean, help: String, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = deckShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) cs.primary.copy(alpha = 0.14f) else cs.surfaceContainerHigh.copy(alpha = igRaised(0.85f)))
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else deckLineAt(0.4f), shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .deckFocusRing(shape, scaleTo = 1f)
                .clickable(role = Role.RadioButton, onClick = onPick)
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Box(
                Modifier.size(18.dp).border(2.dp, if (selected) cs.primary else cs.onSurfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(9.dp).background(cs.primary, CircleShape))
            }
            Spacer(Modifier.width(10.dp))
            Text(p.emoji, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L(p.name), color = cs.onSurface, fontFamily = NunitoSansFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    p.badge?.let {
                        Spacer(Modifier.width(8.dp))
                        IgBadge(L(it).uppercase())
                    }
                }
                Text(L(p.desc), color = cs.onSurfaceVariant, fontFamily = NunitoSansFamily, fontSize = 12.5.sp, lineHeight = 16.sp)
            }
        }
        Box(Modifier.padding(end = 10.dp)) { IgHelpButton(L(p.name), help) }
    }
}

/** A frames row with − / value / + (256-frame steps); the "?" stays usable while the row is greyed. */
@Composable
private fun LabAudioStepperRow(
    name: String, hint: String, value: String, enabled: Boolean, help: String,
    onStep: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    IgRow(name, hint = hint, enabled = enabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IgHelpButton(name, help)
            IgButton("−", null, { onStep(-1) }, enabled = enabled)
            Text(
                value, color = cs.primary, fontFamily = NunitoSansFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.widthIn(min = 72.dp),
            )
            IgButton("+", null, { onStep(1) }, enabled = enabled)
        }
    }
}
