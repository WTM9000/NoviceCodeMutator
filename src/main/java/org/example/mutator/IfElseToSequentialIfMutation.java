package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.IfBranchNode;
import org.example.model.IfElseChainNode;
import org.example.model.IfRootNode;
import org.example.model.StatementNode;
import org.example.neo4j.repository.IfElseChainRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class IfElseToSequentialIfMutation extends MutationOperator {

    private final Random random = new Random();
    private IfElseChainNode selectedChain;

    public IfElseToSequentialIfMutation(FileModel originalFile, IfElseChainRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        IfElseChainRepository repository = (IfElseChainRepository) this.repo;

        List<IfRootNode> roots = repository.findAllRootIfStatements();

        if (roots == null || roots.isEmpty()) {
            System.out.println("No root if-statements found.");
            return;
        }

        IfRootNode selectedRoot = roots.get(random.nextInt(roots.size()));
        selectedChain = repository.findChainByRootId(selectedRoot.getId());

        if (selectedChain == null) {
            System.out.println("Could not load if-else chain.");
            return;
        }

        if (selectedChain.getBranches() == null || selectedChain.getBranches().isEmpty()) {
            System.out.println("Selected chain has no branches.");
            selectedChain = null;
            return;
        }

        if (selectedChain.getBranches().size() == 1 && selectedChain.getElseBlock() == null) {
            System.out.println("Chain is too short. Minimal acceptable structure is if ... else ...");
            selectedChain = null;
            return;
        }

        System.out.println("Selected chain: " + selectedChain);
    }

    @Override
    protected FileModel mutate() {
        if (selectedChain == null) {
            return null;
        }

        StatementNode whole = selectedChain.getWholeStatement();
        if (whole == null) {
            return null;
        }

        int startLineIndex = whole.getStartLine() - 1;
        int endLineIndex = whole.getEndLine() - 1;

        if (startLineIndex < 0 || endLineIndex >= originalFile.getLines().size() || startLineIndex > endLineIndex) {
            System.out.println("Invalid statement bounds for selected if-else chain.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        String firstLine = originalFile.getLines().get(startLineIndex);
        String indentation = leadingWhitespace(firstLine);

        List<String> replacementLines = buildSequentialIfLines(indentation, selectedChain);

        for (int i = endLineIndex; i >= startLineIndex; i--) {
            newLines.remove(i);
        }

        for (int i = replacementLines.size() - 1; i >= 0; i--) {
            newLines.add(startLineIndex, replacementLines.get(i));
        }

        String newName = buildNewName(originalFile.getFileName(), "_ifelse_to_seqif");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "If-Else To Sequential If Mutation";
    }

    private List<String> buildSequentialIfLines(String indentation, IfElseChainNode chain) {
        List<String> result = new ArrayList<>();
        List<String> negatedConditions = new ArrayList<>();

        for (IfBranchNode branch : chain.getBranches()) {
            String conditionCode = safeCode(branch.getCondition()).trim();
            String blockCode = normalizeBlock(safeCode(branch.getBlock()).trim());

            String combinedCondition = negatedConditions.isEmpty()
                    ? conditionCode
                    : String.join(" && ", negatedConditions) + " && " + conditionCode;

            result.add(indentation + "if (" + combinedCondition + ") " + blockCode);
            negatedConditions.add("!(" + conditionCode + ")");
        }

        if (chain.getElseBlock() != null) {
            String elseCondition = String.join(" && ", negatedConditions);
            if (!elseCondition.isBlank()) {
                result.add(indentation + "if (" + elseCondition + ") " + normalizeBlock(safeCode(chain.getElseBlock()).trim()));
            }
        }

        return result;
    }

    private String normalizeBlock(String blockCode) {
        if (blockCode == null || blockCode.isBlank()) {
            return "{ }";
        }

        String trimmed = blockCode.trim();

        if (trimmed.startsWith("{")) {
            return trimmed;
        }

        if (trimmed.endsWith(";")) {
            return "{ " + trimmed + " }";
        }

        return "{ " + trimmed + "; }";
    }

    private String safeCode(StatementNode node) {
        return node == null || node.getCode() == null ? "" : node.getCode();
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }
}
