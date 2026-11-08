package com.winlator.star.contents

/**
 * Layer 1 of the Smart Wrapper Manager (issue #132) — the maintained dictionary that turns a raw,
 * auto-detected env-var NAME (found by "strings"-scanning a wrapper's binaries; see
 * [WrapperManager.scanEnvKeys]) into a proper settings control (toggle / slider / dropdown / text)
 * with a human label, hint, default and value domain.
 *
 * This is OURS to grow: one [SettingDef] per common Winlator-family setting → every imported wrapper
 * that references that env var automatically gets a polished control, with ZERO cooperation from the
 * wrapper author. A detected key NOT in the table falls back to a generic [Type.TEXT] field whose
 * label is the raw env key (still usable by power users) via [defFor].
 *
 * The dialog stores each control's value in `graphicsDriverConfig` under the RAW ENV KEY, and XSDA
 * emits `KEY=value` generically at launch — so adding a row here is the ONLY step needed to support
 * a new setting end-to-end (no dialog or emission code changes).
 */
object WrapperSettingsDictionary {

    enum class Type { TOGGLE, SLIDER, DROPDOWN, TEXT }

    data class SettingDef(
        val key: String,
        val type: Type,
        val label: String,
        val hint: String = "",
        val default: String = "",
        val choices: List<String> = emptyList(),
        val min: Float = 0f,
        val max: Float = 0f,
        val step: Float = 0f,
    )

