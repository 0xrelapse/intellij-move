package org.move.cli

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.move.cli.packages.MovePackage
import org.move.cli.manifest.MoveToml
import org.move.openapiext.contentRoots
import org.move.openapiext.parseTomlFromFile
import org.move.stdext.deepIterateChildrenRecursivery

sealed class InitResult {
    class Ok(val moveProject: MoveProject) : InitResult()
    class Err(val message: String) : InitResult()
}

fun ok(moveProject: MoveProject) = InitResult.Ok(moveProject)
fun error(message: String) = InitResult.Err(message)

//fun findMoveTomlFilesDeepestFirst(project: Project): Sequence<VirtualFile> {
//    return findMoveTomlFiles(project)
//        .sortedByDescending { it.path.split("/").count() }
//}

fun findMoveTomlFiles(project: Project): Sequence<VirtualFile> {
    // search over content roots
    val contentRoots = project.contentRoots
    val moveFiles = mutableSetOf<VirtualFile>()
    for (contentRoot in contentRoots) {
        deepIterateChildrenRecursivery(
            contentRoot, { it.name == Consts.MANIFEST_FILE })
        {
            moveFiles.add(it)
            true
        }
    }
    return moveFiles.asSequence()
}

fun initializeMoveProject(project: Project, fsMoveTomlFile: VirtualFile): MoveProject? {
    return runReadAction {
        val tomlFile = parseTomlFromFile(project, fsMoveTomlFile) ?: return@runReadAction null
        val contentRoot = fsMoveTomlFile.parent!!
        val moveToml = MoveToml.fromTomlFile(tomlFile, contentRoot.toNioPath())
        val addresses = moveToml.declaredAddresses(DevMode.MAIN)
        val devAddresses = moveToml.declaredAddresses(DevMode.DEV)

        val movePackage = MovePackage.fromTomlFile(tomlFile) ?: return@runReadAction null
        MoveProject(project, addresses, devAddresses, movePackage)
    }
}
