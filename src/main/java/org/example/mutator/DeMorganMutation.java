package org.example.mutator;

import org.example.model.DeMorganExpressionNode;
import org.example.model.FileModel;
import org.example.model.BinaryOperationArgument;
import org.example.neo4j.DeMorganRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DeMorganMutation extends MutationOperator {

    private DeMorganExpressionNode selectedExpression;
    private final Random random = new Random();

    public DeMorganMutation(FileModel originalFile, DeMorganRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        DeMorganRepository repository = (DeMorganRepository) this.repo;

        List<DeMorganExpressionNode> candidates = repository.findAllDeMorganCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No De Morgan candidates found.");
            return;
        }

        System.out.println("Found De Morgan candidates: " + candidates.size());
        for (DeMorganExpressionNode candidate : candidates) {
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
            System.out.println("Multiline De Morgan expressions are not supported.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());
        int lineIndex = selectedExpression.getUnaryStartLine() - 1;

        String sourceLine = newLines.get(lineIndex);

        int replaceStart = selectedExpression.getUnaryStartColumn() - 1;
        int replaceEnd = selectedExpression.getUnaryEndColumn() - 1;

        String replacement = buildReplacement(selectedExpression);

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
        return "De Morgan Law Mutation";
    }

    private String buildReplacement(DeMorganExpressionNode expr) {
        String invertedOperator = "&&".equals(expr.getBinaryOperatorType()) ? "||" : "&&";

        BinaryOperationArgument left = expr.getLeftArgument();
        BinaryOperationArgument right = expr.getRightArgument();

        String negatedLeft = "!(" + left.getCode() + ")";
        String negatedRight = "!(" + right.getCode() + ")";

        return "(" + negatedLeft + " " + invertedOperator + " " + negatedRight + ")";
    }

    private boolean needsParens(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String trimmed = code.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return false;
        }
        return trimmed.contains(" ");
    }

    private String buildNewName(String oldName) {
        int lastDot = oldName.lastIndexOf('.');
        LocalDateTime now = LocalDateTime.now();
        String suffix = "_" + now.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"))
                + "_" + now.format(DateTimeFormatter.ofPattern("ss_mm_HH"))
                + "_de_morgan";

        if (lastDot > 0) {
            return oldName.substring(0, lastDot) + suffix + oldName.substring(lastDot);
        }
        return oldName + suffix;
    }
}