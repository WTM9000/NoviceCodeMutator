package org.example.mutator;

import java.util.ArrayList;
import java.util.Objects;

public enum MutationType {
    VARIABLE_NAME_REPLACE("ReplaceNameVariables"),
    VARIABLE_ALGEBRAIC_WRAP("VariableAlgebraicWrap"),
    FOR_TO_WHILE("ForToWhile");

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
