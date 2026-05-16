package org.example.model;

public class DoWhileLoopParts {

    private final DoWhileLoopNode loop;
    private final ForElementNode  condition;
    private final ForElementNode  body;

    public DoWhileLoopParts(DoWhileLoopNode loop,
                            ForElementNode condition,
                            ForElementNode body) {
        this.loop      = loop;
        this.condition = condition;
        this.body      = body;
    }

    public DoWhileLoopNode getLoop()      { return loop; }
    public ForElementNode  getCondition() { return condition; }
    public ForElementNode  getBody()      { return body; }
}