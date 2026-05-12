package org.example.model;

public class IfRootNode {

    private final int id;
    private final StatementNode statement;

    public IfRootNode(int id, StatementNode statement) {
        this.id = id;
        this.statement = statement;
    }

    public int getId() {
        return id;
    }

    public StatementNode getStatement() {
        return statement;
    }

    @Override
    public String toString() {
        return "IfRootNode{" +
                "id=" + id +
                ", statement=" + statement +
                '}';
    }
}
