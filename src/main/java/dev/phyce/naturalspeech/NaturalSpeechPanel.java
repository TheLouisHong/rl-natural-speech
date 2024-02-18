package dev.phyce.naturalspeech;

import com.google.inject.Provides;
import dev.phyce.naturalspeech.tts.DownloadManager;
import dev.phyce.naturalspeech.tts.TTSEngine;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Color;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;

public class NaturalSpeechPanel extends PluginPanel {
    private final NaturalSpeechConfig config;
    private JLabel statusLabel;

    private JPanel downloadPanel, fileBrowsePanel, buttonPanel;
    private JButton downloadButton, browseButton, playButton, /*restartButton,*/ stopButton;
    private JTextField voiceModelInput, filePathField;

    private NaturalSpeechPlugin plugin;

    @Inject
    private ConfigManager configManager;

    public void drawHeaderSegment() {
        JLabel titleLabel = new JLabel("NaturalSpeech", JLabel.CENTER);
        titleLabel.setFont(new Font("Sans", Font.BOLD, 24));
        titleLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(titleLabel);

        // Instructions Link
        JLabel instructionsLink = new JLabel("<html>For instructions, click <a href='#'>here</a>.</html>", JLabel.CENTER);
        instructionsLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        instructionsLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://github.com/phyce/rl-natural-speech"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        add(instructionsLink);

    }
    public void drawEngineSegment() {
        JComboBox<String> ttsEngineSelect = new JComboBox<>(new String[]{"Piper"});
        ttsEngineSelect.setToolTipText("At the moment, only one TTS engine is supported.");
        ttsEngineSelect.setEnabled(false);
        add(ttsEngineSelect);
    }
    public void drawStatusSegment() {
        // Status Label with dynamic background color
        statusLabel = new JLabel("Status: Unknown", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Sans", Font.BOLD, 20));
        statusLabel.setOpaque(true); // Needed to show background color
        statusLabel.setPreferredSize(new Dimension(statusLabel.getWidth(), 30)); // Set preferred height


        add(statusLabel, BorderLayout.NORTH);

        // Button Panel
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Align buttons in the center

        // Initialize buttons with icons
        playButton = createButton("start.png", "Start");
        stopButton = createButton("stop.png", "Stop");

        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startEngine();
            }
        });
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopEngine();
            }
        });

        buttonPanel.add(playButton);
        buttonPanel.add(stopButton);
        add(buttonPanel, BorderLayout.CENTER);
    }
    public void drawBinarySegment() {
        filePathField = new JTextField(config.ttsEngine());
        filePathField.setToolTipText("TTS engine binary file path");

        browseButton = new JButton("Browse");
        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                int returnValue = fileChooser.showOpenDialog(NaturalSpeechPanel.this);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    String newPath = fileChooser.getSelectedFile().getPath();
                    filePathField.setText(newPath);
                    configManager.setConfiguration(NaturalSpeechConfig.CONFIG_GROUP, "ttsEngine", newPath);
                }
            }
        });

        fileBrowsePanel = new JPanel(new BorderLayout(5, 0));
        fileBrowsePanel.setBorder(new EmptyBorder(0, 0, 5, 0));
        fileBrowsePanel.add(filePathField, BorderLayout.CENTER);
        fileBrowsePanel.add(browseButton, BorderLayout.EAST);

        add(fileBrowsePanel);
    }
    public void drawModelSegment() {
        DownloadManager downloads = DownloadManager.getInstance();

        float progress = downloads.getFileReadyPercentage();
        voiceModelInput = new JTextField(String.format("voice (%.0f%%)", progress));
        voiceModelInput.setEditable(false);
        voiceModelInput.setToolTipText("Currently only one voice model available");

        downloadButton = new JButton("Download");
        downloadButton.setToolTipText("Download the en_US-libritts-high.onnx TTS voice file");
        downloadButton.addActionListener(e -> {
            new Thread(downloads::checkAndDownload).start();
        });

        downloadPanel = new JPanel(new BorderLayout(5, 0));
        downloadPanel.setBorder(new EmptyBorder(0, 0, 15, 0));
        downloadPanel.add(voiceModelInput, BorderLayout.CENTER);
        downloadPanel.add(downloadButton, BorderLayout.EAST);
        add(downloadPanel);
    }
    public void updateModelSegment() {
        float progress = DownloadManager.getInstance().getFileProgress();
        SwingUtilities.invokeLater(() -> {
            if (voiceModelInput != null) voiceModelInput.setText(String.format("voice (%.0f%%)", progress));

            if(progress > 0 && downloadButton != null) downloadButton.setEnabled(false);
        });
    }



    public NaturalSpeechPanel(ConfigManager manager, NaturalSpeechPlugin plugin) {
        super();
        this.configManager = manager;
        this.config = manager.getConfig(NaturalSpeechConfig.class);
        this.plugin = plugin;

        setLayout(new GridLayout(0, 1)); // Use GridLayout for simplicity

        drawHeaderSegment();
        drawEngineSegment();
        drawStatusSegment();
        updateStatus(1);
        drawBinarySegment();
        drawModelSegment();
    }

    // Method to update the status text, color, and button states
    public void updateStatus(int status) {
        String statusText = "Status: ";
        Color statusColor = Color.DARK_GRAY; // Default color
        boolean playEnabled = false, restartEnabled = false, stopEnabled = false;

        switch (status) {
            case 1: // Stopped
                statusText += "Stopped";
                statusColor = Color.DARK_GRAY;
                playEnabled = true;
                break;
            case 2: // Running
                statusText += "Ready";
                statusColor = Color.decode("#00B316");
                restartEnabled = true;
                stopEnabled = true;
                break;
            case 3: // Launching
                statusText += "Launching";
                statusColor = Color.ORANGE;
                //stopEnabled = true; // Assuming you can stop a launching process
                break;
            case 4: // Crashed
                statusText += "Crashed";
                statusColor = Color.decode("C40000");
                playEnabled = true;
                break;
        }

        statusLabel.setText(statusText);
        statusLabel.setBackground(statusColor);
        playButton.setEnabled(playEnabled);
        //restartButton.setEnabled(restartEnabled);
        stopButton.setEnabled(stopEnabled);
    }

    private JButton createButton(String iconPath, String toolTipText) {
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), iconPath);
        JButton button = new JButton(new ImageIcon(icon));
        button.setToolTipText(toolTipText);
        return button;
    }

    private void startEngine() {
        updateStatus(3);
        plugin.startTTS();
        if(plugin.getTts().isProcessing())updateStatus(2);
        else updateStatus(4);
    }

    private void stopEngine() {
        plugin.stopTTS();
        updateStatus(1);
    }

    private void restartEngine() {
        stopEngine();
        startEngine();

    }
}
