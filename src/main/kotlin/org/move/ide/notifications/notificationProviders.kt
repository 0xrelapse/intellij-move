package org.move.ide.notifications

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.move.cli.MoveBinary
import org.move.lang.isMoveTomlManifestFile
import org.move.lang.isMoveFile
import org.move.openapiext.common.isUnitTestMode
import org.move.cli.settings.MvSettingsChangedEvent
import org.move.cli.settings.MvSettingsListener
import org.move.cli.settings.moveSettings

fun updateAllNotifications(project: Project) {
    EditorNotifications.getInstance(project).updateAllNotifications()
}

class UpdateNotificationsOnSettingsChangeListener(val project: Project) : MvSettingsListener {

    override fun moveSettingsChanged(e: MvSettingsChangedEvent) {
        updateAllNotifications(project)
    }
}

class UnconfiguredAptosNotification(
    private val project: Project
) : EditorNotifications.Provider<EditorNotificationPanel>(),
    DumbAware {

    private val VirtualFile.disablingKey: String
        get() = NOTIFICATION_STATUS_KEY + path

    override fun getKey(): Key<EditorNotificationPanel> = PROVIDER_KEY

    protected fun disableNotification(file: VirtualFile) {
        PropertiesComponent.getInstance(project).setValue(file.disablingKey, true)
    }

    protected fun isNotificationDisabled(file: VirtualFile): Boolean =
        PropertiesComponent.getInstance(project).getBoolean(file.disablingKey)

    override fun createNotificationPanel(
        file: VirtualFile,
        fileEditor: FileEditor,
        project: Project
    ): EditorNotificationPanel? {
        if (isUnitTestMode) return null
        if (
            !(file.isMoveFile || file.isMoveTomlManifestFile)
            || isNotificationDisabled(file)
        ) return null

        if (MoveBinary(project).version() != null) return null

        return EditorNotificationPanel().apply {
            text = "Move configured incorrectly"
            createActionLabel("Configure") {
                project.moveSettings.showMvConfigureSettings()
            }
            createActionLabel("Do not show again") {
                disableNotification(file)
                updateAllNotifications(project)
            }
        }
    }

    companion object {
        private const val NOTIFICATION_STATUS_KEY = "org.move.hideMoveNotifications"

        private val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("Fix Move.toml file")
    }
}
