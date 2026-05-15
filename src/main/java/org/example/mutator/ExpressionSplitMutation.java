package org.example.mutator;

import org.example.model.ExpressionSplitCandidate;
import org.example.model.FileModel;
import org.example.model.BinaryOperationArgument;
import org.example.neo4j.repository.ExpressionSplitRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ExpressionSplitMutation extends MutationOperator {

    private static final Set<String> SUPPORTED_OPERATORS = Set.of(
            "+", "-", "*", "/", "%", "&", "|", "^", "<<", ">>", ">>>"
    );

    private final Random random = new Random();
    private final String newVariableName;

    private ExpressionSplitCandidate selectedCandidate;
    private boolean hasDeclarationConflict = false;

    public ExpressionSplitMutation(FileModel originalFile,
                                   ExpressionSplitRepository repo,
                                   String newVariableName) {
        super(originalFile, repo);
        this.originalFile = originalFile;
        this.newVariableName = newVariableName;
    }

    @Override
    protected void getRelevantNodes() {
        ExpressionSplitRepository repository = (ExpressionSplitRepository) this.repo;

        List<ExpressionSplitCandidate> candidates = repository.findAllCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No expression split candidates found.");
            return;
        }

        List<ExpressionSplitCandidate> filtered = new ArrayList<>();
        for (ExpressionSplitCandidate candidate : candidates) {
            if (isSupportedCandidate(candidate)) {
                filtered.add(candidate);
            }
        }

        if (filtered.isEmpty()) {
            System.out.println("No supported expression split candidates found.");
            return;
        }

        System.out.println("Found expression split candidates: " + filtered.size());
        for (ExpressionSplitCandidate candidate : filtered) {
            System.out.println(candidate);
        }

        selectedCandidate = filtered.get(random.nextInt(filtered.size()));

        hasDeclarationConflict = repository.hasDeclarationInScope(
                selectedCandidate.getScopeId(),
                newVariableName
        );

        if (hasDeclarationConflict) {
            System.out.println("Declaration conflict detected for variable: " + newVariableName);
            return;
        }

        System.out.println("Selected: " + selectedCandidate);
    }

    @Override
    protected FileModel mutate() {
        if (selectedCandidate == null) {
            return null;
        }

        if (hasDeclarationConflict) {
            System.out.println("Skipping mutation because variable already exists in the same scope.");
            return null;
        }

        if (selectedCandidate.getStatement().getStartLine() != selectedCandidate.getStatement().getEndLine()) {
            System.out.println("Multiline statements are not supported for expression split mutation.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        int lineIndex = selectedCandidate.getStatement().getStartLine() - 1;
        String sourceLine = newLines.get(lineIndex);
        String indentation = leadingWhitespace(sourceLine);

        String rewrittenOriginalStatement = replaceExpressionWithTempVariable(
                sourceLine,
                selectedCandidate,
                newVariableName
        );

        BinaryOperationArgument left = selectedCandidate.getLeftArgument();
        BinaryOperationArgument right = selectedCandidate.getRightArgument();
        String operator = selectedCandidate.getOperator();

        String expressionType = selectedCandidate.getExpressionType();
        if (expressionType == null || expressionType.isBlank()) {
            System.out.println("Expression type is unknown; skipping expression split mutation.");
            return null;
        }

        String declarationLine = indentation + expressionType + " " + newVariableName + " = " + left.getCode() + ";";
        String accumulateLine = indentation + newVariableName + " " + operator + "= " + right.getCode() + ";";

        newLines.remove(lineIndex);
        newLines.add(lineIndex, rewrittenOriginalStatement);
        newLines.add(lineIndex, accumulateLine);
        newLines.add(lineIndex, declarationLine);

        String newName = buildNewName(originalFile.getFileName(), "_expr_split");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Expression Split Mutation";
    }

    private boolean isSupportedCandidate(ExpressionSplitCandidate candidate) {
        if (candidate == null) {
            return false;
        }

        if (!SUPPORTED_OPERATORS.contains(candidate.getOperator())) {
            return false;
        }

        if (candidate.getStatement() == null
                || candidate.getStatement().getCode() == null
                || candidate.getStatement().getCode().isBlank()) {
            return false;
        }

        if (candidate.getLeftArgument() == null || candidate.getRightArgument() == null) {
            return false;
        }

        if (candidate.getLeftArgument().getCode() == null || candidate.getLeftArgument().getCode().isBlank()) {
            return false;
        }

        if (candidate.getRightArgument().getCode() == null || candidate.getRightArgument().getCode().isBlank()) {
            return false;
        }

        String stmt = candidate.getStatement().getCode().trim();

        return stmt.startsWith("return ")
                || stmt.contains("=")
                || stmt.matches("[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*\\)\\s*;");
    }

    private String replaceExpressionWithTempVariable(String sourceLine,
                                                     ExpressionSplitCandidate candidate,
                                                     String tempVariableName) {
        int startColumnIndex = candidate.getExpression().getStartColumn() - 1;
        int endColumnIndex = candidate.getExpression().getEndColumn() - 1;

        if (startColumnIndex < 0 || endColumnIndex < startColumnIndex || endColumnIndex > sourceLine.length()) {
            throw new IllegalArgumentException("Expression bounds are invalid for source line: " + sourceLine);
        }

        return sourceLine.substring(0, startColumnIndex)
                + tempVariableName
                + sourceLine.substring(endColumnIndex);
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }
}