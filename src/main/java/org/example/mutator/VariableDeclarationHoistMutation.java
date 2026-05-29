package org.example.mutator;

import org.example.model.*;
import org.example.neo4j.repository.VariableDeclarationHoistRepository;

import java.nio.file.Path;
import java.util.*;

public class VariableDeclarationHoistMutation extends MutationOperator {

    private final Random random = new Random();
    private StatementNode selectedMethodBody;
    private List<DeclarationStatementGroup> selectedGroups;

    public VariableDeclarationHoistMutation(FileModel originalFile,
                                            VariableDeclarationHoistRepository repo) {
        super(originalFile, repo);
    }

    @Override
    protected void getRelevantNodes() {
        VariableDeclarationHoistRepository repository =
                (VariableDeclarationHoistRepository) this.repo;

        List<VariableDeclarationHoistRepository.MethodBodyInfo> methods =
                repository.findAllFunctionBodies();

        if (methods == null || methods.isEmpty()) {
            System.out.println("No function bodies found.");
            return;
        }

        List<VariableDeclarationHoistRepository.MethodBodyInfo> shuffled =
                new ArrayList<>(methods);
        Collections.shuffle(shuffled, random);

        for (VariableDeclarationHoistRepository.MethodBodyInfo info : shuffled) {
            StatementNode body = info.getBody();
            if (body.getStartLine() < 0 || body.getEndLine() < 0) continue;

            List<DeclarationStatementGroup> groups =
                    repository.findDeclarationGroups(info.getScopeId());

            if (groups.isEmpty()) continue;

            int firstBodyLine = body.getStartLine() + 1;
            List<DeclarationStatementGroup> hoistable = new ArrayList<>();
            for (DeclarationStatementGroup g : groups) {
                if (g.getDeclarationStatement().getStartLine() > firstBodyLine) {
                    hoistable.add(g);
                }
            }

            if (hoistable.isEmpty()) continue;

            selectedMethodBody = body;
            selectedGroups = hoistable;

            System.out.println("Selected function: " + info.getMethodName());
            for (DeclarationStatementGroup g : hoistable) {
                System.out.println("  " + g);
            }
            return;
        }

        System.out.println("No functions with hoistable declarations found.");
    }

    @Override
    protected FileModel mutate() {
        if (selectedMethodBody == null || selectedGroups == null
                || selectedGroups.isEmpty()) {
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        // Collect original declaration lines to hoist (top-to-bottom order)
        List<String> declarationsToHoist = new ArrayList<>();
        for (DeclarationStatementGroup group : selectedGroups) {
            int lineIndex = group.getDeclarationStatement().getStartLine() - 1;
            if (lineIndex < 0 || lineIndex >= newLines.size()) continue;
            String indent = leadingWhitespace(newLines.get(lineIndex));
            // Each declarator becomes its own hoisted declaration line
            for (SingleDeclarator declarator : group.getDeclarators()) {
                String declaration = buildDeclarationLine(indent, declarator);

                if (declaration != null) {
                    declarationsToHoist.add(declaration);
                }
            }
        }

        // Remove original declaration lines bottom-to-top to preserve indices
        List<DeclarationStatementGroup> sortedDesc = new ArrayList<>(selectedGroups);
        sortedDesc.sort(Comparator.comparingInt(
                g -> -g.getDeclarationStatement().getStartLine()));

        for (DeclarationStatementGroup group : sortedDesc) {
            int lineIndex = group.getDeclarationStatement().getStartLine() - 1;
            if (lineIndex < 0 || lineIndex >= newLines.size()) continue;
            newLines.remove(lineIndex);
        }

        // Insert hoisted declarations at the top of the function body (after '{')
        int insertIndex = selectedMethodBody.getStartLine();
        for (int i = declarationsToHoist.size() - 1; i >= 0; i--) {
            newLines.add(insertIndex, declarationsToHoist.get(i));
        }

        String newName = buildNewName(originalFile.getFileName(), "decl_hoist");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);
        return new FileModel(newName, newPath, newLines);
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        return line.substring(0, i);
    }

    private String buildDeclarationLine(String indent, SingleDeclarator declarator) {
        if (declarator == null) {
            return null;
        }

        String typeName = declarator.getTypeName();
        String variableName = declarator.getVariableName();

        if (typeName == null || typeName.isBlank()) {
            return null;
        }

        if (variableName == null || variableName.isBlank()) {
            return null;
        }

        String declaratorText = formatDeclarator(typeName, variableName);
        StringBuilder line = new StringBuilder();

        line.append(indent).append(declaratorText);

        if (declarator.isHasInitializer()) {
            String initializer = declarator.getInitializerCode();

            if (initializer != null && !initializer.isBlank()) {
                line.append(" = ").append(initializer.trim());
            }
        }

        line.append(";");

        return line.toString();
    }

    /**
     * Converts CPG-style array type spelling to valid C/C++ declarator spelling.
     *
     * Examples:
     *   "int",      "a" -> "int a"
     *   "int []",   "a" -> "int a[]"
     *   "int [10]", "a" -> "int a[10]"
     *   "int [][]", "a" -> "int a[][]"
     *   "char * []", "s" -> "char * s[]"
     */
    private String formatDeclarator(String typeName, String variableName) {
        String normalizedType = typeName.trim().replaceAll("\\s+", " ");

        ArrayTypeParts arrayTypeParts = splitArraySuffix(normalizedType);

        return arrayTypeParts.baseType() + " "
                + variableName.trim()
                + arrayTypeParts.arraySuffix();
    }

    /**
     * Moves trailing array suffixes from the type to the variable declarator.
     *
     * Examples:
     *   "int []"      -> baseType="int",    arraySuffix="[]"
     *   "int [10]"    -> baseType="int",    arraySuffix="[10]"
     *   "int [] []"   -> baseType="int",    arraySuffix="[][]"
     *   "char * [32]" -> baseType="char *", arraySuffix="[32]"
     */
    private ArrayTypeParts splitArraySuffix(String typeName) {
        String base = typeName.trim();
        StringBuilder suffix = new StringBuilder();

        while (true) {
            int closeBracket = findTrailingCloseBracket(base);

            if (closeBracket < 0) {
                break;
            }

            int openBracket = base.lastIndexOf('[', closeBracket);

            if (openBracket < 0) {
                break;
            }

            String bracketPart = base.substring(openBracket, closeBracket + 1)
                    .replaceAll("\\s+", "");

            suffix.insert(0, bracketPart);
            base = base.substring(0, openBracket).trim();
        }

        return new ArrayTypeParts(base, suffix.toString());
    }

    private int findTrailingCloseBracket(String value) {
        int i = value.length() - 1;

        while (i >= 0 && Character.isWhitespace(value.charAt(i))) {
            i--;
        }

        if (i >= 0 && value.charAt(i) == ']') {
            return i;
        }

        return -1;
    }

    private record ArrayTypeParts(String baseType, String arraySuffix) {
    }



    @Override
    public String getMutationName() {
        return "Variable Declaration Hoist Mutation";
    }
}