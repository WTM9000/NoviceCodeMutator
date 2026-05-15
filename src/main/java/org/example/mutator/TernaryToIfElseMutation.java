package org.example.mutator;

import org.example.model.BinaryOperationArgument;
import org.example.model.FileModel;
import org.example.model.TernaryExpressionNode;
import org.example.neo4j.repository.TernaryRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TernaryToIfElseMutation extends MutationOperator {

    private TernaryExpressionNode selectedExpression;
    private final Random random = new Random();

    public TernaryToIfElseMutation(FileModel originalFile, TernaryRepository repo) {
        super(originalFile, repo);
        this.originalFile = originalFile;
    }

    @Override
    protected void getRelevantNodes() {
        TernaryRepository repository = (TernaryRepository) this.repo;

        List<TernaryExpressionNode> candidates = repository.findAllTernaryExpressions();

        if (candidates == null || candidates.isEmpty()) {
            System.out.println("No ternary expressions found.");
            return;
        }

        System.out.println("Found ternary expressions: " + candidates.size());
        for (TernaryExpressionNode candidate : candidates) {
            System.out.println(candidate);
        }

        selectedExpression = candidates.get(random.nextInt(candidates.size()));
        System.out.println("Selected: " + selectedExpression);
    }

    @Override
    protected FileModel mutate() {
        if (selectedExpression == null) {
            return null;
        }

        List<String> newLines = new ArrayList<>(originalFile.getLines());

        int startLineIndex = selectedExpression.getTernaryStartLine() - 1;
        int endLineIndex   = selectedExpression.getTernaryEndLine() - 1;

        String firstSourceLine = newLines.get(startLineIndex);
        String indentation = leadingWhitespace(firstSourceLine);

        String condCode = selectedExpression.getCondition().getCode();
        String thenCode = selectedExpression.getThenBranch().getCode();
        String elseCode = selectedExpression.getElseBranch().getCode();

        // Удаляем все строки диапазона снизу вверх (поддержка multiline)
        for (int i = endLineIndex; i >= startLineIndex; i--) {
            newLines.remove(i);
        }

        List<String> replacement = buildReplacement(
                indentation, condCode, thenCode, elseCode,
                firstSourceLine, selectedExpression
        );

        // Вставляем строки замены начиная с позиции удалённого диапазона
        for (int i = replacement.size() - 1; i >= 0; i--) {
            newLines.add(startLineIndex, replacement.get(i));
        }

        String newName = buildNewName(originalFile.getFileName(), "ternary_to_if");
        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        return new FileModel(newName, newPath, newLines);
    }

    /**
     * Строит список строк замены. Два режима:
     *
     * 1. Декларация:  int x = cond ? a : b;
     *    →  int x;
     *       if (cond) {
     *           x = a;
     *       } else {
     *           x = b;
     *       }
     *
     * 2. Обычное присваивание / вызов:  x = cond ? a : b;  /  printf(..., cond ? a : b);
     *    →  if (cond) {
     *           <prefix> a<suffix>
     *       } else {
     *           <prefix> b<suffix>
     *       }
     */
    private List<String> buildReplacement(String indentation,
                                          String condCode,
                                          String thenCode,
                                          String elseCode,
                                          String firstSourceLine,
                                          TernaryExpressionNode expr) {
        List<String> lines = new ArrayList<>();

        if (expr.isDeclaration()) {
            // Режим декларации: тип и имя переменной получены из графа
            String typeName = expr.getDeclarationTypeName();
            String varName  = expr.getDeclarationVarName();

            lines.add(indentation + typeName + " " + varName + ";");
            lines.add(indentation + "if (" + condCode + ") {");
            lines.add(indentation + "    " + varName + " = " + thenCode + ";");
            lines.add(indentation + "} else {");
            lines.add(indentation + "    " + varName + " = " + elseCode + ";");
            lines.add(indentation + "}");

        } else {
            // Режим обычного выражения: строим prefix/suffix из исходной строки
            int replaceStart = expr.getTernaryStartColumn() - 1;
            int replaceEnd   = expr.getTernaryEndColumn() - 1;

            String prefix = firstSourceLine.substring(0, replaceStart).stripTrailing();
            String lastSourceLine = originalFile.getLines().get(expr.getTernaryEndLine() - 1);
            String suffix = lastSourceLine.substring(replaceEnd).stripLeading();

            boolean suffixHasSemicolon = suffix.contains(";");

            String thenBody;
            String elseBody;

            if (prefix.isBlank()) {
                // Тернарный стоит одиноко, всё что было — тернарный + ";"
                thenBody = thenCode + ";";
                elseBody = elseCode + ";";
            } else if (suffixHasSemicolon) {
                // Суффикс уже несёт ";" (например ");" для printf) — не добавляем
                thenBody = prefix + " " + thenCode + suffix;
                elseBody = prefix + " " + elseCode + suffix;
            } else {
                thenBody = prefix + " " + thenCode + suffix + ";";
                elseBody = prefix + " " + elseCode + suffix + ";";
            }

            lines.add(indentation + "if (" + condCode + ") {");
            lines.add(indentation + "    " + thenBody.stripLeading());
            lines.add(indentation + "} else {");
            lines.add(indentation + "    " + elseBody.stripLeading());
            lines.add(indentation + "}");
        }

        return lines;
    }

    @Override
    public String getMutationName() {
        return "Ternary To If-Else Mutation";
    }

    private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }
}