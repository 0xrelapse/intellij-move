package org.move.cli.runconfig

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.EditorTextField
import com.intellij.ui.layout.panel
import com.intellij.util.text.nullize
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent

class MoveRunConfigurationEditor : SettingsEditor<AptosCommandConfiguration>() {
    private val textField = EditorTextField()
    private val environmentVariables = EnvironmentVariablesComponent()
    private val workingDirectory: Path?
        get() = workingDirectoryField.component.text.nullize()?.let { Paths.get(it) }

    val workingDirectoryField: LabeledComponent<TextFieldWithBrowseButton> =
        WorkingDirectoryComponent()

    override fun resetEditorFrom(configuration: AptosCommandConfiguration) {
        textField.text = configuration.command
        workingDirectoryField.component.text = configuration.workingDirectory?.toString().orEmpty()
        environmentVariables.envData = configuration.environmentVariables
    }

    override fun applyEditorTo(configuration: AptosCommandConfiguration) {
        configuration.command = textField.text
        configuration.workingDirectory = this.workingDirectory
        configuration.environmentVariables = environmentVariables.envData
    }

    override fun createEditor(): JComponent {
        return panel {
            row("Command:") {
                textField(growX, pushX)
            }
            row(environmentVariables.label) {
                environmentVariables(growX)
            }
            row(workingDirectoryField.label) {
                workingDirectoryField(growX)
            }
        }
    }

    private class WorkingDirectoryComponent : LabeledComponent<TextFieldWithBrowseButton>() {
        init {
            component = TextFieldWithBrowseButton().apply {
                val fileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                    title = ExecutionBundle.message("select.working.directory.message")
                }
                addBrowseFolderListener(null, null, null, fileChooser)
            }
            text = ExecutionBundle.message("run.configuration.working.directory.label")
        }
    }
}
