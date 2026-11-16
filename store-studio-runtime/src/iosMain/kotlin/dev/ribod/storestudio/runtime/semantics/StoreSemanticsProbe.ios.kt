package dev.ribod.storestudio.runtime.semantics

import androidx.compose.ui.Modifier
import dev.ribod.storestudio.runtime.model.StoreContext

public actual fun Modifier.storeSemanticsProbe(
    context: StoreContext,
    callingMethodFqn: String?,
): Modifier = this

internal actual fun isStoreSemanticsCaptureEnabled(): Boolean = false
