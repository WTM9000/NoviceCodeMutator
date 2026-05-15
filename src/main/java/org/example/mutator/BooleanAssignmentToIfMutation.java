package org.example.mutator;

import org.example.model.BooleanAssignmentCandidate;
import org.example.model.FileModel;
import org.example.model.StatementNode;
import org.example.neo4j.repository.BooleanAssignmentRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BooleanAssignmentToIfMutation extends MutationOperator {

    private final Random random = new Random();

    private BooleanAssignmentCandidate selectedCandidate;

    public BooleanAssignmentToIfMutation(FileModel originalFile,
                                         BooleanAssignmentRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        BooleanAssignmentRepository repository = (BooleanAssignmentRepository) this.repo;

        List<BooleanAssignmentCandidate> candidates = new ArrayList<>();
        candidates.addAll(repository.findStandaloneAssignments());
        candidates.addAll(repository.findDeclarationAssignments());

        System.out.print("Found candidates: " + candidates.size() + "\n");

        if (candidates.isEmpty()) {
            System.out.println("No boolean assignment candidates found.");
            return;
        }

        List<BooleanAssignmentCandidate> filtered = new ArrayList<>();
        for (BooleanAssignmentCandidate candidate : candidates) {
            if (isSupportedCandidate(candidate)) {
                filtered.add(candidate);
            }
        }

        System.out.print("Found supported candidates: " + filtered.size() + "\n");

        if (filtered.isEmpty()) {
            System.out.println("No supported boolean assignment candidates found.");
            return;
        }

        System.out.println("Found boolean assignment candidates: " + filtered.size());
        for (BooleanAssignmentCandidate candidate : filtered) {
            System.out.println(candidate);
        }

        selectedCandidate = filtered.get(random.nextInt(filtered.size()));
        System.out.println("Selected: " + selectedCandidate);
    }

    @Override
    protected FileModel mutate() {
        if (selectedCandidate == null) {
            return null;
        }

        StatementNode stmt = selectedCandidate.getStatement();

//        if (stmt.getStartLine() != stmt.getEndLine()) {
//            System.out.println("Multiline statements are not supported for boolean assignment mutation.");
//            return null;
//        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        String lhs = selectedCandidate.getLeftArgument().getCode().trim();
        String rhs = selectedCandidate.getRightArgument().getCode().trim();

        if (selectedCandidate.isLeftDeclaration()) {
            return mutateDeclaration(newLines, lhs, rhs);
        }

        return mutateStandaloneAssignment(newLines, lhs, rhs);
    }

    private FileModel mutateStandaloneAssignment(List<String> newLines, String lhs, String rhs) {
        int lineIndex = selectedCandidate.getAssignmentExpression().getStartLine() - 1;
        String sourceLine = newLines.get(lineIndex);
        String indent = leadingWhitespace(sourceLine);

        List<String> replacement;

        if (selectedCandidate.isReturnContext()) {
            replacement = buildIfElseWithReturnLines(indent, lhs, rhs,
                    selectedCandidate.isOneLineDirectBody());
        } else {
            replacement = buildIfElseLines(indent, lhs, rhs,
                    selectedCandidate.isOneLineDirectBody());
        }

        newLines.remove(lineIndex);
        for (int i = replacement.size() - 1; i >= 0; i--) {
            newLines.add(lineIndex, replacement.get(i));
        }

        return buildResult(newLines);
    }

    private List<String> buildIfElseWithReturnLines(String indent,
                                                    String lhs,
                                                    String rhs,
                                                    boolean oneLineDirectBody) {
        List<String> lines = new ArrayList<>();
        if (oneLineDirectBody) {
            lines.add(indent + "{");
            lines.add(indent + "    if (" + rhs + ") " + lhs + " = 1;");
            lines.add(indent + "    else " + lhs + " = 0;");
            lines.add(indent + "    return " + lhs + ";");
            lines.add(indent + "}");
        } else {
            lines.add(indent + "if (" + rhs + ") " + lhs + " = 1;");
            lines.add(indent + "else " + lhs + " = 0;");
            lines.add(indent + "return " + lhs + ";");
        }
        return lines;
    }

    private List<String> buildIfElseLines(String indent,
                                          String lhs,
                                          String rhs,
                                          boolean oneLineDirectBody) {
        List<String> lines = new ArrayList<>();
        if (oneLineDirectBody) {
            lines.add(indent + "{");
            lines.add(indent + "    if (" + rhs + ") " + lhs + " = 1;");
            lines.add(indent + "    else " + lhs + " = 0;");
            lines.add(indent + "}");
        } else {
            lines.add(indent + "if (" + rhs + ") " + lhs + " = 1;");
            lines.add(indent + "else " + lhs + " = 0;");
        }
        return lines;
    }

    private FileModel mutateDeclaration(List<String> newLines, String lhs, String rhs) {
        StatementNode decl = selectedCandidate.getDeclarationStatement();
        if (decl == null) {
            System.out.println("Declaration statement is null; skipping.");
            return null;
        }

        int lineIndex = decl.getStartLine() - 1;
        String sourceLine = newLines.get(lineIndex);
        String indent = leadingWhitespace(sourceLine);

        String declarationPrefix = extractDeclarationPrefix(sourceLine, lhs);
        if (declarationPrefix == null) {
            System.out.println("Could not extract declaration prefix for line: " + sourceLine);
            return null;
        }

        List<String> replacement = new ArrayList<>();
        if (selectedCandidate.isOneLineDirectBody()) {
            replacement.add(indent + "{");
            replacement.add(indent + "    " + declarationPrefix.trim() + ";");
            replacement.add(indent + "    if (" + rhs + ") " + lhs + " = 1;");
            replacement.add(indent + "    else " + lhs + " = 0;");
            replacement.add(indent + "}");
        } else {
            replacement.add(indent + declarationPrefix.trim() + ";");
            replacement.add(indent + "if (" + rhs + ") " + lhs + " = 1;");
            replacement.add(indent + "else " + lhs + " = 0;");
        }

        newLines.remove(lineIndex);
        for (int i = replacement.size() - 1; i >= 0; i--) {
            newLines.add(lineIndex, replacement.get(i));
        }

        return buildResult(newLines);
    }

    // Убираем инициализатор: "    int f = cond" -> "    int f"
    private String extractDeclarationPrefix(String sourceLine, String variableName) {
        String trimmed = sourceLine.trim();
        int eqPos = trimmed.indexOf('=');
        if (eqPos <= 0) {
            return null;
        }

        String leftPart = trimmed.substring(0, eqPos).trim();
        if (!leftPart.endsWith(variableName.trim())) {
            return null;
        }

        return leftPart;
    }

    private FileModel buildResult(List<String> newLines) {
        String newName = buildNewName(originalFile.getFileName(), "_bool_assign_to_if");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);
        return new FileModel(newName, newPath, newLines);
    }

    private boolean isSupportedCandidate(BooleanAssignmentCandidate candidate) {
        if (candidate == null) {
            return false;
        }

        if (candidate.getStatement() == null
                || candidate.getStatement().getCode() == null
                || candidate.getStatement().getCode().isBlank()) {
            return false;
        }

        if (candidate.getLeftArgument() == null
                || candidate.getLeftArgument().getCode() == null
                || candidate.getLeftArgument().getCode().isBlank()) {
            return false;
        }

        if (candidate.getRightArgument() == null
                || candidate.getRightArgument().getCode() == null
                || candidate.getRightArgument().getCode().isBlank()) {
            return false;
        }

        return candidate.getAssignmentExpression().getStartLine() == candidate.getAssignmentExpression().getEndLine();
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    @Override
    public String getMutationName() {
        return "Boolean Assignment To If Mutation";
    }
}