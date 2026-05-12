package org.example.neo4j.repository;

import org.example.model.BinaryOperationArgument;
import org.example.model.ExpressionSplitCandidate;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpressionSplitRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public ExpressionSplitRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<ExpressionSplitCandidate> findAllCandidates() {
        String cypher = """
                MATCH (expr:BinaryOperator)
                WHERE expr.name IN ['+', '-', '*', '/', '%', '&', '|', '^', '<<', '>>', '>>>']
                MATCH (expr)-[:OPERATOR_BASE]->(left)
                MATCH (expr)-[:OPERATOR_ARGUMENTS]->(right)
                MATCH (stmt)-[:AST]->(expr)
                OPTIONAL MATCH (stmt)-[:SCOPE]->(scope)
                OPTIONAL MATCH (expr)-[:TYPE]->(type:Type)
                RETURN expr, left, right, stmt, scope, collect(type) AS types
                ORDER BY expr.startLine, expr.startColumn
                """;

        List<ExpressionSplitCandidate> resultList = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();

                Value expr = record.get("expr");
                Value left = record.get("left");
                Value right = record.get("right");
                Value stmt = record.get("stmt");
                Value scope = record.get("scope");
                Value types = record.get("types");

                if (expr == null || expr.isNull()
                        || left == null || left.isNull()
                        || right == null || right.isNull()
                        || stmt == null || stmt.isNull()) {
                    continue;
                }

                String expressionType = pickPreferredType(extractTypeNames(types));

                resultList.add(new ExpressionSplitCandidate(
                        (int) expr.asNode().id(),
                        asNullableString(expr.get("name")),
                        new StatementNode(
                                asNullableString(expr.get("code")),
                                asNullableInt(expr.get("startLine")),
                                asNullableInt(expr.get("endLine")),
                                asNullableInt(expr.get("startColumn")),
                                asNullableInt(expr.get("endColumn"))
                        ),
                        new StatementNode(
                                asNullableString(stmt.get("code")),
                                asNullableInt(stmt.get("startLine")),
                                asNullableInt(stmt.get("endLine")),
                                asNullableInt(stmt.get("startColumn")),
                                asNullableInt(stmt.get("endColumn"))
                        ),
                        scope == null || scope.isNull() ? -1 : (int) scope.asNode().id(),
                        expressionType,
                        mapArgument(left),
                        mapArgument(right)
                ));
            }
        }

        return resultList;
    }

    public boolean hasDeclarationInScope(int scopeId, String variableName) {
        if (scopeId < 0 || variableName == null || variableName.isBlank()) {
            return false;
        }

        String cypher = """
                MATCH (d:Declaration)-[:SCOPE]->(s)
                WHERE id(s) = $scopeId AND d.name = $variableName
                RETURN d
                LIMIT 1
                """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of(
                    "scopeId", scopeId,
                    "variableName", variableName
            ));
            return result.hasNext();
        }
    }

    private List<String> extractTypeNames(Value typesValue) {
        List<String> result = new ArrayList<>();

        if (typesValue == null || typesValue.isNull()) {
            return result;
        }

        for (Value typeValue : typesValue.values()) {
            String extracted = extractTypeName(typeValue);
            if (extracted != null && !extracted.isBlank()) {
                result.add(extracted);
            }
        }

        return result;
    }

    private String extractTypeName(Value typeValue) {
        if (typeValue == null || typeValue.isNull()) {
            return null;
        }

        Value candidate = typeValue.get("code");
        if (candidate != null && !candidate.isNull() && !candidate.asString().isBlank()) {
            return candidate.asString();
        }

        candidate = typeValue.get("name");
        if (candidate != null && !candidate.isNull() && !candidate.asString().isBlank()) {
            return candidate.asString();
        }

        candidate = typeValue.get("fullName");
        if (candidate != null && !candidate.isNull() && !candidate.asString().isBlank()) {
            return candidate.asString();
        }

        return null;
    }

    private String pickPreferredType(List<String> rawTypes) {
        if (rawTypes == null || rawTypes.isEmpty()) {
            return null;
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String rawType : rawTypes) {
            String type = normalizeTypeName(rawType);
            if (type != null && !type.isBlank()) {
                normalized.add(type);
            }
        }

        if (normalized.contains("int")) {
            return "int";
        }

        if (normalized.contains("bool")) {
            return "bool";
        }

        if (normalized.contains("boolean")) {
            return "boolean";
        }

        return normalized.iterator().next();
    }

    private String normalizeTypeName(String rawType) {
        if (rawType == null) {
            return null;
        }

        String type = rawType.trim();
        if (type.isEmpty()) {
            return null;
        }

        int genericIndex = type.indexOf('<');
        if (genericIndex >= 0) {
            type = type.substring(0, genericIndex).trim();
        }

        int lastDot = type.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < type.length() - 1) {
            type = type.substring(lastDot + 1);
        }

        return type;
    }

    private BinaryOperationArgument mapArgument(Value value) {
        return new BinaryOperationArgument(
                asNullableString(value.get("code")),
                asNullableInt(value.get("startLine")),
                asNullableInt(value.get("startColumn")),
                asNullableInt(value.get("endLine")),
                asNullableInt(value.get("endColumn"))
        );
    }

    @Override
    public void close() {
        driver.close();
    }
}