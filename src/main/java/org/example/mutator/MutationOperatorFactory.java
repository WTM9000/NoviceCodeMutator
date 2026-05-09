package org.example.mutator;

import org.example.model.FileModel;
import org.example.neo4j.ForLoopRepository;
import org.example.neo4j.Neo4jConfig;
import org.example.neo4j.NodeRepository;
import org.example.neo4j.VariableRepository;

import java.util.Map;

public class MutationOperatorFactory {

    public MutationOperator create(MutationType mutationType,
                                   FileModel fileModel,
                                   Neo4jConfig config,
                                   Map<String, String> parameters) {
        if (mutationType == null) {
            throw new IllegalArgumentException("mutationType must not be null");
        }

        if (fileModel == null) {
            throw new IllegalArgumentException("fileModel must not be null");
        }

        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        if (parameters == null) {
            parameters = Map.of();
        }

        return switch (mutationType) {
            case VARIABLE_NAME_REPLACE -> createVariableNameReplace(fileModel, config, parameters);
            case FOR_TO_WHILE -> createForToWhile(fileModel, config);
        };
    }

    private MutationOperator createVariableNameReplace(FileModel fileModel,
                                                       Neo4jConfig config,
                                                       Map<String, String> parameters) {
        String newVariableName = parameters.get("newVariableName");

        if (newVariableName == null || newVariableName.isBlank()) {
            throw new IllegalArgumentException("Parameter 'newVariableName' must not be blank for VARIABLE_NAME_REPLACE");
        }

        VariableRepository repository = new VariableRepository(config);
        return new VariableNameReplaceMutation(fileModel, repository, newVariableName);
    }

    private MutationOperator createForToWhile(FileModel fileModel,
                                              Neo4jConfig config) {
        ForLoopRepository repository = new ForLoopRepository(config);
        return new ForToWhileMutation(fileModel, repository);
    }
}
