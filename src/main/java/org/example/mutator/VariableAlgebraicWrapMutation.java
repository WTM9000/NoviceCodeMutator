package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.VariableNode;
import org.example.neo4j.repository.VariableRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VariableAlgebraicWrapMutation extends MutationOperator {

    private List<VariableNode> variablesToChange = null;
    private final String operator;
    private final Random random = new Random();

    public VariableAlgebraicWrapMutation(FileModel originalFile, VariableRepository repo, String operator) {
        super(originalFile, repo);
        this.originalFile = originalFile;
        this.operator = operator;
    }

    public FileModel getOriginalFile() {
        return originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        VariableRepository repository = (VariableRepository) this.repo;

        List<VariableNode> allUsages = repository.findWrappableReferences();

        variablesToChange = new ArrayList<>();
        VariableNode selected = allUsages.get(random.nextInt(allUsages.size()));
        variablesToChange.add(selected);

        System.out.println("Selected variable usage for algebraic wrap: " + selected);
    }

    @Override
    protected FileModel mutate() {
        if (variablesToChange == null || variablesToChange.isEmpty()) {
            return null;
        }

        if (!isSupportedOperator(operator)) {
            System.out.println("Unsupported algebraic operator: " + operator);
            return null;
        }

        FileModel mutatedFile = originalFile;
        List<String> newLines = new ArrayList<>(originalFile.getLines());

        VariableNode variable = variablesToChange.get(0);
        String sourceLine = newLines.get(variable.getLine() - 1);

        int oldVariableStart = variable.getColumn() - 1;
        int oldVariableEnd = oldVariableStart + variable.getName().length();

        String replacement = buildReplacement(variable.getName());

        StringBuilder newString = new StringBuilder(sourceLine)
                .delete(oldVariableStart, oldVariableEnd)
                .insert(oldVariableStart, replacement);

        newLines.set(variable.getLine() - 1, newString.toString());

        String newName = buildNewName(originalFile.getFileName(), "_algebraicWrap");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        mutatedFile = new FileModel(newName, newPath, newLines);
        return mutatedFile;
    }

    @Override
    public String getMutationName() {
        return "Variable Algebraic Wrap";
    }

    private boolean isSupportedOperator(String value) {
        return "+".equals(value)
                || "-".equals(value)
                || "*".equals(value)
                || "/".equals(value);
    }

    private String buildReplacement(String variableName) {
        return switch (operator) {
            case "+" -> "(" + variableName + " + 0)";
            case "-" -> "(" + variableName + " - 0)";
            case "*" -> "(" + variableName + " * 1)";
            case "/" -> "(" + variableName + " / 1)";
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }
}
