package org.example.model;

public class BooleanAssignmentCandidate {

    private final int assignmentId;

    private final StatementNode assignmentExpression;
    private final StatementNode statement;
    private final int scopeId;

    private final BinaryOperationArgument leftArgument;
    private final BinaryOperationArgument rightArgument;

    private final boolean oneLineDirectBody;
    private final boolean leftDeclaration;
    private final boolean returnContext;

    private final StatementNode declarationStatement;

    public BooleanAssignmentCandidate(int assignmentId,
                                      StatementNode assignmentExpression,
                                      StatementNode statement,
                                      int scopeId,
                                      BinaryOperationArgument leftArgument,
                                      BinaryOperationArgument rightArgument,
                                      boolean oneLineDirectBody,
                                      boolean leftDeclaration,
                                      boolean isReturn,
                                      StatementNode declarationStatement) {
        this.assignmentId = assignmentId;
        this.assignmentExpression = assignmentExpression;
        this.statement = statement;
        this.scopeId = scopeId;
        this.leftArgument = leftArgument;
        this.rightArgument = rightArgument;
        this.oneLineDirectBody = oneLineDirectBody;
        this.leftDeclaration = leftDeclaration;
        this.returnContext = isReturn;
        this.declarationStatement = declarationStatement;
    }

    public int getAssignmentId()                    { return assignmentId; }
    public StatementNode getAssignmentExpression()  { return assignmentExpression; }
    public StatementNode getStatement()             { return statement; }
    public int getScopeId()                         { return scopeId; }
    public BinaryOperationArgument getLeftArgument() { return leftArgument; }
    public BinaryOperationArgument getRightArgument() { return rightArgument; }
    public boolean isOneLineDirectBody()            { return oneLineDirectBody; }
    public boolean isLeftDeclaration()              { return leftDeclaration; }


    // + в конструктор, геттер, toString
    public boolean isReturnContext() { return returnContext; }
    public StatementNode getDeclarationStatement()  { return declarationStatement; }

    @Override
    public String toString() {
        return "BooleanAssignmentCandidate{" +
                "assignmentId=" + assignmentId +
                ", statement=" + statement +
                ", leftArgument=" + leftArgument +
                ", rightArgument=" + rightArgument +
                ", oneLineDirectBody=" + oneLineDirectBody +
                ", leftDeclaration=" + leftDeclaration +
                ", returnContext=" + returnContext +
                '}';
    }
}