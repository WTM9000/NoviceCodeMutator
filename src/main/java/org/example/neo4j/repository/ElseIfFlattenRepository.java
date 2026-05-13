package org.example.neo4j.repository;

import org.example.model.ElseIfFlattenCandidate;
import org.example.model.StatementNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.List;

public class ElseIfFlattenRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public ElseIfFlattenRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<ElseIfFlattenCandidate> findCandidates() {
        // Find root if-statements whose direct else is another if-statement (else-if chains)
        // and which are NOT themselves inside an else branch (avoid double-processing sub-chains)
        String cypher = """
                MATCH (rootIf:IfStatement)
                MATCH (rootIf)-[:ELSE_STATEMENT]->(elseIf:IfStatement)
                WHERE NOT ()-[:ELSE_STATEMENT]->(rootIf)
                MATCH (rootIf)-[:CONDITION]->(rootCond)
                MATCH (rootIf)-[:THEN_STATEMENT]->(rootThen)
                RETURN rootIf, rootCond, rootThen
                ORDER BY rootIf.startLine
                """;

        List<ElseIfFlattenCandidate> result = new ArrayList<>();

        try (Session session = driver.session()) {
            Result queryResult = session.run(cypher);

            while (queryResult.hasNext()) {
                Record record = queryResult.next();

                Value rootIfValue   = record.get("rootIf");
                Value rootCondValue = record.get("rootCond");
                Value rootThenValue = record.get("rootThen");

                if (isAnyNull(rootIfValue, rootCondValue, rootThenValue)) {
                    continue;
                }

                StatementNode rootIfNode = mapStatementNode(rootIfValue);

                // Build the full branch list by following ELSE chain inside the session
                List<ElseIfFlattenCandidate.Branch> branches =
                        collectChain(session,
                                rootIfValue.asNode().id(),
                                asNullableString(rootCondValue.get("code")),
                                mapStatementNode(rootThenValue));

                if (branches == null || branches.size() < 2) {
                    continue;
                }

                result.add(new ElseIfFlattenCandidate(rootIfNode, branches));
            }
        }

        return result;
    }

    private List<ElseIfFlattenCandidate.Branch> collectChain(Session session,
                                                             long rootId,
                                                             String firstCondCode,
                                                             StatementNode firstThen) {
        List<ElseIfFlattenCandidate.Branch> branches = new ArrayList<>();

        branches.add(new ElseIfFlattenCandidate.Branch(
                firstCondCode,
                firstThen.getCode(),
                firstThen.getStartLine(),
                firstThen.getEndLine()
        ));

        // Walk the ELSE chain iteratively
        long currentId = rootId;
        while (true) {
            String followCypher = """
                    MATCH (current)-[:ELSE_STATEMENT]->(elseNode)
                    WHERE id(current) = $id
                    OPTIONAL MATCH (elseNode)-[:CONDITION]->(cond)
                    OPTIONAL MATCH (elseNode)-[:THEN_STATEMENT]->(thenBlock)
                    RETURN elseNode, cond, thenBlock, labels(elseNode) AS lbls
                    """;

            Result followResult = session.run(followCypher,
                    java.util.Map.of("id", currentId));

            if (!followResult.hasNext()) {
                break;
            }

            Record followRecord = followResult.next();
            Value elseNodeValue = followRecord.get("elseNode");
            Value condValue     = followRecord.get("cond");
            Value thenValue     = followRecord.get("thenBlock");

            if (elseNodeValue.isNull()) {
                break;
            }

            List<String> labels = new ArrayList<>();
            elseNodeValue.asNode().labels().forEach(labels::add);

            if (labels.contains("IfStatement")) {
                // else-if branch
                if (isAnyNull(condValue, thenValue)) {
                    return null;
                }
                branches.add(new ElseIfFlattenCandidate.Branch(
                        asNullableString(condValue.get("code")),
                        asNullableString(thenValue.get("code")),
                        asNullableInt(thenValue.get("startLine")),
                        asNullableInt(thenValue.get("endLine"))
                ));
                currentId = elseNodeValue.asNode().id();
            } else {
                // Bare else block — final branch, no condition
                branches.add(new ElseIfFlattenCandidate.Branch(
                        null,
                        asNullableString(elseNodeValue.get("code")),
                        asNullableInt(elseNodeValue.get("startLine")),
                        asNullableInt(elseNodeValue.get("endLine"))
                ));
                break;
            }
        }

        return branches;
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
        for (Value v : values) {
            if (v == null || v.isNull()) return true;
        }
        return false;
    }

    @Override
    public void close() { driver.close(); }
}