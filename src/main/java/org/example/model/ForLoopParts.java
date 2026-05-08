package org.example.model;

public class ForLoopParts {
    private final ForLoopNode loop;
    private final ForElementNode initializerStatement;
    private final ForElementNode condition;
    private final ForElementNode iterationStatement;
    private final ForElementNode body;

    public ForLoopParts(ForLoopNode loop,
                        ForElementNode initializerStatement,
                        ForElementNode condition,
                        ForElementNode iterationStatement,
                        ForElementNode body) {
        this.loop = loop;
        this.initializerStatement = initializerStatement;
        this.condition = condition;
        this.iterationStatement = iterationStatement;
        this.body = body;
    }

    public ForLoopNode getLoop() {
        return loop;
    }

    public ForElementNode getInitializerStatement() {
        return initializerStatement;
    }

    public ForElementNode getCondition() {
        return condition;
    }

    public ForElementNode getIterationStatement() {
        return iterationStatement;
    }

    public ForElementNode getBody() {
        return body;
    }
}
