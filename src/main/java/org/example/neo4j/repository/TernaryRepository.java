package org.example.neo4j.repository;

import org.example.model.BinaryOperationArgument;
import org.example.model.TernaryExpressionNode;
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

public class TernaryRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public TernaryRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<TernaryExpressionNode> findAllTernaryExpressions() {
        String cypher = """
                MATCH (t:ConditionalExpression)
                OPTIONAL MATCH (t)-[:CONDITION]->(cond)
                OPTIONAL MATCH (t)-[:THEN_EXPRESSION]->(thenBranch)
                OPTIONAL MATCH (t)-[:ELSE_EXPRESSION]->(elseBranch)
                RETURN t, cond, thenBranch, elseBranch
                ORDER BY t.startLine, t.startColumn
                """;

        List<TernaryExpressionNode> resultList = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();

                Value t = record.get("t");
                Value cond = record.get("cond");
                Value thenVal = record.get("thenBranch");
                Value elseVal = record.get("elseBranch");

                if (t.isNull() || cond.isNull() || thenVal.isNull() || elseVal.isNull()) {
                    continue;
                }

                resultList.add(new TernaryExpressionNode(
                        (int) t.asNode().id(),
                        asNullableInt(cond.get("startLine")),
                        asNullableInt(cond.get("startColumn"))-1,
                        asNullableInt(elseVal.get("endLine")),
                        asNullableInt(elseVal.get("endColumn")),
                        mapArgument(cond),
                        mapArgument(thenVal),
                        mapArgument(elseVal)
                ));
            }
        }

        return resultList;
    }

    private BinaryOperationArgument mapArgument(Value value) {
        return new BinaryOperationArgument(
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