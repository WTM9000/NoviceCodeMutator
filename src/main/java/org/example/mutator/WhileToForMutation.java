package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.ForElementNode;
import org.example.model.WhileLoopNode;
import org.example.model.WhileLoopParts;
import org.example.neo4j.repository.WhileLoopRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WhileToForMutation extends MutationOperator {

    private WhileLoopParts loopParts;
    private final Random random = new Random();

    public WhileToForMutation(FileModel originalFile, WhileLoopRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        WhileLoopRepository repository = (WhileLoopRepository) this.repo;

        List<WhileLoopNode> loops = repository.findAllWhileLoops();

        if (loops.isEmpty()) {
            System.out.println("No while-loops found!");
            return;
        }

        System.out.println("Found while-loops: " + loops.size());
        for (WhileLoopNode loop : loops) {
            System.out.println(loop);
        }

        WhileLoopNode targetLoop = loops.get(random.nextInt(loops.size()));
        loopParts = repository.findWhileLoopPartsById(targetLoop.getId());

        if (loopParts == null) {
            System.out.println("Couldn't get loop parts.");
            return;
        }

        System.out.println("Picked loop: " + loopParts.getLoop());
        System.out.println("Condition: " + loopParts.getCondition());
        System.out.println("Body: " + loopParts.getBody());
    }

    @Override
    protected FileModel mutate() {
        if (loopParts == null || loopParts.getLoop() == null) {
            return null;
        }

        WhileLoopNode loop = loopParts.getLoop();
        ForElementNode condition = loopParts.getCondition();
        ForElementNode body = loopParts.getBody();

        if (body == null) {
            System.out.println("Body node is necessary for while->for mutation.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        String conditionCode = (condition != null && condition.getCode() != null && !condition.getCode().isBlank())
                ? condition.getCode().strip()
                : "true";

        String forHeader = "for (; " + conditionCode + ";)";

        // Конец заголовка while — ровно одна позиция перед началом тела.
        // Это корректно работает и для однострочного, и для многострочного
        // условия: applyEdit удалит все строки от начала while до начала тела
        // и вставит вместо них одну строку с for-заголовком.
        int headerEndLine   = body.getStartLine();
        int headerEndColumn = body.getStartColumn() - 1;

        applyEdit(newLines, new TextEdit(
                loop.getStartLine(),
                loop.getStartColumn(),
                headerEndLine,
                headerEndColumn,
                forHeader + " "
        ));

        String newName = buildNewName(originalFile.getFileName());
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "While To For Replacement";
    }

    /**
     * Заменяет текст от (startLine, startColumn) до (endLine, endColumn)
     * строкой replacement.
     *
     * Если диапазон многострочный — все промежуточные строки удаляются,
     * prefix первой строки и suffix последней склеиваются с replacement.
     */
    private void applyEdit(List<String> lines, TextEdit edit) {
        int startLineIdx = edit.getStartLine() - 1;
        int startColIdx  = edit.getStartColumn() - 1;
        int endLineIdx   = edit.getEndLine() - 1;
        int endColIdx    = edit.getEndColumn() - 1;

        // Граничные случаи: защита от выхода за пределы строки
        String firstLine = lines.get(startLineIdx);
        String lastLine  = lines.get(endLineIdx);

        startColIdx = Math.max(0, Math.min(startColIdx, firstLine.length()));
        endColIdx   = Math.max(0, Math.min(endColIdx,   lastLine.length()));

        if (startLineIdx == endLineIdx) {
            String updated = firstLine.substring(0, startColIdx)
                    + edit.getReplacement()
                    + firstLine.substring(endColIdx);
            lines.set(startLineIdx, updated);
            return;
        }

        String prefix = firstLine.substring(0, startColIdx);
        String suffix = lastLine.substring(endColIdx);

        // Удаляем все строки диапазона снизу вверх, кроме первой
        for (int i = endLineIdx; i > startLineIdx; i--) {
            lines.remove(i);
        }

        lines.set(startLineIdx, prefix + edit.getReplacement() + suffix);
    }

    private String buildNewName(String oldName) {
        int lastDot = oldName.lastIndexOf('.');
        LocalDateTime now = LocalDateTime.now();
        String suffix = "_" + now.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"))
                + "_" + now.format(DateTimeFormatter.ofPattern("ss_mm_HH"))
                + "_while_to_for";

        if (lastDot > 0) {
            return oldName.substring(0, lastDot) + suffix + oldName.substring(lastDot);
        }
        return oldName + suffix;
    }

    private static class TextEdit {
        private final int startLine;
        private final int startColumn;
        private final int endLine;
        private final int endColumn;
        private final String replacement;

        public TextEdit(int startLine, int startColumn, int endLine, int endColumn, String replacement) {
            this.startLine   = startLine;
            this.startColumn = startColumn;
            this.endLine     = endLine;
            this.endColumn   = endColumn;
            this.replacement = replacement;
        }

        public int getStartLine()    { return startLine; }
        public int getStartColumn()  { return startColumn; }
        public int getEndLine()      { return endLine; }
        public int getEndColumn()    { return endColumn; }
        public String getReplacement() { return replacement; }
    }
}