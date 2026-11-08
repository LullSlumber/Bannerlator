package com.winlator.star.ui.screens

import android.content.Context
import android.os.Environment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.winlator.star.R
import com.winlator.star.SettingsFragment
import com.winlator.star.box64.Box64Preset
import com.winlator.star.box64.Box64PresetManager
import com.winlator.star.container.Container
import com.winlator.star.container.Shortcut
import com.winlator.star.contents.ContentProfile
import com.winlator.star.contents.ContentsManager
import com.winlator.star.core.EnvVars
import com.winlator.star.core.FileUtils
import com.winlator.star.core.PresetOverrides
import com.winlator.star.core.PresetScope
import com.winlator.star.core.StringUtils
import com.winlator.star.core.WineInfo
import com.winlator.star.core.WineUtils
import com.winlator.star.fexcore.FEXCorePreset
import com.winlator.star.fexcore.FEXCorePresetManager
import com.winlator.star.inputcontrols.ControlsProfile
import com.winlator.star.inputcontrols.InputControlsManager
import com.winlator.star.reshade.ReshadeCatalog
import com.winlator.star.reshade.ReshadeCatalogEntry
import com.winlator.star.reshade.ReshadeDownloader
import com.winlator.star.reshade.ReshadeLoadout
import com.winlator.star.reshade.ReshadeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.util.Locale

// ── Advanced (ScAdvancedTab) ────────────────────────────────────────────────────────────────────────

/** The async facts ScAdvancedTab's host loads (version lists, presets, controls profiles). */
private class P2AdvData {
    var loaded by mutableStateOf(false)
    var arm64ec by mutableStateOf(false)
    var box64Versions by mutableStateOf<List<String>>(emptyList())
    var fexVersions by mutableStateOf<List<String>>(emptyList())
    var box64Presets by mutableStateOf<List<Box64Preset>>(emptyList())
    var fexPresets by mutableStateOf<List<FEXCorePreset>>(emptyList())
    var profiles by mutableStateOf<List<ControlsProfile>>(emptyList())
}

private fun p2LoadAdv(xmb: XmbScope, c: Container, d: P2AdvData) {
    val ctx = xmb.context
    val res = ctx.resources
    xmb.scope.launch {
        withContext(Dispatchers.IO) {
            val cm = ContentsManager(ctx)
            cm.syncContents()
            val arm = runCatching { WineInfo.fromIdentifier(ctx, cm, c.wineVersion).isArm64EC() }.getOrDefault(false)
            val b64Type = if (arm) ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64 else ContentProfile.ContentType.CONTENT_TYPE_BOX64
            val b64 = (if (arm) res.getStringArray(R.array.wowbox64_version_entries) else res.getStringArray(R.array.box64_version_entries)).toMutableList()
            for (p in cm.getProfiles(b64Type) ?: emptyList<ContentProfile>()) {
                val n = ContentsManager.getEntryName(p)
                b64.add(n.substring(n.indexOf('-') + 1))
            }
            val fex = res.getStringArray(R.array.fexcore_version_entries).toMutableList()
            for (p in cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE) ?: emptyList<ContentProfile>()) {
                val n = ContentsManager.getEntryName(p)
                fex.add(n.substring(n.indexOf('-') + 1))
            }
            val b64Presets: List<Box64Preset> = runCatching { Box64PresetManager.getPresets("box64", ctx).toList() }.getOrDefault(emptyList())
            val fexPresets: List<FEXCorePreset> = runCatching { FEXCorePresetManager.getPresets(ctx).toList() }.getOrDefault(emptyList())
            val profiles: List<ControlsProfile> = runCatching { InputControlsManager(ctx).getProfiles(true).toList() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                d.arm64ec = arm
                d.box64Versions = b64.distinct()
                d.fexVersions = fex.distinct()
                d.box64Presets = b64Presets
                d.fexPresets = fexPresets
                d.profiles = profiles
                d.loaded = true
            }
        }
    }
}

