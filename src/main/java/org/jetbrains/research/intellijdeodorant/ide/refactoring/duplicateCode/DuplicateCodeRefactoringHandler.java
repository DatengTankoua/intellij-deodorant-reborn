package org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode;

import static org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.RefactoringLogger.*;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.intellijdeodorant.IntelliJDeodorantBundle;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeFragment;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeGroup;

import java.util.*;

/**
 * Handler for refactoring duplicated code fragments.
 * 
 * Supports three strategies:
 * 1. WITHIN-CLASS: Extract to method in same class
 * 2. CROSS-CLASS WITH SUPERCLASS: Pull up to common superclass
 * 3. CROSS-CLASS WITHOUT HIERARCHY: Move to utility class
 */
public class DuplicateCodeRefactoringHandler {
    
    private final Project project;
    
    public DuplicateCodeRefactoringHandler(@NotNull Project project) {
        this.project = project;
    }
    
    /**
     * Performs refactoring for a duplicate code group.
     */
    public void performRefactoring(@NotNull DuplicateCodeGroup group) {
        if (group.getFragments().isEmpty()) {
            showError("No duplicates found to refactor");
            return;
        }
        
        RefactoringContext context = ReadAction.compute(() -> analyzeGroup(group));
        
        if (context == null) {
            showError("Could not analyze duplicates");
            return;
        }
        
        RefactoringStrategy strategy = determineStrategy(context);
        
        if (!showConfirmationDialog(context, strategy)) {
            return;
        }
        
        ApplicationManager.getApplication().invokeLater(() -> {
            executeExtractMethod(context, strategy);
        });
    }
    
    /**
     * Analyzes duplicate code group. Must be called in ReadAction.
     */
    @Nullable
    private RefactoringContext analyzeGroup(@NotNull DuplicateCodeGroup group) {
        List<DuplicateCodeFragment> fragments = group.getFragments();
        
        Set<PsiClass> affectedClasses = new HashSet<>();
        Map<DuplicateCodeFragment, PsiClass> fragmentToClass = new HashMap<>();
        
        for (DuplicateCodeFragment fragment : fragments) {
            PsiElement element = fragment.getPsiElement();
            if (element == null) {
                return null;
            }
            
            PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (containingClass == null) {
                return null;
            }
            
            affectedClasses.add(containingClass);
            fragmentToClass.put(fragment, containingClass);
        }
        
        RefactoringContext context = new RefactoringContext();
        context.group = group;
        context.fragments = fragments;
        context.affectedClasses = new ArrayList<>(affectedClasses);
        context.fragmentToClass = fragmentToClass;
        context.isSameClass = affectedClasses.size() == 1;
        
        if (!context.isSameClass) {
            context.commonSuperClass = findCommonSuperClass(context.affectedClasses);
        }
        
        return context;
    }
    
    /**
     * Finds common superclass for multiple classes.
     */
    @Nullable
    private PsiClass findCommonSuperClass(@NotNull List<PsiClass> classes) {
        if (classes.isEmpty()) {
            return null;
        }
        
        if (classes.size() == 1) {
            return classes.get(0).getSuperClass();
        }
        
        PsiClass first = classes.get(0);
        
        List<PsiClass> superClasses = new ArrayList<>();
        PsiClass current = first.getSuperClass();
        while (current != null && !current.getQualifiedName().equals("java.lang.Object")) {
            superClasses.add(current);
            current = current.getSuperClass();
        }
        
        for (PsiClass superClass : superClasses) {
            boolean isCommonForAll = true;
            
            for (int i = 1; i < classes.size(); i++) {
                if (!InheritanceUtil.isInheritorOrSelf(classes.get(i), superClass, true)) {
                    isCommonForAll = false;
                    break;
                }
            }
            
            if (isCommonForAll) {
                return superClass;
            }
        }
        
        return null;
    }
    
    /**
     * Determines refactoring strategy based on context.
     */
    @NotNull
    private RefactoringStrategy determineStrategy(@NotNull RefactoringContext context) {
        if (context.isSameClass) {
            return RefactoringStrategy.WITHIN_CLASS;
        }
        
        if (context.commonSuperClass != null) {
            return RefactoringStrategy.PULL_UP_TO_SUPERCLASS;
        }
        
        return RefactoringStrategy.CREATE_UTILITY_CLASS;
    }
    
    /**
     * Shows confirmation dialog with refactoring details.
     */
    private boolean showConfirmationDialog(@NotNull RefactoringContext context, 
                                          @NotNull RefactoringStrategy strategy) {
        String message = buildConfirmationMessage(context, strategy);
        
        int result = Messages.showOkCancelDialog(
            project,
            message,
            "Extract Method Refactoring",
            "Proceed",
            "Cancel",
            Messages.getQuestionIcon()
        );
        
        return result == Messages.OK;
    }
    
    /**
     * Builds confirmation message.
     */
    @NotNull
    private String buildConfirmationMessage(@NotNull RefactoringContext context, 
                                           @NotNull RefactoringStrategy strategy) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Extract Method Refactoring for Duplicated Code\n\n");
        sb.append("Strategy: ").append(strategy.getDescription()).append("\n\n");
        
        sb.append("Duplicates: ").append(context.fragments.size()).append("\n");
        sb.append("Tokens: ").append(context.group.getTokens()).append("\n");
        sb.append("Avg Lines: ").append((int) context.group.getAverageLines()).append("\n\n");
        
        if (context.isSameClass) {
            sb.append("Class: ").append(context.affectedClasses.get(0).getName()).append("\n");
        } else {
            sb.append("Affected Classes:\n");
            for (PsiClass cls : context.affectedClasses) {
                sb.append("  • ").append(cls.getName()).append("\n");
            }
            
            if (context.commonSuperClass != null) {
                sb.append("\nTarget Super Class: ").append(context.commonSuperClass.getName()).append("\n");
            }
        }
        
        sb.append("\nThe Extract Method dialog will open where you can:\n");
        sb.append("• Configure the method name\n");
        sb.append("• Review and adjust parameters\n");
        sb.append("• Set the return type\n");
        sb.append("• Preview changes before applying\n\n");
        sb.append("Continue with refactoring?");
        
