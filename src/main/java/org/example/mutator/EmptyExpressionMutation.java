package org.example.mutator;

import org.example.model.EmptyExpressionCandidate;
import org.example.model.FileModel;
import org.example.neo4j.repository.EmptyExpressionRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class EmptyExpressionMutation extends MutationOperator {

    private final Random random = new Random();
    private EmptyExpressionCandidate selectedCandidate;

    public EmptyExpressionMutation(FileModel originalFile,
                                   EmptyExpressionRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        EmptyExpressionRepository repository = (EmptyExpressionRepository) this.repo;

        List<EmptyExpressionCandidate> candidates = repository.findAllCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No empty-expression candidates found.");
            return;
        }

        System.out.println("Found empty-expression candidates: " + candidates.size());
        for (EmptyExpressionCandidate c : candidates) {
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

        // Insert after the last line of the chosen statement.
        int insertIndex = endLine; // 0-based index of the line after the statement
        String indent = leadingWhitespace(newLines.get(endLine - 1));

        String deadLine = indent + selectedCandidate.getDeadExpression() + ";";
        newLines.add(insertIndex, deadLine);

        String newName = buildNewName(originalFile.getFileName(), "_empty_expr");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Empty Expression Mutation";
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }
}