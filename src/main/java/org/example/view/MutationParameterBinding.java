package org.example.view;
import javax.swing.*;

public class MutationParameterBinding {
    private final MutationParameterDefinition definition;
    private final JComponent component;

    public MutationParameterBinding(MutationParameterDefinition definition, JComponent component) {
        this.definition = definition;
        this.component = component;
    }

    public MutationParameterDefinition getDefinition() {
        return definition;
    }

    public JComponent getComponent() {
        return component;
    }

    public String getValue() {
        if (component instanceof JTextField textField) {
            return textField.getText().trim();
        }

        if (component instanceof JComboBox<?> comboBox) {
            Object selected = comboBox.getSelectedItem();
            return selected == null ? "" : selected.toString();
        }

        if (component instanceof JCheckBox checkBox) {
            return Boolean.toString(checkBox.isSelected());
        }

        throw new IllegalStateException("Unsupported component type: " + component.getClass().getName());
    }
}
