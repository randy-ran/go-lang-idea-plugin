/*
 * Copyright 2013-2015 Sergey Ignatov, Alexander Zolotov, Mihai Toader, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.completion;

import com.goide.psi.*;
import com.goide.psi.impl.GoReference;
import com.goide.psi.impl.GoScopeProcessor;
import com.goide.psi.impl.GoTypeReference;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GoReferenceCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet set) {
    final GoReferenceExpressionBase expression = PsiTreeUtil.getParentOfType(parameters.getPosition(), GoReferenceExpressionBase.class);
    if (expression != null) {
      fillVariantsByReference(expression.getReference(), set);
    }
  }

  private static void fillVariantsByReference(@Nullable PsiReference reference, @NotNull final CompletionResultSet result) {
    if (reference == null) return;
    if (reference instanceof PsiMultiReference) {
      PsiReference[] references = ((PsiMultiReference)reference).getReferences();
      ContainerUtil.sort(references, PsiMultiReference.COMPARATOR);
      fillVariantsByReference(ArrayUtil.getFirstElement(references), result);
    }
    else if (reference instanceof GoReference) {
      ((GoReference)reference).processResolveVariants(new MyGoScopeProcessor(result, false));
    }
    else if (reference instanceof GoTypeReference) {
      PsiElement element = reference.getElement();
      final PsiElement spec = PsiTreeUtil.getParentOfType(element, GoFieldDeclaration.class, GoTypeSpec.class);
      final boolean insideParameter = PsiTreeUtil.getParentOfType(element, GoParameterDeclaration.class) != null;
      ((GoTypeReference)reference).processResolveVariants(new MyGoScopeProcessor(result, true) {
        @Override
        protected boolean accept(@NotNull PsiElement e) {
          return e != spec &&
                 !(insideParameter &&
                   (e instanceof GoNamedSignatureOwner || e instanceof GoVarDefinition || e instanceof GoConstDefinition));
        }
      });
      if (element instanceof GoReferenceExpressionBase && element.getParent() instanceof GoReceiverType) {
        fillVariantsByReference(new GoReference((GoReferenceExpressionBase)element), result);
      }
    }
  }

  private static void addElement(@NotNull PsiElement o, @NotNull ResolveState state, boolean forTypes, @NotNull CompletionResultSet set) {
    if (o instanceof GoNamedElement && !((GoNamedElement)o).isBlank() || o instanceof GoImportSpec && !((GoImportSpec)o).isDot()) {
      LookupElement lookup;
      if (o instanceof GoImportSpec) {
        lookup = GoCompletionUtil.createPackageLookupElement(((GoImportSpec)o), state.get(GoReference.ACTUAL_NAME));
      }
      else if (o instanceof GoNamedSignatureOwner) {
        lookup = GoCompletionUtil.createFunctionOrMethodLookupElement((GoNamedSignatureOwner)o);
      }
      else if (o instanceof GoTypeSpec) {
        lookup = forTypes
                 ? GoCompletionUtil.createTypeLookupElement((GoTypeSpec)o)
                 : GoCompletionUtil.createTypeConversionLookupElement((GoTypeSpec)o);
      }
      else if (o instanceof PsiDirectory) {
        lookup = GoCompletionUtil.createPackageLookupElement(((PsiDirectory)o).getName(), o, true);
      }
      else {
        lookup = GoCompletionUtil.createVariableLikeLookupElement((GoNamedElement)o);
      }

      if (lookup != null) {
        set.addElement(lookup);
      }
    }
  }

  private static class MyGoScopeProcessor extends GoScopeProcessor {
    private final CompletionResultSet myResult;
    private final boolean myForTypes;

    public MyGoScopeProcessor(@NotNull CompletionResultSet result, boolean forTypes) {
      myResult = result;
      myForTypes = forTypes;
    }

    @Override
    public boolean execute(@NotNull PsiElement o, @NotNull ResolveState state) {
      if (accept(o)) {
        addElement(o, state, myForTypes, myResult);
      }
      return true;
    }

    protected boolean accept(@NotNull PsiElement e) {
      return true;
    }

    @Override
    public boolean isCompletion() {
      return true;
    }
  }
}
                                                      