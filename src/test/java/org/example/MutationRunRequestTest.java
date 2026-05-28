package org.example;

import org.example.mutator.MutationRunRequest;
import org.example.mutator.MutationType;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MutationRunRequestTest {

    private MutationRunRequest build() {
        Map<MutationType, Map<String, String>> params = Map.of(
                MutationType.VARIABLE_NAME_REPLACE, Map.of("newVariableName", "renamed")
        );
        return new MutationRunRequest(
                Paths.get("/workdir"),
                "user",
                "token123",
                "myRepo",
                List.of("File1.java", "File2.java"),
                List.of(MutationType.FOR_TO_WHILE, MutationType.VARIABLE_NAME_REPLACE),
                params
        );
    }

    @Test
    void getters_returnCorrectValues() {
        MutationRunRequest req = build();
        assertEquals(Paths.get("/workdir"), req.getWorkdir());
        assertEquals("user", req.getGithubUsername());
        assertEquals("token123", req.getGithubToken());
        assertEquals("myRepo", req.getRepoName());
        assertEquals(List.of("File1.java", "File2.java"), req.getSelectedFiles());
        assertEquals(List.of(MutationType.FOR_TO_WHILE, MutationType.VARIABLE_NAME_REPLACE),
                req.getSelectedMutations());
    }

    @Test
    void getMutationParameter_returnsValueForKnownKey() {
        MutationRunRequest req = build();
        Map<String, String> parameters = req.getMutationParameters().getOrDefault(MutationType.VARIABLE_NAME_REPLACE, Map.of());
        assertEquals("renamed",
                parameters.get("newVariableName"));
    }

    @Test
    void getMutationParameter_returnsNullForUnknownMutationType() {
        MutationRunRequest req = build();
        Map<String, String> parameters = req.getMutationParameters().getOrDefault(MutationType.EMPTY_LOOP, Map.of());
        assertNull(parameters.get("anyKey"));
    }

    @Test
    void getMutationParameter_returnsNullForUnknownParamKey() {
        MutationRunRequest req = build();
        Map<String, String> parameters = req.getMutationParameters().getOrDefault(MutationType.VARIABLE_NAME_REPLACE, Map.of());
        assertNull(parameters.get("nonExistent"));
    }
}
