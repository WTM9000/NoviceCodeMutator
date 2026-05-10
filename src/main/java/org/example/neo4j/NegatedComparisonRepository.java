package org.example.neo4j;

import org.example.model.BinaryOperationArgument;
import org.example.model.NegatedComparisonNode;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import java.util.ArrayList;
import java.util.List;

public class NegatedComparisonRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public NegatedComparisonRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<NegatedComparisonNode> findAllNegatedComparisons() {
        String cypher = """
                MATCH (n:UnaryOperator)-[:OPERATOR_BASE]->(c:BinaryOperator)
                WHERE n.name = "!"
                  AND (c.name = "<" OR c.name = "<=" OR c.name = ">"
                       OR c.name = ">=" OR c.name = "==" OR c.name = "!=")
                MATCH (c)-[:OPERATOR_BASE]->(b)
                MATCH (c)-[:OPERATOR_ARGUMENTS]->(a)
                RETURN n, c, b, a
                """;

        List<NegatedComparisonNode> resultList = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();

                Value n = record.get("n");
                Value c = record.get("c");
                Value b = record.get("b");
                Value a = record.get("a");

                if (n.isNull() || c.isNull() || b.isNull() || a.isNull()) {
                    continue;
                }

                BinaryOperationArgument leftArgument = mapArgument(b);
                BinaryOperationArgument rightArgument = mapArgument(a);

                resultList.add(new NegatedComparisonNode(
                        (int) n.asNode().id(),
                        (int) c.asNode().id(),
                        asNullableString(c.get("name")),
                        asNullableInt(n.get("startLine")),
                        asNullableInt(n.get("startColumn")),
                        asNullableInt(n.get("endLine")),
                        asNullableInt(n.get("endColumn")),
                        leftArgument,
                        rightArgument
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
