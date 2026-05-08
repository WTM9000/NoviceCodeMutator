package org.example;

import org.example.view.MutationWorkbenchFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {

    public static final String NEO4J_URI = "bolt://localhost:7687";
    public static final String NEO4J_USERNAME = "neo4j";
    public static final String NEO4J_PASSWORD = "password";

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            MutationWorkbenchFrame frame = new MutationWorkbenchFrame();
            frame.setVisible(true);
        });
    }
}