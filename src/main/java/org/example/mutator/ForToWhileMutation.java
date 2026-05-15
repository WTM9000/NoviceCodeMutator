package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.ForElementNode;
import org.example.model.ForLoopNode;
import org.example.model.ForLoopParts;
import org.example.neo4j.repository.ForLoopRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ForToWhileMutation extends MutationOperator {

    private ForLoopParts loopParts;
    private boolean hasDeclarationConflict = false;

    private final Random random = new Random();
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

        ForLoopNode targetLoop = loops.get(random.nextInt(loops.size()));

        loopParts = repository.findForLoopPartsById(targetLoop.getId());

        if (loopParts == null) {
            System.out.println("Couldnt get loop parts.");
            return;
        }

        ForElementNode initializer = loopParts.getInitializerStatement();
        if (initializer != null && initializer.getCode() != null && !initializer.getCode().isBlank()) {
            initializerVariableName = extractDeclaredVariableName(initializer.getCode());

            if (initializerVariableName != null && !initializerVariableName.isBlank() && loopParts.isInitializerDeclaration()) {
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

        ForLoopNode    loop        = loopParts.getLoop();
        ForElementNode initializer = loopParts.getInitializerStatement();
        ForElementNode condition   = loopParts.getCondition();
        ForElementNode iteration   = loopParts.getIterationStatement();
        ForElementNode body        = loopParts.getBody();

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

        String bodyCode        = safeCode(body);
        String iterationCode   = safeCode(iteration);
        String initializerCode = safeCode(initializer);

        boolean bodyLooksLikeBlock = bodyCode.stripLeading().startsWith("{");

        String newBodyText;
        if (bodyLooksLikeBlock) {
            String trimmed     = bodyCode.trim();
            String indentation = (loop.getStartLine() == body.getStartLine())
                    ? defaultLoopIndentation()
                    : indentationOf(body);
            if (trimmed.equals("{")) {
                newBodyText = "{\n" + indentation + "    " + iterationCode + ";\n" + indentation + "}";
            } else {
                int    insertPos     = trimmed.lastIndexOf('}');
                String prefix        = trimmed.substring(0, insertPos).stripTrailing();
                String suffix        = trimmed.substring(insertPos);
                String iterationStmt = iterationCode.isBlank()
                        ? ""
                        : "\n" + indentation + ensureEndsWithSemicolon(iterationCode);
                newBodyText = prefix + iterationStmt + "\n" + indentation + suffix;
            }
        } else {
            String        originalStmt = bodyCode.trim();
            StringBuilder sb           = new StringBuilder();
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

        // Конец старого заголовка for — ровно перед началом тела
        int oldHeaderEndColumn;
        if (loop.getStartLine() == body.getStartLine()) {
            oldHeaderEndColumn = body.getStartColumn() - 1;
        } else {
            oldHeaderEndColumn = originalFile.getLines().get(loop.getStartLine() - 1).length() + 1;
        }

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

        edits.sort(Comparator
                .comparingInt(TextEdit::getStartLine).reversed()
                .thenComparing(Comparator.comparingInt(TextEdit::getStartColumn).reversed()));

        for (TextEdit edit : edits) {
            applyEdit(newLines, edit);
        }

        if (initializer != null && initializer.getCode() != null
                && !initializer.getCode().isBlank()
                && !Objects.equals(initializer.getCode(), ";")) {

            String initText   = initializerCode.trim();
            String insertText = ensureEndsWithSemicolon(initText);
            String padding    = "%1$" + (loop.getStartColumn() - 1 + insertText.length()) + "s";
            insertText = String.format(padding, insertText);

            applyLineEdit(newLines, insertText, loop.getStartLine());
        }

        String newName = buildNewName(originalFile.getFileName(), "_for_to_while");
        Path   newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "For To While Replacement";
    }

    /**
     * Применяет замену диапазона (startLine, startColumn) — (endLine, endColumn)
     * строкой replacement.
     *
     * Если replacement содержит '\n' — результирующая строка разбивается
     * по переносам и каждый фрагмент добавляется как отдельный элемент списка,
     * сохраняя соответствие номеров строк в FileModel.
     */
    private void applyEdit(List<String> lines, TextEdit edit) {
        int startLineIdx = edit.getStartLine() - 1;
        int startColIdx  = edit.getStartColumn() - 1;
        int endLineIdx   = edit.getEndLine() - 1;
        int endColIdx    = edit.getEndColumn() - 1;

        if (startLineIdx < 0 || startLineIdx >= lines.size()
                || endLineIdx < 0 || endLineIdx >= lines.size()) {
            throw new IllegalArgumentException("Edit out of file bounds: " + edit);
        }

        String firstLine = lines.get(startLineIdx);
        String lastLine  = lines.get(endLineIdx);

        String prefix  = firstLine.substring(0, startColIdx);
        String suffix  = startLineIdx == endLineIdx
                ? firstLine.substring(endColIdx)
                : lastLine.substring(endColIdx);

        // Удаляем все строки диапазона снизу вверх
        for (int i = endLineIdx; i >= startLineIdx; i--) {
            lines.remove(i);
        }

        // Собираем итоговый текст и разбиваем по '\n'
        String full = prefix + edit.getReplacement() + suffix;
        List<String> replacementLines = splitByNewline(full);

        // Вставляем строки на место удалённых (снизу вверх для корректных индексов)
        for (int i = replacementLines.size() - 1; i >= 0; i--) {
            lines.add(startLineIdx, replacementLines.get(i));
        }
    }

    /**
     * Вставляет новую строку перед строкой с номером startId (1-based).
     * Если newString содержит '\n' — каждый фрагмент вставляется отдельно.
     */
    private void applyLineEdit(List<String> lines, String newString, int startId) {
        int startLineIdx = startId - 1;

        if (startLineIdx < 0 || startLineIdx >= lines.size()) {
            throw new IllegalArgumentException("Edit out of file bounds: " + newString);
        }

        if (newString == null) {
            return;
        }

        List<String> parts = splitByNewline(newString);

        // Вставляем снизу вверх, чтобы порядок частей был правильным
        for (int i = parts.size() - 1; i >= 0; i--) {
            lines.add(startLineIdx, parts.get(i));
        }
    }

    /**
     * Разбивает строку по символу '\n'.
     * В отличие от String.split(), не теряет trailing-пустые строки.
     */
    private List<String> splitByNewline(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                result.add(text.substring(start, i));
                start = i + 1;
            }
        }
        result.add(text.substring(start));
        return result;
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

    private String defaultLoopIndentation() {
        return " ".repeat(loopParts.getLoop().getStartColumn() + 4);
    }

    private String extractDeclaredVariableName(String initializerCode) {
        if (initializerCode == null) {
            return null;
        }

        String normalized = initializerCode.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        int    eqIndex  = normalized.indexOf('=');
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
        private final int    startLine;
        private final int    startColumn;
        private final int    endLine;
        private final int    endColumn;
        private final String replacement;

        public TextEdit(int startLine, int startColumn, int endLine, int endColumn, String replacement) {
            this.startLine   = startLine;
            this.startColumn = startColumn;
            this.endLine     = endLine;
            this.endColumn   = endColumn;
            this.replacement = replacement;
        }

        public int    getStartLine()      { return startLine; }
        public int    getStartColumn()    { return startColumn; }
        public int    getEndLine()        { return endLine; }
        public int    getEndColumn()      { return endColumn; }
        public String getReplacement()    { return replacement; }

        @Override
        public String toString() {
            return "TextEdit{"
                    + "startLine=" + startLine
                    + ", startColumn=" + startColumn
                    + ", endLine=" + endLine
                    + ", endColumn=" + endColumn
                    + ", replacement='" + replacement + '\''
                    + '}';
        }
    }
}