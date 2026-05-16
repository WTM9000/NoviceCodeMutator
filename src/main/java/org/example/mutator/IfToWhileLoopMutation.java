package org.example.mutation;

import org.example.model.FileModel;
import org.example.model.IfRootNode;
import org.example.model.WhileLoopCandidate;
import org.example.mutator.MutationOperator;
import org.example.neo4j.repository.IfToWhileLoopRepository;
import org.example.neo4j.repository.NodeRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mutation 3.2b — Wrap If+Return in a pseudo-branch while loop.
 *
 * Before:
 *   if (y1 && y2) <body>;
 *   return <expr>;
 *
 * After:
 *   while (y1) {
 *       if (y2) <body>;
 *       return <expr>;
 *   }
 *   return <expr>;
 */
public class IfToWhileLoopMutation extends MutationOperator {

    private WhileLoopCandidate candidate;

    private final Random random = new Random();

    public IfToWhileLoopMutation(FileModel originalFile, NodeRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        IfToWhileLoopRepository repository = (IfToWhileLoopRepository) this.repo;

        List<WhileLoopCandidate> candidates = repository.findAllCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No candidate if-statements found.");
            return;
        }

        candidate = candidates.get(random.nextInt(candidates.size()));
    }

    @Override
    protected FileModel mutate() {
        if (candidate == null) return null;

        int ifStart  = candidate.getIfStatement().getStartLine();
        int ifEnd    = candidate.getIfStatement().getEndLine();
        int retEnd   = candidate.getReturnStatement().getEndLine();

        List<String> lines    = new ArrayList<>(originalFile.getLines());
        String ifLine         = lines.get(ifStart - 1);
        String indent         = leadingWhitespace(ifLine);
        String innerIndent    = indent + "    ";

        // Build the new if-line with the narrowed inner condition.
        String narrowedIf     = buildNarrowedIf(ifLine, candidate.getInnerIfCondition());

        // Lines to insert in place of the original if+return block:
        // while (y1) {
        //     if (y2) <body>;
        //     return <expr>;
        // }
        // return <expr>;
        List<String> replacement = new ArrayList<>();
        replacement.add(indent + "while (" + candidate.getWhileCondition() + ") {");
        replacement.add(innerIndent + narrowedIf.stripLeading());

        // Include any continuation lines of a multi-line if body (ifStart..ifEnd).
        for (int i = ifStart; i < ifEnd; i++) {  // ifStart is 1-indexed; i=ifStart covers line ifStart+1
            replacement.add(innerIndent + lines.get(i).stripLeading());
        }

        // Move the return inside the while block.
        for (int i = ifEnd; i < retEnd; i++) {
            replacement.add(innerIndent + lines.get(i).stripLeading());
        }
        replacement.add(innerIndent + lines.get(retEnd - 1).stripLeading());  // return line
        replacement.add(indent + "}");

        // Duplicate return after the closing brace.
        replacement.add(indent + lines.get(retEnd - 1).stripLeading());

        // Replace lines [ifStart-1 .. retEnd-1] with the replacement block.
        int deleteFrom = ifStart - 1;
        int deleteTo   = retEnd;   // exclusive
        for (int i = deleteTo - 1; i >= deleteFrom; i--) {
            lines.remove(i);
        }
        lines.addAll(deleteFrom, replacement);

        String newName = buildNewName(originalFile.getFileName(), "_while_loop");
        Path newPath   = originalFile.getFilePath().getParent().resolve(newName);
        return new FileModel(newName, newPath, lines);
    }



    @Override
    public String getMutationName() {
        return "If to While Mutation";
    }

    /**
     * Replaces the full "y1 && y2" condition in the original if-line
     * with just the inner (argument) condition "y2".
     */
    private String buildNarrowedIf(String originalIfLine, String innerCond) {
        int open = originalIfLine.indexOf('(');
        if (open < 0) return originalIfLine;
        int depth = 0;
        for (int i = open; i < originalIfLine.length(); i++) {
            char c = originalIfLine.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return originalIfLine.substring(0, open + 1)
                            + innerCond
                            + originalIfLine.substring(i);
                }
            }
        }
        return originalIfLine;
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }
}