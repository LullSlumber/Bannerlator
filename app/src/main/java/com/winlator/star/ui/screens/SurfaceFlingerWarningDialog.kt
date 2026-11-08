package com.winlator.star.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Opt-in confirmation shown when the user selects the experimental SurfaceFlinger (ASR) renderer.
 * ASR composites game frames straight through the display hardware; on some devices/GPUs that can
 * fault the display driver and reboot the device, so selecting it requires explicit confirmation.
 */
@Composable
fun SurfaceFlingerWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SurfaceFlinger 将游戏帧直接交给 Android 的合成器。它可能更快，但在某些设备上会显示黑屏或颜色错误。如果发生这种情况，请切换回 Vulkan 或 OpenGL。") },
        text = {
            Text(
                "The SurfaceFlinger renderer composites game frames directly through your device's " +
                "display hardware, bypassing the GL/Vulkan compositor.\n\n" +
                "On some devices and SoCs this can fault the display/GPU driver and REBOOT your device — " +
                "possibly losing unsaved game progress or corrupting the container. It is validated only " +
                "on recent Adreno GPUs.\n\n" +
                "Use at your own risk."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("我理解，继续使用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
