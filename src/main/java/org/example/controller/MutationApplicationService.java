package org.example.controller;

import org.example.fileWorker.FileModelWriter;
import org.example.git.GitService;
import org.example.model.FileModel;
import org.example.mutator.*;
import org.example.neo4j.Neo4jConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MutationApplicationService {

    private final MutationOperatorFactory mutationOperatorFactory = new MutationOperatorFactory();
    private final FileModelWriter fileModelWriter = new FileModelWriter();

    public List<String> listCandidateFiles(Path workdir) throws IOException {
        if (workdir == null || !Files.exists(workdir) || !Files.isDirectory(workdir)) {
            throw new IllegalArgumentException("Bad working directory: " + workdir);
        }

        try (Stream<Path> stream = Files.walk(workdir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(workdir::relativize)
                    .map(Path::toString)
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }
    }

    public MutationRunResult run(MutationRunRequest request, Consumer<String> logger) {
        MutationRunResult result = new MutationRunResult();

        try {
            validateRequest(request);

            Path workdir = request.getWorkdir();
            logger.accept("Working directory: " + workdir);
            logger.accept("Number of picked files: " + request.getSelectedFiles().size());

            GitService gitService = null;
            if (!isBlank(request.getGithubUsername()) && !isBlank(request.getGithubToken())) {
                gitService = new GitService(workdir.toFile(), request.getGithubUsername(), request.getGithubToken());

                if (!gitService.isGitRepository() && !isBlank(request.getRepoName())) {
                    logger.accept("Git-repository not found. Initialising remote repository...");
                    gitService.initializeAndCreateRemote(request.getRepoName());
                }
            } else {
                logger.accept("GitHub credentials not set. Commit/push will be skipped.");
            }

            List<String> generatedFileNames = new ArrayList<>();

            for (String selectedFile : request.getSelectedFiles()) {
                logger.accept("========================================");
                logger.accept("File processing begin: " + selectedFile);

                Path absolutePath = request.getWorkdir().resolve(selectedFile).normalize();
                FileModel currentFile = readFileModel(absolutePath);

                logger.accept("File uploaded: " + currentFile.getFileName());

                for (MutationType mutationType : request.getSelectedMutations()) {
                    logger.accept("Applying mutation " + mutationType.value() + " to file " + currentFile.getFileName());

                    uploadToDbIfNeeded(currentFile, logger);

                    FileModel mutated = applyMutationWithRepository(currentFile, mutationType, request);

                    if (mutated == null) {
                        logger.accept("Mutation " + mutationType.value() + " gave no result for file " + currentFile.getFileName());
                        continue;
                    }

                    Path writtenPath = fileModelWriter.writeUsingModelFileName(mutated);
                    logger.accept("New file saved: " + writtenPath);

                    result.addGeneratedFile(writtenPath);
                    generatedFileNames.add(mutated.getFileName());

                    currentFile = mutated;
                }

                logger.accept("File process complete: " + selectedFile);
            }

            if (gitService != null && !generatedFileNames.isEmpty()) {
                String commitMessage = generateCommitMessage(generatedFileNames);
                logger.accept("Creating commit...");
                gitService.addCommitAndPushAll(commitMessage);
                logger.accept("Commit и push complete.");
            } else {
                logger.accept("Commit/push skipped.");
            }

            return result;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

//    private FileModel applyMutation(FileModel fileModel,
//                                    MutationType mutationType,
//                                    MutationRunRequest request,
//                                    Consumer<String> logger) {
//        switch (mutationType) {
//            case VARIABLE_NAME_REPLACE:
//                logger.accept("Mutation " + mutationType.value() + " временно отключена: сейчас пропущены запросы к БД.");
//                logger.accept("UI и пайплайн готовы, подключение можно включить позже без переделки формы.");
//                return null;
//            default:
//                logger.accept("Неизвестная мутация: " + mutationType.value());
//                return null;
//        }
//    }

    private FileModel applyMutationWithRepository(FileModel fileModel,
                                                  MutationType mutationType,
                                                  MutationRunRequest request) throws Exception {
        Neo4jConfig config = new Neo4jConfig(
                "bolt://localhost:7687",
                "neo4j",
                "password"
        );

        MutationOperator operator = mutationOperatorFactory.create(
                mutationType,
                fileModel,
                config,
                request.getNewVariableName()
        );

        return operator.execute();
    }

    private void uploadToDbIfNeeded(FileModel fileModel, Consumer<String> logger) {
        logger.accept("Converting and uploading file to DB: " + fileModel.getFileName());

        Path executable = Path.of("cpg-neo4j", "bin", "cpg-neo4j.bat");
        Path filePath = fileModel.getFilePath().toAbsolutePath().normalize();

        List<String> command = List.of(
                executable.toString(),
                filePath.toString()
        );

        logger.accept("Command: " + String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);

        try {
            Process process = processBuilder.start();

            Thread stdoutThread = new Thread(() -> readProcessStream(process.getInputStream(), "[cpg-neo4j][OUT]", logger));
            Thread stderrThread = new Thread(() -> readProcessStream(process.getErrorStream(), "[cpg-neo4j][ERR]", logger));

            stdoutThread.start();
            stderrThread.start();

            int exitCode = process.waitFor();

            stdoutThread.join();
            stderrThread.join();

            if (exitCode != 0) {
                throw new IllegalStateException("Command cpg-neo4j ended with code: " + exitCode);
            }

            logger.accept("CPG successfully uploaded to Neo4J: " + fileModel.getFileName());
        } catch (Exception ex) {
            throw new RuntimeException("Error while uploading CPG to DB for file " + fileModel.getFileName(), ex);
        }
    }

    private void readProcessStream(InputStream inputStream, String prefix, Consumer<String> logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.accept(prefix + " " + line);
            }
        } catch (IOException e) {
            logger.accept(prefix + " Error while reading process thread: " + e.getMessage());
        }
    }

    private FileModel readFileModel(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return new FileModel(path.getFileName().toString(), path, lines);
    }

    private void validateRequest(MutationRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.getWorkdir() == null || !Files.exists(request.getWorkdir()) || !Files.isDirectory(request.getWorkdir())) {
            throw new IllegalArgumentException("Bad working directory: " + request.getWorkdir());
        }
        if (request.getSelectedFiles() == null || request.getSelectedFiles().isEmpty()) {
            throw new IllegalArgumentException("No files picked.");
        }
        if (request.getSelectedMutations() == null || request.getSelectedMutations().isEmpty()) {
            throw new IllegalArgumentException("No mutations picked.");
        }
    }

    private String generateCommitMessage(List<String> generatedFiles) {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern("ss:mm:HH"));
        String date = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Date: %s, Time: %s%nFiles generated:%n", date, time));

        for (int i = 0; i < generatedFiles.size(); i++) {
            sb.append(" ").append(generatedFiles.get(i));
            if (i < generatedFiles.size() - 1) {
                sb.append(",").append(System.lineSeparator());
            }
        }

        return sb.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}