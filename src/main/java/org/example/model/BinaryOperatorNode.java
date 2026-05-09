package org.example.model;

public class BinaryOperatorNode {

    private final int id;
    private final String type;
    private final int startLine;
    private final int startColumn;

    public BinaryOperatorNode(int id, String type, int startLine, int startColumn) {
        this.id = id;
        this.type = type;
        this.startLine = startLine;
        this.startColumn = startColumn;
    }

    public int getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    @Override
    public String toString() {
        return "BinaryOperatorNode{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", startLine=" + startLine +
                ", startColumn=" + startColumn +
                '}';
    }
}
