package org.example.mutator;

import org.example.model.CompoundConditionExpandCandidate;
import org.example.model.FileModel;
import org.example.neo4j.repository.CompoundConditionExpandRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CompoundConditionExpandMutation extends MutationOperator {

    private final Random random = new Random();
    private CompoundConditionExpandCandidate selectedCandidate;

    public CompoundConditionExpandMutation(FileModel originalFile,
                                           CompoundConditionExpandRepository repo) {
        super(originalFile, repo);
    }

    @Override
    protected void getRelevantNodes() {
        CompoundConditionExpandRepository repository =
                (CompoundConditionExpandRepository) this.repo;
        List<CompoundConditionExpandCandidate> candidates = repository.findCandidates();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No compound condition expand candidates found.");
            return;
        }

        selectedCandidate = candidates.get(random.nextInt(candidates.size()));
        System.out.println("Selected candidate: " + selectedCandidate);
    }

    @Override
    protected FileModel mutate() {
        if (selectedCandidate == null) return null;

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        int startLineIndex = selectedCandidate.getIfNode().getStartLine() - 1;
        int endLineIndex   = selectedCandidate.getIfNode().getEndLine() - 1;

        if (startLineIndex < 0 || endLineIndex >= newLines.size()) return null;

        String indent = leadingWhitespace(newLines.get(startLineIndex));

        List<String> replacement;
        if (selectedCandidate.getOperator()
                == CompoundConditionExpandCandidate.LogicalOperator.AND) {
            replacement = buildAndNested(
                    selectedCandidate.getConditions(),
                    selectedCandidate.getBodyCode(),
                    indent);
        } else {
            replacement = buildOrNested(
                    selectedCandidate.getConditions(),
                    selectedCandidate.getBodyCode(),
                    indent);
        }

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

    // B-AND: if (C1 && ... && Cn) { S } → if (C1) { if (C2) { ... if (Cn) { S } } }
    private List<String> buildAndNested(List<String> conditions, String bodyCode,
                                        String baseIndent) {
        // Start from innermost: if (Cn) { S }
        List<String> current = new ArrayList<>();
        String innerIndent = baseIndent + "    ".repeat(conditions.size() - 1);
        current.add(innerIndent + "if (" + conditions.get(conditions.size() - 1) + ") {");
        appendBodyLines(current, bodyCode,
                innerIndent + "    ");
        current.add(innerIndent + "}");

        // Wrap outward from Cn-1 down to C1
        for (int i = conditions.size() - 2; i >= 0; i--) {
            String level = baseIndent + "    ".repeat(i);
            List<String> wrapper = new ArrayList<>();
            wrapper.add(level + "if (" + conditions.get(i) + ") {");
            for (String line : current) {
                wrapper.add("    " + line);
            }
            wrapper.add(level + "}");
            current = wrapper;
        }

        return current;
    }

    // B-OR: if (C1 || ... || Cn) { S }
    // → if (C1) { S } else { if (C2) { S } else { ... if (Cn) { S } ... } }
    private List<String> buildOrNested(List<String> conditions, String bodyCode,
                                       String baseIndent) {
        // Build from tail: base case is if (Cn) { S }
        List<String> tail = new ArrayList<>();
        tail.add("if (" + conditions.get(conditions.size() - 1) + ") {");
        appendBodyLines(tail, bodyCode, "    ");
        tail.add("}");

        // Wrap each level from Cn-1 down to C1
        for (int i = conditions.size() - 2; i >= 0; i--) {
            List<String> wrapper = new ArrayList<>();
            wrapper.add("if (" + conditions.get(i) + ") {");
            appendBodyLines(wrapper, bodyCode, "    ");
            wrapper.add("} else {");
            for (String line : tail) {
                wrapper.add("    " + line);
            }
            wrapper.add("}");
            tail = wrapper;
        }

        // Apply base indent to all lines
        List<String> result = new ArrayList<>();
        for (String line : tail) {
            result.add(baseIndent + line);
        }
        return result;
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
                + "_compound_expand";
        if (lastDot > 0)
            return oldName.substring(0, lastDot) + suffix + oldName.substring(lastDot);
        return oldName + suffix;
    }

    @Override
    public String getMutationName() { return "Compound Condition Expand Mutation"; }
}