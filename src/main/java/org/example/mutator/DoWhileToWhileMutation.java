package org.example.mutator;

import org.example.model.DoWhileLoopNode;
import org.example.model.DoWhileLoopParts;
import org.example.model.FileModel;
import org.example.model.ForElementNode;
import org.example.neo4j.repository.DoWhileLoopRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DoWhileToWhileMutation extends MutationOperator {

    private DoWhileLoopParts loopParts;
    private final Random random = new Random();

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

        // ── indentation of the do-while keyword itself (0-based column count) ──
        // loop.getStartColumn() is 1-based, so subtract 1.
        int loopIndentCount = Math.max(0, loop.getStartColumn() - 1);
        String loopIndent   = " ".repeat(loopIndentCount);

        // ── collect body lines (between the braces, exclusive) ────────────────
        // body.getStartLine() is the "{" line, body.getEndLine() is the "}" line.
        // Content lines are startLine+1 .. endLine-1 (1-based).
        int bodyStart = body.getStartLine();
        int bodyEnd   = body.getEndLine();

        // Determine how many leading spaces the FIRST content line has so we
        // can strip exactly that many spaces (= loop indent + one level added
        // by the block).  This is robust regardless of tab-size conventions.
        int innerIndentCount = 0;
        if (bodyStart + 1 <= bodyEnd - 1) {
            String firstBodyLine = newLines.get(bodyStart);   // 0-based: bodyStart+1-1
            while (innerIndentCount < firstBodyLine.length()
                    && firstBodyLine.charAt(innerIndentCount) == ' ') {
                innerIndentCount++;
            }
        }
        // How many spaces to strip = inner indent - loop indent.
        // This equals exactly one indent level added by the block.
        int stripCount = Math.max(0, innerIndentCount - loopIndentCount);

        List<String> bodyContentLines = new ArrayList<>();
        for (int lineNum = bodyStart + 1; lineNum <= bodyEnd - 1; lineNum++) {
            String raw = newLines.get(lineNum - 1);   // list is 0-based
            bodyContentLines.add(stripLeadingSpaces(raw, stripCount));
        }

        // ── condition string ──────────────────────────────────────────────────
        String condCode = (condition != null
                && condition.getCode() != null
                && !condition.getCode().isBlank())
                ? condition.getCode().strip()
                : "true";

        // ── build replacement lines for the while-loop ────────────────────────
        // Each line becomes a separate String element in the list — no embedded \n.
        List<String> whileLines = new ArrayList<>();
        whileLines.add(loopIndent + "while (" + condCode + ") {");
        for (String bodyLine : bodyContentLines) {
            // Body lines keep their original indent relative to the loop,
            // which is already correct since we only stripped the extra level.
            whileLines.add(loopIndent + bodyLine);
        }
        whileLines.add(loopIndent + "}");

        // ── step 1: replace the do-while range with the while-loop lines ──────
        // Remove old lines (endLine down to startLine, inclusive) and insert new ones.
        // Working bottom-up so indices don't shift during removal.
        int replaceStartIdx = loop.getStartLine() - 1;   // 0-based
        int replaceEndIdx   = loop.getEndLine()   - 1;   // 0-based

        for (int i = replaceEndIdx; i >= replaceStartIdx; i--) {
            newLines.remove(i);
        }
        // Insert while-loop lines at the same position, preserving order.
        newLines.addAll(replaceStartIdx, whileLines);

        // ── step 2: insert first-iteration copy BEFORE the while-loop ─────────
        // After step 1 the while-loop now starts at replaceStartIdx.
        // First-iteration lines get the same indent as the loop itself.
        List<String> firstIterLines = new ArrayList<>();
        for (String bodyLine : bodyContentLines) {
            firstIterLines.add(loopIndent + bodyLine);
        }

        // Insert before replaceStartIdx — indices are still valid because
        // we did the replacement first and are now inserting before it.
        newLines.addAll(replaceStartIdx, firstIterLines);

        // ── assemble result ────────────────────────────────────────────────────
        String newName = buildNewName(originalFile.getFileName(), "_do_while_to_while");
        Path   newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Do-While To While Replacement";
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Removes at most {@code count} leading space characters from {@code line}.
     * Never removes more than the actual number of leading spaces present.
     */
    private String stripLeadingSpaces(String line, int count) {
        if (line == null) return "";
        int remove = 0;
        while (remove < count && remove < line.length() && line.charAt(remove) == ' ') {
            remove++;
        }
        return line.substring(remove);
    }
}