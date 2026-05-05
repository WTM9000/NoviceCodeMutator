package org.example.mutator;

import org.example.model.FileModel;
import org.example.neo4j.VariableNode;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class VariableNameReplaceMutation {

    private FileModel originalFile;

    private List<VariableNode> variablesToChange;

    private String newVariableName;

    public VariableNameReplaceMutation(FileModel originalFile, List<VariableNode> variablesToChange, String newVariableName){
        this.originalFile = originalFile;
        this.variablesToChange = variablesToChange;
        this.newVariableName = newVariableName;
    }

    public FileModel getOriginalFile() {
        return originalFile;
    }

    public List<VariableNode> getVariablesToChange() {
        return variablesToChange;
    }

    public FileModel mutate(){
        FileModel mutatedFile = originalFile;
        List<String> newLines = originalFile.getLines();

        for(VariableNode variable: variablesToChange){
            String stringToChange = mutatedFile.getLines().get(variable.getLine()-1);

            int oldVariableStart = variable.getColumn()-1;
            int oldVariableEnd = oldVariableStart + variable.getName().length();

            StringBuilder newString = new StringBuilder(stringToChange).delete(oldVariableStart, oldVariableEnd);
            newString.insert(oldVariableStart, newVariableName);

            newLines.set(variable.getLine()-1, newString.toString());
        }

        String newName = buildNewName(originalFile.getFileName());

        System.out.print(newName);

        String newPathString = buildNewName(originalFile.getFilePath().toString());

        System.out.print(newPathString);

        Path newPath = originalFile.getFilePath().getParent().resolve(newPathString);

        mutatedFile = new FileModel(newName, newPath, newLines);

        return mutatedFile;
    }

    private String buildNewName(String oldName){
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
            String ext = filename.substring(lastDot); // includes dot
            newName = base + sb + ext;
        } else {
            newName = filename + sb;
        }
        return newName;
    }
}
