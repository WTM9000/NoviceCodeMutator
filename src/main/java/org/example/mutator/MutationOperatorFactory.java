package org.example.mutator;

import org.example.model.FileModel;
import org.example.neo4j.NodeRepository;
import org.example.neo4j.VariableRepository;

public class MutationOperatorFactory {

    public MutationOperator create(MutationType mutationType,
                                   FileModel fileModel,
                                   NodeRepository repository,
                                   String newVariableName) {
        if (mutationType == null) {
            throw new IllegalArgumentException("mutationType must not be null");
        }

        switch (mutationType) {
            case VARIABLE_NAME_REPLACE ->{
                if (!(repository instanceof VariableRepository))
                    throw new IllegalArgumentException("Can't create VariableNameReplaceMutation instance, repository isn't VariableRepository");

                VariableRepository variableRepo = (VariableRepository)repository;
                return new VariableNameReplaceMutation(fileModel, variableRepo, newVariableName);
            }
            default -> {
                return null;
            }
        }
    }
}
