package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.ForElementNode;
import org.example.model.WhileLoopNode;
import org.example.model.WhileLoopParts;
import org.example.neo4j.WhileLoopRepository;

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
                ? condition.getCode()
                : "true";

        String forHeader = "for (; " + conditionCode + ";)";

        // Определяем конец старого заголовка while — это начало тела
        int oldHeaderEndColumn;
        if (loop.getStartLine() == body.getStartLine()) {
            oldHeaderEndColumn = body.getStartColumn() - 1;
        } else {
            oldHeaderEndColumn = newLines.get(loop.getStartLine() - 1).length() + 1;
        }

        applyEdit(newLines, new TextEdit(
                loop.getStartLine(),
                loop.getStartColumn(),
                loop.getStartLine(),
                oldHeaderEndColumn,
                forHeader
        ));

        String newName = buildNewName(originalFile.getFileName());
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "While To For Replacement";
    }

    private void applyEdit(List<String> lines, TextEdit edit) {
        int startLineIndex = edit.getStartLine() - 1;
        int startColumnIndex = edit.getStartColumn() - 1;
        int endLineIndex = edit.getEndLine() - 1;
        int endColumnIndex = edit.getEndColumn() - 1;

        if (startLineIndex == endLineIndex) {
            String line = lines.get(startLineIndex);
            String updated = line.substring(0, startColumnIndex)
                    + edit.getReplacement()
                    + line.substring(endColumnIndex);
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
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
            this.replacement = replacement;
        }

        public int getStartLine() { return startLine; }
        public int getStartColumn() { return startColumn; }
        public int getEndLine() { return endLine; }
        public int getEndColumn() { return endColumn; }
        public String getReplacement() { return replacement; }
    }
}
