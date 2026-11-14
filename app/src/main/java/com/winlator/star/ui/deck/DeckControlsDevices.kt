package com.winlator.star.ui.deck

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.star.R
import com.winlator.star.core.GyroCalibrator
import com.winlator.star.inputcontrols.Binding
import com.winlator.star.inputcontrols.ExternalController
import com.winlator.star.inputcontrols.SteamControllerBackend
import com.winlator.star.ui.components.GlobalControllerPrefs
import com.winlator.star.ui.components.PlayerSlotEditorRow
import com.winlator.star.ui.components.buildPlayerSlotEditorRows
import com.winlator.star.ui.screens.HelpDialog
import com.winlator.star.ui.screens.InputControlsDeckState
import com.winlator.star.ui.screens.MenuButtonCaptureDialog
import com.winlator.star.ui.screens.MenuItemDivider
import com.winlator.star.ui.screens.STEAM_PADDLE_NAMES
import com.winlator.star.ui.screens.ScrollingDropdownMenu
import com.winlator.star.ui.screens.pulsePad
import com.winlator.star.ui.screens.steamPaddleOptions
import com.winlator.star.ui.screens.xmbBtn
import com.winlator.star.ui.screens.xmbMenuBack
import com.winlator.star.winhandler.WinHandler
import kotlin.math.abs

// The Assign and Device tabs of the Deck / XMB Controls page (DeckControlsPage.kt). They keep every control and every preference
// of the Classic Assign / Device tabs (the same GlobalControllerPrefs, GyroCalibrator and SteamControllerBackend calls), drawn as
// compact rows instead of boxed sections with paragraphs.

/** Slot values of the saved overrides (see PlayerSlotsEditor): Auto is "no entry", 0..3 a player, Ignore keeps a device out. */
private const val SLOT_AUTO = -1
private val SLOT_IGNORE = WinHandler.SLOT_IGNORE

/** A card title with optional trailing items (a value, a help button). */
@Composable
private fun CtlCardTitle(title: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing(this)
    }
}

