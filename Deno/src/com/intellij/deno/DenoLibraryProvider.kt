package com.intellij.deno

import com.intellij.lang.javascript.ecmascript6.TypeScriptUtil
import com.intellij.lang.javascript.library.JSSyntheticLibraryProvider
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class DenoLibrary(private val libs: List<VirtualFile>) : SyntheticLibrary(), ItemPresentation {
  override fun getSourceRoots(): Collection<VirtualFile> {
    return libs
  }

  override fun getExcludeFileCondition(): Condition<VirtualFile>? {
    return Condition {
      if (it.isDirectory) return@Condition false
      if (TypeScriptUtil.isDefinitionFile(it)) return@Condition false
      val extension = FileUtil.getExtension(it.nameSequence)
      
      return@Condition extension.isNotEmpty() 
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as DenoLibrary

    if (libs != other.libs) return false

    return true
  }

  override fun getPresentableText(): String {
    return "deno@libs"
  }

  override fun getLocationString(): String? {
    return null
  }

  override fun hashCode(): Int {
    return libs.hashCode()
  }

  override fun getIcon(unused: Boolean): Icon? {
    return null
  }
}

class DenoLibraryProvider : AdditionalLibraryRootsProvider(), JSSyntheticLibraryProvider {
  override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
    if (!DenoSettings.getService(project).isUseDeno()) return emptyList()
    
    val libs = getLibs()
    if (libs.isEmpty()) return emptyList()

    return listOf(DenoLibrary(libs))
  }

  private fun getLibs(): List<VirtualFile> {
    val denoPackages = DenoUtil.getDenoPackagesPath()
    val denoTypings = DenoUtil.getDenoTypings()
    val depsVirtualFile = LocalFileSystem.getInstance().findFileByPath(denoPackages)
    val denoTypingsVirtualFile = LocalFileSystem.getInstance().findFileByPath(denoTypings)
    return listOfNotNull(depsVirtualFile, denoTypingsVirtualFile)
  }

  override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
    if (!DenoSettings.getService(project).isUseDeno()) return emptyList()
    return getLibs()
  }
}