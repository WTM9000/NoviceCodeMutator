package org.example.model;

public class BinaryOperatorNode {

    private final int id;
    private final String type;
    private final int startLine;
    private final int startColumn;
    private final int endColumn;

    public BinaryOperatorNode(int id, String type, int startLine, int startColumn, int endColumn) {
        this.id = id;
        this.type = type;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endColumn = endColumn;
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

    public int getEndColumn() { return endColumn; }

    @Override
    public String toString() {
        return "BinaryOperatorNode{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", startLine=" + startLine +
                ", startColumn=" + startColumn +
                ", endColumn=" + endColumn +
                '}';
    }
}
