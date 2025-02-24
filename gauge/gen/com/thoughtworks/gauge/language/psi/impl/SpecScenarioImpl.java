// This is a generated file. Not intended for manual editing.
package com.thoughtworks.gauge.language.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.thoughtworks.gauge.language.psi.SpecScenario;
import com.thoughtworks.gauge.language.psi.SpecStep;
import com.thoughtworks.gauge.language.psi.SpecTable;
import com.thoughtworks.gauge.language.psi.SpecTags;
import com.thoughtworks.gauge.language.psi.SpecVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpecScenarioImpl extends ASTWrapperPsiElement implements SpecScenario {

    public SpecScenarioImpl(ASTNode node) {
        super(node);
    }

    public void accept(@NotNull PsiElementVisitor visitor) {
        if (visitor instanceof SpecVisitor) ((SpecVisitor) visitor).visitScenario(this);
        else super.accept(visitor);
    }

    @Override
    @NotNull
    public List<SpecStep> getStepList() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, SpecStep.class);
    }

    @Override
    @Nullable
    public SpecTable getTable() {
        return findChildByClass(SpecTable.class);
    }

    @Override
    @Nullable
    public SpecTags getTags() {
        return findChildByClass(SpecTags.class);
    }

}
