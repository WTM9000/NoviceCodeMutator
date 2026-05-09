package org.example.model;

public class BinaryOperationArgument {

    private final String code;
    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;

    public BinaryOperationArgument(String code,
                                   int startLine,
                                   int startColumn,
                                   int endLine,
                                   int endColumn) {
        this.code = code;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
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
        return "BinaryOperationArgument{" +
                "code='" + code + '\'' +
                ", startLine=" + startLine +
                ", startColumn=" + startColumn +
                ", endLine=" + endLine +
                ", endColumn=" + endColumn +
                '}';
    }
}