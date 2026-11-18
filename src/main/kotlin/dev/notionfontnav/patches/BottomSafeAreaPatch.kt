package dev.notionfontnav.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.classDefByStrings
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.util.logging.Logger

private const val VIEW = "Landroid/view/View;"
private const val VISIBILITY = "Lnotion/local/id/shared/compose/FloatingActionButtonVisibility;"
private const val TOOLBAR_COMPOSITION_KEY = 0x7a3896c5L
private const val ORIGINAL_PADDING = 0x40800000L // 4 dp as a float
private const val EXTENSION = "Ldev/notionfontnav/extension/BottomSafeArea;->toolbarPadding(Landroid/view/View;F)F"

private object ToolbarContainer : Fingerprint(
    isStatic = false,
    parameters = listOf("L", VISIBILITY, "J", "L", "L", "I"),
    returnType = "V",
    filters = listOf(literal(TOOLBAR_COMPOSITION_KEY))
)

private object ToolbarMorph : Fingerprint(strings = listOf("BarMorph"))

@Suppress("unused")
val fixNotionBottomSafeArea = patch(
    name = "Fix Notion Bottom Safe Area",
    description = "Restores the floating toolbar's bottom spacing when Samsung gesture hints are hidden.",
    default = true
) {
    compatibleWith(notion)
    extendWith("notion-bottom-extension.dex")

    execute {
        verifyNotionInput()
        val container = ToolbarContainer.matchAll().requireSingle("Notion floating toolbar container")
        val contentType = container.originalMethod.body().mapNotNull { it.reference() as? MethodReference }
            .filter { it.name == "<init>" && it.parameterTypes.size == 2 && it.parameterTypes.first() == "J" }
            .requireSingle("floating toolbar content constructor").definingClass
        val renderer = Fingerprint(
            definingClass = contentType,
            name = "invoke",
            parameters = List(3) { "Ljava/lang/Object;" },
            returnType = "Ljava/lang/Object;",
            filters = listOf(literal(ORIGINAL_PADDING))
        ).matchAll().requireSingle("floating toolbar renderer")

        val holder = classDefByStrings("navigationBarsIgnoringVisibility").requireSingle("Compose WindowInsets holder")
        val viewLookup = Fingerprint(
            isStatic = true,
            parameters = listOf("L"),
            returnType = holder.type,
            custom = { method, _ -> method.body().any { it.opcode == Opcode.CHECK_CAST && (it.reference() as? TypeReference)?.type == VIEW } }
        ).matchAll().requireSingle("Compose LocalView lookup").originalMethod.body()
        val localView = viewLookup.first().reference() as? FieldReference
            ?: error("Compose LocalView no longer starts with a field read")
        val readLocal = viewLookup.filter { it.opcode == Opcode.INVOKE_VIRTUAL }
            .first().reference() as MethodReference

        // Read the existing Notion insets state so Compose re-renders this padding on inset changes.
        val insetState = classDefByStrings("Insets(keyboard=").requireSingle("Notion insets state")
        val morph = ToolbarMorph.matchAll().requireSingle("Notion toolbar animation").originalMethod.body()
        val insetLocal = morph.windowed(4).filter { window ->
            window[0].opcode == Opcode.SGET_OBJECT && window[1].reference() == readLocal &&
                window[2].opcode == Opcode.MOVE_RESULT_OBJECT && window[3].opcode == Opcode.CHECK_CAST &&
                (window[3].reference() as? TypeReference)?.type == insetState.type
        }.requireSingle("Notion insets CompositionLocal").first().reference() as FieldReference

        val body = renderer.originalMethod.body()
        val composer = body.filter {
            it.opcode == Opcode.CHECK_CAST && (it.reference() as? TypeReference)?.type == readLocal.definingClass
        }.requireSingle("toolbar Composer register") as OneRegisterInstruction
        val padding = body.indices.filter { index ->
            val instruction = body[index] as? NarrowLiteralInstruction
            val call = body.getOrNull(index + 5) as? Instruction3rc
            val method = call?.reference as? MethodReference
            instruction?.narrowLiteral == ORIGINAL_PADDING.toInt() &&
                call?.opcode == Opcode.INVOKE_STATIC_RANGE && call.registerCount == 6 &&
                (instruction as? OneRegisterInstruction)?.registerA == call.startRegister + 4 &&
                method?.parameterTypes?.drop(1) == listOf("F", "F", "F", "F", "I") &&
                body[index + 2].opcode == Opcode.CONST_HIGH16 && body[index + 3].opcode == Opcode.CONST_4
        }.requireSingle("floating toolbar padding call")

        val first = (body[padding + 5] as Instruction3rc).startRegister
        val start = first + 1
        val top = first + 2
        val bottom = first + 4
        val composerReg = composer.registerA
        check(start <= 15 && top <= 15 && composerReg <= 15 && composerReg !in start..bottom)
        renderer.method.addInstructions(padding + 1, """
            sget-object v$top, $insetLocal
            invoke-virtual {v$composerReg, v$top}, $readLocal
            sget-object v$top, $localView
            invoke-virtual {v$composerReg, v$top}, $readLocal
            move-result-object v$top
            check-cast v$top, $VIEW
            move/from16 v$start, v$bottom
            invoke-static {v$top, v$start}, $EXTENSION
            move-result v$bottom
        """.trimIndent())
        Logger.getLogger("dev.notionfontnav.bottom").info("Adjusted floating toolbar padding in ${renderer.originalMethod}.")
    }
}
