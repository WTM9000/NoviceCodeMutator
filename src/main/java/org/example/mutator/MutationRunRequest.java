package org.example.mutator;

import org.example.mutator.MutationType;

import java.nio.file.Path;
import java.util.List;

public class MutationRunRequest {

    private final Path workdir;
    private final String githubUsername;
    private final String githubToken;
    private final String repoName;
    private final String newVariableName;
    private final List<String> selectedFiles;
    private final List<MutationType> selectedMutations;

    public MutationRunRequest(Path workdir,
                              String githubUsername,
                              String githubToken,
                              String repoName,
                              String newVariableName,
                              List<String> selectedFiles,
                              List<MutationType> selectedMutations) {
        this.workdir = workdir;
        this.githubUsername = githubUsername;
        this.githubToken = githubToken;
        this.repoName = repoName;
        this.newVariableName = newVariableName;
        this.selectedFiles = selectedFiles;
        this.selectedMutations = selectedMutations;
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

    public String getNewVariableName() {
        return newVariableName;
    }

    public List<String> getSelectedFiles() {
        return selectedFiles;
    }

    public List<MutationType> getSelectedMutations() {
        return selectedMutations;
    }
}
