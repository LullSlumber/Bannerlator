package com.winlator.star.ui.screens

/**
 * Plain-English reference for Wine's debug channels, used by the Log Manager's "Browse all" dialog.
 *
 * Wine ships 521 of these and they are, with few exceptions, named after the DLL or subsystem whose
 * output they switch on. Hand-writing 521 accurate descriptions is not something anyone can do
 * honestly, so this works in two tiers:
 *
 *  1. [DESCRIPTIONS] — channels worth knowing, described specifically. These are the ones a user is
 *     plausibly here to find: the tracing channels, the graphics stack, audio, input, networking.
 *  2. [categoryOf] — a family for every remaining channel, so the tail still gets a true statement
 *     ("Direct3D and graphics — output from Wine's `d3dxof` component") instead of an invented one.
 *
 * The rule for tier 1: if the specific behaviour isn't known, leave it out and let the category
 * answer. A vague-but-true line is more use than a confident-sounding guess about which component
 * logs what.
 */
object WineChannelInfo {

    const val CAT_TRACING = "错误与追踪"
    const val CAT_GRAPHICS = "Direct3D 与图形"
    const val CAT_AUDIO = "声音"
    const val CAT_VIDEO = "视频与媒体播放"
    const val CAT_UI = "窗口、菜单与控件"
    const val CAT_INPUT = "输入与设备"
    const val CAT_NET = "网络与互联网"
    const val CAT_FILES = "文件与存储"
    const val CAT_RUNTIME = "进程、线程与内存"
    const val CAT_REGISTRY = "注册表与设置"
    const val CAT_SECURITY = "安全与加密"
    const val CAT_COM = "COM、脚本与自动化"
    const val CAT_TEXT = "字体与文本"
    const val CAT_PRINT = "打印"
    const val CAT_INSTALL = "安装程序与打包"
    const val CAT_HOST = "Android 端"
    const val CAT_OTHER = "其他所有"

    /** Order the browse dialog groups them in — most useful first, not alphabetical. */
    val CATEGORY_ORDER = listOf(
        CAT_TRACING, CAT_GRAPHICS, CAT_AUDIO, CAT_VIDEO, CAT_INPUT, CAT_UI, CAT_RUNTIME,
        CAT_FILES, CAT_NET, CAT_REGISTRY, CAT_SECURITY, CAT_COM, CAT_TEXT, CAT_PRINT,
        CAT_INSTALL, CAT_HOST, CAT_OTHER,
    )

    /** Short note on what each category is for, shown once above its group. */
    fun categoryBlurb(category: String): String = when (category) {
        CAT_TRACING -> "从这里开始。这些决定了其他所有内容的输出详细程度。"
        CAT_GRAPHICS -> "图形栈 — 大多数游戏问题实际发生的地方。"
        CAT_AUDIO -> "声音输出、混音以及游戏使用的音频 API。"
        CAT_VIDEO -> "过场动画、编解码器与媒体播放。"
        CAT_INPUT -> "控制器、鼠标、键盘和其他硬件。"
        CAT_UI -> "窗口、对话框以及标准 Windows 控件。"
        CAT_RUNTIME -> "程序如何运行：进程、线程、内存、异常。"
        CAT_FILES -> "读写文件、驱动器和文件系统。"
        CAT_NET -> "任何通过网络通信的内容。"
        CAT_REGISTRY -> "Windows 注册表与配置。"
        CAT_SECURITY -> "证书、加密、凭据与权限。"
        CAT_COM -> "Windows 的组件/自动化基础设施，启动器大量使用。"
        CAT_TEXT -> "字体加载与文本渲染。"
        CAT_PRINT -> "打印机与打印任务。"
        CAT_INSTALL -> "游戏与依赖项安装程序。"
        CAT_HOST -> "Wine 与 Android 的对接层 — 显示驱动与音频后端。"
        else -> "没有明确归属的组件。大部分是偏门 Windows DLL。"
    }

