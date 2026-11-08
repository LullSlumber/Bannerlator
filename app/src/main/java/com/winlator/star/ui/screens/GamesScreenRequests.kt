package com.winlator.star.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The Games screen's per-game ⋮ menu, in the order the classic menu lists it, plus the + button. */
enum class GameMenuAction(val label: String) {
    SETTINGS("Settings"),
    REMOVE("移除"),
    CLONE("复制到容器"),
    MOVE_TO_CONTAINER("Move to another container"),
    COPY_TO_DRIVE_C("复制到C盘…"),
    CHANGE_EXE("更改可执行文件…"),
    ADD_TO_HOME("添加到主屏幕"),
    EXPORT("导出"),
    GAME_DETAILS("游戏详情"),
    CLOUD_SAVES("云端存档"),
    BACKUP_SAVES("备份存档"),
    RESTORE_SAVES("恢复存档"),
    SCRAPE_COVER("获取封面"),
    COMMUNITY_CONFIGS("社区配置"),
    VIEW_LOGS("View logs"),
    PROPERTIES("属性"),
    ADD_GAME("添加游戏"),
}

/**
 * One-shot request for a game action whose dialogs live in [GameActionsHost]: "添加游戏" (the Games
 * screen's + button, the Deck's Add games) and the game actions the Deck settings pages hand over.
 * Set [pending]; the host on screen (the Games screen's, or the one the Deck shell mounts over every
 * page) opens the matching dialog and clears it. Nothing needs to navigate anywhere first.
 */
object GamesScreenRequests {
    data class Request(val action: GameMenuAction, val shortcutPath: String? = null)

    var pending by mutableStateOf<Request?>(null)
}
