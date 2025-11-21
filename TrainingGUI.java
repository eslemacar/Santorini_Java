import javax.swing.*;
import java.awt.*;

public class TrainingGUI extends JFrame {

    private JTextArea logArea;
    private JProgressBar progressBar;
    private JButton startButton;

    public TrainingGUI() {
        setTitle("Santorini – KI Training");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        // --- Log Fenster ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);
        add(scroll, BorderLayout.CENTER);

        // --- Untere Leiste mit Fortschritt + Button ---
        JPanel bottom = new JPanel(new BorderLayout());

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottom.add(progressBar, BorderLayout.CENTER);

        startButton = new JButton("KI trainieren");
        bottom.add(startButton, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);

        startButton.addActionListener(e -> startTraining());
    }



    /** Startet Training in einem Hintergrundthread (damit GUI nicht hängt) */
    private void startTraining() {

        startButton.setEnabled(false);
        logArea.append("Training gestartet...\n");

        Thread trainingThread = new Thread(() -> {
            try {

                int episodes = 1000;
                Trainer trainer = new Trainer(episodes, 200);

                for (int ep = 1; ep <= episodes; ep++) {

                    trainer.runOneEpisode();

                    int progress = (int) ((ep / (double) episodes) * 100);
                    int finalEp = ep;

                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progress);
                        logArea.append("Episode " + finalEp + " abgeschlossen.\n");
                    });
                }

                SwingUtilities.invokeLater(() -> {
                    logArea.append("Training abgeschlossen!\n");
                    startButton.setEnabled(true);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("FEHLER: " + ex.getMessage() + "\n");
                    startButton.setEnabled(true);
                });
            }
        });

        trainingThread.start();
    }
}

