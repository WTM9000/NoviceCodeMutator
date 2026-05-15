package org.example.mutator;

import org.example.model.ContinueAntiIdiomCandidate;
import org.example.model.FileModel;
import org.example.neo4j.repository.ContinueAntiIdiomRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ContinueAntiIdiomMutation extends MutationOperator {

    private final Random random = new Random();
    private ContinueAntiIdiomCandidate selectedCandidate;

    public ContinueAntiIdiomMutation(FileModel originalFile,
                                     ContinueAntiIdiomRepository repo) {
        super(originalFile, repo);
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
        int endLineIndex = selectedCandidate.getIfNode().getEndLine() - 1;

        if (startLineIndex < 0 || endLineIndex >= newLines.size() || startLineIndex > endLineIndex) {
            return null;
        }

        String indent = leadingWhitespace(newLines.get(startLineIndex));
        List<String> replacement = buildReplacement(indent, selectedCandidate);

        for (int i = endLineIndex; i >= startLineIndex; i--) {
            newLines.remove(i);
        }

        for (int i = replacement.size() - 1; i >= 0; i--) {
            newLines.add(startLineIndex, replacement.get(i));
        }

        String newName = buildNewName(originalFile.getFileName(), "_continue_anti_idiom");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);
        return new FileModel(newName, newPath, newLines);
    }

    private List<String> buildReplacement(String indent, ContinueAntiIdiomCandidate candidate) {
        List<String> lines = new ArrayList<>();
        String negatedGuard = negateGuard(candidate.getGuardCode());

        if (!candidate.hasElse() || candidate.isTrivialElse()) {
            lines.add(indent + "if (" + negatedGuard + ")");
            lines.add(indent + "    continue;");
            appendBodyLines(lines, candidate.getThenNode().getCode(), indent);
            return lines;
        }

        lines.add(indent + "if (" + negatedGuard + ") {");
        appendIndentedBody(lines, candidate.getElseNode().getCode(), indent + "    ");
        lines.add(indent + "    continue;");
        lines.add(indent + "}");
        appendBodyLines(lines, candidate.getThenNode().getCode(), indent);

        return lines;
    }

    private String negateGuard(String guardCode) {
        String trimmed = guardCode.trim();

        if (trimmed.startsWith("!")) {
            return trimmed.substring(1).trim();
        }

        return "!(" + trimmed + ")";
    }

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
