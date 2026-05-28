package org.example;

import org.example.model.FileModel;
import org.example.mutator.MutationOperator;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MutationOperator.buildNewName via a minimal concrete subclass.
 */
class BuildNewNameTest {

    private static class StubMutation extends MutationOperator {
        StubMutation() {
            super(new FileModel("Stub.java", Paths.get("/tmp/Stub.java"), List.of()), null);
        }
        @Override protected FileModel mutate() { return null; }
        @Override protected void getRelevantNodes() {}
        @Override public String getMutationName() { return "Stub"; }

        public String exposeBuildNewName(String oldName, String suffix) {
            return buildNewName(oldName, suffix);
        }
    }

    private final StubMutation stub = new StubMutation();

    @Test
    void buildNewName_insertsBeforeExtension() {
        assertEquals("MyFile_foo.java", stub.exposeBuildNewName("MyFile.java", "foo"));
    }

    @Test
    void buildNewName_worksWithMultipleDotsInName() {
        assertEquals("My.File_foo.java", stub.exposeBuildNewName("My.File.java", "foo"));
    }

    @Test
    void buildNewName_appendsSuffixWhenNoExtension() {
        assertEquals("MyFile_foo", stub.exposeBuildNewName("MyFile", "foo"));
    }

    @Test
    void buildNewName_handlesHiddenFile() {
        assertEquals(".gitignore_foo", stub.exposeBuildNewName(".gitignore", "foo"));
    }

    @Test
    void buildNewName_emptyMutationSuffix() {
        assertEquals("A_.java", stub.exposeBuildNewName("A.java", ""));
    }
}