    /**
     * The channels worth describing specifically. Everything absent from this map falls through to
     * the category sentence, which is the point — see the class note.
     */
    private val DESCRIPTIONS: Map<String, String> = mapOf(
        // ── Tracing. The ones that change the character of the whole log. ──────────────────
        "err" to "真实的错误。默认开启，是首先需要查看的内容。",
        "warn" to "警告 — 发生了意外但非致命的问题。默认开启。",
        "fixme" to "Wine 表示某个功能缺失或被模拟。通常是 bug 的根源。",
        "seh" to "Windows exceptions as they are thrown. Invaluable for a crash, but games throw " +
            "these constantly, so it makes logs huge and slows the game down noticeably.",
        "relay" to "Logs every call in and out of a DLL. Extremely detailed and extremely slow — " +
            "useful for a specific hang, unusable as a general setting.",
        "snoop" to "与 relay 类似，但针对 Wine 自身未构建的调用。性能开销相同。",
        "exception" to "异常展开时的异常处理。与 seh 配合使用。",
        "unwind" to "异常处理期间的栈展开。",
        "debugstr" to "Text the program itself prints with OutputDebugString — often the " +
            "developer's own messages.",
        "debug_buffer" to "传递的缓冲区内容，以十六进制转储。",

        // ── Graphics ──────────────────────────────────────────────────────────────────────
        "d3d" to "Direct3D 核心。排查图形问题的一般起点。",
        "d3d8" to "Direct3D 8 — 面向较老的游戏。",
        "d3d9" to "Direct3D 9 — 那个时代游戏最常用的 API。",
        "d3d10" to "Direct3D 10.",
        "d3d10core" to "Direct3D 10 核心层。",
        "d3d11" to "Direct3D 11 — Bannerlator 中大多数现代游戏使用的 API。",
        "d3d12" to "Direct3D 12，由 VKD3D 转换。",
        "d3d_shader" to "着色器编译。遇到画面缺失或损坏时可尝试此频道。",
        "d3d_decl" to "顶点声明 — 几何体如何描述给 GPU。",
        "d3dcompiler" to "着色器编译器本身。",
        "d3dx" to "游戏用于纹理、数学计算和特效的 D3DX 辅助库。",
        "dxgi" to "DXGI — swapchains, display modes and presentation. Good for black-screen, " +
            "fullscreen and refresh-rate problems.",
        "ddraw" to "DirectDraw — 2D 及 Direct3D 8 之前的游戏。",
        "vulkan" to "Vulkan 调用。这是 DXVK 和 VKD3D 最终产生的内容。",
        "opengl" to "OpenGL 调用。",
        "wgl" to "连接 OpenGL 与 Windows 窗口的桥梁。",
        "gl_compat" to "OpenGL 兼容性解决方案。",
        "gdi" to "GDI — 经典的 Windows 2D 绘图。菜单和启动器大量使用它。",
        "gdiplus" to "GDI+ — 较新的 2D 绘图库。",
        "d2d" to "Direct2D.",
        "dcomp" to "DirectComposition — 窗口的桌面合成。",
        "dwmapi" to "Desktop Window Manager — 合成与窗口效果。",
        "bitblt" to "位图块传输，2D 绘图的主力。",
        "bitmap" to "位图处理。",
        "palette" to "颜色调色板，用于较老的游戏。",
        "dxcore" to "DXCore — 应用如何枚举图形适配器。",
        "dxva2" to "硬件视频加速。",
        "asmshader" to "汇编着色器解析，用于较旧的着色器模型。",

        // ── Audio ─────────────────────────────────────────────────────────────────────────
        "dsound" to "DirectSound — 较老游戏常用的音频 API。",
        "dsound3d" to "DirectSound 3D 定位音频。",
        "xaudio2" to "XAudio2 — 现代游戏常用的音频 API。",
        "mmdevapi" to "现代 Windows 音频设备 API（WASAPI）。",
        "coreaudio" to "核心音频路由。",
        "winmm" to "多媒体 — 旧版声音、MIDI 和计时器。",
        "midi" to "MIDI 播放。",
        "wavemap" to "波形音频格式之间的映射。",
        "sound" to "通用声音输出。",
        "alsa" to "ALSA 后端 — 音频从 Wine 输��到 Android 端的途径之一。",
        "pulse" to "PulseAudio 后端。",
        "oss" to "OSS 音频后端。",
        "dmusic" to "DirectMusic.",
        "dmsynth" to "DirectMusic 软件合成器。",
        "msacm" to "音频压缩与解压缩。",

        // ── Video / media ─────────────────────────────────────────────────────────────────
        "quartz" to "DirectShow — 过场动画与视频播放。片头视频卡住时可尝试此频道。",
        "mfplat" to "Media Foundation — 现代视频管线。过场动画中也常见。",
        "evr" to "增强视频渲染器。",
        "devenum" to "已安装编解码器和采集设备的枚举。",
        "avifile" to "AVI 文件处理。",
        "mciavi" to "通过 MCI 接口的 AVI 播放。",
        "wmvcore" to "Windows Media Video.",
        "msvideo" to "视频压缩与解压缩。",
        "mmio" to "多媒体文件 I/O。",

        // ── Input ─────────────────────────────────────────────────────────────────────────
        "dinput" to "DirectInput — 较老游戏中的控制器和摇杆。",
        "xinput" to "XInput — 现代手柄 API。当控制器未被检测到时尝试此频道。",
        "rawinput" to "原始输入 — 直接来自设备的鼠标和键盘。射击游戏中常见。",
        "hid" to "USB HID 设备 — 大多数控制器属于此类。",
        "joycpl" to "摇杆控制面板。",
        "keyboard" to "键盘处理。",
        "cursor" to "鼠标光标。光标不可见或卡住时值得尝试。",
        "input" to "通用输入分发。",
        "usb" to "USB 设备。",
        "wintab32" to "绘图板输入。",

        // ── Windows and controls ──────────────────────────────────────────────────────────
        "win" to "窗口创建与管理。范围广泛，对窗口相关问题有用。",
        "message" to "Windows 消息循环 — 程序接收的每个事件。非常冗长。",
        "msg" to "单个窗口消息。",
        "nonclient" to "标题栏、边框和窗口框架。",
        "dialog" to "对话框。",
        "menu" to "菜单。",
        "user" to "USER32 — 核心窗口库。",
        "clipboard" to "复制与粘贴。",
        "dragdrop" to "拖放。",
        "hook" to "Windows 钩子，用于拦截事件。覆盖层和反作弊系统使用它们。",
        "theme_scroll" to "主题滚动条绘制。",
        "uxtheme" to "视觉主题与控件样式。",
        "commctrl" to "标准 Windows 控件组。",
        "commdlg" to "标准文件/打印/颜色对话框。",
        "edit" to "文本框。",
        "listbox" to "列表框。",
        "listview" to "列表视图。",
        "combo" to "组合框（下拉列表）。",
        "button" to "按钮。",
        "toolbar" to "工具栏。",
        "tooltips" to "工具提示。",
        "treeview" to "树形视图。",
        "statusbar" to "状态栏。",
        "systray" to "系统托盘 / 通知区域。",
        "shell" to "Windows shell — 文件夹、图标和文件关联。",
        "explorerframe" to "Explorer 窗口框架。",

        // ── Runtime ───────────────────────────────────────────────────────────────────────
        "process" to "进程创建与退出。适用于立即关闭的游戏。",
        "thread" to "线程创建与调度。",
        "threadpool" to "线程池。",
        "module" to "DLL 加载 — 游戏引入了哪些库。早期排查非常有用。",
        "loaddll" to "每个 DLL 加载时各占一行。开销小，通常单独使用就已足够。",
        "unloaddll" to "DLL 卸载。",
        "heap" to "内存分配。较为冗长，但排查内存损坏时应查看此频道。",
        "globalmem" to "全局内存分配。",
        "sync" to "锁、互斥量与同步。排查卡死或死锁的频道。",
        "ntdll" to "最低层的 Windows 库。范围广泛且信息量较大。",
        "kernelbase" to "核心内核 API 调用。",
        "ntoskrnl" to "内核端调用，驱动程序和反作弊系统使用。",
        "syslevel" to "旧版 16 位锁定。",
        "environ" to "进程视角下的环境变量。",
        "exec" to "程序执行。",
        "toolhelp" to "进程和模块枚举，工具和反作弊系统使用。",
        "vcruntime" to "Visual C++ 运行库。",
        "msvcrt" to "Microsoft C 运行库 — 大多数游戏链接的标准库。",
        "msvcp" to "Microsoft C++ 标准库。",
        "concrt" to "Microsoft 并发运行库。",
        "vcomp" to "OpenMP 并行处理。",
        "wow" to "32 位在 64 位上的转换。",
        "int" to "DOS 时代的中断处理。",
        "int21" to "DOS 中断 21h，用于非常古老的软件。",
        "vxd" to "Windows 9x 虚拟设备驱动程序。",

        // ── Files, registry ───────────────────────────────────────────────────────────────
        "file" to "文件打开、读写。范围广泛，排查文件缺失问题的频道。",
        "reg" to "注册表读写。适用于游戏无法识别自身设置的问题。",
        "storage" to "结构化存储文件。",
        "volume" to "驱动器和卷。",
        "mountmgr" to "盘符分配 — 与 Bannerlator 如何映射存储相关。",
        "cabinet" to "CAB 压缩包解压，安装程序使用。",
        "profile" to "INI 文件读写。老游戏以此方式保存设置。",
        "path" to "路径处理与 Windows/Linux 路径之间的转换。",
        "dosmem" to "DOS 内存仿真。",

        // ── Networking ────────────────────────────────────────────────────────────────────
        "winsock" to "套接字 — 几乎所有网络的基础。排查网络问题从此开始。",
        "wininet" to "经典 HTTP/FTP 库，启动器和更新程序使用。",
        "winhttp" to "较新的 HTTP 库。",
        "http" to "HTTP 处理。",
        "urlmon" to "URL 处理与下载。",
        "dnsapi" to "DNS 查询。",
        "iphlpapi" to "网络适配器与 IP 配置查询。",
        "netapi32" to "Windows 网络与域查询。",
        "dplay" to "DirectPlay — 较老游戏中的多人模式。",
        "dpnet" to "DirectPlay 8 网络。",
        "rpc" to "远程过程调用，用于 Windows 组件之间。",
        "mswsock" to "Winsock 扩展。",

        // ── Security ──────────────────────────────────────────────────────────────────────
        "crypt" to "加密。",
        "crypto" to "底层加密操作。",
        "bcrypt" to "现代加密 API。",
        "ncrypt" to "密钥存储与管理。",
        "schannel" to "TLS/SSL — 安全连接。启动器无法登录时尝试此频道。",
        "secur32" to "身份验证。",
        "wintrust" to "签名验证，包括防篡改检查。",
        "cred" to "存储的凭据。",
        "security" to "权限与访问检查。",
        "advapi" to "高级 Windows API：服务、注册表和安全。",

        // ── COM / scripting ───────────────────────────────────────────────────────────────
        "ole" to "COM/OLE — Windows 的组件系统。启动器大量依赖它。",
        "combase" to "COM 基础库。",
        "olemalloc" to "COM 内存分配。",
        "variant" to "COM 变体类型转换。",
        "msi" to "Windows Installer。排查 .msi 安装失败的频道。",
        "msxml" to "XML 解析。",
        "xmllite" to "轻量级 XML 解析。",
        "vbscript" to "VBScript.",
        "jscript" to "JScript/JavaScript.",
        "mshtml" to "嵌入式 Web 浏览器控件，部分启动器使用。",
        "actctx" to "Activation contexts and side-by-side assemblies — a common source of " +
            "\"the application failed to initialize\" errors.",
        "sxs" to "并行程序集解析，与 actctx 同属一类。",
        "fusion" to ".NET 程序集加载。",

        // ── Text and printing ─────────────────────────────────────────────────────────────
        "font" to "字体加载与匹配。文字缺失或错误的排查频道。",
        "dwrite" to "DirectWrite — 现代文本渲染。",
        "fontcache" to "字体缓存服务。",
        "text" to "文本绘制。",
        "string" to "字符串处理。",
        "nls" to "区域设置与代码页处理 — 与非英文文本相关。",
        "locale" to "区域设置。",
        "imm" to "输入法编辑器，用于中日韩文字输入。",
        "print" to "打印。",
        "winspool" to "打印后台处理程序。",

        // ── Host side ─────────────────────────────────────────────────────────────────────
        "x11drv" to "The X11 display driver — how Wine's windows reach Bannerlator's screen. " +
            "The channel for windowing, fullscreen and mouse-capture problems.",
        "waylanddrv" to "Wayland 显示驱动，用于实验性 Wayland 路径。",
        "xrandr" to "X11 下的显示模式与刷新率。",
        "xrender" to "X11 加速绘图。",
        "xim" to "X11 输入法。",
        "xdnd" to "X11 拖放。",
        "macdrv" to "macOS 显示驱动。Android 上未使用。",
        "winebrowser" to "在浏览器中打开链接。",
    )