internal fun xmbAdvancedMenu(xmb: XmbScope, shortcut: Shortcut): XmbMenu {
    val ctx = xmb.context
    val res = ctx.resources
    val c = shortcut.container
    val d = P2AdvData()
    p2LoadAdv(xmb, c, d)
    val reshade = P2Reshade(xmb, shortcut)
    val startupEntries = res.getStringArray(R.array.startup_selection_entries).toList()
    val sharpEntries = res.getStringArray(R.array.vkbasalt_sharpness_entries).toList()

    return XmbMenu("Advanced", Icons.Filled.Tune) body@{
        val rows = mutableListOf<XmbRow>()
        if (!d.loaded) {
            rows += XmbRow.Info("loading", "Loading versions and presets…", Icons.Filled.Info)
            return@body rows
        }
        val arm = d.arm64ec
        val emu = if (arm) "WOWBox64" else "Box64"

        // Box64 / WOWBox64
        rows += XmbRow.Header("hBox64", emu)
        val b64Stored = shortcut.p2Ex("box64Version", c.getBox64Version())
        val b64 = b64Stored.ifEmpty { d.box64Versions.firstOrNull() ?: "" }
        val b64Opts = if (b64.isNotEmpty() && b64 !in d.box64Versions) d.box64Versions + b64 else d.box64Versions
        if (b64Opts.isNotEmpty()) {
            rows += XmbRow.Choice("b64Version", "$emu version", Icons.Filled.Memory, b64Opts, b64) { v ->
                p2Put(xmb, shortcut, "box64Version", v)
            }
        }
        rows += XmbRow.Link("b64Download", "Download $emu versions", Icons.Filled.CloudDownload) {
            p2ContentDownloadMenu(xmb, "$emu downloads",
                if (arm) ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64 else ContentProfile.ContentType.CONTENT_TYPE_BOX64) { p2LoadAdv(xmb, c, d) }
        }
        p2PresetRows(xmb, shortcut, d, PresetKind.BOX64, emu, rows)

        // FEXCore (arm64ec only)
        if (arm) {
            rows += XmbRow.Header("hFex", "FEXCore")
            val fex = shortcut.p2Ex("fexcoreVersion", c.getFEXCoreVersion())
            val fexOpts = if (fex.isNotEmpty() && fex !in d.fexVersions) d.fexVersions + fex else d.fexVersions
            if (fexOpts.isNotEmpty()) {
                rows += XmbRow.Choice("fexVersion", ctx.getString(R.string.fexcore_version), Icons.Filled.Memory, fexOpts,
                    if (fex.isEmpty()) fexOpts.first() else fex) { v -> p2Put(xmb, shortcut, "fexcoreVersion", v) }
            }
            rows += XmbRow.Link("fexDownload", "下载FEXCore版本", Icons.Filled.CloudDownload) {
                p2ContentDownloadMenu(xmb, "FEXCore downloads", ContentProfile.ContentType.CONTENT_TYPE_FEXCORE) { p2LoadAdv(xmb, c, d) }
            }
            p2PresetRows(xmb, shortcut, d, PresetKind.FEXCORE, "FEXCore", rows)
        }

        // Controls profile / startup / CPU
        rows += XmbRow.Header("hSystem", "System")
        val profileLabels = p2Unique(listOf(ctx.getString(R.string.none)) + d.profiles.map { it.getName() ?: "Profile ${it.id}" })
        val cpId = shortcut.p2Ex("controlsProfile", "0").toIntOrNull() ?: 0
        val cpIdx = if (cpId == 0) 0 else d.profiles.indexOfFirst { it.id == cpId }.let { if (it >= 0) it + 1 else 0 }
        rows += XmbRow.Choice("controlsProfile", "控制配置", Icons.Filled.Gamepad, profileLabels, profileLabels[cpIdx]) { v ->
            val idx = profileLabels.indexOf(v).coerceAtLeast(0)
            val id = if (idx == 0) 0 else d.profiles.getOrNull(idx - 1)?.id ?: 0
            p2Put(xmb, shortcut, "controlsProfile", if (id > 0) id.toString() else null)
        }
        if (startupEntries.isNotEmpty()) {
            val stIdx = (shortcut.p2Ex("startupSelection", c.getStartupSelection().toString()).toIntOrNull() ?: 0)
                .coerceIn(0, startupEntries.lastIndex)
            rows += XmbRow.Choice("startup", ctx.getString(R.string.startup_selection), Icons.Filled.PowerSettingsNew,
                startupEntries, startupEntries[stIdx]) { v ->
                p2Put(xmb, shortcut, "startupSelection", startupEntries.indexOf(v).coerceAtLeast(0).toString())
            }
            if (stIdx == Container.STARTUP_SELECTION_CUSTOM.toInt()) {
                val on = WineUtils.parseStartupServicesCsv(shortcut.p2Ex("startupServices", c.startupServices)).size
                rows += XmbRow.Link("services", "自定义服务", Icons.Filled.Checklist, value = "$on on",
                    subtitle = "选择启动哪些Wine服务") { p2ServicesMenu(xmb, shortcut) }
            }
        }
        val cores = Runtime.getRuntime().availableProcessors()
        val checkedCores = p2CpuChecked(shortcut, cores).size
        rows += XmbRow.Link("cpu", ctx.getString(R.string.processor_affinity), Icons.Filled.Memory,
            value = "$checkedCores of $cores cores", subtitle = "游戏可运行的CPU核心") { p2CpuMenu(xmb, shortcut) }

        // Sharpness (VKBasalt)
        rows += XmbRow.Header("hSharpness", "锐化度 (VKBasalt)")
        if (sharpEntries.isNotEmpty()) {
            val fxStored = shortcut.p2Ex("sharpnessEffect", "None")
            rows += XmbRow.Choice("sharpEffect", "滤镜效果", Icons.Filled.Image, sharpEntries,
                sharpEntries.firstOrNull { it == fxStored } ?: sharpEntries.first()) { v -> p2Put(xmb, shortcut, "sharpnessEffect", v) }
        }
        val level = (shortcut.p2Ex("sharpnessLevel", "100").toIntOrNull() ?: 100).coerceIn(0, 100)
        val denoise = (shortcut.p2Ex("sharpnessDenoise", "100").toIntOrNull() ?: 100).coerceIn(0, 100)
        rows += XmbRow.Slider("sharpLevel", "Level", Icons.Filled.Tune, level.toFloat(), 0f, 100f, 1f,
            format = { "${it.toInt()}%" }) { v -> p2Put(xmb, shortcut, "sharpnessLevel", v.toInt().toString()) }
        rows += XmbRow.Slider("sharpDenoise", "级别", Icons.Filled.Tune, denoise.toFloat(), 0f, 100f, 1f,
            format = { "${it.toInt()}%" }) { v -> p2Put(xmb, shortcut, "sharpnessDenoise", v.toInt().toString()) }

        // ReShade
        rows += XmbRow.Header("hReshade", "ReShade")
        val count = runCatching {
            ReshadeLoadout.parse(
                shortcut.p2Ex("reshadeLoadout", c.getReshadeLoadout()).ifEmpty { null },
                shortcut.p2Ex("reshadeEffect", c.getReshadeEffect()),
            ).size
        }.getOrDefault(0)
        rows += XmbRow.Link("reshade", "ReShade 滤镜配置", Icons.Filled.Layers,
            value = if (count == 0) "None" else "$count effect${if (count == 1) "" else "s"}",
            subtitle = if (reshade.supported()) "效果、模式和参数" else "仅适用于DXVK/VKD3D（Vulkan）游戏") { reshade.loadoutMenu() }
        rows
    }
}

