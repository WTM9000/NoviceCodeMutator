package org.example.neo4j;

import org.example.model.ForElementNode;
import org.example.model.ForLoopNode;
import org.example.model.ForLoopParts;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ForLoopRepository extends NodeRepository implements AutoCloseable {
    private final Driver driver;

    public ForLoopRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<ForLoopNode> findAllForLoops() {
        String cypher = """
                MATCH (n:ForStatement)
                RETURN id(n) AS id,
                       n.code AS code,
                       n.startLine AS startLine,
                       n.startColumn AS startColumn,
                       n.endLine AS endLine,
                       n.endColumn AS endColumn
                ORDER BY n.startLine, n.startColumn
                """;

        List<ForLoopNode> resultList = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();
                resultList.add(new ForLoopNode(
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

    public ForLoopParts findForLoopPartsById(int loopId) {
        String cypher = """
                MATCH (n:ForStatement)
                WHERE id(n) = $loopId
                OPTIONAL MATCH (n)-[:STATEMENT]->(s)
                OPTIONAL MATCH (n)-[:INITIALIZER_STATEMENT]->(i)
                OPTIONAL MATCH (n)-[:CONDITION]->(c)
                OPTIONAL MATCH (n)-[:ITERATION_STATEMENT]->(t)
                RETURN n, s, i, c, t
                LIMIT 1
                """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, java.util.Map.of("loopId", loopId));

            if (!result.hasNext()) {
                return null;
            }

            Record record = result.next();

            ForLoopNode loop = mapLoop(record.get("n"));
            ForElementNode statement = mapElement("body", record.get("s"));
            ForElementNode initializer = mapElement("initializerStatement", record.get("i"));
            ForElementNode condition = mapElement("condition", record.get("c"));
            ForElementNode iteration = mapElement("iterationStatement", record.get("t"));

            return new ForLoopParts(loop, initializer, condition, iteration, statement);
        }
    }

    private ForLoopNode mapLoop(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }

        return new ForLoopNode(
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

    public boolean hasInitializerDeclarationConflict(int loopId, String variableName) {
        String cypher = """
            MATCH (n:ForStatement)-[:SCOPE]->(s)
            WHERE id(n) = $loopId
            MATCH (i:Declaration)-[:SCOPE]->(s)
            WHERE i.name = $variableName
            RETURN i
            LIMIT 25
            """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of(
                    "loopId", loopId,
                    "variableName", variableName
            ));

            return result.hasNext();
        }
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
