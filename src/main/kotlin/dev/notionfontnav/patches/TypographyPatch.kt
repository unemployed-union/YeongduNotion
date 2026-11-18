package dev.notionfontnav.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceIds
import app.morphe.patcher.resource.ResourceType
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import java.util.logging.Logger

private const val TYPEFACE = "Landroid/graphics/Typeface;"
private const val BUILDER = "Landroid/graphics/Typeface\$CustomFallbackBuilder;"
private const val STRING = "Ljava/lang/String;"
private const val RESOURCES = "Landroid/content/res/Resources;"
private const val EXTENSION = "Ldev/notionfontnav/extension/Typography;"

private val resourceFont = ImmutableMethodReference(
    "Landroid/graphics/fonts/Font\$Builder;", "<init>", listOf(RESOURCES, "I"), "V"
)
private val fromFamily = ImmutableMethodReference(
    BUILDER, "<init>", listOf("Landroid/graphics/fonts/FontFamily;"), "V"
)
private val build = ImmutableMethodReference(BUILDER, "build", emptyList(), TYPEFACE)

private object ResourceTypeface : Fingerprint(
    isStatic = false,
    returnType = TYPEFACE,
    parameters = listOf("Landroid/content/Context;", RESOURCES, "I", STRING),
    strings = listOf("TypefaceCompatApi29Impl", "Font load failed"),
    filters = listOf(methodCall(resourceFont), methodCall(fromFamily), methodCall(build))
)

private object ComposeSystemTypeface : Fingerprint(
    isStatic = true,
    returnType = TYPEFACE,
    parameters = listOf(STRING, "L", "I"),
    filters = listOf(
        methodCall(TYPEFACE, "create", listOf(STRING, "I"), TYPEFACE),
        methodCall(TYPEFACE, "create", listOf(TYPEFACE, "I", "Z"), TYPEFACE)
    ),
    custom = { method, _ ->
        method.body().any { (it.reference() as? FieldReference)?.let { field ->
            field.definingClass == TYPEFACE && field.name == "DEFAULT_BOLD"
        } == true }
    }
)

private object EditorTypeface : Fingerprint(
    isStatic = true,
    returnType = TYPEFACE,
    parameters = listOf(STRING),
    strings = listOf("CJK-Regular", "CJK-Fallback", "Emoji-Fallback"),
    filters = listOf(methodCall("Ljava/util/concurrent/ConcurrentHashMap;", "putIfAbsent",
        listOf("Ljava/lang/Object;", "Ljava/lang/Object;"), "Ljava/lang/Object;"))
)

@Suppress("unused")
val preserveNotionTypography = patch(
    name = "Preserve Notion Typography",
    description = "Keeps Notion's native typography and stock Samsung fallback with a custom system font.",
    default = true
) {
    compatibleWith(notion)
    extendWith("notion-typography-extension.dex")

    execute {
        verifyNotionInput()
        val robotoFlex = resourceIds[ResourceType.FONT, "roboto_flex_variable"].toInt()
        val resource = ResourceTypeface.matchAll().requireSingle("resource Typeface loader")
        val compose = ComposeSystemTypeface.matchAll().requireSingle("Compose system Typeface resolver")
        val editor = EditorTypeface.matchAll().requireSingle("native editor Typeface registry")

        val body = resource.originalMethod.body()
        val font = body.filter { it.reference() == resourceFont }.requireSingle("resource Font constructor") as Instruction35c
        val family = body.filter { it.reference() == fromFamily }.requireSingle("Typeface family constructor") as Instruction35c
        val buildIndex = body.indices.filter { body[it].reference() == build }.requireSingle("Typeface build call")
        val call = body[buildIndex] as Instruction35c
        check(call.opcode == Opcode.INVOKE_VIRTUAL && call.registerCount == 1 && font.registerCount == 3 &&
            family.registerCount == 2 && setOf(call.registerC, font.registerE, family.registerD).size == 3 &&
            body.getOrNull(buildIndex + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT &&
            body.getOrNull(buildIndex + 2)?.opcode == Opcode.RETURN_OBJECT)

        val editorBody = editor.originalMethod.body()
        val defaultIndex = editorBody.indices.filter { index ->
            val instruction = editorBody[index]
            val field = instruction.reference() as? FieldReference
            instruction.opcode == Opcode.SGET_OBJECT && field?.definingClass == TYPEFACE && field.name == "DEFAULT"
        }.requireSingle("native editor default fallback read")
        val fallbackRegister = (editorBody[defaultIndex] as OneRegisterInstruction).registerA

        // Keep the font, builder style, resource cache, and the later variation-axis processing.
        resource.method.replaceInstruction(buildIndex,
            "invoke-static {v${call.registerC}, v${font.registerE}, v${family.registerD}}, $EXTENSION->resourceTypeface(${BUILDER}II)$TYPEFACE")
        resource.method.addInstructions(buildIndex, "const v${family.registerD}, $robotoFlex")
        compose.method.addInstructions(0, """
            invoke-static {p0}, $EXTENSION->composeFamily($STRING)$STRING
            move-result-object p0
        """.trimIndent())
        editor.method.replaceInstruction(defaultIndex, "invoke-static {p0}, $EXTENSION->nativeEditorFallback($STRING)$TYPEFACE")
        editor.method.addInstructions(defaultIndex + 1, "move-result-object v$fallbackRegister")

        Logger.getLogger("dev.notionfontnav.typography").info(
            "Patched ${resource.originalMethod}, ${compose.originalMethod}, and ${editor.originalMethod}."
        )
    }
}
