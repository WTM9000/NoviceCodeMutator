package org.example.mutator;

import org.example.model.FileModel;
import org.example.neo4j.NodeRepository;

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

    public FileModel execute(){
        FileModel newFile = mutate();
        //Запихать сюда событие, если надо
        return newFile;
    }

    public abstract String getMutationName();
}
