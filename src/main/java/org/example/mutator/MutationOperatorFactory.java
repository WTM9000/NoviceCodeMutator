package org.example.mutator;

import org.example.model.FileModel;
import org.example.neo4j.ForLoopRepository;
import org.example.neo4j.Neo4jConfig;
import org.example.neo4j.NodeRepository;
import org.example.neo4j.VariableRepository;

public class MutationOperatorFactory {

    public MutationOperator create(MutationType mutationType,
                                   FileModel fileModel,
                                   Neo4jConfig config,
                                   String newVariableName) {
        if (mutationType == null) {
            throw new IllegalArgumentException("mutationType must not be null");
        }

        return switch (mutationType) {
            case VARIABLE_NAME_REPLACE -> new VariableNameReplaceMutation(fileModel, new VariableRepository(config), newVariableName);
            case FOR_TO_WHILE -> new ForToWhileMutation(fileModel, new ForLoopRepository(config));
            default -> null;
        };
    }
}
