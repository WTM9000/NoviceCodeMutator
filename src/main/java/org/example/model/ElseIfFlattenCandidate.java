package org.example.model;

import java.util.List;

public class ElseIfFlattenCandidate {

    // Each branch: condition code + body code (condition is null for the final else)
    public static class Branch {
        private final String conditionCode; // null for bare else
        private final String bodyCode;
        private final int startLine;
        private final int endLine;

        public Branch(String conditionCode, String bodyCode, int startLine, int endLine) {
            this.conditionCode = conditionCode;
            this.bodyCode = bodyCode;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        public String getConditionCode() { return conditionCode; }
        public String getBodyCode()      { return bodyCode; }
        public int getStartLine()        { return startLine; }
        public int getEndLine()          { return endLine; }
        public boolean isFinalElse()     { return conditionCode == null; }
    }

    // The entire if/else-if/.../else statement span
    private final StatementNode ifChainNode;

    // Branches in order: if, else-if..., optional else
    private final List<Branch> branches;

    public ElseIfFlattenCandidate(StatementNode ifChainNode, List<Branch> branches) {
        this.ifChainNode = ifChainNode;
        this.branches = branches;
    }

    public StatementNode getIfChainNode() { return ifChainNode; }
    public List<Branch> getBranches()     { return branches; }

    @Override
    public String toString() {
        return "ElseIfFlattenCandidate{" +
                "ifChainNode=" + ifChainNode +
                ", branches=" + branches.size() +
                '}';
    }
}