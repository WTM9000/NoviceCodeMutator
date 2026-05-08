package org.example.mutator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MutationRunResult {

    private final List<Path> generatedFiles = new ArrayList<>();

    public void addGeneratedFile(Path path) {
        generatedFiles.add(path);
    }

    public List<Path> getGeneratedFiles() {
        return Collections.unmodifiableList(generatedFiles);
    }
}
