package org.example.model;

import java.util.List;

public class CompoundConditionExpandCandidate {

    public enum LogicalOperator { AND, OR }

    private final StatementNode ifNode;
    private final List<String> conditions; // [C1, C2, ..., Cn]
    private final LogicalOperator operator;
    private final String bodyCode;

    public CompoundConditionExpandCandidate(StatementNode ifNode,
                                            List<String> conditions,
                                            LogicalOperator operator,
                                            String bodyCode) {
        this.ifNode = ifNode;
        this.conditions = conditions;
        this.operator = operator;
        this.bodyCode = bodyCode;
    }

    public StatementNode getIfNode()           { return ifNode; }
    public List<String> getConditions()        { return conditions; }
    public LogicalOperator getOperator()       { return operator; }
    public String getBodyCode()                { return bodyCode; }

    @Override
    public String toString() {
        return "CompoundConditionExpandCandidate{" +
                "ifNode=" + ifNode +
                ", operator=" + operator +
                ", conditions=" + conditions +
                '}';
    }
}
