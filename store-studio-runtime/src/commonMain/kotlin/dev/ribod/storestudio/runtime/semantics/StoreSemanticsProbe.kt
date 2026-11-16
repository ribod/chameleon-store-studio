package dev.ribod.storestudio.runtime.semantics

import androidx.compose.ui.Modifier
import dev.ribod.storestudio.runtime.model.StoreContext

/**
 * Attaches a semantics inspection probe to the screenshot canvas (DD-18).
 *
 * In headless rendering on the JVM, intercepts Compose SemanticsOwner to extract text nodes,
 * accessibility descriptions, bounds, and layout overflow indicators.
 */
public expect fun Modifier.storeSemanticsProbe(
    context: StoreContext,
    callingMethodFqn: String? = null,
): Modifier

/** Whether the current process renders headlessly with semantics capture enabled. */
internal expect fun isStoreSemanticsCaptureEnabled(): Boolean
