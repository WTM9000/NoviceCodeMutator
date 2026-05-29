package org.example.view;

import org.example.controller.MutationApplicationService;
import org.example.fileWorker.AppConfig;
import org.example.mutator.MutationType;
import org.example.mutator.MutationRunRequest;
import org.example.mutator.MutationRunResult;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MutationWorkbenchFrame extends JFrame {

    private final JTextField workdirField = new JTextField(40);
    private final JTextField githubUsernameField = new JTextField(20);
    private final JTextField githubTokenField = new JPasswordField(20);
    private final JTextField repoNameField = new JTextField(20);

    private final DefaultListModel<String> filesModel = new DefaultListModel<>();
    private final JList<String> filesList = new JList<>(filesModel);

    private final Map<MutationType, JCheckBox> mutationCheckboxes = new LinkedHashMap<>();
    private final Map<MutationType, JPanel> mutationParameterPanels = new LinkedHashMap<>();
    Map<MutationType, List<MutationParameterBinding>> mutationParameterBindings = new LinkedHashMap<>();

    private final JTextArea logArea = new JTextArea();

    private final JButton browseButton = new JButton("Select workdir");
    private final JButton scanButton = new JButton("Scan files");
    private final JButton runButton = new JButton("Start");
    private final JButton clearLogButton = new JButton("Clear log");

    private final MutationApplicationService service = new MutationApplicationService();

    public MutationWorkbenchFrame() {
        super("Mutation Workbench");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 700));
        setLocationRelativeTo(null);

        initUi();
        bindActions();
        loadConfigValuesIntoForm();
    }

    private void initUi() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new GridLayout(0, 1, 8, 8));
        topPanel.add(buildConfigPanel());

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        centerPanel.add(buildFilesPanel());
        centerPanel.add(buildMutationsPanel());

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.add(buildActionsPanel(), BorderLayout.NORTH);
        bottomPanel.add(buildLogPanel(), BorderLayout.CENTER);

        root.add(topPanel, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);
        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Settings"));

        JPanel workdirPanel = new JPanel(new BorderLayout(6, 6));
        workdirPanel.add(new JLabel("Workdir:"), BorderLayout.WEST);
        workdirPanel.add(workdirField, BorderLayout.CENTER);
        workdirPanel.add(browseButton, BorderLayout.EAST);

        JPanel githubPanel = new JPanel(new GridLayout(1, 4, 6, 6));
        githubPanel.add(new JLabel("GitHub username:"));
        githubPanel.add(githubUsernameField);
        githubPanel.add(new JLabel("GitHub token:"));
        githubPanel.add(githubTokenField);

        JPanel repoPanel = new JPanel(new GridLayout(1, 4, 6, 6));
        repoPanel.add(new JLabel("Repo name:"));
        repoPanel.add(repoNameField);

        panel.add(workdirPanel);
        panel.add(githubPanel);
        panel.add(repoPanel);

        return panel;
    }

    private JPanel buildFilesPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Files"));

        filesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        filesList.setVisibleRowCount(18);

        JLabel hintLabel = new JLabel("Possible to select multiple files: Ctrl/Shift + click");
        panel.add(hintLabel, BorderLayout.NORTH);
        panel.add(new JScrollPane(filesList), BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildMutationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Mutations"));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        for (MutationType mutationType : MutationType.values()) {
            JPanel mutationBlock = new JPanel(new BorderLayout(4, 4));
            mutationBlock.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));

            JCheckBox checkBox = new JCheckBox(mutationType.value());
            mutationCheckboxes.put(mutationType, checkBox);
            mutationBlock.add(checkBox, BorderLayout.NORTH);

            JPanel paramsPanel = buildParametersPanelForMutation(mutationType);
            mutationParameterPanels.put(mutationType, paramsPanel);

            paramsPanel.setVisible(false);
            mutationBlock.add(paramsPanel, BorderLayout.CENTER);

            checkBox.addActionListener(e -> paramsPanel.setVisible(checkBox.isSelected()));

            content.add(mutationBlock);
        }

        panel.add(new JScrollPane(content), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildParametersPanelForMutation(MutationType mutationType) {
        List<MutationParameterDefinition> definitions = getParameterDefinitions(mutationType);
        List<MutationParameterBinding> bindings = new ArrayList<>();

        JPanel panel = new JPanel();

        if (definitions.isEmpty()) {
            panel.setLayout(new BorderLayout());
            panel.add(new JLabel("No parameters"), BorderLayout.CENTER);
            mutationParameterBindings.put(mutationType, bindings);
            return panel;
        }

        panel.setLayout(new GridLayout(0, 2, 6, 6));

        for (MutationParameterDefinition definition : definitions) {
            MutationParameterBinding binding = createBinding(definition);
            bindings.add(binding);

            panel.add(new JLabel(definition.getLabel()));
            panel.add(binding.getComponent());
        }

        mutationParameterBindings.put(mutationType, bindings);
        return panel;
    }

    private Map<MutationType, Map<String, String>> collectMutationParameters(List<MutationType> selectedMutations) {
        Map<MutationType, Map<String, String>> result = new LinkedHashMap<>();

        for (MutationType mutationType : selectedMutations) {
            List<MutationParameterBinding> bindings = mutationParameterBindings.getOrDefault(mutationType, List.of());
            Map<String, String> values = new LinkedHashMap<>();

            for (MutationParameterBinding binding : bindings) {
                values.put(binding.getDefinition().getKey(), binding.getValue());
            }

            result.put(mutationType, values);
        }

        return result;
    }

    private MutationParameterBinding createBinding(MutationParameterDefinition definition) {
        JComponent component;

        switch (definition.getControlType()) {
            case TEXT -> {
                JTextField textField = new JTextField(definition.getDefaultValue(), 20);
                component = textField;
            }
            case COMBO -> {
                JComboBox<String> comboBox = new JComboBox<>(definition.getOptions().toArray(new String[0]));
                comboBox.setSelectedItem(definition.getDefaultValue());
                component = comboBox;
            }
            case CHECKBOX -> {
                JCheckBox checkBox = new JCheckBox();
                checkBox.setSelected(Boolean.parseBoolean(definition.getDefaultValue()));
                component = checkBox;
            }
            default -> throw new IllegalStateException("Unsupported control type: " + definition.getControlType());
        }

        return new MutationParameterBinding(definition, component);
    }

    private List<MutationParameterDefinition> getParameterDefinitions(MutationType mutationType) {
        return switch (mutationType) {
            case SYNCHRONIZED_VARIABLES -> List.of(
                    new MutationParameterDefinition(
                            "syncVarX",
                            "First new variable name:",
                            MutationParameterControlType.TEXT,
                            "mutated_x",
                            List.of()
                    ),new MutationParameterDefinition(
                            "syncVarY",
                            "Second new variable name:",
                            MutationParameterControlType.TEXT,
                            "mutated_y",
                            List.of()
                    )

            );
            case VARIABLE_NAME_REPLACE -> List.of(
                    new MutationParameterDefinition(
                            "newVariableName",
                            "New variable name:",
                            MutationParameterControlType.TEXT,
                            "mutated_a",
                            List.of()
                    )
            );
            case CONTINUE_ANTI_IDIOM -> List.of(
                    new MutationParameterDefinition(
                            "addDeadCode",
                            "Add dead code:",
                            MutationParameterControlType.CHECKBOX,
                            "false",
                            List.of()
                    )
            );
            default -> List.of();
        };
    }

    private JPanel buildActionsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(scanButton);
        panel.add(runButton);
        panel.add(clearLogButton);
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Log"));

        logArea.setEditable(false);
        logArea.setRows(15);

        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private void bindActions() {
        browseButton.addActionListener(e -> chooseDirectory());
        scanButton.addActionListener(e -> scanFiles());
        clearLogButton.addActionListener(e -> logArea.setText(""));
        runButton.addActionListener(e -> runProcessing());
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select work directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            workdirField.setText(selected.getAbsolutePath());
        }
    }

    private void scanFiles() {
        String workdir = workdirField.getText().trim();
        if (workdir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select work directory", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        filesModel.clear();

        try {
            List<String> files = service.listCandidateFiles(Path.of(workdir));
            for (String file : files) {
                filesModel.addElement(file);
            }
            appendLog("Scan complete. Found files: " + files.size());
        } catch (Exception ex) {
            appendLog("Scan error: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runProcessing() {
        String workdir = workdirField.getText().trim();
        String githubUsername = githubUsernameField.getText().trim();
        String githubToken = githubTokenField.getText().trim();
        String repoName = repoNameField.getText().trim();

        List<String> selectedFiles = new ArrayList<>(filesList.getSelectedValuesList());
        List<MutationType> selectedMutations = getSelectedMutations();

        if (workdir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select work directory.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (selectedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (selectedMutations.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one mutation.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setControlsEnabled(false);
        appendLog("Start processing...");
        appendLog("Selected files: " + selectedFiles.size());

        Map<MutationType, Map<String, String>> mutationParameters = collectMutationParameters(selectedMutations);

        MutationRunRequest request = new MutationRunRequest(
                Path.of(workdir),
                githubUsername,
                githubToken,
                repoName,
                selectedFiles,
                selectedMutations,
                mutationParameters
        );

        SwingWorker<MutationRunResult, String> worker = new SwingWorker<>() {
            @Override
            protected MutationRunResult doInBackground() {
                return service.run(request, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    appendLog(chunk);
                }
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
                try {
                    MutationRunResult result = get();
                    appendLog("Done. Generated files: " + result.getGeneratedFiles().size());
                } catch (Exception ex) {
                    appendLog("Process error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(MutationWorkbenchFrame.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private List<MutationType> getSelectedMutations() {
        List<MutationType> selected = new ArrayList<>();
        for (Map.Entry<MutationType, JCheckBox> entry : mutationCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    private void setControlsEnabled(boolean enabled) {
        browseButton.setEnabled(enabled);
        scanButton.setEnabled(enabled);
        runButton.setEnabled(enabled);
        clearLogButton.setEnabled(enabled);
        workdirField.setEnabled(enabled);
        githubUsernameField.setEnabled(enabled);
        githubTokenField.setEnabled(enabled);
        repoNameField.setEnabled(enabled);
        filesList.setEnabled(enabled);
        for (JCheckBox checkBox : mutationCheckboxes.values()) {
            checkBox.setEnabled(enabled);
        }
    }

    private void appendLog(String message) {
        logArea.append(message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void loadConfigValuesIntoForm() {
        try {
            AppConfig config = AppConfig.loadFromDefaultLocation();

            if (config.getWorkdir() != null && !config.getWorkdir().isBlank()) {
                workdirField.setText(config.getWorkdir());
            }

            if (config.getGithubUsername() != null && !config.getGithubUsername().isBlank()) {
                githubUsernameField.setText(config.getGithubUsername());
            }

            if (config.getGithubToken() != null && !config.getGithubToken().isBlank()) {
                githubTokenField.setText(config.getGithubToken());
            }

            if (config.getRepoName() != null && !config.getRepoName().isBlank()) {
                repoNameField.setText(config.getRepoName());
            }

            appendLog("Configuration loaded from config.txt");
        } catch (Exception ex) {
            appendLog("Configuration was not loaded: " + ex.getMessage());
        }
    }
}
