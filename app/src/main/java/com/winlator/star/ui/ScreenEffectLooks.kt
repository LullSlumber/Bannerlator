package com.winlator.star.ui

import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
//  Screen Effect "外观" — one-tap presets for the in-game drawer.
//
//  A Look is nothing but a named set of values for controls that ALREADY exist
//  in the Graphics tab: the colour grade (brightness / contrast / gamma /
//  saturation), the sharpening pass, the four shader toggles, terminal
//  debanding, and — for two of them — the scaling mode. Tapping a Look moves
//  those controls; every one of them stays adjustable afterwards, and the moment
//  the user moves one the chip row falls back to "Custom".
//
//  This file is the SINGLE source of truth: both renderer paths (the OpenGL
//  EffectComposer block and the Vulkan post-chain block in XServerDrawer.kt)
//  read the same table, so a Look looks the same on either renderer.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One preset. Units match the drawer's own sliders exactly, so a Look value can
 * be assigned straight into the control it drives:
 *
 *  - [brightness] / [contrast]  raw slider units, -100..100, 0 = neutral
 *  - [gamma]                    0.5..3.0, 1.0 = neutral
 *  - [saturation]               0..200 percent, 100 = neutral
 *  - [cas]                      sharpening level 0..100; **0 means the
 *                               sharpening pass is OFF**, not "off at zero"
 *  - [scalingMode]              null = leave the user's scaling mode alone;
 *                               otherwise a scaling-mode int (see SCALING_* below)
 */
data class Look(
    val name: String,
    val brightness: Float,
    val contrast: Float,
    val gamma: Float,
    val saturation: Int,
    val cas: Int,
    val fxaa: Boolean = false,
    val crt: Boolean = false,
    val toon: Boolean = false,
    val ntsc: Boolean = false,
    val deband: Boolean = false,
    val scalingMode: Int? = null,
    /** One line shown under the chip row while this Look is selected. */
    val desc: String = ""
)

object ScreenEffectLooks {

    // Scaling-mode integers. BOTH renderer paths render their picker with the same
    // UpscalerModeButtons list (0=None 1=Linear 2=Nearest 3=SGSR 4=FSR 5=FSR(Fit)
    // 6=Sharpen 7=NIS 8=SGSR HQ) and both feed a "mode" int straight to their apply callback,
    // so one constant serves GL and Vulkan alike — no per-path resolver needed.
    // If the two pickers ever diverge, this is the single place to fork them.
    const val SCALING_NEAREST = 2
    const val SCALING_FSR = 4

    /**
     * The approved Look table. Index 0 ("Off") is the neutral state and is what a
     * fresh session matches, so the row always starts with something selected.
     */
    val LOOKS: List<Look> = listOf(
        Look("Off",              0f,   0f, 1.00f, 100,  0,
             desc = "未应用任何内容。"),
        Look("游戏清晰度",     2f,  12f, 1.00f, 108, 55,
             desc = "锐化加一点对比度 — 日常使用。"),
        Look("鲜艳",            3f,  10f, 0.98f, 145, 30, deband = true,
             desc = "颜色强力增强。开启去条带，天空不会出现条纹。"),
        Look("电影",       -4f,  22f, 1.10f,  88, 20, fxaa = true, deband = true,
             desc = "更深对比度，更冷色调，边缘平滑。"),
        Look("竞技",     16f,  18f, 0.92f,  82, 75,
             desc = "提亮阴影并重度锐化 — 先发现敌人。"),
        Look("自适应锐化", 0f,   0f, 1.00f, 100, 85,
             desc = "仅锐化，颜色不变。"),
        Look("胶片",          -2f,  16f, 1.14f,  92, 15, fxaa = true, deband = true,
             desc = "类胶片曲线，略微柔和。"),
        Look("街机",           6f,  20f, 0.95f, 170, 45, deband = true,
             desc = "鲜艳而强烈。"),
        Look("复古CRT",        6f,  14f, 1.05f, 115,  0, crt = true, ntsc = true,
             desc = "CRT显像管和模拟信号一起呈现。"),
        Look("放大锐化",    0f,   6f, 1.00f, 104, 70, scalingMode = SCALING_FSR,
             desc = "For running below panel resolution."),
        Look("像素清晰",      0f,   8f, 1.00f, 100,  0, deband = true, scalingMode = SCALING_NEAREST,
             desc = "为2D和经典游戏提供清晰像素。"),
        Look("动漫边缘",       2f,  10f, 1.00f, 125, 50, toon = true, deband = true,
             desc = "卡通轮廓加锐化。"),
    )

    /** Label of the trailing chip shown once the user has adjusted a Look's own controls. */
    const val CUSTOM_LABEL = "Custom"

    /**
     * Which Look (if any) the given live control values ARE. Used to seed the chip
     * row when the drawer is first composed, so a session that was launched with
     * non-default effects honestly shows "Custom" instead of claiming "Off".
     *
     * [cas] is the EFFECTIVE sharpening level: pass 0 when the sharpening pass is
     * switched off, whatever level its slider happens to be parked at. A Look with
     * a null [Look.scalingMode] does not care what the scaling mode is, so it
     * matches regardless.
     *
     * Returns null when nothing matches (= Custom).
     */
    fun indexOfMatch(
        brightness: Float,
        contrast: Float,
        gamma: Float,
        saturation: Float,
        cas: Int,
        fxaa: Boolean,
        crt: Boolean,
        toon: Boolean,
        ntsc: Boolean,
        deband: Boolean,
        scalingMode: Int
    ): Int? {
        val i = LOOKS.indexOfFirst { l ->
            abs(l.brightness - brightness) < 0.5f &&
            abs(l.contrast - contrast) < 0.5f &&
            abs(l.gamma - gamma) < 0.005f &&
            abs(l.saturation - saturation) < 0.5f &&
            l.cas == cas &&
            l.fxaa == fxaa && l.crt == crt && l.toon == toon && l.ntsc == ntsc &&
            l.deband == deband &&
            (l.scalingMode == null || l.scalingMode == scalingMode)
        }
        return if (i >= 0) i else null
    }
}
