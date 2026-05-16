package org.example.neo4j.repository;

import org.example.model.EmptyLoopCandidate;
import org.example.model.StatementNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmptyLoopRepository extends NodeRepository implements AutoCloseable {

    /**
     * Fallback conditions — pure logical/literal expressions, no side effects.
     * Taken from the conditions catalogue in the spec document.
     */
    public static final List<String> FALLBACK_CONDITIONS = List.of(
            "1",
            "0 == 0",
            "1 != 1",
            "(1 < 2) && (3 > 1)",
            "(5 >= 5) || (0 > 1)",
            "(1 && 0) || (0 && 1)"
    );

    private final Driver driver;

    public EmptyLoopRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    /**
     * Finds all Statement nodes that are direct STATEMENTS children of a
     * FunctionDeclaration body Block.
     * Each candidate is enriched with a loop condition:
     *   - Variant A: name of an initialized local variable declared before the statement.
     *   - Fallback:  a literal from FALLBACK_CONDITIONS.
     */
    public List<EmptyLoopCandidate> findAllCandidates() {
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

        List<EmptyLoopCandidate> candidates = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();

                int blockId = record.get("blockId").asInt();
                Integer stmtStartLine = asNullableInt(record.get("startLine"));

                StatementNode stmt = new StatementNode(
                        asNullableString(record.get("stmtCode")),
                        stmtStartLine,
                        asNullableInt(record.get("endLine")),
                        asNullableInt(record.get("startColumn")),
                        asNullableInt(record.get("endColumn"))
                );

                // Variant A: find an initialized variable declared before this statement.
                String scopeCond = (stmtStartLine != null)
                        ? findInitializedVarBefore(session, blockId, stmtStartLine)
                        : null;

                if (scopeCond != null) {
                    candidates.add(new EmptyLoopCandidate(stmt, scopeCond, true));
                } else {
                    String fallback = FALLBACK_CONDITIONS.get(
                            Math.abs(blockId) % FALLBACK_CONDITIONS.size()
                    );
                    candidates.add(new EmptyLoopCandidate(stmt, fallback, false));
                }
            }
        }

        return candidates;
    }

    /**
     * Returns the name of any initialized local variable declared inside the given
     * block strictly before stmtStartLine.
     * Returns null if no such variable exists.
     */
    private String findInitializedVarBefore(Session session, int blockId, int stmtStartLine) {
        String cypher = """
                MATCH (block)-[:STATEMENTS]->(decl:DeclarationStatement)
                       -[:DECLARATIONS]->(var:VariableDeclaration)
                WHERE id(block) = $blockId
                  AND var.name IS NOT NULL
                  AND (EXISTS{ MATCH (var)-[:INITIALIZER]->()})
                  AND decl.startLine < $stmtLine
                RETURN var.name AS varName
                ORDER BY decl.startLine
                LIMIT 1
                """;

        Result result = session.run(cypher, Map.of(
                "blockId", blockId,
                "stmtLine", stmtStartLine
        ));

        if (result.hasNext()) {
            Value v = result.next().get("varName");
            return (v == null || v.isNull()) ? null : v.asString();
        }
        return null;
    }

    @Override
    public void close() {
        driver.close();
    }
}