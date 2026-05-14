package org.example.neo4j.repository;

import org.example.model.DeclarationStatementGroup;
import org.example.model.SingleDeclarator;
import org.example.model.StatementNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.*;

public class VariableDeclarationHoistRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public VariableDeclarationHoistRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<MethodBodyInfo> findAllFunctionBodies() {
        String cypher = """
                MATCH (m:FunctionDeclaration)
                MATCH (m)-[:AST]->(body:Block)-[]->(parentScope:Scope)
                MATCH (childScope:Scope)-[:PARENT]->(parentScope)
                RETURN m.name        AS name,
                       body.startLine AS startLine,
                       body.endLine   AS endLine,
                       body.code      AS code,
                       id(childScope) AS scopeId
                ORDER BY m.name
                """;

        List<MethodBodyInfo> result = new ArrayList<>();

        try (Session session = driver.session()) {
            Result queryResult = session.run(cypher);
            while (queryResult.hasNext()) {
                Record record = queryResult.next();
                StatementNode body = new StatementNode(
                        asNullableString(record.get("code")),
                        asNullableInt(record.get("startLine")),
                        asNullableInt(record.get("endLine")),
                        -1, -1
                );
                result.add(new MethodBodyInfo(
                        asNullableString(record.get("name")),
                        body,
                        record.get("scopeId").asLong()
                ));
            }
        }

        return result;
    }

    // Возвращает все DeclarationStatement в scope, сгруппированные по stmt
    public List<DeclarationStatementGroup> findDeclarationGroups(long scopeId) {
        String cypher = """
                MATCH (declStmt:DeclarationStatement)-[]->(scope)
                WHERE id(scope) = $scopeId
                MATCH (declStmt)-[:DECLARATIONS]->(decl:Declaration)
                MATCH (decl)-[:TYPE]->(t)
                WHERE t.name <> 'bool'
                OPTIONAL MATCH (decl)-[:INITIALIZER]->(initializer)
                RETURN declStmt,
                       id(decl)          AS declId,
                       decl.name         AS varName,
                       t.name            AS typeName,
                       initializer.code  AS initCode
                ORDER BY declStmt.startLine, declId
                """;

        // LinkedHashMap сохраняет порядок по startLine
        LinkedHashMap<Integer, GroupAccumulator> accumulators = new LinkedHashMap<>();

        try (Session session = driver.session()) {
            Result queryResult = session.run(cypher, Map.of("scopeId", scopeId));

            while (queryResult.hasNext()) {
                Record record = queryResult.next();

                Value declStmtValue = record.get("declStmt");
                if (declStmtValue.isNull()) continue;

                int startLine = asNullableInt(declStmtValue.get("startLine"));
                String varName = asNullableString(record.get("varName"));
                String typeName  = asNullableString(record.get("typeName"));
                String initCode = asNullableString(record.get("initCode"));
                long declId = record.get("declId").asLong();

                if (varName == null) continue;
                if (typeName == null) continue;

                accumulators.computeIfAbsent(startLine, k ->
                        new GroupAccumulator(mapStatementNode(declStmtValue))
                ).addDeclarator(new SingleDeclarator(
                        (int) declId,
                        varName,
                        typeName,
                        initCode != null,
                        initCode
                ));
            }
        }

        List<DeclarationStatementGroup> result = new ArrayList<>();
        for (GroupAccumulator acc : accumulators.values()) {
            result.add(new DeclarationStatementGroup(
                    acc.declarationStatement,
                    acc.declarators
            ));
        }
        return result;
    }

    // Внутренний аккумулятор для группировки
    private static class GroupAccumulator {
        final StatementNode declarationStatement;
        final List<SingleDeclarator> declarators = new ArrayList<>();

        GroupAccumulator(StatementNode declarationStatement) {
            this.declarationStatement = declarationStatement;
        }

        void addDeclarator(SingleDeclarator d) {
            declarators.add(d);
        }
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

    public static class MethodBodyInfo {
        private final String methodName;
        private final StatementNode body;
        private final long scopeId;

        public MethodBodyInfo(String methodName, StatementNode body, long scopeId) {
            this.methodName = methodName;
            this.body = body;
            this.scopeId = scopeId;
        }

        public String getMethodName() { return methodName; }
        public StatementNode getBody(){ return body; }
        public long getScopeId()      { return scopeId; }
    }
}