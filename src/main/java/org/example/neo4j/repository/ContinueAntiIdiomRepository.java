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
        // Finds top-level if-statements that are the last AST child of a loop body.
        // Includes cases where elseNode is a plain else OR the start of an else-if chain.
        String cypher = """
                MATCH (loop)
                WHERE loop:LoopStatement
                MATCH (loop)-[:AST]->(body:Block)
                MATCH (body)-[:AST]->(ifNode)
                WITH loop, body, ifNode
                ORDER BY ifNode.startLine, ifNode.startColumn
                WITH loop, body, collect(ifNode) AS ifNodes
                WITH loop, body, last(ifNodes) AS ifNode
                WHERE ifNode:IfStatement
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

                Value loopValue  = record.get("loop");
                Value bodyValue  = record.get("body");
                Value ifValue    = record.get("ifNode");
                Value condValue  = record.get("cond");
                Value thenValue  = record.get("thenNode");
                Value elseValue  = record.get("elseNode");

                if (isAnyNull(loopValue, bodyValue, ifValue, condValue, thenValue)) {
                    continue;
                }

                String guardCode = asNullableString(condValue.get("code"));
                if (!isSafeGuard(guardCode)) {
                    continue;
                }

                StatementNode loopNode = mapStatementNode(loopValue);
                StatementNode bodyNode = mapStatementNode(bodyValue);
                StatementNode ifNode   = mapStatementNode(ifValue);
                StatementNode thenNode = mapStatementNode(thenValue);

                boolean elseIsNull = elseValue == null || elseValue.isNull();

                // Check if the else branch is a nested IfStatement (else-if chain)
                boolean elseIsIfStatement = !elseIsNull
                        && elseValue.get("labels") != null
                        && isIfStatementNode(elseValue);

                ContinueAntiIdiomCandidate elseIfBranch = null;
                StatementNode elseNode = null;
                boolean hasElse = false;
                boolean trivialElse = true;

                if (!elseIsNull) {
                    if (elseIsIfStatement) {
                        // else-if chain: recurse into nested IfStatement nodes
                        elseIfBranch = buildElseIfBranch(session, elseValue, loopNode, bodyNode);
                        // elseNode stays null — the else branch is represented by elseIfBranch
                    } else {
                        elseNode = mapStatementNode(elseValue);
                        hasElse = true;
                        trivialElse = isTrivialElse(elseNode.getCode());
                    }
                }

                result.add(new ContinueAntiIdiomCandidate(
                        loopNode,
                        bodyNode,
                        ifNode,
                        thenNode,
                        elseNode,
                        guardCode,
                        hasElse,
                        trivialElse,
                        elseIfBranch
                ));
            }
        }

        return result;
    }

    /**
     * Recursively builds a ContinueAntiIdiomCandidate chain for an else-if node.
     * The CPG structure:
     *   (ifElse:IfStatement)-[:CONDITION]->(cond)
     *                       -[:THEN_STATEMENT]->(stmt)
     *                       -[:ELSE_STATEMENT]->(next)   // next may be IfStatement or plain Statement
     */
    private ContinueAntiIdiomCandidate buildElseIfBranch(Session session,
                                                         Value ifElseValue,
                                                         StatementNode loopNode,
                                                         StatementNode bodyNode) {
        long nodeId = ifElseValue.asNode().id();

        String cypher = """
                MATCH (ifNode) WHERE id(ifNode) = $nodeId
                MATCH (ifNode)-[:CONDITION]->(cond)
                MATCH (ifNode)-[:THEN_STATEMENT]->(thenNode)
                OPTIONAL MATCH (ifNode)-[:ELSE_STATEMENT]->(elseNode)
                RETURN ifNode, cond, thenNode, elseNode
                """;

        Result result = session.run(cypher, org.neo4j.driver.Values.parameters("nodeId", nodeId));
        if (!result.hasNext()) {
            return null;
        }

        Record record = result.next();

        Value ifValue   = record.get("ifNode");
        Value condValue = record.get("cond");
        Value thenValue = record.get("thenNode");
        Value elseValue = record.get("elseNode");

        if (isAnyNull(ifValue, condValue, thenValue)) {
            return null;
        }

        String guardCode = asNullableString(condValue.get("code"));
        if (!isSafeGuard(guardCode)) {
            return null;
        }

        StatementNode ifNode   = mapStatementNode(ifValue);
        StatementNode thenNode = mapStatementNode(thenValue);

        boolean elseIsNull = elseValue == null || elseValue.isNull();

        ContinueAntiIdiomCandidate nestedElseIfBranch = null;
        StatementNode elseNode = null;
        boolean hasElse = false;
        boolean trivialElse = true;

        if (!elseIsNull) {
            if (isIfStatementNode(elseValue)) {
                nestedElseIfBranch = buildElseIfBranch(session, elseValue, loopNode, bodyNode);
            } else {
                elseNode = mapStatementNode(elseValue);
                hasElse = true;
                trivialElse = isTrivialElse(elseNode.getCode());
            }
        }

        return new ContinueAntiIdiomCandidate(
                loopNode,
                bodyNode,
                ifNode,
                thenNode,
                elseNode,
                guardCode,
                hasElse,
                trivialElse,
                nestedElseIfBranch
        );
    }

    /**
     * Checks if a CPG node Value represents an IfStatement.
     * Uses presence of CONDITION/THEN_STATEMENT relationships as a heuristic,
     * since the Java driver exposes labels via asNode().labels().
     */
    private boolean isIfStatementNode(Value value) {
        try {
            Iterable<String> labels = value.asNode().labels();
            for (String label : labels) {
                if ("IfStatement".equals(label)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Not a node value
        }
        return false;
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