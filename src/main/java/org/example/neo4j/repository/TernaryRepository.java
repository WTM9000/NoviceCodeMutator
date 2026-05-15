package org.example.neo4j.repository;

import org.example.model.BinaryOperationArgument;
import org.example.model.TernaryExpressionNode;
import org.example.neo4j.Neo4jConfig;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TernaryRepository extends NodeRepository implements AutoCloseable {

    private final Driver driver;

    public TernaryRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<TernaryExpressionNode> findAllTernaryExpressions() {

        // Шаг 1: загружаем все ConditionalExpression-узлы плоским списком.
        // Для каждого фиксируем id узлов его трёх частей (cond, then, else).
        // Контекст декларации получаем здесь же.
        String cypherTernaries = """
                MATCH (t:ConditionalExpression)
                OPTIONAL MATCH (t)-[:CONDITION]->(cond)
                OPTIONAL MATCH (t)-[:THEN_EXPRESSION]->(thenNode)
                OPTIONAL MATCH (t)-[:ELSE_EXPRESSION]->(elseNode)
                OPTIONAL MATCH (decl:Declaration)-[:INITIALIZER]->(t)
                OPTIONAL MATCH (decl)-[:TYPE]->(typeNode)
                    WHERE typeNode.name <> 'bool'
                RETURN
                    id(t)        AS tId,
                    id(cond)     AS condId,
                    id(thenNode) AS thenId,
                    id(elseNode) AS elseId,
                    cond.startLine   AS tStartLine,
                    cond.startColumn AS tStartColumn,
                    decl.name        AS varName,
                    typeNode.name    AS typeName
                ORDER BY cond.startLine, cond.startColumn
                """;

        // Шаг 2: загружаем все узлы, у которых есть атрибут code —
        // это листовые выражения (Identifier, NumberLiteral, BinaryExpression и т.д.).
        // ConditionalExpression-узлы здесь тоже присутствуют через thenNode/elseNode,
        // но без code — они будут отсутствовать в этой карте, что и
        // служит признаком вложенного тернарного.
        String cypherLeaves = """
                MATCH (n)
                WHERE n.code IS NOT NULL
                RETURN
                    id(n)      AS nId,
                    n.code        AS code,
                    n.startLine   AS startLine,
                    n.startColumn AS startColumn,
                    n.endLine     AS endLine,
                    n.endColumn   AS endColumn
                """;

        // Карта: nodeId → RawLeaf (атрибуты листового узла)
        Map<Long, RawLeaf> leafMap = new HashMap<>();

        // Сырые строки о каждом тернарном: tId, condId, thenId, elseId + координаты
        List<RawTernaryRow> rows = new ArrayList<>();

        // Карта: tId → RawTernaryRow (для рекурсивной сборки вложенных)
        Map<Long, RawTernaryRow> ternaryMap = new HashMap<>();

        try (Session session = driver.session()) {

            // Загружаем листовые узлы
            Result leavesResult = session.run(cypherLeaves);
            while (leavesResult.hasNext()) {
                Record rec = leavesResult.next();
                long nId = rec.get("nId").asLong();
                leafMap.put(nId, new RawLeaf(
                        asNullableString(rec.get("code")),
                        asNullableInt(rec.get("startLine")),
                        asNullableInt(rec.get("startColumn")),
                        asNullableInt(rec.get("endLine")),
                        asNullableInt(rec.get("endColumn"))
                ));
            }

            // Загружаем тернарные узлы
            Result ternaryResult = session.run(cypherTernaries);
            while (ternaryResult.hasNext()) {
                Record rec = ternaryResult.next();

                if (rec.get("tId").isNull()
                        || rec.get("condId").isNull()
                        || rec.get("thenId").isNull()
                        || rec.get("elseId").isNull()) {
                    continue;
                }

                RawTernaryRow row = new RawTernaryRow(
                        rec.get("tId").asLong(),
                        rec.get("condId").asLong(),
                        rec.get("thenId").asLong(),
                        rec.get("elseId").asLong(),
                        asNullableInt(rec.get("tStartLine")),
                        asNullableInt(rec.get("tStartColumn")),
                        asNullableString(rec.get("varName")),
                        asNullableString(rec.get("typeName"))
                );

                rows.add(row);
                ternaryMap.put(row.tId, row);
            }
        }

        // Шаг 3: для каждой строки рекурсивно строим BinaryOperationArgument
        // для cond, then и else, передавая обе карты.
        List<TernaryExpressionNode> resultList = new ArrayList<>();

        for (RawTernaryRow row : rows) {
            BinaryOperationArgument condArg  = resolveArgument(row.condId,  leafMap, ternaryMap);
            BinaryOperationArgument thenArg  = resolveArgument(row.thenId,  leafMap, ternaryMap);
            BinaryOperationArgument elseArg  = resolveArgument(row.elseId,  leafMap, ternaryMap);

            if (condArg == null || thenArg == null || elseArg == null) {
                continue;
            }

            boolean isDeclaration = row.varName != null && row.typeName != null;

            resultList.add(new TernaryExpressionNode(
                    (int) row.tId,
                    row.tStartLine,
                    row.tStartColumn - 1,
                    elseArg.getEndLine(),
                    elseArg.getEndColumn(),
                    condArg,
                    thenArg,
                    elseArg,
                    isDeclaration,
                    row.typeName,
                    row.varName
            ));
        }

        return resultList;
    }

    /**
     * Рекурсивно строит BinaryOperationArgument для узла с данным id.
     *
     * Если id есть в leafMap — узел является листом, возвращаем его атрибуты напрямую.
     * Если id есть в ternaryMap — узел является ConditionalExpression,
     * рекурсивно собираем code = "cond ? then : else" и координаты
     * от начала cond до конца else.
     * Если id нет нигде — данные отсутствуют, возвращаем null.
     */
    private BinaryOperationArgument resolveArgument(
            long nodeId,
            Map<Long, RawLeaf> leafMap,
            Map<Long, RawTernaryRow> ternaryMap
    ) {
        RawLeaf leaf = leafMap.get(nodeId);
        if (leaf != null) {
            return new BinaryOperationArgument(
                    leaf.code,
                    leaf.startLine,
                    leaf.startColumn,
                    leaf.endLine,
                    leaf.endColumn
            );
        }

        RawTernaryRow nested = ternaryMap.get(nodeId);
        if (nested != null) {
            BinaryOperationArgument condArg = resolveArgument(nested.condId, leafMap, ternaryMap);
            BinaryOperationArgument thenArg = resolveArgument(nested.thenId, leafMap, ternaryMap);
            BinaryOperationArgument elseArg = resolveArgument(nested.elseId, leafMap, ternaryMap);

            if (condArg == null || thenArg == null || elseArg == null) {
                return null;
            }

            String code = condArg.getCode() + " ? " + thenArg.getCode() + " : " + elseArg.getCode();

            return new BinaryOperationArgument(
                    code,
                    condArg.getStartLine(),
                    condArg.getStartColumn(),
                    elseArg.getEndLine(),
                    elseArg.getEndColumn()
            );
        }

        return null;
    }

    // ─── Внутренние record-классы ────────────────────────────────────────────

    /** Листовой узел с атрибутами из Neo4j. */
    private record RawLeaf(
            String code,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn
    ) {}

    /**
     * Сырая строка о ConditionalExpression-узле:
     * хранит id самого узла и id его трёх частей.
     */
    private record RawTernaryRow(
            long tId,
            long condId,
            long thenId,
            long elseId,
            int tStartLine,
            int tStartColumn,
            String varName,
            String typeName
    ) {}

    @Override
    public void close() {
        driver.close();
    }
}