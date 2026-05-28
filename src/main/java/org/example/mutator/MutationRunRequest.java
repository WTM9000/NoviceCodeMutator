package org.example.mutator;

import org.example.mutator.MutationType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MutationRunRequest {

    private final Path workdir;
    private final String githubUsername;
    private final String githubToken;
    private final String repoName;
    private final List<String> selectedFiles;
    private final List<MutationType> selectedMutations;
    private final Map<MutationType, Map<String, String>> mutationParameters;

    public MutationRunRequest(Path workdir,
                              String githubUsername,
                              String githubToken,
                              String repoName,
                              List<String> selectedFiles,
                              List<MutationType> selectedMutations,
                              Map<MutationType, Map<String, String>> mutationParameters) {
        this.workdir = workdir;
        this.githubUsername = githubUsername;
        this.githubToken = githubToken;
        this.repoName = repoName;
        this.selectedFiles = selectedFiles;
        this.selectedMutations = selectedMutations;
        this.mutationParameters = mutationParameters;
    }

    public Path getWorkdir() {
        return workdir;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public String getGithubToken() {
        return githubToken;
    }

    public String getRepoName() {
        return repoName;
    }

    public List<String> getSelectedFiles() {
        return selectedFiles;
    }

    public List<MutationType> getSelectedMutations() {
        return selectedMutations;
    }

    public Map<MutationType, Map<String, String>> getMutationParameters() {
        return mutationParameters;
    }
}