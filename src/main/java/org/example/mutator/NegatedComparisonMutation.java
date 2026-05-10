package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.NegatedComparisonNode;
import org.example.model.BinaryOperationArgument;
import org.example.neo4j.NegatedComparisonRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class NegatedComparisonMutation extends MutationOperator {

    private NegatedComparisonNode selectedExpression;
    private final Random random = new Random();

    private static final Map<String, String> INVERTED_OPERATORS = Map.of(
            "<",  ">=",
            "<=", ">",
            ">",  "<=",
            ">=", "<",
            "==", "!=",
            "!=", "=="
    );

    public NegatedComparisonMutation(FileModel originalFile, NegatedComparisonRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        NegatedComparisonRepository repository = (NegatedComparisonRepository) this.repo;

        List<NegatedComparisonNode> candidates = repository.findAllNegatedComparisons();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No negated comparison candidates found.");
            return;
        }

        System.out.println("Found negated comparison candidates: " + candidates.size());
        for (NegatedComparisonNode candidate : candidates) {
            System.out.println(candidate);
        }

        selectedExpression = candidates.get(random.nextInt(candidates.size()));
        System.out.println("Selected: " + selectedExpression);
    }

    @Override
    protected FileModel mutate() {
        if (selectedExpression == null) {
            return null;
        }

        if (selectedExpression.getUnaryStartLine() != selectedExpression.getUnaryEndLine()) {
            System.out.println("Multiline negated comparisons are not supported.");
            return null;
        }

        String invertedOperator = INVERTED_OPERATORS.get(selectedExpression.getComparisonOperator());
        if (invertedOperator == null) {
            System.out.println("Unknown comparison operator: " + selectedExpression.getComparisonOperator());
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());
        int lineIndex = selectedExpression.getUnaryStartLine() - 1;

        String sourceLine = newLines.get(lineIndex);

        int replaceStart = selectedExpression.getUnaryStartColumn() - 1;
        int replaceEnd = selectedExpression.getUnaryEndColumn() - 1;

        String replacement = buildReplacement(selectedExpression, invertedOperator);

        String newLine = sourceLine.substring(0, replaceStart)
                + replacement
                + sourceLine.substring(replaceEnd);

        newLines.set(lineIndex, newLine);

        String newName = buildNewName(originalFile.getFileName());
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Negated Comparison Mutation";
    }

    private String buildReplacement(NegatedComparisonNode expr, String invertedOperator) {
        BinaryOperationArgument left = expr.getLeftArgument();
        BinaryOperationArgument right = expr.getRightArgument();

        // !(a < b) -> a >= b: скобки и отрицание убираются полностью
        return left.getCode() + " " + invertedOperator + " " + right.getCode();
    }

    private String buildNewName(String oldName) {
        int lastDot = oldName.lastIndexOf('.');
        LocalDateTime now = LocalDateTime.now();
        String suffix = "_" + now.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"))
                + "_" + now.format(DateTimeFormatter.ofPattern("ss_mm_HH"))
                + "_negated_comparison";

        if (lastDot > 0) {
            return oldName.substring(0, lastDot) + suffix + oldName.substring(lastDot);
        }
        return oldName + suffix;
    }
}
