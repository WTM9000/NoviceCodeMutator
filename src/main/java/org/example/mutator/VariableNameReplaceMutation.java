package org.example.mutator;

import org.example.model.FileModel;
import org.example.model.IfRootNode;
import org.example.model.VariableNode;
import org.example.neo4j.repository.VariableRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VariableNameReplaceMutation extends MutationOperator {

    private List<VariableNode> variablesToChange = null;
    private final Random random = new Random();

    private String newVariableName;

    public VariableNameReplaceMutation(FileModel originalFile, VariableRepository repo, String newVariableName){
        super(originalFile, repo);
        this.originalFile = originalFile;
        this.newVariableName = newVariableName;
    }

    protected void getRelevantNodes(){
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

        VariableNode targetVariable = variables.get(random.nextInt(variables.size()));

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
        List<String> newLines = new ArrayList<>(originalFile.getLines());

        for(VariableNode variable: variablesToChange){
            String stringToChange = newLines.get(variable.getLine()-1);

            int oldVariableStart = variable.getColumn()-1;
            int oldVariableEnd = oldVariableStart + variable.getName().length();

            StringBuilder newString = new StringBuilder(stringToChange).delete(oldVariableStart, oldVariableEnd);
            newString.insert(oldVariableStart, newVariableName);

            newLines.set(variable.getLine()-1, newString.toString());
        }

        String newName = buildNewName(originalFile.getFileName(), "_varname_replace");

        Path newPath = originalFile.getFilePath().getParent().resolve(newName);

        mutatedFile = new FileModel(newName, newPath, newLines);

        return mutatedFile;
    }

    @Override
    public String getMutationName() {
        return "Replaced Variable Name";
    }
}
