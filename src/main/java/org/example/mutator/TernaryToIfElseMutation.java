package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.TernaryExpressionNode;
import org.example.model.BinaryOperationArgument;
import org.example.neo4j.TernaryRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TernaryToIfElseMutation extends MutationOperator {

    private TernaryExpressionNode selectedExpression;
    private final Random random = new Random();

    public TernaryToIfElseMutation(FileModel originalFile, TernaryRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        TernaryRepository repository = (TernaryRepository) this.repo;

        List<TernaryExpressionNode> candidates = repository.findAllTernaryExpressions();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No ternary expressions found.");
            return;
        }

        System.out.println("Found ternary expressions: " + candidates.size());
        for (TernaryExpressionNode candidate : candidates) {
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

        if (selectedExpression.getTernaryStartLine() != selectedExpression.getTernaryEndLine()) {
            System.out.println("Multiline ternary expressions are not supported.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());
        int lineIndex = selectedExpression.getTernaryStartLine() - 1;

        String sourceLine = newLines.get(lineIndex);
        String indentation = leadingWhitespace(sourceLine);

        int replaceStart = selectedExpression.getTernaryStartColumn() - 1;
        int replaceEnd = selectedExpression.getTernaryEndColumn() - 1;

        String prefix = sourceLine.substring(0, replaceStart).stripTrailing();
        String suffix = sourceLine.substring(replaceEnd).stripLeading();

        if (suffix.equals(";")) {
            suffix = "";
        }

        BinaryOperationArgument cond = selectedExpression.getCondition();
        BinaryOperationArgument thenBranch = selectedExpression.getThenBranch();
        BinaryOperationArgument elseBranch = selectedExpression.getElseBranch();

        String condCode = cond.getCode();
        String thenCode = thenBranch.getCode();
        String elseCode = elseBranch.getCode();

        String thenStatement;
        String elseStatement;

        if (prefix.isBlank()) {
            thenStatement = thenCode + ";";
            elseStatement = elseCode + ";";
        } else {
            thenStatement = prefix + " " + thenCode + suffix + ";";
            elseStatement = prefix + " " + elseCode + suffix + ";";
        }

        String ifLine   = indentation + "if (" + condCode + ") " + thenStatement;
        String elseLine = indentation + "else " + elseStatement;

        // Удаляем оригинальную строку и вставляем две новые на её место
        newLines.remove(lineIndex);
        newLines.add(lineIndex, elseLine);
        newLines.add(lineIndex, ifLine);

        String newName = buildNewName(originalFile.getFileName());
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Ternary To If-Else Mutation";
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    private String buildNewName(String oldName) {
        int lastDot = oldName.lastIndexOf('.');
        LocalDateTime now = LocalDateTime.now();
        String suffix = "_" + now.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"))
                + "_" + now.format(DateTimeFormatter.ofPattern("ss_mm_HH"))
                + "_ternary_to_if";

        if (lastDot > 0) {
            return oldName.substring(0, lastDot) + suffix + oldName.substring(lastDot);
        }
        return oldName + suffix;
    }
}
