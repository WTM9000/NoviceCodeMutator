package org.example.model;

public class SynchronizedVariablesCandidate {

    /** Neo4j internal ID of the function body block that owns the chosen statement. */
    private final int blockId;

    /** The statement that will be wrapped. */
    private final StatementNode statement;

    /**
     * The expression used for both sync assignments.
     * Maybe a fallback literal when no in-scope binary expression is found.
     */
    private final String syncExpression;

    /** True when syncExpression was taken from the graph; false when a fallback literal is used. */
    private final boolean expressionFromScope;

    public SynchronizedVariablesCandidate(int blockId,
                                          StatementNode statement,
                                          String syncExpression,
                                          boolean expressionFromScope) {
        this.blockId = blockId;
        this.statement = statement;
        this.syncExpression = syncExpression;
        this.expressionFromScope = expressionFromScope;
    }

    public int getBlockId()             { return blockId; }
    public StatementNode getStatement() { return statement; }
    public String getSyncExpression()   { return syncExpression; }
    public boolean isExpressionFromScope() { return expressionFromScope; }

    @Override
    public String toString() {
        return "SynchronizedVariablesCandidate{"
                + "blockId=" + blockId
                + ", stmt=" + (statement != null ? statement.getCode() : "null")
                + ", expr='" + syncExpression + "'"
                + ", fromScope=" + expressionFromScope
                + '}';
    }
}