// ── Box64 / FEXCore presets ─────────────────────────────────────────────────────────────────────────

private fun p2PresetRows(xmb: XmbScope, s: Shortcut, d: P2AdvData, kind: PresetKind, emu: String, rows: MutableList<XmbRow>) {
    val ctx = xmb.context
    val c = s.container
    val isFex = kind == PresetKind.FEXCORE
    val ids: List<String> = if (isFex) d.fexPresets.map { it.id } else d.box64Presets.map { it.id }
    val names: List<String> = p2Unique(if (isFex) d.fexPresets.map { it.name ?: it.id } else d.box64Presets.map { it.name ?: it.id })
    if (ids.isEmpty()) return
    val key = if (isFex) "fexcorePreset" else "box64Preset"
    val stored = if (isFex) s.p2Ex(key, c.getFEXCorePreset()) else s.p2Ex(key, c.getBox64Preset())
    val idx = ids.indexOf(stored).coerceAtLeast(0)
    val selId = ids[idx]
    val customised = runCatching { PresetOverrides.isCustomised(ctx, isFex, selId, PresetScope.SHORTCUT, c, s) }.getOrDefault(false)
    val label = if (isFex) ctx.getString(R.string.fexcore_preset) else "$emu preset"
    rows += XmbRow.Choice(if (isFex) "fexPreset" else "b64Preset", label, Icons.Filled.Tune, names, names[idx],
        subtitle = if (customised) "已为此游戏自定义" else null) { v ->
        val i = names.indexOf(v)
        if (i >= 0) p2Put(xmb, s, key, ids[i])
    }
    rows += XmbRow.Link(if (isFex) "fexPresetEdit" else "b64PresetEdit", "编辑此游戏的预设", Icons.Filled.Edit,
        value = if (customised) "Customised" else null, subtitle = "仅此游戏更改") {
        p2PresetValuesMenu(xmb, s, kind, selId, names[idx])
    }
    rows += XmbRow.Link(if (isFex) "fexPresetManage" else "b64PresetManage", "Manage $emu presets", Icons.Filled.ListAlt,
        subtitle = "新建、复制、删除、导出、导入") {
        p2PresetManageMenu(xmb, s, d, kind)
    }
}

/** One row of <prefix>_env_vars.json (PresetEditDialog's VarSpec). kind: 0 toggle, 1 dropdown, 2 text. */
private class P2VarSpec(val name: String, val values: List<String>, val kind: Int, val defaultValue: String)

private fun p2LoadSpecs(ctx: Context, prefix: String): List<P2VarSpec> = try {
    val data = JSONArray(FileUtils.readString(ctx, prefix + "_env_vars.json"))
    (0 until data.length()).map { i ->
        val item = data.getJSONObject(i)
        val values = item.optJSONArray("values")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
        val toggle = item.optBoolean("toggleSwitch", false) || item.optBoolean("toggleswitch", false) ||
            item.optString("toggleSwitch") == "true" || item.optString("toggleswitch") == "true"
        val text = item.optBoolean("editText", false) || item.optString("editText") == "true"
        P2VarSpec(
            item.getString("name"), values,
            when { toggle -> 0; text -> 2; else -> 1 },
            item.optString("defaultValue", values.firstOrNull() ?: ""),
        )
    }.distinctBy { it.name }
} catch (_: Throwable) {
    emptyList()
}

