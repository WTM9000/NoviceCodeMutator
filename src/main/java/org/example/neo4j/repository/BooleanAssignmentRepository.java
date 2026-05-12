package org.example.neo4j.repository;

import org.example.model.BinaryOperationArgument;
import org.example.model.BooleanAssignmentCandidate;
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
import java.util.List;

public class BooleanAssignmentRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public BooleanAssignmentRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    // Случай 1: standalone присвоение — f = cond;
    public List<BooleanAssignmentCandidate> findStandaloneAssignments() {
        String cypher = """
            MATCH (assign:AssignExpression)
            MATCH (assign)-[:LHS]->(left:Reference)
            MATCH (assign)-[:RHS]->(right)
            WHERE (
               (right:BinaryOperator AND right.name IN ['<', '<=', '>', '>=', '==', '!=', '&&', '||'])
               OR
               (right:UnaryOperator AND right.name = '!')
               OR
               (right.code IN ['true', 'false'])
               )
               AND NOT EXISTS {
                   MATCH (right)-[:AST*1..]->(badCall:Call)
               }
               AND NOT EXISTS {
                   MATCH (right)-[:AST*1..]->(badAssign:BinaryOperator)
                   WHERE badAssign.name IN ['=', '+=', '-=', '*=', '/=', '%=', '&=', '|=', '^=', '<<=', '>>=', '>>>=']
               }
               AND NOT EXISTS {
                   MATCH (right)-[:AST*1..]->(badUnary:UnaryOperator)
                   WHERE badUnary.name IN ['++', '--']
               }
            MATCH (stmt)-[:AST]->(assign)
            WHERE NOT EXISTS {
                (stmt)-[:CONDITION]->(assign)
            }
            OPTIONAL MATCH (assign)-[:SCOPE]->(scope)
            OPTIONAL MATCH (parent)-[:AST]->(stmt)
            RETURN assign, left, right, stmt, scope, parent
            ORDER BY assign.startLine, assign.startColumn
            """;

        List<BooleanAssignmentCandidate> result = new ArrayList<>();

        try (Session session = driver.session()) {
            Result queryResult = session.run(cypher);

            while (queryResult.hasNext()) {
                Record record = queryResult.next();

                Value assign = record.get("assign");
                Value left   = record.get("left");
                Value right  = record.get("right");
                Value stmt   = record.get("stmt");
                Value scope  = record.get("scope");
                Value parent = record.get("parent");

                if (isAnyNull(assign, left, right, stmt)) {
                    continue;
                }

                // oneLineDirectBody: родитель stmt — ControlStructure, но НЕ через Block.
                // Если родитель — Block, тело уже обёрнуто в фигурные скобки.
                boolean oneLineDirectBody = isOneLineDirectBody(parent, stmt);

                // В runQuery, после получения stmt:
                List<String> stmtLabels = new ArrayList<>();

                stmt.asNode().labels().forEach(stmtLabels::add);
                boolean returnContext = stmtLabels.contains("ReturnStatement");

                result.add(new BooleanAssignmentCandidate(
                        (int) assign.asNode().id(),
                        mapStatementNode(assign),
                        mapStatementNode(stmt),
                        scope == null || scope.isNull() ? -1 : (int) scope.asNode().id(),
                        mapArgument(left),
                        mapArgument(right),
                        oneLineDirectBody,
                        false,
                        returnContext,
                        null
                ));
            }
        }

        return result;
    }

    // Случай 2: объявление с инициализацией — int f = cond;
    public List<BooleanAssignmentCandidate> findDeclarationAssignments() {
        String cypher = """
            MATCH (declStmt:DeclarationStatement)
            MATCH (declStmt)-[:DECLARATIONS]->(decl:Declaration)
            MATCH (decl)-[:INITIALIZER]->(right)
            WHERE (
                        (right:BinaryOperator AND right.name IN ['<', '<=', '>', '>=', '==', '!=', '&&', '||'])
                        OR
                        (right:UnaryOperator AND right.name = '!')
                        OR
                        (right.code IN ['true', 'false'])
                    )
                   AND NOT EXISTS {
                        MATCH (right)-[:AST*1..]->(badCall:Call)
                    }
                    AND NOT EXISTS {
                        MATCH (right)-[:AST*1..]->(badAssign:BinaryOperator)
                        WHERE badAssign.name IN ['=', '+=', '-=', '*=', '/=', '%=', '&=', '|=', '^=', '<<=', '>>=', '>>>=']
                    }
                    AND NOT EXISTS {
                        MATCH (right)-[:AST*1..]->(badUnary:UnaryOperator)
                        WHERE badUnary.name IN ['++', '--']
                    }
            OPTIONAL MATCH (declStmt)-[:SCOPE]->(scope)
            OPTIONAL MATCH (parent)-[:AST]->(declStmt)
            RETURN decl, decl.name as left, right, declStmt AS stmt, scope, parent, declStmt AS declarationNode
            ORDER BY declStmt.startLine, declStmt.startColumn
            """;

        List<BooleanAssignmentCandidate> result = new ArrayList<>();

        try (Session session = driver.session()) {
            Result queryResult = session.run(cypher);

            while (queryResult.hasNext()) {
                Record record = queryResult.next();

                Value decl   = record.get("decl");
                Value left   = record.get("left");
                Value right  = record.get("right");
                Value stmt   = record.get("stmt");
                Value scope  = record.get("scope");
                Value parent = record.get("parent");

                if (isAnyNull(decl, left, right, stmt)) {
                    continue;
                }

                boolean oneLineDirectBody = isOneLineDirectBody(parent, stmt);

                StatementNode declarationNode = mapStatementNode(record.get("declarationNode"));

                result.add(new BooleanAssignmentCandidate(
                        (int) decl.asNode().id(),
                        mapStatementNode(stmt),   // assignmentExpression — здесь это declStmt целиком
                        mapStatementNode(stmt),
                        scope == null || scope.isNull() ? -1 : (int) scope.asNode().id(),
                        new BinaryOperationArgument(
                                left.asString(),
                                asNullableInt(decl.get("startLine")),
                                asNullableInt(decl.get("startColumn")),
                                asNullableInt(decl.get("endLine")),
                                asNullableInt(decl.get("endColumn"))
                        ),
                        mapArgument(right),
                        oneLineDirectBody,
                        true,
                        false,
                        declarationNode
                ));
            }
        }

        return result;
    }

    // Вычисляем oneLineDirectBody:
    // Если непосредственный родитель stmt — ControlStructure (if/while/for),
    // то тело — однострочное (без {}).
    // Если родитель — Block, то тело уже в фигурных скобках.
    private boolean isOneLineDirectBody(Value parent, Value stmt) {
        if (parent == null || parent.isNull()) {
            return false;
        }

        List<String> parentLabels = new ArrayList<>();
        parent.asNode().labels().forEach(parentLabels::add);

        if (!parentLabels.contains("LoopStatement") && !parentLabels.contains("IfStatement")) {
            // Родитель — не if/while/for, значит это просто standalone statement
            return false;
        }

        // stmt — это Block (тело ControlStructure).
        // Если его code начинается с '{' — тело в фигурных скобках.
        // Если нет — однострочное тело без скобок.
        String stmtCode = asNullableString(stmt.get("code"));
        if (stmtCode == null || stmtCode.isBlank()) {
            return false;
        }

        return !stmtCode.trim().startsWith("{");
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

    private BinaryOperationArgument mapArgument(Value value) {
        return new BinaryOperationArgument(
                asNullableString(value.get("code")),
                asNullableInt(value.get("startLine")),
                asNullableInt(value.get("startColumn")),
                asNullableInt(value.get("endLine")),
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
    public void close() {
        driver.close();
    }
}