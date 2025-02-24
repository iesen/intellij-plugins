// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.angular2.lang.types

import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass
import com.intellij.lang.javascript.psi.resolve.JSGenericMappings
import com.intellij.lang.javascript.psi.resolve.JSGenericTypesEvaluatorBase
import com.intellij.lang.javascript.psi.resolve.generic.JSTypeSubstitutorImpl
import com.intellij.lang.javascript.psi.types.*
import com.intellij.lang.javascript.psi.types.JSTypeSubstitutor.JSTypeGenericId
import com.intellij.lang.javascript.psi.types.JSUnionOrIntersectionType.OptimizedKind
import com.intellij.lang.javascript.psi.types.guard.TypeScriptTypeRelations
import com.intellij.psi.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import org.angular2.codeInsight.Angular2DeclarationsScope
import org.angular2.codeInsight.Angular2LibrariesHacks.hackNgModelChangeType
import org.angular2.codeInsight.attributes.Angular2ApplicableDirectivesProvider
import org.angular2.codeInsight.attributes.Angular2AttributeDescriptor
import org.angular2.entities.Angular2ComponentLocator.findComponentClass
import org.angular2.entities.Angular2Directive
import org.angular2.entities.Angular2DirectiveProperty
import org.angular2.entities.Angular2EntityUtils.TEMPLATE_REF
import org.angular2.lang.expr.psi.Angular2Binding
import org.angular2.lang.expr.psi.Angular2TemplateBinding
import org.angular2.lang.expr.psi.Angular2TemplateBindings
import org.angular2.lang.html.parser.Angular2AttributeNameParser
import org.jetbrains.annotations.Contract
import java.util.*
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Predicate