/**
 * PresetEditDialog at SHORTCUT scope, applied as you go: every change stores this game's own copy
 * of the preset's values (PresetOverrides.writeLocal), or drops it when the values match what the
 * game would inherit (clearLocal) — the same comparison the dialog's Save makes.
 */
private fun p2PresetValuesMenu(xmb: XmbScope, s: Shortcut, kind: PresetKind, presetId: String, presetName: String): XmbMenu {
    val ctx = xmb.context
    val c = s.container
    val isFex = kind == PresetKind.FEXCORE
    val specs = p2LoadSpecs(ctx, kind.prefix)
    val inherited: EnvVars? = runCatching { PresetOverrides.inheritedBy(ctx, isFex, presetId, PresetScope.SHORTCUT, c, s) }.getOrNull()
    fun inheritedSeed(spec: P2VarSpec): String =
        inherited?.takeIf { it.has(spec.name) }?.get(spec.name) ?: spec.defaultValue
    val helpCache = HashMap<String, String?>()
    fun help(name: String): String? {
        if (helpCache.containsKey(name)) return helpCache[name]
        val h = StringUtils.getString(ctx, kind.prefix + "_env_var_help__" + name.replace(kind.varPrefix, "").lowercase(Locale.ENGLISH))
        helpCache[name] = h
        return h
    }
    fun current(): Map<String, String> {
        val cur = runCatching { PresetOverrides.localOf(ctx, isFex, presetId, PresetScope.SHORTCUT, c, s) }.getOrNull() ?: inherited
        return specs.associate { spec -> spec.name to (cur?.takeIf { it.has(spec.name) }?.get(spec.name) ?: spec.defaultValue) }
    }
    fun write(name: String, value: String) {
        val vals = current().toMutableMap()
        vals[name] = value
        val unchanged = specs.all { spec -> (vals[spec.name] ?: spec.defaultValue) == inheritedSeed(spec) }
        if (unchanged) PresetOverrides.clearLocal(isFex, PresetScope.SHORTCUT, c, s)
        else {
            val env = EnvVars()
            specs.forEach { spec -> env.put(spec.name, vals[spec.name] ?: spec.defaultValue) }
            PresetOverrides.writeLocal(isFex, presetId, PresetScope.SHORTCUT, c, s, env)
        }
        xmb.saved()
        xmb.refresh()
    }

    return XmbMenu(presetName, Icons.Filled.Edit) {
        val rows = mutableListOf<XmbRow>()
        val customised = runCatching { PresetOverrides.isCustomised(ctx, isFex, presetId, PresetScope.SHORTCUT, c, s) }.getOrDefault(false)
        rows += XmbRow.Info("scope",
            if (customised) "已为此游戏自定义。重置后它将再次遵循其容器。"
            else "仅为此游戏保存 — 没有其他游戏或容器更改。", Icons.Filled.Info)
        if (specs.isEmpty()) rows += XmbRow.Info("noSpecs", "This preset has no editable variables", Icons.Filled.Info)
        val vals = current()
        specs.forEach { spec ->
            val v = vals[spec.name] ?: spec.defaultValue
            when (spec.kind) {
                0 -> rows += XmbRow.Toggle("v:${spec.name}", spec.name, Icons.Filled.Tune, v == "1", subtitle = help(spec.name)) { on ->
                    write(spec.name, if (on) "1" else "0")
                }
                2 -> rows += XmbRow.Text("v:${spec.name}", spec.name, Icons.Filled.Tune, v, subtitle = help(spec.name), numeric = true) { nv ->
                    write(spec.name, nv)
                }
                else -> {
                    val opts = p2Unique(if (v.isNotEmpty() && v !in spec.values) spec.values + v else spec.values)
                    if (opts.isEmpty()) {
                        rows += XmbRow.Text("v:${spec.name}", spec.name, Icons.Filled.Tune, v, subtitle = help(spec.name)) { nv -> write(spec.name, nv) }
                    } else {
                        rows += XmbRow.Choice("v:${spec.name}", spec.name, Icons.Filled.Tune, opts, v, subtitle = help(spec.name)) { nv ->
                            write(spec.name, nv)
                        }
                    }
                }
            }
        }
        if (customised) {
            rows += XmbRow.Action("reset", "重置", Icons.Filled.RestartAlt, subtitle = "Follow the container's values again", danger = true) {
                PresetOverrides.clearLocal(isFex, PresetScope.SHORTCUT, c, s)
                xmb.saved()
                xmb.refresh()
            }
        }
        rows
    }
}

