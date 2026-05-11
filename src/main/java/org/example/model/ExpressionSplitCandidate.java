package org.example.model;

import org.example.model.BinaryOperationArgument;

public class ExpressionSplitCandidate {

    private final int expressionId;
    private final String operator;

    private final StatementNode expression;
    private final StatementNode statement;
    private final int scopeId;

    private final String expressionType;

    private final BinaryOperationArgument leftArgument;
    private final BinaryOperationArgument rightArgument;

    public ExpressionSplitCandidate(int expressionId,
                                    String operator,
                                    StatementNode expression,
                                    StatementNode statement,
                                    int scopeId,
                                    String expressionType,
                                    BinaryOperationArgument leftArgument,
                                    BinaryOperationArgument rightArgument) {
        this.expressionId = expressionId;
        this.operator = operator;
        this.expression = expression;
        this.statement = statement;
        this.scopeId = scopeId;
        this.expressionType = expressionType;
        this.leftArgument = leftArgument;
        this.rightArgument = rightArgument;
    }

    public int getExpressionId() { return expressionId; }
    public String getOperator() { return operator; }
    public StatementNode getExpression() { return expression; }
    public StatementNode getStatement() { return statement; }
    public int getScopeId() { return scopeId; }
    public String getExpressionType() { return expressionType; }
    public BinaryOperationArgument getLeftArgument() { return leftArgument; }
    public BinaryOperationArgument getRightArgument() { return rightArgument; }

    @Override
    public String toString() {
        return "ExpressionSplitCandidate{" +
                "expressionId=" + expressionId +
                ", operator='" + operator + '\'' +
                ", expression=" + expression +
                ", statement=" + statement +
                ", scopeId=" + scopeId +
                ", expressionType='" + expressionType + '\'' +
                '}';
    }
}