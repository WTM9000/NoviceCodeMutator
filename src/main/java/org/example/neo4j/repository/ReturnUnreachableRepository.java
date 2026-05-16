package org.example.neo4j.repository;

import org.example.model.ReturnUnreachableCandidate;
import org.example.model.StatementNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReturnUnreachableRepository extends NodeRepository implements AutoCloseable {

    /**
     * Fallback dead expressions from the spec catalogue.
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
     * Unary decorators applied to in-scope variables.
     * Chosen deterministically by returnStatement startLine.
     */
    private static final List<String> UNARY_WRAPPERS = List.of(
            "%s++",
            "%s--",
            "++%s",
            "--%s",
            "-%s",
            "~%s"
    );

    private final Driver driver;

    public ReturnUnreachableRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    /**
     * Finds all ReturnStatement nodes that have a non-null code field
     * (i.e. explicitly written return statements in source).
     * Each candidate is enriched with a dead expression via Variant A or fallback.
     */
    public List<ReturnUnreachableCandidate> findAllCandidates() {
        // Finds all explicit ReturnStatements and their enclosing function body block.
        String cypher = """
            MATCH (fn:FunctionDeclaration)-[:BODY]->(fnBlock:Block)
            MATCH (ret:ReturnStatement)
            WHERE ret.startLine >= fnBlock.startLine
            AND ret.endLine <= fnBlock.endLine
            AND ret.code IS NOT NULL
            RETURN id(fnBlock)      AS fnBlockId,
                   id(ret)          AS retId,
                   ret.code         AS retCode,
                   ret.startLine    AS startLine,
                   ret.endLine      AS endLine,
                   ret.startColumn  AS startColumn,
                   ret.endColumn    AS endColumn
            ORDER BY startLine
            """;

        List<ReturnUnreachableCandidate> candidates = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();

                int fnBlockId    = record.get("fnBlockId").asInt();
                int retId        = record.get("retId").asInt();
                Integer retStart = asNullableInt(record.get("startLine"));

                StatementNode retStmt = new StatementNode(
                        asNullableString(record.get("retCode")),
                        retStart,
                        asNullableInt(record.get("endLine")),
                        asNullableInt(record.get("startColumn")),
                        asNullableInt(record.get("endColumn"))
                );

                // Check whether this return is a direct child of a braceless
                // IfStatement or LoopStatement (no intermediate Block node).
                boolean needsBraces = isBracelessParent(session, retId);

                String scopeExpr = (retStart != null)
                        ? findInitializedVarBefore(session, fnBlockId, retStart)
                        : null;

                if (scopeExpr != null) {
                    String template = UNARY_WRAPPERS.get(
                            Math.abs(retStart) % UNARY_WRAPPERS.size()
                    );
                    String decorated = String.format(template, scopeExpr);
                    candidates.add(new ReturnUnreachableCandidate(
                            retStmt, decorated, true, needsBraces));
                } else {
                    String fallback = FALLBACK_EXPRESSIONS.get(
                            Math.abs(fnBlockId) % FALLBACK_EXPRESSIONS.size()
                    );
                    candidates.add(new ReturnUnreachableCandidate(
                            retStmt, fallback, false, needsBraces));
                }
            }
        }

        return candidates;
    }

    /**
     * Returns true when the ReturnStatement's immediate parent in the AST is an
     * IfStatement (via THEN_STMT or ELSE_STMT) or a LoopStatement (via BODY)
     * without an intermediate Block node — i.e. a braceless single-statement branch.
     */
    private boolean isBracelessParent(Session session, int retId) {
        String cypher = """
            MATCH (parent)-[r]->(ret:ReturnStatement)
            WHERE id(ret) = $retId
              AND (parent:IfStatement OR parent:LoopStatement)
              AND type(r) IN ['THEN_STATEMENT', 'ELSE_STATEMENT', 'STATEMENT']
            RETURN count(parent) > 0 AS braceless
            """;

        Result result = session.run(cypher, Map.of("retId", retId));
        if (result.hasNext()) {
            Value v = result.next().get("braceless");
            return v != null && !v.isNull() && v.asBoolean();
        }
        return false;
    }

    /**
     * Returns the name of an initialized local variable declared in fnBlock
     * strictly before the given line number.
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
            return (v == null || v.isNull()) ? null : v.asString();
        }
        return null;
    }

    @Override
    public void close() {
        driver.close();
    }
}