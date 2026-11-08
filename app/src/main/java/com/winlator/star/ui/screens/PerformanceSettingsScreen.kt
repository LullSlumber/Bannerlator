package com.winlator.star.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.star.perf.PerfGpuTurbo
import com.winlator.star.perf.PerfRootApplier
import com.winlator.star.perf.PerformanceSettings
import com.winlator.star.perf.RootManager
import kotlinx.coroutines.launch

/**
 * App Settings → Performance menu. Binds the GLOBAL DEFAULTS (non-root three + root six) to the same
 * [PerformanceSettings] flows the in-game drawer reads, so a change here is reflected live in the
 * other surface (two-way sync via one store).
 *
 * Root tier: a real grant gate (scroll-to-bottom + accept disclaimer -> [RootManager.requestGrant]),
 * then the root toggles applied live through [PerfRootApplier] (snapshot-before-write; reverted on
 * exit/background/crash). The two dangerous toggles (thermal disable, fan max) stay disabled until the
 * safety harness is proven. The temperature watchdog is device-wide; turning it OFF requires its own
 * hard disclaimer.
 */
@Composable
fun PerformanceSettingsScreen(onClose: () -> Unit) {
    val sustained by PerformanceSettings.sustainedPerfMode.collectAsState()
    val priority by PerformanceSettings.perfPriorityBoost.collectAsState()
    val bigCores by PerformanceSettings.preferBigCores.collectAsState()
    val rootState by PerformanceSettings.rootState.collectAsState()
    val harnessProven by PerformanceSettings.harnessProven.collectAsState()
    // "启动时自动深度清理" (Tier 2) global default — persisted via the same root-default store.
    val autoDeepClean by PerformanceSettings.rootDefaultFlow(PerfRootApplier.KEY_AUTO_DEEP_CLEAN).collectAsState()

    val scope = rememberCoroutineScope()
    var showRootDisclaimer by remember { mutableStateOf(false) }
    // Per-toggle "?" info dialog: (title, body). And the consolidated "开关说明" sheet.
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showExplainAll by remember { mutableStateOf(false) }

    val granted = rootState == RootManager.RootState.GRANTED

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Performance",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Text(
                "Global defaults apply to every game unless a game sets its own override " +
                    "(in the game's settings or from the in-game menu).",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
            )

            // ── Non-root global defaults (always editable) ──
            PerfCard(title = "全局默认值") {
                PerfToggle("持续性能模式", sustained,
                    onInfo = { info = "持续性能模式" to PerfCopy.SUSTAINED }) { PerformanceSettings.setSustainedPerfMode(it) }
                PerfToggle("线程优先级提升", priority,
                    onInfo = { info = "线程优先级提升" to PerfCopy.PRIORITY }) { PerformanceSettings.setPerfPriorityBoost(it) }
                PerfToggle("优先使用大核", bigCores,
                    onInfo = { info = "优先使用大核" to PerfCopy.BIG_CORES }) { PerformanceSettings.setPreferBigCores(it) }
                // GPU pin lives here (not in the root card) because it now has a non-root path on
                // Adreno. It still upgrades itself to the stronger sysfs pin when root is granted.
                RootToggle(PerfRootApplier.KEY_GPU_CLOCK_LOCK, "锁定 GPU 最高频率", granted, harnessProven,
                    onInfo = { info = "锁定 GPU 最高频率" to PerfCopy.GPU_CLOCK })
                if (!granted && !PerfGpuTurbo.isSupported) {
                    Text("将 GPU 锁定到最高频率需要 Adreno（高通）GPU，或在其他 GPU 上需要 root 权限。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }

            // ── Root tier ──
            PerfCard(title = "Root 性能控制") {
                Text("Root 状态: " + rootStateLabel(rootState),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                when (rootState) {
                    RootManager.RootState.UNAVAILABLE -> {
                        Text("未检测到 Root 管理器（Magisk / KernelSU / APatch）。Root 控制项保持禁用。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    RootManager.RootState.GRANTED -> { /* toggles below are enabled */ }
                    else -> {
                        // AVAILABLE_NOT_GRANTED or DENIED -> offer (or re-offer) the grant.
                        val label = if (rootState == RootManager.RootState.DENIED) "Grant Root (retry)" else "Grant Root"
                        Button(onClick = { showRootDisclaimer = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                RootToggle(PerfRootApplier.KEY_CPU_GOVERNOR, "CPU 调度器 → 性能模式", granted, harnessProven,
                    onInfo = { info = "CPU 调度器 → 性能模式" to PerfCopy.CPU_GOV })
                RootToggle(PerfRootApplier.KEY_CPU_FREQ_LOCK, "锁定 CPU 最高频率", granted, harnessProven,
                    onInfo = { info = "锁定 CPU 最高频率" to PerfCopy.CPU_FREQ })
                RootToggle(PerfRootApplier.KEY_CORES_ONLINE, "保持所有核心在线", granted, harnessProven,
                    onInfo = { info = "保持所有核心在线" to PerfCopy.CORES_ONLINE })
                RootToggle(PerfRootApplier.KEY_THERMAL_DISABLE, "Disable thermal throttling", granted, harnessProven,
                    onInfo = { info = "Disable thermal throttling" to PerfCopy.THERMAL })
                RootToggle(PerfRootApplier.KEY_FAN_MAX, "风扇最大转速", granted, harnessProven,
                    onInfo = { info = "风扇最大转速" to PerfCopy.FAN })

                if (granted && !harnessProven) {
                    Text("Thermal / fan controls are locked until safety-revert is verified on this device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }

                // ── Free memory (dual tier). Section "?" explains both tiers + the auto toggle. ──
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("释放内存", color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    InfoButton { info = "释放内存" to PerfCopy.FREE_MEM_SECTION }
                }

                // TIER 1 — drop file caches (light; near-invisible RAM by design). Root-gated as before.
                Button(
                    onClick = { PerfRootApplier.freeMemoryNow() },
                    enabled = granted,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("清除文件缓存", color = MaterialTheme.colorScheme.onPrimary) }
                Text("释放缓存文件。可见 RAM 变化很小；系统会自动回收缓存。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                // TIER 2 — deep clean (root-only; the real RAM free via `am kill-all`).
                Button(
                    onClick = { PerfRootApplier.deepCleanMemory() },
                    enabled = granted,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("深度清理（释放应用内存）", color = MaterialTheme.colorScheme.onPrimary) }
                Text("强制关闭后台应用以释放真正的内存。不会影响您的游戏或系统。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                // AUTO toggle — persists like the other root defaults; fires at game launch.
                PerfToggle("启动时自动深度清理", autoDeepClean, enabled = granted,
                    onInfo = { info = "启动时自动深度清理" to PerfCopy.AUTO_DEEP_CLEAN }) {
                    PerformanceSettings.setRootDefault(PerfRootApplier.KEY_AUTO_DEEP_CLEAN, it)
                }
            }

            // ── Temperature watchdog (device-wide; not root-gated). Shared control block, identical
            // and synced with the in-game surface (both bind the one TempWatchdog singleton). ──
            PerfCard(title = "温度看门狗") {
                Text(
                    "Auto-reverts all performance settings before the device gets too hot, anchored to " +
                        "your device's own thermal trip points. Keep this on unless you know what you're doing.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
                WatchdogSection()
            }

            Text(
                "游戏退出、应用进入后台或崩溃时自动恢复始终开启且无法禁用。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
            )

            Button(onClick = { showExplainAll = true }, modifier = Modifier.fillMaxWidth()) {
                Text("开关说明", color = MaterialTheme.colorScheme.onPrimary)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    info?.let { (title, body) ->
        PerfInfoDialog(title = title, body = body, onDismiss = { info = null })
    }

    if (showExplainAll) {
        PerfInfoDialog(title = "各开关功能说明", body = explainAllBody(), onDismiss = { showExplainAll = false })
    }

    if (showRootDisclaimer) {
        PerfDisclaimerDialog(
            title = "启用",
            body = PerfDisclaimerCopy.ROOT_RISK,
            confirmLabel = "Grant Root",
            onDismiss = { showRootDisclaimer = false },
            onConfirm = {
                showRootDisclaimer = false
                scope.launch { RootManager.requestGrant() } // fires the su prompt; state updates live
            }
        )
    }
}

/** A PerfRootApplier-owned toggle bound to its global default; applies live via PerfRootApplier. */
@Composable
private fun RootToggle(key: String, label: String, granted: Boolean, harnessProven: Boolean, onInfo: () -> Unit) {
    val checked by PerformanceSettings.rootDefaultFlow(key).collectAsState()
    val gated = PerfRootApplier.isHarnessGated(key) && !harnessProven
    // The GPU pin is usable without root on Adreno (KGSL turbo); everything else needs the grant.
    val usableWithoutRoot = key == PerfRootApplier.KEY_GPU_CLOCK_LOCK && PerfGpuTurbo.isSupported
    val enabled = (granted || usableWithoutRoot) && !gated
    PerfToggle(label, checked, enabled = enabled, onInfo = onInfo) { on ->
        PerformanceSettings.setRootDefault(key, on)
        PerfRootApplier.apply(key, on)
    }
}

private fun rootStateLabel(state: RootManager.RootState): String = when (state) {
    RootManager.RootState.UNKNOWN -> "检查中…"
    RootManager.RootState.UNAVAILABLE -> "此设备不可用"
    RootManager.RootState.AVAILABLE_NOT_GRANTED -> "可用（未授予）"
    RootManager.RootState.GRANTED -> "granted"
    RootManager.RootState.DENIED -> "denied"
}

@Composable
private fun PerfCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun PerfToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onInfo: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // Only the label + switch dim/gate on `enabled`; the "?" stays live so a locked toggle is
        // still explainable.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier.alpha(0.4f))
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
        if (onInfo != null) InfoButton(onInfo)
        Spacer(Modifier.width(4.dp))
        Row(modifier = if (enabled) Modifier else Modifier.alpha(0.4f)) {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/** Small "?" affordance that opens a soft info dialog. Always live (even for disabled toggles). */
@Composable
private fun InfoButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Outlined.HelpOutline, "这是什么?",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

private object PerfCopy {
    const val SUSTAINED = "保持时钟频率稳定，而非频繁升降，让长时间游戏更加流畅。安全，无需 root。"
    const val PRIORITY = "为游戏渲染赋予高于其他应用的优先级，以获得更多 CPU 时间。安全，无需 root。"
    const val BIG_CORES = "将游戏绑定到最快的 CPU 核心，而非能效核心。安全，无需 root。"
    const val CPU_GOV = "强制 CPU 始终高速运行，而非按需升降频。性能更强，发热和耗电更高。（需 Root）"
    const val CPU_FREQ = "将 CPU 锁定在最高频率，永不降频。极致性能，发热和耗电最高。（需 Root）"
    const val CORES_ONLINE = "阻止核心休眠，使所有核心始终可用。（需 Root）"
    const val GPU_CLOCK =
        "Pins the GPU at top speed so it stops dropping clocks to save power. Smoother and more " +
        "consistent, but hotter and heavier on battery.\n\n" +
        "No root needed on Adreno (Qualcomm) GPUs — this is the same \"Adreno turbo\" trick Switch " +
        "emulators use: it asks the GPU driver directly to stop scaling clocks down, through a device " +
        "file the app is already allowed to open.\n\n" +
        "With root granted, it switches to a stronger system-level clock pin instead. Either way your " +
        "device's own heat protection still applies, and the setting is undone when you exit the game, " +
        "background the app, or it crashes.\n\n" +
        "On non-Adreno GPUs (Mali, Xclipse, PowerVR) this toggle needs root."
    const val THERMAL = "移除设备的内置过热保护。⚠️ 可能过热 — 开启后，温度看门狗是您唯一的安全保障。（需 Root）"
    const val FAN = "将风扇运行在最高转速以获得最大散热（适用于带风扇的设备）。（需 Root）"
    const val FREE_MEM_SECTION =
        "Two ways to free memory, from lightest to strongest.\n\n" +
        "Drop file caches (Tier 1)\n" +
        "Writes to the kernel's drop-caches control to release cached files. This is light and " +
        "near-invisible by design — the system refills the cache almost immediately, so you'll rarely " +
        "see the free-RAM number move. It does NOT close any apps.\n\n" +
        "Deep clean (Tier 2)\n" +
        "The real RAM free. Force-closes background apps to reclaim memory. It uses Android's own " +
        "\"kill background\" command (am kill-all), which only touches apps the system considers safe " +
        "to kill — it will NOT close your running game, this app, or system/persistent apps. This is " +
        "the same kind of thing a phone's built-in \"RAM cleaner\" / Game Turbo does.\n\n" +
        "Auto deep-clean on launch\n" +
        "When on, a deep clean runs automatically each time you start a game or container, so it opens " +
        "with the most free RAM available.\n\n" +
        "Root is required for deep clean (and for dropping caches)."
    const val AUTO_DEEP_CLEAN =
        "Runs a deep clean automatically every time a game/container launches — force-closes safe " +
        "background apps (via am kill-all, never your game or system) to free real RAM before you start. (Root)"
}

/** Consolidated explainer body, grouped by root requirement. */
private fun explainAllBody(): String = buildString {
    append("No root needed\n\n")
    append("• Sustained Performance Mode\n${PerfCopy.SUSTAINED}\n\n")
    append("• Thread Priority Boost\n${PerfCopy.PRIORITY}\n\n")
    append("• Prefer Big Cores\n${PerfCopy.BIG_CORES}\n\n")
    append("• Lock GPU to max clock\n${PerfCopy.GPU_CLOCK}\n\n")
    append("\nRequires root\n\n")
    append("• CPU governor → performance\n${PerfCopy.CPU_GOV}\n\n")
    append("• Lock CPU frequency to max\n${PerfCopy.CPU_FREQ}\n\n")
    append("• Keep all cores online\n${PerfCopy.CORES_ONLINE}\n\n")
    append("• Disable thermal throttling\n${PerfCopy.THERMAL}\n\n")
    append("• Fan to maximum\n${PerfCopy.FAN}\n\n")
    append("• Free memory\n${PerfCopy.FREE_MEM_SECTION}\n")
}
