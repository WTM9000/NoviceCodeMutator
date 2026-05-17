package org.example.model;

public class ContinueAntiIdiomCandidate {

    private final StatementNode loopNode;
    private final StatementNode loopBodyNode;
    private final StatementNode ifNode;
    private final StatementNode thenNode;
    private final StatementNode elseNode; // null if no plain else
    private final String guardCode;
    private final boolean hasElse;
    private final boolean trivialElse;
    private final ContinueAntiIdiomCandidate elseIfBranch; // null if no else-if chain

    public ContinueAntiIdiomCandidate(StatementNode loopNode,
                                      StatementNode loopBodyNode,
                                      StatementNode ifNode,
                                      StatementNode thenNode,
                                      StatementNode elseNode,
                                      String guardCode,
                                      boolean hasElse,
                                      boolean trivialElse,
                                      ContinueAntiIdiomCandidate elseIfBranch) {
        this.loopNode = loopNode;
        this.loopBodyNode = loopBodyNode;
        this.ifNode = ifNode;
        this.thenNode = thenNode;
        this.elseNode = elseNode;
        this.guardCode = guardCode;
        this.hasElse = hasElse;
        this.trivialElse = trivialElse;
        this.elseIfBranch = elseIfBranch;
    }

    public StatementNode getLoopNode() { return loopNode; }
    public StatementNode getLoopBodyNode() { return loopBodyNode; }
    public StatementNode getIfNode() { return ifNode; }
    public StatementNode getThenNode() { return thenNode; }
    public StatementNode getElseNode() { return elseNode; }
    public String getGuardCode() { return guardCode; }
    public boolean hasElse() { return hasElse; }
    public boolean isTrivialElse() { return trivialElse; }
    public ContinueAntiIdiomCandidate getElseIfBranch() { return elseIfBranch; }

    @Override
    public String toString() {
        return "ContinueAntiIdiomCandidate{" +
                "loopNode=" + loopNode +
                ", ifNode=" + ifNode +
                ", guardCode='" + guardCode + '\'' +
                ", hasElse=" + hasElse +
                ", trivialElse=" + trivialElse +
                ", hasElseIfBranch=" + (elseIfBranch != null) +
                '}';
    }
}