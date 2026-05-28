package org.example.neo4j.repository;

import org.example.model.CompoundConditionExpandCandidate;
import org.example.model.StatementNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompoundConditionExpandRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public CompoundConditionExpandRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<CompoundConditionExpandCandidate> findCandidates() {
        // Find if-statements without else whose condition is a pure && or || chain
        String cypher = """
                MATCH (ifNode:IfStatement)
                WHERE NOT (ifNode)-[:ELSE_STATEMENT]->()
                MATCH (ifNode)-[:CONDITION]->(cond:BinaryOperator)
                WHERE cond.operatorCode IN ["&&", "||"]
                MATCH (ifNode)-[:THEN_STATEMENT]->(thenBlock)
                RETURN ifNode, cond, thenBlock
                ORDER BY ifNode.startLine
                """;

        List<CompoundConditionExpandCandidate> result = new ArrayList<>();

        try (Session session = driver.session()) {
            Result queryResult = session.run(cypher);

            while (queryResult.hasNext()) {
                Record record = queryResult.next();

                Value ifValue   = record.get("ifNode");
                Value condValue = record.get("cond");
                Value thenValue = record.get("thenBlock");

                if (isAnyNull(ifValue, condValue, thenValue)) continue;

                String operatorCode = asNullableString(condValue.get("operatorCode"));
                CompoundConditionExpandCandidate.LogicalOperator op =
                        "&&".equals(operatorCode)
                                ? CompoundConditionExpandCandidate.LogicalOperator.AND
                                : CompoundConditionExpandCandidate.LogicalOperator.OR;

                // Flatten the binary operator tree into a list of leaf conditions
                List<String> conditions = flattenConditionTree(
                        session, condValue.asNode().id(), operatorCode);

                if (conditions == null || conditions.size() < 2) continue;

                result.add(new CompoundConditionExpandCandidate(
                        mapStatementNode(ifValue),
                        conditions,
                        op,
                        asNullableString(thenValue.get("code"))
                ));
            }
        }

        return result;
    }

    // Flatten left-associative BinaryOperator tree into ordered list of leaf codes
    // Returns null if the tree contains mixed operators (e.g. && and || together)
    private List<String> flattenConditionTree(Session session,
                                              long nodeId,
                                              String expectedOp) {
        String cypher = """
                MATCH (node)
                WHERE id(node) = $id
                OPTIONAL MATCH (node)-[:LHS]->(lhs)
                OPTIONAL MATCH (node)-[:RHS]->(rhs)
                RETURN node.operatorCode AS op,
                       node.code AS nodeCode,
                       lhs, id(lhs) AS lhsId, labels(lhs) AS lhsLabels,
                       rhs, id(rhs) AS rhsId, labels(rhs) AS rhsLabels
                """;

        Result result = session.run(cypher, java.util.Map.of("id", nodeId));
        if (!result.hasNext()) return null;

        Record record = result.next();
        String op = asNullableString(record.get("op"));

        // If this node is not a BinaryOperator of the expected type, it's a leaf
        if (op == null || !op.equals(expectedOp)) {
            String code = asNullableString(record.get("nodeCode"));
            if (code == null) return null;
            return new ArrayList<>(Collections.singletonList(code));
        }

        // Check for mixed operators — LHS or RHS BinaryOperator with different op
        List<String> lhsLabels = record.get("lhsLabels").asList(Value::asString);
        List<String> rhsLabels = record.get("rhsLabels").asList(Value::asString);

        Value lhsIdValue = record.get("lhsId");
        Value rhsIdValue = record.get("rhsId");

        if (lhsIdValue == null || lhsIdValue.isNull()
                || rhsIdValue == null || rhsIdValue.isNull()) {
            return null;
        }

        long lhsId = lhsIdValue.asLong();
        long rhsId = rhsIdValue.asLong();

        List<String> left = flattenConditionTree(session, lhsId, expectedOp);
        List<String> right = flattenConditionTree(session, rhsId, expectedOp);

        if (left == null || right == null) return null;

        List<String> combined = new ArrayList<>(left);
        combined.addAll(right);
        return combined;
    }

    private StatementNode mapStatementNode(Value value) {
        return new StatementNode(
                asNullableString(value.get("code")),
                asNullableInt(value.get("startLine")),
                asNullableInt(value.get("endLine")),
                asNullableInt(value.get("startColumn")),
                asNullableInt(value.get("endColumn"))
        );
    }

    private boolean isAnyNull(Value... values) {
        for (Value v : values) {
            if (v == null || v.isNull()) return true;
        }
        return false;
    }

    @Override
    public void close() { driver.close(); }
}
