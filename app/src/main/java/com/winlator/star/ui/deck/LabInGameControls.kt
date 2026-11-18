package com.winlator.star.ui.deck

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.star.R
import com.winlator.star.core.ControllerIdentity
import com.winlator.star.core.RumbleMotors
import com.winlator.star.core.StickPacing
import com.winlator.star.ui.XServerDialogState
import com.winlator.star.ui.XServerDrawerState
import com.winlator.star.ui.components.toHexString
import com.winlator.star.ui.simple.L
import com.winlator.star.ui.simple.MenuAction
import com.winlator.star.ui.simple.SimpleActionSheet
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// The Lab in-game menu's Controls page: the Classic drawer's Controls tab (XServerDrawer.kt ControlsContent and the
// sections it calls: Swipe, Players, Pad, Save for, touch gestures, Gyro, the rumble rows and the controls accent
// picker) drawn with the in-game Lab rows. Every control writes the same flows and fires the same callbacks, in the same
// order, as its Classic one.

/** Lab-drawn Classic ControlsContent: the same sub-tabs in every session kind (Pad only where the pad session runs). */
@Composable
internal fun LabControlsPanel(state: XServerDrawerState, kind: InGameSessionKind) {
    val profiles by XServerDialogState.inputProfiles.collectAsState()
    val initProfileIdx by XServerDialogState.selectedProfileIdx.collectAsState()
    val initTouchscreen by XServerDialogState.showTouchscreen.collectAsState()
    val initTimeout by XServerDialogState.timeoutEnabled.collectAsState()
    val initHaptics by XServerDialogState.hapticsEnabled.collectAsState()
    val initOverlayOpacity by state.overlayOpacity.collectAsState()
    val controlsFollowTheme by state.controlsFollowTheme.collectAsState()
    val initControlsAccent by state.controlsAccentColor.collectAsState()
    val pad by XServerDialogState.pad.collectAsState()

    // The Classic sub-tab list. "Pad" (live controller settings) exists only where LiveControllerSession runs.
    val subTab by state.controlsSubTab.collectAsState()
    val subTabs = listOf(
        0 to L("触摸"), 1 to L("Mouse"), 2 to L("震动"), 3 to L("陀螺仪"), 4 to L("玩家"),
        5 to stringResource(R.string.swipe_tab), 6 to L("Pad"),
    ).filter { (i, _) -> pad.supported || i != 6 }
    // A sub-tab this session doesn't offer falls back to the first one it does.
    LaunchedEffect(subTab, subTabs.size) {
        if (subTabs.none { it.first == subTab }) state.setControlsSubTab(subTabs.first().first)
    }
    val shown = if (subTabs.any { it.first == subTab }) subTab else subTabs.first().first

    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        IgSegmented(subTabs, shown) { state.setControlsSubTab(it) }
    }
    if (kind == InGameSessionKind.LINUX_STEAM && (shown == 0 || shown == 1)) {
        IgNote(L("The Steam client also has its own Touch choice on its Steam page."), modifier = Modifier.padding(top = 6.dp))
    }

    // Hoisted above the sub-tab switch, as in Classic, so in-flight profile/flag edits survive a hop to another sub-tab.
    var selectedIdx by remember(initProfileIdx) { mutableIntStateOf(initProfileIdx) }
    var showTouchscreen by remember(initTouchscreen) { mutableStateOf(initTouchscreen) }
    var timeoutEnabled by remember(initTimeout) { mutableStateOf(initTimeout) }
    var hapticsEnabled by remember(initHaptics) { mutableStateOf(initHaptics) }
    val allItems = listOf(L("-- 已禁用 --")) + profiles
    val confirm: () -> Unit = {
        XServerDialogState.onInputControlsConfirm?.invoke(selectedIdx, showTouchscreen, timeoutEnabled, hapticsEnabled)
    }

    when (shown) {
        // ── Touch ──
        0 -> {
            IgSectionLabel(L("Input controls"))
            IgCard {
                // A layout applies for this session; choosing none is remembered (smart_default_touch_optout).
                IgRow(L("配置"), scopes = listOf(if (selectedIdx == 0) SaveScope.ALL else SaveScope.SESSION), wideControl = true) {
                    IgPickButton(L("配置"), allItems, if (selectedIdx in allItems.indices) selectedIdx else 0) { i ->
                        selectedIdx = i
                        confirm()
                    }
                }
                IgDivider()
                IgToggleRow(L("触摸控件"), showTouchscreen, scopes = listOf(SaveScope.SESSION)) {
                    showTouchscreen = it
                    confirm()
                    // The confirm callback doesn't echo the flag; mirror it so the Quick page's switch agrees (as that one does).
                    XServerDialogState.setShowTouchscreen(it)
                }
                IgDivider()
                IgToggleRow(L("Timeout"), timeoutEnabled, scopes = listOf(SaveScope.ALL)) {
                    timeoutEnabled = it
                    confirm()
                }
                IgDivider()
                IgToggleRow(L("触觉反馈"), hapticsEnabled, scopes = listOf(SaveScope.ALL)) {
                    hapticsEnabled = it
                    confirm()
                }
                IgDivider()
                // Live: applied to the visible overlay as it moves.
                var overlayOpacity by remember(initOverlayOpacity) { mutableFloatStateOf(initOverlayOpacity) }
                IgSliderRow(
                    title = L("覆盖层透明度"),
                    value = overlayOpacity,
                    range = 0f..1f,
                    step = 0.05f,
                    format = { "${(it * 100).toInt()}%" },
                    onChange = {
                        overlayOpacity = it
                        state.setOverlayOpacity(it)
                        state.onOverlayOpacityChange?.run()
                    },
                    onFinished = {},
                    scopes = listOf(SaveScope.ALL),
                )
            }

            // Per-profile accent: follow the app theme (default) or a custom accent for the active profile.
            val accentScope = SaveScope.other(L("保存在控制配置中"))
            IgSectionLabel(L("控制强调色"))
            IgCard {
                IgToggleRow(L("应用主题"), controlsFollowTheme, scopes = listOf(accentScope)) {
                    state.setControlsFollowTheme(it)
                    state.onControlsColorChange?.run()
                }
                if (!controlsFollowTheme) {
                    IgDivider()
                    LabCtlColorPicker(initialColor = Color(initControlsAccent)) {
                        state.setControlsAccentColor(it.toArgb())
                        state.onControlsColorChange?.run()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                IgButton(
                    L("配置文件设置…"), null,
                    {
                        confirm()
                        XServerDialogState.onInputControlsSettings?.invoke(selectedIdx)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedIdx > 0,
                )
                IgButton(
                    L("应用并关闭"), null,
                    {
                        // state.onClose is the activity's closeInGameMenu (closes the drawer and this menu); closing
                        // this menu first is a no-op for it.
                        DeckInGameState.close()
                        confirm()
                        state.onClose?.run()
                    },
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
            }
        }

        // ── Mouse ──
        1 -> LabCtlMouseSection(state)

        // ── Vibration ──
        2 -> LabCtlVibrationSection()

        // ── Gyro ── (unrelated to rumble: shows whether or not vibration is on)
        3 -> LabCtlGyroSection()

        // ── Players ──
        4 -> LabCtlPlayersSection()

        // ── Swipe ──
        5 -> LabCtlSwipeSection(state)

        // ── Pad ──
        6 -> LabCtlPadSection()
    }
}

// ───────────────────────────── Mouse ─────────────────────────────

@Composable
private fun LabCtlMouseSection(state: XServerDrawerState) {
    val game = igGameScope()
    val touchMode by state.touchMode.collectAsState()
    val aimMode by state.aimMode.collectAsState()
    val cursorAutoHide by state.cursorAutoHide.collectAsState()
    val isWaylandSession by state.isWaylandMode.collectAsState()
    val isMouseDisabled by state.isMouseDisabled.collectAsState()

    IgSectionLabel(L("Mouse & cursor"))
    IgCard {
        // Touch and Aim mode are saved for this game (TouchAimSettings); they apply at once.
        IgRow(L("触摸"), scopes = listOf(game), wideControl = true) {
            IgSegmented(listOf("touchpad" to L("Touchpad"), "direct" to L("Direct"), "touchscreen" to L("Touchscreen")), touchMode) {
                state.onTouchMode?.accept(it)
            }
        }
        IgDivider()
        IgRow(L("瞄准"), scopes = listOf(game), wideControl = true) {
            IgSegmented(listOf("auto" to L("Auto"), "on" to L("On"), "off" to L("Off")), aimMode) {
                state.onAimMode?.accept(it)
            }
        }
        IgDivider()
        // The activity flips its own flag (onDisableMouse toggles), as the Classic chip does.
        IgToggleRow(L("禁用鼠标"), isMouseDisabled, scopes = listOf(SaveScope.SESSION)) {
            state.onDisableMouse?.run()
        }
        IgDivider()
        IgToggleRow(L("自动隐藏光标"), cursorAutoHide, scopes = listOf(SaveScope.ALL)) {
            state.setCursorAutoHide(it); state.onCursorAutoHideChange?.run()
        }
        if (cursorAutoHide) {
            val initHideMs by state.cursorAutoHideMs.collectAsState()
            var hideMs by remember(initHideMs) { mutableIntStateOf(initHideMs) }
            IgDivider()
            // Classic: 500..10000 in 19 steps of 500.
            IgSliderRow(
                title = L("隐藏延迟 (ms)"),
                value = hideMs.toFloat(),
                range = 500f..10000f,
                step = 500f,
                format = { "${it.roundToInt()}" },
                onChange = { hideMs = ((it / 500f).roundToInt() * 500).coerceIn(500, 10000) },
                onFinished = { state.setCursorAutoHideMs(hideMs); state.onCursorAutoHideChange?.run() },
                scopes = listOf(SaveScope.ALL),
            )
        }
        if (isWaylandSession) {
            IgNote(
                L("Wayland：鼠标增量发送到合成器；锁定指针的游戏会将其作为相对运动接收"),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }

    // The gestures only exist with the pointer under the finger, so they show with Direct.
    if (touchMode == "direct") LabCtlTouchGestures(state)
}

@Composable
private fun LabCtlTouchGestures(state: XServerDrawerState) {
    val dragSelect by state.gestureDragSelect.collectAsState()
    val longPress by state.gestureLongPressRightClick.collectAsState()

    IgSectionLabel(L("触摸手势"))
    IgCard {
        IgInfo(L("拖拽以框选，长按为右键。双指拖拽可滚动滚轮。"))
        IgDivider()
        IgToggleRow(L("框选"), dragSelect, scopes = listOf(SaveScope.ALL)) {
            state.setGestureDragSelect(it); state.onGestureConfigChange?.run()
        }
        IgDivider()
        IgToggleRow(L("长按 = 右键"), longPress, scopes = listOf(SaveScope.ALL)) {
            state.setGestureLongPressRightClick(it); state.onGestureConfigChange?.run()
        }
        if (longPress) {
            val initHoldMs by state.gestureLongPressMs.collectAsState()
            var holdMs by remember(initHoldMs) { mutableIntStateOf(initHoldMs) }
            IgDivider()
            IgSliderRow(
                title = L("长按延迟"),
                value = holdMs.toFloat(),
                range = 150f..800f,
                step = 32f,
                format = { "${it.roundToInt()}" },
                onChange = { holdMs = it.roundToInt() },
                onFinished = { state.setGestureLongPressMs(holdMs); state.onGestureConfigChange?.run() },
                scopes = listOf(SaveScope.ALL),
            )
        }
    }
}

// ───────────────────────────── Vibration ─────────────────────────────

@Composable
private fun LabCtlVibrationSection() {
    val padScope = labCtlPadScope()
    // Master switch: off suppresses all controller rumble and hides the rows below. Saved app-wide.
    val vibrationMasterOn by XServerDialogState.vibrationMasterEnabled.collectAsState()
    val vibrationSlots by XServerDialogState.vibrationSlots.collectAsState()

    IgSectionLabel(L("震动"))
    IgCard {
        IgToggleRow(L("Enabled"), vibrationMasterOn, scopes = listOf(SaveScope.ALL)) {
            XServerDialogState.setVibrationMasterEnabled(it)
            XServerDialogState.onVibrationMasterChanged?.invoke(it)
        }
        if (vibrationMasterOn) {
            val initVibrationMode by XServerDialogState.vibrationMode.collectAsState()
            var vibrationMode by remember(initVibrationMode) { mutableIntStateOf(initVibrationMode) }
            IgDivider()
            IgRow(L("震动目标"), scopes = listOf(padScope), wideControl = true) {
                IgSegmented(listOf(0 to L("Off"), 1 to L("Controller"), 2 to L("Device"), 3 to L("Both")), vibrationMode) {
                    vibrationMode = it
                    XServerDialogState.setVibrationMode(it)
                    XServerDialogState.onVibrationModeChanged?.invoke(it)
                }
            }

            if (vibrationMode != 0) {
                val initVibrationIntensity by XServerDialogState.vibrationIntensity.collectAsState()
                var vibrationIntensity by remember(initVibrationIntensity) { mutableIntStateOf(initVibrationIntensity) }
                IgDivider()
                IgSliderRow(
                    title = L("强度"),
                    value = vibrationIntensity.toFloat(),
                    range = 0f..100f,
                    step = 5f,
                    format = { "${it.roundToInt()}" },
                    onChange = { vibrationIntensity = it.roundToInt() },
                    onFinished = {
                        XServerDialogState.setVibrationIntensity(vibrationIntensity)
                        XServerDialogState.onVibrationIntensityChanged?.invoke(vibrationIntensity)
                    },
                    scopes = listOf(padScope),
                )
            }

            // Only a controller has two motors: the row shows for the Controller and Both targets.
            if (vibrationMode == 1 || vibrationMode == 3) {
                val initRumbleMotors by XServerDialogState.rumbleMotors.collectAsState()
                var rumbleMotors by remember(initRumbleMotors) { mutableIntStateOf(initRumbleMotors) }
                val motorsTitle = stringResource(R.string.rumble_motors_label)
                val motorsHelp = labCtlHelpText(R.string.help_rumble_motors)
                IgDivider()
                IgRow(motorsTitle, scopes = listOf(padScope), wideControl = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IgSegmented(
                            listOf(
                                RumbleMotors.BOTH to stringResource(R.string.rumble_motors_both_short),
                                RumbleMotors.SINGLE to stringResource(R.string.rumble_motors_single_short),
                            ),
                            rumbleMotors,
                        ) { motors ->
                            rumbleMotors = motors
                            XServerDialogState.setRumbleMotors(motors)
                            XServerDialogState.onRumbleMotorsChanged?.invoke(motors)
                        }
                        IgHelpButton(motorsTitle, motorsHelp)
                    }
                }
            }
            // Target, intensity and motors save like Controls › Pad.
            IgDivider()
            LabCtlSaveForRows()
        }
    }

    if (vibrationMasterOn && vibrationSlots.isNotEmpty()) {
        IgSectionLabel(L("Per slot"))
        IgCard {
            vibrationSlots.forEachIndexed { index, slot ->
                if (index > 0) IgDivider()
                IgToggleRow(slot.first, slot.second, scopes = listOf(SaveScope.ALL)) {
                    XServerDialogState.updateVibrationSlot(index, it)          // reflect in the UI immediately
                    XServerDialogState.onVibrationSlotChanged?.invoke(index, it) // persist to WinHandler
                }
            }
        }
    }
}

// ───────────────────────────── Gyro ─────────────────────────────

@Composable
private fun LabCtlGyroSection() {
    val gyroSupported by XServerDialogState.gyroSupported.collectAsState()
    val title = stringResource(R.string.gyro_drawer_title)
    IgSectionLabel(title)
    if (!gyroSupported) {
        // Classic hides the section on a device with no gyroscope.
        IgCard { IgInfo(L("This device has no gyroscope.")) }
    } else {
        val game = igGameScope()
        val gyroEnabled by XServerDialogState.gyroEnabled.collectAsState()
        IgCard {
            IgToggleRow(stringResource(R.string.gyro_drawer_enabled), gyroEnabled, scopes = listOf(game)) {
                XServerDialogState.setGyroEnabled(it)
                XServerDialogState.onGyroEnabledChanged?.invoke(it)
            }
        }
        if (gyroEnabled) LabCtlGyroSettings(game)
    }
}

@Composable
private fun LabCtlGyroSettings(game: SaveScope) {
    // Where the tilt goes. Mouse drives the pointer instead of a stick.
    val initGyroTarget by XServerDialogState.gyroTarget.collectAsState()
    var gyroTarget by remember(initGyroTarget) { mutableIntStateOf(initGyroTarget) }

    // How the tilt is read. Tilt to aim and the Mouse target exclude each other.
    val orientationSupported by XServerDialogState.gyroOrientationSupported.collectAsState()
    val initGyroMode by XServerDialogState.gyroMode.collectAsState()
    var gyroMode by remember(initGyroMode) { mutableIntStateOf(initGyroMode) }
    val orientationBlockedByMouse = gyroTarget == 2
    val orientationSelectable = orientationSupported && !orientationBlockedByMouse
    val modeHint = when {
        !orientationSupported -> stringResource(R.string.gyro_drawer_orientation_unsupported)
        orientationBlockedByMouse -> stringResource(R.string.gyro_drawer_orientation_mouse_hint)
        gyroMode == 1 -> stringResource(R.string.gyro_drawer_orientation_hint)
        else -> null
    }
    val targetHint = when {
        gyroMode == 1 -> stringResource(R.string.gyro_drawer_mouse_unavailable_hint)
        gyroTarget == 2 -> stringResource(R.string.gyro_drawer_mouse_hint)
        else -> null
    }

    Spacer(Modifier.height(10.dp))
    IgCard {
        IgRow(stringResource(R.string.gyro_drawer_mode), hint = modeHint, scopes = listOf(game), wideControl = true) {
            IgSegmented(
                listOf(0 to stringResource(R.string.gyro_drawer_mode_rate), 1 to stringResource(R.string.gyro_drawer_mode_orientation)),
                gyroMode,
                isEnabled = { it != 1 || orientationSelectable },
            ) { labCtlSetGyroModeLive(it) { v -> gyroMode = v } }
        }
        // With the "Always" activator there is never a rising edge, so this is the only way to fix a drifted centre.
        if (orientationSupported && !orientationBlockedByMouse && gyroMode == 1) {
            IgDivider()
            val recenter = stringResource(R.string.gyro_drawer_recenter)
            IgRow(recenter, hint = stringResource(R.string.gyro_drawer_recenter_hint)) {
                IgButton(recenter, null, { XServerDialogState.onGyroRecenterRequested?.invoke() }, primary = true)
            }
        }
        IgDivider()
        IgRow(stringResource(R.string.gyro_drawer_apply_to), hint = targetHint, scopes = listOf(game), wideControl = true) {
            IgSegmented(
                listOf(
                    0 to stringResource(R.string.gyro_drawer_target_right_stick),
                    1 to stringResource(R.string.gyro_drawer_target_left_stick),
                    2 to stringResource(R.string.gyro_drawer_target_mouse),
                ),
                gyroTarget,
                isEnabled = { !(gyroMode == 1 && it == 2) },
            ) { labCtlSetGyroTargetLive(it) { v -> gyroTarget = v } }
        }
        IgDivider()
        val initGyroSensitivity by XServerDialogState.gyroSensitivity.collectAsState()
        var gyroSensitivity by remember(initGyroSensitivity) { mutableFloatStateOf(initGyroSensitivity) }
        IgSliderRow(
            title = stringResource(R.string.gyro_sensitivity_label),
            value = gyroSensitivity,
            range = 0.1f..10f,
            step = (10f - 0.1f) / 20f,
            format = { "%.1f".format(it) },
            onChange = { gyroSensitivity = it },
            onFinished = {
                XServerDialogState.setGyroSensitivity(gyroSensitivity)
                XServerDialogState.onGyroSensitivityChanged?.invoke(gyroSensitivity)
            },
            scopes = listOf(game),
        )
        IgDivider()
        // Which button gates the tilt ("Always" removes the gate), then Hold / Toggle for it (greyed under "Always").
        val initGyroActivator by XServerDialogState.gyroActivator.collectAsState()
        var gyroActivator by remember(initGyroActivator) { mutableIntStateOf(initGyroActivator) }
        val initGyroActivationMode by XServerDialogState.gyroActivationMode.collectAsState()
        var gyroActivationMode by remember(initGyroActivationMode) { mutableIntStateOf(initGyroActivationMode) }
        val activationModeEnabled = gyroActivator != 4
        IgRow(
            stringResource(R.string.gyro_drawer_activation),
            hint = if (activationModeEnabled && gyroActivationMode == 1) stringResource(R.string.gyro_drawer_toggle_hint) else null,
            scopes = listOf(game),
            wideControl = true,
        ) {
            Column(horizontalAlignment = if (LocalIgCompact.current) Alignment.Start else Alignment.End) {
                IgChips(
                    listOf(
                        0 to stringResource(R.string.gyro_activator_l1),
                        1 to stringResource(R.string.gyro_activator_l2),
                        2 to stringResource(R.string.gyro_activator_r1),
                        3 to stringResource(R.string.gyro_activator_r3),
                        4 to stringResource(R.string.gyro_drawer_activator_always),
                    ),
                    gyroActivator,
                ) { labCtlSetGyroActivatorLive(it) { v -> gyroActivator = v } }
                Spacer(Modifier.height(8.dp))
                IgSegmented(
                    listOf(0 to stringResource(R.string.gyro_activation_hold), 1 to stringResource(R.string.gyro_activation_toggle)),
                    gyroActivationMode,
                    enabled = activationModeEnabled,
                ) { labCtlSetGyroActivationModeLive(it) { v -> gyroActivationMode = v } }
            }
        }
    }

    // Fine tuning: set once, kept last.
    IgSectionLabel(L("Fine tuning"))
    IgCard {
        val initGyroDeadzone by XServerDialogState.gyroDeadzone.collectAsState()
        var gyroDeadzone by remember(initGyroDeadzone) { mutableFloatStateOf(initGyroDeadzone) }
        IgSliderRow(
            title = stringResource(R.string.gyro_deadzone_label),
            value = gyroDeadzone,
            range = 0f..0.5f,
            step = 0.5f / 20f,
            format = { "%.2f".format(it) },
            onChange = { gyroDeadzone = it },
            onFinished = {
                XServerDialogState.setGyroDeadzone(gyroDeadzone)
                XServerDialogState.onGyroDeadzoneChanged?.invoke(gyroDeadzone)
            },
            scopes = listOf(SaveScope.CONTAINER),
        )
        IgDivider()
        val initGyroSmoothing by XServerDialogState.gyroSmoothing.collectAsState()
        var gyroSmoothing by remember(initGyroSmoothing) { mutableFloatStateOf(initGyroSmoothing) }
        IgSliderRow(
            title = stringResource(R.string.gyro_smoothing_label),
            value = gyroSmoothing,
            range = 0f..0.95f,
            step = 0.95f / 20f,
            format = { "%.2f".format(it) },
            onChange = { gyroSmoothing = it },
            onFinished = {
                XServerDialogState.setGyroSmoothing(gyroSmoothing)
                XServerDialogState.onGyroSmoothingChanged?.invoke(gyroSmoothing)
            },
            scopes = listOf(SaveScope.CONTAINER),
        )
        IgDivider()
        val gyroInvertX by XServerDialogState.gyroInvertX.collectAsState()
        val gyroInvertY by XServerDialogState.gyroInvertY.collectAsState()
        IgToggleRow(stringResource(R.string.gyro_invert_x), gyroInvertX, scopes = listOf(game)) {
            XServerDialogState.setGyroInvertX(it)
            XServerDialogState.onGyroInvertXChanged?.invoke(it)
        }
        IgDivider()
        IgToggleRow(stringResource(R.string.gyro_invert_y), gyroInvertY, scopes = listOf(game)) {
            XServerDialogState.setGyroInvertY(it)
            XServerDialogState.onGyroInvertYChanged?.invoke(it)
        }
    }
}

// Target / activator / mode changes hit the local picker state AND WinHandler (Classic setGyro*Live, XServerDrawer.kt).
private fun labCtlSetGyroTargetLive(target: Int, reflect: (Int) -> Unit) {
    reflect(target)
    XServerDialogState.setGyroTarget(target)
    XServerDialogState.onGyroTargetChanged?.invoke(target)
}

private fun labCtlSetGyroActivatorLive(activator: Int, reflect: (Int) -> Unit) {
    reflect(activator)
    XServerDialogState.setGyroActivator(activator)
    XServerDialogState.onGyroActivatorChanged?.invoke(activator)
}

private fun labCtlSetGyroModeLive(mode: Int, reflect: (Int) -> Unit) {
    reflect(mode)
    XServerDialogState.setGyroMode(mode)
    XServerDialogState.onGyroModeChanged?.invoke(mode)
}

private fun labCtlSetGyroActivationModeLive(mode: Int, reflect: (Int) -> Unit) {
    reflect(mode)
    XServerDialogState.setGyroActivationMode(mode)
    XServerDialogState.onGyroActivationModeChanged?.invoke(mode)
}

// ───────────────────────────── Players ─────────────────────────────

@Composable
private fun LabCtlPlayersSection() {
    // The activity writes the slot map to the container unless the shortcut already carries its own (persistControllerSlotOverridesJson).
    val game = SaveScope.CONTAINER
    val rows by XServerDialogState.playerSlots.collectAsState()
    // Devices hot-plug, so pull a fresh snapshot whenever this sub-tab is shown.
    LaunchedEffect(Unit) { XServerDialogState.onPlayerSlotsRefresh?.run() }

    IgSectionLabel(L("玩家槽位"))
    IgCard {
        IgRow(
            L("物理控制器测试/绑定"),
            hint = L("测试每个物理控制器，重新映射其按钮，并确认每个输入均已注册。"),
        ) {
            IgButton(L("Test / Bind"), Icons.Outlined.SportsEsports, { XServerDialogState.show(XServerDialogState.ActiveDialog.CONTROLLER_TEST) })
        }
    }
    Spacer(Modifier.height(10.dp))
    IgCard {
        IgInfo(L("将每个设备分配给一个玩家，或将其忽略。立即应用并为此容器保存。"))
        if (rows.isEmpty()) {
            IgDivider()
            IgInfo(L("No input devices detected."))
        } else {
            rows.forEach { row ->
                IgDivider()
                LabCtlPlayerSlotRow(row, game)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    IgCard {
        // Rebuilds the fake-input transport in place (no relaunch), then re-reads the slots.
        IgRow(L("重置输入"), hint = L("如果输入停止响应，重新连接控制器和屏幕控制。")) {
            IgButton(L("重置输入"), Icons.Outlined.Refresh, {
                XServerDialogState.onResetInput?.run()
                XServerDialogState.onPlayerSlotsRefresh?.run()
            })
        }
    }
}

/** One device: name, "currently Player N / unassigned", and Auto / Player 1-4 / Ignore (the onPlayerSlotChanged contract). */
@Composable
private fun LabCtlPlayerSlotRow(row: XServerDialogState.PlayerSlotRow, scope: SaveScope) {
    val options = buildList {
        add(XServerDialogState.SLOT_AUTO to L("Auto"))
        for (i in 0 until 4) add(i to L("Player %1\$s", i + 1))
        add(XServerDialogState.SLOT_IGNORE to L("忽略"))
    }
    // Classic shows "Auto" for an override it doesn't list.
    val selected = if (options.any { it.first == row.override }) row.override else XServerDialogState.SLOT_AUTO
    val subtitle = when {
        row.currentSlot >= 0 -> L("Currently Player %1\$s", row.currentSlot + 1)
        row.override == XServerDialogState.SLOT_IGNORE -> L("Ignored")
        else -> L("未分配")
    }
    IgRow(
        row.displayName,
        hint = subtitle + if (row.isOnScreen) L(" · 屏幕控制") else "",
        scopes = listOf(scope),
        wideControl = true,
    ) {
        IgChips(options, selected) { value ->
            if (value != row.override) XServerDialogState.onPlayerSlotChanged?.invoke(row.descriptor, value)
        }
    }
}

// ───────────────────────────── Swipe ─────────────────────────────

@Composable
private fun LabCtlSwipeSection(state: XServerDrawerState) {
    val swipeButtons by state.swipeButtons.collectAsState()
    val swipeDpad by state.swipeDpad.collectAsState()
    val swipeSticks by state.swipeSticks.collectAsState()

    IgSectionLabel(stringResource(R.string.swipe_tab))
    IgCard {
        IgInfo(L("将手指滑动到控件上即可按下——可连续操作按钮和方向键，无需抬起手指。"))
        IgDivider()
        IgToggleRow(stringResource(R.string.swipe_buttons), swipeButtons, scopes = listOf(SaveScope.ALL)) {
            state.setSwipeButtons(it)
            state.onSetSwipeButtons?.accept(it)
        }
        IgDivider()
        IgToggleRow(stringResource(R.string.swipe_dpad), swipeDpad, scopes = listOf(SaveScope.ALL)) {
            state.setSwipeDpad(it)
            state.onSetSwipeDpad?.accept(it)
        }
        IgDivider()
        IgToggleRow(stringResource(R.string.swipe_sticks), swipeSticks, scopes = listOf(SaveScope.ALL)) {
            state.setSwipeSticks(it)
            state.onSetSwipeSticks?.accept(it)
        }
    }
}

// ───────────────────────────── Pad ─────────────────────────────

/** How a Pad change reaches the game (Classic PadApply). */
private enum class LabCtlPadApply { LIVE, RECONNECT, RESTART }

private fun LabCtlPadApply.label(): String = when (this) {
    LabCtlPadApply.LIVE -> L("实时")
    LabCtlPadApply.RECONNECT -> L("重新连接控制器")
    LabCtlPadApply.RESTART -> L("需要重启")
}

/** Where Pad and the rumble rows save: the "保存用于" choice (ControllerSaveTarget in the activity). */
@Composable
private fun labCtlPadScope(): SaveScope {
    val s by XServerDialogState.controllerSaveScope.collectAsState()
    return if (s.gameName != null && s.toGame) SaveScope.GAME else SaveScope.CONTAINER
}

@Composable
private fun LabCtlPadSection() {
    val pad by XServerDialogState.pad.collectAsState()
    val padScope = labCtlPadScope()
    // "Now: N Hz" follows the game while the sub-tab is open.
    LaunchedEffect(Unit) {
        while (true) {
            XServerDialogState.onPadRefresh?.run()
            delay(1000)
        }
    }

    IgSectionLabel(L("Controller"))
    IgCard {
        // Controller type. The Classic DirectInput pad switches in or out on the next launch.
        val choices = remember { ControllerIdentity.wineChoices() }
        val identityApply = if (pad.nextLaunchOnly.contains("identity")) LabCtlPadApply.RESTART else LabCtlPadApply.RECONNECT
        val identityLocked = pad.identityLockedReason != null
        LabCtlPadRow(L("控制器类型"), identityApply, R.string.help_controller_identity, listOf(padScope), enabled = !identityLocked) {
            LabCtlPickButton(
                title = L("控制器类型"),
                label = ControllerIdentity.label(pad.identity),
                options = choices.map { it.label },
                selected = choices.indexOfFirst { it.value == pad.identity },
                enabled = !identityLocked,
            ) { i ->
                val id = choices[i]
                if (id.value != pad.identity) XServerDialogState.onPadIdentity?.invoke(id.value)
            }
        }
        pad.identityLockedReason?.let { IgNote(it) }
        if (pad.nextLaunchOnly.contains("identity")) IgNote(L("The Classic DirectInput pad switches in or out on the next launch."))

        // XInput source: Direct (BANNERIN) or Wine's own; live only when in-game switching was allowed at launch.
        if (pad.xinputAvailable) {
            IgDivider()
            LabCtlPadRow(
                L("XInput 源"),
                if (pad.xinputSwitchable) LabCtlPadApply.RECONNECT else LabCtlPadApply.RESTART,
                R.string.help_bannerin_xinput,
                listOf(padScope),
            ) {
                IgSegmented(listOf(true to L("Direct"), false to L("Wine")), pad.xinputDirect) { v ->
                    if (v != pad.xinputDirect) XServerDialogState.onPadXInputDirect?.invoke(v)
                }
            }
            if (!pad.xinputSwitchable) IgNote(L("重启以应用。打开“允许游戏内切换”以在不重启的情况下切换。"))
        }

        // DirectInput visibility.
        IgDivider()
        val dinputOpen = pad.dinputLockedReason == null
        LabCtlPadRow(stringResource(R.string.dinput_visible), LabCtlPadApply.RECONNECT, R.string.help_dinput_visible, listOf(padScope), enabled = dinputOpen) {
            IgSegmented(listOf(false to L("隐藏"), true to L("可见")), pad.dinputVisible, enabled = dinputOpen) { v ->
                if (v != pad.dinputVisible) XServerDialogState.onPadDInputVisible?.invoke(v)
            }
        }
        pad.dinputLockedReason?.let { IgNote(it) }

        // DirectInput vibration: BANNERIN takes it when the game opens the pad, so a change reconnects it.
        IgDivider()
        val dinputVibrationOpen = pad.dinputVibrationLockedReason == null
        LabCtlPadRow(stringResource(R.string.dinput_vibration), LabCtlPadApply.RECONNECT, R.string.help_dinput_vibration, listOf(padScope), enabled = dinputVibrationOpen) {
            IgSegmented(listOf(true to L("On"), false to L("Off")), pad.dinputVibration, enabled = dinputVibrationOpen) { v ->
                if (v != pad.dinputVibration) XServerDialogState.onPadDInputVibration?.invoke(v)
            }
        }
        pad.dinputVibrationLockedReason?.let { IgNote(it) }

        // Stick updates: BANNERIN's pacing, live. − / + rather than a slider (core.StickPacing.step).
        IgDivider()
        val pacing = StickPacing.parse(pad.pacing) ?: StickPacing.DEFAULT
        val pacingLocked = pad.pacingLockedReason != null
        LabCtlPadRow(L("摇杆更新"), LabCtlPadApply.LIVE, R.string.help_stick_pacing, listOf(padScope), enabled = !pacingLocked) {
            IgSegmented(
                listOf(StickPacing.MODE_AUTO to L("Auto"), StickPacing.MODE_FIXED to L("Fixed"), StickPacing.MODE_OFF to L("Unpaced")),
                pacing.mode,
                enabled = !pacingLocked,
            ) { m ->
                when (m) {
                    StickPacing.MODE_AUTO -> XServerDialogState.onPadPacing?.invoke(StickPacing(StickPacing.MODE_AUTO, StickPacing.AUTO_DEFAULT).format())
                    StickPacing.MODE_FIXED -> XServerDialogState.onPadPacing?.invoke(StickPacing(StickPacing.MODE_FIXED, StickPacing.FIXED_DEFAULT).format())
                    else -> XServerDialogState.onPadPacing?.invoke(StickPacing(StickPacing.MODE_OFF, 0).format())
                }
            }
            if (pacing.mode != StickPacing.MODE_OFF) {
                val down = pacing.step(-1)
                val up = pacing.step(1)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        pacing.rateLabel(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = NunitoSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                    )
                    IgButton("−", null, { XServerDialogState.onPadPacing?.invoke(down.format()) }, enabled = !pacingLocked && down != pacing)
                    IgButton("+", null, { XServerDialogState.onPadPacing?.invoke(up.format()) }, enabled = !pacingLocked && up != pacing)
                }
            }
        }
        IgNote(if (pad.nowHz > 0) L("Now: %1\$s Hz", pad.nowHz) else L("当前：尚无游戏读取手柄"))
        pad.pacingLockedReason?.let { IgNote(it) }
        Spacer(Modifier.height(6.dp))
    }

    // Changes wait for the controller to reconnect (shortly after the last change, or when the menu closes), or keep
    // the session as it is and apply on the next launch.
    if (pad.pendingReconnect || pad.reconnecting) {
        Spacer(Modifier.height(10.dp))
        IgCard {
            if (pad.reconnecting) {
                IgRow(L("Reconnecting the controller…"))
            } else {
                IgRow(L("The controller reconnects to apply this.")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IgButton(L("Reconnect now"), null, { XServerDialogState.onPadReconnectNow?.run() }, primary = true)
                        IgButton(L("Next launch"), null, { XServerDialogState.onPadNextLaunch?.run() })
                    }
                }
            }
        }
    }

    val laterNotes = pad.nextLaunchOnly.filter { it != "identity" && it != "xinput" }
    val xinputLater = pad.nextLaunchOnly.contains("xinput") && pad.xinputSwitchable
    Spacer(Modifier.height(10.dp))
    IgCard {
        if (laterNotes.isNotEmpty() || xinputLater) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                laterNotes.forEach { IgNote(it) }
                if (xinputLater) IgNote(L("XInput source applies on the next launch."))
            }
            IgDivider()
        }
        LabCtlSaveForRows()
    }
}

/** A Pad setting row: how the change applies, the Classic "?" help, then its control. */
@Composable
private fun LabCtlPadRow(
    title: String,
    apply: LabCtlPadApply,
    @StringRes helpRes: Int,
    scopes: List<SaveScope>,
    enabled: Boolean = true,
    control: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val help = labCtlHelpText(helpRes)
    val compact = LocalIgCompact.current
    IgRow(title, scopes = scopes, enabled = enabled, wideControl = true) {
        Column(horizontalAlignment = if (compact) Alignment.Start else Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IgBadge(apply.label(), dot = if (apply == LabCtlPadApply.RESTART) cs.error else cs.primary)
                IgHelpButton(title, help)
            }
            Spacer(Modifier.height(8.dp))
            control()
        }
    }
}

/**
 * "Save for: This game / All games in container" under Pad and the rumble rows (Classic SaveScopeFooter). A desktop /
 * container launch has no game, so only the note shows.
 */
@Composable
private fun LabCtlSaveForRows() {
    val scope by XServerDialogState.controllerSaveScope.collectAsState()
    val s = scope
    if (s.gameName != null) {
        IgRow(L("保存用于"), wideControl = true) {
            IgSegmented(listOf(true to L("This game"), false to L("All games in container")), s.toGame) {
                XServerDialogState.onControllerSaveScope?.invoke(it)
            }
        }
    }
    IgNote(
        s.lastSaved ?: when {
            s.gameName == null -> L("Changes are saved for every game in %1\$s.", s.containerName)
            s.toGame -> L("Changes are saved for %1\$s only.", s.gameName)
            else -> L("Changes are saved for every game in %1\$s.", s.containerName)
        },
        modifier = Modifier.padding(top = if (s.gameName == null) 8.dp else 0.dp, bottom = 8.dp),
    )
}

// ───────────────────────────── Shared pieces ─────────────────────────────

/** The Classic help text (small HTML: <b>, <br>) as plain text for [IgHelpButton]. */
@Composable
private fun labCtlHelpText(@StringRes res: Int): String {
    val raw = stringResource(res)
    return remember(raw) { com.winlator.star.ui.screens.htmlToAnnotated(raw).text }
}

/** [IgPickButton] with its own button text, for a current value the list may not hold (an identity outside the picker). */
@Composable
private fun LabCtlPickButton(
    title: String,
    label: String,
    options: List<String>,
    selected: Int,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IgButton("$label  ▾", null, { open = true }, enabled = enabled)
    if (open) {
        SimpleActionSheet(
            title = title,
            actions = options.mapIndexed { i, o -> MenuAction(if (i == selected) "$o  ✓" else o, { onPick(i) }) },
            onDismiss = { open = false },
        ) { a ->
            open = false
            a.run()
        }
    }
}

/**
 * The controls-accent picker (Classic components/ColorPicker: preview, hue / saturation / brightness bars, hex field) in
 * Lab pieces. Same seeding (once, from [initialColor]) and the same onColorChanged on every change.
 */
@Composable
private fun LabCtlColorPicker(initialColor: Color, onColorChanged: (Color) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hsv = remember(initialColor) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), arr)
        arr
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var hexInput by remember { mutableStateOf(initialColor.toHexString()) }
    var hexError by remember { mutableStateOf(false) }

    fun currentColor() = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    fun notifyChange() {
        val c = currentColor()
        hexInput = c.toHexString()
        hexError = false
        onColorChanged(c)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(currentColor())
                    .border(1.dp, deckLineAt(0.6f), CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(L("预览"), color = cs.onSurfaceVariant, fontFamily = NunitoSansFamily, fontSize = 12.5.sp)
                Text(hexInput, color = cs.onSurface, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
        }
        LabCtlGradientBar(
            L("色相"), hue, 0f..360f,
            Brush.horizontalGradient((0..12).map { i -> Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f))) }),
        ) { hue = it; notifyChange() }
        LabCtlGradientBar(
            L("饱和度"), saturation, 0f..1f,
            Brush.horizontalGradient(
                listOf(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, brightness))),
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, brightness))),
                )
            ),
        ) { saturation = it; notifyChange() }
        LabCtlGradientBar(
            L("亮度"), brightness, 0f..1f,
            Brush.horizontalGradient(listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))))),
        ) { brightness = it; notifyChange() }
        IgTextField(
            value = hexInput,
            onValueChange = { raw ->
                hexInput = raw
                val clean = raw.trimStart('#')
                if (clean.length == 6) {
                    try {
                        val parsed = android.graphics.Color.parseColor("#$clean")
                        val arr = FloatArray(3)
                        android.graphics.Color.colorToHSV(parsed, arr)
                        hue = arr[0]; saturation = arr[1]; brightness = arr[2]
                        hexError = false
                        onColorChanged(Color(parsed))
                    } catch (_: Exception) {
                        hexError = true
                    }
                } else {
                    hexError = clean.length > 6
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = L("十六进制颜色代码 (#RRGGBB)"),
            monospace = true,
        )
        if (hexError) IgNote(L("Enter a valid 6-digit hex color"), error = true)
    }
}

