// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.vuejs.lang.html.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.stubs.DefaultStubBuilder
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.xml.HtmlFileElementType
import org.jetbrains.vuejs.lang.LangMode
import org.jetbrains.vuejs.lang.VueScriptLangs
import org.jetbrains.vuejs.lang.expr.parser.VueJSStubElementTypes
import org.jetbrains.vuejs.lang.html.VueLanguage
import org.jetbrains.vuejs.lang.html.lexer.VueLexer

class VueFileElementType : IStubFileElementType<VueFileStub>("vue", VueLanguage.INSTANCE) {
  companion object {
    @JvmStatic
    val INSTANCE: VueFileElementType = VueFileElementType()

    const val INJECTED_FILE_SUFFIX = ".#@injected@#.html"

    fun readDelimiters(fileName: String?): Pair<String, String>? {
      if (fileName == null || !fileName.endsWith(INJECTED_FILE_SUFFIX)) return null
      val endDot = fileName.length - INJECTED_FILE_SUFFIX.length
      val separatorDot = fileName.lastIndexOf('.', endDot - 1)
      val startDot = fileName.lastIndexOf('.', separatorDot - 1)
      if (endDot < 0 || startDot < 0 || separatorDot < 0) {
        return null
      }
      return Pair(fileName.substring(startDot + 1, separatorDot), fileName.substring(separatorDot + 1, endDot))
    }
  }

  override fun getStubVersion(): Int {
    return HtmlFileElementType.getHtmlStubVersion() + VueStubElementTypes.VERSION + VueJSStubElementTypes.STUB_VERSION
  }

  override fun getExternalId(): String {
    return "$language:$this"
  }

  override fun getBuilder(): StubBuilder? {
    return object : DefaultStubBuilder() {
      override fun createStubForFile(file: PsiFile): StubElement<*> {
        return if (file is VueFile) VueFileStub(file) else super.createStubForFile(file)
      }
    }
  }

  override fun serialize(stub: VueFileStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.langMode.canonicalAttrValue)
  }

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): VueFileStub {
    return VueFileStub(LangMode.fromAttrValue(dataStream.readNameString()!!))
  }

  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode {
    val delimiters = readDelimiters(SharedImplUtil.getContainingFile(chameleon).name)
    val languageForParser = getLanguageForParser(psi)
    // TODO support for custom delimiters - port to Angular and merge
    if (languageForParser === VueLanguage.INSTANCE) {
      val project = psi.project
      val lexer = VueParserDefinition.createLexer(project, delimiters)
      val builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, languageForParser, chameleon.chars)
      lexer as VueLexer
      if (lexer.lexedLangMode == LangMode.PENDING) {
        lexer.lexedLangMode = LangMode.NO_TS
      }
      builder.putUserData(VueScriptLangs.LANG_MODE, lexer.lexedLangMode) // read in VueParsing
      psi.putUserData(VueScriptLangs.LANG_MODE, lexer.lexedLangMode) // read in VueElementTypes
      val parser = LanguageParserDefinitions.INSTANCE.forLanguage(languageForParser)!!.createParser(project)
      val node = parser.parse(this, builder)

      return node.firstChildNode
    }

    return super.doParseContents(chameleon, psi)
  }
}
