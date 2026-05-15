package org.example.mutator;

import org.example.model.FileModel;
import org.example.neo4j.repository.NodeRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class MutationOperator {


    protected FileModel originalFile;
    protected NodeRepository repo;

    protected MutationOperator(FileModel originalFile, NodeRepository repo){
        this.originalFile = originalFile;
        this.repo = repo;
    }

    public FileModel getOriginalFile() {
        return originalFile;
    }

    protected abstract FileModel mutate();

    protected abstract void getRelevantNodes();

    public FileModel execute(){
        getRelevantNodes();
        FileModel newFile = mutate();
        //Запихать сюда событие, если надо
        return newFile;
    }

    public abstract String getMutationName();

    protected String buildNewName(String oldName, String mutationName) {
        int lastDot = oldName.lastIndexOf('.');
        String suffix = "_" + mutationName;

        if (lastDot > 0) {
            return oldName.substring(0, lastDot) + suffix + oldName.substring(lastDot);
        }

        return oldName + suffix;
    }
}
