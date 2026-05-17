package org.example.neo4j.repository;

import org.example.model.EmptyInitializationCandidate;
import org.example.model.SingleDeclarator;
import org.example.model.StatementNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.*;

public class EmptyInitializationRepository extends NodeRepository implements AutoCloseable {

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "int", "long", "short", "unsigned", "unsigned int", "unsigned long",
            "unsigned short", "long int", "unsigned long int", "long long",
            "long long int", "unsigned long long", "unsigned long long int",
            "size_t", "ssize_t", "int8_t", "int16_t", "int32_t", "int64_t",
            "uint8_t", "uint16_t", "uint32_t", "uint64_t",
            "float", "double", "long double", "char"
    );

    private final Driver driver;

    public EmptyInitializationRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<EmptyInitializationCandidate> findCandidates() {
        // Collect all type names for each declarator to handle compound types
        // (e.g. long -> ["int", "long int"]; we pick the longest non-bool name in Java).
        String cypher = """
                MATCH (m:FunctionDeclaration)
                MATCH (m)-[:AST]->(body:Block)-[]->(parentScope:Scope)
                MATCH (childScope:Scope)-[:PARENT]->(parentScope)
                MATCH (declStmt:DeclarationStatement)-[]->(childScope)
                MATCH (declStmt)-[:DECLARATIONS]->(decl:Declaration)
                MATCH (decl)-[:TYPE]->(t)
                WHERE t.name <> 'bool'
                  AND (decl.isConst IS NULL OR decl.isConst = false)
                OPTIONAL MATCH (decl)-[:INITIALIZER]->(initializer)
                WITH declStmt, decl, collect(t.name) AS typeNames, initializer
                RETURN declStmt,
                       id(decl)           AS declId,
                       decl.name          AS varName,
                       typeNames          AS typeNames,
                       initializer.code   AS initCode
                ORDER BY declStmt.startLine, declId
                """;

        // Group by startLine: one GroupAccumulator per DeclarationStatement line
        LinkedHashMap<Integer, LineAccumulator> lineAccumulators = new LinkedHashMap<>();

        try (Session session = driver.session()) {
            Result queryResult = session.run(cypher);

            while (queryResult.hasNext()) {
                Record record = queryResult.next();

                Value declStmtValue = record.get("declStmt");
                if (declStmtValue == null || declStmtValue.isNull()) continue;

                String varName = asNullableString(record.get("varName"));
                String initCode = asNullableString(record.get("initCode"));
                long declId = record.get("declId").asLong();

                if (varName == null) continue;

                // Pick the longest type name that is not 'bool'
                List<Object> rawTypes = record.get("typeNames").asList();
                String typeName = pickLongestType(rawTypes);
                if (typeName == null) continue;

                // Skip non-primitive types
                if (!PRIMITIVE_TYPES.contains(typeName.trim())) continue;

                int startLine = asNullableInt(declStmtValue.get("startLine"));
                StatementNode declStmt = mapStatementNode(declStmtValue);
                boolean hasInitializer = initCode != null;

                lineAccumulators
                        .computeIfAbsent(startLine, k -> new LineAccumulator(declStmt))
                        .add(new SingleDeclarator(
                                (int) declId,
                                varName,
                                typeName,
                                hasInitializer,
                                initCode
                        ));
            }
        }

        // For each line, build one candidate per declarator;
        // the others on the same line become remainingDeclarators.
        List<EmptyInitializationCandidate> result = new ArrayList<>();
        for (LineAccumulator acc : lineAccumulators.values()) {
            List<SingleDeclarator> all = acc.declarators;
            for (int i = 0; i < all.size(); i++) {
                SingleDeclarator chosen = all.get(i);
                List<SingleDeclarator> remaining = new ArrayList<>(all);
                remaining.remove(i);

                result.add(new EmptyInitializationCandidate(
                        acc.declarationStatement,
                        chosen.getVariableName(),
                        chosen.getTypeName(),
                        chosen.isHasInitializer(),
                        chosen.getInitializerCode(),
                        remaining
                ));
            }
        }

        return result;
    }

    /**
     * From a list of type name strings for one declarator, returns the longest
     * name that is not 'bool'. This handles compound types like "long int" vs "int".
     */
    private String pickLongestType(List<Object> rawTypes) {
        String best = null;
        for (Object raw : rawTypes) {
            if (raw == null) continue;
            String name = raw.toString().trim();
            if (name.equals("bool")) continue;
            if (best == null || name.length() > best.length()) {
                best = name;
            }
        }
        return best;
    }

    private static class LineAccumulator {
        final StatementNode declarationStatement;
        final List<SingleDeclarator> declarators = new ArrayList<>();

        LineAccumulator(StatementNode declarationStatement) {
            this.declarationStatement = declarationStatement;
        }

        void add(SingleDeclarator d) { declarators.add(d); }
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

    @Override
    public void close() { driver.close(); }
}