package org.example.mutator;

import org.example.model.EmptyLoopCandidate;
import org.example.model.FileModel;
import org.example.neo4j.repository.EmptyLoopRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mutation 4 — Empty Loop.
 *
 * Inserts a dead while-loop after a randomly chosen statement in a function body:
 *
 *   while (<cond>) {
 *       if (<cond>) break;
 *   }
 *
 * The loop never executes meaningful work:
 *   - if cond is falsy  -> while body never entered.
 *   - if cond is truthy -> if-body executes immediately, break exits the loop.
 * Observable behaviour of the original program is unchanged.
 */
public class EmptyLoopMutation extends MutationOperator {

    private final Random random = new Random();
    private EmptyLoopCandidate selectedCandidate;

    public EmptyLoopMutation(FileModel originalFile, EmptyLoopRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        EmptyLoopRepository repository = (EmptyLoopRepository) this.repo;

        List<EmptyLoopCandidate> candidates = repository.findAllCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No empty-loop candidates found.");
            return;
        }

        System.out.println("Found empty-loop candidates: " + candidates.size());
        for (EmptyLoopCandidate c : candidates) {
            System.out.println(c);
        }

        selectedCandidate = candidates.get(random.nextInt(candidates.size()));
        System.out.println("Selected: " + selectedCandidate);
    }

    @Override
    protected FileModel mutate() {
        if (selectedCandidate == null) {
            return null;
        }

        Integer endLine = selectedCandidate.getStatement().getEndLine();
        if (endLine == null) {
            System.out.println("Statement has no endLine — skipping.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        String indent = leadingWhitespace(newLines.get(endLine - 1));
        String innerIndent = indent + "    ";
        String cond = selectedCandidate.getLoopCondition();

        // Build the four-line empty loop block.
        List<String> loopLines = List.of(
                indent + "while (" + cond + ") {",
                innerIndent + "if (" + cond + ") break;",
                indent + "}"
        );

        // Insert after the last line of the chosen statement (endLine is 1-based).
        int insertIndex = endLine;
        for (int i = loopLines.size() - 1; i >= 0; i--) {
            newLines.add(insertIndex, loopLines.get(i));
        }

        String newName = buildNewName(originalFile.getFileName(), "_empty_loop");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Empty Loop Mutation";
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }
}