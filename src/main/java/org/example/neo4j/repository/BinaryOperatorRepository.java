package org.example.neo4j.repository;

import org.example.model.BinaryOperationArgument;
import org.example.model.BinaryOperatorNode;
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
import java.util.Map;

public class BinaryOperatorRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public BinaryOperatorRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<BinaryOperatorNode> findSupportedBinaryOperators() {
        String cypher = """
                MATCH (n:BinaryOperator)
                WHERE n.name = "+"
                   OR n.name = "*"
                   OR n.name = "&&"
                   OR n.name = "||"
                RETURN id(n) AS id,
                       n.name AS type,
                       n.startLine AS startLine,
                       n.startColumn AS startColumn,
                       n.endColumn as endColumn
                """;

        List<BinaryOperatorNode> resultList = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();

                if (record.get("id").isNull()) {
                    return resultList;
                }

                int id = record.get("id").asInt();
                String type = asNullableString(record.get("type"));
                int startLine = record.get("startLine").asInt();
                int startColumn = record.get("startColumn").asInt();
                int endColumn = record.get("endColumn").asInt();

                resultList.add(new BinaryOperatorNode(id, type, startLine, startColumn, endColumn));
            }
        }

        return resultList;
    }

    public BinaryOperationArgument findLeftArgumentByOperationId(int operationId) {
        String cypher = """
                MATCH (n:BinaryOperator)
                WHERE id(n) = $operationId
                MATCH (n)-[:OPERATOR_BASE]->(b)
                RETURN b
                """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("operationId", operationId));

            if (!result.hasNext()) {
                return null;
            }

            Record record = result.next();
            Value value = record.get("b");

            return new BinaryOperationArgument(
                    asNullableString(value.get("code")),
                    value.get("startLine").asInt(),
                    value.get("startColumn").asInt(),
                    value.get("endLine").asInt(),
                    value.get("endColumn").asInt()
            );
        }
    }

    public BinaryOperationArgument findRightArgumentByOperationId(int operationId) {
        String cypher = """
                MATCH (n:BinaryOperator)
                WHERE id(n) = $operationId
                MATCH (n)-[:OPERATOR_ARGUMENTS]->(a)
                RETURN a
                """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("operationId", operationId));

            if (!result.hasNext()) {
                return null;
            }

            Record record = result.next();
            Value value = record.get("a");

            return new BinaryOperationArgument(
                    asNullableString(value.get("code")),
                    value.get("startLine").asInt(),
                    value.get("startColumn").asInt(),
                    value.get("endLine").asInt(),
                    value.get("endColumn").asInt()
            );
        }
    }

    @Override
    public void close() {
        driver.close();
    }
}
