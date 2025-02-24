// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.plugins.drools.lang.lexer;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;

import java.util.HashMap;
import java.util.Map;

public final class DroolsElementFactory {
  private static final Map<String, IElementType> ourCompositeMap = new HashMap<>();

  public static synchronized IElementType getTokenType(String name) {
    IElementType elementType = ourCompositeMap.get(name);
    if (elementType == null) {
      if (name.equals("JAVA_STATEMENT")) {
        elementType = new DroolsJavaStatementLazyParseableElementType();
      }
      else if (name.equals("BLOCK_EXPRESSION")) {
        elementType = new DroolsBlockExpressionsLazyParseableElementType();
      }
      else if ("true".equals(name) || "false".equals(name)) {
        elementType = new DroolsElementType(StringUtil.toUpperCase(name));
      }
      else if ("==".equals(name)) {
        elementType = new DroolsElementType("EQ");
      }
      else {
        elementType = new DroolsElementType(name);
      }

      ourCompositeMap.put(name, elementType);
    }
    return elementType;
  }
}
