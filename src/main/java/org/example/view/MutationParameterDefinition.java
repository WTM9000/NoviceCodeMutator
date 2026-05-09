package org.example.view;

public class MutationParameterDefinition {
    private final String key;
    private final String label;
    private final MutationParameterControlType controlType;
    private final String defaultValue;
    private final java.util.List<String> options;

    public MutationParameterDefinition(String key,
                                       String label,
                                       MutationParameterControlType controlType,
                                       String defaultValue,
                                       java.util.List<String> options) {
        this.key = key;
        this.label = label;
        this.controlType = controlType;
        this.defaultValue = defaultValue;
        this.options = options;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public MutationParameterControlType getControlType() {
        return controlType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public java.util.List<String> getOptions() {
        return options;
    }
}
