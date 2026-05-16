package org.example.model;

public class WhileLoopCandidate {

    /** The IfStatement node: "if (y1 && y2) <body>" */
    private final StatementNode ifStatement;

    /** The ReturnStatement node immediately following the if in the same Block. */
    private final StatementNode returnStatement;

    /**
     * Left operand of the && condition (base): goes into while (<y1>).
     * Extracted from ifStatement.code by splitting on "&&".
     */
    private final String whileCondition;

    /**
     * Right operand of the && condition (argument): goes into inner if (<y2>).
     * For chained &&: everything to the right of the leftmost && goes here.
     */
    private final String innerIfCondition;

    public WhileLoopCandidate(StatementNode ifStatement,
                              StatementNode returnStatement,
                              String whileCondition,
                              String innerIfCondition) {
        this.ifStatement      = ifStatement;
        this.returnStatement  = returnStatement;
        this.whileCondition   = whileCondition;
        this.innerIfCondition = innerIfCondition;
    }

    public StatementNode getIfStatement()       { return ifStatement; }
    public StatementNode getReturnStatement()   { return returnStatement; }
    public String getWhileCondition()           { return whileCondition; }
    public String getInnerIfCondition()         { return innerIfCondition; }

    @Override
    public String toString() {
        return "WhileLoopCandidate{"
                + "whileCond='" + whileCondition + "'"
                + ", innerCond='" + innerIfCondition + "'"
                + ", ret=" + (returnStatement != null ? returnStatement.getCode() : "null")
                + '}';
    }
}