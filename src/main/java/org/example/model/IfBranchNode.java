package org.example.model;

public class IfBranchNode {

    private final StatementNode condition;
    private final StatementNode block;

    public IfBranchNode(StatementNode condition, StatementNode block) {
        this.condition = condition;
        this.block = block;
    }

    public StatementNode getCondition() {
        return condition;
    }

    public StatementNode getBlock() {
        return block;
    }

    @Override
    public String toString() {
        return "IfBranchNode{" +
                "condition=" + condition +
                ", block=" + block +
                '}';
    }
}
