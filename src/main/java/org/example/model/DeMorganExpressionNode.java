package org.example.model;

import org.example.model.BinaryOperationArgument;

public class DeMorganExpressionNode {

    private final int unaryOperatorId;
    private final int binaryOperatorId;
    private final String binaryOperatorType;

    private final int unaryStartLine;
    private final int unaryStartColumn;
    private final int unaryEndLine;
    private final int unaryEndColumn;

    private final BinaryOperationArgument leftArgument;
    private final BinaryOperationArgument rightArgument;

    public DeMorganExpressionNode(int unaryOperatorId,
                                  int binaryOperatorId,
                                  String binaryOperatorType,
                                  int unaryStartLine,
                                  int unaryStartColumn,
                                  int unaryEndLine,
                                  int unaryEndColumn,
                                  BinaryOperationArgument leftArgument,
                                  BinaryOperationArgument rightArgument) {
        this.unaryOperatorId = unaryOperatorId;
        this.binaryOperatorId = binaryOperatorId;
        this.binaryOperatorType = binaryOperatorType;
        this.unaryStartLine = unaryStartLine;
        this.unaryStartColumn = unaryStartColumn;
        this.unaryEndLine = unaryEndLine;
        this.unaryEndColumn = unaryEndColumn;
        this.leftArgument = leftArgument;
        this.rightArgument = rightArgument;
    }

    public int getUnaryOperatorId() { return unaryOperatorId; }
    public int getBinaryOperatorId() { return binaryOperatorId; }
    public String getBinaryOperatorType() { return binaryOperatorType; }
    public int getUnaryStartLine() { return unaryStartLine; }
    public int getUnaryStartColumn() { return unaryStartColumn; }
    public int getUnaryEndLine() { return unaryEndLine; }
    public int getUnaryEndColumn() { return unaryEndColumn; }
    public BinaryOperationArgument getLeftArgument() { return leftArgument; }
    public BinaryOperationArgument getRightArgument() { return rightArgument; }

    @Override
    public String toString() {
        return "DeMorganExpressionNode{" +
                "unaryOperatorId=" + unaryOperatorId +
                ", binaryOperatorType='" + binaryOperatorType + '\'' +
                ", unaryStartLine=" + unaryStartLine +
                ", unaryStartColumn=" + unaryStartColumn +
                '}';
    }
}