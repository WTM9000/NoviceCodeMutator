package org.example.model;

public class ContinueUnreachableCandidate {

    /**
     * The last statement in the loop body — continue and dead code are inserted after it.
     */
    private final StatementNode lastStatement;

    /**
     * The dead expression placed after continue (e.g. "x" as "x++;" or a fallback literal).
     * Always rendered as "<expr>;" in the output.
     */
    private final String deadExpression;

    /** True when deadExpression was derived from an in-scope variable; false when fallback. */
    private final boolean expressionFromScope;

    public ContinueUnreachableCandidate(StatementNode lastStatement,
                                        String deadExpression,
                                        boolean expressionFromScope) {
        this.lastStatement = lastStatement;
        this.deadExpression = deadExpression;
        this.expressionFromScope = expressionFromScope;
    }

    public StatementNode getLastStatement()    { return lastStatement; }
    public String getDeadExpression()          { return deadExpression; }
    public boolean isExpressionFromScope()     { return expressionFromScope; }

    @Override
    public String toString() {
        return "ContinueUnreachableCandidate{"
                + "lastStmt=" + (lastStatement != null ? lastStatement.getCode() : "null")
                + ", deadExpr='" + deadExpression + "'"
                + ", fromScope=" + expressionFromScope
                + '}';
    }
}