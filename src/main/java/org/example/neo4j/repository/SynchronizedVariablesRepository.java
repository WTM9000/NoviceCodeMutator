package org.example.neo4j.repository;

import org.example.model.StatementNode;
import org.example.model.SynchronizedVariablesCandidate;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SynchronizedVariablesRepository extends NodeRepository implements AutoCloseable {

    /**
     * Fallback expressions used when no suitable in-scope binary expression is found.
     * All are pure integer arithmetic — no side effects, always valid in C.
     */
    public static final List<String> FALLBACK_EXPRESSIONS = List.of(
            "1 + 1",
            "2 * 3",
            "10 - 4",
            "8 / 2",
            "3 + 5"
    );

    private final Driver driver;

    public SynchronizedVariablesRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    /**
     * Finds all single-line ExpressionStatement nodes that live directly inside
     * a function body block. Each candidate is enriched with an expression:
     * either a simple binary expression from the same block (Variant A),
     * or a fallback literal (e.g. "1 + 1").
     */
    public List<SynchronizedVariablesCandidate> findAllCandidates() {
        String stmtCypher = """
                MATCH (fn)-[:BODY]->(block)
                WHERE (fn:MethodDeclarations OR fn:FunctionDeclaration)
                MATCH (block)-[:STATEMENTS]->(stmt)
                WHERE stmt.startLine = stmt.endLine
                RETURN id(block)     AS blockId,
                       stmt.code        AS stmtCode,
                       stmt.startLine   AS startLine,
                       stmt.endLine     AS endLine,
                       stmt.startColumn AS startColumn,
                       stmt.endColumn   AS endColumn
                ORDER BY startLine
                """;

        List<SynchronizedVariablesCandidate> candidates = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(stmtCypher);

            while (result.hasNext()) {
                Record record = result.next();

                int blockId = record.get("blockId").asInt();
                StatementNode stmt = new StatementNode(
                        asNullableString(record.get("stmtCode")),
                        asNullableInt(record.get("startLine")),
                        asNullableInt(record.get("endLine")),
                        asNullableInt(record.get("startColumn")),
                        asNullableInt(record.get("endColumn"))
                );

                // Variant A: look for a BinaryOperator with two Identifier operands in the same block.
                String scopeExpr = findSimpleBinaryExprInBlock(session, blockId);

                if (scopeExpr != null) {
                    candidates.add(new SynchronizedVariablesCandidate(blockId, stmt, scopeExpr, true));
                } else {
                    // Fallback: pick a deterministic literal based on blockId.
                    String fallback = FALLBACK_EXPRESSIONS.get(
                            Math.abs(blockId) % FALLBACK_EXPRESSIONS.size()
                    );
                    candidates.add(new SynchronizedVariablesCandidate(blockId, stmt, fallback, false));
                }
            }
        }

        return candidates;
    }

    /**
     * Returns the code of the simplest BinaryOperator (+, -, *, /) inside the given block
     * whose both operands are plain Identifiers. Returns null if nothing qualifies.
     */
    private String findSimpleBinaryExprInBlock(Session session, int blockId) {
        String cypher = """
                MATCH (block)-[:AST*1..]->(expr:BinaryOperator)
                WHERE id(block) = $blockId
                  AND expr.name IN ['+', '-', '*', '/']
                MATCH (expr)-[:OPERATOR_BASE]->(left:Identifier)
                MATCH (expr)-[:OPERATOR_ARGUMENTS]->(right:Identifier)
                RETURN expr.code AS exprCode
                LIMIT 1
                """;

        Result result = session.run(cypher, Map.of("blockId", blockId));
        if (result.hasNext()) {
            Value v = result.next().get("exprCode");
            return (v == null || v.isNull()) ? null : v.asString();
        }
        return null;
    }

    /**
     * Checks whether a local variable with the given name already exists in the block.
     */
    public boolean hasDeclarationInBlock(int blockId, String variableName) {
        if (blockId < 0 || variableName == null || variableName.isBlank()) {
            return false;
        }

        String cypher = """
                MATCH (block)-[:AST*1..]->(d:Declaration)
                WHERE id(block) = $blockId AND d.name = $varName
                RETURN d
                LIMIT 1
                """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of(
                    "blockId", blockId,
                    "varName", variableName
            ));
            return result.hasNext();
        }
    }

    @Override
    public void close() {
        driver.close();
    }
}
