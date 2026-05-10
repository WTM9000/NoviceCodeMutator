package org.example.model;

import org.example.model.BinaryOperationArgument;

public class TernaryExpressionNode {

    private final int ternaryId;

    private final int ternaryStartLine;
    private final int ternaryStartColumn;
    private final int ternaryEndLine;
    private final int ternaryEndColumn;

    private final BinaryOperationArgument condition;
    private final BinaryOperationArgument thenBranch;
    private final BinaryOperationArgument elseBranch;

    public TernaryExpressionNode(int ternaryId,
                                 int ternaryStartLine,
                                 int ternaryStartColumn,
                                 int ternaryEndLine,
                                 int ternaryEndColumn,
                                 BinaryOperationArgument condition,
                                 BinaryOperationArgument thenBranch,
                                 BinaryOperationArgument elseBranch) {
        this.ternaryId = ternaryId;
        this.ternaryStartLine = ternaryStartLine;
        this.ternaryStartColumn = ternaryStartColumn;
        this.ternaryEndLine = ternaryEndLine;
        this.ternaryEndColumn = ternaryEndColumn;
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public int getTernaryId() { return ternaryId; }
    public int getTernaryStartLine() { return ternaryStartLine; }
    public int getTernaryStartColumn() { return ternaryStartColumn; }
    public int getTernaryEndLine() { return ternaryEndLine; }
    public int getTernaryEndColumn() { return ternaryEndColumn; }
    public BinaryOperationArgument getCondition() { return condition; }
    public BinaryOperationArgument getThenBranch() { return thenBranch; }
    public BinaryOperationArgument getElseBranch() { return elseBranch; }

    @Override
    public String toString() {
        return "TernaryExpressionNode{" +
                "ternaryId=" + ternaryId +
                ", ternaryStartLine=" + ternaryStartLine +
                ", ternaryStartColumn=" + ternaryStartColumn +
                '}';
    }
}
