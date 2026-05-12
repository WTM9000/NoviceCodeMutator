package org.example.model;

public class SingleDeclarator {

    private final int declarationId;
    private final String variableName;
    private final boolean hasInitializer;
    private final String initializerCode; // null если нет инициализатора

    public SingleDeclarator(int declarationId,
                            String variableName,
                            boolean hasInitializer,
                            String initializerCode) {
        this.declarationId = declarationId;
        this.variableName = variableName;
        this.hasInitializer = hasInitializer;
        this.initializerCode = initializerCode;
    }

    public int getDeclarationId()    { return declarationId; }
    public String getVariableName()  { return variableName; }
    public boolean isHasInitializer(){ return hasInitializer; }
    public String getInitializerCode(){ return initializerCode; }

    @Override
    public String toString() {
        return "SingleDeclarator{" +
                "name='" + variableName + '\'' +
                ", hasInitializer=" + hasInitializer +
                ", initializerCode='" + initializerCode + '\'' +
                '}';
    }
}
