package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.SynchronizedVariablesCandidate;
import org.example.neo4j.repository.SynchronizedVariablesRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mutation 2 — Synchronized Variables.
 *
 * Wraps a randomly chosen single-line ExpressionStatement:
 *
 *   <original_stmt>;
 *
 * into:
 *
 *   int <syncVarX> = <expr>;
 *   int <syncVarY> = <expr>;
 *   if (<syncVarX> == <syncVarY>) {
 *       <original_stmt>;
 *   }
 *
 * The condition is always true, so observable behaviour is unchanged.
 * Both variable names are caller-supplied via parameters "syncVarX" / "syncVarY".
 */
public class SynchronizedVariablesMutation extends MutationOperator {

    private final Random random = new Random();
    private final String syncVarX;
    private final String syncVarY;

    private SynchronizedVariablesCandidate selectedCandidate;
    private boolean hasConflict = false;

    public SynchronizedVariablesMutation(FileModel originalFile,
                                         SynchronizedVariablesRepository repo,
                                         String syncVarX,
                                         String syncVarY) {
        super(originalFile, repo);
        this.originalFile = originalFile;
        this.syncVarX = syncVarX;
        this.syncVarY = syncVarY;
    }

    @Override
    protected void getRelevantNodes() {
        SynchronizedVariablesRepository repository = (SynchronizedVariablesRepository) this.repo;

        List<SynchronizedVariablesCandidate> candidates = repository.findAllCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No synchronized-variables candidates found.");
            return;
        }

        System.out.println("Found synchronized-variables candidates: " + candidates.size());
        for (SynchronizedVariablesCandidate c : candidates) {
            System.out.println(c);
        }

        selectedCandidate = candidates.get(random.nextInt(candidates.size()));

        // Check that neither variable name conflicts with an existing declaration in the block.
        boolean xConflict = repository.hasDeclarationInBlock(selectedCandidate.getBlockId(), syncVarX);
        boolean yConflict = repository.hasDeclarationInBlock(selectedCandidate.getBlockId(), syncVarY);

        if (xConflict || yConflict) {
            System.out.println("Declaration conflict: '"
                    + (xConflict ? syncVarX : syncVarY)
                    + "' already exists in block " + selectedCandidate.getBlockId());
            hasConflict = true;
            return;
        }

        System.out.println("Selected: " + selectedCandidate);
    }

    @Override
    protected FileModel mutate() {
        if (selectedCandidate == null) {
            return null;
        }

        if (hasConflict) {
            System.out.println("Skipping mutation due to variable name conflict.");
            return null;
        }

        if (selectedCandidate.getStatement().getStartLine()
                != selectedCandidate.getStatement().getEndLine()) {
            System.out.println("Multiline statements are not supported.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        int lineIndex = selectedCandidate.getStatement().getStartLine() - 1;
        String originalLine = newLines.get(lineIndex);
        String indent = leadingWhitespace(originalLine);
        String innerIndent = indent + "    ";

        String expr = selectedCandidate.getSyncExpression();

        String xDecl    = indent + "int " + syncVarX + " = " + expr + ";";
        String yDecl    = indent + "int " + syncVarY + " = " + expr + ";";
        String ifOpen   = indent + "if (" + syncVarX + " == " + syncVarY + ") {";
        String innerStmt = innerIndent + originalLine.trim();
        String ifClose  = indent + "}";

        newLines.remove(lineIndex);
        // Insert in reverse order to keep indices valid.
        newLines.add(lineIndex, ifClose);
        newLines.add(lineIndex, innerStmt);
        newLines.add(lineIndex, ifOpen);
        newLines.add(lineIndex, yDecl);
        newLines.add(lineIndex, xDecl);

        String newName = buildNewName(originalFile.getFileName(), "_sync_vars");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Synchronized Variables Mutation";
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }
}
