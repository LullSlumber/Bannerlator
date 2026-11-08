package com.winlator.star.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winlator.star.container.GameDetails
import com.winlator.star.container.Shortcut
import com.winlator.star.core.WinePath
import com.winlator.star.androidgames.AndroidGames
import com.winlator.star.linux.LinuxShortcuts
import com.winlator.star.store.SteamAchievementStore
import com.winlator.star.ui.DeckDialogBars
import com.winlator.star.ui.DeckDialogScaled
import com.winlator.star.ui.screens.GameMenuAction
import androidx.compose.ui.draw.alpha
import com.winlator.star.ui.screens.ShortcutActionDialogs
import com.winlator.star.ui.screens.ShortcutLaunchDialogs
import com.winlator.star.ui.screens.ShortcutsViewModel
import com.winlator.star.ui.screens.gameMenuOffers
import com.winlator.star.ui.screens.isWineEntry
import com.winlator.star.ui.screens.isSteamOriginShortcut
import com.winlator.star.ui.screens.rememberShortcutActions
import com.winlator.star.ui.screens.rememberShortcutLauncher
import com.winlator.star.ui.screens.steamAppIdOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A game's page in Deck mode: hero art, the facts (store, playtime, last played, achievements,
 * container), Play / Game settings / Options, the setup at a glance, and the whole per-game menu.
 * Play is the shared ShortcutLauncher and every menu item runs through ShortcutActions, so each
 * dialog opens right here rather than on the classic Games screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeckGamePage(
    shortcutPath: String,
    onBack: () -> Unit,
    onOpenContainer: (Int) -> Unit,
    vm: ShortcutsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val shortcuts by vm.shortcuts.collectAsState(initial = emptyList())
    val launcher = rememberShortcutLauncher()
    val actions = rememberShortcutActions(vm)
    val shortcut = shortcuts.firstOrNull { it.file.path == shortcutPath }

    // Same resume refresh as the library, so playtime and edits made in the editor show up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Removed (or renamed away) while the page was open: go back once the list has loaded without it.
    LaunchedEffect(shortcuts.isNotEmpty(), shortcut == null) {
        if (shortcuts.isNotEmpty() && shortcut == null) onBack()
    }

    var menuOpen by remember { mutableStateOf(false) }
    val deckActions = LocalDeckActions.current
    DisposableEffect(deckActions) {
        val open: () -> Unit = { menuOpen = true }
        deckActions.options.value = open
        deckActions.optionsLabel.value = "游戏菜单"
        onDispose { if (deckActions.options.value === open) deckActions.options.value = null }
    }

    // Backdrop tint follows this game's cover, as on Home.
    val ambient = LocalDeckAmbient.current
    val tints by produceState<Pair<Color, Color>?>(null, shortcut?.file?.path, shortcut?.icon) {
        val bmp = shortcut?.icon
        value = if (bmp == null) null else withContext(Dispatchers.Default) { coverTints(bmp) }
    }
    LaunchedEffect(tints) { ambient.value = tints }
    DisposableEffect(ambient) { onDispose { ambient.value = null } }

    if (shortcut == null) {
        ShortcutActionDialogs(actions)
        return
    }
    val s: Shortcut = shortcut
    // An Android game opens as its own app: no container, no Wine setup, no per-game settings.
    val isAndroid = remember(s) { AndroidGames.isAndroidEntry(s) }
    // The Linux Steam client: no Wine container, DX wrapper or Steam launch method of its own.
    val isLinux = remember(s) { LinuxShortcuts.isLinuxEntry(s) }
    // Re-read on every refresh: shortcuts is rebuilt by refresh(), so keying on it picks up new playtime and extras.
    val stats = remember(shortcuts) { readPlayStats(context, listOf(s))[s.file.path] ?: PlayStats(0L, 0, 0L) }
    val details = remember(shortcuts) { GameDetails.from(s) }
    val onSd = remember(s) { !isAndroid && runCatching { WinePath.isOnRemovableStorage(s.container, s.path) }.getOrDefault(false) }
    val achievements by produceState<Pair<Int, Int>?>(null, s.file.path) {
        val appId = if (isSteamOriginShortcut(s)) steamAppIdOf(s) else 0
        value = if (appId <= 0) null else withContext(Dispatchers.IO) {
            SteamAchievementStore.cached(context, appId).takeIf { it.isNotEmpty() }?.let { list -> list.count { it.unlocked } to list.size }
        }
    }
    val playRequester = remember { FocusRequester() }
    DeckEntryFocus(s.file.path) { playRequester.requestFocus() }
    var remembered by remember(shortcuts) { mutableStateOf(rememberedLaunchLabel(s)) }
    val gutter = deckGutter()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Page and game menu side by side: on any landscape page from 720dp (a large display size), otherwise from 900dp.
        val wide = maxWidth >= 900.dp || (maxWidth > maxHeight && maxWidth >= 720.dp)
        // Portrait: the art is a clear banner the page starts under, so the chips and stats never sit on top of it.
        // Otherwise it runs across the top behind the page, fading into it.
        val banner = !wide && maxHeight > maxWidth
        if (!banner) s.icon?.let { bmp ->
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (wide) 420.dp else 300.dp)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.9f), 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
        val backButton: @Composable () -> Unit = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .deckFocusRing(RoundedCornerShape(16.dp), scaleTo = 1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(deckGlass())
                            .border(1.dp, deckLine(), RoundedCornerShape(16.dp))
                            .clickable(onClick = onBack)
                            .size(52.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = cs.onSurface)
                    }
        }
        val main: @Composable (Modifier) -> Unit = { mod ->
            Column(modifier = mod, verticalArrangement = Arrangement.spacedBy(if (banner) 12.dp else 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!banner) {
                        backButton()
                        Spacer(Modifier.width(16.dp))
                    }
                    Icon(Icons.Filled.Storefront, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = (storeLabelOf(s) + if (LinuxShortcuts.isLinuxEntry(s)) " · Linux client" else "").uppercase(),
                        color = cs.primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 2.sp,
                    )
                }
                Text(
                    text = s.name,
                    color = cs.onSurface,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (wide) 48.sp else 32.sp,
                    lineHeight = if (wide) 52.sp else 36.sp,
                    letterSpacing = (-0.8).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isAndroid) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckAndroidPill()
                    AndroidGames.packageOf(s)?.let { DeckPill(it) }
                } else if (isLinux) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckPill("Linux runtime")
                    DeckPill("gamescope · Wayland")
                } else FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The container chip opens that container's settings.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .deckFocusRing(RoundedCornerShape(16.dp), scaleTo = 1.03f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, deckLine(), RoundedCornerShape(16.dp))
                            .clickable { onOpenContainer(s.container.id) }
                            .heightIn(min = 38.dp)
                            .padding(horizontal = 14.dp),
                    ) {
                        Icon(Icons.Filled.Inventory2, contentDescription = null, tint = cs.onSurface, modifier = Modifier.size(17.dp))
                        Text(s.container.name, color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    s.getExtra("dxwrapper", s.container.getDXWrapper()).takeIf { it.isNotBlank() }?.let { DeckPill(it.uppercase()) }
                    s.getExtra("launchMode", "").takeIf { it.isNotBlank() }?.let {
                        DeckPill(when (it) { "RealSteam" -> "SteamLite"; else -> it })
                    }
                    if (onSd) DeckPill("SD card")
                }
                // Stats: playtime, last played, achievements (Steam, when cached), storage.
                val lastPlayed = lastPlayedLabel(context, stats.lastPlayed) ?: if (stats.ms > 0L) "追踪之前" else "从未"
                if (!banner) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val tile = Modifier.widthIn(min = 150.dp, max = 240.dp)
                    DeckStat(playtimeShort(stats.ms), "游玩时间", tile)
                    DeckStat(lastPlayed, "上次游玩", tile)
                    achievements?.let { (got, total) ->
                        DeckStat("$got / $total", "成就", tile, progress = got.toFloat() / total.coerceAtLeast(1))
                    }
                    DeckStat(if (stats.count > 0) "${stats.count}" else "—", if (stats.count == 1) "Launch" else "Launches", tile)
                    if (!isAndroid) DeckStat(if (onSd) "SD card" else "Internal", "Storage", tile)
                } else DeckStatStrip(
                    buildList {
                        add(playtimeShort(stats.ms) to "游玩时间")
                        add(lastPlayed to "上次游玩")
                        achievements?.let { (got, total) -> add("$got/$total" to "成就") }
                        add((if (stats.count > 0) "${stats.count}" else "—") to (if (stats.count == 1) "Launch" else "Launches"))
                        if (!isAndroid) add((if (onSd) "SD card" else "Internal") to "Storage")
                    },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    // Room for the focus glow, which a scroll container would otherwise clip.
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(6.dp),
                ) {
                    DeckButton("游玩", Icons.Filled.PlayArrow, { launcher.requestLaunch(s) }, primary = true, big = true, glyph = "A", modifier = Modifier.focusRequester(playRequester))
                    if (!isAndroid) DeckButton("游戏设置", Icons.Filled.Tune, { actions.perform(GameMenuAction.SETTINGS, s) }, big = true)
                    DeckButton("Options", Icons.Filled.MoreHoriz, { menuOpen = true }, big = true, glyph = "X")
                }
                remembered?.let { how ->
                    DeckCard {
                        DeckRow(
                            title = "Starts with $how every time",
                            subtitle = "The launch choice is remembered for this game.",
                            icon = Icons.Filled.RocketLaunch,
                            trailing = {
                                DeckButton("Ask every time", Icons.AutoMirrored.Filled.Undo, {
                                    s.putExtra("launchModeRemembered", "0")
                                    s.saveData()
                                    remembered = null
                                })
                            },
                        )
                    }
                }
                // Setup at a glance: does this game follow its container, or carry its own settings?
                // An Android game has neither, so the card is left out.
                if (!isAndroid) {
                    val overrides = overrideCount(s)
                    val setupShape = RoundedCornerShape(20.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .deckFocusRing(setupShape, scaleTo = 1.01f)
                            .clip(setupShape)
                            .background(cs.primary.copy(alpha = 0.10f))
                            .border(1.dp, cs.primary.copy(alpha = 0.45f), setupShape)
                            .clickable { actions.perform(GameMenuAction.SETTINGS, s) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Icon(Icons.Filled.AccountTree, contentDescription = null, tint = cs.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    isLinux -> "Steam (Linux) 设置"
                                    overrides == 0 -> "Uses its container’s setup"
                                    else -> "$overrides setting${if (overrides == 1) "" else "s"} set just for this game"
                                },
                                color = cs.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Text(setupSummary(s), color = cs.onSurfaceVariant, fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
                    }
                }
                if (details.hasDisplayableDetails()) {
                    Column {
                        DeckSectionLabel("关于此游戏")
                        DeckCard {
                            val facts = listOfNotNull(
                                details.releaseYear,
                                details.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
                                details.metacritic?.let { "Metacritic $it" },
                            ).joinToString(" · ")
                            DeckRow(
                                title = if (facts.isNotEmpty()) facts else "详情",
                                subtitle = details.description,
                                onClick = { actions.perform(GameMenuAction.GAME_DETAILS, s) },
                            )
                        }
                    }
                }
            }
        }

        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = gutter),
            ) {
                main(
                    Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 24.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .padding(vertical = 24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(deckGlass())
                        .border(1.dp, deckLine(), RoundedCornerShape(28.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    DeckSectionLabel("游戏菜单", Modifier.padding(start = 6.dp))
                    DeckGameMenuList(s, onPlay = { launcher.requestLaunch(s) }, onAction = { actions.perform(it, s) }, onContainer = { onOpenContainer(s.container.id) })
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = if (banner) 0.dp else 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Portrait: the art as a banner of its own, with only the back button on it.
                if (banner) Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    s.icon?.let { bmp ->
                        val img = remember(bmp) { bmp.asImageBitmap() }
                        Image(
                            bitmap = img,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    // Fade only the bottom edge into the page.
                                    drawRect(
                                        brush = Brush.verticalGradient(0.7f to Color.Black, 1f to Color.Transparent),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        )
                    }
                    Box(Modifier.padding(start = gutter, top = 16.dp)) { backButton() }
                }
                main(Modifier.fillMaxWidth().padding(horizontal = gutter))
                Column(Modifier.padding(horizontal = gutter)) {
                    DeckSectionLabel("游戏菜单")
                    DeckGameMenuList(s, onPlay = { launcher.requestLaunch(s) }, onAction = { actions.perform(it, s) }, onContainer = { onOpenContainer(s.container.id) })
                }
            }
        }
    }

    if (menuOpen) {
        DeckGameMenuSheet(
            shortcut = s,
            subtitle = storeLabelOf(s) + " · " + playtimeLabel(stats.ms),
            onDismiss = { menuOpen = false },
            onPlay = { menuOpen = false; launcher.requestLaunch(s) },
            onAction = { menuOpen = false; actions.perform(it, s) },
            onContainer = { menuOpen = false; onOpenContainer(s.container.id) },
        )
    }

    ShortcutActionDialogs(actions)
    ShortcutLaunchDialogs(launcher, onRemove = { vm.remove(it, context) })
}

/** One Deck game-menu entry: a classic ⋮ item (or Play / Container settings) with a line saying what it does. */
private data class GameMenuEntry(val title: String, val hint: String, val action: GameMenuAction?, val danger: Boolean = false)

/** Pseudo-actions the classic ⋮ menu doesn't have. */
private const val ENTRY_PLAY = "play"
private const val ENTRY_CONTAINER = "container"

/**
 * The classic ⋮ menu for [s] in Deck groups: the same items under the same rules ([gameMenuOffers]),
 * plus Play and Container settings at the top. An Android game has no container to open.
 */
private fun gameMenuGroups(s: Shortcut): List<List<Pair<String?, GameMenuEntry>>> {
    val isAndroid = AndroidGames.isAndroidEntry(s)
    fun e(a: GameMenuAction, hint: String, danger: Boolean = false) =
        if (gameMenuOffers(a, s)) null as String? to GameMenuEntry(menuTitle(a), hint, a, danger) else null
    return listOf(
        listOf(ENTRY_PLAY to GameMenuEntry("游玩", when {
            isAndroid -> "打开应用，如同 Android 自身的启动器一样。"
            LinuxShortcuts.isLinuxEntry(s) -> "在 Linux 运行时上启动 Steam 客户端。"
            else -> "从每个视图都走相同的启动路径：启动选项、Steam 预检、Goldberg、云存档同步。"
        }, null)),
        listOfNotNull(
            e(GameMenuAction.SETTINGS, "显示、图形、控制等，仅用于此游戏。"),
            // Android games and the Linux Steam entry have no Wine container to open.
            if (!isWineEntry(s)) null else ENTRY_CONTAINER to GameMenuEntry("容器设置", "打开此游戏运行的容器。", null),
        ),
        listOfNotNull(
            e(GameMenuAction.GAME_DETAILS, "名称、Steam 链接、类型、年份、描述。"),
            e(GameMenuAction.SCRAPE_COVER, "在 SteamGridDB 上查找封面图并选择一张。"),
            e(GameMenuAction.COMMUNITY_CONFIGS, "其他玩家在其设备上为此游戏调整的设置。"),
        ),
        listOfNotNull(
            e(GameMenuAction.CLOUD_SAVES, "此游戏的 Steam 云同步。"),
            e(GameMenuAction.BACKUP_SAVES, "将此游戏的存档压缩到其备份文件夹中。"),
            e(GameMenuAction.RESTORE_SAVES, "选择存档备份并将其恢复到容器中。"),
            e(GameMenuAction.VIEW_LOGS, "此游戏捕获的日志。"),
            e(GameMenuAction.PROPERTIES, "游玩次数和总游玩时间。"),
        ),
        listOfNotNull(
            e(GameMenuAction.CLONE, "将此快捷方式复制到另一个容器。"),
            e(GameMenuAction.COPY_TO_DRIVE_C, "将游戏文件夹复制到容器的 C 盘并重新指向它。"),
            e(GameMenuAction.CHANGE_EXE, "将快捷方式指向游戏文件夹中的另一个 .exe。"),
            e(GameMenuAction.ADD_TO_HOME, "将启动器图标固定到 Android 主屏幕。"),
            e(GameMenuAction.EXPORT, "将此快捷方式保存到您的导出文件夹。"),
        ),
        listOfNotNull(e(GameMenuAction.REMOVE, "会先询问。仅移除快捷方式；游戏文件保留。", danger = true)),
    ).filter { it.isNotEmpty() }
}

private fun menuTitle(a: GameMenuAction): String = when (a) {
    GameMenuAction.SETTINGS -> "游戏设置"
    GameMenuAction.GAME_DETAILS -> "游戏详情"
    GameMenuAction.SCRAPE_COVER -> "封面图"
    GameMenuAction.COMMUNITY_CONFIGS -> "此游戏的社区配置"
    GameMenuAction.CLOUD_SAVES -> "云存档"
    GameMenuAction.VIEW_LOGS -> "View logs"
    else -> a.label
}

/** The grouped game menu, as cards of rows. Used on the game page's side column and in [DeckGameMenuSheet]. */
/** Game-menu actions that need the installed copy of the game. */
private val NEEDS_INSTALL = setOf(GameMenuAction.CLONE, GameMenuAction.COPY_TO_DRIVE_C, GameMenuAction.CHANGE_EXE,
    GameMenuAction.ADD_TO_HOME, GameMenuAction.EXPORT, GameMenuAction.CLOUD_SAVES, GameMenuAction.BACKUP_SAVES,
    GameMenuAction.RESTORE_SAVES, GameMenuAction.COMMUNITY_CONFIGS, GameMenuAction.VIEW_LOGS, GameMenuAction.PROPERTIES)

@Composable
internal fun DeckGameMenuList(
    s: Shortcut,
    onPlay: () -> Unit,
    onAction: (GameMenuAction) -> Unit,
    onContainer: () -> Unit,
    firstRequester: FocusRequester? = null,
) {
    val groups = remember(s) { gameMenuGroups(s) }
    // An Xbox entry not downloaded yet: options that need the installed game say so and do nothing.
    val notInstalled = remember(s) { com.winlator.star.store.xbox.XboxCloudShortcuts.isNotInstalled(s) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        groups.forEachIndexed { gi, group ->
            DeckCard {
                group.forEachIndexed { i, (key, entry) ->
                    if (i > 0) DeckRowDivider()
                    val blocked = notInstalled && (key == ENTRY_CONTAINER || entry.action in NEEDS_INSTALL)
                    DeckRow(
                        title = entry.title,
                        subtitle = if (blocked) "Download the game first" else entry.hint,
                        danger = entry.danger,
                        modifier = (if (gi == 0 && i == 0 && firstRequester != null) Modifier.focusRequester(firstRequester) else Modifier)
                            .then(if (blocked) Modifier.alpha(0.45f) else Modifier),
                        onClick = {
                            when {
                                blocked -> Unit
                                key == ENTRY_PLAY -> onPlay()
                                key == ENTRY_CONTAINER -> onContainer()
                                entry.action != null -> onAction(entry.action)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * The game menu as a right-hand sheet (a bottom-anchored full-width one on a phone): cover, title and
 * a line of facts on top, the grouped menu below. X / Start opens it on Home and on the game page.
 */
@Composable
internal fun DeckGameMenuSheet(
    shortcut: Shortcut,
    subtitle: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onAction: (GameMenuAction) -> Unit,
    onContainer: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val compact = deckCompact()
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DeckDialogBars()
        DeckDialogScaled {
            BackHandler(onBack = onDismiss)
            Box(
                contentAlignment = if (compact) Alignment.BottomCenter else Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
            ) {
                val shape = if (compact) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                Column(
                    modifier = Modifier
                        .then(if (compact) Modifier.fillMaxWidth().fillMaxHeight(0.9f) else Modifier.fillMaxHeight().widthIn(max = 520.dp).fillMaxWidth(0.5f))
                        .clip(shape)
                        .background(cs.surfaceContainerHigh)
                        .border(1.dp, deckLine(), shape)
                        // Swallow taps so they don't reach the dismiss scrim behind the sheet.
                        .clickable(onClick = {}, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("游戏菜单", color = cs.onSurface, fontFamily = SoraFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .deckFocusRing(RoundedCornerShape(14.dp), scaleTo = 1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onDismiss)
                                .size(44.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = cs.onSurface)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 14.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(width = 54.dp, height = 76.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant),
                        ) {
                            val bmp = shortcut.icon
                            if (bmp != null) {
                                val img = remember(bmp) { bmp.asImageBitmap() }
                                Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = cs.primary)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(shortcut.name, color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, color = cs.onSurfaceVariant, fontSize = 13.5.sp, maxLines = 1)
                        }
                    }
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
                        DeckGameMenuList(shortcut, onPlay, onAction, onContainer, firstRequester)
                    }
                }
            }
        }
    }
}