/**
 * One gradient bar of the accent picker: a tap or a drag sets the value; with it focused, d-pad left / right steps it by
 * a twentieth of its range (the Classic drawer's slider step).
 */
@Composable
private fun LabCtlGradientBar(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    brush: Brush,
    onChange: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val change by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    var widthPx by remember { mutableIntStateOf(0) }
    val span = range.endInclusive - range.start
    val step = span / 20f
    val shape = deckShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = cs.onSurfaceVariant, fontFamily = NunitoSansFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .deckFocusRing(shape, scaleTo = 1.02f)
                .onPreviewKeyEvent { ev ->
                    val dir = when (ev.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        else -> 0
                    }
                    if (dir == 0) return@onPreviewKeyEvent false
                    if (ev.type == KeyEventType.KeyDown) change((current + dir * step).coerceIn(range.start, range.endInclusive))
                    true
                }
                .focusable()
                .clip(shape)
                .background(brush)
                .border(1.dp, deckLineAt(0.5f), shape)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(range) {
                    detectTapGestures { offset ->
                        if (widthPx > 0) change(range.start + (offset.x / widthPx).coerceIn(0f, 1f) * span)
                    }
                }
                .pointerInput(range) {
                    detectHorizontalDragGestures { c, _ ->
                        if (widthPx > 0) change(range.start + (c.position.x / widthPx).coerceIn(0f, 1f) * span)
                    }
                },
        ) {
            val fraction = if (span > 0f) ((value - range.start) / span).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth(fraction)) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                            .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
                    )
                }
            }
        }
    }
}
