package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.BinaryOperationArgument;
import org.example.model.BinaryOperatorNode;
import org.example.neo4j.BinaryOperatorRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BinaryOperatorCommuteMutation extends MutationOperator {

    private final Random random = new Random();

    private BinaryOperatorNode selectedOperator;
    private BinaryOperationArgument leftArgument;
    private BinaryOperationArgument rightArgument;

    public BinaryOperatorCommuteMutation(FileModel originalFile, BinaryOperatorRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        BinaryOperatorRepository repository = (BinaryOperatorRepository) this.repo;

        List<BinaryOperatorNode> operators = repository.findSupportedBinaryOperators();

        if (operators == null || operators.isEmpty()) {
            System.out.println("No supported binary operators found.");
            return;
        }

        selectedOperator = operators.get(random.nextInt(operators.size()));
        leftArgument = repository.findLeftArgumentByOperationId(selectedOperator.getId());
        rightArgument = repository.findRightArgumentByOperationId(selectedOperator.getId());

        if (leftArgument == null || rightArgument == null) {
            System.out.println("Failed to load arguments for selected binary operator.");
            selectedOperator = null;
            return;
        }

        System.out.println("Selected binary operator: " + selectedOperator);
        System.out.println("Left argument: " + leftArgument);
        System.out.println("Right argument: " + rightArgument);
    }

    @Override
    protected FileModel mutate() {
        if (selectedOperator == null || leftArgument == null || rightArgument == null) {
            return null;
        }

        if (leftArgument.getStartLine() != rightArgument.getEndLine()) {
            System.out.println("Multiline binary operations are not supported.");
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());
        int lineIndex = leftArgument.getStartLine() - 1;

        String sourceLine = newLines.get(lineIndex);

        int replaceStart = leftArgument.getStartColumn() - 1;
        int replaceEnd = rightArgument.getEndColumn() - 1;

        String replacement = "(" + rightArgument.getCode()
                + " " + selectedOperator.getType()
                + " " + leftArgument.getCode() + ")";

        String newLine = sourceLine.substring(0, replaceStart)
                + replacement
                + sourceLine.substring(replaceEnd);

        newLines.set(lineIndex, newLine);

        String newName = buildNewName(originalFile.getFileName());
        String newPathString = buildNewName(originalFile.getFilePath().toString());
        Path newPath = originalFile.getFilePath().getParent().resolve(newPathString);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Binary Operator Commute";
    }

    private String buildNewName(String oldName) {
        String filename = oldName;
        int lastDot = filename.lastIndexOf('.');

        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern("ss_mm_HH"));
        String date = now.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s_%s", date, time));

        String newName;
        if (lastDot > 0) {
            String base = filename.substring(0, lastDot);
            String ext = filename.substring(lastDot);
            newName = base + sb + ext;
        } else {
            newName = filename + sb;
        }

        return newName;
    }
}
