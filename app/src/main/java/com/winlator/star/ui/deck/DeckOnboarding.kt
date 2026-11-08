package com.winlator.star.ui.deck

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import com.winlator.star.store.AmazonCredentialStore
import com.winlator.star.store.EpicCredentialStore
import com.winlator.star.store.SteamPrefs
import com.winlator.star.ui.Screen
import com.winlator.star.ui.theme.AppThemeState
import com.winlator.star.ui.theme.CUSTOM_PRESET_INDEX
import com.winlator.star.ui.theme.themePresets

/** The seven first-run steps, in order. */
private val ONBOARDING_STEPS = listOf(
    "欢迎使用 Bannerlator",
    "访问您的文件",
    "Notifications",
    "按您的喜好设置",
    "正在安装基础系统",
    "登录您的商店（可选）",
    "您已准备就绪",
)

/** The one interface size the first run offers: Compact, Appearance's UI size at 80%. The full slider stays in Appearance. */
private const val UI_SIZE_COMPACT = 0.8f

/** Marks the Deck first-run finished so it doesn't come back on the next start. */
internal fun markDeckSetupDone(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(DeckSetup.PREF_DONE, true).apply()
    DeckSetup.rerun.value = false
}

/** True when this start should open the Deck first-run: Deck style and never finished. */
internal fun deckSetupPending(context: Context): Boolean =
    AppThemeState.uiStyle.value == AppThemeState.UI_STYLE_DECK &&
        !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DeckSetup.PREF_DONE, false)

private fun filesGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Sign-in state of the five stores, read the same way each store's own screen reads it. */
private fun storeSignIns(context: Context): Map<Screen, Boolean> = mapOf(
    Screen.Steam to runCatching { SteamPrefs.init(context); SteamPrefs.isLoggedIn }.getOrDefault(false),
    Screen.Epic to runCatching { EpicCredentialStore.isLoggedIn(context) }.getOrDefault(false),
    Screen.Gog to runCatching { context.getSharedPreferences("bh_gog_prefs", 0).getString("access_token", null) != null }.getOrDefault(false),
    Screen.Amazon to runCatching { AmazonCredentialStore.isLoggedIn(context) }.getOrDefault(false),
    Screen.Xbox to runCatching { com.winlator.star.store.xbox.XboxAccountStore.signedInAs(context) != null }.getOrDefault(false),
)

/**
 * The Deck first-run: welcome, file access, notifications, theme and interface style, the base-system
 * install, optional store sign-in, done. It sits over the whole app while it is up. Step 5 shows the
 * SplashViewModel install the Classic splash would show ([installing], [installProgress],
 * [installDone]) and [onInstallContinue] is the splash's own Proceed. [onFinish] gets true when the
 * user picked "Add a game" on the last step.
 */
@Composable
internal fun DeckOnboarding(
    installing: Boolean,
    installProgress: Int,
    installDone: Boolean,
    onInstallContinue: () -> Unit,
    onLaunchStore: (Screen) -> Unit,
    onFinish: (addGame: Boolean) -> Unit,
) {
    DeckTheme {
        DeckOnboardingContent(installing, installProgress, installDone, onInstallContinue, onLaunchStore, onFinish)
    }
}

