package org.example;

import org.example.mutator.MutationRunResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MutationRunResultTest {

    @Test
    void initiallyEmpty() {
        MutationRunResult result = new MutationRunResult();
        assertTrue(result.getGeneratedFiles().isEmpty());
    }

    @Test
    void addGeneratedFile_appendsPath() {
        MutationRunResult result = new MutationRunResult();
        Path p = Paths.get("/tmp/Foo_mutated.java");
        result.addGeneratedFile(p);
        assertEquals(List.of(p), result.getGeneratedFiles());
    }

    @Test
    void addGeneratedFile_multipleFiles_preservesOrder() {
        MutationRunResult result = new MutationRunResult();
        Path p1 = Paths.get("/tmp/A.java");
        Path p2 = Paths.get("/tmp/B.java");
        Path p3 = Paths.get("/tmp/C.java");
        result.addGeneratedFile(p1);
        result.addGeneratedFile(p2);
        result.addGeneratedFile(p3);
        assertEquals(List.of(p1, p2, p3), result.getGeneratedFiles());
    }

    @Test
    void getGeneratedFiles_returnsUnmodifiableView() {
        MutationRunResult result = new MutationRunResult();
        result.addGeneratedFile(Paths.get("/tmp/X.java"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getGeneratedFiles().add(Paths.get("/tmp/Y.java")));
    }
}