    // Seeded with the known Winlator-family vocabulary. Keys that a curated control already exposes
    // (see WrapperManager.HANDLED_ENV_KEYS) are still listed for completeness, but the "Detected
    // settings" section filters them out so nothing is shown twice.
    private val DEFS: Map<String, SettingDef> = listOf(
        // --- WRAPPER_* (integrated ICD) ---
        SettingDef("WRAPPER_VK_VERSION", Type.TEXT, "Vulkan 版本", "e.g. 1.4", default = "1.4"),
        SettingDef("WRAPPER_EMULATE_BCN", Type.DROPDOWN, "BCn 模拟",
            "0 关闭 · 1 基本 · 2 完整 · 3 自动", default = "3", choices = listOf("0", "1", "2", "3")),
        SettingDef("WRAPPER_BCN_ASTC", Type.TOGGLE, "BCn → ASTC 转码"),
        SettingDef("WRAPPER_EXTENSION_BLACKLIST", Type.TEXT, "已屏蔽的 Vulkan 扩展",
            "逗号分隔"),
        SettingDef("WRAPPER_DRIVER_ID", Type.TEXT, "强制指定驱动 ID"),
        SettingDef("WRAPPER_SAFE_CREATE_DEVICE", Type.TOGGLE, "安全模式创建设备"),
        SettingDef("WRAPPER_DEVICE_NAME", Type.TEXT, "伪装 GPU 名称"),
        SettingDef("WRAPPER_DEVICE_ID", Type.TEXT, "伪造设备 ID"),
        SettingDef("WRAPPER_VENDOR_ID", Type.TEXT, "伪造厂商 ID"),
        SettingDef("WRAPPER_VMEM_MAX_SIZE", Type.TEXT, "设备最大内存 (MB)", "数值，0 表示未设置"),
        SettingDef("WRAPPER_DISABLE_PRESENT_WAIT", Type.TOGGLE, "禁用呈现等待"),
        SettingDef("WRAPPER_MAX_IMAGE_COUNT", Type.TEXT, "交换链最大图像数", "number"),
        SettingDef("WRAPPER_ONE_BY_ONE", Type.TOGGLE, "逐个提交命令",
            "兼容性/稳定性；速度可能会较慢"),
        SettingDef("WRAPPER_REDUCE_DEPTH_FORMAT", Type.TOGGLE, "降低深度缓冲格式",
            "为部分深度格式兼容性不佳的 GPU 提供兼容性支持"),
        SettingDef("WRAPPER_DISABLE_PLACED", Type.TOGGLE, "禁用已放置内存", "兼容性开关"),
        // --- MESA_VK_* / GALLIUM_* ---
        SettingDef("MESA_VK_VERSION_OVERRIDE", Type.TEXT, "强制 Vulkan 版本 (Mesa)",
            "例如 1.3 — 覆盖报告的 Vulkan 版本"),
        SettingDef("MESA_VK_WSI_PRESENT_MODE", Type.DROPDOWN, "WSI 呈现模式",
            default = "mailbox", choices = listOf("mailbox", "fifo", "immediate")),
        SettingDef("GALLIUM_DRIVER", Type.TEXT, "Gallium 驱动", "例如 zink"),
        // --- BCn layer (leegao standalone) ---
        SettingDef("ENABLE_BCN_COMPUTE", Type.TOGGLE, "启用 BCn 计算"),
        SettingDef("BCN_COMPUTE_AUTO", Type.TOGGLE, "BCn 计算自动检测"),
        SettingDef("BCN_TRANSCODE_TO_ETC2", Type.TOGGLE, "转码 BCn → ETC2"),
        SettingDef("BCN_TRANSCODE_TO_ASTC", Type.TOGGLE, "转码 BCn → ASTC"),
        SettingDef("BCN_COMPUTE_IMAGE_VIEW", Type.TOGGLE, "存储图像路径"),
        SettingDef("BCN_LAYER_LOG_LEVEL", Type.TEXT, "BCn 日志级别", "例如 info、error"),
        SettingDef("BCN_MAX_STAGING_CACHE_MB", Type.TEXT, "BCn 暂存缓存 (MB)", "number"),
        SettingDef("BCN_QUEUE_THROTTLE_LIMIT", Type.TEXT, "BCn 队列限流", "number"),
        SettingDef("BCN_ASTC_USE_LARGE_STEPS", Type.TOGGLE, "ASTC：使用大步长",
            "Fcharan BCn 分支 — ASTC 转码更快，画质略低"),
        // --- DXVK/Mali compat + DX12 ---
        SettingDef("ENABLE_DXVK_MALI_COMPAT_LAYER", Type.TOGGLE, "启用 DXVK Mali 兼容层"),
        SettingDef("COMPAT_EMULATE_SPARSE_BINDING", Type.TOGGLE, "模拟稀疏绑定"),
        SettingDef("COMPAT_FORCE_MASKING", Type.TOGGLE, "强制掩码"),
        SettingDef("COMPAT_EMULATE_PUSH_DESCRIPTORS", Type.TOGGLE, "模拟推送描述符 (DX12)",
            "兼容层 — 对于使用推送描述符的 DX12 游戏请保持开启"),
        SettingDef("COMPAT_EMULATE_NULL_DESCRIPTORS", Type.TOGGLE, "模拟空描述符 (DX12)",
            "兼容层 — DX12 空描述符支持"),
        SettingDef("COMPAT_SPARSE_COMMIT_BUDGET", Type.TEXT, "稀疏提交预算 (MB)",
            "兼容层 — D3D12 平铺/稀疏资源的内存预算"),
        // --- Added from a strings-scan of the WinlatorMali / WinNative / leegao / Fcharan fork binaries
        //     (#132 dictionary hardening). Debug/diag/profile keys these binaries also expose are left to
        //     WrapperManager.isDebugEnvKey (hidden from settings), not curated here.
        // BCn transcode / emulation (leegao + Fcharan BCn layer)
        SettingDef("DISABLE_BCN", Type.TOGGLE, "禁用 BCn 转码",
            "完全跳过 BCn→ASTC/ETC 路径"),
        SettingDef("FORCE_BCN_EMULATION", Type.TOGGLE, "强制 BCn 模拟",
            "即使 GPU 声称原生支持，也强制模拟 BCn"),
        SettingDef("BCN_TRANSCODE_TO_ETC1", Type.TOGGLE, "转码 BCn → ETC1"),
        SettingDef("BCN_ASTC_TRY_2P", Type.TOGGLE, "ASTC：尝试双分区块",
            "质量更高，转码更慢"),
        SettingDef("BCN_ASTC_ONLY_2P", Type.TOGGLE, "ASTC：仅双分区"),
        SettingDef("ENABLE_COMPUTE_TRACKING", Type.TOGGLE, "跟踪计算资源"),
        // Wrapper-side BCn acceleration (base ICD)
        SettingDef("WRAPPER_BCN_GPU", Type.TOGGLE, "使用 GPU 加速 BCn 解码"),
        SettingDef("WRAPPER_BCN_GPU_CAP_MB", Type.TEXT, "BCn GPU 内存上限 (MB)", "数值，0 表示未设置"),
        SettingDef("WRAPPER_USE_BCN_CACHE", Type.TOGGLE, "缓存转码后的 BCn 纹理"),
        SettingDef("WRAPPER_NO_BCN_THREAD", Type.TOGGLE, "禁用 BCn 工作线程",
            "以内联方式转码，而非在后台线程中进行"),
        SettingDef("WRAPPER_DMAHEAP_CACHED", Type.TOGGLE, "缓存的 DMA 堆分配"),
        // Shader-compat family (base ICD) — workarounds for SPIR-V/driver quirks
        SettingDef("DISABLE_CLIP_DISTANCE", Type.TOGGLE, "禁用 gl_ClipDistance"),
        SettingDef("FORCE_CLIP_DISTANCE", Type.TOGGLE, "强制 gl_ClipDistance"),
        SettingDef("WRAPPER_NO_REMOVE_CLIP_DISTANCE", Type.TOGGLE, "保留着色器中的裁剪距离"),
        SettingDef("DISABLE_OPTIMIZATION_BARRIERS", Type.TOGGLE, "禁用优化屏障"),
        SettingDef("FORCE_OPTIMIZATION_BARRIERS", Type.TOGGLE, "强制优化屏障"),
        SettingDef("DISABLE_SPEC_COMPOSITE_CONSTANTS", Type.TOGGLE, "禁用 Spec 复合常量"),
        SettingDef("FORCE_SPEC_COMPOSITE_CONSTANTS", Type.TOGGLE, "强制 Spec 复合常量"),
        SettingDef("WRAPPER_NO_PATCH_OPCONSTCOMP", Type.TOGGLE, "不修补 OpConstantComposite"),
        SettingDef("DISABLE_EXTERNAL_FD", Type.TOGGLE, "禁用外部 FD 内存"),
        // Paths / misc (base ICD)
        SettingDef("WRAPPER_CACHE_PATH", Type.TEXT, "Wrapper 缓存路径", "绝对路径"),
        SettingDef("WRAPPER_RESOURCE_TYPE", Type.TEXT, "资源类型覆盖"),
        // --- Confirmed by getenv() disassembly across the WinlatorMali/WinNative/GameNative/leegao/base
        //     binaries (#132). Types are what the binary actually does with the value (atoi→INT/TOGGLE,
        //     pointer→TEXT). System/driver/debug getenv reads (HOME, PREFIX, NIR_SKIP, HWCPIPE_*, …) are
        //     filtered by WrapperManager.isDriverInternalEnvKey / isDebugEnvKey, NOT curated here.
        SettingDef("WRAPPER_BLIT", Type.TOGGLE, "强制交换链 Blit",
            "通过 Blit 方式提交呈现请求——为某些交换链提供兼容性支持"),
        SettingDef("WRAPPER_DEVICE_FAULT", Type.TOGGLE, "设备故障报告",
            "Enable VK_EXT_device_fault diagnostics on GPU hangs"),
        SettingDef("WRAPPER_EMULATE_PUSH_DESCRIPTOR", Type.TOGGLE, "模拟推送描述符",
            "ICD 端推送描述符模拟"),
        SettingDef("WRAPPER_ASTC_BLOCK", Type.TEXT, "ASTC 块大小", "数值，0 为默认值"),
        SettingDef("WRAPPER_LAYER_PATH", Type.TEXT, "Vulkan 层路径（高级）",
            "绝对路径 — 通常留空即可"),
        SettingDef("USE_IMAGE_VIEW", Type.TOGGLE, "BCn：使用存储图像视图",
            "leegao BCn — 存储图像转码路径"),
    ).associateBy { it.key }

    /** Dictionary hit for [key], else a generic TEXT field whose label is the raw env key. */
    fun defFor(key: String): SettingDef = DEFS[key] ?: SettingDef(key, Type.TEXT, key)
}
