package org.example.fileWorker;

import org.example.model.FileModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileManager {

    public Path writeTo(FileModel fileModel) throws IOException {
        if (fileModel.getFilePath() == null) {
            throw new IllegalArgumentException("Target path must not be null");
        }

        if (fileModel == null) {
            throw new IllegalArgumentException("FileModel must not be null");
        }

        List<String> lines = fileModel.getLines();
        List<String> content = new ArrayList<>();

        if (lines != null) {
            for (Object line : lines) {
                content.add(line == null ? "" : line.toString());
            }
        }

        if (fileModel.getFilePath().getParent() != null) {
            Files.createDirectories(fileModel.getFilePath().getParent());
        }

        Files.write(fileModel.getFilePath(), content, StandardCharsets.UTF_8);

        return fileModel.getFilePath();
    }

    public Path writeUsingModelFileName(FileModel fileModel) throws IOException {
        if (fileModel.getFilePath() == null) {
            throw new IllegalArgumentException("Target directory must not be null");
        }

        if (fileModel == null) {
            throw new IllegalArgumentException("FileModel must not be null");
        }

        return writeTo(fileModel);
    }

    public void processFileNames(){

    }

    public boolean checkIfFileInDir(){
        return true;
    }
}
