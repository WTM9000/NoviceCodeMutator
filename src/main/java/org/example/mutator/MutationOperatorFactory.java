package org.example.mutator;

import org.example.model.FileModel;
import org.example.neo4j.*;
import org.example.neo4j.repository.*;

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
            case REDUNDANT_ASSIGNMENT -> createRedundantAssignment(fileModel, config);
            case SYNCHRONIZED_VARIABLES -> createSynchronizedVariables(fileModel, config, parameters);
            case EMPTY_EXPRESSION -> createEmptyExpression(fileModel, config);
            case EMPTY_LOOP -> createEmptyLoop(fileModel, config);
            case EMPTY_INITIALIZATION -> createEmptyInitialization(fileModel, config);
            case CONTINUE_UNREACHABLE -> createContinueUnreachable(fileModel, config);
            case RETURN_UNREACHABLE -> createReturnUnreachable(fileModel, config);
            case VARIABLE_NAME_REPLACE -> createVariableNameReplace(fileModel, config, parameters);
            case FOR_TO_WHILE -> createForToWhile(fileModel, config);
            case BOOLEAN_ASSIGNMENT_TO_IF -> createBooleanAssignmentToIf(fileModel, config);
            case VARIABLE_DECLARATION_HOIST -> createVariableDeclarationHoist(fileModel, config);
            case CONTINUE_ANTI_IDIOM -> createContinueAntiIdiom(fileModel, config, parameters);
            case ELSE_IF_FLATTEN -> createElseIfFlatten(fileModel, config);
            case COMPOUND_CONDITION_EXPAND -> createCompoundConditionExpand(fileModel, config);
            case IF_TO_WHILE_CONVERT -> createIfToWhile(fileModel, config);
            case DO_WHILE_TO_WHILE -> createDoWhileToWhile(fileModel, config);
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

    private MutationOperator createBooleanAssignmentToIf(FileModel fileModel,
                                                         Neo4jConfig config) {
        BooleanAssignmentRepository repository = new BooleanAssignmentRepository(config);
        return new BooleanAssignmentToIfMutation(fileModel, repository);
    }

    private MutationOperator createVariableDeclarationHoist(FileModel fileModel,
                                                            Neo4jConfig config) {
        VariableDeclarationHoistRepository repository =
                new VariableDeclarationHoistRepository(config);
        return new VariableDeclarationHoistMutation(fileModel, repository);
    }

    private MutationOperator createContinueAntiIdiom(FileModel fileModel,
                                                     Neo4jConfig config,
                                                     Map<String, String> parameters) {
        boolean addDeadCode = Boolean.parseBoolean(
                parameters.getOrDefault("addDeadCode", "false")
        );
        ContinueAntiIdiomRepository repository = new ContinueAntiIdiomRepository(config);
        return new ContinueAntiIdiomMutation(fileModel, repository, addDeadCode);
    }

    private MutationOperator createElseIfFlatten(FileModel fileModel, Neo4jConfig config) {
        ElseIfFlattenRepository repository = new ElseIfFlattenRepository(config);
        return new ElseIfFlattenMutation(fileModel, repository);
    }

    private MutationOperator createCompoundConditionExpand(FileModel fileModel,
                                                           Neo4jConfig config) {
        CompoundConditionExpandRepository repository =
                new CompoundConditionExpandRepository(config);
        return new CompoundConditionExpandMutation(fileModel, repository);
    }

    private MutationOperator createRedundantAssignment(FileModel fileModel,
                                                       Neo4jConfig config) {
        SimpleAssignmentRepository repository =
                new SimpleAssignmentRepository(config);
        return new RedundantAssignmentMutation(fileModel, repository);
    }

    private MutationOperator createSynchronizedVariables(FileModel fileModel,
                                                         Neo4jConfig config,
                                                         Map<String, String> parameters) {
        String syncVarX = parameters.get("syncVarX");
        String syncVarY = parameters.get("syncVarY");

        if (syncVarX == null || syncVarX.isBlank()) {
            throw new IllegalArgumentException(
                    "Parameter 'syncVarX' must not be blank for SYNCHRONIZED_VARIABLES");
        }

        if (syncVarY == null || syncVarY.isBlank()) {
            throw new IllegalArgumentException(
                    "Parameter 'syncVarY' must not be blank for SYNCHRONIZED_VARIABLES");
        }

        SynchronizedVariablesRepository repository = new SynchronizedVariablesRepository(config);
        return new SynchronizedVariablesMutation(fileModel, repository, syncVarX, syncVarY);
    }

    private MutationOperator createEmptyExpression(FileModel fileModel, Neo4jConfig config) {
        EmptyExpressionRepository repository = new EmptyExpressionRepository(config);
        return new EmptyExpressionMutation(fileModel, repository);
    }

    private MutationOperator createEmptyLoop(FileModel fileModel, Neo4jConfig config) {
        EmptyLoopRepository repository = new EmptyLoopRepository(config);
        return new EmptyLoopMutation(fileModel, repository);
    }

    private MutationOperator createContinueUnreachable(FileModel fileModel, Neo4jConfig config) {
        ContinueUnreachableRepository repository = new ContinueUnreachableRepository(config);
        return new ContinueUnreachableMutation(fileModel, repository);
    }

    private MutationOperator createReturnUnreachable(FileModel fileModel, Neo4jConfig config) {
        ReturnUnreachableRepository repository = new ReturnUnreachableRepository(config);
        return new ReturnUnreachableMutation(fileModel, repository);
    }

    private MutationOperator createIfToWhile(FileModel fileModel, Neo4jConfig config) {
        IfToWhileLoopRepository repository = new IfToWhileLoopRepository(config);
        return new IfToWhileLoopMutation(fileModel, repository);
    }

    private MutationOperator createDoWhileToWhile(FileModel fileModel, Neo4jConfig config) {
        DoWhileLoopRepository repository = new DoWhileLoopRepository(config);
        return new DoWhileToWhileMutation(fileModel, repository);
    }

    private MutationOperator createEmptyInitialization(FileModel fileModel, Neo4jConfig config) {
        EmptyInitializationRepository repository = new EmptyInitializationRepository(config);
        return new EmptyInitializationMutation(fileModel, repository);
    }
}