        return sb.toString();
    }
    
    /**
     * Executes extract method refactoring.
     */
    private void executeExtractMethod(@NotNull RefactoringContext context, 
                                      @NotNull RefactoringStrategy strategy) {
        
        if (strategy == RefactoringStrategy.WITHIN_CLASS) {
            // WITHIN-CLASS: Normale Extract Method
            executeExtractMethodNormal(context, strategy);
        } else {
            // CROSS-CLASS: Verwende temporäre Klasse für Accept Signature Dialog
            executeExtractMethodViaTempClass(context, strategy);
        }
    }
    
    /**
     * Normal extract method for within-class duplicates.
     */
    private void executeExtractMethodNormal(@NotNull RefactoringContext context,
                                           @NotNull RefactoringStrategy strategy) {
        DuplicateCodeFragment firstFragment = context.fragments.get(0);
        
        ReadAction.run(() -> {
            PsiElement[] statements = firstFragment.getStatements();
            
            if (statements == null || statements.length == 0) {
                showError("No statements found to extract");
                return;
            }
            
            PsiClass sourceClass = PsiTreeUtil.getParentOfType(statements[0], PsiClass.class);
            if (sourceClass == null) {
                showError("Could not find containing class");
                return;
            }
            
            PsiFile file = firstFragment.getFile();
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            
            ExtractMethodProcessor processor = new ExtractMethodProcessor(
                project,
                editor,
                statements,
                null,
                IntelliJDeodorantBundle.message("duplicated.code.refactoring.name"),
                "",
                HelpID.EXTRACT_METHOD
            );
            
            processor.setTargetClass(sourceClass);
            
            try {
                processor.setShowErrorDialogs(true);
                if (processor.prepare()) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        ExtractMethodHandler.invokeOnElements(project, processor, file, true);
                    });
                } else {
                    showError("Cannot prepare extract method refactoring. The code may have compilation errors.");
                }
            } catch (PrepareFailedException e) {
                showError("Failed to prepare refactoring: " + e.getMessage());
            }
        });
    }
    
    /**
     * Extract method via temporary class for cross-class duplicates.
     * Uses temp class to enable IntelliJ's Accept Signature dialog.
     */
    private void executeExtractMethodViaTempClass(@NotNull RefactoringContext context,
                                                  @NotNull RefactoringStrategy strategy) {
        debug("\n=== EXTRACT METHOD VIA TEMP CLASS ===");
        debug("Creating temporary class for duplicate detection...");
        
        WriteCommandAction.runWriteCommandAction(project, "Create Temp Class for Extraction", null, () -> {
            try {
                PsiClass tempClass = createTemporaryClass(context);
                if (tempClass == null) {
                    showError("Could not create temporary class");
                    return;
                }
                debug("Temporary class created: " + tempClass.getName());
                
                List<PsiMethod> tempMethods = new ArrayList<>();
                Map<String, PsiClass> tempMethodToOriginClass = new HashMap<>();
                int methodIndex = 0;
                
                for (DuplicateCodeFragment fragment : context.fragments) {
                    PsiMethod originalMethod = null;
                    List<PsiStatement> statements = new ArrayList<>();
                    
                    PsiElement element = fragment.getPsiElement();
                    
                    if (element instanceof PsiMethod) {
                        originalMethod = (PsiMethod) element;
                        PsiCodeBlock body = originalMethod.getBody();
                        if (body != null) {
                            statements = Arrays.asList(body.getStatements());
                            debug("Fragment " + (methodIndex + 1) + " is a complete method: " + originalMethod.getName());
                        }
                    } else if (element instanceof PsiCodeBlock) {
                        PsiCodeBlock codeBlock = (PsiCodeBlock) element;
                        originalMethod = PsiTreeUtil.getParentOfType(codeBlock, PsiMethod.class);
                        
                        if (originalMethod != null) {
                            statements = Arrays.asList(codeBlock.getStatements());
                            debug("Fragment " + (methodIndex + 1) + " is a code block in method: " + originalMethod.getName());
                        } else {
                            statements = Arrays.asList(codeBlock.getStatements());
                            debug("Fragment " + (methodIndex + 1) + " is a code block (no parent method)");
                        }
                    } else {
                        statements = collectStatementsFromFragment(fragment);
                        
                        PsiFile file = fragment.getFile();
                        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                        if (document != null) {
                            int startLine = fragment.getStartLine() - 1;
                            int startOffset = document.getLineStartOffset(startLine);
                            PsiElement elementAtStart = file.findElementAt(startOffset);
                            if (elementAtStart != null) {
                                originalMethod = PsiTreeUtil.getParentOfType(elementAtStart, PsiMethod.class);
                            }
                        }
                        
                        debug("Fragment " + (methodIndex + 1) + " is a code fragment (lines " + 
                                         fragment.getStartLine() + "-" + fragment.getEndLine() + ")");
                        if (originalMethod != null) {
                            debug("  Found containing method: " + originalMethod.getName());
                        }
                    }
                    
                    if (statements.isEmpty()) {
                        error("No statements for fragment " + (methodIndex + 1));
                        continue;
                    }
                    debug("  Found " + statements.size() + " statements");
                    
                    String tempMethodName = "tempDuplicate" + (methodIndex + 1);
                    PsiMethod tempMethod;
                    
                    if (originalMethod != null) {
                        tempMethod = createTempMethodFromOriginal(tempClass, tempMethodName, originalMethod);
                    } else {
                        tempMethod = createTempMethod(tempClass, tempMethodName, statements);
                    }
                    
                    if (tempMethod != null) {
                        tempMethods.add(tempMethod);
                        debug("  Created temp method: " + tempMethodName);
                        PsiClass originClass = null;
                        if (originalMethod != null) {
                            originClass = originalMethod.getContainingClass();
                        }
                        if (originClass == null) {
                            originClass = context.fragmentToClass.get(fragment);
                        }
                        if (originClass != null) {
                            tempMethodToOriginClass.put(tempMethod.getName(), originClass);
                        }
                    }
                    
                    methodIndex++;
                }
                
                if (tempMethods.isEmpty()) {
                    showError("No temp methods created");
                    deleteTempClass(tempClass);
                    return;
                }
                debug("Total temp methods created: " + tempMethods.size());
                
                String tempClassName = tempClass.getQualifiedName();
                VirtualFile tempFileVirtual = tempClass.getContainingFile().getVirtualFile();
                
                ApplicationManager.getApplication().invokeLater(() -> {
                    ReadAction.run(() -> {
                        PsiFile resolvedTempFile = PsiManager.getInstance(project).findFile(tempFileVirtual);
                        if (resolvedTempFile == null) {
                            showError("Could not resolve temp file");
                            return;
                        }
                        
                        PsiClass resolvedTempClass = JavaPsiFacade.getInstance(project)
                            .findClass(tempClassName, GlobalSearchScope.allScope(project));
                        if (resolvedTempClass == null) {
                            showError("Could not resolve temp class");
                            return;
                        }
                        
                        PsiMethod[] methods = resolvedTempClass.getMethods();
                        if (methods.length == 0) {
                            showError("No methods found in temp class");
                            return;
                        }
                        
                        performExtractOnTempMethod(methods[0], resolvedTempClass, context, strategy, tempMethodToOriginClass);
                    });
                });
                
            } catch (Exception e) {
                showError("Failed to create temp class: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Creates temporary class for extraction.
     */
    @Nullable
    private PsiClass createTemporaryClass(@NotNull RefactoringContext context) {
        PsiClass firstClass = context.affectedClasses.get(0);
        PsiFile firstFile = firstClass.getContainingFile();
        
        if (!(firstFile instanceof PsiJavaFile)) {
            return null;
        }
        
        PsiJavaFile javaFile = (PsiJavaFile) firstFile;
        String packageName = javaFile.getPackageName();

        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        StringBuilder classContent = new StringBuilder();
        if (!packageName.isEmpty()) {
            classContent.append("package ").append(packageName).append("\n\n");
        }
        
        classContent.append("// TEMPORARY CLASS FOR DUPLICATE CODE EXTRACTION\n");
        classContent.append("// This class will be deleted after refactoring\n");
        classContent.append("class TempDuplicateExtraction {\n");
        classContent.append("}\n");
        
        PsiFile tempFile = PsiFileFactory.getInstance(project).createFileFromText(
            "TempDuplicateExtraction.java",
            JavaLanguage.INSTANCE,
            classContent.toString()
        );
        
        PsiFile addedFile = (PsiFile) firstFile.getContainingDirectory().add(tempFile);
        
        if (addedFile instanceof PsiJavaFile) {
            PsiClass[] classes = ((PsiJavaFile) addedFile).getClasses();
            if (classes.length > 0) {
                return classes[0];
            }
        }
        
        return null;
    }
    
    /**
     * Creates temp method in temp class with statements from fragment.
     */
    @Nullable
    private PsiMethod createTempMethod(@NotNull PsiClass tempClass,
                                      @NotNull String methodName,
                                      @NotNull List<PsiStatement> statements) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        StringBuilder methodText = new StringBuilder();
        methodText.append("void ").append(methodName).append("() {\n");
        
        for (PsiStatement statement : statements) {
            methodText.append("    ").append(statement.getText()).append("\n");
        }
        
        methodText.append("}");
        
        try {
            PsiMethod method = factory.createMethodFromText(methodText.toString(), tempClass);
            return (PsiMethod) tempClass.add(method);
        } catch (Exception e) {
            error("Failed to create temp method: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates temp method by copying original method with full signature.
     */
    @Nullable
    private PsiMethod createTempMethodFromOriginal(@NotNull PsiClass tempClass,
                                                   @NotNull String newName,
                                                   @NotNull PsiMethod originalMethod) {
        try {
            debug("  Copying original method: " + originalMethod.getName());
            debug("    Original signature: " + originalMethod.getSignature(PsiSubstitutor.EMPTY));
            debug("    Parameters: " + originalMethod.getParameterList().getParametersCount());
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiCodeBlock body = originalMethod.getBody();
            if (body == null) {
                error("Failed to copy original method: body is null");
                return null;
            }

            StringBuilder methodText = new StringBuilder();

            String modifiers = originalMethod.getModifierList().getText();
            if (!modifiers.isEmpty()) {
                methodText.append(modifiers).append(" ");
            }

            PsiType returnType = originalMethod.getReturnType();
            methodText.append(returnType != null ? returnType.getPresentableText() : "void");
            methodText.append(" ");
            methodText.append(newName);

            methodText.append("(");
            PsiParameter[] params = originalMethod.getParameterList().getParameters();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) methodText.append(", ");
                methodText.append(params[i].getType().getPresentableText())
                          .append(" ")
                          .append(params[i].getName());
            }
            methodText.append(") {\n");

            for (PsiStatement statement : body.getStatements()) {
                methodText.append("    ").append(statement.getText()).append("\n");
            }

            methodText.append("}");

            PsiMethod copiedMethod = factory.createMethodFromText(methodText.toString(), tempClass);
            
            debug("    New name: " + newName);
            debug("    Copied signature: " + copiedMethod.getSignature(PsiSubstitutor.EMPTY));
            PsiMethod addedMethod = (PsiMethod) tempClass.add(copiedMethod);
            
            debug("    Final signature in temp class: " + addedMethod.getSignature(PsiSubstitutor.EMPTY));
            debug("    Final parameters: " + addedMethod.getParameterList().getParametersCount());
            
            return addedMethod;
        } catch (Exception e) {
            error("Failed to copy original method: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Performs extract method on temp method. Analyzes differences for Type-2 clones.
     */
    private void performExtractOnTempMethod(@NotNull PsiMethod tempMethod,
                                           @NotNull PsiClass tempClass,
                                           @NotNull RefactoringContext context,
                                           @NotNull RefactoringStrategy strategy,
                                           @NotNull Map<String, PsiClass> tempMethodToOriginClass) {
        PsiCodeBlock body = tempMethod.getBody();
        if (body == null) {
            showError("Temp method has no body");
            return;
        }
        
        PsiStatement[] statements = body.getStatements();
        if (statements.length == 0) {
            showError("No statements in temp method");
            return;
        }
        
        PsiMethod[] allTempMethods = tempClass.getMethods();
        debug("\n=== TYPE-2 CLONE ANALYSIS ===");
        debug("Analyzing " + allTempMethods.length + " temp methods for differences...");
        List<VariableDifference> differences = findAllDifferences(allTempMethods);
        
        if (!differences.isEmpty()) {
            debug("✓ Detected " + differences.size() + " difference(s) - converting to parameters");
            for (VariableDifference diff : differences) {
                debug("  - Position " + diff.statementIndex + ": " + 
                     diff.variableNames.stream().reduce((a, b) -> a + ", " + b).orElse(""));
            }
            
            // SCHRITT 2: Konvertiere alle temp-Methoden durch Hinzufügen von Parametern
            boolean success = convertTempMethodsToParameterized(allTempMethods, differences, tempClass, tempMethodToOriginClass);
            
            if (!success) {
                showError("Failed to parametrize temp methods");
                deleteTempClass(tempClass);
                return;
            }
            debug("✓ All temp methods parametrized successfully");
            
            PsiMethod[] updatedMethods = tempClass.getMethods();
            if (updatedMethods.length > 0) {
                performStandardExtraction(updatedMethods[0], tempClass, context, strategy);
            }
        } else {
            debug("No differences detected - using standard IntelliJ duplicate detection");
            performStandardExtraction(tempMethod, tempClass, context, strategy);
        }
    }
    
    /**
     * Finds all differences between temp methods (statement-by-statement comparison).
     */
    @NotNull
    private List<VariableDifference> findAllDifferences(@NotNull PsiMethod[] methods) {
        List<VariableDifference> differences = new ArrayList<>();
        
        if (methods.length < 2) {
            return differences;
        }
        
        PsiMethod referenceMethod = methods[0];
        PsiStatement[] referenceStatements = referenceMethod.getBody().getStatements();
        
        for (int stmtIndex = 0; stmtIndex < referenceStatements.length; stmtIndex++) {
            PsiStatement refStmt = referenceStatements[stmtIndex];
            
            if (!(refStmt instanceof PsiDeclarationStatement)) {
                List<PsiLiteralExpression> refLiterals = getStringLiteralExpressions(refStmt);
                for (int litIndex = 0; litIndex < refLiterals.size(); litIndex++) {
                    PsiLiteralExpression refLit = refLiterals.get(litIndex);
                    String refText = refLit.getText();

                    Set<String> literalTexts = new HashSet<>();
                    literalTexts.add(refText);

                    boolean hasDifference = false;

                    for (int i = 1; i < methods.length; i++) {
                        PsiStatement[] otherStatements = methods[i].getBody().getStatements();
                        if (stmtIndex >= otherStatements.length) {
                            hasDifference = true;
                            continue;
                        }

                        List<PsiLiteralExpression> otherLiterals = getStringLiteralExpressions(otherStatements[stmtIndex]);
                        if (litIndex >= otherLiterals.size()) {
                            hasDifference = true;
                            continue;
                        }

                        String otherText = otherLiterals.get(litIndex).getText();
                        literalTexts.add(otherText);

                        if (!refText.equals(otherText)) {
                            hasDifference = true;
                        }
                    }

                    if (hasDifference || literalTexts.size() > 1) {
                        debug("  Skipping string literal value difference at statement " + stmtIndex +
                            ", literalIndex " + litIndex + ": " + literalTexts);
                    }
                }

                continue;
            }
            
            PsiDeclarationStatement refDecl = (PsiDeclarationStatement) refStmt;
            PsiElement[] refElements = refDecl.getDeclaredElements();
            
            if (refElements.length == 0 || !(refElements[0] instanceof PsiLocalVariable)) {
                continue;
            }
            
            PsiLocalVariable refVar = (PsiLocalVariable) refElements[0];
            String refVarName = refVar.getName();
            PsiType refType = refVar.getType();
            String refInitializer = refVar.getInitializer() != null ? 
                refVar.getInitializer().getText() : null;
            
            // Vergleiche mit allen anderen Methoden am gleichen Statement-Index
            Set<String> differentTypes = new HashSet<>();
            Set<String> differentInitializers = new HashSet<>();
            Set<String> variableNames = new HashSet<>();
            
            differentTypes.add(refType.getPresentableText());
            if (refInitializer != null) {
                differentInitializers.add(refInitializer);
            }
            variableNames.add(refVarName);
            
            boolean hasTypeDifference = false;
            
            for (int i = 1; i < methods.length; i++) {
                PsiStatement[] otherStatements = methods[i].getBody().getStatements();
                
                if (stmtIndex >= otherStatements.length) {
                    continue;
                }
                
                PsiStatement otherStmt = otherStatements[stmtIndex];
                
                if (!(otherStmt instanceof PsiDeclarationStatement)) {
                    continue;
                }
                
                PsiDeclarationStatement otherDecl = (PsiDeclarationStatement) otherStmt;
                PsiElement[] otherElements = otherDecl.getDeclaredElements();
                
                if (otherElements.length == 0 || !(otherElements[0] instanceof PsiLocalVariable)) {
                    continue;
                }
                
                PsiLocalVariable otherVar = (PsiLocalVariable) otherElements[0];
                String otherVarName = otherVar.getName();
                PsiType otherType = otherVar.getType();
                String otherInitializer = otherVar.getInitializer() != null ? 
                    otherVar.getInitializer().getText() : null;
                
                variableNames.add(otherVarName);
                differentTypes.add(otherType.getPresentableText());
                
                if (otherInitializer != null) {
                    differentInitializers.add(otherInitializer);
                }
                
                // Prüfe ob Typ unterschiedlich ist (Initializer-Differenzen ignorieren)
                if (!refType.equals(otherType)) {
                    hasTypeDifference = true;
                }
            }
            
            boolean typeDifference = differentTypes.size() > 1;
            boolean initializerDifference = differentInitializers.size() > 1;

            // Wenn Unterschiede gefunden, erstelle VariableDifference
            // WICHTIG: Nur Typ-Unterschiede parametrisieren; Wert-Unterschiede ignorieren.
            if (hasTypeDifference || typeDifference) {
                if (!typeDifference) {
                    debug("  Skipping value-only difference at statement " + stmtIndex +
                        " (same type: " + refType.getPresentableText() + ")");
                    continue;
                }

                VariableDifference diff = new VariableDifference();
                diff.kind = VariableDifferenceKind.VARIABLE_DECLARATION;
                diff.statementIndex = stmtIndex;
                diff.variableNames = new ArrayList<>(variableNames);
                diff.types = new ArrayList<>(differentTypes);
                diff.initializers = new ArrayList<>(differentInitializers);
                diff.parameterName = "param" + (differences.size() + 1);
                diff.literalIndex = -1;
                
                // Bestimme gemeinsamen Typ (Object wenn unterschiedlich)
                if (typeDifference) {
                    diff.commonType = PsiType.getJavaLangObject(
                        PsiManager.getInstance(project), 
                        GlobalSearchScope.allScope(project)
                    );
                } else {
                    diff.commonType = refType;
                }
                
                differences.add(diff);
                
                debug("  Found difference at statement " + stmtIndex + ":");
                debug("    Variable names: " + variableNames);
                debug("    Types: " + differentTypes);
                if (initializerDifference) {
                    debug("    Initializers: " + differentInitializers);
                }
                debug("    Will use parameter: " + diff.parameterName + " (" + 
                     diff.commonType.getPresentableText() + ")");
            }
        }
        
        return differences;
    }

    /**
     * Liefert alle String-Literale in einem Statement in der Reihenfolge ihres Auftretens.
     */
    @NotNull
    private List<PsiLiteralExpression> getStringLiteralExpressions(@NotNull PsiStatement statement) {
        List<PsiLiteralExpression> literals = new ArrayList<>();
        Collection<PsiLiteralExpression> allLiterals = PsiTreeUtil.findChildrenOfType(statement, PsiLiteralExpression.class);
        for (PsiLiteralExpression literal : allLiterals) {
            if (literal.getValue() instanceof String) {
                literals.add(literal);
            }
        }
        literals.sort(Comparator.comparingInt(l -> l.getTextRange().getStartOffset()));
        return literals;
    }

    /**
     * Sammelt Feld-Referenzen (z. B. baseStr, base) die im Methodenkörper verwendet werden,
     * aber nicht als lokale Variablen oder Parameter deklariert sind.
     */
    @NotNull
    private LinkedHashMap<String, PsiType> collectFieldParameters(@NotNull PsiMethod method,
                                                                  @Nullable PsiClass originClass) {
        LinkedHashMap<String, PsiType> result = new LinkedHashMap<>();

        Set<String> localNames = new HashSet<>();
        for (PsiParameter param : method.getParameterList().getParameters()) {
            localNames.add(param.getName());
        }
        for (PsiLocalVariable local : PsiTreeUtil.findChildrenOfType(method, PsiLocalVariable.class)) {
            localNames.add(local.getName());
        }

        PsiType objectType = PsiType.getJavaLangObject(
            PsiManager.getInstance(project),
            GlobalSearchScope.allScope(project)
        );

        for (PsiReferenceExpression ref : PsiTreeUtil.findChildrenOfType(method, PsiReferenceExpression.class)) {
            // Überspringe Methodennamen in Aufrufen
            if (ref.getParent() instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression call = (PsiMethodCallExpression) ref.getParent();
                if (call.getMethodExpression() == ref) {
                    continue;
                }
            }

            PsiExpression qualifier = ref.getQualifierExpression();
            if (qualifier != null) {
                if (!(qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression)) {
                    continue;
                }
            }

            String name = ref.getReferenceName();
            if (name == null || name.equals("this") || name.equals("super")) {
                continue;
            }
            if (localNames.contains(name) || result.containsKey(name)) {
                continue;
            }

            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                result.put(name, field.getType());
            } else if (resolved == null) {
                if (originClass != null) {
                    PsiField originField = findFieldInOriginClass(originClass, name);
                    if (originField != null) {
                        result.put(name, originField.getType());
                        continue;
                    }
                }
                result.put(name, objectType);
            }
        }

        return result;
    }

    /**
     * Finds field in origin class without index access to avoid PSI/index mismatch.
     */
    @Nullable
    private PsiField findFieldInOriginClass(@NotNull PsiClass originClass, @NotNull String name) {
        try {
            for (PsiField field : originClass.getFields()) {
                if (name.equals(field.getName())) {
                    return field;
                }
            }
        } catch (Throwable t) {
            debug("Field lookup skipped due to PSI/index mismatch: " + t.getMessage());
        }
        return null;
    }
    
    /**
     * Converts all temp methods by adding parameters for differences.
     */
    private boolean convertTempMethodsToParameterized(@NotNull PsiMethod[] methods,
                                                      @NotNull List<VariableDifference> differences,
                                                      @NotNull PsiClass tempClass,
                                                      @NotNull Map<String, PsiClass> tempMethodToOriginClass) {
        debug("\n=== CONVERTING TO PARAMETRIZED METHODS ===");
        
        try {
            WriteCommandAction.runWriteCommandAction(project, "Parametrize Temp Methods", null, () -> {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                
                for (int methodIndex = 0; methodIndex < methods.length; methodIndex++) {
                    PsiMethod method = methods[methodIndex];
                    PsiCodeBlock body = method.getBody();
                    
                    if (body == null) {
                        continue;
                    }
                    
                    debug("Processing " + method.getName() + ":");
                    
                    PsiStatement[] statements = body.getStatements();
                    
                    List<String> parameterDeclarations = new ArrayList<>();
                    List<String> parameterNames = new ArrayList<>();
                    Map<Integer, String> statementIndexToValue = new HashMap<>();

                    PsiParameter[] existingParams = method.getParameterList().getParameters();
                    for (PsiParameter param : existingParams) {
                        String decl = param.getType().getPresentableText() + " " + param.getName();
                        if (!parameterDeclarations.contains(decl)) {
                            parameterDeclarations.add(decl);
                            parameterNames.add(param.getName());
                            debug("  Existing param kept: " + decl);
                        }
                    }

                    PsiClass originClass = tempMethodToOriginClass.get(method.getName());
                    LinkedHashMap<String, PsiType> fieldParams = collectFieldParameters(method, originClass);
                    for (Map.Entry<String, PsiType> entry : fieldParams.entrySet()) {
                        String decl = entry.getValue().getPresentableText() + " " + entry.getKey();
                        if (!parameterDeclarations.contains(decl)) {
                            parameterDeclarations.add(decl);
                            parameterNames.add(entry.getKey());
                            debug("  Field param added: " + decl);
                        }
                    }
                    
                    debug("  Differences to apply: " + differences.size());
                    for (VariableDifference diff : differences) {
                        if (diff.kind == VariableDifferenceKind.VARIABLE_DECLARATION) {
                            if (diff.types == null || diff.types.size() <= 1) {
                                debug("  Skip diff (no type change) at stmt " + diff.statementIndex +
                                    ", types=" + diff.types + ", initializers=" + diff.initializers);
                                continue;
                            }
                            if (diff.statementIndex >= statements.length) {
                                continue;
                            }

                            PsiStatement stmt = statements[diff.statementIndex];

                            if (stmt instanceof PsiDeclarationStatement) {
                                PsiDeclarationStatement declStmt = (PsiDeclarationStatement) stmt;
                                PsiElement[] elements = declStmt.getDeclaredElements();

                                if (elements.length > 0 && elements[0] instanceof PsiLocalVariable) {
                                    PsiLocalVariable var = (PsiLocalVariable) elements[0];
                                    String value = var.getInitializer() != null ?
                                        var.getInitializer().getText() : "null";

                                    statementIndexToValue.put(diff.statementIndex, value);

                                    parameterDeclarations.add(
                                        diff.commonType.getPresentableText() + " " + diff.parameterName
                                    );
                                    parameterNames.add(diff.parameterName);

                                    debug("  Param " + diff.parameterName + " = " + value +
                                        " (types=" + diff.types + ")");
                                }
                            }
                        } else if (diff.kind == VariableDifferenceKind.STRING_LITERAL) {
                            parameterDeclarations.add(
                                diff.commonType.getPresentableText() + " " + diff.parameterName
                            );
                            parameterNames.add(diff.parameterName);
                            debug("  Param " + diff.parameterName + " = <string literal>");
                        }
                    }
                    
                    StringBuilder newMethodText = new StringBuilder();
                    newMethodText.append("void ").append(method.getName()).append("(");
                    
                    for (int i = 0; i < parameterDeclarations.size(); i++) {
                        if (i > 0) newMethodText.append(", ");
                        newMethodText.append(parameterDeclarations.get(i));
                    }
                    
                    newMethodText.append(") {\n");
                    
                    for (int stmtIndex = 0; stmtIndex < statements.length; stmtIndex++) {
                        PsiStatement stmt = statements[stmtIndex];
                        
                        VariableDifference matchingDeclDiff = null;
                        for (VariableDifference diff : differences) {
                            if (diff.kind == VariableDifferenceKind.VARIABLE_DECLARATION && diff.statementIndex == stmtIndex) {
                                if (diff.types == null || diff.types.size() <= 1) {
                                    debug("  Not replacing decl at stmt " + stmtIndex +
                                        " (no type change). types=" + diff.types +
                                        ", initializers=" + diff.initializers);
                                    continue;
                                }
                                matchingDeclDiff = diff;
                                break;
                            }
                        }

                        if (matchingDeclDiff != null && stmt instanceof PsiDeclarationStatement) {
                            PsiDeclarationStatement declStmt = (PsiDeclarationStatement) stmt;
                            PsiElement[] elements = declStmt.getDeclaredElements();

                            if (elements.length > 0 && elements[0] instanceof PsiLocalVariable) {
                                PsiLocalVariable var = (PsiLocalVariable) elements[0];
                                String originalVarName = var.getName();
                                String paramName = matchingDeclDiff.parameterName;
                                debug("  Replacing decl at stmt " + stmtIndex +
                                    ": " + var.getType().getPresentableText() + " " + originalVarName +
                                    " -> param " + paramName + ", types=" + matchingDeclDiff.types);

                                if (matchingDeclDiff.commonType.equals(PsiType.getJavaLangObject(
                                    PsiManager.getInstance(project), GlobalSearchScope.allScope(project)))) {
                                    newMethodText.append("    ")
                                        .append("Object ")
                                        .append(originalVarName).append(" = ")
                                        .append(paramName).append(";\n");
                                } else {
                                    String typeText = var.getType().getPresentableText();
                                    newMethodText.append("    ")
                                        .append(typeText).append(" ")
                                        .append(originalVarName).append(" = ")
                                        .append(paramName).append(";\n");
                                }

                                debug("  Replaced declaration: " + originalVarName + " = " + paramName);
                            }
                        } else {
                            if (stmt instanceof PsiDeclarationStatement) {
                                debug("  Keeping declaration at stmt " + stmtIndex + ": " + stmt.getText());
                            }
                            String stmtText = stmt.getText();
                            List<PsiLiteralExpression> literals = getStringLiteralExpressions(stmt);
                            List<VariableDifference> literalDiffs = new ArrayList<>();
                            for (VariableDifference diff : differences) {
                                if (diff.kind == VariableDifferenceKind.STRING_LITERAL && diff.statementIndex == stmtIndex) {
                                    literalDiffs.add(diff);
                                }
                            }

                            if (!literalDiffs.isEmpty() && !literals.isEmpty()) {
                                literalDiffs.sort(Comparator.comparingInt(d -> d.literalIndex));
                                StringBuilder stmtBuilder = new StringBuilder(stmtText);
                                TextRange stmtRange = stmt.getTextRange();

                                for (int i = literalDiffs.size() - 1; i >= 0; i--) {
                                    VariableDifference diff = literalDiffs.get(i);
                                    if (diff.literalIndex < 0 || diff.literalIndex >= literals.size()) {
                                        continue;
                                    }
                                    PsiLiteralExpression literal = literals.get(diff.literalIndex);
                                    TextRange litRange = literal.getTextRange();
                                    int relStart = litRange.getStartOffset() - stmtRange.getStartOffset();
                                    int relEnd = litRange.getEndOffset() - stmtRange.getStartOffset();
                                    if (relStart >= 0 && relEnd <= stmtBuilder.length()) {
                                        stmtBuilder.replace(relStart, relEnd, diff.parameterName);
                                        debug("  Replaced string literal with parameter: " + diff.parameterName);
                                    }
                                }

                                stmtText = stmtBuilder.toString();
                            }

                            newMethodText.append("    ").append(stmtText).append("\n");
                        }
                    }
                    
                    newMethodText.append("}");
                    debug("New method signature: " + newMethodText.substring(0, 
                        Math.min(100, newMethodText.length())) + "...");
                    
                    PsiMethod newMethod = factory.createMethodFromText(newMethodText.toString(), tempClass);
                    method.replace(newMethod);
                    
                    debug("✓ Converted " + method.getName());
                }
            });
            
            return true;
            
        } catch (Exception e) {
            error("Failed to convert temp methods: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    
    /**
     * Standard extraction for Type-1 clones (identical duplicates).
     */
    private void performStandardExtraction(@NotNull PsiMethod tempMethod,
                                          @NotNull PsiClass tempClass,
                                          @NotNull RefactoringContext context,
                                          @NotNull RefactoringStrategy strategy) {
        PsiCodeBlock body = tempMethod.getBody();
        if (body == null) {
            showError("Temp method has no body");
            return;
        }
        
        PsiStatement[] statements = body.getStatements();
        if (statements.length == 0) {
            showError("No statements in temp method");
            return;
        }
        
        PsiFile tempFile = tempClass.getContainingFile();
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        
        ExtractMethodProcessor processor = new ExtractMethodProcessor(
            project,
            editor,
            statements,
            null,
            IntelliJDeodorantBundle.message("duplicated.code.refactoring.name"),
            "",
            HelpID.EXTRACT_METHOD
        );
        
        processor.setTargetClass(tempClass);
        
        try {
            processor.setShowErrorDialogs(true);
            if (processor.prepare()) {
                debug("Extract Method prepared - showing dialog...");
                
                ApplicationManager.getApplication().invokeLater(() -> {
                    ExtractMethodHandler.invokeOnElements(
                        project,
                        processor,
                        tempFile,
                        true
                    );
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        applyExtractedMethodToRealClasses(processor, tempClass, context, strategy);
                    }, com.intellij.openapi.application.ModalityState.NON_MODAL);
                });
            } else {
                showError("Cannot prepare extract method on temp class");
                deleteTempClass(tempClass);
            }
        } catch (PrepareFailedException e) {
            showError("Failed to prepare extraction: " + e.getMessage());
            deleteTempClass(tempClass);
        }
    }
    
    /**
     * Applies extracted method to real classes.
     */
    private void applyExtractedMethodToRealClasses(@NotNull ExtractMethodProcessor processor,
                                                   @NotNull PsiClass tempClass,
                                                   @NotNull RefactoringContext context,
                                                   @NotNull RefactoringStrategy strategy) {
        ReadAction.run(() -> {
            PsiMethod extractedMethod = processor.getExtractedMethod();
            if (extractedMethod == null) {
                debug("Extract Method was cancelled");
                deleteTempClass(tempClass);
                return;
            }
            
            debug("\n=== APPLYING TO REAL CLASSES ===");
            debug("Extracted method: " + extractedMethod.getName());
            debug("Signature: " + extractedMethod.getSignature(PsiSubstitutor.EMPTY));
            
            PsiMethod methodCopy = (PsiMethod) extractedMethod.copy();
            
            debug("Created independent copy of extracted method");
            
            deleteTempClass(tempClass);
            
            applyCrossFileStrategyWithSignature(methodCopy, context, strategy);
        });
    }
    
    /**
     * Deletes temporary class.
     */
    private void deleteTempClass(@NotNull PsiClass tempClass) {
        WriteCommandAction.runWriteCommandAction(project, "Delete Temp Class", null, () -> {
            try {
                PsiFile tempFile = tempClass.getContainingFile();
                if (tempFile != null) {
                    tempFile.delete();
                    debug("Temporary class deleted");
                }
            } catch (Exception e) {
                error("Failed to delete temp class: " + e.getMessage());
            }
        });
    }
    
    /**
     * Applies cross-file strategy with IntelliJ-optimized signature.
     */
    private void applyCrossFileStrategyWithSignature(@NotNull PsiMethod extractedMethod,
                                                     @NotNull RefactoringContext context,
                                                     @NotNull RefactoringStrategy strategy) {
        debug("\n=== APPLYING CROSS-FILE STRATEGY WITH SIGNATURE ===");
        debug("Extracted method: " + extractedMethod.getName());
        debug("Signature: " + extractedMethod.getSignature(PsiSubstitutor.EMPTY));
        
        if (strategy == RefactoringStrategy.PULL_UP_TO_SUPERCLASS && context.commonSuperClass != null) {
            debug("Target: Pull up to " + context.commonSuperClass.getQualifiedName());
            
            WriteCommandAction.runWriteCommandAction(project, "Pull Up Method to Superclass", null, () -> {
                try {
                    PsiMethod copiedMethod = (PsiMethod) extractedMethod.copy();
                    PsiMethod movedMethod = (PsiMethod) context.commonSuperClass.add(copiedMethod);
                    
                    PsiModifierList modifierList = movedMethod.getModifierList();
                    modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
                    modifierList.setModifierProperty(PsiModifier.PROTECTED, true);
                    
                    addMissingImportsFromFragments(context, movedMethod);
                    JavaCodeStyleManager.getInstance(project).shortenClassReferences(movedMethod);
                    debug("Method successfully copied to superclass");
                    
                    replaceOriginalMethodsWithCalls(context, movedMethod, false);
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        showInfo(
                            "Pull Up to Superclass Successful\n\n" +
                            "Method: " + movedMethod.getName() + "\n" +
                            "Target: " + context.commonSuperClass.getQualifiedName() + "\n\n" +
                            "All duplicate methods have been replaced with calls."
                        );
                    });
                    
                } catch (Exception e) {
                    showError("Failed to pull up method: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
        } else if (strategy == RefactoringStrategy.CREATE_UTILITY_CLASS) {
            debug("Target: Move to utility class");
            
            WriteCommandAction.runWriteCommandAction(project, "Move to Utility Class", null, () -> {
                try {
                    PsiClass utilityClass = getOrCreateUtilityClass(context);
                    if (utilityClass == null) {
                        showError("Could not create utility class");
                        return;
                    }
                    
                    PsiMethod copiedMethod = (PsiMethod) extractedMethod.copy();
                    PsiMethod movedMethod = (PsiMethod) utilityClass.add(copiedMethod);
                    
                    PsiModifierList modifierList = movedMethod.getModifierList();
                    modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
                    modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
                    modifierList.setModifierProperty(PsiModifier.STATIC, true);
                    
                    addMissingImportsFromFragments(context, movedMethod);
                    JavaCodeStyleManager.getInstance(project).shortenClassReferences(movedMethod);
                    debug("Method successfully copied to utility class");
                    
                    replaceOriginalMethodsWithCalls(context, movedMethod, true);
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        showInfo(
                            "Move to Utility Class Successful\n\n" +
                            "Method: " + movedMethod.getName() + "\n" +
                            "Target: " + utilityClass.getQualifiedName() + "\n\n" +
                            "All duplicate methods have been replaced with calls."
                        );
                    });
                    
                } catch (Exception e) {
                    showError("Failed to move to utility class: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }
    
    /**
     * Replaces all original duplicate methods with calls to extracted method.
     */
    private void replaceOriginalMethodsWithCalls(@NotNull RefactoringContext context,
                                                 @NotNull PsiMethod extractedMethod,
                                                 boolean isStatic) {
        debug("\n=== REPLACING ORIGINAL METHODS ===");
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (DuplicateCodeFragment fragment : context.fragments) {
            try {
                PsiElement element = fragment.getPsiElement();
                PsiMethod originalMethod = null;
                if (element instanceof PsiMethod) {
                    originalMethod = (PsiMethod) element;
                } else if (element instanceof PsiCodeBlock) {
                    originalMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                } else {
                    PsiFile file = fragment.getFile();
                    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                    if (document != null) {
                        int startLine = fragment.getStartLine() - 1;
                        int startOffset = document.getLineStartOffset(startLine);
                        PsiElement elementAtStart = file.findElementAt(startOffset);
                        if (elementAtStart != null) {
                            originalMethod = PsiTreeUtil.getParentOfType(elementAtStart, PsiMethod.class);
                        }
                    }
                }
                
                if (originalMethod == null) {
                    debug("Skipping - no containing method found for fragment in " + fragment.getFilePath());
                    continue;
                }
                
                debug("Replacing method: " + originalMethod.getName() + " in " + 
                                 originalMethod.getContainingClass().getName());
                
                StringBuilder callText = new StringBuilder();
                
                if (isStatic) {
                    PsiClass utilityClass = extractedMethod.getContainingClass();
                    callText.append(utilityClass.getName()).append(".");
                }
                
                callText.append(extractedMethod.getName()).append("(");
                
                PsiParameter[] originalParams = originalMethod.getParameterList().getParameters();
                PsiParameter[] extractedParams = extractedMethod.getParameterList().getParameters();
                
                debug("  Original method has " + originalParams.length + " parameters");
                debug("  Extracted method has " + extractedParams.length + " parameters");
                
                List<String> argumentList = new ArrayList<>();

                PsiClass originalClass = originalMethod.getContainingClass();

                for (int i = 0; i < extractedParams.length; i++) {
                    String paramName = extractedParams[i].getName();
                    String argument = null;

                    // 1) Original-Parameter
                    for (PsiParameter originalParam : originalParams) {
                        if (originalParam.getName().equals(paramName)) {
                            argument = originalParam.getName();
                            debug("    Param " + (i + 1) + ": " + argument + " (from original parameter)");
                            break;
                        }
                    }

                    // 2) Feld-Referenz (baseStr, base, ...)
                    if (argument == null && originalClass != null) {
                        PsiField field = findFieldInOriginClass(originalClass, paramName);
                        if (field != null) {
                            argument = paramName;
                            debug("    Param " + (i + 1) + ": " + argument + " (from field)");
                        }
                    }

                    // 3) Fallback: Wert aus Fragment extrahieren
                    if (argument == null) {
                        argument = extractParameterValueFromFragment(fragment, extractedParams[i]);
                        debug("    Param " + (i + 1) + ": " + argument + " (extracted from fragment)");
                    }

                    argumentList.add(argument);
                }
                
                for (int i = 0; i < argumentList.size(); i++) {
                    if (i > 0) callText.append(", ");
                    callText.append(argumentList.get(i));
                }
                
                callText.append(")");
                
                debug("  Method call: " + callText);
                
                // Erstelle neue Methode mit nur einem Aufruf
                PsiType returnType = originalMethod.getReturnType();
                boolean hasReturn = returnType != null && !returnType.equals(PsiType.VOID);
                
                StringBuilder newMethodText = new StringBuilder();
                newMethodText.append(originalMethod.getModifierList().getText()).append(" ");
                newMethodText.append(returnType != null ? returnType.getPresentableText() : "void").append(" ");
                newMethodText.append(originalMethod.getName());
                newMethodText.append(originalMethod.getParameterList().getText());
                newMethodText.append(" {\n");
                
                if (hasReturn) {
                    newMethodText.append("    return ");
                } else {
                    newMethodText.append("    ");
                }
                
                newMethodText.append(callText).append(";\n");
                newMethodText.append("}");
                
                debug("  New method body:\n" + newMethodText);
                
                PsiMethod newMethod = factory.createMethodFromText(newMethodText.toString(), originalMethod.getContainingClass());
                originalMethod.replace(newMethod);
                
                debug("  Successfully replaced method");
                
            } catch (Exception e) {
                error("Failed to replace method in " + fragment.getFilePath() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        debug("=== REPLACEMENT COMPLETE ===\n");
    }

    /**
     * Fügt fehlende Imports aus den Quell-Dateien der Fragmente in die Zielklasse ein.
     * Dadurch werden z. B. statische Imports wie assertEquals übernommen.
     */
    private void addMissingImportsFromFragments(@NotNull RefactoringContext context,
                                                @NotNull PsiMethod targetMethod) {
        PsiFile targetFile = targetMethod.getContainingFile();
        if (!(targetFile instanceof PsiJavaFile)) {
            return;
        }

        PsiJavaFile targetJavaFile = (PsiJavaFile) targetFile;
        PsiImportList targetImports = targetJavaFile.getImportList();
        if (targetImports == null) {
            return;
        }

        Set<String> existingImports = new HashSet<>();
        for (PsiImportStatementBase stmt : targetImports.getAllImportStatements()) {
            existingImports.add(stmt.getText());
        }
        for (PsiImportStaticStatement stmt : targetImports.getImportStaticStatements()) {
            existingImports.add(stmt.getText());
        }

        for (DuplicateCodeFragment fragment : context.fragments) {
            PsiFile sourceFile = fragment.getFile();
            if (!(sourceFile instanceof PsiJavaFile)) {
                continue;
            }

            PsiImportList sourceImports = ((PsiJavaFile) sourceFile).getImportList();
            if (sourceImports == null) {
                continue;
            }

            for (PsiImportStatementBase stmt : sourceImports.getAllImportStatements()) {
                String text = stmt.getText();
                if (!existingImports.contains(text)) {
                    targetImports.add(stmt.copy());
                    existingImports.add(text);
                }
            }
            for (PsiImportStaticStatement stmt : sourceImports.getImportStaticStatements()) {
                String text = stmt.getText();
                if (!existingImports.contains(text)) {
                    targetImports.add(stmt.copy());
                    existingImports.add(text);
                }
            }
        }
    }
    
    /**
     * Extrahiert den Wert für einen zusätzlichen Parameter aus dem Fragment-Code.
     * 
     * Wenn Accept Signature Dialog Parameter hinzugefügt hat, müssen wir diese Werte aus dem Fragment extrahieren.
     */
    @NotNull
    private String extractParameterValueFromFragment(@NotNull DuplicateCodeFragment fragment,
                                                     @NotNull PsiParameter parameter) {
        try {
            String paramName = parameter.getName();
            debug("      Searching for value of parameter: " + paramName);
            
            // Lese den Fragment-Code DIREKT aus den Statements
            PsiElement element = fragment.getPsiElement();
            PsiMethod containingMethod = null;
            
            if (element instanceof PsiMethod) {
                containingMethod = (PsiMethod) element;
            } else if (element instanceof PsiCodeBlock) {
                containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            } else {
                PsiFile file = fragment.getFile();
                Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                if (document != null) {
                    int startLine = fragment.getStartLine() - 1;
                    int startOffset = document.getLineStartOffset(startLine);
                    PsiElement elementAtStart = file.findElementAt(startOffset);
                    if (elementAtStart != null) {
                        containingMethod = PsiTreeUtil.getParentOfType(elementAtStart, PsiMethod.class);
                    }
                }
            }
            
            if (containingMethod == null || containingMethod.getBody() == null) {
                error("      No containing method found");
                return getDefaultValueForType(parameter.getType());
            }
            
            // Suche in den Statements nach Variablen-Deklaration/Zuweisung
            PsiCodeBlock body = containingMethod.getBody();
            PsiStatement[] statements = body.getStatements();
            
            debug("      Analyzing " + statements.length + " statements in method: " + containingMethod.getName());
            
            for (PsiStatement statement : statements) {
                String stmtText = statement.getText().trim();
                debug("        Statement: " + stmtText);
                
                // Suche nach BELIEBIGER lokalen Variablen-Deklaration mit Initialisierung
                // Pattern: type varName = value;
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "^(\\w+)\\s+(\\w+)\\s*=\\s*([^;]+);$"
                );
                java.util.regex.Matcher matcher = pattern.matcher(stmtText);
                
                if (matcher.find()) {
                    String type = matcher.group(1);
                    String varName = matcher.group(2);
                    String value = matcher.group(3).trim();
                    
                    // Prüfe ob es ein primitiver Typ oder eine Klasse ist (lokale Variable)
                    if (isPrimitiveOrClassType(type)) {
                        debug("Found local variable '" + varName + "' = " + value);
                        return value;
                    }
                }
            }
            
            // Fallback: Suche nach String-Literalen (z. B. assertEquals erwartet einen String)
            for (PsiStatement statement : statements) {
                List<PsiLiteralExpression> literals = getStringLiteralExpressions(statement);
                if (!literals.isEmpty()) {
                    String literalText = literals.get(0).getText();
                    debug("Found string literal value: " + literalText);
                    return literalText;
                }
            }

            error("No local variable declaration or string literal found, using default");
            return getDefaultValueForType(parameter.getType());
            
        } catch (Exception e) {
            error("Failed to extract parameter value: " + e.getMessage());
            e.printStackTrace();
            return getDefaultValueForType(parameter.getType());
        }
    }
    
    /**
     * Prüft ob ein String ein primitiver Typ oder Klassen-Typ ist.
     */
    private boolean isPrimitiveOrClassType(String type) {
        // Primitive Typen
        if (type.equals("int") || type.equals("double") || type.equals("float") || 
            type.equals("long") || type.equals("short") || type.equals("byte") ||
            type.equals("boolean") || type.equals("char")) {
            return true;
        }
        
        // Klassen-Typen beginnen mit Großbuchstaben
        return type.length() > 0 && Character.isUpperCase(type.charAt(0));
    }
    
    /**
     * Gibt einen Default-Wert basierend auf dem Typ zurück.
     */
    @NotNull
    private String getDefaultValueForType(@NotNull PsiType type) {
        if (type.equals(PsiType.INT) || type.equals(PsiType.LONG) || 
            type.equals(PsiType.SHORT) || type.equals(PsiType.BYTE)) {
            return "0";
        } else if (type.equals(PsiType.DOUBLE) || type.equals(PsiType.FLOAT)) {
            return "0.0";
        } else if (type.equals(PsiType.BOOLEAN)) {
            return "false";
        } else if (type.equalsToText("java.lang.String")) {
            return "\"\"";
        } else {
            return "null";
        }
    }
    
    /**
     * Sammelt Statements aus einem DuplicateCodeFragment.
     */
    @NotNull
    private List<PsiStatement> collectStatementsFromFragment(@NotNull DuplicateCodeFragment fragment) {
        List<PsiStatement> statements = new ArrayList<>();
        
        PsiFile file = fragment.getFile();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return statements;
        }
        
        // Berechne Offsets aus Line-Range
        int startLine = fragment.getStartLine() - 1;
        int endLine = fragment.getEndLine() - 1;
        
        int startOffset = document.getLineStartOffset(startLine);
        int endOffset = document.getLineEndOffset(endLine);
        
        // Finde die enthaltende Methode
        PsiElement elementAtStart = file.findElementAt(startOffset);
        if (elementAtStart == null) {
            return statements;
        }
        
        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(elementAtStart, PsiMethod.class);
        if (containingMethod == null || containingMethod.getBody() == null) {
            return statements;
        }
        
        // Sammle alle Statements, die vollständig im Fragment-Bereich liegen
        PsiCodeBlock body = containingMethod.getBody();
        for (PsiStatement statement : body.getStatements()) {
            int stmtStart = statement.getTextRange().getStartOffset();
            int stmtEnd = statement.getTextRange().getEndOffset();
            
            // Statement muss vollständig im Fragment liegen
            // ODER: Statement überlappt mit Fragment (für partielle Statements am Rand)
            boolean completelyInside = (stmtStart >= startOffset && stmtEnd <= endOffset);
            boolean overlaps = (stmtStart < endOffset && stmtEnd > startOffset);
            
            if (completelyInside || overlaps) {
                statements.add(statement);
            }
        }
        
        return statements;
    }
    
    /**
     * Sammelt Statements aus einem PSI-Element.
     */
    @NotNull
    private List<PsiStatement> collectStatementsFromElement(@NotNull PsiElement element) {
        List<PsiStatement> statements = new ArrayList<>();
        
        // Element ist eine Methode → Extrahiere Body-Statements
        if (element instanceof PsiMethod) {
            PsiCodeBlock body = ((PsiMethod) element).getBody();
            if (body != null) {
                statements.addAll(Arrays.asList(body.getStatements()));
            }
            return statements;
        }
        
        // Element ist ein Code-Block → Extrahiere direkte Statements
        if (element instanceof PsiCodeBlock) {
            statements.addAll(Arrays.asList(((PsiCodeBlock) element).getStatements()));
            return statements;
        }
        
        // Element ist ein einzelnes Statement → Direkt zurückgeben
        if (element instanceof PsiStatement) {
            statements.add((PsiStatement) element);
            return statements;
        }
        
        // Fragment (z.B. von findElementAt) → Suche Statements im TextRange
        // Dies ist der kritische Fall für Duplikat-Fragmente innerhalb von Methoden
        
        // Finde die enthaltende Methode
        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (containingMethod != null && containingMethod.getBody() != null) {
            PsiCodeBlock body = containingMethod.getBody();
            int fragmentStart = element.getTextRange().getStartOffset();
            int fragmentEnd = element.getTextRange().getEndOffset();
            
            // Sammle alle Statements, die vollständig im Fragment-Bereich liegen
            for (PsiStatement statement : body.getStatements()) {
                int stmtStart = statement.getTextRange().getStartOffset();
                int stmtEnd = statement.getTextRange().getEndOffset();
                
                // Statement muss vollständig im Fragment liegen
                if (stmtStart >= fragmentStart && stmtEnd <= fragmentEnd) {
                    statements.add(statement);
                }
            }
        }
        
        //Wenn keine Statements gefunden, suche rekursiv im Element
        if (statements.isEmpty()) {
            PsiTreeUtil.processElements(element, e -> {
                if (e instanceof PsiStatement) {
                    // Füge nur hinzu, wenn Parent ein CodeBlock ist (Top-Level Statements)
                    if (e.getParent() instanceof PsiCodeBlock) {
                        int stmtStart = e.getTextRange().getStartOffset();
                        int stmtEnd = e.getTextRange().getEndOffset();
                        int fragmentStart = element.getTextRange().getStartOffset();
                        int fragmentEnd = element.getTextRange().getEndOffset();
                        
                        // Statement muss im Fragment-Bereich liegen
                        if (stmtStart >= fragmentStart && stmtEnd <= fragmentEnd) {
                            statements.add((PsiStatement) e);
                        }
                    }
                }
                return true;
            });
        }
        
        return statements;
    }
    
    /**
     * Erstellt oder findet eine Utility-Klasse für Cross-File Refactoring.
     */
    @Nullable
    private PsiClass getOrCreateUtilityClass(@NotNull RefactoringContext context) {
        return ReadAction.compute(() -> {
            // Bestimme Package aus erster betroffener Klasse
            PsiClass firstClass = context.affectedClasses.get(0);
            PsiFile firstFile = firstClass.getContainingFile();
            
            if (!(firstFile instanceof PsiJavaFile)) {
                return null;
            }
            
            PsiJavaFile javaFile = (PsiJavaFile) firstFile;
            String packageName = javaFile.getPackageName();
            
            // Name für Utility-Klasse
            String utilityClassName = "DuplicateCodeUtils";
            
            // Prüfe ob Utility-Klasse bereits existiert
            PsiPackage psiPackage = JavaPsiFacade.getInstance(project)
                .findPackage(packageName);
            
            if (psiPackage != null) {
                PsiClass[] existingClasses = psiPackage.findClassByShortName(
                    utilityClassName, 
                    GlobalSearchScope.projectScope(project)
                );
                
                if (existingClasses.length > 0) {
                    // Verwende existierende Utility-Klasse
                    return existingClasses[0];
                }
            }
            
            // Erstelle neue Utility-Klasse
            return createUtilityClass(packageName, utilityClassName, firstFile.getContainingDirectory());
        });
    }
    
    /**
     * Erstellt eine neue Utility-Klasse.
     */
    @Nullable
    private PsiClass createUtilityClass(@NotNull String packageName, 
                                        @NotNull String className,
                                        @NotNull PsiDirectory directory) {
        try {
            PsiClass[] created = new PsiClass[1];
            
            WriteCommandAction.runWriteCommandAction(project, () -> {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                
                // Erstelle Utility-Klasse mit private Constructor
                StringBuilder classContent = new StringBuilder();
                if (!packageName.isEmpty()) {
                    classContent.append("package ").append(packageName).append("\n\n");
                }
                
                classContent.append("/**\n");
                classContent.append(" * Utility class for extracted duplicate code.\n");
                classContent.append(" * Auto-generated by IntelliJDeodorant.\n");
                classContent.append(" */\n");
                classContent.append("public final class ").append(className).append(" {\n\n");
                classContent.append("    /**\n");
                classContent.append("     * Private constructor to prevent instantiation.\n");
                classContent.append("     */\n");
                classContent.append("    private ").append(className).append("() {\n");
                classContent.append("        throw new AssertionError(\"Utility class\");\n");
                classContent.append("    }\n");
                classContent.append("}\n");
                
                // Erstelle Datei
                PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(
                    className + ".java",
                    JavaLanguage.INSTANCE,
                    classContent.toString()
                );
                
                PsiFile addedFile = (PsiFile) directory.add(file);
                if (addedFile instanceof PsiJavaFile) {
                    PsiClass[] classes = ((PsiJavaFile) addedFile).getClasses();
                    if (classes.length > 0) {
                        created[0] = classes[0];
                    }
                }
            });
            
            return created[0];
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Wendet Cross-File-Strategie nach Extract Method an.
     */
    private void applyCrossFileStrategy(@NotNull RefactoringContext context,
                                       @NotNull RefactoringStrategy strategy,
                                       @NotNull ExtractMethodProcessor processor) {
        // Warte kurz, damit Extract Method vollständig abgeschlossen ist
        ApplicationManager.getApplication().invokeLater(() -> {
            ReadAction.run(() -> {
                PsiMethod extractedMethod = processor.getExtractedMethod();
                if (extractedMethod == null) {
                    debug("Extract Method was cancelled or failed");
                    return;  // Extraktion fehlgeschlagen oder abgebrochen
                }
                
                debug("\n=== CROSS-FILE STRATEGY ===");
                debug("Extracted method: " + extractedMethod.getName());
                debug("Strategy: " + strategy);
                
                // Prüfe ob IntelliJ's Processor bereits alle Duplikate ersetzt hat
                if (strategy == RefactoringStrategy.PULL_UP_TO_SUPERCLASS && context.commonSuperClass != null) {
                    // Pull Members Up zur Superklasse
                    debug("Performing automatic pull-up to: " + context.commonSuperClass.getQualifiedName());
                    automaticPullUp(extractedMethod, context.commonSuperClass, context);
                } else if (strategy == RefactoringStrategy.CREATE_UTILITY_CLASS) {
                    // Move zu Utility-Klasse
                    debug("Performing automatic move to utility class");
                    automaticMoveToUtilityClass(extractedMethod, context);
                }
            });
        }, com.intellij.openapi.application.ModalityState.NON_MODAL);
    }
    
    /**
     * Führt automatisch Pull Members Up durch.
     */
    private void automaticPullUp(@NotNull PsiMethod method, 
                                 @NotNull PsiClass targetSuperClass,
                                 @NotNull RefactoringContext context) {
        WriteCommandAction.runWriteCommandAction(project, "Pull Up Method", null, () -> {
            try {
                // Hole die Quell-Klasse
                PsiClass sourceClass = method.getContainingClass();
                if (sourceClass == null) {
                    return;
                }
                
                // Prüfe ob Superklasse erreichbar ist
                if (!InheritanceUtil.isInheritorOrSelf(sourceClass, targetSuperClass, true)) {
                    showError("Source class does not inherit from target superclass");
                    return;
                }
                
                // Erstelle Kopie der Methode in Superklasse
                PsiMethod movedMethod = (PsiMethod) targetSuperClass.add(method);
                addMissingImportsFromFragments(context, movedMethod);
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(movedMethod);
                
                // Ändere Sichtbarkeit auf protected (damit Subklassen zugreifen können)
                PsiModifierList modifierList = movedMethod.getModifierList();
                modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
                modifierList.setModifierProperty(PsiModifier.PROTECTED, true);
                
                // Lösche Original-Methode
                method.delete();
                
                // Ersetze alle Duplikat-Fragmente durch Methodenaufrufe
                replaceFragmentsWithMethodCalls(context, movedMethod, false);
                
                // Erfolgs-Nachricht
                ApplicationManager.getApplication().invokeLater(() -> {
                    showInfo(
                        "Pull Up Successful\n\n" +
                        "The method '" + movedMethod.getName() + "' has been moved to:\n" +
                        targetSuperClass.getQualifiedName() + "\n\n" +
                        "All duplicate fragments have been replaced with method calls."
                    );
                });
                
            } catch (Exception e) {
                showError("Pull up failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Führt automatisch Move zu Utility-Klasse durch.
     */
    private void automaticMoveToUtilityClass(@NotNull PsiMethod method, @NotNull RefactoringContext context) {
        WriteCommandAction.runWriteCommandAction(project, "Move to Utility Class", null, () -> {
            try {
                // Hole oder erstelle Utility-Klasse
                PsiClass utilityClass = getOrCreateUtilityClass(context);
                if (utilityClass == null) {
                    showError("Could not create utility class");
                    return;
                }
                
                // Erstelle Kopie der Methode in Utility-Klasse
                PsiMethod movedMethod = (PsiMethod) utilityClass.add(method);
                addMissingImportsFromFragments(context, movedMethod);
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(movedMethod);
                
                // Mache Methode public static
                PsiModifierList modifierList = movedMethod.getModifierList();
                modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
                modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
                modifierList.setModifierProperty(PsiModifier.STATIC, true);
                
                // Lösche Original-Methode
                method.delete();
                
                // Ersetze alle Duplikat-Fragmente durch statische Methodenaufrufe
                replaceFragmentsWithMethodCalls(context, movedMethod, true);
                
                // Erfolgs-Nachricht
                ApplicationManager.getApplication().invokeLater(() -> {
                    showInfo(
                        "Move to Utility Class Successful\n\n" +
                        "The method '" + movedMethod.getName() + "' has been moved to:\n" +
                        utilityClass.getQualifiedName() + "\n\n" +
                        "All duplicate fragments have been replaced with method calls."
                    );
                });
                
            } catch (Exception e) {
                showError("Move to utility class failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Ersetzt alle Duplikat-Fragmente durch Methodenaufrufe.
     */
    private void replaceFragmentsWithMethodCalls(@NotNull RefactoringContext context,
                                                 @NotNull PsiMethod extractedMethod,
                                                 boolean isStatic) {
        debug("\n=== CHECKING FRAGMENTS FOR REPLACEMENT ===");
        debug("Total fragments: " + context.fragments.size());
        debug("Extracted method: " + extractedMethod.getName());
        debug("Is static: " + isStatic);
        
        // Prüfe welche Fragmente noch ersetzt werden müssen
        List<DuplicateCodeFragment> fragmentsToReplace = new ArrayList<>();
        
        for (DuplicateCodeFragment fragment : context.fragments) {
            ReadAction.run(() -> {
                PsiFile file = fragment.getFile();
                if (file == null) return;
                
                Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                if (document == null) return;
                
                int startLine = fragment.getStartLine() - 1;
                int endLine = fragment.getEndLine() - 1;
                
                if (startLine < 0 || endLine >= document.getLineCount()) return;
                
                int startOffset = document.getLineStartOffset(startLine);
                int endOffset = document.getLineEndOffset(endLine);
                
                String fragmentText = document.getText().substring(startOffset, Math.min(endOffset, document.getTextLength()));
                
                // Prüfe ob Fragment noch Original-Code enthält oder schon ersetzt wurde
                // Wenn es bereits einen Aufruf zur extractedMethod enthält, überspringe es
                if (fragmentText.contains(extractedMethod.getName() + "(")) {
                    debug("Fragment in " + file.getName() + " (lines " + 
                                     fragment.getStartLine() + "-" + fragment.getEndLine() + 
                                     ") already replaced - skipping");
                } else {
                    debug("Fragment in " + file.getName() + " (lines " + 
                                     fragment.getStartLine() + "-" + fragment.getEndLine() + 
                                     ") needs replacement");
                    fragmentsToReplace.add(fragment);
                }
            });
        }
        
        if (fragmentsToReplace.isEmpty()) {
            debug("All fragments already replaced by IntelliJ's ExtractMethodProcessor!");
            debug("=== REPLACEMENT CHECK COMPLETE ===\n");
            return;
        }
        
        debug("\nFragments needing replacement: " + fragmentsToReplace.size());
        debug("=== STARTING MANUAL REPLACEMENT ===\n");
        debug("\nFragments needing replacement: " + fragmentsToReplace.size());
        debug("=== STARTING MANUAL REPLACEMENT ===\n");
        
        int replacedCount = 0;
        
        for (int idx = 0; idx < fragmentsToReplace.size(); idx++) {
            DuplicateCodeFragment fragment = fragmentsToReplace.get(idx);
            int fragmentIndex = idx;
            
            debug("\n--- Processing fragment " + (idx + 1) + " ---");
            debug("File: " + fragment.getFilePath());
            debug("Lines: " + fragment.getStartLine() + "-" + fragment.getEndLine());
            
            WriteCommandAction.runWriteCommandAction(project, "Replace Duplicate Fragment " + (idx + 1), null, () -> {
                try {
                    PsiFile file = fragment.getFile();
                    if (file == null) {
                        error("Fragment file is null!");
                        return;
                    }
                    
                    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                    if (document == null) {
                        error("Document is null for file: " + file.getName());
                        return;
                    }
                    
                    // Berechne Offsets
                    int startLine = fragment.getStartLine() - 1;  // 0-based
                    int endLine = fragment.getEndLine() - 1;
                    
                    if (startLine < 0 || endLine >= document.getLineCount()) {
                        error("Invalid line range: " + startLine + "-" + endLine);
                        return;
                    }
                    
                    int startOffset = document.getLineStartOffset(startLine);
                    int endOffset = document.getLineEndOffset(endLine);
                    
                    debug("Offsets: " + startOffset + "-" + endOffset);
                    
                    // Finde die Methode, die das Fragment enthält
                    // Probiere mehrere Offsets, falls startOffset auf Whitespace liegt
                    PsiMethod containingMethod = null;
                    PsiElement searchElement = null;
                    
                    // Strategie 1: Probiere startOffset
                    searchElement = file.findElementAt(startOffset);
                    if (searchElement != null) {
                        containingMethod = PsiTreeUtil.getParentOfType(searchElement, PsiMethod.class);
                        debug("Try 1 (startOffset): element=" + searchElement.getClass().getSimpleName() + 
                                         ", method=" + (containingMethod != null ? containingMethod.getName() : "null"));
                    }
                    
                    // Strategie 2: Falls nicht gefunden, probiere Mitte des Bereichs
                    if (containingMethod == null) {
                        int midOffset = (startOffset + endOffset) / 2;
                        searchElement = file.findElementAt(midOffset);
                        if (searchElement != null) {
                            containingMethod = PsiTreeUtil.getParentOfType(searchElement, PsiMethod.class);
                            debug("Try 2 (midOffset): element=" + searchElement.getClass().getSimpleName() + 
                                             ", method=" + (containingMethod != null ? containingMethod.getName() : "null"));
                        }
                    }
                    
                    // Strategie 3: Falls nicht gefunden, probiere erste nicht-whitespace Position
                    if (containingMethod == null) {
                        for (int offset = startOffset; offset < endOffset && offset < document.getTextLength(); offset++) {
                            char c = document.getCharsSequence().charAt(offset);
                            if (!Character.isWhitespace(c)) {
                                searchElement = file.findElementAt(offset);
                                if (searchElement != null) {
                                    containingMethod = PsiTreeUtil.getParentOfType(searchElement, PsiMethod.class);
                                    debug("Try 3 (first non-whitespace at " + offset + "): element=" + 
                                                     searchElement.getClass().getSimpleName() + 
                                                     ", method=" + (containingMethod != null ? containingMethod.getName() : "null"));
                                    if (containingMethod != null) break;
                                }
                            }
                        }
                    }
                    
                    if (containingMethod == null) {
                        error("No containing method found after trying multiple strategies!");
                        error("Fragment text preview: " + 
                                         document.getText().substring(startOffset, Math.min(startOffset + 100, endOffset)));
                        return;
                    }
                    
                    debug("Containing method: " + containingMethod.getName());
                    
                    // Überspringe das Fragment, wo die Methode extrahiert wurde
                    if (containingMethod.getName().equals(extractedMethod.getName())) {
                        debug("Skipping - this is the extracted method itself");
                        return;
                    }
                    
                    PsiCodeBlock body = containingMethod.getBody();
                    if (body == null) {
                        error("Method has no body!");
                        return;
                    }
                    
                    // Sammle Statements im Fragment-Bereich
                    List<PsiStatement> statementsToReplace = new ArrayList<>();
                    PsiStatement[] allStatements = body.getStatements();
                    
                    debug("Total statements in method: " + allStatements.length);
                    
                    for (PsiStatement statement : allStatements) {
                        int stmtStart = statement.getTextRange().getStartOffset();
                        int stmtEnd = statement.getTextRange().getEndOffset();
                        
                        // Statement muss im Fragment-Bereich liegen
                        if (stmtStart >= startOffset && stmtEnd <= endOffset) {
                            statementsToReplace.add(statement);
                            debug("  Found statement at offset " + stmtStart + "-" + stmtEnd);
                        }
                    }
                    
                    debug("Statements to replace: " + statementsToReplace.size());
                    
                    if (statementsToReplace.isEmpty()) {
                        error("No statements found to replace!");
                        return;
                    }
                    
                    // Erstelle Methodenaufruf MIT PARAMETERN
                    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                    
                    // Sammle Parameter der extrahierten Methode
                    PsiParameterList parameterList = extractedMethod.getParameterList();
                    PsiParameter[] parameters = parameterList.getParameters();
                    
                    debug("Extracted method has " + parameters.length + " parameters");
                    
                    // Baue Argument-Liste auf
                    // Verwende gleiche Variablennamen wie die Parameter
                    // (IntelliJ's ExtractMethodProcessor findet automatisch passende Variablen)
                    StringBuilder arguments = new StringBuilder();
                    for (int i = 0; i < parameters.length; i++) {
                        if (i > 0) arguments.append(", ");
                        
                        // Verwende Parameter-Namen als Argument
                        // Das funktioniert, weil ExtractMethodProcessor die Variablen bereits richtig gemappt hat
                        String paramName = parameters[i].getName();
                        arguments.append(paramName);
                        
                        debug("  Parameter " + (i + 1) + ": " + 
                                         parameters[i].getType().getPresentableText() + " " + paramName);
                    }
                    
                    String methodCall;
                    
                    if (isStatic) {
                        // Utility-Klasse: UtilityClass.methodName(args)
                        PsiClass utilityClass = extractedMethod.getContainingClass();
                        if (utilityClass != null) {
                            methodCall = utilityClass.getName() + "." + extractedMethod.getName() + 
                                       "(" + arguments + ")";
                        } else {
                            methodCall = extractedMethod.getName() + "(" + arguments + ")";
                        }
                    } else {
                        // Pull-Up: methodName(args) - wird automatisch von Superklasse aufgerufen
                        methodCall = extractedMethod.getName() + "(" + arguments + ")";
                    }
                    
                    debug("Method call: " + methodCall);
                    
                    // Prüfe ob die Methode einen Return-Wert hat
                    PsiType returnType = extractedMethod.getReturnType();
                    boolean hasReturnValue = returnType != null && !returnType.equals(PsiType.VOID);
                    
                    debug("Method has return value: " + hasReturnValue + 
                                     (hasReturnValue ? " (" + returnType.getPresentableText() + ")" : ""));
                    
                    PsiStatement callStatement;
                    if (hasReturnValue) {
                        // Methode gibt Wert zurück - verwende return statement
                        callStatement = factory.createStatementFromText("return " + methodCall + ";", null);
                    } else {
                        // Methode gibt nichts zurück - einfacher Aufruf
                        callStatement = factory.createStatementFromText(methodCall + ";", null);
                    }
                    
                    // Ersetze erstes Statement durch Aufruf, lösche den Rest
                    statementsToReplace.get(0).replace(callStatement);
                    debug("Replaced first statement");
                    
                    for (int i = 1; i < statementsToReplace.size(); i++) {
                        statementsToReplace.get(i).delete();
                        debug("Deleted statement " + (i + 1));
                    }
                    
                    // Commit changes
                    PsiDocumentManager.getInstance(project).commitDocument(document);
                    debug("SUCCESS: Fragment " + (fragmentIndex + 1) + " replaced!");
                    
                } catch (Exception e) {
                    error("FAILED to replace fragment " + (fragmentIndex + 1) + " in " + fragment.getFilePath());
                    error("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
        
        debug("\n=== REPLACEMENT COMPLETE ===");
    }
    
    /**
     * Zeigt eine Error-Nachricht.
     */
    private void showError(@NotNull String message) {
        Messages.showErrorDialog(project, message, "Refactoring Error");
    }
    
    /**
     * Zeigt eine Info-Nachricht.
     */
    private void showInfo(@NotNull String message) {
        Messages.showInfoMessage(project, message, "Refactoring Info");
    }
    
    /**
     * Kontext-Klasse für Refactoring-Informationen.
     */
    private static class RefactoringContext {
        DuplicateCodeGroup group;
        List<DuplicateCodeFragment> fragments;
        List<PsiClass> affectedClasses;
        Map<DuplicateCodeFragment, PsiClass> fragmentToClass;
        boolean isSameClass;
        PsiClass commonSuperClass;
    }
    
    /**
     * Refactoring-Strategien.
     */
    private enum VariableDifferenceKind {
        VARIABLE_DECLARATION,
        STRING_LITERAL
    }

    /**
     * Hilfsklasse für Unterschiede zwischen Methoden.
     */
    private static class VariableDifference {
        VariableDifferenceKind kind;
        int statementIndex;  // Index des Statements in der Methode
        int literalIndex;  // Index des String-Literals im Statement (nur für STRING_LITERAL)
        List<String> variableNames;  // Alle Variablen-Namen an dieser Position
        List<String> types;  // Alle Typen
        List<String> initializers;  // Alle Initializer-Werte
        String parameterName;  // Name des zu erstellenden Parameters
        PsiType commonType;  // Gemeinsamer Typ (Object wenn unterschiedlich)
    }
    
    private enum RefactoringStrategy {
        WITHIN_CLASS("Extract method in the same class"),
        PULL_UP_TO_SUPERCLASS("Extract method, then pull up to common superclass"),
        CREATE_UTILITY_CLASS("Extract method, then move to utility class");
        
        private final String description;
        
        RefactoringStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
