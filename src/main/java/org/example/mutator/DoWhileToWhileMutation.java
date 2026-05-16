package org.example.mutator;

import org.example.model.DoWhileLoopNode;
import org.example.model.DoWhileLoopParts;
import org.example.model.FileModel;
import org.example.model.ForElementNode;
import org.example.neo4j.repository.DoWhileLoopRepository;

import java.nio.file.Path;
import java.util.*;

public class DoWhileToWhileMutation extends MutationOperator {

    private static final int INDENT_STEP = 4;

    private DoWhileLoopParts loopParts;
    private final Random     random = new Random();

    public DoWhileToWhileMutation(FileModel originalFile, DoWhileLoopRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    // ── candidate selection ───────────────────────────────────────────────────

    @Override
    protected void getRelevantNodes() {
        DoWhileLoopRepository repository = (DoWhileLoopRepository) this.repo;

        List<DoWhileLoopNode> loops = repository.findAllDoWhileLoops();

        if (loops.isEmpty()) {
            System.out.println("No do-while loops found.");
            return;
        }

        System.out.println("Found do-while loops: " + loops.size());
        loops.forEach(System.out::println);

        DoWhileLoopNode target = loops.get(random.nextInt(loops.size()));
        loopParts = repository.findDoWhilePartsById(target.getId());

        if (loopParts == null) {
            System.out.println("Could not fetch do-while parts.");
            return;
        }

        System.out.println("Selected loop : " + loopParts.getLoop());
        System.out.println("Condition     : " + loopParts.getCondition());
        System.out.println("Body          : " + loopParts.getBody());
    }

    // ── mutation ──────────────────────────────────────────────────────────────

    @Override
    protected FileModel mutate() {
        if (loopParts == null || loopParts.getLoop() == null) {
            return null;
        }

        DoWhileLoopNode loop      = loopParts.getLoop();
        ForElementNode  condition = loopParts.getCondition();
        ForElementNode  body      = loopParts.getBody();

        if (body == null) {
            System.out.println("Body node is required for DoWhileToWhile mutation.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        // ── step 1: collect body lines (inside the braces, dedented by one level) ──

        // Body Block spans startLine..endLine (1-based).
        // We copy lines startLine+1 .. endLine-1 (the content between the braces).
        int bodyContentStart = body.getStartLine();     // first line of block "{" line
        int bodyContentEnd   = body.getEndLine();       // last  line of block "}" line

        // Lines between the braces (exclusive of the brace lines themselves):
        List<String> bodyContent = new ArrayList<>();
        for (int lineNum = bodyContentStart + 1; lineNum < bodyContentEnd; lineNum++) {
            String raw = originalFile.getLines().get(lineNum - 1);
            bodyContent.add(dedentOnce(raw));
        }

        // ── step 2: build the replacement while-loop text ──────────────────────────

        String condCode = (condition != null && condition.getCode() != null
                && !condition.getCode().isBlank())
                ? condition.getCode()
                : "true";

        String loopIndent = " ".repeat(Math.max(0, loop.getStartColumn() - 1));

        StringBuilder whileLoop = new StringBuilder();
        whileLoop.append("while (").append(condCode).append(") {");

        for (String bodyLine : bodyContent) {
            whileLoop.append("\n").append(loopIndent).append(bodyLine);
        }
        whileLoop.append("\n").append(loopIndent).append("}");

        // ── step 3: replace the entire do-while range with the while-loop ─────────

        // The do-while node's own code spans loop.startLine .. loop.endLine.
        // We replace that whole range in newLines.
        replaceLineRange(newLines,
                loop.getStartLine(),
                loop.getEndLine(),
                whileLoop.toString());

        // ── step 4: insert the first-iteration copy before the loop ───────────────

        // After step 3 the while-loop starts at loop.getStartLine() (0-based: index startLine-1).
        // We insert the body lines BEFORE that position.
        List<String> firstIterLines = new ArrayList<>();
        for (String bodyLine : bodyContent) {
            firstIterLines.add(loopIndent + bodyLine);
        }

        int insertAt = loop.getStartLine() - 1;   // 0-based index
        newLines.addAll(insertAt, firstIterLines);

        // ── step 5: assemble result ────────────────────────────────────────────────

        String   newName = buildNewName(originalFile.getFileName(), "_do_while_to_while");
        Path     newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Do-While To While Replacement";
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Replaces lines [startLine..endLine] (1-based, inclusive) in the list
     * with the lines produced by splitting replacementText on '\n'.
     */
    private void replaceLineRange(List<String> lines,
                                  int startLine, int endLine,
                                  String replacementText) {
        int startIdx = startLine - 1;
        int endIdx   = endLine   - 1;

        for (int i = endIdx; i >= startIdx; i--) {
            lines.remove(i);
        }

        List<String> parts = splitByNewline(replacementText);
        for (int i = parts.size() - 1; i >= 0; i--) {
            lines.add(startIdx, parts.get(i));
        }
    }

    /**
     * Removes exactly one indent level (INDENT_STEP spaces) from the
     * beginning of the line, if present.
     */
    private String dedentOnce(String line) {
        if (line == null) {
            return "";
        }
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        int remove = Math.min(spaces, INDENT_STEP);
        return line.substring(remove);
    }

    /** Splits on '\n' without losing trailing empty strings. */
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
}