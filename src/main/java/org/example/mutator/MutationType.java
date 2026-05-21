package org.example.mutator;

import java.util.ArrayList;
import java.util.Objects;

public enum MutationType {

    REDUNDANT_ASSIGNMENT("RedundantAssignment"),
    SYNCHRONIZED_VARIABLES("SynchronizedVariables"),
    EMPTY_EXPRESSION("EmptyExpression"),
    EMPTY_LOOP("EmptyLoop"),
    EMPTY_INITIALIZATION("EmptyInitialization"),
    CONTINUE_UNREACHABLE("ContinueUnreachable"),
    RETURN_UNREACHABLE("ReturnUnreachable"),
    VARIABLE_NAME_REPLACE("ReplaceNameVariables"),
    FOR_TO_WHILE("ForToWhile"),
    BOOLEAN_ASSIGNMENT_TO_IF("BooleanAssignmentToIf"),
    VARIABLE_DECLARATION_HOIST("VariableDeclarationHoist"),
    CONTINUE_ANTI_IDIOM("ContinueAntiIdiom"),
    ELSE_IF_FLATTEN("ElseIfFlatten"),
    IF_TO_WHILE_CONVERT("IfToWhileConvert"),
    DO_WHILE_TO_WHILE("DoWhileToWhile");

    private final String text;

    MutationType(String mutationName){
        text = mutationName;
    }

    public String value(){
        return text;
    }

    public static MutationType getByName(String name){
        for (MutationType mutation: values()){
            if (Objects.equals(mutation.text, name))
                return mutation;
        }
        return null;
    }

    public static ArrayList<String> allValues(){
        ArrayList<String> res = new ArrayList<>();
        for (MutationType mutation: values()){
            res.add(mutation.value());
        }
        return res;
    }
}
