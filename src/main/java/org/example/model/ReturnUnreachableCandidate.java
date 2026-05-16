package org.example.model;

public class ReturnUnreachableCandidate {

    private final StatementNode returnStatement;
    private final String deadExpression;
    private final boolean expressionFromScope;

    /**
     * True when the return is a direct child of a braceless IfStatement or
     * LoopStatement (Variant A in CPG: parent -[:THEN_STMT / :BODY]-> ReturnStatement,
     * no intermediate Block node). The mutator must wrap the return + dead expr in braces.
     */
    private final boolean needsBraces;

    public ReturnUnreachableCandidate(StatementNode returnStatement,
                                      String deadExpression,
                                      boolean expressionFromScope,
                                      boolean needsBraces) {
        this.returnStatement = returnStatement;
        this.deadExpression = deadExpression;
        this.expressionFromScope = expressionFromScope;
        this.needsBraces = needsBraces;
    }

    public StatementNode getReturnStatement()  { return returnStatement; }
    public String getDeadExpression()          { return deadExpression; }
    public boolean isExpressionFromScope()     { return expressionFromScope; }
    public boolean isNeedsBraces()             { return needsBraces; }

    @Override
    public String toString() {
        return "ReturnUnreachableCandidate{"
                + "return=" + (returnStatement != null ? returnStatement.getCode() : "null")
                + ", deadExpr='" + deadExpression + "'"
                + ", fromScope=" + expressionFromScope
                + ", needsBraces=" + needsBraces
                + '}';
    }
}