package org.example;

import org.example.model.FileModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileModelTest {

    private FileModel buildModel(String name, String pathStr, List<String> lines) {
        return new FileModel(name, Paths.get(pathStr), lines);
    }

    @Test
    void constructor_storesAllFields() {
        List<String> lines = Arrays.asList("line1", "line2");
        FileModel m = buildModel("Sample.java", "/tmp/Sample.java", lines);

        assertEquals("Sample.java", m.getFileName());
        assertEquals(Paths.get("/tmp/Sample.java"), m.getFilePath());
        assertEquals(lines, m.getLines());
    }

    @Test
    void setFileName_updatesNameAndResolvesNewPath() {
        FileModel m = buildModel("Old.java", "/tmp/Old.java", List.of());
        m.setFileName("New.java");

        assertEquals("New.java", m.getFileName());
        assertEquals(Paths.get("/tmp/New.java"), m.getFilePath());
    }

    @Test
    void equals_trueForSameNameAndPath() {
        FileModel a = buildModel("A.java", "/tmp/A.java", List.of("x"));
        FileModel b = buildModel("A.java", "/tmp/A.java", List.of("y", "z"));
        assertEquals(a, b);
    }

    @Test
    void equals_falseForDifferentName() {
        FileModel a = buildModel("A.java", "/tmp/A.java", List.of());
        FileModel b = buildModel("B.java", "/tmp/A.java", List.of());
        assertNotEquals(a, b);
    }

    @Test
    void equals_falseForDifferentPath() {
        FileModel a = buildModel("A.java", "/tmp/A.java", List.of());
        FileModel b = buildModel("A.java", "/other/A.java", List.of());
        assertNotEquals(a, b);
    }

    @Test
    void hashCode_equalForEqualObjects() {
        FileModel a = buildModel("A.java", "/tmp/A.java", List.of("1"));
        FileModel b = buildModel("A.java", "/tmp/A.java", List.of("2"));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toString_containsFileNameAndLinesCount() {
        FileModel m = buildModel("Test.java", "/tmp/Test.java",
                Arrays.asList("a", "b", "c"));
        String s = m.toString();
        assertTrue(s.contains("Test.java"));
        assertTrue(s.contains("3"));
    }

    @Test
    void toString_handlesNullLines() {
        FileModel m = new FileModel("X.java", Paths.get("/tmp/X.java"), null);
        String s = m.toString();
        assertTrue(s.contains("0"));
    }

    @Test
    void getLines_returnsReferenceToOriginalList() {
        List<String> lines = Arrays.asList("a", "b");
        FileModel m = buildModel("F.java", "/tmp/F.java", lines);
        assertSame(lines, m.getLines());
    }
}
