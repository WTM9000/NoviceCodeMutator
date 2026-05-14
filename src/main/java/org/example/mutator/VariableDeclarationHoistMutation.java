package org.example.mutator;

import org.example.model.*;
import org.example.neo4j.repository.VariableDeclarationHoistRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

            // Оставляем только группы, которые не стоят уже в самом начале тела
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

        // Собираем строки объявлений для вставки наверх (порядок — сверху вниз)
        List<String> declarationsToHoist = new ArrayList<>();
        for (DeclarationStatementGroup group : selectedGroups) {
            String indent = detectIndent(newLines,
                    group.getDeclarationStatement().getStartLine());
            for (SingleDeclarator declarator : group.getDeclarators()) {
                String typeName = declarator.getTypeName();
                if (typeName == null || typeName.isBlank()) continue;

                declarationsToHoist.add(indent + typeName + " "
                        + declarator.getVariableName() + ";");
            }
        }

        // Обрабатываем исходные строки снизу вверх — чтобы не сбивать индексы
        List<DeclarationStatementGroup> sortedDesc = new ArrayList<>(selectedGroups);
        sortedDesc.sort(Comparator.comparingInt(
                g -> -g.getDeclarationStatement().getStartLine()));

        for (DeclarationStatementGroup group : sortedDesc) {
            int lineIndex = group.getDeclarationStatement().getStartLine() - 1;
            if (lineIndex < 0 || lineIndex >= newLines.size()) continue;

            String indent = leadingWhitespace(newLines.get(lineIndex));

            if (group.noneHaveInitializer()) {
                // int a; int b; — просто удаляем всю строку
                newLines.remove(lineIndex);

            } else {
                // Есть хотя бы один инициализатор — строим список присвоений
                List<String> assignments = new ArrayList<>();
                for (SingleDeclarator d : group.getDeclarators()) {
                    if (d.isHasInitializer()) {
                        assignments.add(indent + d.getVariableName()
                                + " = " + d.getInitializerCode() + ";");
                    }
                    // Без инициализатора — объявление уйдёт наверх, здесь ничего не остаётся
                }

                // Заменяем исходную строку на список присвоений
                newLines.remove(lineIndex);
                for (int i = assignments.size() - 1; i >= 0; i--) {
                    newLines.add(lineIndex, assignments.get(i));
                }
            }
        }

        // Вставляем объявления в начало тела функции (после строки с '{')
        int insertIndex = selectedMethodBody.getStartLine(); // startLine — строка с '{'
        for (int i = declarationsToHoist.size() - 1; i >= 0; i--) {
            newLines.add(insertIndex, declarationsToHoist.get(i));
        }

        String newName = buildNewName(originalFile.getFileName());
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);
        return new FileModel(newName, newPath, newLines);
    }

    private String detectIndent(List<String> lines, int startLine) {
        int lineIndex = startLine - 1;
        if (lineIndex < 0 || lineIndex >= lines.size()) return "";
        return leadingWhitespace(lines.get(lineIndex));
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
                + "_decl_hoist";
        if (lastDot > 0) {
            return oldName.substring(0, lastDot) + suffix + oldName.substring(lastDot);
        }
        return oldName + suffix;
    }

    @Override
    public String getMutationName() {
        return "Variable Declaration Hoist Mutation";
    }
}