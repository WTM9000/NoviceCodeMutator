package org.example.model;

import java.util.List;

public class EmptyInitializationCandidate {

    private final StatementNode declarationStatement;
    private final String variableName;
    private final String typeName;
    private final boolean hasInitializer;
    private final String initializerCode; // null if hasInitializer == false

    // Other declarators on the same line that must be preserved as-is
    private final List<SingleDeclarator> remainingDeclarators;

    public EmptyInitializationCandidate(StatementNode declarationStatement,
                                        String variableName,
                                        String typeName,
                                        boolean hasInitializer,
                                        String initializerCode,
                                        List<SingleDeclarator> remainingDeclarators) {
        this.declarationStatement  = declarationStatement;
        this.variableName          = variableName;
        this.typeName              = typeName;
        this.hasInitializer        = hasInitializer;
        this.initializerCode       = initializerCode;
        this.remainingDeclarators  = remainingDeclarators;
    }

    public StatementNode getDeclarationStatement()         { return declarationStatement; }
    public String getVariableName()                        { return variableName; }
    public String getTypeName()                            { return typeName; }
    public boolean isHasInitializer()                      { return hasInitializer; }
    public String getInitializerCode()                     { return initializerCode; }
    public List<SingleDeclarator> getRemainingDeclarators(){ return remainingDeclarators; }

    @Override
    public String toString() {
        return "EmptyInitializationCandidate{" +
                "declarationStatement=" + declarationStatement +
                ", variableName='" + variableName + '\'' +
                ", typeName='" + typeName + '\'' +
                ", hasInitializer=" + hasInitializer +
                ", initializerCode='" + initializerCode + '\'' +
                ", remainingDeclarators=" + remainingDeclarators +
                '}';
    }
}