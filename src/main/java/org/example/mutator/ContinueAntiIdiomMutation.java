package org.example.mutator;

import org.example.model.ContinueAntiIdiomCandidate;
import org.example.model.FileModel;
import org.example.neo4j.repository.ContinueAntiIdiomRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ContinueAntiIdiomMutation extends MutationOperator {

    // Catalog of neutral dead-code expressions (from the approved list)
    private static final List<String> DEAD_CODE_EXPRESSIONS = List.of(
            "0",
            "sizeof(int)",
            "(2 - 2) * 10",
            "(3 & 5) | 0",
            "(5 >= 5) || (0 > 1)",
            "(1 < 2) && (3 > 1)",
            "0 == 0",
            "1 != 1",
            "(1 && 0) || (0 && 1)"
    );

    private final Random random = new Random();
    private final boolean addDeadCode;

    private ContinueAntiIdiomCandidate selectedCandidate;

    public ContinueAntiIdiomMutation(FileModel originalFile,
                                     ContinueAntiIdiomRepository repo,
                                     boolean addDeadCode) {
        super(originalFile, repo);
        this.addDeadCode = addDeadCode;
    }

    @Override
    protected void getRelevantNodes() {
        ContinueAntiIdiomRepository repository = (ContinueAntiIdiomRepository) this.repo;
        List<ContinueAntiIdiomCandidate> candidates = repository.findCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No continue anti-idiom candidates found.");
            return;
        }

        selectedCandidate = candidates.get(random.nextInt(candidates.size()));
        System.out.println("Selected candidate: " + selectedCandidate);
    }

    @Override
    protected FileModel mutate() {
        if (selectedCandidate == null) {
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        int startLineIndex = selectedCandidate.getIfNode().getStartLine() - 1;
        int endLineIndex   = selectedCandidate.getIfNode().getEndLine() - 1;

        if (startLineIndex < 0 || endLineIndex >= newLines.size() || startLineIndex > endLineIndex) {
            return null;
        }

        String indent = leadingWhitespace(newLines.get(startLineIndex));

        // Top-level call: isNested=false — no continue after the then-body of the outermost if
        List<String> replacement = buildReplacement(indent, selectedCandidate, false);

        // Remove original lines back-to-front to preserve indices
        for (int i = endLineIndex; i >= startLineIndex; i--) {
            newLines.remove(i);
        }

        // Insert replacement lines one by one back-to-front to preserve order
        for (int i = replacement.size() - 1; i >= 0; i--) {
            newLines.add(startLineIndex, replacement.get(i));
        }

        // Append dead code after the mutation if the flag is set
        if (addDeadCode) {
            int deadCodeInsertIndex = startLineIndex + replacement.size();
            List<String> deadLines = buildDeadCodeLines(indent, selectedCandidate.getGuardCode());
            for (int i = deadLines.size() - 1; i >= 0; i--) {
                newLines.add(deadCodeInsertIndex, deadLines.get(i));
            }
        }

        String newName = buildNewName(originalFile.getFileName(), "_continue_anti_idiom");
        Path newPath   = originalFile.getFilePath().getParent().resolve(newName);
        return new FileModel(newName, newPath, newLines);
    }

    /**
     * Recursively builds replacement lines for the candidate chain.
     *
     * isNested=true  — this branch is inside an outer {}-block (i.e. it is an else-if node).
     *                  A "continue" must be appended after the then-body.
     * isNested=false — this is the outermost if. No continue after the then-body;
     *                  execution simply falls through to the next statement in the loop body.
     *
     * Structure produced (matches the spec from the modification description):
     *
     *   if (!y2) {
     *       if (!y3) {        <- isNested=true call for elseIfBranch
     *           zzz;
     *           continue;     <- added because isNested=true for innermost
     *       }
     *       yyy;
     *       continue;         <- added because isNested=true for this level
     *   }
     *   xxx;                  <- then-body of outermost, no continue (isNested=false)
     */
    private List<String> buildReplacement(String indent,
                                          ContinueAntiIdiomCandidate candidate,
                                          boolean isNested) {
        List<String> lines = new ArrayList<>();
        String negatedGuard = negateGuard(candidate.getGuardCode());

        if (candidate.getElseIfBranch() != null) {
            // --- Case: else-if chain ---
            // Open block for the negated guard
            lines.add(indent + "if (" + negatedGuard + ") {");

            // Recurse into the next else-if link; it is nested, so it gets continue
            List<String> innerLines = buildReplacement(
                    indent + "    ", candidate.getElseIfBranch(), true);
            lines.addAll(innerLines);

            lines.add(indent + "}");

            // Then-body of the current level
            appendBodyLines(lines, candidate.getThenNode().getCode(), indent);

            // FIX BUG 1: if this level is itself nested, emit continue after the then-body
            if (isNested) {
                lines.add(indent + "continue;");
            }

        } else if (!candidate.hasElse() || candidate.isTrivialElse()) {
            // --- Case: no else, or trivially ignorable else ---
            lines.add(indent + "if (" + negatedGuard + ")");
            lines.add(indent + "    continue;");
            appendBodyLines(lines, candidate.getThenNode().getCode(), indent);

            // If nested, continue after the then-body as well
            if (isNested) {
                lines.add(indent + "continue;");
            }

        } else {
            // --- Case: non-trivial plain else ---
            lines.add(indent + "if (" + negatedGuard + ") {");
            appendIndentedBody(lines, candidate.getElseNode().getCode(), indent + "    ");
            lines.add(indent + "    continue;");
            lines.add(indent + "}");
            appendBodyLines(lines, candidate.getThenNode().getCode(), indent);

            if (isNested) {
                lines.add(indent + "continue;");
            }
        }

        return lines;
    }

    /**
     * Builds the two dead-code lines appended after the main mutation:
     *   if (!firstGuard) continue;   // unreachable: guard was already inverted above
     *   <random neutral expression>; // unreachable: after the continue above
     *
     * FIX BUG 2: negateGuard() is explicitly applied to firstGuardCode here,
     * producing "if (!y2) continue;" as specified.
     */
    private List<String> buildDeadCodeLines(String indent, String firstGuardCode) {
        List<String> lines = new ArrayList<>();
        // Negate the original guard — this is the dead-code condition
        lines.add(indent + "if (" + firstGuardCode + ") continue;");
        String neutralExpr = DEAD_CODE_EXPRESSIONS.get(random.nextInt(DEAD_CODE_EXPRESSIONS.size()));
        lines.add(indent + neutralExpr + ";");
        return lines;
    }

    /**
     * Negates a guard expression.
     * "!expr" -> "expr"  (strips one layer of negation)
     * "expr"  -> "!(expr)"
     */
    private String negateGuard(String guardCode) {
        String trimmed = guardCode.trim();
        if (trimmed.startsWith("!")) {
            return trimmed.substring(1).trim();
        }
        return "!(" + trimmed + ")";
    }

    /**
     * Appends body lines at the given indent level.
     * Strips outer braces if the body is a block; emits single-statement body as-is.
     */
    private void appendBodyLines(List<String> target, String bodyCode, String indent) {
        if (bodyCode == null || bodyCode.isBlank()) {
            return;
        }

        String trimmed = bodyCode.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isBlank()) {
                return;
            }
            for (String line : inner.split("\\R")) {
                target.add(indent + line.stripLeading());
            }
            return;
        }

        target.add(indent + trimmed);
    }

    /**
     * Same as appendBodyLines but always applies the given indent
     * (used for else-body placed inside an extra level of braces).
     */
    private void appendIndentedBody(List<String> target, String bodyCode, String indent) {
        if (bodyCode == null || bodyCode.isBlank()) {
            return;
        }

        String trimmed = bodyCode.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isBlank()) {
                return;
            }
            for (String line : inner.split("\\R")) {
                target.add(indent + line.stripLeading());
            }
            return;
        }

        target.add(indent + trimmed);
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
        return "Continue Anti-Idiom Mutation";
    }
}