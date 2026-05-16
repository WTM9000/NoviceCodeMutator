package org.example.neo4j.repository;

import org.example.model.DoWhileLoopNode;
import org.example.model.DoWhileLoopParts;
import org.example.model.ForElementNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.List;

public class DoWhileLoopRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public DoWhileLoopRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    /**
     * Returns all do-while loops found in the CPG.
     * In Fraunhofer CPG a do-while is represented as a WhileStatement
     * node whose isDoWhile property equals true.
     */
    public List<DoWhileLoopNode> findAllDoWhileLoops() {
        String cypher = """
                MATCH (n:DoStatement)
                RETURN id(n)          AS id,
                       n.code         AS code,
                       n.startLine    AS startLine,
                       n.startColumn  AS startColumn,
                       n.endLine      AS endLine,
                       n.endColumn    AS endColumn
                ORDER BY n.startLine, n.startColumn
                """;

        List<DoWhileLoopNode> result = new ArrayList<>();

        try (Session session = driver.session()) {
            Result rs = session.run(cypher);
            while (rs.hasNext()) {
                Record r = rs.next();
                result.add(new DoWhileLoopNode(
                        r.get("id").asInt(),
                        asNullableString(r.get("code")),
                        asNullableInt(r.get("startLine")),
                        asNullableInt(r.get("startColumn")),
                        asNullableInt(r.get("endLine")),
                        asNullableInt(r.get("endColumn"))
                ));
            }
        }

        return result;
    }

    /**
     * Fetches the condition and body nodes for a specific do-while loop.
     * The CONDITION edge leads to the loop guard expression;
     * the STATEMENT edge leads to the Block that is the loop body.
     */
    public DoWhileLoopParts findDoWhilePartsById(int loopId) {
        String cypher = """
                MATCH (n:DoStatement)
                WHERE id(n) = $loopId
                OPTIONAL MATCH (n)-[:CONDITION]->(c)
                OPTIONAL MATCH (n)-[:STATEMENT]->(s)
                RETURN n, c, s
                LIMIT 1
                """;

        try (Session session = driver.session()) {
            Result rs = session.run(cypher, java.util.Map.of("loopId", loopId));

            if (!rs.hasNext()) {
                return null;
            }

            Record record = rs.next();

            DoWhileLoopNode loop      = mapLoop(record.get("n"));
            ForElementNode  condition = mapElement("condition", record.get("c"));
            ForElementNode  body      = mapElement("body",      record.get("s"));

            return new DoWhileLoopParts(loop, condition, body);
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private DoWhileLoopNode mapLoop(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return new DoWhileLoopNode(
                (int) value.asNode().id(),
                asNullableString(value.get("code")),
                asNullableInt(value.get("startLine")),
                asNullableInt(value.get("startColumn")),
                asNullableInt(value.get("endLine")),
                asNullableInt(value.get("endColumn"))
        );
    }

    private ForElementNode mapElement(String role, Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return new ForElementNode(
                (int) value.asNode().id(),
                role,
                asNullableString(value.get("code")),
                asNullableInt(value.get("startLine")),
                asNullableInt(value.get("startColumn")),
                asNullableInt(value.get("endLine")),
                asNullableInt(value.get("endColumn"))
        );
    }

    @Override
    public void close() {
        driver.close();
    }
}