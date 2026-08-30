package com.permieware.osmapdigger.desktop.importing

import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Desktop platform adapter for selecting and reading the simple UTF-8 settlement-list text format. */
object DesktopSettlementListFilePicker {
    fun chooseAndRead(dialogTitle: String): String? =
        operationalBoundary(OperationalFailureKind.FILE_ACCESS, "Could not read settlement-list file") {
            val chooser =
                JFileChooser().apply {
                    this.dialogTitle = dialogTitle
                    fileSelectionMode = JFileChooser.FILES_ONLY
                    fileFilter = FileNameExtensionFilter("*.txt", "txt")
                }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                return@operationalBoundary null
            }
            Files.readString(chooser.selectedFile.toPath(), StandardCharsets.UTF_8)
        }
}
