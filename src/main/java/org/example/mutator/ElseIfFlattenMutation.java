package org.example.mutator;

import org.example.model.ElseIfFlattenCandidate;
import org.example.model.FileModel;
import org.example.neo4j.repository.ElseIfFlattenRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ElseIfFlattenMutation extends MutationOperator {

    private final Random random = new Random();
    private ElseIfFlattenCandidate selectedCandidate;

    public ElseIfFlattenMutation(FileModel originalFile, ElseIfFlattenRepository repo) {
        super(originalFile, repo);
    }

    @Override
    protected void getRelevantNodes() {
        ElseIfFlattenRepository repository = (ElseIfFlattenRepository) this.repo;
        List<ElseIfFlattenCandidate> candidates = repository.findCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No else-if flatten candidates found.");
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

        int startLineIndex = selectedCandidate.getIfChainNode().getStartLine() - 1;
        int endLineIndex   = selectedCandidate.getIfChainNode().getEndLine() - 1;

        if (startLineIndex < 0 || endLineIndex >= newLines.size()) {
            return null;
        }

        String indent = leadingWhitespace(newLines.get(startLineIndex));
        List<String> replacement = buildNestedIfElse(
                selectedCandidate.getBranches(), indent);

        for (int i = endLineIndex; i >= startLineIndex; i--) {
            newLines.remove(i);
        }

        for (int i = replacement.size() - 1; i >= 0; i--) {
            newLines.add(startLineIndex, replacement.get(i));
        }

        String newName = buildNewName(originalFile.getFileName());
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);
        return new FileModel(newName, newPath, newLines);
    }

    // Recursively build nested if/else from branch list
    // branches[0] = first if, branches[1..n-1] = else-if/else
    private List<String> buildNestedIfElse(List<ElseIfFlattenCandidate.Branch> branches,
                                           String indent) {
        List<String> lines = new ArrayList<>();
        ElseIfFlattenCandidate.Branch head = branches.get(0);

        lines.add(indent + "if (" + head.getConditionCode() + ") {");
        appendBodyLines(lines, head.getBodyCode(), indent + "    ");
        lines.add(indent + "}");

        if (branches.size() > 1) {
            List<ElseIfFlattenCandidate.Branch> tail = branches.subList(1, branches.size());

            if (tail.size() == 1 && tail.get(0).isFinalElse()) {
                // Bare else — just append
                lines.set(lines.size() - 1, indent + "} else {");
                appendBodyLines(lines, tail.get(0).getBodyCode(), indent + "    ");
                lines.add(indent + "}");
            } else {
                // Recurse into nested else { if ... }
                lines.set(lines.size() - 1, indent + "} else {");
                List<String> nested = buildNestedIfElse(tail, indent + "    ");
                lines.addAll(nested);
                lines.add(indent + "}");
            }
        }

        return lines;
    }

    private void appendBodyLines(List<String> target, String bodyCode, String indent) {
        if (bodyCode == null || bodyCode.isBlank()) return;
        String trimmed = bodyCode.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isBlank()) return;
        for (String line : trimmed.split("\\R")) {
            target.add(indent + line.stripLeading());
        }
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        return line.substring(0, i);
    }

    private String buildNewName(String oldName) {
        int lastDot = oldName.lastIndexOf('.');
        String suffix = "_" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd_MM_yyyy_ss_mm_HH"))
                + "_elseif_flatten";
        if (lastDot > 0)
            return oldName.substring(0, lastDot) + suffix + oldName.substring(lastDot);
        return oldName + suffix;
    }

    @Override
    public String getMutationName() { return "Else-If Flatten Mutation"; }
}