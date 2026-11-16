package dev.ribod.storestudio.idea.ui

import com.intellij.openapi.ui.ComboBox
import javax.swing.DefaultComboBoxModel

/**
 * Replaces the combo's options, keeping the current selection when it is still offered and
 * falling back to the first option otherwise. Reloads happen on every Gradle write, so filters
 * must not jump back to their defaults.
 */
internal fun ComboBox<String>.replaceOptionsKeepingSelection(options: List<String>) {
    val current = selectedItem as? String
    model = DefaultComboBoxModel(options.toTypedArray())
    selectedItem = current?.takeIf { it in options } ?: options.firstOrNull()
}
