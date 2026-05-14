package org.example.model;

public class SingleDeclarator {

    private final int declarationId;
    private final String variableName;
    private final String typeName;          // получен из графа, не из текста
    private final boolean hasInitializer;
    private final String initializerCode;

    public SingleDeclarator(int declarationId,
                            String variableName,
                            String typeName,
                            boolean hasInitializer,
                            String initializerCode) {
        this.declarationId    = declarationId;
        this.variableName     = variableName;
        this.typeName         = typeName;
        this.hasInitializer   = hasInitializer;
        this.initializerCode  = initializerCode;
    }

    public int    getDeclarationId()     { return declarationId; }
    public String getVariableName()      { return variableName; }
    public String getTypeName()          { return typeName; }
    public boolean isHasInitializer()    { return hasInitializer; }
    public String getInitializerCode()   { return initializerCode; }

    @Override
    public String toString() {
        return "SingleDeclarator{" +
                "name='" + variableName + '\'' +
                ", type='" + typeName + '\'' +
                ", hasInitializer=" + hasInitializer +
                ", initializerCode='" + initializerCode + '\'' +
                '}';
    }
}