@Composable
private fun DeckOnboardingContent(
    installing: Boolean,
    installProgress: Int,
    installDone: Boolean,
    onInstallContinue: () -> Unit,
    onLaunchStore: (Screen) -> Unit,
    onFinish: (addGame: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val compact = deckCompact()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = ONBOARDING_STEPS.size - 1

    // Permission and sign-in state is re-read whenever the app comes back (from Android settings, a store login).
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val filesOk = remember(resumeTick) { filesGranted(context) }
    var notifTick by remember { mutableIntStateOf(0) }
    val notifOk = remember(resumeTick, notifTick) { notificationsGranted(context) }
    val stores = remember(resumeTick) { storeSignIns(context) }

    val legacyStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resumeTick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifTick++ }

    val requestFiles: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // The same Android screen MainActivity's "All files access" dialog opens.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).setData(Uri.parse("package:${context.packageName}"))
                )
            }
        } else {
            legacyStorageLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    // The install is "done" once it reports Proceed, or when nothing is installing at all (already installed).
    val installReady = installDone || !installing
    val finish: (Boolean) -> Unit = { addGame ->
        // Leaving the first-run with the install finished but not yet acknowledged would leave the splash overlay armed.
        if (installing && installDone) onInstallContinue()
        markDeckSetupDone(context)
        onFinish(addGame)
    }
    val next: () -> Unit = {
        when {
            step == 4 && installing && installDone -> { onInstallContinue(); step++ }
            step == 4 && !installReady -> Unit
            step < last -> step++
            else -> finish(false)
        }
    }
    val back: () -> Unit = { if (step > 0) step-- }
    BackHandler(enabled = step > 0) { back() }

    val continueRequester = remember { FocusRequester() }
    LaunchedEffect(step) {
        withFrameNanos { }
        runCatching { continueRequester.requestFocus() }
    }

    val body: @Composable () -> Unit = {
        StepBody(
            step = step,
            compact = compact,
            filesOk = filesOk,
            notifOk = notifOk,
            stores = stores,
            installing = installing,
            installProgress = installProgress,
            installReady = installReady,
            onRequestFiles = requestFiles,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onLaunchStore = onLaunchStore,
            onNext = next,
            onFinish = finish,
        )
        Spacer(Modifier.height(24.dp))
        NavButtons(
            step = step,
            last = last,
            compact = compact,
            continueEnabled = step != 4 || installReady,
            continueRequester = continueRequester,
            onBack = back,
            onSkip = { finish(false) },
            onContinue = next,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OnboardingMark(40)
                    Spacer(Modifier.width(12.dp))
                    Text("设置 Bannerlator", color = cs.onSurface, fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { (step + 1) / ONBOARDING_STEPS.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().clip(deckShape(3.dp)),
                    color = cs.primary,
                    trackColor = cs.surfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                body()
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                StepPanel(step = step, modifier = Modifier.fillMaxHeight().fillMaxWidth(0.31f).widthIn(max = 460.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 48.dp, vertical = 36.dp)
                        .widthIn(max = 1100.dp),
                ) { body() }
            }
        }
    }
}

/** Left panel on a wide screen: brand tile, heading and the step list with done/current states. */
@Composable
private fun StepPanel(step: Int, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.16f), cs.surface.copy(alpha = 0.6f))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 36.dp),
    ) {
        OnboardingMark(64)
        Spacer(Modifier.height(24.dp))
        Text("设置 Bannerlator", color = cs.onSurface, fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp)
        Spacer(Modifier.height(20.dp))
        ONBOARDING_STEPS.forEachIndexed { i, label ->
            val current = i == step
            val done = i < step
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(deckShape(14.dp))
                    .background(if (current) cs.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (current) Modifier.background(cs.primary, CircleShape)
                            else Modifier.border(1.5.dp, if (done) cs.primary else cs.outline, CircleShape)
                        ),
                ) {
                    if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp))
                    else Text("${i + 1}", color = if (current) cs.onPrimary else cs.onSurfaceVariant, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = label,
                    color = if (current) cs.onSurface else cs.onSurfaceVariant,
                    fontWeight = if (current) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

/** The Deck brand mark (accent keycap with a play notch), sized for the onboarding. */
@Composable
private fun OnboardingMark(sizeDp: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val r = size.width * 0.3f
        drawRoundRect(
            brush = Brush.sweepGradient(listOf(accent, lerp(accent, Color.White, 0.45f), accent)),
            cornerRadius = CornerRadius(r, r),
        )
        val w = size.width
        val h = size.height
        val play = Path().apply {
            moveTo(w * 0.36f, h * 0.27f)
            lineTo(w * 0.74f, h * 0.5f)
            lineTo(w * 0.36f, h * 0.73f)
            close()
        }
        drawPath(play, bg)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepBody(
    step: Int,
    compact: Boolean,
    filesOk: Boolean,
    notifOk: Boolean,
    stores: Map<Screen, Boolean>,
    installing: Boolean,
    installProgress: Int,
    installReady: Boolean,
    onRequestFiles: () -> Unit,
    onRequestNotifications: () -> Unit,
    onLaunchStore: (Screen) -> Unit,
    onNext: () -> Unit,
    onFinish: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = "STEP ${step + 1} OF ${ONBOARDING_STEPS.size}",
        color = cs.primary,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
        letterSpacing = 1.8.sp,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = ONBOARDING_STEPS[step],
        color = cs.onSurface,
        fontFamily = SoraFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = if (compact) 30.sp else 52.sp,
        lineHeight = if (compact) 34.sp else 58.sp,
        letterSpacing = (-0.5).sp,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = when (step) {
            0 -> "在此设备上运行 Windows 和 Linux PC 游戏。设置大约需要一分钟 — 基础系统会在您完成设置的过程中安装。"
            1 -> "Bannerlator 需要 \"所有文件访问权限\" 才能读取内部存储、SD 卡和 USB 驱动器上的游戏、安装程序和存档。不会上传任何内容。"
            2 -> "获取有关下载完成、解压进度、Steam 聊天和应用更新的通知。"
            3 -> "选择颜色主题、界面风格以及元素大小。所有这些都可以稍后在设置 › 外观中更改。"
            4 -> "如果尚未完成，请在此等待。在此之前您无法启动游戏。"
            5 -> "导入您拥有的游戏。如果您只玩自己的安装程序，请跳过。"
            else -> "从商店添加游戏，或使用 \"添加游戏\" 从文件安装游戏。"
        },
        color = cs.onSurfaceVariant,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        modifier = Modifier.widthIn(max = 820.dp),
    )
    Spacer(Modifier.height(24.dp))

    when (step) {
        0 -> DeckCard {
            DeckRow(title = "开始使用", subtitle = "七个简短步骤；每一步都可以跳过。", trailing = {
                DeckButton("开始使用", null, onNext)
            })
        }
        1 -> DeckCard {
            DeckRow(
                title = "允许文件访问",
                subtitle = if (filesOk) "已授予。" else "打开 Android 设置；返回后设置将继续。",
                trailing = { if (filesOk) GrantedMark() else DeckButton("允许文件访问", null, onRequestFiles) },
            )
            DeckRowDivider()
            DeckRow(
                title = "暂不",
                subtitle = "您可以稍后从 Android 的应用设置中授予。在您授予之前，共享存储上的游戏将无法打开。",
                trailing = { DeckButton("暂不", null, onNext) },
            )
        }
        2 -> DeckCard {
            DeckRow(
                title = "允许通知",
                subtitle = if (notifOk) "已授予。" else null,
                trailing = { if (notifOk) GrantedMark() else DeckButton("允许通知", null, onRequestNotifications) },
            )
            DeckRowDivider()
            DeckRow(title = "跳过", trailing = { DeckButton("跳过", null, onNext) })
        }
        3 -> {
            val presetIndex by AppThemeState.presetIndex.collectAsState()
            val uiStyle by AppThemeState.uiStyle.collectAsState()
            val uiScale by AppThemeState.uiScale.collectAsState()
            // The Deck is laid out for Compact: the first run sets it; Appearance can change it later.
            LaunchedEffect(Unit) { AppThemeState.setUiScale(UI_SIZE_COMPACT) }
            DeckCard {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("主题", color = cs.onSurface, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // The last slot is "Custom", which needs Appearance's colour picker.
                        themePresets.forEachIndexed { i, preset ->
                            if (i != CUSTOM_PRESET_INDEX) {
                                DeckChoiceChip(preset.name, i == presetIndex, dot = preset.primary) { AppThemeState.setPreset(i) }
                            }
                        }
                    }
                }
                DeckRowDivider()
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("界面风格", color = cs.onSurface, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "Simple is a full-screen launcher for controllers; Deck has tabs across the top. Classic is the drawer " +
                            "and top bar; choosing it closes this setup and switches straight away.",
                        color = cs.onSurfaceVariant,
                        fontSize = 13.5.sp,
                    )
                    val simpleHome by AppThemeState.simpleHome.collectAsState()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeckChoiceChip("Lab", uiStyle == AppThemeState.UI_STYLE_DECK && simpleHome) {
                            AppThemeState.setDeckXmbHome(false)
                            AppThemeState.setSimpleHome(true)
                            AppThemeState.setUiStyle(AppThemeState.UI_STYLE_DECK)
                        }
                        DeckChoiceChip("Deck", uiStyle == AppThemeState.UI_STYLE_DECK && !simpleHome) {
                            AppThemeState.setSimpleHome(false)
                            AppThemeState.setUiStyle(AppThemeState.UI_STYLE_DECK)
                        }
                        DeckChoiceChip("Classic", uiStyle == AppThemeState.UI_STYLE_CLASSIC) {
                            AppThemeState.setUiStyle(AppThemeState.UI_STYLE_CLASSIC)
                            onFinish(false)
                        }
                    }
                }
                DeckRowDivider()
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("界面大小", color = cs.onSurface, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeckChoiceChip("紧凑", kotlin.math.abs(uiScale - UI_SIZE_COMPACT) < 0.01f) { AppThemeState.setUiScale(UI_SIZE_COMPACT) }
                    }
                }
            }
        }
        4 -> {
            DeckCard {
                DeckRow(
                    title = if (installReady) "基础系统已安装" else "正在安装…",
                    subtitle = when {
                        !installing -> "游戏所需的一切已在此设备上。"
                        installReady -> "完成。继续以完成设置。"
                        else -> "$installProgress% — this runs once and takes about a minute."
                    },
                    trailing = { if (installReady) GrantedMark() },
                )
            }
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { if (!installing) 1f else installProgress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(deckShape(4.dp)),
                color = cs.primary,
                trackColor = cs.surfaceVariant,
            )
        }
        5 -> {
            val showStores by AppThemeState.showStores.collectAsState()
            DeckCard {
                listOf(Screen.Steam, Screen.Epic, Screen.Gog, Screen.Amazon, Screen.Xbox).forEachIndexed { i, store ->
                    if (i > 0) DeckRowDivider()
                    val signedIn = stores[store] == true
                    DeckRow(
                        title = store.label,
                        subtitle = if (signedIn) "已登录" else null,
                        trailing = {
                            if (signedIn) GrantedMark()
                            else DeckButton("Sign in", null, { onLaunchStore(store) })
                        },
                    )
                }
                DeckRowDivider()
                DeckRow(
                    title = "我不使用商店 — 隐藏它们",
                    trailing = { Switch(checked = !showStores, onCheckedChange = { AppThemeState.setShowStores(!it) }) },
                )
                DeckRowDivider()
                DeckRow(title = "跳过", trailing = { DeckButton("跳过", null, onNext) })
            }
        }
        else -> DeckCard {
            DeckRow(title = "Add a game", subtitle = "选择此设备上的安装程序或游戏文件夹。", trailing = {
                DeckButton("Add a game", null, { onFinish(true) })
            })
            DeckRowDivider()
            DeckRow(title = "前往库", trailing = { DeckButton("前往库", null, { onFinish(false) }) })
        }
    }
}

