package org.example.model;

public class TernaryExpressionNode {

    private final int ternaryId;

    private final int ternaryStartLine;
    private final int ternaryStartColumn;
    private final int ternaryEndLine;
    private final int ternaryEndColumn;

    private final BinaryOperationArgument condition;
    private final BinaryOperationArgument thenBranch;
    private final BinaryOperationArgument elseBranch;

    // Контекст объявления: заполняется если тернарный стоит
    // как инициализатор декларации, например: int x = cond ? a : b;
    private final boolean isDeclaration;
    private final String declarationTypeName; // "int", "float", ...
    private final String declarationVarName;  // "x"

    public TernaryExpressionNode(int ternaryId,
                                 int ternaryStartLine,
                                 int ternaryStartColumn,
                                 int ternaryEndLine,
                                 int ternaryEndColumn,
                                 BinaryOperationArgument condition,
                                 BinaryOperationArgument thenBranch,
                                 BinaryOperationArgument elseBranch,
                                 boolean isDeclaration,
                                 String declarationTypeName,
                                 String declarationVarName) {
        this.ternaryId            = ternaryId;
        this.ternaryStartLine     = ternaryStartLine;
        this.ternaryStartColumn   = ternaryStartColumn;
        this.ternaryEndLine       = ternaryEndLine;
        this.ternaryEndColumn     = ternaryEndColumn;
        this.condition            = condition;
        this.thenBranch           = thenBranch;
        this.elseBranch           = elseBranch;
        this.isDeclaration        = isDeclaration;
        this.declarationTypeName  = declarationTypeName;
        this.declarationVarName   = declarationVarName;
    }

    public int    getTernaryId()           { return ternaryId; }
    public int    getTernaryStartLine()    { return ternaryStartLine; }
    public int    getTernaryStartColumn()  { return ternaryStartColumn; }
    public int    getTernaryEndLine()      { return ternaryEndLine; }
    public int    getTernaryEndColumn()    { return ternaryEndColumn; }
    public BinaryOperationArgument getCondition()   { return condition; }
    public BinaryOperationArgument getThenBranch()  { return thenBranch; }
    public BinaryOperationArgument getElseBranch()  { return elseBranch; }
    public boolean isDeclaration()         { return isDeclaration; }
    public String  getDeclarationTypeName(){ return declarationTypeName; }
    public String  getDeclarationVarName() { return declarationVarName; }

    @Override
    public String toString() {
        return "TernaryExpressionNode{" +
                "ternaryId=" + ternaryId +
                ", ternaryStartLine=" + ternaryStartLine +
                ", ternaryStartColumn=" + ternaryStartColumn +
                ", isDeclaration=" + isDeclaration +
                (isDeclaration
                        ? ", type='" + declarationTypeName + "', var='" + declarationVarName + "'"
                        : "") +
                '}';
    }
}