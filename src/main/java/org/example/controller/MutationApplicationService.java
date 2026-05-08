package org.example.controller;

import org.example.fileWorker.FileModelWriter;
import org.example.git.GitService;
import org.example.model.FileModel;
import org.example.mutator.*;
import org.example.neo4j.Neo4jConfig;
import org.example.neo4j.VariableRepository;

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
            throw new IllegalArgumentException("Некорректная рабочая папка: " + workdir);
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
            logger.accept("Рабочая папка: " + workdir);

            GitService gitService = null;
            if (!isBlank(request.getGithubUsername()) && !isBlank(request.getGithubToken())) {
                gitService = new GitService(workdir.toFile(), request.getGithubUsername(), request.getGithubToken());

                if (!gitService.isGitRepository() && !isBlank(request.getRepoName())) {
                    logger.accept("Git-repository not found. Initializing remote repo...");
                    gitService.initializeAndCreateRemote(request.getRepoName());
                }
            } else {
                logger.accept("GitHub credentials not set. Skip credentials...");
            }

            List<String> generatedFileNames = new ArrayList<>();

            for (String selectedFile : request.getSelectedFiles()) {
                Path absolutePath = workdir.resolve(selectedFile).normalize();
                logger.accept("Reading file... " + absolutePath);

                FileModel currentFile = readFileModel(absolutePath);

                for (MutationType mutationType : request.getSelectedMutations()) {
                    logger.accept("Preparing to mutate... " + mutationType.value());

                    uploadToDbIfNeeded(currentFile, logger);

                    FileModel mutated = applyMutationWithRepository(currentFile, mutationType, request);
                    if (mutated == null) {
                        logger.accept("Mutatuion  " + mutationType.value() + " skipped for file " + currentFile.getFileName());
                        continue;
                    }

                    Path writtenPath = fileModelWriter.writeUsingModelFileName(mutated);
                    logger.accept("File saved: " + writtenPath);

                    result.addGeneratedFile(writtenPath);
                    generatedFileNames.add(mutated.getFileName());

                    currentFile = mutated;
                }
            }

            if (gitService != null && !generatedFileNames.isEmpty()) {
                String commitMessage = generateCommitMessage(generatedFileNames);
                logger.accept("Create commit: " + commitMessage.replace(System.lineSeparator(), " | "));
                gitService.addCommitAndPushAll(commitMessage);
                logger.accept("Commit and push complete.");
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

        try (VariableRepository repository = new VariableRepository(config)) {
            MutationOperator operator = mutationOperatorFactory.create(
                    mutationType,
                    fileModel,
                    repository,
                    request.getNewVariableName()
            );
            return operator.execute();
        }
    }

    private void uploadToDbIfNeeded(FileModel fileModel, Consumer<String> logger) {
        logger.accept("Загрузка CPG в БД для файла: " + fileModel.getFileName());

        Path executable = Path.of("cpg-neo4j", "bin", "cpg-neo4j.bat");
        Path filePath = fileModel.getFilePath().toAbsolutePath().normalize();

        List<String> command = List.of(
                executable.toString(),
                filePath.toString()
        );

        logger.accept("Команда: " + String.join(" ", command));

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
                throw new IllegalStateException("Команда cpg-neo4j завершилась с кодом: " + exitCode);
            }

            logger.accept("CPG успешно загружен в Neo4j для файла: " + fileModel.getFileName());
        } catch (Exception ex) {
            throw new RuntimeException("Ошибка загрузки CPG в БД для файла " + fileModel.getFileName(), ex);
        }
    }

    private void readProcessStream(InputStream inputStream, String prefix, Consumer<String> logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.accept(prefix + " " + line);
            }
        } catch (IOException e) {
            logger.accept(prefix + " Ошибка чтения потока процесса: " + e.getMessage());
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
            throw new IllegalArgumentException("Некорректная рабочая папка: " + request.getWorkdir());
        }
        if (request.getSelectedFiles() == null || request.getSelectedFiles().isEmpty()) {
            throw new IllegalArgumentException("Не выбраны файлы.");
        }
        if (request.getSelectedMutations() == null || request.getSelectedMutations().isEmpty()) {
            throw new IllegalArgumentException("Не выбраны мутации.");
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