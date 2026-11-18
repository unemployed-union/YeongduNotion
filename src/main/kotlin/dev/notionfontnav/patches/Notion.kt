package dev.notionfontnav.patches

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.listApkEntries
import app.morphe.patcher.patch.packageMetadata
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val PACKAGE = "notion.id"
private const val VERSION = "0.6.4318"
private const val BUILD = 10318

internal val notion = Compatibility(
    packageName = PACKAGE,
    name = "Notion",
    apkFileType = ApkFileType.APKS,
    targets = listOf(AppTarget(VERSION, BUILD, minSdk = 32))
)

internal fun verifyNotionInput() {
    val metadata = packageMetadata
    if (metadata.packageName != PACKAGE || metadata.versionName != VERSION || metadata.versionCode != BUILD.toString()) {
        throw PatchException("Expected $PACKAGE $VERSION ($BUILD), found ${metadata.packageName} ${metadata.versionName} (${metadata.versionCode}).")
    }
    if (listApkEntries("lib/").none { it.endsWith("/libnotion-nfm-core.so") }) {
        throw PatchException("Missing Notion native code. Supply the base APK and all of its matching splits in an APKS archive.")
    }
}

internal fun Method.body(): List<Instruction> = implementation?.instructions?.toList().orEmpty()

internal fun Instruction.reference() = (this as? ReferenceInstruction)?.reference

internal fun <T> List<T>.requireSingle(description: String): T = singleOrNull()
    ?: throw PatchException("Expected one $description, found $size.")
