package com.winlator.star.ui

sealed class Screen(val route: String, val label: String, val iconName: String) {
    object Containers    : Screen("containers",     "容器管理",             "folder")
    object Games         : Screen("games",           "游戏",                  "shortcut")
    object Contents      : Screen("contents",       "存档与云",               "inventory_2")
    object InputControls : Screen("input_controls", "虚拟按键",         "sports_esports")
    object AdrenoTools   : Screen("adreno_tools",   "Adrenotools GPU Drivers","memory")
    object Wrappers      : Screen("wrapper_manager","管理 Wrappers",        "layers")
    object Saves         : Screen("saves",          "Saves",                  "save")
    object SaveManager   : Screen("save_manager",   "存档管理器",           "save")
    object FileManager   : Screen("file_manager",   "文件管理器",           "folder_open")
    object Settings      : Screen("settings",       "Settings",               "settings")
    object Appearance    : Screen("appearance",     "Appearance",             "palette")

    object Gog    : Screen("gog",    "GOG",          "storefront")
    object Epic   : Screen("epic",   "Epic 游戏",   "storefront")
    object Amazon : Screen("amazon", "Amazon 游戏", "storefront")
    object Xbox   : Screen("xbox",   "Xbox",         "storefront")
    object Steam  : Screen("steam",  "Steam",        "storefront")

    object ContainerDetail : Screen("container_detail?id={id}", "Container", "")

    // Couch/TV launcher shown at startup instead of the normal UI when enable_big_picture_mode is on.
    // Registered as a route (see AppNavGraph) but intentionally NOT listed in the drawer.
    object BigPicture : Screen("big_picture", "容器管理", "sports_esports")

    companion object {
        val drawerItems by lazy {
            // Screen.Wrappers stays registered as a route (the wrapper manager is now reached via the
            // ☁ cloud button in container/game settings) but is intentionally NOT listed in the drawer.
            listOf(Games, Containers, FileManager, Settings, Appearance, InputControls, Contents, Saves)
        }
        val storeItems by lazy {
            listOf(Gog, Epic, Amazon, Xbox, Steam)
        }
    }
}
