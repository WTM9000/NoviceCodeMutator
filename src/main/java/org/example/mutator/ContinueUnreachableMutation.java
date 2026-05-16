package org.example.mutator;

import org.example.model.ContinueUnreachableCandidate;
import org.example.model.FileModel;
import org.example.neo4j.repository.ContinueUnreachableRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mutation 5 — Unreachable Code After Continue.
 *
 * Inserts a continue statement followed by a dead expression at the end of a
 * randomly chosen loop body:
 *
 *   // before:
 *   while (y1) {
 *       if (y2) x = 0;
 *   }
 *
 *   // after:
 *   while (y1) {
 *       if (y2) x = 0;
 *       continue;
 *       x++;
 *   }
 *
 * The continue is inserted after the last statement of the loop body, making
 * everything that follows it unreachable dead code.
 */
public class ContinueUnreachableMutation extends MutationOperator {

    private final Random random = new Random();
    private ContinueUnreachableCandidate selectedCandidate;

    public ContinueUnreachableMutation(FileModel originalFile,
                                       ContinueUnreachableRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        ContinueUnreachableRepository repository = (ContinueUnreachableRepository) this.repo;

        List<ContinueUnreachableCandidate> candidates = repository.findAllCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No continue-unreachable candidates found.");
            return;
        }

        System.out.println("Found continue-unreachable candidates: " + candidates.size());
        for (ContinueUnreachableCandidate c : candidates) {
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

        Integer endLine = selectedCandidate.getLastStatement().getEndLine();
        if (endLine == null) {
            System.out.println("Last statement has no endLine — skipping.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        String indent = leadingWhitespace(newLines.get(endLine - 1));
        String deadExpr = selectedCandidate.getDeadExpression();


        // Insert in reverse order at endLine (0-based index = endLine).
        newLines.add(endLine, indent + deadExpr + ";");
        newLines.add(endLine, indent + "continue;");

        String newName = buildNewName(originalFile.getFileName(), "_continue_unreachable");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Continue Unreachable Mutation";
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }
}