/** A help button that opens the same help text Classic's "?" opens. */
@Composable
private fun CtlHelpButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp).xmbBtn(CircleShape)) {
        Icon(
            Icons.Filled.Help,
            contentDescription = "音频设置",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** An on/off row: A or a tap flips it. The switch only shows the state. */
@Composable
private fun CtlToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    CtlRow(onClick = { onChange(!checked) }) { cs ->
        Column(Modifier.weight(1f)) {
            Text(label, color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (sub != null) Text(sub, color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Assign
// ---------------------------------------------------------------------------------------------------------------------

/**
 * The default player slots of new containers: the four slots with whoever is pinned to each (the on-screen pad and every controller
 * that is plugged in or was pinned), and the two defaults that go with them. A tap or A on a slot opens its device list: picking a
 * device pins it to that slot, picking it again frees it. The values are the same GlobalControllerPrefs ones, in the same JSON.
 */
@Composable
internal fun DeckAssignTab(s: InputControlsDeckState) {
    val context = LocalContext.current
    var json by remember { mutableStateOf(GlobalControllerPrefs.getSlotOverridesJson(context)) }
    var onScreenMode by remember { mutableStateOf(GlobalControllerPrefs.getOnScreenMode(context)) }
    var autoHide by remember { mutableStateOf(GlobalControllerPrefs.getAutoHideControlsOnPad(context)) }
    var helpRes by remember { mutableStateOf<Int?>(null) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }
    // Re-read when the saved pins change or a controller comes or goes (the screen's hot-plug listener replaces s.controllers).
    val rows = remember(json, s.controllers) { buildPlayerSlotEditorRows(json) }

    fun pin(descriptor: String, slot: Int) {
        val map = WinHandler.parseSlotOverridesJson(json).toMutableMap()
        if (slot == SLOT_AUTO) map.remove(descriptor)
        else map[descriptor] = slot
        val next = WinHandler.buildSlotOverridesJson(map)
        json = next
        GlobalControllerPrefs.setSlotOverridesJson(context, next)
    }

    CtlPanes(
        left = {
            DeckCard {
                Spacer(Modifier.height(4.dp))
                for (slot in 0 until 4) {
                    if (slot > 0) DeckRowDivider()
                    SlotRow("Player ${slot + 1}", slot, rows) { d, sl -> pin(d, sl) }
                }
                DeckRowDivider()
                SlotRow("Ignored", SLOT_IGNORE, rows) { d, sl -> pin(d, sl) }
                DeckRowDivider()
                val auto = rows.filter { it.override == SLOT_AUTO }
                CtlInfoRow { cs ->
                    Text("Auto", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(76.dp))
                    Text(
                        text = if (auto.isEmpty()) "无人在线" else auto.joinToString(", ") { slotName(it) },
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    CtlChip("启动顺序", cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        right = {
            DeckCard {
                Column(modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp)) {
                    CtlCardTitle("新容器启动时使用") { CtlHelpButton { helpRes = R.string.help_player_slots } }
                }
                val modeLabels = listOf("保持屏幕玩家", "将玩家1让给手柄", "共享该玩家")
                CtlChoiceRow(
                    label = "屏幕优先级",
                    value = modeLabels.getOrElse(onScreenMode) { modeLabels[0] },
                    options = modeLabels,
                    onPick = { i ->
                        onScreenMode = i
                        GlobalControllerPrefs.setOnScreenMode(context, i)
                    },
                )
                DeckRowDivider()
                CtlToggleRow(
                    label = "当控制器连接时隐藏屏幕控制",
                    checked = autoHide,
                    onChange = { on ->
                        autoHide = on
                        GlobalControllerPrefs.setAutoHideControlsOnPad(context, on)
                    },
                )
                DeckRowDivider()
                CtlNote("仅对新容器生效；现有容器保留各自设置。同一玩家的两台设备共享操控。游戏内菜单仍可实时更改。")
            }
        },
        leftWeight = 1.2f,
        rightWeight = 1f,
    )
}

private fun slotName(r: PlayerSlotEditorRow): String = if (r.connected) r.displayName else r.displayName + " (not connected)"

/** One slot: its name, who is pinned to it and how many (Pinned / Shared); opens the device list. */
@Composable
private fun SlotRow(title: String, slot: Int, rows: List<PlayerSlotEditorRow>, onPin: (String, Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val here = rows.filter { it.override == slot }
    Box {
        CtlRow(onClick = { open = true }) { cs ->
            Text(title, color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(76.dp))
            Text(
                text = if (here.isEmpty()) (if (slot >= 0) "Open" else "没有忽略任何内容") else here.joinToString(", ") { slotName(it) },
                color = if (here.isEmpty()) cs.onSurfaceVariant else cs.onSurface,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            when {
                here.isEmpty() -> Unit
                slot < 0 -> CtlChip("Ignored", cs.onSurfaceVariant)
                here.size > 1 -> CtlChip("共享", cs.primary)
                else -> CtlChip("已固定", cs.primary)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = cs.onSurfaceVariant)
        }
        ScrollingDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.xmbMenuBack { open = false },
        ) {
            rows.forEachIndexed { i, r ->
                if (i > 0) MenuItemDivider()
                val pinned = r.override == slot
                DropdownMenuItem(
                    text = { Text((if (pinned) "✓ " else "") + slotName(r)) },
                    onClick = {
                        open = false
                        onPin(r.descriptor, if (pinned) SLOT_AUTO else slot)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Device
// ---------------------------------------------------------------------------------------------------------------------

/** The connected controllers on the left; gyroscope, menu button and Steam Controller (device-level settings) on the right. */
@Composable
internal fun DeckDeviceTab(s: InputControlsDeckState) {
    CtlPanes(
        left = { ConnectedDevicesCard(s) },
        right = {
            GyroCard()
            MenuButtonCard()
            SteamControllerCard()
        },
        leftWeight = 1f,
        rightWeight = 1f,
    )
}

/** Opens Android's Bluetooth settings (where a controller is paired); nothing happens when the device has none. */
private fun openBluetoothSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
    }
}

/** Every game controller Android reports right now: its name, kind, where it comes from, ids and battery, with a rumble test. */
@Composable
private fun ConnectedDevicesCard(s: InputControlsDeckState) {
    val context = LocalContext.current
    // Read fresh on every composition: the screen's hot-plug listener replaces s, which recomposes this.
    val pads = ExternalController.getControllers()
    DeckCard {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
            CtlCardTitle("已连接的控制器") { CtlChip("${pads.size}", MaterialTheme.colorScheme.primary) }
        }
        if (pads.isEmpty()) {
            CtlNote("未连接手柄。请插入 USB 或通过蓝牙配对。")
        } else {
            Spacer(Modifier.height(4.dp))
            for (c in pads) {
                val pad = ctlPadOf(c)
                val dev = pad.device
                CtlInfoRow { cs ->
                    CtlDot(if (pad.connected) CTL_GREEN else CTL_RED)
                    Column(Modifier.weight(1f)) {
                        Text(c.getName() ?: c.getId(), color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(deviceLine(pad), color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (dev != null && pad.vibrator) {
                        DeckButton("震动", Icons.Filled.Vibration, { pulsePad(dev) })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 2.dp)) {
            DeckButton("蓝牙设置", null, { openBluetoothSettings(context) })
        }
    }
}

/** "Xbox · External · 045E:0B13 · battery 80%". */
private fun deviceLine(pad: CtlPad): String {
    val dev = pad.device ?: return "Not connected"
    val parts = ArrayList<String>()
    parts.add(
        when (ExternalController.classifyType(dev)) {
            ExternalController.PadType.XBOX -> "Xbox"
            ExternalController.PadType.PS -> "PlayStation"
            else -> "Generic"
        }
    )
    if (Build.VERSION.SDK_INT >= 29) parts.add(if (dev.isExternal) "外部" else "Built-in")
    parts.add("%04X:%04X".format(dev.vendorId, dev.productId))
    if (pad.battery >= 0) parts.add("battery ${pad.battery}%")
    return parts.joinToString(" · ")
}

/**
 * Gyroscope calibration (the zero-rate bias is a property of the hardware, not of a game; sensitivity and the rest stay with the
 * container). The run is cancelled when the page goes, so a listener is never left on the sensor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GyroCard() {
    val context = LocalContext.current
    val sensor = remember { GyroCalibrator.getSensor(context) }
    var calibrated by remember { mutableStateOf(GyroCalibrator.isCalibrated(context)) }
    var biasX by remember { mutableStateOf(0f) }
    var biasY by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var activeRun by remember { mutableStateOf<GyroCalibrator.Run?>(null) }

    fun refreshBias() {
        val bias = FloatArray(2)
        GyroCalibrator.loadBias(context, bias)
        biasX = bias[0]
        biasY = bias[1]
        calibrated = GyroCalibrator.isCalibrated(context)
    }

    DisposableEffect(Unit) {
        refreshBias()
        onDispose {
            activeRun?.cancel()
            activeRun = null
        }
    }

    DeckCard {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val summary = when {
                sensor == null -> "No gyroscope on this device"
                !calibrated -> "未校准"
                abs(biasX) < GyroCalibrator.NEGLIGIBLE_BIAS && abs(biasY) < GyroCalibrator.NEGLIGIBLE_BIAS ->
                    "漂移极小：设备已在自行补偿"
                else -> "已移除偏差：x %.4f，y %.4f 弧度/秒".format(biasX, biasY)
            }
            CtlCardTitle("陀螺仪")
            Text(summary, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            if (sensor != null) {
                Text(
                    if (status.isNotEmpty()) status else "将设备平放在水平面上，然后校准。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeRun == null) {
                        DeckButton("校准", null, {
                            status = "请保持静止…"
                            activeRun = GyroCalibrator.calibrate(context) { result ->
                                activeRun = null
                                status = when (result) {
                                    is GyroCalibrator.Result.Success ->
                                        if (result.negligible) "已校准：没有需要移除的" else "校准完成"
                                    GyroCalibrator.Result.Moved -> "请保持设备静止并重试"
                                    GyroCalibrator.Result.NotEnoughSamples -> "采样失败，请重试"
                                    GyroCalibrator.Result.Unavailable -> "陀螺仪不可用"
                                }
                                refreshBias()
                            }
                        }, primary = true)
                        if (calibrated) {
                            DeckButton("重置", null, {
                                GyroCalibrator.clearBias(context)
                                status = "校准已重置"
                                refreshBias()
                            })
                        }
                    }
                }
            }
        }
    }
}

/**
 * The device-level menu button: one button on a handheld's own pad that opens the in-game side menu, for devices whose built-in
 * controls have no Back key. LIVE (each game session reads it). The picker takes the next key press through MainActivity.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuButtonCard() {
    val context = LocalContext.current
    val prefs = GlobalControllerPrefs
    var keyCode by remember { mutableStateOf(prefs.getMenuButtonKeyCode(context)) }
    var capturing by remember { mutableStateOf(false) }
    var helpRes by remember { mutableStateOf<Int?>(null) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }

    DeckCard {
        Column(modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CtlCardTitle("菜单按钮") {
                CtlChip(prefs.menuButtonName(keyCode), MaterialTheme.colorScheme.primary)
                CtlHelpButton { helpRes = R.string.help_menu_button }
            }
            Text(
                "在没有返回键的手持设备上打开游戏内菜单。该按键不会再发送给游戏。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(end = 6.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckButton("设置按钮", null, { capturing = true }, primary = true)
                if (keyCode != 0) {
                    DeckButton("清除", null, {
                        prefs.setMenuButtonKeyCode(context, 0)
                        keyCode = prefs.getMenuButtonKeyCode(context)
                    })
                }
            }
        }
    }

    if (capturing) {
        MenuButtonCaptureDialog { picked ->
            capturing = false
            if (picked > 0) {
                prefs.setMenuButtonKeyCode(context, picked)
                keyCode = prefs.getMenuButtonKeyCode(context)
            }
        }
    }
}

/**
 * Steam Controller support (SDL3's HIDAPI Steam drivers, see SteamControllerBackend): off by default, LIVE at every game launch.
 * Turning it on asks for the Nearby-devices (Bluetooth) permission on Android 12+; without it only a USB controller works.
 */
@Composable
private fun SteamControllerCard() {
    val context = LocalContext.current
    val prefs = GlobalControllerPrefs
    var enabled by remember { mutableStateOf(prefs.isSteamControllerEnabled(context)) }
    var trackpadMode by remember { mutableStateOf(prefs.getSteamTrackpadMouseMode(context)) }
    var bluetoothGranted by remember { mutableStateOf(SteamControllerBackend.hasBluetoothPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        bluetoothGranted = SteamControllerBackend.hasBluetoothPermission(context)
    }
    fun requestBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 && !bluetoothGranted)
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }
    // Order = the labels; values are SteamControllerBackend.TRACKPAD_MOUSE_*.
    val trackpadLabels = listOf("右触控板", "左触控板", "双触控板", "Off")
    val trackpadModes = listOf(
        SteamControllerBackend.TRACKPAD_MOUSE_RIGHT, SteamControllerBackend.TRACKPAD_MOUSE_LEFT,
        SteamControllerBackend.TRACKPAD_MOUSE_BOTH, SteamControllerBackend.TRACKPAD_MOUSE_OFF,
    )
    val paddleOptions = remember { steamPaddleOptions() }
    val paddleLabels = remember { paddleOptions.map { it.first } }
    var paddles by remember { mutableStateOf(prefs.getSteamPaddleBindings(context).toList()) }

    DeckCard {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)) {
            CtlCardTitle("Steam Controller")
        }
        CtlToggleRow(
            label = "Steam控制器支持",
            checked = enabled,
            sub = "像 Steam 一样直接读取。在蓝牙设置中配对或直接插入。从下次游戏启动时生效。",
            onChange = { on ->
                enabled = on
                prefs.setSteamControllerEnabled(context, on)
                if (on) requestBluetooth()
            },
        )
        if (enabled) {
            DeckRowDivider()
            CtlChoiceRow(
                label = "触控板鼠标",
                value = trackpadLabels[trackpadModes.indexOf(trackpadMode).coerceAtLeast(0)],
                options = trackpadLabels,
                onPick = { i ->
                    trackpadMode = trackpadModes[i.coerceIn(0, trackpadModes.size - 1)]
                    prefs.setSteamTrackpadMouseMode(context, trackpadMode)
                },
                sub = "点击为左键；两个都接时，左边那个是右键",
            )
            for (i in STEAM_PADDLE_NAMES.indices) {
                DeckRowDivider()
                CtlChoiceRow(
                    label = STEAM_PADDLE_NAMES[i],
                    value = paddleOptions.firstOrNull { it.second == paddles[i] }?.first ?: paddleLabels[0],
                    options = paddleLabels,
                    onPick = { idx ->
                        val binding = paddleOptions.getOrNull(idx)?.second ?: Binding.NONE
                        paddles = paddles.toMutableList().also { it[i] = binding }
                        prefs.setSteamPaddleBinding(context, i, binding)
                    },
                )
            }
            if (!bluetoothGranted) {
                DeckRowDivider()
                CtlRow(onClick = { requestBluetooth() }) { cs ->
                    Text(
                        "蓝牙权限已关闭：仅支持 USB 手柄",
                        color = cs.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (Build.VERSION.SDK_INT >= 31) Text("Allow", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            CtlNote("返回类按键和 … 按键在所有游戏中生效。其余按键使用「默认 / 任意手柄」的按键映射。")
        }
        Spacer(Modifier.height(4.dp))
    }
}
