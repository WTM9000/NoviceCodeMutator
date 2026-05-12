package org.example.neo4j.repository;

import org.example.model.IfBranchNode;
import org.example.model.IfElseChainNode;
import org.example.model.IfRootNode;
import org.example.model.StatementNode;
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

public class IfElseChainRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public IfElseChainRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<IfRootNode> findAllRootIfStatements() {
        String cypher = """
                MATCH (n:IfStatement)
                WHERE NOT EXISTS {
                    MATCH (:IfStatement)-[:ELSE_STATEMENT]->(n)
                }
                RETURN n
                ORDER BY n.startLine, n.startColumn
                """;

        List<IfRootNode> resultList = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();
                Value n = record.get("n");

                if (n == null || n.isNull()) {
                    continue;
                }

                resultList.add(new IfRootNode(
                        (int) n.asNode().id(),
                        mapStatement(n)
                ));
            }
        }

        return resultList;
    }

    public IfElseChainNode findChainByRootId(int rootIfId) {
        String cypher = """
                MATCH path = (root:IfStatement)-[:ELSE_STATEMENT*0..]->(lastIf:IfStatement)
                WHERE id(root) = $rootIfId
                  AND NOT EXISTS { MATCH (lastIf)-[:ELSE_STATEMENT]->(:IfStatement) }
                WITH root, nodes(path) AS ifNodes, lastIf
                OPTIONAL MATCH (lastIf)-[:ELSE_STATEMENT]->(finalElse)
                RETURN root, ifNodes, finalElse
                LIMIT 1
                """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("rootIfId", rootIfId));

            if (!result.hasNext()) {
                return null;
            }

            Record record = result.next();

            Value root = record.get("root");
            Value ifNodes = record.get("ifNodes");
            Value finalElse = record.get("finalElse");

            if (root == null || root.isNull() || ifNodes == null || ifNodes.isNull()) {
                return null;
            }

            List<IfBranchNode> branches = new ArrayList<>();

            for (Value ifNode : ifNodes.values()) {
                IfBranchNode branch = loadBranchByIfId((int) ifNode.asNode().id());
                if (branch != null) {
                    branches.add(branch);
                }
            }

            if (branches.isEmpty()) {
                return null;
            }

            StatementNode elseBlock = null;
            if (finalElse != null && !finalElse.isNull()) {
                // finalElse должен быть обычным блоком/statement, а не очередным IfStatement
                if (!hasIfPrefix(finalElse)) {
                    elseBlock = mapStatement(finalElse);
                }
            }

            return new IfElseChainNode(
                    (int) root.asNode().id(),
                    mapStatement(root),
                    branches,
                    elseBlock
            );
        }
    }

    private IfBranchNode loadBranchByIfId(int ifId) {
        String cypher = """
                MATCH (n:IfStatement)
                WHERE id(n) = $ifId
                MATCH (n)-[:CONDITION]->(cond)
                MATCH (n)-[:THEN_STATEMENT]->(block)
                RETURN cond, block
                LIMIT 1
                """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("ifId", ifId));
            if (!result.hasNext()) {
                return null;
            }

            Record record = result.next();
            Value cond = record.get("cond");
            Value block = record.get("block");

            if (cond == null || cond.isNull() || block == null || block.isNull()) {
                return null;
            }

            return new IfBranchNode(mapStatement(cond), mapStatement(block));
        }
    }

    private boolean hasIfPrefix(Value value) {
        if (value == null || value.isNull()) {
            return false;
        }

        Value code = value.get("code");
        if (code == null || code.isNull()) {
            return false;
        }

        return code.asString().trim().startsWith("if");
    }

    private StatementNode mapStatement(Value value) {
        return new StatementNode(
                asNullableString(value.get("code")),
                asNullableInt(value.get("startLine")),
                asNullableInt(value.get("endLine")),
                asNullableInt(value.get("startColumn")),
                asNullableInt(value.get("endColumn"))
        );
    }

    @Override
    public void close() {
        driver.close();
    }
}