/** PresetEditorRow's list actions (they act on the shared preset list, like everywhere else). */
private fun p2PresetManageMenu(xmb: XmbScope, s: Shortcut, d: P2AdvData, kind: PresetKind): XmbMenu {
    val ctx = xmb.context
    val c = s.container
    val isFex = kind == PresetKind.FEXCORE
    val prefix = kind.prefix
    val key = if (isFex) "fexcorePreset" else "box64Preset"
    fun ids(): List<String> = if (isFex) d.fexPresets.map { it.id } else d.box64Presets.map { it.id }
    fun selectedId(): String {
        val stored = if (isFex) s.p2Ex(key, c.getFEXCorePreset()) else s.p2Ex(key, c.getBox64Preset())
        val all = ids()
        return all.getOrNull(all.indexOf(stored).coerceAtLeast(0)) ?: stored
    }
    fun reloadLists() {
        d.box64Presets = runCatching { Box64PresetManager.getPresets("box64", ctx).toList() }.getOrDefault(d.box64Presets)
        d.fexPresets = runCatching { FEXCorePresetManager.getPresets(ctx).toList() }.getOrDefault(d.fexPresets)
        xmb.refresh()
    }
    return XmbMenu("${if (isFex) "FEXCore" else if (d.arm64ec) "WOWBox64" else "Box64"} presets", Icons.Filled.ListAlt) {
        val rows = mutableListOf<XmbRow>()
        val sel = selectedId()
        val custom = sel.startsWith(if (isFex) FEXCorePreset.CUSTOM else Box64Preset.CUSTOM)
        rows += XmbRow.Text("new", "新预设", Icons.Filled.Add, "", subtitle = "默认值；成为此游戏的预设",
            placeholder = "预设名称") { name ->
            val clean = name.trim().replace(Regex("[,|]+"), "")
            if (clean.isEmpty()) xmb.toast("Enter a name")
            else {
                val env = EnvVars()
                p2LoadSpecs(ctx, prefix).forEach { env.put(it.name, it.defaultValue) }
                if (isFex) FEXCorePresetManager.editPreset(ctx, null, clean, env)
                else Box64PresetManager.editPreset(prefix, ctx, null, clean, env)
                reloadLists()
                val newId = ids().lastOrNull()
                if (newId != null) p2Put(xmb, s, key, newId)
                xmb.toast("Created “$clean”")
            }
        }
        rows += XmbRow.Action("duplicate", "复制所选预设", Icons.Filled.ContentCopy) {
            xmb.confirm(XmbConfirm("Duplicate preset", ctx.getString(R.string.do_you_want_to_duplicate_this_preset), "复制")) {
                if (isFex) FEXCorePresetManager.duplicatePreset(ctx, sel)
                else Box64PresetManager.duplicatePreset(prefix, ctx, sel)
                reloadLists()
            }
        }
        rows += XmbRow.Action("delete", "删除所选预设", Icons.Filled.Delete, danger = true,
            disabledReason = if (custom) null else ctx.getString(R.string.you_cannot_remove_this_preset)) {
            xmb.confirm(XmbConfirm("Delete preset", ctx.getString(R.string.do_you_want_to_remove_this_preset), "删除", danger = true)) {
                if (isFex) FEXCorePresetManager.removePreset(ctx, sel)
                else Box64PresetManager.removePreset(prefix, ctx, sel)
                reloadLists()
                // The game pointed at the removed preset: follow the list's first entry, as the editor shows.
                ids().firstOrNull()?.let { p2Put(xmb, s, key, it) }
            }
        }
        rows += XmbRow.Action("export", "导出所选预设", Icons.Filled.FileUpload,
            disabledReason = if (custom) null else "Cannot export this preset") {
            if (isFex) FEXCorePresetManager.exportPreset(ctx, sel)
            else Box64PresetManager.exportPreset(prefix, ctx, sel)
        }
        rows += XmbRow.Link("import", "导入预设", Icons.Filled.FileDownload, subtitle = "从.wbp文件") {
            val ext = Environment.getExternalStorageDirectory()
            val presetsDir = File(SettingsFragment.DEFAULT_WINLATOR_PATH, "预设")
            val roots = listOf(
                "预设" to presetsDir,
                "Download" to File(ext, "Download"),
                "Internal storage" to ext,
            ).filter { it.second.isDirectory }
            val start = if (presetsDir.isDirectory) presetsDir else ext
            xmbFileBrowserMenu(xmb, "导入预设", start, roots, { true }) { f ->
                val ok = runCatching {
                    FileInputStream(f).use { input ->
                        if (isFex) FEXCorePresetManager.importPreset(ctx, input)
                        else Box64PresetManager.importPreset(prefix, ctx, input)
                    }
                }.isSuccess
                // The browser has already closed its columns: we're back on this one.
                reloadLists()
                xmb.toast(if (ok) "Preset imported" else "Couldn't import that file")
            }
        }
        rows
    }
}

// ── Startup services / CPU cores ────────────────────────────────────────────────────────────────────

/** StartupServicesToggleList: the Custom selection's per-service enabled set ("startupServices" CSV). */
private fun p2ServicesMenu(xmb: XmbScope, s: Shortcut): XmbMenu = XmbMenu("自定义服务", Icons.Filled.Checklist) {
    val c = s.container
    val enabled: Set<String> = WineUtils.parseStartupServicesCsv(s.p2Ex("startupServices", c.startupServices)).toSet()
    val rows = mutableListOf<XmbRow>()
    rows += XmbRow.Info("note", "Custom starts with every service off. Disabling Wine Bus/HID can break controllers.", Icons.Filled.Warning)
    WineUtils.STARTUP_SERVICES.forEachIndexed { i, entry ->
        val raw = WineUtils.startupServiceRawName(entry)
        val label = WineUtils.STARTUP_SERVICE_LABELS.getOrElse(i) { raw }
        rows += XmbRow.Toggle("svc:$raw", label, Icons.Filled.PowerSettingsNew, raw in enabled, subtitle = raw) { on ->
            val next = if (on) enabled + raw else enabled - raw
            p2Put(xmb, s, "startupServices", next.joinToString(","))
        }
    }
    rows
}

