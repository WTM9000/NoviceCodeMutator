package org.example.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * FileModel: represents a file that will be processed into a CPG and uploaded to Neo4j.
 */
public class FileModel {
    private final String fileName;
    private final Path filePath;
    private final List<String> lines;

    public FileModel(String fileName, Path filePath, List<String> lines) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.lines = lines;
    }

    public String getFileName() {
        return fileName;
    }

    public Path getFilePath() {
        return filePath;
    }

    public List<String> getLines() {
        return lines;
    }

    @Override
    public String toString() {
        return "FileModel{" +
                "fileName='" + fileName + '\'' +
                ", filePath=" + filePath +
                ", linesCount=" + (lines == null ? 0 : lines.size()) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileModel fileModel = (FileModel) o;
        return Objects.equals(fileName, fileModel.fileName) &&
                Objects.equals(filePath, fileModel.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, filePath);
    }
}