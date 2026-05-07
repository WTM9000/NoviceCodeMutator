package org.example.mutator;

import org.example.model.FileModel;
import org.example.neo4j.VariableNode;
import org.example.neo4j.VariableRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class VariableNameReplaceMutation extends MutationOperator {

    private List<VariableNode> variablesToChange = null;

    private String newVariableName;

    public VariableNameReplaceMutation(FileModel originalFile, VariableRepository repo, String newVariableName){
        super(originalFile, repo);
        this.originalFile = originalFile;
        this.newVariableName = newVariableName;
    }

    public FileModel getOriginalFile() {
        return originalFile;
    }

    public List<VariableNode> getVariablesToChange() {
        return variablesToChange;
    }

    private void getRelevantNodes(){
        VariableRepository repository = (VariableRepository) this.repo;

        List<VariableNode> variables = repository.findAllVariableDeclarations();

        if (variables.isEmpty()){
            System.out.print("No variables found!");
            return;
        }

        System.out.println("Найдено объявлений: " + variables.size());
        for (VariableNode variable : variables) {
            System.out.println(variable);
        }

        VariableNode targetVariable;

        if (variables.size() > 3 ){
            targetVariable = variables.get(2);
        } else targetVariable = variables.get(0);

        variablesToChange = repository.findAllReferencesToVariable(targetVariable.getId());

        variablesToChange.add(targetVariable);

        System.out.println("Найдено использований переменной "+ targetVariable.getName() +": " + variablesToChange.size());
        for (VariableNode variable : variablesToChange) {
            System.out.println(variable);
        }
    }

    protected FileModel mutate(){

        if(variablesToChange == null){
            return null;
        }

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

    @Override
    public String getMutationName() {
        return "Replaced Variable Name";
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
