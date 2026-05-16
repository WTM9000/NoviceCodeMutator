package org.example.neo4j.repository;

import org.example.model.EmptyExpressionCandidate;
import org.example.model.StatementNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmptyExpressionRepository extends NodeRepository implements AutoCloseable {

    public static final List<String> FALLBACK_EXPRESSIONS = List.of(
            "0",
            "sizeof(int)",
            "(2 - 2) * 10",
            "(3 & 5) | 0",
            "(5 >= 5) || (0 > 1)",
            "(1 < 2) && (3 > 1)"
    );

    private final Driver driver;

    public EmptyExpressionRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    /**
     * Finds all Statement nodes that are direct children of a function body Block
     * (via FunctionDeclaration -[BODY]-> Block -[STATEMENTS]-> Statement).
     * Nested blocks are NOT traversed — only top-level statements of each function body.
     *
     * Each candidate is enriched with a dead expression:
     *   - Variant A: a simple binary expression built from two in-scope local variables.
     *   - Fallback:  a literal from FALLBACK_EXPRESSIONS.
     */
    public List<EmptyExpressionCandidate> findAllCandidates() {
        String cypher = """
                MATCH (fn:FunctionDeclaration)-[:BODY]->(block:Block)-[:STATEMENTS]->(stmt)
                WHERE stmt.startLine IS NOT NULL
                RETURN id(block)        AS blockId,
                       stmt.code        AS stmtCode,
                       stmt.startLine   AS startLine,
                       stmt.endLine     AS endLine,
                       stmt.startColumn AS startColumn,
                       stmt.endColumn   AS endColumn
                ORDER BY startLine
                """;

        List<EmptyExpressionCandidate> candidates = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

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

                // Variant A: find two local variables in the same block.
                int stmtStartLine = asNullableInt(record.get("startLine"));
                String scopeExpr = buildBinaryExprFromScope(session, blockId, stmtStartLine);

                if (scopeExpr != null) {
                    candidates.add(new EmptyExpressionCandidate(stmt, scopeExpr, true));
                } else {
                    String fallback = FALLBACK_EXPRESSIONS.get(
                            Math.abs(blockId) % FALLBACK_EXPRESSIONS.size()
                    );
                    candidates.add(new EmptyExpressionCandidate(stmt, fallback, false));
                }
            }
        }

        return candidates;
    }

    /**
     * Attempts to build a simple "varA + varB" expression from two distinct local
     * variable declarations inside the given block.
     * Returns null if fewer than two suitable variables are found.
     */
    private String buildBinaryExprFromScope(Session session, int blockId, int stmtStartLine) {
        String cypher = """
            MATCH (block)-[:STATEMENTS]->(decl:DeclarationStatement)
                   -[:DECLARATIONS]->(var:VariableDeclaration)
            WHERE id(block) = $blockId
              AND var.name IS NOT NULL
              AND (EXISTS{ MATCH (var)-[:INITIALIZER]->()})
              AND decl.startLine <= $stmtLine
            RETURN var.name AS varName
            ORDER BY decl.startLine
            LIMIT 2
            """;

        Result result = session.run(cypher, Map.of(
                "blockId", blockId,
                "stmtLine", stmtStartLine
        ));

        List<String> vars = new ArrayList<>();
        while (result.hasNext()) {
            Value v = result.next().get("varName");
            if (v != null && !v.isNull()) {
                vars.add(v.asString());
            }
        }

        if (vars.size() < 2) return null;
        return vars.get(0) + " + " + vars.get(1);
    }

    @Override
    public void close() {
        driver.close();
    }
}
