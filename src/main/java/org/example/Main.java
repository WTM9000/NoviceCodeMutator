package org.example;

import org.example.git.GitService;
import org.example.model.FileModel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    // Global (hardcoded) Neo4j connection placeholders - currently stubs as requested.
    // NOTE: also present inside CpgUploader; keep these here as global placeholders per task.
    public static final String NEO4J_URI = "bolt://localhost:7687";
    public static final String NEO4J_USERNAME = "neo4j";
    public static final String NEO4J_PASSWORD = "password";

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // Load configuration from config.txt in project root
        Path projectRoot = Path.of(System.getProperty("user.dir")); // project working dir
        Path configPath = projectRoot.resolve("config.txt");

        Map<String, String> cfg;
        try {
            cfg = loadConfig(configPath);
        } catch (IOException e) {
            System.err.println("Failed to read config file at " + configPath.toAbsolutePath() + ": " + e.getMessage());
            return;
        }

        String GITHUB_TOKEN = cfg.getOrDefault("github_token", "").trim();
        String GITHUB_USERNAME = cfg.getOrDefault("github_username", "").trim();
        String WORKDIR = cfg.getOrDefault("workdir", "").trim();

        // Basic validation of configs
        if (GITHUB_TOKEN == null || GITHUB_TOKEN.isBlank()) {
            System.out.println("GITHUB_TOKEN is not set in config.txt. Please set GITHUB_TOKEN and restart.");
            return;
        }
        if (WORKDIR == null || WORKDIR.isBlank()) {
            System.out.println("WORKDIR is not set in config.txt. Please set WORKDIR and restart.");
            return;
        }
        // Optional: if username not provided, ask interactively
        if (GITHUB_USERNAME == null || GITHUB_USERNAME.isBlank()) {
            System.out.print("GITHUB_USERNAME not set in config.txt. Please enter GitHub username: ");
            GITHUB_USERNAME = sc.nextLine().trim();
            if (GITHUB_USERNAME.isEmpty()) {
                System.out.println("GITHUB_USERNAME cannot be empty. Exiting.");
                return;
            }
        }

        File workdir = new File(WORKDIR);
        if (!workdir.exists() || !workdir.isDirectory()) {
            System.out.println("WORKDIR path does not exist or is not a directory: " + WORKDIR);
            return;
        }

        try {
            GitService gitService = new GitService(workdir, GITHUB_USERNAME, GITHUB_TOKEN);

            if (!gitService.isGitRepository()) {
                // prompt for repo name
                System.out.print("No git repository found in workdir. Enter repository name to create on GitHub: ");
                String repoName = sc.nextLine().trim();
                if (repoName.isEmpty()) {
                    System.out.println("Repository name cannot be empty. Exiting.");
                    return;
                }
                gitService.initializeAndCreateRemote(repoName);
            } else {
                System.out.println("Git repository detected in workdir. Skipping initialization.");
            }

            // Simple menu loop
            while (true) {
                System.out.println("\nChoose an option:\n1. сохранить\n2. выйти");
                System.out.print("> ");
                String opt = sc.nextLine().trim();
                if ("1".equals(opt)) {
                    // New behavior: prompt for comma-separated filenames to process into CPG and upload to Neo4j
                    while (true) {
                        System.out.println("Enter comma-separated filenames to process from dataset (e.g. file1.c,file2.c), or press Enter to cancel:");
                        System.out.print("> ");
                        String filesLine = sc.nextLine().trim();
                        if (filesLine.isEmpty()) {
                            // Return to main menu
                            System.out.println("No files specified. Returning to main menu.");
                            break;
                        }
                        // Parse filenames, trim whitespace
                        List<String> requested = Stream.of(filesLine.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
                        if (requested.isEmpty()) {
                            System.out.println("No valid filenames entered. Please try again.");
                            continue;
                        }

                        // Attempt to find each requested file in workdir (recursively)
                        Map<String, Path> found = new HashMap<>();
                        for (String name : requested) {
                            Path match = findFileRecursive(workdir.toPath(), name);
                            if (match != null) {
                                found.put(name, match);
                            }
                        }

                        if (found.size() != requested.size()) {
                            System.out.println("One or more files were not found in the dataset. Please check names and try again.");
                            // Show which files weren't found
                            List<String> notFound = requested.stream()
                                    .filter(n -> !found.containsKey(n))
                                    .collect(Collectors.toList());
                            System.out.println("Not found: " + String.join(", ", notFound));
                            // Re-prompt for filenames
                            continue;
                        }

                        // Build FileModel instances by reading file content
                        List<FileModel> models = new ArrayList<>();
                        boolean readFailed = false;
                        for (Map.Entry<String, Path> e : found.entrySet()) {
                            Path p = e.getValue();
                            try {
                                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                                models.add(new FileModel(e.getKey(), p, lines));
                            } catch (IOException ioEx) {
                                readFailed = true;
                                System.err.println("Failed to read file " + p + ": " + ioEx.getMessage());
                                break;
                            }
                        }
                        if (readFailed) {
                            System.out.println("Failed to read files. Please try again.");
                            continue;
                        }

                        // Convert each model to CPG and upload to Neo4j
                        try (org.example.CpgUploader uploader = new org.example.CpgUploader()) {
                            for (FileModel fm : models) {
                                System.out.println("Processing file: " + fm.getFileName());
                                try {
                                    uploader.convertAndUpload(fm);
                                    System.out.println("Uploaded CPG for " + fm.getFileName() + " to Neo4j (" + NEO4J_URI + ").");
                                } catch (Exception ex) {
                                    System.err.println("Failed to convert/upload file " + fm.getFileName() + ": " + ex.getMessage());
                                    ex.printStackTrace();
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Error initializing CPG uploader: " + e.getMessage());
                            e.printStackTrace();
                        }

                        // After successful conversion/upload, create commit and push
                        try {
                            String commitMessage = generateNewCommitMessage(requested);
                            System.out.println("Creating commit with message: " + commitMessage);
                            gitService.addCommitAndPushAll(commitMessage);
                            System.out.println("Committed and pushed.");
                        } catch (Exception ex) {
                            System.err.println("Failed to commit/push: " + ex.getMessage());
                            ex.printStackTrace();
                        }

                        // After operation go back to main menu
                        break;
                    }
                } else if ("2".equals(opt)) {
                    System.out.println("Exiting.");
                    break;
                } else {
                    System.out.println("Unknown option. Please enter 1 or 2.");
                }
            }

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads key=value pairs from config file. Ignores lines starting with '#' and blank lines.
     * Keys are lowercased in the returned map.
     */
    private static Map<String, String> loadConfig(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            throw new IOException("config.txt not found at: " + configPath.toAbsolutePath());
        }
        List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue; // skip malformed
            }
            String key = line.substring(0, eq).trim().toLowerCase();
            String value = line.substring(eq + 1).trim();
            map.put(key, value);
        }
        return map;
    }

    /**
     * Generates commit message using current time and date.
     * Note: formats per new requirement:
     * "Дата: дд.мм.гггг, время: сс:мм:чч \n Обработаны файлы: f1, \n f2, \n ..."
     */
    private static String generateNewCommitMessage(List<String> processedFiles) {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern("ss:mm:HH"));
        String date = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Дата: %s, время: %s%n Обработаны файлы:", date, time));
        for (int i = 0; i < processedFiles.size(); i++) {
            sb.append(" ").append(processedFiles.get(i));
            if (i < processedFiles.size() - 1) {
                sb.append(",").append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    /**
     * Recursively searches rootDir for a file with the exact name fileName.
     * Returns the first match found, or null if none.
     */
    private static Path findFileRecursive(Path rootDir, String fileName) {
        try (Stream<Path> stream = Files.walk(rootDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Builds a target path for the copy placed in the same directory as original with "_copy" appended
     * before the extension. E.g. /path/to/file1.a -> /path/to/file1_copy.a
     * If the file has no extension, "_copy" is appended to the filename.
     *
     * Kept for compatibility but not used in current flow.
     */
    private static Path buildCopyPath(Path original) {
        String filename = original.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        String newName;
        if (lastDot > 0) {
            String base = filename.substring(0, lastDot);
            String ext = filename.substring(lastDot); // includes dot
            newName = base + "_copy" + ext;
        } else {
            newName = filename + "_copy";
        }
        return original.getParent().resolve(newName);
    }
}