package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.ForElementNode;
import org.example.model.ForLoopNode;
import org.example.model.ForLoopParts;
import org.example.neo4j.ForLoopRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ForToWhileMutation extends MutationOperator {

    private ForLoopParts loopParts;
    private boolean hasDeclarationConflict = false;
    private String initializerVariableName;

    public ForToWhileMutation(FileModel originalFile, ForLoopRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        ForLoopRepository repository = (ForLoopRepository) this.repo;

        List<ForLoopNode> loops = repository.findAllForLoops();

        if (loops.isEmpty()) {
            System.out.println("No for-loops found!");
            return;
        }

        System.out.println("Found for-loops: " + loops.size());
        for (ForLoopNode loop : loops) {
            System.out.println(loop);
        }

        ForLoopNode targetLoop;
        if (loops.size() > 3) {
            targetLoop = loops.get(2);
        } else {
            targetLoop = loops.get(0);
        }

        loopParts = repository.findForLoopPartsById(targetLoop.getId());

        if (loopParts == null) {
            System.out.println("Couldnt get loop parts.");
            return;
        }

        ForElementNode initializer = loopParts.getInitializerStatement();
        if (initializer != null && initializer.getCode() != null && !initializer.getCode().isBlank()) {
            initializerVariableName = extractDeclaredVariableName(initializer.getCode());

            if (initializerVariableName != null && !initializerVariableName.isBlank()) {
                hasDeclarationConflict = repository.hasInitializerDeclarationConflict(
                        loopParts.getLoop().getId(),
                        initializerVariableName
                );

                if (hasDeclarationConflict) {
                    System.out.println("Declaration conflict detected for variable: " + initializerVariableName);
                    return;
                }
            }
        }

        System.out.println("Picked loop: " + loopParts.getLoop());
        System.out.println("Initializer: " + loopParts.getInitializerStatement());
        System.out.println("Condition: " + loopParts.getCondition());
        System.out.println("Iteration: " + loopParts.getIterationStatement());
        System.out.println("Body: " + loopParts.getBody());
    }

    @Override
    protected FileModel mutate() {
        if (loopParts == null || loopParts.getLoop() == null) {
            return null;
        }

        if (hasDeclarationConflict) {
            System.out.println("Skipping mutation because initializer declaration conflicts with an earlier declaration.");
            return null;
        }

        ForLoopNode loop = loopParts.getLoop();
        ForElementNode initializer = loopParts.getInitializerStatement();
        ForElementNode condition = loopParts.getCondition();
        ForElementNode iteration = loopParts.getIterationStatement();
        ForElementNode body = loopParts.getBody();

        if (body == null) {
            System.out.println("Body node necessary for mutation for->while.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        List<TextEdit> edits = new ArrayList<>();

        String whileCondition = (condition != null && condition.getCode() != null && !condition.getCode().isBlank())
                ? condition.getCode()
                : "true";

        String whileHeader = "while (" + whileCondition + ")";

        String bodyCode = safeCode(body);
        String iterationCode = safeCode(iteration);
        String initializerCode = safeCode(initializer);

        boolean bodyLooksLikeBlock = bodyCode.stripLeading().startsWith("{");

        String newBodyText;
        if (bodyLooksLikeBlock) {
            String trimmed = bodyCode.trim();
            if (trimmed.equals("{")) {
                newBodyText = "{\n" + indentationOf(body) + "    " + iterationCode + ";\n" + indentationOf(body) + "}";
            } else {
                int insertPos = trimmed.lastIndexOf('}');
                String prefix = trimmed.substring(0, insertPos).stripTrailing();
                String suffix = trimmed.substring(insertPos);
                String iterationStmt = iterationCode.isBlank() ? "" : "\n" + indentationOf(body) + "    " + ensureEndsWithSemicolon(iterationCode);
                newBodyText = prefix + iterationStmt + "\n" + indentationOf(body) + suffix;
            }
        } else {
            String originalStmt = bodyCode.trim();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append(indentationOf(body)).append("    ").append(originalStmt);
            if (!originalStmt.endsWith(";")) {
                sb.append(";");
            }
            if (!iterationCode.isBlank()) {
                sb.append("\n").append(indentationOf(body)).append("    ").append(ensureEndsWithSemicolon(iterationCode));
            }
            sb.append("\n").append(indentationOf(body)).append("}");
            newBodyText = sb.toString();
        }

        // Find where old header ends:
        int oldHeaderEndColumn;
        if (loop.getStartLine() == body.getStartLine()){
            oldHeaderEndColumn = body.getStartColumn()-1;
        } else oldHeaderEndColumn = originalFile.getLines().get(loop.getStartLine()-1).length()+1;

        edits.add(new TextEdit(
                body.getStartLine(),
                body.getStartColumn(),
                body.getEndLine(),
                body.getEndColumn(),
                newBodyText
        ));

        edits.add(new TextEdit(
                loop.getStartLine(),
                loop.getStartColumn(),
                loop.getStartLine(),
                oldHeaderEndColumn,
                whileHeader
        ));


        int i = 0;

        edits.sort(Comparator
                .comparingInt(TextEdit::getStartLine).reversed()
                .thenComparing(Comparator.comparingInt(TextEdit::getStartColumn).reversed()));

        // Apply in-string edits
        for (TextEdit edit : edits) {
            applyEdit(newLines, edit);
        }

        // Insert/delete strings
        if (initializer != null && initializer.getCode() != null && !initializer.getCode().isBlank()) {
            String initText = initializer.getCode().trim();

            String insertText = ensureEndsWithSemicolon(initText);
            String padding = "%1$" + (loop.getStartColumn()-1 + insertText.length()) + "s" ;

            initText = String.format(padding, insertText);

            applyLineEdit(newLines, initText, loop.getStartLine());
        }

        String newName = buildNewName(originalFile.getFileName());
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "For To While Replacement";
    }

    private void applyEdit(List<String> lines, TextEdit edit) {
        int startLineIndex = edit.getStartLine() - 1;
        int startColumnIndex = edit.getStartColumn() - 1;
        int endLineIndex = edit.getEndLine() - 1;
        int endColumnIndex = edit.getEndColumn() - 1;

        if (startLineIndex < 0 || startLineIndex >= lines.size() || endLineIndex < 0 || endLineIndex >= lines.size()) {
            throw new IllegalArgumentException("Edit out of file bounds: " + edit);
        }

        if (startLineIndex == endLineIndex) {
            String line = lines.get(startLineIndex);
            String updated = line.substring(0, startColumnIndex) + edit.getReplacement() + line.substring(endColumnIndex);

            lines.set(startLineIndex, updated);
            return;
        }

        String firstLine = lines.get(startLineIndex);
        String lastLine = lines.get(endLineIndex);

        String prefix = firstLine.substring(0, startColumnIndex);
        String suffix = lastLine.substring(endColumnIndex);

        for (int i = endLineIndex; i > startLineIndex; i--) {
            lines.remove(i);
        }

        lines.set(startLineIndex, prefix + edit.getReplacement() + suffix);
    }

    private void applyLineEdit(List<String> lines, String newString, int startId){
        int startLineId = startId - 1;

        if (startLineId < 0 || startLineId >= lines.size()) {
            throw new IllegalArgumentException("Edit out of file bounds: " + newString);
        }

        if (newString == null){
            return;
        }

        lines.add(startLineId, newString);
    }

    private String safeCode(ForElementNode node) {
        return node == null || node.getCode() == null ? "" : node.getCode();
    }

    private String ensureEndsWithSemicolon(String code) {
        String trimmed = code.trim();
        if (trimmed.isEmpty() || trimmed.endsWith(";")) {
            return trimmed;
        }
        return trimmed + ";";
    }

    private String indentationOf(ForElementNode node) {
        if (node == null || node.getStartColumn() <= 1) {
            return "";
        }
        return " ".repeat(node.getStartColumn() - 1);
    }

    private String buildNewName(String oldName) {
        String filename = oldName;
        int lastDot = filename.lastIndexOf('.');

        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern("ss_mm_HH"));
        String date = now.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
        String suffix = "_" + date + "_" + time + "_for_to_while";

        if (lastDot > 0) {
            String base = filename.substring(0, lastDot);
            String ext = filename.substring(lastDot);
            return base + suffix + ext;
        }

        return filename + suffix;
    }

    private String extractDeclaredVariableName(String initializerCode) {
        if (initializerCode == null) {
            return null;
        }

        String normalized = initializerCode.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        int eqIndex = normalized.indexOf('=');
        String leftPart = eqIndex >= 0 ? normalized.substring(0, eqIndex).trim() : normalized;

        if (leftPart.isEmpty()) {
            return null;
        }

        leftPart = leftPart.replace("*", " ").replace("&", " ").trim();
        String[] parts = leftPart.split("\\s+");

        if (parts.length == 0) {
            return null;
        }

        return parts[parts.length - 1].trim();
    }

    private static class TextEdit {
        private final int startLine;
        private final int startColumn;
        private final int endLine;
        private final int endColumn;
        private final String replacement;

        public TextEdit(int startLine, int startColumn, int endLine, int endColumn, String replacement) {
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
            this.replacement = replacement;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getStartColumn() {
            return startColumn;
        }

        public int getEndLine() {
            return endLine;
        }

        public int getEndColumn() {
            return endColumn;
        }

        public String getReplacement() {
            return replacement;
        }

        @Override
        public String toString() {
            return "TextEdit{" +
                    "startLine=" + startLine +
                    ", startColumn=" + startColumn +
                    ", endLine=" + endLine +
                    ", endColumn=" + endColumn +
                    ", replacement='" + replacement + '\'' +
                    '}';
        }
    }
}
