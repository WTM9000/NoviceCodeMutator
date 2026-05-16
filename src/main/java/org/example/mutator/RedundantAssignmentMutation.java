package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.SimpleAssignmentNode;
import org.example.neo4j.repository.SimpleAssignmentRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RedundantAssignmentMutation extends MutationOperator {

    private final Random random = new Random();
    private SimpleAssignmentNode selectedAssignment;

    public RedundantAssignmentMutation(FileModel originalFile, SimpleAssignmentRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        SimpleAssignmentRepository repository = (SimpleAssignmentRepository) this.repo;

        List<SimpleAssignmentNode> candidates = repository.findSimpleIdentifierAssignments();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No simple identifier assignments found.");
            return;
        }

        selectedAssignment = candidates.get(random.nextInt(candidates.size()));
        System.out.println("Selected assignment: " + selectedAssignment);
    }

    @Override
    protected FileModel mutate() {
        if (selectedAssignment == null) {
            return null;
        }

        int lineIndex = selectedAssignment.getStartLine() - 1;

        if (lineIndex < 0 || lineIndex >= originalFile.getLines().size()) {
            System.out.println("Invalid line index for selected assignment.");
            return null;
        }

        String originalLine = originalFile.getLines().get(lineIndex);
        String mutatedLine  = applyRedundantAssignment(originalLine, selectedAssignment);

        if (mutatedLine == null) {
            System.out.println("Could not apply redundant assignment to line: " + originalLine);
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());
        newLines.set(lineIndex, mutatedLine);

        String newName = buildNewName(originalFile.getFileName(), "_redundant_assign");
        Path   newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    @Override
    public String getMutationName() {
        return "Redundant Assignment Mutation";
    }

    /**
     * Replaces first rhs instanse.
     *
     * Look for percise fragment = "= <rhsCode>" in line
     * Use startColumn/endColumn from graph,
     * else do text search.
     */
    private String applyRedundantAssignment(String line, SimpleAssignmentNode assignment) {
        String rhs = assignment.getRhsCode().trim();

        int startCol = assignment.getStartColumn();
        int endCol   = assignment.getEndColumn();

        // Если колонки из графа валидны — ищем RHS по ним.
        // endColumn указывает на конец assign-expression, нам нужен именно rhs.
        // Надёжнее — текстовый поиск "= rhs" с конца, чтобы не попасть на lhs.
        String pattern = "= " + rhs;
        int patternIdx = line.lastIndexOf(pattern);

        if (patternIdx < 0) {
            // Попробуем без пробела (компактный стиль: "=x")
            pattern   = "=" + rhs;
            patternIdx = line.lastIndexOf(pattern);
        }

        if (patternIdx < 0) {
            return null;
        }

        // Позиция сразу после паттерна
        int afterPattern = patternIdx + pattern.length();

        // Убеждаемся, что за RHS идёт только пробелы/;/конец строки
        // (не хотим трогать "= x + something")
        String tail = line.substring(afterPattern).trim();
        if (!tail.isEmpty() && !tail.startsWith(";") && !tail.startsWith(")") && !tail.startsWith(",")) {
            return null;
        }

        // Вставляем " = rhs" сразу после текущего rhs
        return line.substring(0, afterPattern) + " = " + rhs + line.substring(afterPattern);
    }

}