internal class BindingsTypeResolver private constructor(element: PsiElement,
                                                        provider: Angular2ApplicableDirectivesProvider,
                                                        inputExpressionsProvider: (PsiElement) -> Sequence<Pair<String, JSExpression>>) {
  private val myElement: PsiElement
  private val myMatched: List<Angular2Directive>
  private val myScope: Angular2DeclarationsScope
  private val myRawTemplateContextType: JSType?
  private val myTypeSubstitutor: JSTypeSubstitutor?


  fun resolveDirectiveEventType(name: String): JSType? {
    val types: MutableList<JSType?> = SmartList()
    for (directive in myMatched) {
      var property: Angular2DirectiveProperty? = null
      if (myScope.contains(directive) && directive.outputs.find { it.name == name }.also { property = it } != null) {
        types.add(hackNgModelChangeType(property!!.type, name))
      }
    }
    return processAndMerge(types)
  }

  fun resolveDirectiveInputType(key: String): JSType? {
    val types: MutableList<JSType?> = SmartList()
    for (directive in myMatched) {
      var property: Angular2DirectiveProperty? = null
      if (myScope.contains(directive) && directive.inputs.find { it.name == key }.also { property = it } != null) {
        types.add(property!!.type)
      }
    }
    return processAndMerge(types)
  }

  fun resolveTemplateContextType(): JSType? {
    return if (myTypeSubstitutor != null) JSTypeUtils.applyGenericArguments(myRawTemplateContextType, myTypeSubstitutor)
    else myRawTemplateContextType
  }

  init {
    myElement = element
    myMatched = provider.matched
    myScope = Angular2DeclarationsScope(element)
    val directives = myMatched.filter { myScope.contains(it) }
    if (directives.isEmpty()) {
      myRawTemplateContextType = null
      myTypeSubstitutor = null
    }
    else {
      val analyzed = analyze(directives, element, inputExpressionsProvider)
      myRawTemplateContextType = analyzed.first
      myTypeSubstitutor = analyzed.second
    }
  }

  private fun processAndMerge(types: List<JSType?>): JSType? {
    var notNullTypes = types.mapNotNull { it }
    val source = getTypeSource(myElement, notNullTypes)
    if (source == null || notNullTypes.isEmpty()) {
      return null
    }
    if (myTypeSubstitutor != null) {
      notNullTypes = notNullTypes.mapNotNull { JSTypeUtils.applyGenericArguments(it, myTypeSubstitutor) }
    }
    return merge(source, notNullTypes, false)
  }

  companion object {

    fun resolve(attribute: XmlAttribute,
                infoValidation: Predicate<Angular2AttributeNameParser.AttributeInfo>,
                resolveMethod: BiFunction<BindingsTypeResolver, String, JSType?>): JSType? {
      val descriptor = attribute.descriptor as? Angular2AttributeDescriptor
      val tag = attribute.parent
      val info = Angular2AttributeNameParser.parse(attribute.name, attribute.parent)
      return if (descriptor == null || tag == null || !infoValidation.test(info)) {
        null
      }
      else resolveMethod.apply(get(tag), info.name)
    }

    fun get(tag: XmlTag): BindingsTypeResolver =
      CachedValuesManager.getCachedValue(tag) {
        CachedValueProvider.Result.create(create(tag), PsiModificationTracker.MODIFICATION_COUNT)
      }

    fun get(bindings: Angular2TemplateBindings): BindingsTypeResolver =
      CachedValuesManager.getCachedValue(bindings) {
        CachedValueProvider.Result.create(
          create(bindings), PsiModificationTracker.MODIFICATION_COUNT)
      }

    @Suppress("UNCHECKED_CAST")
    private fun <T : PsiElement> create(element: T,
                                        provider: Angular2ApplicableDirectivesProvider,
                                        inputExpressionsProvider: (T) -> Sequence<Pair<String, JSExpression>>) =
      BindingsTypeResolver(element, provider, inputExpressionsProvider as (PsiElement) -> Sequence<Pair<String, JSExpression>>)

    private fun create(bindings: Angular2TemplateBindings) =
      create(bindings, Angular2ApplicableDirectivesProvider(bindings)) { b: Angular2TemplateBindings ->
        b.bindings
          .asSequence()
          .filter { binding: Angular2TemplateBinding -> !binding.keyIsVar() }
          .mapNotNull { Pair(it.key, it.expression ?: return@mapNotNull null) }
      }

    private fun create(tag: XmlTag) =
      create(tag, Angular2ApplicableDirectivesProvider(tag)) { t: XmlTag ->
        t.attributes
          .asSequence()
          .mapNotNull { attr ->
            val name = Angular2AttributeNameParser.parse(attr.name, attr.parent)
                         .takeIf { Angular2PropertyBindingType.isPropertyBindingAttribute(it) }
                         ?.name
                       ?: return@mapNotNull null
            Pair(name, Angular2Binding.get(attr)?.expression ?: return@mapNotNull null)
          }
      }

    private fun <T : PsiElement> analyze(directives: List<Angular2Directive>,
                                         element: T,
                                         inputExpressionsProvider: (T) -> Sequence<Pair<String, JSExpression>>): Pair<JSType?, JSTypeSubstitutor?> {
      val inputsMap = inputExpressionsProvider(element)
        .distinctBy { it.first }
        .toMap()
      val genericArguments = MultiMap<JSTypeGenericId, JSType?>()
      val templateContextTypes: MutableList<JSType?> = SmartList()
      directives.forEach { directive: Angular2Directive ->
        val clazz = directive.typeScriptClass ?: return@forEach
        val templateContextType = getTemplateContextType(clazz)
        if (templateContextType != null) {
          templateContextTypes.add(templateContextType)
        }
        val processingContext = JSTypeComparingContextService.createProcessingContextWithCache(clazz)
        directive.inputs.forEach(Consumer { property: Angular2DirectiveProperty ->
          val inputExpression = inputsMap[property.name]
          var propertyType: JSType? = null
          if (inputExpression != null && property.type.also { propertyType = it } != null) {
            val inputType = JSPsiBasedTypeOfType(inputExpression, true)
            if (JSTypeUtils.isAnyType(JSTypeUtils.getApparentType(JSTypeWithIncompleteSubstitution.substituteCompletely(inputType)))) {
              // This workaround is needed, because many users expect to have ngForOf working with variable of type `any`.
              // This is not correct according to TypeScript inferring rules for generics, but it's better for Angular type
              // checking to be less strict here. Additionally, if `any` type is passed to e.g. async pipe it's going to be resolved
              // with `null`, so we need to check for `null` and `undefined` as well
              val anyType = JSAnyType.get(inputType.source)
              TypeScriptTypeRelations.expandAndOptimizeTypeRecursive(propertyType).accept(object : JSRecursiveTypeVisitor(true) {
                override fun visitJSType(type: JSType) {
                  if (type is JSGenericParameterType) {
                    genericArguments.putValue(type.genericId, anyType)
                  }
                  super.visitJSType(type)
                }
              })
            }
            else {
              JSGenericTypesEvaluatorBase
                .matchGenericTypes(JSGenericMappings(genericArguments), processingContext, inputType, propertyType!!) { true }
              JSGenericTypesEvaluatorBase
                .widenInferredTypes(genericArguments, listOf<JSType?>(propertyType), null, null, processingContext)
            }
          }
        })
      }
      val typeSource = getTypeSource(element, templateContextTypes, genericArguments)
      return Pair(
        if (typeSource == null || templateContextTypes.isEmpty())
          null
        else
          merge(typeSource, templateContextTypes, true),
        if (typeSource == null || genericArguments.isEmpty)
          null
        else
          intersectGenerics(genericArguments, typeSource)
      )
    }

    private fun intersectGenerics(arguments: MultiMap<JSTypeGenericId, JSType?>,
                                  source: JSTypeSource): JSTypeSubstitutor {
      val result = JSTypeSubstitutorImpl()
      for ((key, value) in arguments.entrySet()) {
        result.put(key, merge(source, value.mapNotNull { it }, false))
      }
      return result
    }

    private fun merge(source: JSTypeSource, types: List<JSType?>, union: Boolean): JSType {
      return JSCompositeTypeImpl.optimizeTypeIfComposite(
        if (union) JSCompositeTypeFactory.createUnionType(source, types) else JSCompositeTypeFactory.createIntersectionType(types, source),
        OptimizedKind.OPTIMIZED_SIMPLE)
    }

    private fun getTypeSource(element: PsiElement,
                              templateContextTypes: List<JSType?>,
                              genericArguments: MultiMap<JSTypeGenericId, JSType?>): JSTypeSource? {
      val source = getTypeSource(element, templateContextTypes)
      return source
             ?: genericArguments.values().find { it != null }?.source
    }

    private fun getTypeSource(element: PsiElement,
                              types: List<JSType?>): JSTypeSource? {
      val componentClass = findComponentClass(element)
      return if (componentClass != null) {
        JSTypeSourceFactory.createTypeSource(componentClass, true)
      }
      else types.firstOrNull()?.source
    }

    @Contract("null -> null") //NON-NLS
    private fun getTemplateContextType(clazz: TypeScriptClass?): JSType? {
      if (clazz == null) {
        return null
      }
      var templateRefType: JSType? = null
      for (`fun` in clazz.constructors) {
        for (param in `fun`.parameterVariables) {
          if (param.jsType.let { it != null && it.typeText.startsWith("$TEMPLATE_REF<") }) {
            templateRefType = param.jsType
            break
          }
        }
      }
      return if (templateRefType !is JSGenericTypeImpl) {
        null
      }
      else templateRefType.arguments.firstOrNull()
    }
  }
}