/** Checked core indices the way CPUListView reads "cpuList" (shortcut → container, fallback allowed). */
private fun p2CpuChecked(s: Shortcut, cores: Int): List<Int> {
    val set = s.p2Ex("cpuList", s.container.getCPUList(true)).split(",").map { it.trim() }.toSet()
    return (0 until cores).filter { "$it" in set }
}

/** Processor affinity (the editor's CPUListView): one toggle per core, saved as "0,1,4,…". */
private fun p2CpuMenu(xmb: XmbScope, s: Shortcut): XmbMenu = XmbMenu(xmb.context.getString(R.string.processor_affinity), Icons.Filled.Memory) {
    val cores = Runtime.getRuntime().availableProcessors()
    val checked = p2CpuChecked(s, cores)
    (0 until cores).map { i ->
        XmbRow.Toggle("cpu$i", "CPU$i", Icons.Filled.Memory, i in checked) { on ->
            val next = (0 until cores).filter { j -> if (j == i) on else j in checked }
            if (next.isEmpty()) xmb.toast("Keep at least one core enabled")
            else p2Put(xmb, s, "cpuList", next.joinToString(","))
        }
    }
}

// ── ReShade loadout (ReshadeLoadoutEditor + ReshadeCatalogPicker) ───────────────────────────────────
// One ReshadeLoadoutState per open Advanced section, loaded from shortcut → container like the editor's
// init, and written back to the same four extras after every change.

private class P2Reshade(val xmb: XmbScope, val shortcut: Shortcut) {
    val ctx: Context = xmb.context
    val st = ReshadeLoadoutState()
    var effects by mutableStateOf<List<ReshadeManager.ReshadeEffect>>(emptyList())
    var loaded by mutableStateOf(false)
    private var loading = false

    fun ensureLoaded() {
        if (loaded || loading) return
        loading = true
        xmb.scope.launch {
            val e: List<ReshadeManager.ReshadeEffect> = withContext(Dispatchers.IO) {
                runCatching { ReshadeManager.scanEffects(ctx).toList() }.getOrDefault(emptyList())
            }
            val c = shortcut.container
            effects = e
            st.init(
                e,
                shortcut.p2Ex("reshadeLoadout", c.getReshadeLoadout()).ifEmpty { null },
                shortcut.p2Ex("reshadeMode", c.getReshadeMode()),
                shortcut.p2Ex("reshadeParams", c.getReshadeParams()).ifEmpty { null },
                shortcut.p2Ex("reshadeEffect", c.getReshadeEffect()),
            )
            loaded = true
            loading = false
        }
    }

    fun persist() {
        shortcut.putExtra("reshadeLoadout", st.loadoutJsonOrNull())
        shortcut.putExtra("reshadeMode", st.mode)
        shortcut.putExtra("reshadeParams", st.paramsJsonOrNull())
        shortcut.putExtra("reshadeEffect", st.firstEffectName())
        shortcut.saveData()
        xmb.saved()
        xmb.refresh()
    }

    /** ReShade only applies to DXVK/VKD3D (Vulkan) games — the editor's reshadeSupported. */
    fun supported(): Boolean {
        val c = shortcut.container
        val entries = ctx.resources.getStringArray(R.array.dxwrapper_entries).toList()
        val id = shortcut.p2Ex("dxwrapper", c.getDXWrapper())
        val w = StringUtils.parseIdentifier(entries.firstOrNull { StringUtils.parseIdentifier(it) == id } ?: entries.firstOrNull() ?: id)
        return w.contains("dxvk") || w.contains("vegas")
    }

    fun toggle(name: String, add: Boolean) {
        if (add) effects.firstOrNull { it.name == name }?.let { st.add(it, null) } else st.remove(name)
        persist()
    }

