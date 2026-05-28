package org.example.fileWorker;

import org.example.model.FileModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileModelWriter {

    public Path writeTo(FileModel fileModel) throws IOException {
        if (fileModel == null) {
            throw new IllegalArgumentException("FileModel must not be null");
        }

        if (fileModel.getFilePath() == null) {
            throw new IllegalArgumentException("Target path must not be null");
        }

        List<?> lines = fileModel.getLines();
        List<String> content = new ArrayList<>();

        if (lines != null) {
            for (Object line : lines) {
                content.add(line == null ? "" : line.toString());
            }
        }

        Path targetPath = fileModel.getFilePath();

        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }

        Files.write(targetPath, content, StandardCharsets.UTF_8);

        return targetPath;
    }

    public Path writeUsingModelFileName(FileModel fileModel) throws IOException {
        if (fileModel == null) {
            throw new IllegalArgumentException("FileModel must not be null");
        }

        if (fileModel.getFilePath() == null) {
            throw new IllegalArgumentException("Target path must not be null");
        }

        return writeTo(fileModel);
    }
}
