package org.example.model;

public class ForLoopNode {
    private final int id;
    private final String code;
    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;

    public ForLoopNode(int id, String code, int startLine, int startColumn, int endLine, int endColumn) {
        this.id = id;
        this.code = code;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public int getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    @Override
    public String toString() {
        return "ForLoopNode{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", startLine=" + startLine +
                ", startColumn=" + startColumn +
                ", endLine=" + endLine +
                ", endColumn=" + endColumn +
                '}';
    }
}
