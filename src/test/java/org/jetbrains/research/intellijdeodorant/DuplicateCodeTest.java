package org.jetbrains.research.intellijdeodorant;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeFragment;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeGroup;
import org.jetbrains.research.intellijdeodorant.core.duplication.DuplicateCodeValidator;
import org.jetbrains.research.intellijdeodorant.core.duplication.PMDDuplicateCodeDetector;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.DuplicateCodeRefactoringHandler;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy.DuplicateRefactoringStrategy;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy.ExtractAndPullUpStrategy;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy.ExtractMethodStrategy;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.duplicateCode.strategy.ExtractUtilityMethodStrategy;
import org.jetbrains.research.intellijdeodorant.utils.DuplicateRangeAdjuster;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class DuplicateCodeTest extends LightJavaCodeInsightFixtureTestCase {

    private static final String MOCK_JDK_HOME = "src/test/resources/mockJDK-1.8";
    private static final String TEST_DATA_PATH = "src/test/resources/testdata";

    public void testGroup_newGroup_hasZeroOccurrences() {
        assertEquals(0, new DuplicateCodeGroup(100).getOccurrences());
    }

    public void testGroup_addFragment_increasesOccurrences() {
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(fragmentAt("AddA.java", 1, 5));
        assertEquals(1, group.getOccurrences());
    }

    public void testGroup_addSameFragmentTwice_notAddedTwice() {
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        DuplicateCodeFragment f = fragmentAt("Dup.java", 1, 5);
        group.addFragment(f);
        group.addFragment(f);
        assertEquals(1, group.getOccurrences());
    }

    public void testGroup_removeFragment_decreasesOccurrences() {
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        DuplicateCodeFragment f = fragmentAt("Rm.java", 1, 5);
        group.addFragment(f);
        assertTrue(group.removeFragment(f));
        assertEquals(0, group.getOccurrences());
    }

    public void testGroup_removeNonExistentFragment_returnsFalse() {
        assertFalse(new DuplicateCodeGroup(100).removeFragment(fragmentAt("X.java", 1, 5)));
    }

    public void testGroup_severity_equalsTokensTimesOccurrences() {
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(fragmentAt("SevA.java", 1, 5));
        group.addFragment(fragmentAt("SevB.java", 1, 5));
        assertEquals(200, group.getSeverity());
    }

    public void testGroup_averageLines_empty_returnsZero() {
        assertEquals(0.0, new DuplicateCodeGroup(100).getAverageLines(), 0.0001);
    }

    public void testGroup_averageLines_twoFragments_isCorrect() {
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(fragmentAt("AvgA.java",  1, 10)); // 10 lines
        group.addFragment(fragmentAt("AvgB.java",  1, 20)); // 20 lines
        assertEquals(15.0, group.getAverageLines(), 0.0001);
    }

    public void testGroup_getFirstFragment_emptyGroup_throwsIllegalState() {
        try {
            new DuplicateCodeGroup(100).getFirstFragment();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) { /* correct */ }
    }

    public void testGroup_getFragments_returnsUnmodifiableView() {
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(fragmentAt("Unmod.java", 1, 5));
        try {
            group.getFragments().add(fragmentAt("Unmod2.java", 6, 10));
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { /* correct */ }
    }

    public void testGroup_equals_sameFragmentsAndTokens() {
        PsiFile file = myFixture.addFileToProject("EqFile.java", "class EqFile {}");
        DuplicateCodeGroup g1 = new DuplicateCodeGroup(100);
        DuplicateCodeGroup g2 = new DuplicateCodeGroup(100);
        g1.addFragment(new DuplicateCodeFragment(file, 1, 5, 100, "a"));
        g2.addFragment(new DuplicateCodeFragment(file, 1, 5, 100, "a"));
        assertEquals(g1, g2);
        assertEquals(g1.hashCode(), g2.hashCode());
    }

    public void testGroup_isCrossFile_differentFiles_returnsTrue() {
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(new DuplicateCodeFragment(myFixture.addFileToProject("CrossA.java", "class CrossA {}"), 1, 5, 100, "code"));
        group.addFragment(new DuplicateCodeFragment(myFixture.addFileToProject("CrossB.java", "class CrossB {}"), 1, 5, 100, "code"));
        assertTrue(group.isCrossFile());
    }

    public void testGroup_isCrossFile_sameFile_returnsFalse() {
        PsiFile file = myFixture.addFileToProject("SameFileCross.java", "class SameFileCross {}");
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(new DuplicateCodeFragment(file,  1,  5, 100, "code"));
        group.addFragment(new DuplicateCodeFragment(file, 10, 15, 100, "code"));
        assertFalse(group.isCrossFile());
    }

    public void testFragment_getLineCount_correctCalculation() {
        PsiFile file = myFixture.addFileToProject("LineCount.java", "class LineCount {}");
        assertEquals(5, new DuplicateCodeFragment(file, 3, 7, 100, "code").getLineCount());
    }

    public void testFragment_getCode_returnsConstructorValue() {
        PsiFile file = myFixture.addFileToProject("CodeFrag.java", "class CodeFrag {}");
        assertEquals("int x = 42;", new DuplicateCodeFragment(file, 1, 5, 100, "int x = 42;").getCode());
    }

    public void testFragment_locationString_containsFilenameAndLines() {
        PsiFile file = myFixture.addFileToProject("Location.java", "class Location {}");
        String loc = ReadAction.compute(new DuplicateCodeFragment(file, 3, 7, 100, "code")::getLocationString);
        assertTrue(loc.contains("Location.java"));
        assertTrue(loc.contains("3"));
        assertTrue(loc.contains("7"));
    }

    public void testFragment_equals_basedOnFileAndLines_ignoresTokensAndCode() {
        PsiFile file = myFixture.addFileToProject("EqFrag.java", "class EqFrag {}");
        DuplicateCodeFragment f1 = new DuplicateCodeFragment(file, 1, 10, 100, "a");
        DuplicateCodeFragment f2 = new DuplicateCodeFragment(file, 1, 10, 999, "completely different");
        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    public void testFragment_getClassName_withPsiStatements() {
        PsiFile file = myFixture.addFileToProject("MyClass.java",
                "class MyClass {\n    void foo() {\n        int x = 1;\n    }\n}\n");
        PsiStatement[] stmts = ReadAction.compute(() ->
                ((PsiJavaFile) file).getClasses()[0].getMethods()[0].getBody().getStatements());
        DuplicateCodeFragment f = new DuplicateCodeFragment(file, 3, 3, 10, "int x = 1;");
        f.setStatements(stmts);
        assertEquals("MyClass", ReadAction.compute(f::getClassName));
    }

    public void testFragment_getClassName_noStatements_returnsUnknown() {
        PsiFile file = myFixture.addFileToProject("NoStmt.java", "class NoStmt {}");
        assertEquals("<unknown>", ReadAction.compute(new DuplicateCodeFragment(file, 1, 1, 10, "code")::getClassName));
    }

    public void testFragment_setStatements_getStatements_roundtrip() {
        PsiFile file = myFixture.addFileToProject("StmtRound.java",
                "class StmtRound {\n    void m() { int a = 1; }\n}\n");
        PsiStatement[] stmts = ReadAction.compute(() ->
                ((PsiJavaFile) file).getClasses()[0].getMethods()[0].getBody().getStatements());
        DuplicateCodeFragment f = new DuplicateCodeFragment(file, 2, 2, 10, "int a = 1;");
        f.setStatements(stmts);
        assertNotNull(f.getStatements());
        assertEquals(1, f.getStatements().length);
        assertSame(stmts[0], f.getPsiElement());
    }

    public void testRangeAdjuster_simpleRange_returnsCorrectStatements() {
        PsiFile file = myFixture.addFileToProject("Adjuster.java",
                "class Adjuster {\n" +
                "    void compute() {\n" +
                "        int a = 1;\n" +      
                "        int b = 2;\n" +     
                "        int c = a + b;\n" +  
                "    }\n" +
                "}\n");
        Document doc = getDocument(file);
        List<DuplicateRangeAdjuster.AdjustedRange> ranges =
                ReadAction.compute(() -> DuplicateRangeAdjuster.adjustRangeWithLines(file, doc, 3, 5));
        assertFalse(ranges.isEmpty());
        assertEquals(3, ranges.get(0).statements.length);
        assertEquals(3, ranges.get(0).startLine);
        assertEquals(5, ranges.get(0).endLine);
    }

    public void testRangeAdjuster_singleStatement_returnsOneStatement() {
        PsiFile file = myFixture.addFileToProject("Single.java",
                "class Single {\n" +
                "    void run() {\n" +
                "        doSomething();\n" +
                "    }\n" +
                "}\n");
        Document doc = getDocument(file);
        List<DuplicateRangeAdjuster.AdjustedRange> ranges =
                ReadAction.compute(() -> DuplicateRangeAdjuster.adjustRangeWithLines(file, doc, 3, 3));
        assertFalse(ranges.isEmpty());
        assertEquals(1, ranges.get(0).statements.length);
    }

    public void testRangeAdjuster_outsideMethod_returnsEmpty() {
        PsiFile file = myFixture.addFileToProject("Outside.java",
                "class Outside {\n" +
                "    int field = 0;\n" +
                "}\n");
        Document doc = getDocument(file);
        assertTrue(ReadAction.compute(() ->
                DuplicateRangeAdjuster.adjustRangeWithLines(file, doc, 2, 2)).isEmpty());
    }

    public void testRangeAdjuster_rangeCoveredByTwoMethods_returnsOneRangeEach() {
        PsiFile file = myFixture.addFileToProject("TwoMethods.java",
                "class TwoMethods {\n" +
                "    void first()  { int x = 1; }\n" +
                "    void second() { int y = 2; }\n" +
                "}\n");
        Document doc = getDocument(file);
        List<DuplicateRangeAdjuster.AdjustedRange> ranges =
                ReadAction.compute(() -> DuplicateRangeAdjuster.adjustRangeWithLines(file, doc, 2, 3));
        assertEquals(2, ranges.size());
    }

    public void testRangeAdjuster_ifStatement_returnsAtLeastTwoStatements() {
        PsiFile file = myFixture.addFileToProject("IfStmt.java",
                "class IfStmt {\n" +
                "    void check(int n) {\n" +
                "        if (n > 0) { n++; }\n" +
                "        n--;\n" +
                "    }\n" +
                "}\n");
        Document doc = getDocument(file);
        List<DuplicateRangeAdjuster.AdjustedRange> ranges =
                ReadAction.compute(() -> DuplicateRangeAdjuster.adjustRangeWithLines(file, doc, 3, 4));
        assertFalse(ranges.isEmpty());
        assertTrue(ranges.stream().mapToInt(r -> r.statements.length).sum() >= 2);
    }

    public void testDetector_defaultConfig_hasExpectedDefaults() {
        PMDDuplicateCodeDetector d = new PMDDuplicateCodeDetector();
        String cfg = d.getConfiguration();
        assertTrue(cfg.contains("minimumTileSize=60"));
        assertTrue(cfg.contains("ignoreIdentifiers=true"));
        assertTrue(cfg.contains("ignoreLiterals=true"));
        assertTrue(cfg.contains("ignoreAnnotations=true"));
    }

    public void testDetector_setMinimumTileSize_updatesConfig() {
        PMDDuplicateCodeDetector d = new PMDDuplicateCodeDetector();
        d.setMinimumTileSize(75);
        assertTrue(d.getConfiguration().contains("minimumTileSize=75"));
    }

    public void testDetector_setNoOpValues_doesNotChangeConfig() {
        PMDDuplicateCodeDetector d = new PMDDuplicateCodeDetector();
        String before = d.getConfiguration();
        d.setMinimumTileSize(60);
        d.setIgnoreIdentifiers(true);
        d.setIgnoreLiterals(true);
        assertEquals(before, d.getConfiguration());
    }

    public void testDetector_invalidateCache_doesNotResetUserConfig() {
        PMDDuplicateCodeDetector d = new PMDDuplicateCodeDetector();
        d.setMinimumTileSize(90);
        d.invalidateCache();
        assertTrue(d.getConfiguration().contains("minimumTileSize=90"));
    }

    public void testValidator_emptyGroups_doesNotThrow() {
        DuplicateCodeValidator.validate(new HashSet<>(), 0, 0);
    }

    public void testValidator_tooShortFragments_groupRemoved() {
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(new DuplicateCodeFragment(myFixture.addFileToProject("ShortA.java", "class ShortA {}"), 1, 3, 100, "code"));
        group.addFragment(new DuplicateCodeFragment(myFixture.addFileToProject("ShortB.java", "class ShortB {}"), 1, 3, 100, "code"));
        Set<DuplicateCodeGroup> groups = new HashSet<>(Arrays.asList(group));
        DuplicateCodeValidator.validate(groups, 0, 0);
        assertTrue(groups.isEmpty());
    }

    public void testValidator_longFragmentsNoStatements_groupRemoved() {
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(new DuplicateCodeFragment(myFixture.addFileToProject("LongA.java", "class LongA {}"), 1, 8, 100, "code"));
        group.addFragment(new DuplicateCodeFragment(myFixture.addFileToProject("LongB.java", "class LongB {}"), 1, 8, 100, "code"));
        Set<DuplicateCodeGroup> groups = new HashSet<>(Arrays.asList(group));
        DuplicateCodeValidator.validate(groups, 0, 0);
        assertTrue(groups.isEmpty());
    }

    public void testAnalyzeClasses_sameClass_isSameClassTrue() {
        PsiFile file = myFixture.addFileToProject("AnalyzeSame.java",
                "class AnalyzeSame {\n    void a() { int x = 1; }\n    void b() { int y = 2; }\n}\n");
        PsiJavaFile jf = (PsiJavaFile) file;
        PsiStatement[] s1 = ReadAction.compute(() -> jf.getClasses()[0].getMethods()[0].getBody().getStatements());
        PsiStatement[] s2 = ReadAction.compute(() -> jf.getClasses()[0].getMethods()[1].getBody().getStatements());
        DuplicateCodeFragment f1 = new DuplicateCodeFragment(file, 2, 2, 100, "int x = 1;"); f1.setStatements(s1);
        DuplicateCodeFragment f2 = new DuplicateCodeFragment(file, 3, 3, 100, "int y = 2;"); f2.setStatements(s2);
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(f1); group.addFragment(f2);
        DuplicateRefactoringStrategy.RefactoringContext ctx =
                ReadAction.compute(() -> DuplicateRefactoringStrategy.analyzeClasses(group));
        assertNotNull(ctx);
        assertTrue(ctx.isSameClass);
        assertEquals(1, ctx.affectedClasses.size());
    }

    public void testAnalyzeClasses_differentClasses_isSameClassFalse() {
        PsiFile fileA = myFixture.addFileToProject("AnalyzeA.java", "class AnalyzeA { void m() { int a = 1; } }\n");
        PsiFile fileB = myFixture.addFileToProject("AnalyzeB.java", "class AnalyzeB { void m() { int b = 2; } }\n");
        PsiStatement[] sA = ReadAction.compute(() -> ((PsiJavaFile) fileA).getClasses()[0].getMethods()[0].getBody().getStatements());
        PsiStatement[] sB = ReadAction.compute(() -> ((PsiJavaFile) fileB).getClasses()[0].getMethods()[0].getBody().getStatements());
        DuplicateCodeFragment fA = new DuplicateCodeFragment(fileA, 1, 1, 100, "int a = 1;"); fA.setStatements(sA);
        DuplicateCodeFragment fB = new DuplicateCodeFragment(fileB, 1, 1, 100, "int b = 2;"); fB.setStatements(sB);
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(fA); group.addFragment(fB);
        DuplicateRefactoringStrategy.RefactoringContext ctx =
                ReadAction.compute(() -> DuplicateRefactoringStrategy.analyzeClasses(group));
        assertNotNull(ctx);
        assertFalse(ctx.isSameClass);
        assertEquals(2, ctx.affectedClasses.size());
    }

    public void testAnalyzeClasses_fragmentWithNoStatements_returnsNull() {
        PsiFile file = myFixture.addFileToProject("AnaNoStmt.java", "class AnaNoStmt {}");
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(new DuplicateCodeFragment(file, 1, 5, 100, "code"));
        assertNull(ReadAction.compute(() -> DuplicateRefactoringStrategy.analyzeClasses(group)));
    }

    public void testAnalyzeClasses_fragmentToClassMapping_isCorrect() {
        PsiFile fileA = myFixture.addFileToProject("MapA.java", "class MapA { void m() { int x = 1; } }\n");
        PsiFile fileB = myFixture.addFileToProject("MapB.java", "class MapB { void m() { int y = 2; } }\n");
        PsiClass clsA = ReadAction.compute(() -> ((PsiJavaFile) fileA).getClasses()[0]);
        PsiClass clsB = ReadAction.compute(() -> ((PsiJavaFile) fileB).getClasses()[0]);
        PsiStatement[] sA = ReadAction.compute(() -> clsA.getMethods()[0].getBody().getStatements());
        PsiStatement[] sB = ReadAction.compute(() -> clsB.getMethods()[0].getBody().getStatements());
        DuplicateCodeFragment fA = new DuplicateCodeFragment(fileA, 1, 1, 100, "int x = 1;"); fA.setStatements(sA);
        DuplicateCodeFragment fB = new DuplicateCodeFragment(fileB, 1, 1, 100, "int y = 2;"); fB.setStatements(sB);
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(fA); group.addFragment(fB);
        DuplicateRefactoringStrategy.RefactoringContext ctx =
                ReadAction.compute(() -> DuplicateRefactoringStrategy.analyzeClasses(group));
        assertNotNull(ctx);
        assertEquals(clsA, ctx.fragmentToClass.get(fA));
        assertEquals(clsB, ctx.fragmentToClass.get(fB));
    }

    private static final String E2E_TWO_METHOD_SRC =
            "class %s {\n" +
            "    void methodOne(int[] arr) {\n" +
            "        int a = 0;\n" +
            "        int b = 0;\n" +
            "        int c = 1;\n" +
            "        int d = arr[0];\n" +
            "        for (int i = 0; i < arr.length; i++) {\n" +
            "            a = a + arr[i];\n" +
            "            b = b + 1;\n" +
            "            c = c * arr[i];\n" +
            "        }\n" +
            "        int e = a / b;\n" +
            "        int f = e + c;\n" +
            "        int g = f + d;\n" +
            "    }\n" +
            "    void methodTwo(int[] vs) {\n" +
            "        int p = 0;\n" +
            "        int q = 0;\n" +
            "        int r = 1;\n" +
            "        int s = vs[0];\n" +
            "        for (int j = 0; j < vs.length; j++) {\n" +
            "            p = p + vs[j];\n" +
            "            q = q + 1;\n" +
            "            r = r * vs[j];\n" +
            "        }\n" +
            "        int t = p / q;\n" +
            "        int u = t + r;\n" +
            "        int v = u + s;\n" +
            "    }\n" +
            "}\n";

    public void testE2E_validator_preserves_group_with_extractable_code() {
        PsiFile file = myFixture.addFileToProject("E2EValSame.java",
                String.format(E2E_TWO_METHOD_SRC, "E2EValSame"));
        PsiJavaFile jf = (PsiJavaFile) file;
        PsiStatement[] stmts1 = ReadAction.compute(() -> jf.getClasses()[0].getMethods()[0].getBody().getStatements());
        PsiStatement[] stmts2 = ReadAction.compute(() -> jf.getClasses()[0].getMethods()[1].getBody().getStatements());

        DuplicateCodeFragment frag1 = new DuplicateCodeFragment(file, 3, 14, 80, "dup");
        frag1.setStatements(stmts1);
        DuplicateCodeFragment frag2 = new DuplicateCodeFragment(file, 17, 28, 80, "dup");
        frag2.setStatements(stmts2);

        DuplicateCodeGroup group = new DuplicateCodeGroup(80);
        group.addFragment(frag1);
        group.addFragment(frag2);
        Set<DuplicateCodeGroup> groups = new HashSet<>(Collections.singletonList(group));
        DuplicateCodeValidator.validate(groups, 0, 0);

        assertFalse("Validator must keep the group: fragments are extractable (>= 7 lines + valid PSI)",
                groups.isEmpty());
        assertEquals("Surviving group must retain both occurrences", 2,
                groups.iterator().next().getOccurrences());
    }

    public void testE2E_pipeline_sameClass_validateThenAnalyze() {
        PsiFile file = myFixture.addFileToProject("E2EPipeSame.java",
                String.format(E2E_TWO_METHOD_SRC, "E2EPipeSame"));
        PsiJavaFile jf = (PsiJavaFile) file;
        PsiStatement[] s1 = ReadAction.compute(() -> jf.getClasses()[0].getMethods()[0].getBody().getStatements());
        PsiStatement[] s2 = ReadAction.compute(() -> jf.getClasses()[0].getMethods()[1].getBody().getStatements());

        DuplicateCodeFragment f1 = new DuplicateCodeFragment(file, 3, 14, 80, "dup");
        f1.setStatements(s1);
        DuplicateCodeFragment f2 = new DuplicateCodeFragment(file, 17, 28, 80, "dup");
        f2.setStatements(s2);

        DuplicateCodeGroup group = new DuplicateCodeGroup(80);
        group.addFragment(f1);
        group.addFragment(f2);
        Set<DuplicateCodeGroup> groups = new HashSet<>(Collections.singletonList(group));
        DuplicateCodeValidator.validate(groups, 0, 0);
        assertFalse("Group must survive validation before context analysis", groups.isEmpty());

        DuplicateRefactoringStrategy.RefactoringContext ctx =
                ReadAction.compute(() -> DuplicateRefactoringStrategy.analyzeClasses(groups.iterator().next()));
        assertNotNull("analyzeClasses must return a context for a valid same-class group", ctx);
        assertTrue("Both fragments in the same class -> isSameClass must be true", ctx.isSameClass);
        assertEquals("Same-class group must have exactly one affected class", 1, ctx.affectedClasses.size());
    }

    public void testE2E_pipeline_crossClass_validateThenAnalyze() {
        String body =
                "    void compute(int[] arr) {\n" +
                "        int a = 0;\n" +
                "        int b = 0;\n" +
                "        int c = 1;\n" +
                "        int d = arr[0];\n" +
                "        for (int i = 0; i < arr.length; i++) {\n" +
                "            a = a + arr[i];\n" +
                "            b = b + 1;\n" +
                "            c = c * arr[i];\n" +
                "        }\n" +
                "        int e = a / b;\n" +
                "        int f = e + c + d;\n" +
                "    }\n";
        // body starts at line 2 of each file; last statement is at line 13 -> lineCount 12
        PsiFile fileX = myFixture.addFileToProject("E2ECrossX.java", "class E2ECrossX {\n" + body + "}\n");
        PsiFile fileY = myFixture.addFileToProject("E2ECrossY.java", "class E2ECrossY {\n" + body + "}\n");
        PsiStatement[] sX = ReadAction.compute(() -> ((PsiJavaFile) fileX).getClasses()[0].getMethods()[0].getBody().getStatements());
        PsiStatement[] sY = ReadAction.compute(() -> ((PsiJavaFile) fileY).getClasses()[0].getMethods()[0].getBody().getStatements());

        DuplicateCodeFragment fX = new DuplicateCodeFragment(fileX, 2, 13, 80, "dup");
        fX.setStatements(sX);
        DuplicateCodeFragment fY = new DuplicateCodeFragment(fileY, 2, 13, 80, "dup");
        fY.setStatements(sY);

        DuplicateCodeGroup group = new DuplicateCodeGroup(80);
        group.addFragment(fX);
        group.addFragment(fY);
        Set<DuplicateCodeGroup> groups = new HashSet<>(Collections.singletonList(group));
        DuplicateCodeValidator.validate(groups, 0, 0);
        assertFalse("Cross-class group must survive validation", groups.isEmpty());

        DuplicateRefactoringStrategy.RefactoringContext ctx =
                ReadAction.compute(() -> DuplicateRefactoringStrategy.analyzeClasses(groups.iterator().next()));
        assertNotNull("analyzeClasses must return context for cross-class group", ctx);
        assertFalse("Fragments in different classes -> isSameClass must be false", ctx.isSameClass);
        assertEquals("Cross-class group must have exactly 2 affected classes", 2, ctx.affectedClasses.size());
        assertTrue("Fragments from different files -> isCrossFile must be true",
                groups.iterator().next().isCrossFile());
    }

    public void testRangeAdjuster_codeText_containsAllExtractedStatements() {
        PsiFile file = myFixture.addFileToProject("CodeText.java",
                "class CodeText {\n" +
                "    void run() {\n" +
                "        int alpha = 10;\n" +   // line 3
                "        int beta  = 20;\n" +   // line 4
                "    }\n" +
                "}\n");
        Document doc = getDocument(file);
        List<DuplicateRangeAdjuster.AdjustedRange> ranges =
                ReadAction.compute(() -> DuplicateRangeAdjuster.adjustRangeWithLines(file, doc, 3, 4));
        assertFalse("Range must not be empty", ranges.isEmpty());
        String code = ranges.get(0).code;
        assertTrue("Code must contain first variable name",  code.contains("alpha"));
        assertTrue("Code must contain second variable name", code.contains("beta"));
    }

    public void testRangeAdjuster_forLoopBoundary_innerBodyExtracted() {
        PsiFile file = myFixture.addFileToProject("ForBody.java",
                "class ForBody {\n" +
                "    void loop() {\n" +
                "        for (int i = 0; i < 10; i++) {\n" +   // line 3
                "            int doubled = i * 2;\n" +           // line 4
                "            int tripled = i * 3;\n" +           // line 5
                "        }\n" +
                "    }\n" +
                "}\n");
        Document doc = getDocument(file);
        // Lines 4-5 are inside the for body; the adjuster must descend into the block.
        List<DuplicateRangeAdjuster.AdjustedRange> ranges =
                ReadAction.compute(() -> DuplicateRangeAdjuster.adjustRangeWithLines(file, doc, 4, 5));
        assertFalse("For-loop body range must yield at least one range", ranges.isEmpty());
        int total = ranges.stream().mapToInt(r -> r.statements.length).sum();
        assertEquals("For-loop body must yield exactly 2 inner statements", 2, total);
    }

    public void testGroup_listConstructor_storesFragmentsAndTokens() {
        PsiFile f1 = myFixture.addFileToProject("LC1.java", "class LC1 {}");
        PsiFile f2 = myFixture.addFileToProject("LC2.java", "class LC2 {}");
        List<DuplicateCodeFragment> frags = Arrays.asList(
                new DuplicateCodeFragment(f1, 1, 8, 80, "code"),
                new DuplicateCodeFragment(f2, 1, 8, 80, "code")
        );
        DuplicateCodeGroup group = new DuplicateCodeGroup(frags, 80);
        assertEquals("List constructor must store both fragments", 2, group.getOccurrences());
        assertEquals("List constructor must preserve token count", 80, group.getTokens());
        assertEquals(frags.get(0), group.getFragments().get(0));
        assertEquals(frags.get(1), group.getFragments().get(1));
    }

    public void testGroup_listConstructor_addDuplicateFragment_noGrowth() {
        PsiFile f = myFixture.addFileToProject("LCDedup.java", "class LCDedup {}");
        DuplicateCodeFragment frag = new DuplicateCodeFragment(f, 1, 5, 50, "code");
        DuplicateCodeGroup group = new DuplicateCodeGroup(Collections.singletonList(frag), 50);
        group.addFragment(frag); // must be deduplicated by addFragment
        assertEquals("Adding the same fragment again must not increase size", 1, group.getOccurrences());
    }

    public void testHandler_selectStrategy_sameClass_returnsExtractMethodStrategy() throws Exception {
        DuplicateCodeRefactoringHandler handler =
                new DuplicateCodeRefactoringHandler(myFixture.getProject());
        Method m = DuplicateCodeRefactoringHandler.class
                .getDeclaredMethod("selectStrategy", DuplicateRefactoringStrategy.RefactoringContext.class);
        m.setAccessible(true);

        DuplicateRefactoringStrategy.RefactoringContext ctx =
                new DuplicateRefactoringStrategy.RefactoringContext();
        ctx.isSameClass = true;

        DuplicateRefactoringStrategy strategy =
                (DuplicateRefactoringStrategy) m.invoke(handler, ctx);
        assertTrue("Same-class duplicates must use ExtractMethodStrategy",
                strategy instanceof ExtractMethodStrategy);
    }

    public void testHandler_selectStrategy_noCommonHierarchy_returnsUtilityMethodStrategy() throws Exception {
        DuplicateCodeRefactoringHandler handler =
                new DuplicateCodeRefactoringHandler(myFixture.getProject());
        Method m = DuplicateCodeRefactoringHandler.class
                .getDeclaredMethod("selectStrategy", DuplicateRefactoringStrategy.RefactoringContext.class);
        m.setAccessible(true);

        DuplicateRefactoringStrategy.RefactoringContext ctx =
                new DuplicateRefactoringStrategy.RefactoringContext();
        ctx.isSameClass = false;
        ctx.commonSuperClass = null;

        DuplicateRefactoringStrategy strategy =
                (DuplicateRefactoringStrategy) m.invoke(handler, ctx);
        assertTrue("Cross-class duplicates with no common hierarchy must use ExtractUtilityMethodStrategy",
                strategy instanceof ExtractUtilityMethodStrategy);
    }

    public void testHandler_selectStrategy_sourceSuperClass_returnsExtractAndPullUpStrategy() throws Exception {
        PsiFile superFile = myFixture.addFileToProject("HierSuper.java", "class HierSuper {}");
        PsiClass superCls = ReadAction.compute(() -> ((PsiJavaFile) superFile).getClasses()[0]);

        DuplicateCodeRefactoringHandler handler =
                new DuplicateCodeRefactoringHandler(myFixture.getProject());
        Method m = DuplicateCodeRefactoringHandler.class
                .getDeclaredMethod("selectStrategy", DuplicateRefactoringStrategy.RefactoringContext.class);
        m.setAccessible(true);

        DuplicateRefactoringStrategy.RefactoringContext ctx =
                new DuplicateRefactoringStrategy.RefactoringContext();
        ctx.isSameClass = false;
        ctx.commonSuperClass = superCls;
        ctx.superClassIsInSource = true;

        DuplicateRefactoringStrategy strategy =
                (DuplicateRefactoringStrategy) m.invoke(handler, ctx);
        assertTrue("Cross-class duplicates with in-source superclass must use ExtractAndPullUpStrategy",
                strategy instanceof ExtractAndPullUpStrategy);
    }

    public void testHandler_findCommonSuperClass_twoChildrenSameParent_returnsParent() throws Exception {
        myFixture.addFileToProject("CSCParent.java", "class CSCParent {}");
        PsiFile childAFile = myFixture.addFileToProject("CSCChildA.java", "class CSCChildA extends CSCParent {}");
        PsiFile childBFile = myFixture.addFileToProject("CSCChildB.java", "class CSCChildB extends CSCParent {}");
        PsiClass childA = ReadAction.compute(() -> ((PsiJavaFile) childAFile).getClasses()[0]);
        PsiClass childB = ReadAction.compute(() -> ((PsiJavaFile) childBFile).getClasses()[0]);

        DuplicateCodeRefactoringHandler handler =
                new DuplicateCodeRefactoringHandler(myFixture.getProject());
        Method m = DuplicateCodeRefactoringHandler.class
                .getDeclaredMethod("findCommonSuperClass", List.class);
        m.setAccessible(true);

        PsiClass result = (PsiClass) m.invoke(handler, Arrays.asList(childA, childB));
        assertNotNull("Must find CSCParent as common superclass", result);
        assertEquals("CSCParent", ReadAction.compute(result::getName));
    }

    public void testHandler_findCommonSuperClass_unrelatedClasses_returnsNull() throws Exception {
        PsiFile f1 = myFixture.addFileToProject("Unrel1.java", "class Unrel1 {}");
        PsiFile f2 = myFixture.addFileToProject("Unrel2.java", "class Unrel2 {}");
        PsiClass cls1 = ReadAction.compute(() -> ((PsiJavaFile) f1).getClasses()[0]);
        PsiClass cls2 = ReadAction.compute(() -> ((PsiJavaFile) f2).getClasses()[0]);

        DuplicateCodeRefactoringHandler handler =
                new DuplicateCodeRefactoringHandler(myFixture.getProject());
        Method m = DuplicateCodeRefactoringHandler.class
                .getDeclaredMethod("findCommonSuperClass", List.class);
        m.setAccessible(true);

        PsiClass result = (PsiClass) m.invoke(handler, Arrays.asList(cls1, cls2));
        assertNull("Classes sharing only Object must return null (Object is excluded)", result);
    }

    public void testHandler_findCommonSuperClass_deepHierarchy_returnsLowestCommonAncestor() throws Exception {
        myFixture.addFileToProject("DeepA.java", "class DeepA {}");
        myFixture.addFileToProject("DeepB.java", "class DeepB extends DeepA {}");
        PsiFile childC = myFixture.addFileToProject("DeepC.java", "class DeepC extends DeepB {}");
        PsiFile childD = myFixture.addFileToProject("DeepD.java", "class DeepD extends DeepB {}");
        PsiClass c = ReadAction.compute(() -> ((PsiJavaFile) childC).getClasses()[0]);
        PsiClass d = ReadAction.compute(() -> ((PsiJavaFile) childD).getClasses()[0]);

        DuplicateCodeRefactoringHandler handler =
                new DuplicateCodeRefactoringHandler(myFixture.getProject());
        Method m = DuplicateCodeRefactoringHandler.class
                .getDeclaredMethod("findCommonSuperClass", List.class);
        m.setAccessible(true);

        PsiClass result = (PsiClass) m.invoke(handler, Arrays.asList(c, d));
        assertNotNull("Should find DeepB as the lowest common ancestor", result);
        assertEquals("DeepB", ReadAction.compute(result::getName));
    }

    public void testValidator_mixedLineCountFragments_groupAlwaysRemoved() {
        PsiFile fileA = myFixture.addFileToProject("MixedA.java", "class MixedA {}");
        PsiFile fileB = myFixture.addFileToProject("MixedB.java", "class MixedB {}");
        DuplicateCodeGroup group = new DuplicateCodeGroup(100);
        group.addFragment(new DuplicateCodeFragment(fileA, 1, 7, 100, "code")); // 7 lines, no stmts → not refactorable
        group.addFragment(new DuplicateCodeFragment(fileB, 1, 3, 100, "code")); // 3 lines  → too short
        Set<DuplicateCodeGroup> groups = new HashSet<>(Collections.singletonList(group));
        DuplicateCodeValidator.validate(groups, 0, 0); 
        assertTrue("Group with no feasible fragments must be removed by validator", groups.isEmpty());
    }

    private DuplicateCodeFragment fragmentAt(String fileName, int startLine, int endLine) {
        String className = fileName.replace(".java", "");
        PsiFile file = myFixture.addFileToProject(fileName, "class " + className + " {}");
        return new DuplicateCodeFragment(file, startLine, endLine, 100, "placeholder");
    }

    private Document getDocument(PsiFile file) {
        return ReadAction.compute(() ->
                PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(file));
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return new ProjectDescriptor(LanguageLevel.JDK_1_8, false) {
            @Override
            public Sdk getSdk() {
                return JavaSdk.getInstance().createJdk("java 1.8", MOCK_JDK_HOME, false);
            }
        };
    }

    @Override
    protected String getTestDataPath() {
        return TEST_DATA_PATH;
    }
}