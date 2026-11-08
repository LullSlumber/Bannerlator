package com.winlator.star.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.winlator.star.container.Shortcut
import com.winlator.star.perf.PerfNodeResolver
import com.winlator.star.perf.PerfRootApplier
import com.winlator.star.perf.PerformanceSettings
import com.winlator.star.perf.RootManager
import com.winlator.star.ui.components.PlayerSlotEditorRow
import com.winlator.star.ui.components.buildPlayerSlotEditorRows
import com.winlator.star.widget.HudMetrics
import com.winlator.star.winhandler.WinHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Performance (PerformanceDashboardDialogPerGame) ─────────────────────────────────────────────────
// Per-game overrides of the App Settings > Performance defaults, stored override-when-different exactly
// as the editor's save(): perfExtraOrNull writes the key only when it differs from the global default,
// otherwise clears it so the game re-inherits. Toggling a row back to the global value is its Reset.

private val P2_PERF_NO_ROOT = listOf("sustainedPerfMode", "perfPriorityBoost", "preferBigCores")
private val P2_PERF_KEYS = P2_PERF_NO_ROOT + PerfRootApplier.ROOT_KEYS
private val P2_PROFILES = listOf("Off", "Battery", "Balanced", "Performance", "Turbo")

private fun p2PerfGlobal(key: String): Boolean = when (key) {
    "sustainedPerfMode" -> PerformanceSettings.sustainedPerfMode.value
    "perfPriorityBoost" -> PerformanceSettings.perfPriorityBoost.value
    "preferBigCores" -> PerformanceSettings.preferBigCores.value
    else -> PerformanceSettings.rootDefaultValue(key)
}

/** Effective seed = the per-game override if present, else the global default (no container level). */
private fun p2PerfValue(s: Shortcut, key: String): Boolean = s.p2Ex(key, if (p2PerfGlobal(key)) "1" else "0") == "1"

private fun p2ReadGovernor(): String? {
    for (core in PerfNodeResolver.cpuCores()) {
        val g = core.governor ?: continue
        val v = runCatching { java.io.File(g).readText().trim() }.getOrNull()
        if (!v.isNullOrEmpty()) return v
    }
    return null
}

