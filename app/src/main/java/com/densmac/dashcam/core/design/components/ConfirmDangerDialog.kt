package com.densmac.dashcam.core.design.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.densmac.dashcam.core.design.haptics.HapticEvent
import com.densmac.dashcam.core.design.haptics.rememberHapticClick
import com.densmac.dashcam.core.design.theme.DialogShape

@Composable
fun ConfirmDangerDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmClick = rememberHapticClick(HapticEvent.Warning, onConfirm)
    val dismissClick = rememberHapticClick(onClick = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = confirmClick) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = dismissClick) { Text("Cancel") }
        }
    )
}
