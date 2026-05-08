package org.example.fileWorker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppConfig {
    private final String githubToken;
    private final String githubUsername;
    private final String workdir;
    private final String repoName;

    public AppConfig(String githubToken, String githubUsername, String workdir, String repoName) {
        this.githubToken = githubToken;
        this.githubUsername = githubUsername;
        this.workdir = workdir;
        this.repoName = repoName;
    }

    public String getGithubToken() {
        return githubToken;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public String getWorkdir() {
        return workdir;
    }

    public String getRepoName() {
        return repoName;
    }

    public static AppConfig loadFromDefaultLocation() throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path configPath = projectRoot.resolve("config.txt");
        return load(configPath);
    }

    public static AppConfig load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            throw new IOException("config.txt not found at: " + configPath.toAbsolutePath());
        }

        List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();

        for (String raw : lines) {
            String line = raw.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }

            String key = line.substring(0, eq).trim().toLowerCase();
            String value = line.substring(eq + 1).trim();
            values.put(key, value);
        }

        return new AppConfig(
                values.getOrDefault("github_token", ""),
                values.getOrDefault("github_username", ""),
                values.getOrDefault("workdir", ""),
                values.getOrDefault("repo_name", "")
        );
    }
}
