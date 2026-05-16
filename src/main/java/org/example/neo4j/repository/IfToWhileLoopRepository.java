package org.example.neo4j.repository;

import org.example.model.StatementNode;
import org.example.model.WhileLoopCandidate;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IfToWhileLoopRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public IfToWhileLoopRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    /**
     * Finds all adjacent (IfStatement, ReturnStatement) pairs in the same Block
     * where:
     *   - ifStmt.order + 1 == ret.order  (they are immediate neighbors)
     *   - ifStmt.code contains "&&"       (condition is splittable)
     *   - ret.code IS NOT NULL            (explicit return in source)
     *
     * Splits the if condition on the FIRST "&&": left -> whileCondition, right -> innerIfCondition.
     * For chained &&  (y1 && y2 && y3): while(y1), inner if(y2 && y3).
     */
    public List<WhileLoopCandidate> findAllCandidates() {
        String cypher = """
            MATCH (block:Block)-[:STATEMENTS]->(ifStmt:IfStatement)
            MATCH (block:Block)-[:STATEMENTS]->(ret:ReturnStatement)
            MATCH (ifStmt)-[:CONDITION]->(op:ShortCircuitOperator)
            MATCH (op)-[:OPERATOR_BASE]->(lhs)
            MATCH (op)-[:OPERATOR_ARGUMENTS]->(rhs)
            WHERE op.operatorCode = '&&'
              AND ret.startLine > ifStmt.endLine
              AND NOT EXISTS {
                  MATCH (block)-[:STATEMENTS]->(mid:Statement)
                  WHERE mid.startLine > ifStmt.endLine
                    AND mid.startLine < ret.startLine
              }
              AND ret.code IS NOT NULL
            RETURN ifStmt.code        AS ifCode,
                   ifStmt.startLine   AS ifStart,
                   ifStmt.endLine     AS ifEnd,
                   ifStmt.startColumn AS ifCol,
                   ifStmt.endColumn   AS ifEndCol,
                   lhs.code           AS whileCond,
                   rhs.code           AS innerCond,
                   ret.code           AS retCode,
                   ret.startLine      AS retStart,
                   ret.endLine        AS retEnd,
                   ret.startColumn    AS retCol,
                   ret.endColumn      AS retEndCol
            ORDER BY ifStmt.startLine
            """;

        List<WhileLoopCandidate> candidates = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record r = result.next();

                String whileCond = asNullableString(r.get("whileCond"));
                String innerCond = asNullableString(r.get("innerCond"));
                if (whileCond == null || innerCond == null) continue;
                if (whileCond.isBlank() || innerCond.isBlank()) continue;

                StatementNode ifStmt = new StatementNode(
                        asNullableString(r.get("ifCode")),
                        asNullableInt(r.get("ifStart")),
                        asNullableInt(r.get("ifEnd")),
                        asNullableInt(r.get("ifCol")),
                        asNullableInt(r.get("ifEndCol"))
                );
                StatementNode retStmt = new StatementNode(
                        asNullableString(r.get("retCode")),
                        asNullableInt(r.get("retStart")),
                        asNullableInt(r.get("retEnd")),
                        asNullableInt(r.get("retCol")),
                        asNullableInt(r.get("retEndCol"))
                );

                candidates.add(new WhileLoopCandidate(ifStmt, retStmt, whileCond, innerCond));
            }
        }

        return candidates;
    }

    /**
     * Pulls the condition string out of "if (<cond>) <body>".
     * Returns null if the pattern is not found.
     */
    private String extractIfCondition(String ifCode) {
        int open  = ifCode.indexOf('(');
        if (open < 0) return null;
        // Find the matching closing paren, respecting nesting.
        int depth = 0;
        for (int i = open; i < ifCode.length(); i++) {
            char c = ifCode.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return ifCode.substring(open + 1, i);
            }
        }
        return null;
    }

    @Override
    public void close() {
        driver.close();
    }
}