package org.example.model;

public class EmptyExpressionCandidate {

    /** The statement after which the empty expression will be inserted. */
    private final StatementNode statement;

    /**
     * The expression to insert as a dead statement (e.g. "a + b" or "(2 - 2) * 10").
     * Result is never used — pure dead code.
     */
    private final String deadExpression;

    /** True when deadExpression was derived from in-scope variables; false when fallback literal. */
    private final boolean expressionFromScope;

    public EmptyExpressionCandidate(StatementNode statement,
                                    String deadExpression,
                                    boolean expressionFromScope) {
        this.statement = statement;
        this.deadExpression = deadExpression;
        this.expressionFromScope = expressionFromScope;
    }

    public StatementNode getStatement()        { return statement; }
    public String getDeadExpression()          { return deadExpression; }
    public boolean isExpressionFromScope()     { return expressionFromScope; }

    @Override
    public String toString() {
        return "EmptyExpressionCandidate{"
                + "stmt=" + (statement != null ? statement.getCode() : "null")
                + ", expr='" + deadExpression + "'"
                + ", fromScope=" + expressionFromScope
                + '}';
    }
}