    /** The specific description when there is one, otherwise an honest category-based line. */
    fun describe(channel: String): String {
        DESCRIPTIONS[channel]?.let { return it }
        val cat = categoryOf(channel)
        val tail = when (cat) {
            CAT_OTHER -> "Debug output from Wine's `$channel` component."
            else -> "${cat.replaceFirstChar { it.uppercase() }} — debug output from Wine's " +
                "`$channel` component."
        }
        return tail
    }

    /** True when [describe] has something specific to say, rather than a category fallback. */
    fun hasDetail(channel: String): Boolean = DESCRIPTIONS.containsKey(channel)

    /**
     * Family for any channel. Prefix and suffix matching, because Wine's naming is consistent
     * enough for it: everything starting "d3d" is graphics, everything starting "crypt" is
     * security, and so on. Order matters — the first match wins.
     */
    fun categoryOf(channel: String): String {
        val c = channel.lowercase()

        if (c in setOf("err", "warn", "fixme", "seh", "relay", "snoop", "trace", "exception",
                "unwind", "debugstr", "debug_buffer", "fixup", "stress", "message", "msg")) return CAT_TRACING

        if (c in setOf("x11drv", "waylanddrv", "macdrv", "xrandr", "xrender", "xim", "xdnd",
                "xvidmode", "alsa", "pulse", "oss", "winebrowser", "winemapi", "wineusb")) return CAT_HOST

        if (c.startsWith("d3d") || c.startsWith("dxg") || c.startsWith("ddraw") ||
            c.startsWith("gdi") || c.startsWith("opengl") || c.startsWith("wgl") ||
            c in setOf("vulkan", "d2d", "dcomp", "dwmapi", "bitblt", "bitmap", "palette", "dxcore",
                "dxva2", "asmshader", "gl_compat", "glu", "graphics", "icm", "image", "region",
                "clipping", "dc", "dciman", "enhmetafile", "metafile", "dxtrans", "dxdiag",
                "wincodecs", "wing", "icon", "imagelist", "cursor", "display", "psdrv", "olepicture",
                "d3drm", "dx8vb", "uianimation", "manipulation")) return CAT_GRAPHICS

        if (c.startsWith("dsound") || c.startsWith("dm") || c.startsWith("xaudio") ||
            c.startsWith("midi") || c.startsWith("mci") && c.contains("wave") ||
            c in setOf("winmm", "mmdevapi", "coreaudio", "wavemap", "sound", "msacm", "mmaux",
                "mmsys", "mmtime", "adpcm", "g711", "gsm", "speech", "sapi", "msttsengine",
                "mp3dmod", "wmadec", "avrt", "mciwave", "mcicda", "mcimidi", "audio")) return CAT_AUDIO

        if (c.startsWith("wmv") || c.startsWith("mpeg") || c.startsWith("msvid") ||
            c in setOf("quartz", "mfplat", "evr", "devenum", "avifile", "avicap", "mciavi",
                "mciqtz", "msvideo", "mmio", "media", "mediacontrol", "capture", "iccvid",
                "ir50_32", "msrle32", "msvidc32", "msmpeg2vdec", "msauddecmft", "dmo", "msdmo",
                "dsdmo", "twain", "sti", "wia", "bytecodewriter", "packager")) return CAT_VIDEO

        if (c.startsWith("dinput") || c.startsWith("xinput") || c.startsWith("wintab") ||
            c in setOf("rawinput", "hid", "joycpl", "keyboard", "input", "usb", "usbd", "winusb",
                "bluetooth", "bluetoothapis", "hotkey", "ndis", "plugplay", "setupapi", "ir",
                "scsiport", "tape", "aspi", "capi", "ctapi32", "smbios", "perception",
                "geolocator", "sensapi", "winscard", "hostname")) return CAT_INPUT

        if (c.startsWith("list") || c.startsWith("combo") || c.startsWith("tool") ||
            c.startsWith("prop") || c.startsWith("tree") ||
            c in setOf("win", "nonclient", "dialog", "menu", "menubuilder", "user", "clipboard",
                "dragdrop", "hook", "theme_scroll", "uxtheme", "commctrl", "commdlg", "edit",
                "button", "statusbar", "systray", "shell", "explorerframe", "shdocvw", "shlctrl",
                "shcore", "browseui", "appbar", "animate", "comboex", "header", "monthcal",
                "pager", "progress", "rebar", "scroll", "static", "syslink", "tab", "trackbar",
                "updown", "ipaddress", "datetime", "taskdialog", "uiribbon", "uiautomation",
                "oleacc", "nstc", "cards", "mdi", "richedit", "richedit_lists", "class", "ui",
                "ninput", "gamebar", "gameux", "gamingtcui", "mmc", "recyclebin", "pidl",
                "selector", "enumeration", "twinapi", "wpc", "htmlhelp", "hlink")) return CAT_UI

        if (c.startsWith("msvc") || c.startsWith("dbghelp") ||
            c in setOf("process", "thread", "threadpool", "module", "loaddll", "unloaddll", "heap",
                "globalmem", "virtual", "sync", "ntdll", "kernelbase", "ntoskrnl", "server",
                "syslevel", "environ", "exec", "toolhelp", "vcruntime", "concrt", "vcomp", "wow",
                "int", "int21", "int31", "vxd", "dosmem", "atom", "handle", "global", "local",
                "context", "thunk", "atlthunk", "atl", "dll", "resource", "ver", "system",
                "dbgeng", "diasymreader", "faultrep", "wer", "rstrtmgr", "apphelp", "fltlib",
                "fltmgr", "driver", "vdmdbg", "event", "eventlog", "wevtapi", "tdh", "pdh",
                "loadperf", "powermgnt", "powrprof", "clusapi", "schedsvc", "mstask", "taskschd",
                "task", "service", "wmi", "wmiutils", "wbemprox", "wbemdisp", "mgmtapi",
                "query", "data", "model", "wldp", "hvsi", "tbs", "amsi")) return CAT_RUNTIME

        if (c in setOf("file", "storage", "volume", "mountmgr", "cabinet", "profile", "path",
                "davclnt", "wofutil", "virtdisk", "sfc", "msisip", "mspatcha", "msopc",
                "wimgapi", "wnet", "mpr", "lanman", "itss", "infosoft")) return CAT_FILES

        if (c.startsWith("dp") || c.startsWith("dhcp") || c.startsWith("snmp") ||
            c.startsWith("wsnmp") || c.startsWith("ras") || c.startsWith("wlan") ||
            c in setOf("winsock", "wininet", "winhttp", "http", "urlmon", "dnsapi", "iphlpapi",
                "netapi32", "rpc", "mswsock", "netbios", "netcfgx", "netio", "netprofm", "nsi",
                "url", "jsproxy", "inetcomm", "inetcpl", "inetmib1", "wldap32", "ldap", "webservices",
                "wsdapi", "qwave", "traffic", "qmgr", "hnetcfg", "fwpuclnt", "mprapi", "rtutils",
                "tdi", "tapi", "wpcap", "wtsapi", "winsta", "winstation", "mapi", "cdosys",
                "wuapi", "connect", "sensapi2", "wsock")) return CAT_NET

        if (c in setOf("reg", "advapi", "policy")) return CAT_REGISTRY

        if (c.startsWith("crypt") || c.startsWith("acl") || c.startsWith("cred") ||
            c in setOf("bcrypt", "ncrypt", "schannel", "secur32", "wintrust", "security", "authz",
                "kerberos", "ntlm", "ksecdd", "sspicli", "msasn1", "dssenh", "pstores", "msdrm",
                "slc", "pidgen", "mssign", "ntdsapi", "activeds", "adsldp", "dsquery", "dsdmo2",
                "dsuiext", "objsel", "msident", "scrobj")) return CAT_SECURITY

        if (c.startsWith("ole") || c.startsWith("msxml") || c.startsWith("msdas") ||
            c in setOf("ole", "combase", "olemalloc", "variant", "xmllite", "vbscript", "jscript",
                "mshtml", "actctx", "sxs", "fusion", "actxprxy", "comsvcs", "msctf", "msctfmonitor",
                "msimtf", "msscript", "scrrun", "odbc", "oledb", "msado15", "xolehlp", "dhtmled",
                "ieframe", "inkobj", "inseng", "propsys", "wintypes", "winstring", "dcom",
                "typelib", "msdasql")) return CAT_COM

        if (c.startsWith("font") || c.startsWith("atm") ||
            c in setOf("dwrite", "text", "string", "nls", "locale", "imm", "t2embed", "fontsub",
                "nativefont", "mlang", "bidi", "msftedit", "atmlib")) return CAT_TEXT

        if (c.startsWith("print") || c.startsWith("spool") || c.startsWith("localsp") ||
            c in setOf("winspool", "compstui", "prntvpt", "ntprint", "winprint", "localui",
                "printui", "spoolss")) return CAT_PRINT

        if (c.startsWith("msi") || c.startsWith("appwiz") || c.startsWith("adv") ||
            c in setOf("appx", "advpack", "difxapi", "updspapi", "setupapi2", "cabinet2",
                "msidb", "msisys")) return CAT_INSTALL

        return CAT_OTHER
    }
}
