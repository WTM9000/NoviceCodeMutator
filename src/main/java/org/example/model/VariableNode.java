package org.example.model;

import java.util.Map;

public class VariableNode {
    private final int id;
    private final String name;
    private final int line;
    private final int column;

    public VariableNode(int id, String name, int line, int column) {
        this.id = id;
        this.name = name;
        this.line = line;
        this.column = column;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getLine() {
        return line;
    }

    public int getColumn(){
        return column;
    }


    @Override
    public String toString() {
        return "VariableNode{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", line=" + line +
                ", column=" + column +
                '}';
    }
}
