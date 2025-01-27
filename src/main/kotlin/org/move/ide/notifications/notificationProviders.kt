package org.move.ide.notifications

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.move.cli.moveProjects
import org.move.cli.settings.*
import org.move.lang.isMoveOrManifest
import org.move.openapiext.common.isUnitTestMode
import org.move.openapiext.showSettings

fun updateAllNotifications(project: Project) {
    EditorNotifications.getInstance(project).updateAllNotifications()
}

class UpdateNotificationsOnSettingsChangeListener(val project: Project) : MoveSettingsListener {

    override fun moveSettingsChanged(e: MoveSettingsChangedEvent) {
        updateAllNotifications(project)
    }
}

class InvalidAptosBinaryNotification(
    private val project: Project
) : EditorNotifications.Provider<EditorNotificationPanel>(),
    DumbAware {

    private val VirtualFile.disablingKey: String
        get() = NOTIFICATION_STATUS_KEY + path

    override fun getKey(): Key<EditorNotificationPanel> = PROVIDER_KEY

    override fun createNotificationPanel(
        file: VirtualFile,
        fileEditor: FileEditor,
        project: Project
    ): EditorNotificationPanel? {
        if (isUnitTestMode) return null
        if (!file.isMoveOrManifest) return null

        if (project.moveProjects.allProjects.isEmpty()) {
            project.moveProjects.refreshAllProjects()
        }

        if (project.aptosPath.isValidExecutable()) return null
        if (isNotificationDisabled(file)) return null

        return EditorNotificationPanel().apply {
            text = "Aptos binary path is not provided or invalid"
            createActionLabel("Configure") {
                project.showSettings<PerProjectMoveConfigurable>()
            }
            createActionLabel("Do not show again") {
                disableNotification(file)
                updateAllNotifications(project)
            }
        }
    }

    private fun disableNotification(file: VirtualFile) {
        PropertiesComponent.getInstance(project).setValue(file.disablingKey, true)
    }

    private fun isNotificationDisabled(file: VirtualFile): Boolean =
        PropertiesComponent.getInstance(project).getBoolean(file.disablingKey)

    companion object {
        private const val NOTIFICATION_STATUS_KEY = "org.move.hideMoveNotifications"

        private val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("Fix Move.toml file")
    }
}
