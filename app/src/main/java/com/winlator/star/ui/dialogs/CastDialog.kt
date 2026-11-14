package com.winlator.star.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.star.ui.XServerDialogState

@Composable
fun CastDialog(state: XServerDialogState) {
    val devices by state.castDevices.collectAsState()
    val scanning by state.castScanning.collectAsState()
    val status by state.castStatus.collectAsState()
    val targetName by state.castTargetName.collectAsState()
    val detail by state.castStatusDetail.collectAsState()

    var showHelp by remember { mutableStateOf(false) }
    // Which device row is expanded (tapped). Selecting a device only shows its info + a Connect button;
    // it does NOT auto-connect — the user connects explicitly.
    var selected by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { state.dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Title row: name + "?" help toggle + Refresh (Refresh hidden on the help screen).
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showHelp) "投屏 — 选项与提示" else "投屏到电视TV （无线）",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!showHelp) {
                        if (scanning) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        // Round "?" help button.
                        Box(Modifier.size(28.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showHelp = true }, contentAlignment = Alignment.Center) {
                            Text("?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.size(4.dp))
                        TextButton(onClick = { state.onCastRefresh?.run() }) { Text("刷新") }
                    }
                }

                if (showHelp) {
                    CastHelp()
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showHelp = false }) { Text("Back") }
                    }
                    return@Column
                }

                Text(
                    text = "Google TV / Chromecast devices on your Wi-Fi. Nothing to install on the TV. " +
                        "Tap the “?” for how casting works and the trade-offs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                if (devices.isEmpty()) {
                    Text(
                        text = if (scanning) "正在搜索…" else "No devices found. Make sure your TV is on the " +
                            "same Wi-Fi, then tap Refresh.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        devices.forEach { d ->
                            // "Active" = this device is the one we're connecting to / connected to.
                            val isActive = d.name == targetName && status != XServerDialogState.CastStatus.IDLE
                            val expanded = d.name == selected || isActive
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selected = d.name },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (expanded) MaterialTheme.colorScheme.primaryContainer
                                                     else MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(d.name, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                    Text(d.type, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                                    if (expanded) {
                                        if (d.host.isNotBlank())
                                            Text(d.host, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                                        Spacer(Modifier.height(6.dp))

                                        if (isActive) {
                                            // Connected / connecting: show live status + Disconnect.
                                            val label = when (status) {
                                                XServerDialogState.CastStatus.CONNECTING -> "正在连接…"
                                                XServerDialogState.CastStatus.CONNECTED  -> "Connected"
                                                XServerDialogState.CastStatus.FAILED     -> "Failed to connect"
                                                else -> ""
                                            }
                                            val color = when (status) {
                                                XServerDialogState.CastStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                                XServerDialogState.CastStatus.FAILED    -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (status == XServerDialogState.CastStatus.CONNECTING)
                                                    CircularProgressIndicator(Modifier.size(14.dp).padding(end = 6.dp), strokeWidth = 2.dp)
                                                Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            }
                                            if (detail.isNotBlank())
                                                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                            TextButton(onClick = { state.onCastDisconnect?.run() },
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                                Text("Disconnect")
                                            }
                                        } else {
                                            // Selected but not connected: explicit Connect button.
                                            TextButton(onClick = { state.onCastConnect?.accept(d) },
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                                Text("Connect")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { state.dismiss() }) { Text("Close") }
                }
            }
        }
    }
}

// The "?" help: explains the ways to get the game onto a TV and the pros/cons of each.
@Composable
private fun CastHelp() {
    Column(modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
        HelpBlock(
            title = "投屏 — 电视TV上无应用（此屏幕）· 实验性",
            body = "从列表中选择一个 Google TV 或 Chromecast投屏器 — TV上无需安装任何内容。",
            pros = listOf("电视TV上无需设置任何内容", "使用您已有的设备"),
            cons = listOf(
                "几秒延迟（视频缓冲）— 适合慢速游戏，不适合快节奏游戏",
                "目前仅支持视频 — 游戏声音保留在此设备上",
                "仅支持 Google TV / Chromecast投屏器 — Roku 不在此列表中"
            )
        )
        HelpBlock(
            title = "投屏 — 使用接收端应用（即将推出）",
            body = "在 Google TV 上安装一次我们的小应用，然后从此同一列表投屏。",
            pros = listOf("清晰、低延迟 — 适合任何游戏", "手机保持空闲（手机黑屏，游戏在TV上）"),
            cons = listOf("需要在 Google TV 上一次性安装", "不适用于 Roku（封闭设备）")
        )
        HelpBlock(
            title = "有线线缆 — 目前最低延迟",
            body = "USB‑C→HDMI 线缆可将游戏移至电视TV，几乎无延迟。",
            pros = listOf("最佳质量和响应性", "无需 Wi‑Fi"),
            cons = listOf("需要线缆和 HDMI 端口")
        )
        HelpBlock(
            title = "Roku 电视TV",
            body = "Roku can't run our app and can't take our stream. Use Android's built-in Screen " +
                "Mirroring (in Settings) with the Roku's Screen-mirroring turned on.",
            pros = listOf("无需应用"),
            cons = listOf("使用 Android 的系统屏幕，而非此应用内列表", "取决于您手机的 Miracast")
        )
    }
}

@Composable
private fun HelpBlock(title: String, body: String, pros: List<String>, cons: List<String>) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(body, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
        pros.forEach { Text("✓  $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) }
        cons.forEach { Text("✗  $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
    }
}
