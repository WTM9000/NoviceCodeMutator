package org.example.model;

public class EmptyLoopCandidate {

    /** The statement after which the empty loop will be inserted. */
    private final StatementNode statement;

    /**
     * The condition used in both the while-header and the inner if.
     * E.g. "x" (from scope) or "1" (fallback literal).
     */
    private final String loopCondition;

    /** True when loopCondition was taken from an in-scope variable; false when fallback. */
    private final boolean conditionFromScope;

    public EmptyLoopCandidate(StatementNode statement,
                              String loopCondition,
                              boolean conditionFromScope) {
        this.statement = statement;
        this.loopCondition = loopCondition;
        this.conditionFromScope = conditionFromScope;
    }

    public StatementNode getStatement()       { return statement; }
    public String getLoopCondition()          { return loopCondition; }
    public boolean isConditionFromScope()     { return conditionFromScope; }

    @Override
    public String toString() {
        return "EmptyLoopCandidate{"
                + "stmt=" + (statement != null ? statement.getCode() : "null")
                + ", cond='" + loopCondition + "'"
                + ", fromScope=" + conditionFromScope
                + '}';
    }
}