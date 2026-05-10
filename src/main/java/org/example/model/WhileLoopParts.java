package org.example.model;

public class WhileLoopParts {

    private final WhileLoopNode loop;
    private final ForElementNode condition;
    private final ForElementNode body;

    public WhileLoopParts(WhileLoopNode loop, ForElementNode condition, ForElementNode body) {
        this.loop = loop;
        this.condition = condition;
        this.body = body;
    }

    public WhileLoopNode getLoop() { return loop; }
    public ForElementNode getCondition() { return condition; }
    public ForElementNode getBody() { return body; }
}
