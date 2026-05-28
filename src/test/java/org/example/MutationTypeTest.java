package org.example;

import org.example.mutator.MutationType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MutationTypeTest {

    @Test
    void value_returnsCorrectTextForEachVariant() {
        assertEquals("RedundantAssignment",      MutationType.REDUNDANT_ASSIGNMENT.value());
        assertEquals("SynchronizedVariables",    MutationType.SYNCHRONIZED_VARIABLES.value());
        assertEquals("EmptyExpression",          MutationType.EMPTY_EXPRESSION.value());
        assertEquals("EmptyLoop",                MutationType.EMPTY_LOOP.value());
        assertEquals("EmptyInitialization",      MutationType.EMPTY_INITIALIZATION.value());
        assertEquals("ContinueUnreachable",      MutationType.CONTINUE_UNREACHABLE.value());
        assertEquals("ReturnUnreachable",        MutationType.RETURN_UNREACHABLE.value());
        assertEquals("ReplaceNameVariables",     MutationType.VARIABLE_NAME_REPLACE.value());
        assertEquals("ForToWhile",               MutationType.FOR_TO_WHILE.value());
        assertEquals("BooleanAssignmentToIf",    MutationType.BOOLEAN_ASSIGNMENT_TO_IF.value());
        assertEquals("VariableDeclarationHoist", MutationType.VARIABLE_DECLARATION_HOIST.value());
        assertEquals("ContinueAntiIdiom",        MutationType.CONTINUE_ANTI_IDIOM.value());
        assertEquals("ElseIfFlatten",            MutationType.ELSE_IF_FLATTEN.value());
        assertEquals("CompoundConditionExpand",  MutationType.COMPOUND_CONDITION_EXPAND.value());
        assertEquals("IfToWhileConvert",         MutationType.IF_TO_WHILE_CONVERT.value());
        assertEquals("DoWhileToWhile",           MutationType.DO_WHILE_TO_WHILE.value());
    }

    @Test
    void getByName_returnsCorrectEnumForKnownName() {
        assertEquals(MutationType.FOR_TO_WHILE,
                MutationType.getByName("ForToWhile"));
        assertEquals(MutationType.EMPTY_LOOP,
                MutationType.getByName("EmptyLoop"));
        assertEquals(MutationType.VARIABLE_NAME_REPLACE,
                MutationType.getByName("ReplaceNameVariables"));
    }

    @Test
    void getByName_returnsNullForUnknownName() {
        assertNull(MutationType.getByName("NonExistent"));
        assertNull(MutationType.getByName(""));
        assertNull(MutationType.getByName(null));
    }

    @Test
    void getByName_isCaseSensitive() {
        assertNull(MutationType.getByName("foRtowhile"));
        assertNull(MutationType.getByName("FORTOWHILE"));
    }

    @Test
    void allValues_containsAllSixteenEntries() {
        ArrayList<String> all = MutationType.allValues();
        assertEquals(16, all.size());
    }

    @Test
    void allValues_containsExpectedStrings() {
        ArrayList<String> all = MutationType.allValues();
        assertTrue(all.contains("ForToWhile"));
        assertTrue(all.contains("DoWhileToWhile"));
        assertTrue(all.contains("CompoundConditionExpand"));
        assertFalse(all.contains("NonExistent"));
    }
}
