package org.example.model;

import java.util.List;

public class IfElseChainNode {

    private final int rootIfId;
    private final StatementNode wholeStatement;
    private final List<IfBranchNode> branches;
    private final StatementNode elseBlock;

    public IfElseChainNode(int rootIfId,
                           StatementNode wholeStatement,
                           List<IfBranchNode> branches,
                           StatementNode elseBlock) {
        this.rootIfId = rootIfId;
        this.wholeStatement = wholeStatement;
        this.branches = branches;
        this.elseBlock = elseBlock;
    }

    public int getRootIfId() {
        return rootIfId;
    }

    public StatementNode getWholeStatement() {
        return wholeStatement;
    }

    public List<IfBranchNode> getBranches() {
        return branches;
    }

    public StatementNode getElseBlock() {
        return elseBlock;
    }

    @Override
    public String toString() {
        return "IfElseChainNode{" +
                "rootIfId=" + rootIfId +
                ", wholeStatement=" + wholeStatement +
                ", branchesCount=" + (branches == null ? 0 : branches.size()) +
                ", hasElseBlock=" + (elseBlock != null) +
                '}';
    }
}
