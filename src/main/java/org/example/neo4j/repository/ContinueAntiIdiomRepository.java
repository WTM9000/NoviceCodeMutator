package org.example.neo4j.repository;

import org.example.model.ContinueAntiIdiomCandidate;
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

public class ContinueAntiIdiomRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public ContinueAntiIdiomRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<ContinueAntiIdiomCandidate> findCandidates() {
        String cypher = """
                MATCH (loop)
                WHERE loop:LoopStatement
                MATCH (loop)-[:AST]->(body:Block)
                MATCH (body)-[:AST]->(ifNode)
                WHERE ifNode:IfStatement
                WITH loop, body, ifNode
                ORDER BY ifNode.startLine, ifNode.startColumn
                WITH loop, body, collect(ifNode) AS ifNodes
                WITH loop, body, last(ifNodes) AS ifNode
                MATCH (ifNode)-[:CONDITION]->(cond)
                MATCH (ifNode)-[:THEN_STATEMENT]->(thenNode)
                OPTIONAL MATCH (ifNode)-[:ELSE_STATEMENT]->(elseNode)
                RETURN loop, body, ifNode, cond, thenNode, elseNode
                ORDER BY ifNode.startLine, ifNode.startColumn
                """;

        List<ContinueAntiIdiomCandidate> result = new ArrayList<>();

        try (Session session = driver.session()) {
            Result queryResult = session.run(cypher);

            while (queryResult.hasNext()) {
                Record record = queryResult.next();

                Value loopValue = record.get("loop");
                Value bodyValue = record.get("body");
                Value ifValue = record.get("ifNode");
                Value condValue = record.get("cond");
                Value thenValue = record.get("thenNode");
                Value elseValue = record.get("elseNode");

                if (isAnyNull(loopValue, bodyValue, ifValue, condValue, thenValue)) {
                    continue;
                }

                StatementNode loopNode = mapStatementNode(loopValue);
                StatementNode bodyNode = mapStatementNode(bodyValue);
                StatementNode ifNode = mapStatementNode(ifValue);
                StatementNode thenNode = mapStatementNode(thenValue);
                StatementNode elseNode = elseValue == null || elseValue.isNull()
                        ? null
                        : mapStatementNode(elseValue);

                String guardCode = asNullableString(condValue.get("code"));
                if (!isSafeGuard(guardCode)) {
                    continue;
                }

                if (isElseIf(elseNode)) {
                    continue;
                }

                boolean hasElse = elseNode != null;
                boolean trivialElse = !hasElse || isTrivialElse(elseNode.getCode());

                result.add(new ContinueAntiIdiomCandidate(
                        loopNode,
                        bodyNode,
                        ifNode,
                        thenNode,
                        elseNode,
                        guardCode,
                        hasElse,
                        trivialElse
                ));
            }
        }

        return result;
    }

    private boolean isSafeGuard(String guardCode) {
        if (guardCode == null || guardCode.isBlank()) {
            return false;
        }

        String normalized = guardCode.replace(" ", "");
        if (normalized.contains("=") && !normalized.contains("==") && !normalized.contains("!=")
                && !normalized.contains(">=") && !normalized.contains("<=")) {
            return false;
        }
        if (normalized.contains("++") || normalized.contains("--")) {
            return false;
        }
        if (normalized.matches(".*\\w+\\s*\\(.*")) {
            return false;
        }

        return true;
    }

    private boolean isElseIf(StatementNode elseNode) {
        if (elseNode == null || elseNode.getCode() == null) {
            return false;
        }
        String code = elseNode.getCode().trim();
        return code.startsWith("if") || code.startsWith("else if");
    }

    private boolean isTrivialElse(String elseCode) {
        if (elseCode == null) {
            return true;
        }

        String code = elseCode.trim();
        if (code.isBlank() || "{}".equals(code) || "{ }".equals(code)) {
            return true;
        }

        String normalized = code.replaceAll("\\s+", " ").trim().toLowerCase();
        return normalized.contains("log")
                || normalized.contains("printf")
                || normalized.contains("fprintf")
                || normalized.contains("trace")
                || normalized.contains("debug")
                || normalized.contains("counter");
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
        for (Value value : values) {
            if (value == null || value.isNull()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        driver.close();
    }
}