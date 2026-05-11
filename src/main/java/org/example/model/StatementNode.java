package org.example.model;

public class StatementNode {

    private final String code;
    private final int startLine;
    private final int endLine;
    private final int startColumn;
    private final int endColumn;

    public StatementNode(String code,
                         int startLine,
                         int endLine,
                         int startColumn,
                         int endColumn) {
        this.code = code;
        this.startLine = startLine;
        this.endLine = endLine;
        this.startColumn = startColumn;
        this.endColumn = endColumn;
    }

    public String getCode() {
        return code;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public int getEndColumn() {
        return endColumn;
    }

    @Override
    public String toString() {
        return "StatementNode{" +
                "code='" + code + '\'' +
                ", startLine=" + startLine +
                ", endLine=" + endLine +
                ", startColumn=" + startColumn +
                ", endColumn=" + endColumn +
                '}';
    }
}
