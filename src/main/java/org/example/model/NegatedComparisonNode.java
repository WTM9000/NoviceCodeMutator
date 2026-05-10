package org.example.model;

import org.example.model.BinaryOperationArgument;

public class NegatedComparisonNode {

    private final int unaryOperatorId;
    private final int binaryOperatorId;
    private final String comparisonOperator;

    private final int unaryStartLine;
    private final int unaryStartColumn;
    private final int unaryEndLine;
    private final int unaryEndColumn;

    private final BinaryOperationArgument leftArgument;
    private final BinaryOperationArgument rightArgument;

    public NegatedComparisonNode(int unaryOperatorId,
                                 int binaryOperatorId,
                                 String comparisonOperator,
                                 int unaryStartLine,
                                 int unaryStartColumn,
                                 int unaryEndLine,
                                 int unaryEndColumn,
                                 BinaryOperationArgument leftArgument,
                                 BinaryOperationArgument rightArgument) {
        this.unaryOperatorId = unaryOperatorId;
        this.binaryOperatorId = binaryOperatorId;
        this.comparisonOperator = comparisonOperator;
        this.unaryStartLine = unaryStartLine;
        this.unaryStartColumn = unaryStartColumn;
        this.unaryEndLine = unaryEndLine;
        this.unaryEndColumn = unaryEndColumn;
        this.leftArgument = leftArgument;
        this.rightArgument = rightArgument;
    }

    public int getUnaryOperatorId() { return unaryOperatorId; }
    public int getBinaryOperatorId() { return binaryOperatorId; }
    public String getComparisonOperator() { return comparisonOperator; }
    public int getUnaryStartLine() { return unaryStartLine; }
    public int getUnaryStartColumn() { return unaryStartColumn; }
    public int getUnaryEndLine() { return unaryEndLine; }
    public int getUnaryEndColumn() { return unaryEndColumn; }
    public BinaryOperationArgument getLeftArgument() { return leftArgument; }
    public BinaryOperationArgument getRightArgument() { return rightArgument; }

    @Override
    public String toString() {
        return "NegatedComparisonNode{" +
                "unaryOperatorId=" + unaryOperatorId +
                ", comparisonOperator='" + comparisonOperator + '\'' +
                ", unaryStartLine=" + unaryStartLine +
                ", unaryStartColumn=" + unaryStartColumn +
                '}';
    }
}
