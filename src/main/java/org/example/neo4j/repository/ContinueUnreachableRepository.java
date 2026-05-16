package org.example.neo4j.repository;

import org.example.model.ContinueUnreachableCandidate;
import org.example.model.StatementNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContinueUnreachableRepository extends NodeRepository implements AutoCloseable {

    /**
     * Fallback dead expressions from the spec catalogue.
     * Rendered as "<expr>;" after the continue statement.
     */
    public static final List<String> FALLBACK_EXPRESSIONS = List.of(
            "0",
            "sizeof(int)",
            "(2 - 2) * 10",
            "(3 & 5) | 0",
            "(5 >= 5) || (0 > 1)",
            "(1 < 2) && (3 > 1)"
    );

    /**
     * Unary decorators applied to the in-scope variable to make the dead expression
     * slightly more complex. One is chosen deterministically based on fnBlockId.
     * All are pure side-effect-free expressions when used as a statement — the result
     * is discarded. We use post-increment form so the mutation reads naturally.
     */
    private static final List<String> UNARY_WRAPPERS = List.of(
            "%s++",      // post-increment
            "%s--",      // post-decrement
            "++%s",      // pre-increment
            "--%s",      // pre-decrement
            "-%s",       // unary minus
            "~%s"        // bitwise NOT
    );

    private final Driver driver;

    public ContinueUnreachableRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    /**
     * For every LoopStatement found in the graph, finds the last direct STATEMENTS
     * child of its body Block. Returns one candidate per loop.
     *
     * The dead expression is either:
     *   - Variant A: name of an initialized variable visible at loop scope
     *                (declared in the enclosing function block before the loop).
     *   - Fallback:  a literal from FALLBACK_EXPRESSIONS.
     */
    public List<ContinueUnreachableCandidate> findAllCandidates() {
        // Find each LoopStatement body block and the endLine of its last statement.
        String cypher = """
                MATCH (loop:LoopStatement)-[:STATEMENT]->(block:Block)-[:STATEMENTS]->(stmt)
                WHERE stmt.endLine IS NOT NULL
                WITH loop, block, stmt
                ORDER BY stmt.endLine DESC
                WITH loop, block, collect(stmt)[0] AS lastStmt
                MATCH (fn:FunctionDeclaration)-[:BODY]->(fnBlock:Block)
                      -[:STATEMENTS*]->(loop)
                RETURN id(loop)             AS loopId,
                       id(fnBlock)          AS fnBlockId,
                       loop.startLine       AS loopStartLine,
                       lastStmt.code        AS stmtCode,
                       lastStmt.startLine   AS startLine,
                       lastStmt.endLine     AS endLine,
                       lastStmt.startColumn AS startColumn,
                       lastStmt.endColumn   AS endColumn
                """;

        List<ContinueUnreachableCandidate> candidates = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();

                int fnBlockId = record.get("fnBlockId").asInt();
                Integer loopStartLine = asNullableInt(record.get("loopStartLine"));

                StatementNode lastStmt = new StatementNode(
                        asNullableString(record.get("stmtCode")),
                        asNullableInt(record.get("startLine")),
                        asNullableInt(record.get("endLine")),
                        asNullableInt(record.get("startColumn")),
                        asNullableInt(record.get("endColumn"))
                );

                // Variant A: look for an initialized variable in the enclosing function
                // block that is declared before the loop starts.
                String scopeExpr = (loopStartLine != null)
                        ? findInitializedVarBefore(session, fnBlockId, loopStartLine)
                        : null;

                if (scopeExpr != null) {
                    candidates.add(new ContinueUnreachableCandidate(lastStmt, scopeExpr, true));
                } else {
                    String fallback = FALLBACK_EXPRESSIONS.get(
                            Math.abs(fnBlockId) % FALLBACK_EXPRESSIONS.size()
                    );
                    candidates.add(new ContinueUnreachableCandidate(lastStmt, fallback, false));
                }
            }
        }

        return candidates;
    }

    /**
     * Returns the name of an initialized local variable declared inside fnBlock
     * strictly before the given line number (i.e. before the loop starts).
     * Returns null if no such variable is found.
     */
    private String findInitializedVarBefore(Session session, int fnBlockId, int beforeLine) {
        String cypher = """
                MATCH (block)-[:STATEMENTS]->(decl:DeclarationStatement)
                       -[:DECLARATIONS]->(var:VariableDeclaration)
                WHERE id(block) = $fnBlockId
                  AND var.name IS NOT NULL
                  AND (EXISTS{ MATCH (var)-[:INITIALIZER]->()})
                  AND decl.startLine < $beforeLine
                RETURN var.name AS varName
                ORDER BY decl.startLine
                LIMIT 1
                """;

        Result result = session.run(cypher, Map.of(
                "fnBlockId", fnBlockId,
                "beforeLine", beforeLine
        ));

        if (result.hasNext()) {
            Value v = result.next().get("varName");
            if (v == null || v.isNull()) return null;

            String varName = v.asString();
            // Pick a unary wrapper deterministically by fnBlockId to keep runs reproducible.
            String template = UNARY_WRAPPERS.get(Math.abs(fnBlockId) % UNARY_WRAPPERS.size());
            return String.format(template, varName);
        }
        return null;
    }

    @Override
    public void close() {
        driver.close();
    }
}