internal fun xmbPerformanceMenu(xmb: XmbScope, shortcut: Shortcut): XmbMenu {
    val jobs = P2Jobs(xmb)
    var profile by mutableStateOf<String?>(null)
    var readouts by mutableStateOf<Map<String, String>>(emptyMap())
    var rootState by mutableStateOf(RootManager.state.value)

    // Current device state for the gauges (the dialog polls HudMetrics every ~1.5 s while open).
    jobs.scope.launch {
        val hud = HudMetrics(xmb.context)
        while (true) {
            val m = withContext(Dispatchers.IO) {
                val out = HashMap<String, String>()
                runCatching {
                    out["gpuMhz"] = hud.getGpuClockMhz()?.let { "$it MHz" } ?: "—"
                    val cpuT = hud.getCpuTempC()
                    val gpuT = hud.getGpuTempC()
                    val soc = if (cpuT != null && gpuT != null) maxOf(cpuT, gpuT) else (cpuT ?: gpuT)
                    out["socTemp"] = soc?.let { "$it °C" } ?: "—"
                    out["governor"] = p2ReadGovernor() ?: "—"
                }
                out
            }
            readouts = m
            rootState = RootManager.state.value
            delay(1500)
        }
    }

    fun write(values: List<Pair<String, Boolean>>) {
        p2PutAll(xmb, shortcut, values.map { (k, v) -> k to perfExtraOrNull(v, p2PerfGlobal(k)) })
    }
    fun setOne(key: String, v: Boolean) = write(listOf(key to v))

    fun applyProfile(p: String) {
        profile = p
        val off = listOf(
            PerfRootApplier.KEY_GPU_CLOCK_LOCK to false, PerfRootApplier.KEY_CPU_GOVERNOR to false,
            PerfRootApplier.KEY_CPU_FREQ_LOCK to false, PerfRootApplier.KEY_CORES_ONLINE to false,
        )
        when (p) {
            // Off neutralises the root tier so Galaxy Performance can own the clocks; Battery matches it.
            "Off", "Battery" -> write(listOf("sustainedPerfMode" to false, "perfPriorityBoost" to false, "preferBigCores" to false) + off)
            "Balanced" -> write(listOf("sustainedPerfMode" to true, "perfPriorityBoost" to false, "preferBigCores" to false,
                PerfRootApplier.KEY_GPU_CLOCK_LOCK to false))
            else -> write(listOf("sustainedPerfMode" to true, "perfPriorityBoost" to true, "preferBigCores" to true,
                PerfRootApplier.KEY_GPU_CLOCK_LOCK to true, PerfRootApplier.KEY_CPU_GOVERNOR to true,
                PerfRootApplier.KEY_CPU_FREQ_LOCK to true, PerfRootApplier.KEY_CORES_ONLINE to true))
        }
    }

    return XmbMenu("Performance", Icons.Filled.Speed, onClose = { jobs.cancel() }) {
        val rows = mutableListOf<XmbRow>()
        val granted = rootState == RootManager.RootState.GRANTED

        fun toggle(key: String, label: String, sub: String, danger: Boolean = false) {
            val v = p2PerfValue(shortcut, key)
            val global = p2PerfGlobal(key)
            rows += XmbRow.Toggle("perf:$key", label, if (danger) Icons.Filled.Warning else Icons.Filled.Bolt, v,
                subtitle = if (v != global) "Overridden for this game · global is ${if (global) "on" else "off"}" else sub) { on ->
                profile = null
                if (on && danger) {
                    xmb.confirm(XmbConfirm(label, "使用风险自负 — 写入系统时钟/温度/风扇文件；可能导致设备过热或损坏。", "启用", danger = true)) {
                        setOne(key, true)
                    }
                } else setOne(key, on)
            }
        }

        rows += XmbRow.Header("hNow", "此设备当前")
        rows += XmbRow.Info("gpuClock", "GPU clock", Icons.Filled.Memory, readouts["gpuMhz"] ?: "…")
        rows += XmbRow.Info("socTemp", "SoC temp", Icons.Filled.Speed, readouts["socTemp"] ?: "…")
        rows += XmbRow.Info("governor", "Governor", Icons.Filled.Tune, readouts["governor"] ?: "…")

        rows += XmbRow.Header("hProfile", "电源配置")
        val profOpts = if (profile == null) listOf("Custom") + P2_PROFILES else P2_PROFILES
        rows += XmbRow.Choice("profile", "电源配置", Icons.Filled.Speed, profOpts, profile ?: "Custom",
            subtitle = "一次性设置以下开关", disabledOptions = setOf("Custom")) { v -> applyProfile(v) }

        rows += XmbRow.Header("hQuick", "快速控制 · 无需root")
        toggle("sustainedPerfMode", "持续性能模式", "保持稳定时钟以避免热调节降频")
        toggle("perfPriorityBoost", "线程优先级提升", "提高模拟器线程优先级，使调度器优先处理游戏")
        toggle("preferBigCores", "优先使用大核", "将模拟器线程路由到大核/首要集群")
        toggle(PerfRootApplier.KEY_GPU_CLOCK_LOCK, "锁定 GPU 最高频率", "Adreno：无root的KGSL加速；有root的sysfs固定")

        rows += XmbRow.Header("hRoot", "Root与危险")
        rows += XmbRow.Info("rootState", if (granted) "已授予root" else "为此游戏保存 · 在root可用时启动时应用",
            if (granted) Icons.Filled.Info else Icons.Filled.Warning)
        if (!granted) {
            rows += XmbRow.Action("grantRoot", "Grant root", Icons.Filled.Warning, subtitle = "高级用户控制 — 请先阅读警告") {
                xmb.confirm(XmbConfirm("高级用户性能", "使用风险自负 — 这些控制会写入系统文件，可能导致设备过热或损坏。", "Grant Root", danger = true)) {
                    xmb.scope.launch {
                        RootManager.requestGrant()
                        rootState = RootManager.state.value
                    }
                }
            }
        }
        toggle(PerfRootApplier.KEY_CPU_GOVERNOR, "CPU 调度器 → 性能模式", "强制每个集群使用性能调度器")
        toggle(PerfRootApplier.KEY_CPU_FREQ_LOCK, "锁定 CPU 最高频率", "每个集群上 scaling_min_freq = scaling_max_freq")
        toggle(PerfRootApplier.KEY_CORES_ONLINE, "保持所有核心在线", "阻止内核将核心热拔插离线")
        toggle(PerfRootApplier.KEY_THERMAL_DISABLE, "Disable thermal throttling", "运行时看门狗仍然守护", danger = true)
        toggle(PerfRootApplier.KEY_FAN_MAX, "风扇最大转速", "Forces active cooling to full at launch", danger = true)

        val anyOverride = P2_PERF_KEYS.any { p2PerfValue(shortcut, it) != p2PerfGlobal(it) }
        if (anyOverride) {
            rows += XmbRow.Action("resetAll", "全部重置为全局", Icons.Filled.RestartAlt, subtitle = "此游戏再次遵循应用设置") {
                profile = null
                write(P2_PERF_KEYS.map { it to p2PerfGlobal(it) })
            }
        }
        rows
    }
}

