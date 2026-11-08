package com.winlator.star.ui.components

import com.winlator.star.ui.deck.XmbTapToEdit
import com.winlator.star.ui.deck.xmbDoneActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.star.ui.DeckDialogBars
import com.winlator.star.ui.DeckDialogScaled

// ─────────────────────────────────────────────────────────────────────────────
// Container glossary — a plain-language "what is all this?" compendium for the
// container editor. Newcomer-facing: short, friendly definitions of the terms a
// first-time user meets on the setup screen (wrappers, DXVK/VKD3D, Mali/Adreno,
// BCn, colours, glibc/bionic, …). Searchable. Self-contained so the shortcut
// editor (or a future setup-wizard tutorial) can reuse it verbatim.
// ─────────────────────────────────────────────────────────────────────────────

internal data class GlossaryEntry(val term: String, val definition: String)
internal data class GlossarySection(val title: String, val entries: List<GlossaryEntry>)

internal val CONTAINER_GLOSSARY: List<GlossarySection> = listOf(
    GlossarySection("图形转译（DirectX → Vulkan）", listOf(
        GlossaryEntry("DXVK",
            "将游戏的 DirectX 9/10/11 图形翻译为 Vulkan —— 这是您手机 GPU 真正使用的语言。它是默认选项，通常也是最快选项。"),
        GlossaryEntry("VKD3D",
            "与 DXVK 理念相同，但针对 DirectX 12 游戏。它需要更新版本的 Vulkan 环境，因此并非所有wrapper或 GPU 都能使用。"),
        GlossaryEntry("WineD3D",
            "一种更旧、更慢的转换器，将 DirectX 转为 OpenGL 而非 Vulkan。当游戏在 DXVK 上表现异常时，可将其作为备选。"),
        GlossaryEntry("VEGAS",
            "一种针对特定设备（着重于 Adreno）调整的替代 DXVK 风格转换器。如果某个游戏在 DXVK 上遇到问题，值得一试。"),
        GlossaryEntry("DirectX (D3D9–12)",
            "游戏所依赖的 Windows 图形 API —— D3D9/10/11 较旧，D3D12 最新。上述wrapper将其转换为 Vulkan 以供您的手机运行。"),
        GlossaryEntry("OpenGL",
            "一种较旧的跨平台图形标准。它是“渲染器”备选，也是 WineD3D 和 Zink 在不直接使用 Vulkan 时的目标。"),
        GlossaryEntry("Zink",
            "将 OpenGL 转换为 Vulkan，从而让基于 OpenGL 的游戏和工具仍能在只支持 Vulkan 的手机 GPU 上运行。"),
    )),
    GlossarySection("Windows 层", listOf(
        GlossaryEntry("Wine",
            "在 Android 上直接运行 Windows 程序，无需 Windows 本身 —— 它实时将游戏的 Windows 系统调用转换为 Android/Linux 调用。"),
        GlossaryEntry("Proton",
            "Valve 面向游戏的 Wine 定制版，带有额外补丁，可让许多游戏运行更佳。Bannerlator 内置了 Proton 构建。"),
        GlossaryEntry("Container",
            "一个为游戏提供的自包含虚拟 Windows 环境 — 拥有自己的 C: 盘、设置和已安装组件。可以将其视为每个游戏或每个配置的独立沙盒。"),
        GlossaryEntry("Wrapper（图形驱动程序）",
            "容器用于与 GPU 通信的 Vulkan 驱动层。不同的wrapper适用于不同的 GPU — 这正是 Mali 与 Adreno 的选择至关重要的地方。"),
    )),
    GlossarySection("在 ARM 上运行 x86 游戏", listOf(
        GlossaryEntry("FEXCore",
            "将游戏的 x86/x64（Intel/AMD）指令转换为 ARM，以便在您的手机处理器上运行。Bannerlator 的主要转换器（专注于 arm64ec）。"),
        GlossaryEntry("box64",
            "经典的 x86/x64 转 ARM 指令转换器 —— 可替代 FEXCore，按容器选用。"),
        GlossaryEntry("arm64ec",
            "一种构建格式，允许原生 ARM 代码和翻译后的 x86 代码高效协同运行。现代 Proton 构建已采用此格式。"),
        GlossaryEntry("FEXCore 预设",
            "通过调整 FEXCore 复制 PC 内存顺序（TSO）的保真程度，在模拟精度和速度之间做取舍。稳定性/兼容性保持完全精确（最安全）；性能模式放宽限制以换取更高 FPS（可能破坏某些游戏）；均衡模式和 Denuvo D加密模式是折中选项。从默认值开始。"),
    )),
    GlossarySection("GPU 及其绘制方式", listOf(
        GlossaryEntry("Vulkan",
            "您的手机 GPU 使用的现代低级图形标准。上述所有转换器最终都输出 Vulkan。"),
        GlossaryEntry("Mali vs Adreno",
            "两大主流手机 GPU 家族。Adreno（骁龙）在此支持最佳。Mali（许多联发科/Exynos 手机）可用，但压缩纹理需要额外支持 —— 这正是 BCn / wrapper设置的用处。"),
        GlossaryEntry("Turnip",
            "Bannerlator 为 Adreno GPU 使用的开源 Vulkan 驱动，以实现最佳性能和兼容性。"),
        GlossaryEntry("BCn 模拟",
            "PC 游戏以压缩格式存储纹理（BCn / DXT / S3TC），Adreno 可直接读取，但 Mali 无法直接处理。BCn 仿真会实时转换它们，使其正确显示。如果在 Mali 设备上关闭或不完整，某些区域会出现黑色纹理。"),
        GlossaryEntry("ASTC / ETC2",
            "Mali GPU 可原生读取的压缩纹理格式。BCn 模拟将游戏纹理转换为其中一种格式，以便 Mali 手机正常显示。"),
        GlossaryEntry("Adrenotools",
            "一种加载器，允许 Bannerlator 使用自定义 GPU 驱动（如 Turnip）而非手机内置驱动 —— 通常可在 Adreno 上获得更好的速度和兼容性。"),
        GlossaryEntry("Vulkan 版本",
            "驱动程序向 DirectX 转换器（DXVK/VKD3D）报告的 Vulkan 级别。更高的数值可以解锁某些游戏所需的功能，但会自动限制在您的驱动程序真正支持的范围内——因此选择高于驱动程序的版本不会有任何效果。"),
        GlossaryEntry("GPU 伪装（GPU 名称）",
            "向游戏报告与您实际不同的显卡型号。某些游戏在识别到特定 GPU 时会解锁更好的图形选项，或者仅能运行。保留为“设备”可报告真实的 GPU。"),
        GlossaryEntry("最大设备内存（显存上限）",
            "限制驱动程序告知游戏的显存大小。当游戏尝试使用超出手机可用内存时很有用——可修复内存不足导致的崩溃或卡顿。除非游戏出现问题，否则保持默认。"),
    )),
    GlossarySection("Turnip 与驱动调优", listOf(
        GlossaryEntry("GMEM（分块渲染）",
            "GPU 绘制每一帧的方式。GMEM（分块）渲染将屏幕分割成小块，并在超快的片上内存中绘制它们——这是高效的移动端原生方式。自动模式仅对 Adreno 710/720/722 启用（它们需要它来避免图形异常）；强制开启适用于任何 GPU；强制关闭则永不使用。其对立面是 'sysmem'。"),
        GlossaryEntry("sysmem",
            "强制 GPU 直接绘制到主内存，而不是快速的片上分块内存——与 GMEM 相反。这是最具兼容性的选项（也是默认值），但会消耗更多内存带宽。如果强制 GMEM 导致游戏出现异常，请使用此选项。"),
        GlossaryEntry("Sync同步每帧",
            "让 GPU 在开始下一帧之前完全完成并显示每一帧。开启可修复画面撕裂、闪烁或屏幕上帧异常；关闭可获得最佳性能，因为它会阻止 CPU 和 GPU 超前工作，从而可能损失一些 FPS。"),
        GlossaryEntry("并发分箱（forcecb / nocb）",
            "分块（Binning）是 GPU 对每个屏幕分块内容进行排序的步骤。'forcecb' 强制它与渲染同时运行（有助于提升性能）；'nocb' 将其关闭（可修复某些游戏的图形异常）。这些是 Turnip 专家选项——仅在特定游戏需要时才去调整。"),
        GlossaryEntry("deck_emu",
            "让 GPU 向游戏报告自身为 Steam Deck，这可以在某些游戏中解锁更好的图形设置或代码路径。仅在 Bannerlator 自带的（Banners-Turnip）驱动程序上有效——在原生 Turnip 上会被忽略。"),
        GlossaryEntry("KHR_present_wait",
            "一项 Vulkan 功能，允许游戏等待帧真正显示在屏幕上。某些 GPU 驱动程序实现有误，会导致卡顿或挂起——“禁用 KHR_present_wait” 是兼容性回退选项，可阻止驱动程序使用该功能。"),
        GlossaryEntry("BCn 模拟 / 转码为 ASTC",
            "在 Mali 和其他非 Adreno GPU 上，游戏的压缩 BCn/DXT 纹理必须进行模拟。BCn “类型” 选择解码器（GPU 计算层或软件层），缓存可重用解码后的纹理以减少卡顿，“转码为 ASTC” 将它们重新打包为 ASTC——一种移动 GPU 硬件可读取的格式——以节省内存。所有这些对 Adreno 均无影响，因为 Adreno 原生支持读取 BCn。"),
    )),
    GlossarySection("画面设置", listOf(
        GlossaryEntry("颜色：RGBA vs BGRA",
            "红蓝颜色通道的顺序。BGRA（默认）对几乎所有情况都正确 —— 仅当游戏颜色看起来互换（红色位置显示蓝色）时才切换到 RGBA。"),
        GlossaryEntry("渲染缩放 / 超采样",
            "以更高分辨率渲染游戏并缩小以获得更清晰图像（消耗性能），或以较低分辨率获得更高 FPS。"),
        GlossaryEntry("Frame generation",
            "插入额外的中间帧使运动看起来更流畅。插值总是会增加大约一帧的输入延迟，并且可能偶尔产生伪影，因此它是可选的。两个引擎都运行在 Bannerlator 自身内部，因此额外帧直接到达屏幕：Win-FG Native（内置，无需任何东西）和 LSFG Native（运行 Lossless Scaling 着色器，并且需要您自己导入的 Lossless.dll）。需要 Vulkan 渲染器。"),
        GlossaryEntry("FPS 限制器",
            "限制游戏每秒渲染的帧数。限制可降低发热和电池消耗，并能稳定不稳定的帧率。开启它只是加载限制器——实际限制值需在游戏内 FPS 菜单中实时设置。"),
        GlossaryEntry("原生渲染",
            "将游戏的图像直接发送到屏幕，而不是通过 Bannerlator 的合成器——延迟更低且速度稍快，但会禁用颜色交换（RGBA）和呈现模式控制。如果游戏显示颜色错误或无法显示，请关闭此选项。"),
        GlossaryEntry("帧生成 FPS 数值",
            "开启帧生成后，两个应用的 FPS 读数不会一致——它们在不同节点计数。读取游戏原始输出的计数器会显示干净的 2倍/3倍/4倍（看起来可观，但计入了屏幕实际不会显示的帧）。Bannerlator 的 HUD 在帧到达显示管线时计数，因此显示的是真实增益——不是完美的倍数，并且始终受屏幕刷新率限制。这两个数字都不是“屏幕上显示的帧数”；屏幕刷新率才是真正的上限。"),
        GlossaryEntry("DXVK HUD / 覆盖层",
            "用于诊断性能的屏幕数据显示（FPS、帧时间、GPU 负载、驱动信息）。仅提供信息 —— 不会改变游戏运行。"),
        GlossaryEntry("呈现模式",
            "将完成的帧如何交付给屏幕的方式——在流畅度、输入延迟和电量之间权衡。FIFO 是安全的默认选项；Mailbox 最适合帧生成；Immediate 追求最低延迟，但可能出现画面撕裂。"),
        GlossaryEntry("FIFO（呈现模式）",
            "默认的“vsync垂直同步开启”：每帧按顺序排队，在下次屏幕刷新时显示。流畅、无撕裂且最省电——但它会让游戏等待显示，这可能会制约帧生成。"),
        GlossaryEntry("Mailbox（呈现模式）",
            "“Fast vsync快速垂直同步”：屏幕总是取用最新完成的帧，游戏无需等待。与 FIFO 一样无撕裂，但延迟更低——并且是帧生成的最佳选择，因为额外生成的帧能真正通过。耗电略多。"),
        GlossaryEntry("Immediate（呈现模式）",
            "“Vsync垂直同步关闭”：帧准备好后立即送往屏幕。输入延迟最低，但可能会出现画面撕裂（同时看到两帧的部分画面）。在手机高刷屏上通常不太值得。"),
        GlossaryEntry("SurfaceFlinger（渲染器）",
            "一种实验性渲染器，将帧直接交给 Android 显示系统。有时更快，但可能在某些设备上表现异常（甚至重启）—— Vulkan 是安全的默认选项。"),
        GlossaryEntry("Fast OpenGL",
            "通过 Vulkan 将 OpenGL 游戏的帧直接发送到屏幕，与 DXVK 游戏所走的路径相同，而不是先复制每一帧再发回。在 X11 上快数倍，并且通过 WineD3D 也能加速 DirectDraw 和较旧的 Direct3D 游戏。在 Wayland 下始终开启；在 X11 上需要 Turnip 驱动程序，并可与任何 Wine 层配合使用（Wine 11 arm64ec 层通过 EGL，其他所有层（包括 x86-64 层）通过 GLX）。默认开启 — 对于出现异常的游戏，请将其关闭。它不会改变游戏速度：与帧率绑定的非常老的游戏应使用 FPS 限制器。"),
        GlossaryEntry("Show OpenGL FPS",
            "Mesa 的每秒帧数计数器，位于 OpenGL 游戏的左上角（GALLIUM_HUD=fps）。适用于 X11 和 Wayland；DXVK 游戏不显示它。默认关闭。"),
        GlossaryEntry("OpenGL vsync off",
            "垂直同步关闭：允许 OpenGL 游戏以高于屏幕刷新率的帧率运行，而不是等待每次刷新（vblank_mode=0）。可能会出现画面撕裂；与帧率绑定的非常老的游戏可能会运行过快，因此请对它们使用 FPS 限制器。默认关闭。"),
        GlossaryEntry("着色器卡顿与缓存",
            "首次游玩时，游戏会编译着色器，可能导致短暂卡顿。DXVK 的着色器（状态）缓存会保存它们，以便后续运行流畅。"),
    )),
    GlossarySection("底层原理", listOf(
        GlossaryEntry("glibc vs bionic",
            "两种不同的 C 库。Android 使用 'bionic'，桌面 Linux 使用 'glibc'。Bannerlator 的 Wine 和驱动程序是为 bionic 构建的 — 无论看起来多好，glibc 构建都无法加载。"),
        GlossaryEntry("Environment variables",
            "高级每个容器的开关（例如 DXVK_FRAME_RATE=60 或 BCN_TRANSCODE_TO_ASTC=1）。大多数人从不需要它们，但它们允许高级用户对游戏进行微调。"),
        GlossaryEntry("Sync同步（esync / ntsync / fsync / wineserver）",
            "游戏的线程如何相互等待（'等到这个加载完成'、'帧准备好时发出信号'）。Wine 有几种处理方式；较快的那些意味着更少的卡顿和 CPU 开销。在容器的常规选项卡（兼容层下）或按游戏选择一种 — 除非您另行选择，否则游戏遵循其容器。层无法执行的选项会置灰。"),
        GlossaryEntry("esync",
            "默认同步模式。Wine 使用 Linux 事件文件处理大多数等待，而不是每次都询问其后台服务器。快速且经过充分测试（WINEESYNC=1）。"),
        GlossaryEntry("ntsync",
            "复制 Windows 自身做法的同步模式 — 最准确，而且快速。使用 GameNative 的用户空间 ntsync，因此不需要内核支持。新功能：仅适用于 v9 Proton 11 层；如果无法启动，游戏会自动回退到 esync（WINENTSYNC=1）。"),
        GlossaryEntry("fsync",
            "一种更快的同步模式，用于 Steam Deck 等 Linux PC。它依赖于 Android 对应用阻止的 Linux 功能，因此在此处始终置灰，WINEFSYNC 不起作用。"),
        GlossaryEntry("wineserver (Sync)",
            "没有捷径的同步模式：每次等待都通过 Wine 的后台服务器。最慢且 CPU 开销最大，但兼容性最好 — 当游戏在其他模式下挂起、卡顿或出现异常时使用它（WINEESYNC=0）。"),
        GlossaryEntry("DLL 覆盖",
            "告知 Wine 使用游戏自带的系统文件副本（原生）还是 Wine 内置版本。这是对自带 DLL 的游戏的常见修复方法。"),
        GlossaryEntry("Components",
            "某些游戏需要的额外 Windows 运行库（Visual C++ 可再发行组件、.NET、DirectX 等）。如果游戏提示缺少某个组件，请从容器的组件列表中安装它们。"),
    )),
    GlossarySection("控制与音频", listOf(
        GlossaryEntry("XInput vs DInput",
            "Windows 游戏读取控制器的两种方式。XInput 是现代 Xbox 风格标准；DInput 是较旧的。根据游戏期望选择匹配。"),
        GlossaryEntry("Exclusive input独占键鼠输入",
            "将您的控制器完全交给一个游戏，避免 Android 与游戏争抢 —— 当输入感觉重复或被忽略时很有帮助。"),
        GlossaryEntry("MIDI SoundFont",
            "用于播放游戏 MIDI 音乐的音色库。仅对使用 MIDI 的老游戏有意义 —— 如果游戏音乐无声，请选择一个 SoundFont。"),
        GlossaryEntry("陀螺仪（运动瞄准）",
            "通过倾斜手机利用运动传感器进行瞄准。模式设置倾斜速度还是倾斜角度进行瞄准；激活器是开启它的按钮；目标是指它移动的对象（右摇杆、左摇杆或鼠标）；灵敏度设置倾斜转动的幅度。"),
        GlossaryEntry("音频驱动（ALSA / PulseAudio）",
            "游戏声音如何到达 Android。ALSA 是简单、低开销的默认选项；PulseAudio 是某些游戏或语音功能需要的更完整的音频服务器。如果游戏没有声音或出现爆音，请切换到 PulseAudio。"),
    )),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerGlossarySheet(onDismiss: () -> Unit, initialQuery: String = "") {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    // A per-field help icon opens the sheet pre-filtered to that term (initialQuery); the general
    // "这些是什么?" button opens it unfiltered (initialQuery == "").
    var query by remember { mutableStateOf(initialQuery) }

    // Flatten + filter. A section is shown only if it has any matching entries.
    val q = query.trim()
    val sections = remember(q) {
        if (q.isEmpty()) CONTAINER_GLOSSARY
        else CONTAINER_GLOSSARY.mapNotNull { section ->
            val hits = section.entries.filter {
                it.term.contains(q, ignoreCase = true) || it.definition.contains(q, ignoreCase = true)
            }
            if (hits.isEmpty()) null else section.copy(entries = hits)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        DeckDialogBars()
        DeckDialogScaled {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "这些是什么?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "设置选项的通俗语言解释。点击术语可阅读其作用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                XmbTapToEdit(Modifier.fillMaxWidth()) { fieldModifier, stopEditing ->
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索术语") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = fieldModifier,
                        keyboardActions = xmbDoneActions(stopEditing),
                    )
                }
                Spacer(Modifier.padding(top = 8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sections.forEach { section ->
                        item(key = "sec:${section.title}") {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(section.entries, key = { "${section.title}:${it.term}" }) { entry ->
                            Surface(
                                color = cs.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(entry.term, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        entry.definition,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = cs.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (sections.isEmpty()) {
                        item {
                            Text(
                                "No terms match \"$q\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                    item { Spacer(Modifier.padding(bottom = 16.dp)) }
                }
            }
        }
    }
}
