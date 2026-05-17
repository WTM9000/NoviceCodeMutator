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
                String typeName = declarator.getTypeName();
                if (typeName == null || typeName.isBlank()) continue;

                if (declarator.isHasInitializer()) {
                    declarationsToHoist.add(indent + typeName + " "
                            + declarator.getVariableName()
                            + " = " + declarator.getInitializerCode() + ";");
                } else {
                    declarationsToHoist.add(indent + typeName + " "
                            + declarator.getVariableName() + ";");
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

    @Override
    public String getMutationName() {
        return "Variable Declaration Hoist Mutation";
    }
}