package org.example.neo4j.repository;

import org.example.model.SimpleAssignmentNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import java.util.ArrayList;
import java.util.List;

public class SimpleAssignmentRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public SimpleAssignmentRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    /**
     * Находит standalone-присваивания вида   y = x;
     * где оба операнда — простые идентификаторы (Reference),
     * lhs != rhs по имени, rhs не является вызовом или выражением с side effects.
     * спользуется мутацией RedundantAssignment  (y = x  →  y = x = x)
     * и будет переиспользован мутацией SynchronizedVariables.
     */
    public List<SimpleAssignmentNode> findSimpleIdentifierAssignments() {
        String cypher = """
            MATCH (assign:AssignExpression)
            MATCH (assign)-[:LHS]->(left:Reference)
            MATCH (assign)-[:RHS]->(right:Reference)
            WHERE left.code <> right.code
              AND NOT EXISTS {
                  MATCH (right)-[:AST*1..]->(anyChild)
              }
              AND NOT EXISTS {
                  MATCH (parent)-[:AST]->(assign)
                  WHERE parent:BinaryOperator
                     OR parent:UnaryOperator
                     OR parent:Call
              }
            RETURN
                id(assign)         AS nodeId,
                left.code          AS lhsCode,
                right.code         AS rhsCode,
                assign.startLine   AS startLine,
                assign.endLine     AS endLine,
                assign.startColumn AS startColumn,
                assign.endColumn   AS endColumn
            ORDER BY assign.startLine, assign.startColumn
            """;

        List<SimpleAssignmentNode> result = new ArrayList<>();

        try (Session session = driver.session()) {
            Result queryResult = session.run(cypher);

            while (queryResult.hasNext()) {
                Record record = queryResult.next();

                Value nodeId      = record.get("nodeId");
                Value lhsCode     = record.get("lhsCode");
                Value rhsCode     = record.get("rhsCode");
                Value startLine   = record.get("startLine");
                Value endLine     = record.get("endLine");
                Value startColumn = record.get("startColumn");
                Value endColumn   = record.get("endColumn");

                if (isAnyNull(nodeId, lhsCode, rhsCode, startLine, endLine)) {
                    continue;
                }

                result.add(new SimpleAssignmentNode(
                        nodeId.asInt(),
                        lhsCode.asString(),
                        rhsCode.asString(),
                        startLine.asInt(),
                        endLine.asInt(),
                        asNullableInt(startColumn),
                        asNullableInt(endColumn)
                ));
            }
        }

        return result;
    }

    private boolean isAnyNull(Value... values) {
        for (Value v : values) {
            if (v == null || v.isNull()) return true;
        }
        return false;
    }

    @Override
    public void close() {
        driver.close();
    }
}