    fun loadoutMenu(): XmbMenu {
        ensureLoaded()
        return XmbMenu("ReShade 滤镜配置", Icons.Filled.Layers) body@{
            val rows = mutableListOf<XmbRow>()
            if (!loaded) {
                rows += XmbRow.Info("loading", "Scanning effects…", Icons.Filled.Info)
                return@body rows
            }
            if (!supported() && st.order.isNotEmpty()) {
                rows += XmbRow.Info("unsupported", "ReShade仅适用于DXVK/VKD3D（Vulkan）游戏；对此DXwrapper无效。", Icons.Filled.Warning)
            }
            rows += XmbRow.Link("browse", "Browse / download effects", Icons.Filled.CloudDownload,
                value = "${st.order.size} in loadout", subtitle = "Pick installed effects or fetch new ones") { catalogMenu() }
            if (st.order.isEmpty()) {
                rows += XmbRow.Info("empty", "No effects in the loadout", Icons.Filled.Info)
                return@body rows
            }
            val solo = st.mode == ReshadeLoadout.MODE_SOLO
            rows += XmbRow.Choice("mode", "Mode", Icons.Filled.Tune, listOf("Solo", "Stack"), if (solo) "Solo" else "Stack",
                subtitle = if (solo) "One effect active at a time (A/B compare)" else "Layer any subset of effects") { v ->
                st.changeMode(if (v == "Stack") ReshadeLoadout.MODE_STACK else ReshadeLoadout.MODE_SOLO)
                persist()
            }
            if (st.order.size > 6) rows += XmbRow.Info("compile", "${st.order.size} effects — longer launch compile.", Icons.Filled.Warning)
            rows += XmbRow.Header("hEffects", "Effects")
            st.order.toList().forEach { name ->
                val on = st.isEnabled(name)
                rows += XmbRow.Link("fx:$name", name, Icons.Filled.Layers,
                    value = if (on) (if (solo) "Active" else "On") else "Off") { effectMenu(name) }
            }
            rows += XmbRow.Action("clear", "清除配置", Icons.Filled.Delete, danger = true) {
                xmb.confirm(XmbConfirm("清除配置", "从此游戏的ReShade配置中移除所有效果？", "清除", danger = true)) {
                    st.order.toList().forEach { st.remove(it) }
                    persist()
                }
            }
            rows
        }
    }

    fun effectMenu(name: String): XmbMenu = XmbMenu(name, Icons.Filled.Layers) body@{
        val rows = mutableListOf<XmbRow>()
        if (!st.contains(name)) {
            rows += XmbRow.Info("gone", "不再在配置中", Icons.Filled.Info)
            return@body rows
        }
        val solo = st.mode == ReshadeLoadout.MODE_SOLO
        rows += XmbRow.Toggle("enabled", "Enabled", Icons.Filled.Layers, st.isEnabled(name),
            subtitle = if (solo) "Solo: turning this on turns the others off" else null) { on ->
            st.setEnabled(name, on)
            persist()
        }
        val params: List<ReshadeManager.ReshadeParam> = effects.firstOrNull { it.name == name }?.params?.toList() ?: emptyList()
        if (params.isEmpty()) {
            rows += XmbRow.Info("noParams", "无可调参数。", Icons.Filled.Info)
        } else {
            rows += XmbRow.Header("hParams", "Parameters")
            params.forEach { p -> p2ReshadeParamRows(this@P2Reshade, name, p, rows) }
            rows += XmbRow.Action("resetParams", "重置参数", Icons.Filled.RestartAlt, subtitle = "恢复效果的默认值") {
                params.forEach { p ->
                    val tmp = HashMap<String, Float>()
                    ReshadeManager.seedValues(p, null, tmp)
                    tmp.forEach { (k, v) -> st.setParam(name, k, v) }
                }
                persist()
            }
        }
        rows += XmbRow.Action("remove", "从配置中移除", Icons.Filled.Delete, danger = true) {
            st.remove(name)
            persist()
            xmb.pop()
        }
        rows
    }

