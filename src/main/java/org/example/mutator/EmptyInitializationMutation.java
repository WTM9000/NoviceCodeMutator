package org.example.mutator;

import org.example.model.EmptyInitializationCandidate;
import org.example.model.FileModel;
import org.example.model.SingleDeclarator;
import org.example.neo4j.repository.EmptyInitializationRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EmptyInitializationMutation extends MutationOperator {

    private final Random random = new Random();
    private EmptyInitializationCandidate selectedCandidate;

    public EmptyInitializationMutation(FileModel originalFile,
                                       EmptyInitializationRepository repo) {
        super(originalFile, repo);
    }

    @Override
    protected void getRelevantNodes() {
        EmptyInitializationRepository repository =
                (EmptyInitializationRepository) this.repo;

        List<EmptyInitializationCandidate> candidates = repository.findCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No empty initialization candidates found.");
            return;
        }

        // If multiple declarators share a line, one is chosen at random here
        selectedCandidate = candidates.get(random.nextInt(candidates.size()));
        System.out.println("Selected candidate: " + selectedCandidate);
    }

    @Override
    protected FileModel mutate() {
        if (selectedCandidate == null) {
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        int lineIndex = selectedCandidate.getDeclarationStatement().getStartLine() - 1;
        if (lineIndex < 0 || lineIndex >= newLines.size()) {
            return null;
        }

        String indent    = leadingWhitespace(newLines.get(lineIndex));
        String typeName  = selectedCandidate.getTypeName();
        String varName   = selectedCandidate.getVariableName();
        String zeroValue = zeroValueFor(typeName);

        // Build the mutated declaration line for the chosen declarator
        String mutatedDecl = indent + typeName + " " + varName + " = " + zeroValue + ";";

        // Build the preserved line for remaining declarators on the same original line (if any)
        String remainingLine = buildRemainingLine(indent, typeName,
                selectedCandidate.getRemainingDeclarators());

        if (selectedCandidate.isHasInitializer()) {
            // Variant 2: int x = 3;  ->  int x = 0;
            //                             [int b = 2;]   <- remaining, if any
            //                             x = 3;
            String originalInit = selectedCandidate.getInitializerCode();
            newLines.set(lineIndex, mutatedDecl);
            int insertAt = lineIndex + 1;
            if (remainingLine != null) {
                newLines.add(insertAt, remainingLine);
                insertAt++;
            }
            newLines.add(insertAt, indent + varName + " = " + originalInit + ";");
        } else {
            // Variant 1: int x;  ->  int x = 0;
            //                         [int b = 2;]  <- remaining, if any
            newLines.set(lineIndex, mutatedDecl);
            if (remainingLine != null) {
                newLines.add(lineIndex + 1, remainingLine);
            }
        }

        String newName = buildNewName(originalFile.getFileName(), "empty_init");
        Path newPath   = originalFile.getFilePath().getParent().resolve(newName);
        return new FileModel(newName, newPath, newLines);
    }

    /**
     * Builds a declaration line for the declarators that were NOT mutated.
     * Example: for remaining [b = 2, c] with type "int" -> "    int b = 2, c;"
     * Returns null if the list is empty.
     */
    private String buildRemainingLine(String indent,
                                      String typeName,
                                      List<SingleDeclarator> remaining) {
        if (remaining == null || remaining.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder(indent).append(typeName).append(" ");
        for (int i = 0; i < remaining.size(); i++) {
            SingleDeclarator d = remaining.get(i);
            if (d.isHasInitializer()) {
                sb.append(d.getVariableName()).append(" = ").append(d.getInitializerCode());
            } else {
                sb.append(d.getVariableName());
            }
            if (i < remaining.size() - 1) sb.append(", ");
        }
        sb.append(";");
        return sb.toString();
    }

    /**
     * Returns the appropriate zero-value literal for the given primitive type.
     *
     * int / long / short / unsigned variants / size_t / intN_t -> 0
     * float / double / long double                              -> 0.0
     * char                                                      -> '\0'
     */
    private String zeroValueFor(String typeName) {
        String t = typeName.trim().toLowerCase();
        if (t.equals("char")) {
            return "'\\0'";
        }
        if (t.equals("float") || t.equals("double") || t.equals("long double")) {
            return "0.0";
        }
        return "0";
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        return line.substring(0, i);
    }

    @Override
    public String getMutationName() {
        return "Empty Initialization Mutation";
    }
}