/** A small accent check used where a step's action is already done. */
@Composable
private fun GrantedMark() {
    Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
}

@Composable
private fun NavButtons(
    step: Int,
    last: Int,
    compact: Boolean,
    continueEnabled: Boolean,
    continueRequester: FocusRequester,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    val cont: @Composable () -> Unit = {
        OnboardingButton(
            label = if (step == last) "Start playing" else "Continue",
            icon = if (step == last) Icons.Filled.SportsEsports else Icons.AutoMirrored.Filled.ArrowForward,
            glyph = "A" to GlyphKind.A,
            primary = true,
            enabled = continueEnabled,
            onClick = onContinue,
            modifier = Modifier.focusRequester(continueRequester),
        )
    }
    val secondary: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) OnboardingButton("Back", Icons.AutoMirrored.Filled.ArrowBack, "B" to GlyphKind.B, primary = false, onClick = onBack)
            OnboardingButton("跳过设置", null, null, primary = false, onClick = onSkip)
        }
    }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(6.dp)) {
            cont()
            secondary()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            secondary()
            Spacer(Modifier.weight(1f))
            cont()
        }
    }
}

/** DeckButton's look with a chosen controller glyph (A on Continue, B on Back) and a disabled state. */
@Composable
private fun OnboardingButton(
    label: String,
    icon: ImageVector?,
    glyph: Pair<String, GlyphKind>?,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val shape = deckShape(14.dp)
    val ink = if (primary) cs.onPrimary else cs.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .deckFocusRing(shape, scaleTo = 1.04f)
            .clip(shape)
            .background(if (primary) cs.primary.copy(alpha = if (enabled) 1f else 0.4f) else Color.Transparent)
            .then(if (primary) Modifier else Modifier.border(1.dp, deckLine(), shape))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = if (primary) 56.dp else 48.dp)
            .padding(horizontal = if (primary) 26.dp else 18.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(if (primary) 24.dp else 20.dp))
        Text(
            text = label,
            color = ink,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (primary) 19.sp else 15.sp,
            maxLines = 1,
        )
        if (glyph != null) DeckGlyph(glyph.first, glyph.second)
    }
}