    /** ReshadeCatalogSheet: installed effects toggle loadout membership; available ones download. */
    fun catalogMenu(): XmbMenu {
        var catalog by mutableStateOf<List<ReshadeCatalogEntry>>(emptyList())
        var source by mutableStateOf(ReshadeCatalog.Source.NONE)
        var installed by mutableStateOf<Set<String>>(emptySet())
        var catLoading by mutableStateOf(true)
        var downloadingId by mutableStateOf<String?>(null)
        var phase by mutableStateOf("Downloading")
        var prog by mutableStateOf(0f)
        xmb.scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ReshadeCatalog.loadCached(ctx) }.getOrNull() }
            catalog = r?.entries ?: emptyList()
            source = r?.source ?: ReshadeCatalog.Source.NONE
            installed = withContext(Dispatchers.IO) { runCatching { ReshadeManager.scanEffectNames(ctx).toSet() }.getOrDefault(emptySet()) }
            catLoading = false
        }
        fun download(entry: ReshadeCatalogEntry) {
            downloadingId = entry.id
            phase = "Downloading"
            prog = 0f
            xmb.scope.launch {
                val ok = runCatching {
                    ReshadeDownloader.install(ctx, entry) { ph, f ->
                        p2OnMain(xmb) {
                            phase = if (ph == ReshadeDownloader.Phase.EXTRACT) "Installing" else "Downloading"
                            prog = f
                        }
                    }
                }.getOrDefault(false)
                downloadingId = null
                if (ok) {
                    installed = installed + entry.id
                    val e: List<ReshadeManager.ReshadeEffect> = withContext(Dispatchers.IO) {
                        runCatching { ReshadeManager.scanEffects(ctx).toList() }.getOrDefault(effects)
                    }
                    effects = e
                    st.reconcile(e)
                    toggle(entry.id, true) // auto-add the freshly installed effect
                } else xmb.toast("Failed to download ${entry.name}.")
            }
        }
        return XmbMenu("ReShade效果", Icons.Filled.CloudDownload) body@{
            val rows = mutableListOf<XmbRow>()
            if (catLoading) {
                rows += XmbRow.Info("loading", "正在加载效果目录…", Icons.Filled.CloudDownload)
                return@body rows
            }
            val offline = source != ReshadeCatalog.Source.NETWORK
            if (offline) {
                rows += XmbRow.Info("offline",
                    if (source == ReshadeCatalog.Source.CACHE) "Offline — showing a cached list. Connect to download new effects."
                    else "离线 — 仅显示已安装的效果。", Icons.Filled.Warning)
            }
            val selectedLc = st.order.map { it.lowercase() }.toSet()
            val catIds = catalog.map { it.id }.toSet()
            val extras = installed.filter { it !in catIds }.map { ReshadeCatalogEntry(it, it, "", "Installed", "", "", "", 0L, "", 1) }
            val all = (catalog + extras).distinctBy { it.id }.sortedBy { it.name.lowercase() }
            val inst = all.filter { it.id in installed }
            val avail = all.filter { it.id !in installed }
            if (inst.isNotEmpty()) rows += XmbRow.Header("hInstalled", "Installed (${inst.size})")
            inst.forEach { e ->
                rows += XmbRow.Toggle("i:${e.id}", e.name, Icons.Filled.Layers, e.id.lowercase() in selectedLc,
                    subtitle = listOf(e.author, e.category).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }) { on ->
                    toggle(e.id, on)
                }
            }
            if (avail.isNotEmpty()) rows += XmbRow.Header("hAvailable", "Available (${avail.size})")
            avail.forEach { e ->
                val busy = downloadingId == e.id
                rows += XmbRow.Action("a:${e.id}", e.name, Icons.Filled.Download,
                    subtitle = e.description.ifBlank { null } ?: listOf(e.author, e.category).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null },
                    value = if (busy) "$phase ${(prog * 100).toInt()}%" else null,
                    disabledReason = when {
                        offline -> "连接到互联网以下载效果。"
                        downloadingId != null && !busy -> "Another download is running"
                        busy -> "Downloading…"
                        else -> null
                    }) { download(e) }
            }
            if (inst.isEmpty() && avail.isEmpty()) rows += XmbRow.Info("none", "没有可用的效果。", Icons.Filled.Info)
            rows
        }
    }
}

/** One reflected uniform's control(s) — ReshadeParamControl as rows (sliders step through the range). */
private fun p2ReshadeParamRows(r: P2Reshade, effect: String, p: ReshadeManager.ReshadeParam, rows: MutableList<XmbRow>) {
    val st = r.st
    val value = st.paramValue(effect, p.name, p.defaultValue)
    val label = p.label ?: p.name
    when (p.type) {
        ReshadeManager.ParamType.BOOL -> rows += XmbRow.Toggle("p:${p.name}", label, Icons.Filled.Tune, value >= 0.5f) { on ->
            st.setParam(effect, p.name, if (on) 1f else 0f)
            r.persist()
        }
        ReshadeManager.ParamType.COMBO -> {
            val opts = p2Unique(p.options?.toList() ?: emptyList())
            if (opts.isNotEmpty()) {
                rows += XmbRow.Choice("p:${p.name}", label, Icons.Filled.Tune, opts, opts.getOrElse(value.toInt()) { opts.first() }) { v ->
                    st.setParam(effect, p.name, opts.indexOf(v).coerceAtLeast(0).toFloat())
                    r.persist()
                }
            }
        }
        ReshadeManager.ParamType.COLOR -> {
            val comp = listOf("R", "G", "B", "A")
            for (ci in 0 until p.components) {
                val k = "${p.name}_$ci"
                val cv = st.paramValue(effect, k, p.componentDefaults?.getOrNull(ci) ?: 0f)
                rows += XmbRow.Slider("p:$k", "$label ${comp.getOrElse(ci) { "$ci" }}", Icons.Filled.Image,
                    cv.coerceIn(0f, 1f), 0f, 1f, 0.05f, format = { "%.2f".format(it) }) { v ->
                    st.setParam(effect, k, v)
                    r.persist()
                }
            }
        }
        else -> {
            val isInt = p.type == ReshadeManager.ParamType.INT
            val min = p.min
            val max = if (p.max > p.min) p.max else p.min + 1f
            // The host rounds slider values to 0.01, so never step finer than that.
            val step = if (p.step > 0f) maxOf(p.step, 0.01f) else if (isInt) 1f else maxOf((max - min) / 100f, 0.01f)
            rows += XmbRow.Slider("p:${p.name}", label, Icons.Filled.Tune, value.coerceIn(min, max), min, max, step,
                format = { if (isInt) it.toInt().toString() else "%.2f".format(it) }) { v ->
                st.setParam(effect, p.name, if (isInt) Math.round(v).toFloat() else v)
                r.persist()
            }
        }
    }
}