// ── Player slots (PlayerSlotsEditor, per-game) ──────────────────────────────────────────────────────
// "controllerSlotOverrides" holds the same JSON the container editor and the in-game Players tab use,
// only ever parsed/built by WinHandler. Empty = inherit the container's Player Slots.

private val P2_SLOT_LABELS = listOf("Auto", "Player 1", "Player 2", "Player 3", "Player 4", "Ignore")
private fun p2SlotValue(label: String): Int = when (label) {
    "Player 1" -> 0
    "Player 2" -> 1
    "Player 3" -> 2
    "Player 4" -> 3
    "Ignore" -> WinHandler.SLOT_IGNORE
    else -> -1
}
private fun p2SlotLabel(v: Int): String = when {
    v == WinHandler.SLOT_IGNORE -> "Ignore"
    v in 0..3 -> "Player ${v + 1}"
    else -> "Auto"
}

internal fun xmbPlayerSlotsMenu(xmb: XmbScope, shortcut: Shortcut): XmbMenu {
    fun saved(): String = shortcut.p2Ex("controllerSlotOverrides", "")
    fun build(): List<PlayerSlotEditorRow> = runCatching { buildPlayerSlotEditorRows(saved().ifEmpty { "{}" }) }.getOrDefault(emptyList())
    // Enumerating input devices isn't free: rebuilt on open, after each change and on Refresh.
    var slotRows by mutableStateOf(build())

    fun pin(descriptor: String, value: Int) {
        val map = WinHandler.parseSlotOverridesJson(saved().ifEmpty { "{}" }).toMutableMap()
        if (value == -1) map.remove(descriptor) else map[descriptor] = value
        p2Put(xmb, shortcut, "controllerSlotOverrides", WinHandler.buildSlotOverridesJson(map).ifEmpty { null })
        slotRows = build()
    }

    return XmbMenu("玩家槽位", Icons.Filled.Gamepad) {
        val rows = mutableListOf<XmbRow>()
        val json = saved()
        if (json.isEmpty()) {
            rows += XmbRow.Info("inherit", "继承容器的玩家槽位。在下方固定一个控制器以设置每个游戏的覆盖。", Icons.Filled.Info)
        } else {
            rows += XmbRow.Action("useDefault", "使用容器默认", Icons.Filled.RestartAlt, subtitle = "Drop this game's pins") {
                p2Put(xmb, shortcut, "controllerSlotOverrides", null)
                slotRows = build()
            }
        }
        rows += XmbRow.Info("about", "将控制器（或屏幕手柄）固定到玩家槽位。启动时应用。", Icons.Filled.Info)
        rows += XmbRow.Action("refresh", "刷新控制器", Icons.Filled.Refresh, subtitle = "重新扫描已连接的手柄") { slotRows = build() }
        if (slotRows.isEmpty()) {
            rows += XmbRow.Info("none", "No controllers detected. Connect one to assign it.", Icons.Filled.Info)
        }
        val seen = HashSet<String>()
        slotRows.forEach { r ->
            if (!seen.add(r.descriptor)) return@forEach
            val status = when {
                r.override == WinHandler.SLOT_IGNORE -> "Ignored"
                r.override >= 0 -> "Pinned to Player ${r.override + 1}"
                else -> "自动（按启动顺序分配）"
            }
            val suffix = when {
                r.isOnScreen -> " · 屏幕控制"
                !r.connected -> " · not connected (saved pin)"
                else -> ""
            }
            rows += XmbRow.Choice("slot:${r.descriptor}", r.displayName, if (r.isOnScreen) Icons.Filled.TouchApp else Icons.Filled.Gamepad,
                P2_SLOT_LABELS, p2SlotLabel(r.override), subtitle = status + suffix) { v ->
                pin(r.descriptor, p2SlotValue(v))
            }
        }
        rows
    }
}
