package org.example;

import org.example.model.FileModel;
import org.example.mutator.MutationOperator;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MutationOperator.execute() template method flow.
 */
class MutationOperatorExecuteTest {

    private static class RecordingMutation extends MutationOperator {
        boolean relevantNodesCalled = false;
        boolean mutateCalled = false;
        private final FileModel returnVal;

        RecordingMutation(FileModel original, FileModel returnVal) {
            super(original, null);
            this.returnVal = returnVal;
        }

        @Override
        protected void getRelevantNodes() { relevantNodesCalled = true; }

        @Override
        protected FileModel mutate() {
            mutateCalled = true;
            return returnVal;
        }

        @Override
        public String getMutationName() { return "Recording"; }
    }

    @Test
    void execute_callsGetRelevantNodesThenMutate() {
        FileModel orig = new FileModel("Orig.java", Paths.get("/tmp/Orig.java"), List.of());
        FileModel mutated = new FileModel("Mutated.java", Paths.get("/tmp/Mutated.java"), List.of());
        RecordingMutation op = new RecordingMutation(orig, mutated);

        FileModel result = op.execute();

        assertTrue(op.relevantNodesCalled, "getRelevantNodes must be called first");
        assertTrue(op.mutateCalled, "mutate must be called after getRelevantNodes");
        assertSame(mutated, result);
    }

    @Test
    void execute_returnsNullWhenMutateReturnsNull() {
        FileModel orig = new FileModel("Orig.java", Paths.get("/tmp/Orig.java"), List.of());
        RecordingMutation op = new RecordingMutation(orig, null);

        assertNull(op.execute());
    }

    @Test
    void getOriginalFile_returnsConstructorValue() {
        FileModel orig = new FileModel("Orig.java", Paths.get("/tmp/Orig.java"), List.of("a"));
        RecordingMutation op = new RecordingMutation(orig, null);

        assertSame(orig, op.getOriginalFile());
    }
}
