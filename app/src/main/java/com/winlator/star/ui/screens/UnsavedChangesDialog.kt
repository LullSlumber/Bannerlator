package com.winlator.star.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Asked when an editor is left with edits that were never saved (system back, tap outside, ✕ or Cancel).
// Tapping outside the dialog or pressing back again means "继续编辑", so nothing is ever lost by accident.
@Composable
internal fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    OutlinedAlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("保存更改？") },
        text = { Text("您更改了尚未保存的设置。") },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("放弃", color = Color(0xFFE57373)) } // intentional: destructive-action red
                TextButton(onClick = onKeepEditing) { Text("继续编辑") }
            }
        },
    )
}
