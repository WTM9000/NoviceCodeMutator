package org.example.model;

public class ContinueAntiIdiomCandidate {

    private final StatementNode loopNode;
    private final StatementNode loopBodyNode;
    private final StatementNode ifNode;
    private final StatementNode thenNode;
    private final StatementNode elseNode; // may be null
    private final String guardCode;
    private final boolean hasElse;
    private final boolean trivialElse;

    public ContinueAntiIdiomCandidate(StatementNode loopNode,
                                      StatementNode loopBodyNode,
                                      StatementNode ifNode,
                                      StatementNode thenNode,
                                      StatementNode elseNode,
                                      String guardCode,
                                      boolean hasElse,
                                      boolean trivialElse) {
        this.loopNode = loopNode;
        this.loopBodyNode = loopBodyNode;
        this.ifNode = ifNode;
        this.thenNode = thenNode;
        this.elseNode = elseNode;
        this.guardCode = guardCode;
        this.hasElse = hasElse;
        this.trivialElse = trivialElse;
    }

    public StatementNode getLoopNode() {
        return loopNode;
    }

    public StatementNode getLoopBodyNode() {
        return loopBodyNode;
    }

    public StatementNode getIfNode() {
        return ifNode;
    }

    public StatementNode getThenNode() {
        return thenNode;
    }

    public StatementNode getElseNode() {
        return elseNode;
    }

    public String getGuardCode() {
        return guardCode;
    }

    public boolean hasElse() {
        return hasElse;
    }

    public boolean isTrivialElse() {
        return trivialElse;
    }

    @Override
    public String toString() {
        return "ContinueAntiIdiomCandidate{" +
                "loopNode=" + loopNode +
                ", ifNode=" + ifNode +
                ", guardCode='" + guardCode + '\'' +
                ", hasElse=" + hasElse +
                ", trivialElse=" + trivialElse +
                '}';
    }
}