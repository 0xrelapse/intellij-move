package org.move.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.move.cli.Constants
import org.move.cli.MoveToml
import org.move.lang.core.psi.MoveAddressBlock
import org.move.lang.core.psi.MoveAddressDef
import org.move.lang.core.psi.MoveScriptBlock
import org.move.lang.core.psi.MoveScriptDef
import org.move.openapiext.parseToml
import org.move.openapiext.resolveAbsPath
import org.toml.lang.psi.TomlFile
import java.nio.file.Path

fun findMoveTomlPath(currentFilePath: Path): Path? {
    var dir = currentFilePath.parent
    while (dir != null) {
        val moveTomlPath = dir.resolveAbsPath(Constants.MOVE_MANIFEST_FILE)
        if (moveTomlPath != null) {
            return moveTomlPath
        }
        dir = dir.parent
    }
    return null
}

fun PsiFile.getCorrespondingMoveTomlFile(): TomlFile? {
    try {
        val moveTomlPath =
            this.virtualFile?.toNioPath()?.let { findMoveTomlPath(it) } ?: return null
        return parseToml(this.project, moveTomlPath)

    } catch (e: UnsupportedOperationException) {
        return null
    }
}

fun PsiFile.getCorrespondingMoveToml(): MoveToml? {
    val tomlFile = getCorrespondingMoveTomlFile() ?: return null
    return MoveToml.fromTomlFile(tomlFile)
}

class MoveFile(fileViewProvider: FileViewProvider) : PsiFileBase(fileViewProvider, MoveLanguage) {
    override fun getFileType(): FileType = MoveFileType

    fun addressBlocks(): List<MoveAddressBlock> {
        val defs = PsiTreeUtil.getChildrenOfTypeAsList(this, MoveAddressDef::class.java)
        return defs.mapNotNull { it.addressBlock }.toList()
    }

    fun scriptBlocks(): List<MoveScriptBlock> {
        val defs = PsiTreeUtil.getChildrenOfTypeAsList(this, MoveScriptDef::class.java)
        return defs.mapNotNull { it.scriptBlock }.toList()
    }
}

val VirtualFile.isMoveFile: Boolean get() = fileType == MoveFileType
