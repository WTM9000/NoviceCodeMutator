package org.example.mutator;

import java.util.ArrayList;
import java.util.Objects;

public enum MutationType {
    VARIABLE_NAME_REPLACE("ReplaceNameVariables"),
    VARIABLE_ALGEBRAIC_WRAP("VariableAlgebraicWrap"),
    BINARY_OPERATOR_COMMUTE("BinaryOperatorCommute"),
    FOR_TO_WHILE("ForToWhile"),
    WHILE_TO_FOR("WhileToFor"),
    DE_MORGAN("DeMorgan"),
    NEGATED_COMPARISON("NegatedComparison"),
    TERNARY_TO_IF_ELSE("TernaryToIfElse"),
    EXPRESSION_SPLIT("ExpressionSplit"),
    IF_ELSE_TO_SEQUENTIAL_IF("IfElseToSequentialIf"),
    BOOLEAN_ASSIGNMENT_TO_IF("BooleanAssignmentToIf");

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
