package org.move.cli.module

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import org.move.cli.MoveProject
import org.move.cli.moveProjects
import org.move.ide.MoveIcons
import org.move.openapiext.contentRoots
import org.move.openapiext.toVirtualFile
import javax.swing.Icon

class MoveLibrary(
    private val name: String,
    private val sourceRoots: Set<VirtualFile>,
    private val excludedRoots: Set<VirtualFile>,
    private val icon: Icon,
    private val version: String?
) : SyntheticLibrary(), ItemPresentation {
    override fun getSourceRoots(): Collection<VirtualFile> = sourceRoots
    override fun getExcludedRoots(): Set<VirtualFile> = excludedRoots

    override fun equals(other: Any?): Boolean = other is MoveLibrary && other.sourceRoots == sourceRoots
    override fun hashCode(): Int = sourceRoots.hashCode()

    override fun getLocationString(): String? = null

    override fun getIcon(unused: Boolean): Icon = icon

    override fun getPresentableText(): String = if (version != null) "$name $version" else name
}

private val MoveProject.ideaLibraries: Collection<MoveLibrary>
    get() {
        return this.dependencies
            .map { it.first }
            // dependency is not a child of any content root
            .filter { pkg ->
                this.project.contentRoots.all { ideRoot ->
                    !pkg.contentRoot.path.startsWith(ideRoot.path)
                }
            }
            .map {
                val sourceRoots = it.layoutPaths().mapNotNull { p -> p.toVirtualFile() }.toMutableSet()
                it.moveToml.tomlFile
                    ?.virtualFile?.let { f -> sourceRoots.add(f) }
                MoveLibrary(it.packageName, sourceRoots, emptySet(), MoveIcons.MOVE, null)
            }

    }

class BuildLibraryRootsProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): MutableSet<SyntheticLibrary> {
        return project.moveProjects
            .allProjects
            .flatMap { it.ideaLibraries }
            .toMutableSet()
    }

    override fun getRootsToWatch(project: Project): List<VirtualFile> {
        return getAdditionalProjectLibraries(project).flatMap { it.sourceRoots }
    }
}
