package org.example;

import org.example.fileWorker.FileModelWriter;
import org.example.model.FileModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileManagerTest {

    private final FileModelWriter manager = new FileModelWriter();

    @Test
    void writeTo_createsFileWithCorrectContent(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("Output.java");
        List<String> lines = Arrays.asList("public class Output {", "}", "");
        FileModel model = new FileModel("Output.java", target, lines);

        Path written = manager.writeTo(model);

        assertEquals(target, written);
        assertTrue(Files.exists(written));
        List<String> read = Files.readAllLines(written, StandardCharsets.UTF_8);
        assertEquals(lines, read);
    }

    @Test
    void writeTo_createsParentDirectoriesIfMissing(@TempDir Path tempDir) throws IOException {
        Path nested = tempDir.resolve("a/b/c/File.java");
        FileModel model = new FileModel("File.java", nested, List.of("// content"));

        manager.writeTo(model);

        assertTrue(Files.exists(nested));
    }

    @Test
    void writeTo_handlesEmptyLineList(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("Empty.java");
        FileModel model = new FileModel("Empty.java", target, List.of());

        manager.writeTo(model);

        assertTrue(Files.exists(target));
        assertEquals(0, Files.readAllLines(target, StandardCharsets.UTF_8).size());
    }

    @Test
    void writeTo_handlesNullLines(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("NullLines.java");
        FileModel model = new FileModel("NullLines.java", target, null);

        manager.writeTo(model);

        assertTrue(Files.exists(target));
        assertEquals(0, Files.readAllLines(target, StandardCharsets.UTF_8).size());
    }

    @Test
    void writeTo_overwritesExistingFile(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("Existing.java");
        Files.writeString(target, "old content");

        FileModel model = new FileModel("Existing.java", target, List.of("new content"));
        manager.writeTo(model);

        List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertEquals("new content", lines.get(0));
    }

    @Test
    void writeUsingModelFileName_delegatesToWriteTo(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("Via.java");
        FileModel model = new FileModel("Via.java", target, List.of("line"));

        Path result = manager.writeUsingModelFileName(model);
        assertEquals(target, result);
        assertTrue(Files.exists(result));
    }
}
