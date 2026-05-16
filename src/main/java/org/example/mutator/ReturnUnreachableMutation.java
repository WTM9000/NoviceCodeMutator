package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.ReturnUnreachableCandidate;
import org.example.neo4j.repository.ReturnUnreachableRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mutation 6 — Unreachable Code After Return.
 *
 * Inserts a dead expression statement immediately after a randomly chosen
 * ReturnStatement. The inserted line is unreachable by definition.
 *
 *   // before:
 *   return x;
 *
 *   // after:
 *   return x;
 *   x++;     // unreachable dead code
 */
public class ReturnUnreachableMutation extends MutationOperator {

    private final Random random = new Random();
    private ReturnUnreachableCandidate selectedCandidate;

    public ReturnUnreachableMutation(FileModel originalFile,
                                     ReturnUnreachableRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        ReturnUnreachableRepository repository = (ReturnUnreachableRepository) this.repo;

        List<ReturnUnreachableCandidate> candidates = repository.findAllCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No return-unreachable candidates found.");
            return;
        }

        System.out.println("Found return-unreachable candidates: " + candidates.size());
        for (ReturnUnreachableCandidate c : candidates) {
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

        Integer startLine = selectedCandidate.getReturnStatement().getStartLine();
        int endLine   = selectedCandidate.getReturnStatement().getEndLine();

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        String returnIndent = leadingWhitespace(newLines.get(endLine - 1));
        String deadLine     = returnIndent + selectedCandidate.getDeadExpression() + ";";

        if (selectedCandidate.isNeedsBraces()) {
            // Braceless parent detected: wrap the return + dead expr in braces.
            // The parent header line (e.g. "if (a > 0)") is on startLine - 1.
            String innerIndent = returnIndent + "    ";

            // Re-indent the return line itself.
            String originalReturn = newLines.get(endLine - 1).stripLeading();

            // Build replacement lines.
            String openBrace   = returnIndent + "{";
            String indentedRet = innerIndent + originalReturn;
            String indentedDead = innerIndent + selectedCandidate.getDeadExpression() + ";";
            String closeBrace  = returnIndent + "}";

            // Remove original return line and insert the braced block.
            newLines.remove(endLine - 1);
            newLines.add(endLine - 1, closeBrace);
            newLines.add(endLine - 1, indentedDead);
            newLines.add(endLine - 1, indentedRet);
            newLines.add(endLine - 1, openBrace);
        } else {
            // Parent already has braces — simple insertion after the return.
            newLines.add(endLine, deadLine);
        }

        String newName = buildNewName(originalFile.getFileName(), "_return_unreachable");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Return Unreachable Mutation";
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }
}