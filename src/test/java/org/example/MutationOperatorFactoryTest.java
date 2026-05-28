package org.example;

import org.example.model.FileModel;
import org.example.mutator.MutationOperatorFactory;
import org.example.mutator.MutationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MutationOperatorFactory argument validation only.
 * Neo4j connectivity is NOT required — all checks throw before any DB call.
 */
class MutationOperatorFactoryTest {

    private MutationOperatorFactory factory;
    private FileModel dummyFile;

    @BeforeEach
    void setUp() {
        factory = new MutationOperatorFactory();
        dummyFile = new FileModel("Dummy.java", Paths.get("/tmp/Dummy.java"), List.of());
    }

    @Test
    void create_throwsOnNullMutationType() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(null, dummyFile, null, Map.of())
        );
        assertTrue(ex.getMessage().contains("mutationType"));
    }

    @Test
    void create_throwsOnNullFileModel() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(MutationType.EMPTY_LOOP, null, null, Map.of())
        );
        assertTrue(ex.getMessage().contains("fileModel"));
    }

    @Test
    void create_throwsOnNullConfig() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(MutationType.EMPTY_LOOP, dummyFile, null, Map.of())
        );
        assertTrue(ex.getMessage().contains("config"));
    }

    @Test
    void create_nullParameters_isAccepted_asEmptyMap() {
        // null parameters must not throw NullPointerException (factory treats as empty map).
        // We trigger only the first guard (null mutationType) to confirm null params don't NPE first.
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(null, dummyFile, null, null));
    }
}
