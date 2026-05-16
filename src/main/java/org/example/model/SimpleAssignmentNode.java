package org.example.model;

public class SimpleAssignmentNode {

    private final int    nodeId;
    private final String lhsCode;
    private final String rhsCode;
    private final int    startLine;
    private final int    endLine;
    private final int    startColumn;
    private final int    endColumn;

    public SimpleAssignmentNode(
            int    nodeId,
            String lhsCode,
            String rhsCode,
            int    startLine,
            int    endLine,
            int    startColumn,
            int    endColumn
    ) {
        this.nodeId      = nodeId;
        this.lhsCode     = lhsCode;
        this.rhsCode     = rhsCode;
        this.startLine   = startLine;
        this.endLine     = endLine;
        this.startColumn = startColumn;
        this.endColumn   = endColumn;
    }

    public int    getNodeId()      { return nodeId;      }
    public String getLhsCode()     { return lhsCode;     }
    public String getRhsCode()     { return rhsCode;     }
    public int    getStartLine()   { return startLine;   }
    public int    getEndLine()     { return endLine;     }
    public int    getStartColumn() { return startColumn; }
    public int    getEndColumn()   { return endColumn;   }

    @Override
    public String toString() {
        return "SimpleAssignmentNode{" +
                "nodeId=" + nodeId +
                ", lhs='" + lhsCode + '\'' +
                ", rhs='" + rhsCode + '\'' +
                ", lines=" + startLine + "-" + endLine +
                '}';
    }
}