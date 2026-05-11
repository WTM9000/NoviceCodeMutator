package org.example.neo4j.repository;

import org.example.neo4j.Neo4jConfig;
import org.example.model.VariableNode;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import java.util.ArrayList;
import java.util.List;

public class VariableRepository extends NodeRepository implements AutoCloseable {
    private final Driver driver;

    public VariableRepository(Neo4jConfig config) {
        this.driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword())
        );
    }

    public List<VariableNode> findAllVariableDeclarations() {
        String cypher = """
                MATCH (v:VariableDeclaration)
                RETURN id(v) AS id,
                       v.name AS name,
                       v.startLine AS line,
                       v.startColumn as column
                       
                ORDER BY name
                """;

        List<VariableNode> resultList = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);

            while (result.hasNext()) {
                Record record = result.next();

                if (record.get("id").isNull()){
                    return resultList;
                }

                int id = record.get("id").asInt();
                String name = asNullableString(record.get("name"));
                int line = (record.get("line").asInt());
                int column = record.get("column").asInt();

                resultList.add(new VariableNode(id, name, line, column));
            }
        }

        return resultList;
    }

    public List<VariableNode> findAllReferencesToVariable(int referenceId){
        String cypher = """
                MATCH (decl)
                WHERE id(decl) = """ + referenceId + """
                                
                MATCH (use)-[:REFERS_TO]->(decl)
                                
                RETURN id(use) AS id,
                       use.name AS name,
                       use.startLine AS line,
                       use.startColumn as column
                       
                ORDER BY use.startLine DESC, use.startColumn DESC;
                """;

        List<VariableNode> resultList = new ArrayList<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypher);



            while (result.hasNext()) {
                Record record = result.next();

                if (record.get("id").isNull()){
                    return resultList;
                }

                int id = record.get("id").asInt();
                String name = asNullableString(record.get("name"));
                int line = (record.get("line").asInt());
                int column = record.get("column").asInt();

                resultList.add(new VariableNode(id, name, line, column));
            }
        }

        return resultList;
    }

    private String asNullableString(Value value) {
        return value.isNull() ? null : value.asString();
    }

    @Override
    public void close() {
        driver.close();
    }
}
