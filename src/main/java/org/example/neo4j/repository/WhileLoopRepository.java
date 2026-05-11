package org.example.neo4j.repository;

import org.example.model.ForElementNode;
import org.example.model.WhileLoopNode;
import org.example.model.WhileLoopParts;
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

public class WhileLoopRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public WhileLoopRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<WhileLoopNode> findAllWhileLoops() {
        String cypher = """
                MATCH (n:WhileStatement)
                RETURN id(n) AS id,
                       n.code AS code,
                       n.startLine AS startLine,
                       n.startColumn AS startColumn,
                       n.endLine AS endLine,
                       n.endColumn AS endColumn
                ORDER BY n.startLine, n.startColumn
                """;

        List<WhileLoopNode> resultList = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();
                resultList.add(new WhileLoopNode(
                        record.get("id").asInt(),
                        asNullableString(record.get("code")),
                        asNullableInt(record.get("startLine")),
                        asNullableInt(record.get("startColumn")),
                        asNullableInt(record.get("endLine")),
                        asNullableInt(record.get("endColumn"))
                ));
            }
        }

        return resultList;
    }

    public WhileLoopParts findWhileLoopPartsById(int loopId) {
        String cypher = """
                MATCH (n:WhileStatement)
                WHERE id(n) = $loopId
                OPTIONAL MATCH (n)-[:CONDITION]->(c)
                OPTIONAL MATCH (n)-[:STATEMENT]->(s)
                RETURN n, c, s
                LIMIT 1
                """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, java.util.Map.of("loopId", loopId));

            if (!result.hasNext()) {
                return null;
            }

            Record record = result.next();

            WhileLoopNode loop = mapLoop(record.get("n"));
            ForElementNode condition = mapElement("condition", record.get("c"));
            ForElementNode body = mapElement("body", record.get("s"));

            return new WhileLoopParts(loop, condition, body);
        }
    }

    private WhileLoopNode mapLoop(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return new WhileLoopNode(
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

    private String asNullableString(Value value) {
        return value == null || value.isNull() ? null : value.asString();
    }

    private int asNullableInt(Value value) {
        return value == null || value.isNull() ? -1 : value.asInt();
    }

    @Override
    public void close() {
        driver.close();
    }
}
