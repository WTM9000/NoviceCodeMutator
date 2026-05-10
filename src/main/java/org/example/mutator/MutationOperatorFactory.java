package org.example.mutator;

import org.example.model.FileModel;
import org.example.neo4j.*;

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
            case VARIABLE_ALGEBRAIC_WRAP -> createVariableAlgebraicWrap(fileModel, config, parameters);
            case BINARY_OPERATOR_COMMUTE -> createBinaryOperatorCommute(fileModel, config);
            case WHILE_TO_FOR -> createWhileToFor(fileModel, config);
            case FOR_TO_WHILE -> createForToWhile(fileModel, config);
            case DE_MORGAN -> createDeMorgan(fileModel, config);
            case NEGATED_COMPARISON -> createNegatedComparison(fileModel, config);
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

    private MutationOperator createVariableAlgebraicWrap(FileModel fileModel,
                                                         Neo4jConfig config,
                                                         Map<String, String> parameters) {
        String operator = parameters.get("operator");

        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("Parameter 'operator' must not be blank for VARIABLE_ALGEBRAIC_WRAP");
        }

        VariableRepository repository = new VariableRepository(config);
        return new VariableAlgebraicWrapMutation(fileModel, repository, operator);
    }

    private MutationOperator createBinaryOperatorCommute(FileModel fileModel,
                                                         Neo4jConfig config) {
        BinaryOperatorRepository repository = new BinaryOperatorRepository(config);
        return new BinaryOperatorCommuteMutation(fileModel, repository);
    }

    private MutationOperator createWhileToFor(FileModel fileModel, Neo4jConfig config) {
        WhileLoopRepository repository = new WhileLoopRepository(config);
        return new WhileToForMutation(fileModel, repository);
    }

    private MutationOperator createDeMorgan(FileModel fileModel, Neo4jConfig config) {
        DeMorganRepository repository = new DeMorganRepository(config);
        return new DeMorganMutation(fileModel, repository);
    }

    private MutationOperator createNegatedComparison(FileModel fileModel, Neo4jConfig config) {
        NegatedComparisonRepository repository = new NegatedComparisonRepository(config);
        return new NegatedComparisonMutation(fileModel, repository);
    }
}
