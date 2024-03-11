package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.tts.Piper;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.ui.layouts.OnlyVisibleGridLayout;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

@Slf4j
public class MainSettingsPanel extends PluginPanel {

	static {
		BufferedImage sectionRetractIcon =
			ImageUtil.loadImageResource(MainSettingsPanel.class, "section_icons/arrow_right.png");
		sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
		SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
		final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
		SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);
	}

	public static final ImageIcon SECTION_EXPAND_ICON;
	private static final EmptyBorder BORDER_PADDING = new EmptyBorder(6, 6, 6, 6);
	private static final ImageIcon SECTION_RETRACT_ICON;
	private static final Dimension OUTER_PREFERRED_SIZE = new Dimension(242, 0);


	private final FixedWidthPanel mainContentPanel;
	private final JScrollPane scrollPane;
	private final ModelRepository modelRepository;
	private final NaturalSpeechConfig config;
	private final NaturalSpeechPlugin plugin;
	private final ConfigManager configManager;
	private final Downloader downloader;
	private final NaturalSpeechRuntimeConfig runtimeConfig;

	@Inject
	public MainSettingsPanel(
		NaturalSpeechConfig config,
		NaturalSpeechPlugin plugin,
		ConfigManager configManager,
		Downloader downloader,
		ModelRepository modelRepository,
		NaturalSpeechRuntimeConfig runtimeConfig
	) {
		super(false);
		this.config = config;
		this.plugin = plugin;
		this.configManager = configManager;
		this.downloader = downloader;
		this.modelRepository = modelRepository;
		this.runtimeConfig = runtimeConfig;

		this.setLayout(new BorderLayout());
		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		mainContentPanel = new FixedWidthPanel();
		mainContentPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
		mainContentPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		mainContentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel mainContentNorthWrapper = new FixedWidthPanel();
		mainContentNorthWrapper.setLayout(new BorderLayout());
		mainContentNorthWrapper.add(mainContentPanel, BorderLayout.NORTH);

		scrollPane = new JScrollPane(mainContentNorthWrapper);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		this.add(scrollPane);

		buildHeaderSegment();
		buildPiperStatusSection();
		buildVoiceRepositorySegment();

		this.revalidate();
	}

	public void buildHeaderSegment() {
		JLabel titleLabel = new JLabel("NaturalSpeech", JLabel.CENTER);
		titleLabel.setFont(new Font("Sans", Font.BOLD, 24));
		titleLabel.setBorder(new EmptyBorder(1, 0, 1, 0));
		mainContentPanel.add(titleLabel);

		// Instructions Link
		JLabel instructionsLink =
			new JLabel("<html>For instructions, click <a href='#'>here</a>.</html>", JLabel.CENTER);
		instructionsLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
		instructionsLink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					Desktop.getDesktop().browse(new URI("https://github.com/phyce/rl-natural-speech"));
				} catch (Exception ex) {
					log.error("Error opening instruction link.", ex);
				}
			}
		});
		instructionsLink.setBorder(new EmptyBorder(0, 0, 5, 0));
		mainContentPanel.add(instructionsLink);

	}

	public void buildVoiceRepositorySegment() {
		final JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setMinimumSize(new Dimension(PANEL_WIDTH, 0));

		final JPanel sectionHeader = new JPanel();
		sectionHeader.setLayout(new BorderLayout());
		sectionHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		// For whatever reason, the header extends out by a single pixel when closed. Adding a single pixel of
		// border on the right only affects the width when closed, fixing the issue.
		sectionHeader.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, 3, 1)));
		section.add(sectionHeader);

		final JButton sectionToggle = new JButton(SECTION_RETRACT_ICON);
		sectionToggle.setPreferredSize(new Dimension(18, 0));
		sectionToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
		sectionToggle.setToolTipText("Retract");
		SwingUtil.removeButtonDecorations(sectionToggle);
		sectionHeader.add(sectionToggle, BorderLayout.WEST);


		final String name = "Voice Repository";
		final String description = "Manage your voice models.";
		final JLabel sectionName = new JLabel(name);
		sectionName.setForeground(ColorScheme.BRAND_ORANGE);
		sectionName.setFont(FontManager.getRunescapeBoldFont());
		sectionName.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
		sectionHeader.add(sectionName, BorderLayout.CENTER);

		final JPanel sectionContent = new JPanel();
		sectionContent.setLayout(new OnlyVisibleGridLayout(0, 1, 0, 5));
		sectionContent.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		section.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)
		));
		section.add(sectionContent, BorderLayout.SOUTH);

		mainContentPanel.add(section);

		// Toggle section action listeners
		final MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleSection(sectionToggle, sectionContent);
			}
		};
		sectionToggle.addActionListener(actionEvent -> toggleSection(sectionToggle, sectionContent));
		sectionName.addMouseListener(adapter);
		sectionHeader.addMouseListener(adapter);
	}

	public void buildPiperStatusSection() {
		final JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setMinimumSize(new Dimension(PANEL_WIDTH, 0));

		final JPanel sectionHeader = new JPanel();
		sectionHeader.setLayout(new BorderLayout());
		sectionHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		// For whatever reason, the header extends out by a single pixel when closed. Adding a single pixel of
		// border on the right only affects the width when closed, fixing the issue.
		sectionHeader.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, 3, 1)));
		section.add(sectionHeader);

		final JButton sectionToggle = new JButton(SECTION_RETRACT_ICON);
		sectionToggle.setPreferredSize(new Dimension(18, 0));
		sectionToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
		sectionToggle.setToolTipText("Retract");
		SwingUtil.removeButtonDecorations(sectionToggle);
		sectionHeader.add(sectionToggle, BorderLayout.WEST);

		final String name = "Piper Status";
		final String description = "Manage your piper instances.";
		final JLabel sectionName = new JLabel(name);
		sectionName.setForeground(ColorScheme.BRAND_ORANGE);
		sectionName.setFont(FontManager.getRunescapeBoldFont());
		sectionName.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
		sectionHeader.add(sectionName, BorderLayout.CENTER);

		final JPanel sectionContent = new JPanel();
		sectionContent.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		sectionContent.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		section.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)
		));
		section.add(sectionContent, BorderLayout.SOUTH);
		mainContentPanel.add(section);

		// Toggle section action listeners
		final MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleSection(sectionToggle, sectionContent);
			}
		};
		sectionToggle.addActionListener(actionEvent -> toggleSection(sectionToggle, sectionContent));
		sectionName.addMouseListener(adapter);
		sectionHeader.addMouseListener(adapter);

		// Status Label with dynamic background color
		JPanel statusPanel = buildPiperStatusPanel();
		sectionContent.add(statusPanel);

		JPanel piperFileChoosePanel = buildPiperFileChoose();
		sectionContent.add(piperFileChoosePanel);

		JPanel piperProcessMonitorPanel = buildPiperProcessMonitorPanel();
		sectionContent.add(piperProcessMonitorPanel);
	}

	private JPanel buildPiperProcessMonitorPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new DynamicGridLayout(0, 1, 0, 2));
		panel.setBorder(new EmptyBorder(5, 0, 5, 0));

		plugin.getTextToSpeech().addPiperLifetimeListener(
			new TextToSpeech.PiperLifetimeListener() {
				private Map<Piper, PiperListItem> piperItemList = new HashMap<>();

				@Override
				public void onPiperStart(Piper piper) {
					PiperListItem piperItem = new PiperListItem(piper);
					piperItemList.put(piper, piperItem);
					panel.add(piperItem);
					panel.revalidate();
				}

				@Override
				public void onPiperExit(Piper piper) {
					panel.remove(piperItemList.remove(piper));
					panel.revalidate();
				}
			}
		);
		return panel;
	}

	private JPanel buildPiperStatusPanel() {
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BorderLayout());
		statusPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

		JLabel statusLabel = new JLabel("Status: Unknown", SwingConstants.CENTER);
		statusLabel.setFont(new Font("Sans", Font.BOLD, 20));
		statusLabel.setOpaque(true); // Needed to show background color
		statusLabel.setPreferredSize(new Dimension(statusLabel.getWidth(), 50)); // Set preferred height
		statusLabel.setBackground(Color.DARK_GRAY);

		statusPanel.add(statusLabel, BorderLayout.NORTH);

		plugin.getTextToSpeech().addPiperLifetimeListener(
			new TextToSpeech.PiperLifetimeListener() {
				@Override
				public void onPiperStart(Piper piper) {
					// FIXME(Louis) Temporary just for testing. Should check if any pipers are running,
					// not just one starting piper
					statusLabel.setText("Status: Running");
					statusLabel.setBackground(Color.GREEN.darker());
					statusLabel.setForeground(Color.WHITE);
				}

				@Override
				public void onPiperExit(Piper piper) {
					// FIXME(Louis) Temporary just for testing. Should check if any pipers are running,
					// not just one starting piper
					statusLabel.setText("Status: Stopped");
					statusLabel.setBackground(Color.DARK_GRAY);
					statusLabel.setForeground(null);
				}
			}
		);

		// Button Panel
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Align buttons in the center

		// Initialize buttons with icons
		JButton playButton = createButton("start.png", "Start");
		JButton stopButton = createButton("stop.png", "Stop");

		playButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					log.info("Play button pressed");
					startEngine();
				} catch (LineUnavailableException | IOException ex) {
					log.error("Piper failed to start. ", ex);
					throw new RuntimeException(ex);
				}
			}
		});
		stopButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					stopEngine();
				} catch (IOException ex) {
					log.error("Piper failed to stop. ", ex);
					throw new RuntimeException(ex);
				}
			}
		});

		buttonPanel.add(playButton);
		buttonPanel.add(stopButton);
		statusPanel.add(buttonPanel, BorderLayout.CENTER);
		return statusPanel;
	}

	private JPanel buildPiperFileChoose() {
		JTextField filePathField = new JTextField(runtimeConfig.getPiperPath().toString());
		filePathField.setToolTipText("Piper binary file path");
		filePathField.setEditable(false);

		JButton browseButton = new JButton("Browse");
		browseButton.setToolTipText("Requires manual download, please read instructions.");
		browseButton.addActionListener(e -> {
			// open in drive top path
			JFileChooser fileChooser = new JFileChooser(System.getProperty("user.home"));
			fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			int returnValue = fileChooser.showOpenDialog(MainSettingsPanel.this);
			if (returnValue == JFileChooser.APPROVE_OPTION) {
				String newPath = fileChooser.getSelectedFile().getPath();
				filePathField.setText(newPath);
				runtimeConfig.setPiperPath(Path.of(newPath));
			}
		});

		JPanel fileBrowsePanel = new JPanel(new BorderLayout());
		fileBrowsePanel.setBorder(new EmptyBorder(0, 0, 5, 0));
		fileBrowsePanel.add(filePathField, BorderLayout.CENTER);
		fileBrowsePanel.add(browseButton, BorderLayout.SOUTH);
		return fileBrowsePanel;
	}

	private void toggleSection(JButton toggleButton, JPanel sectionContent) {
		boolean newState = !sectionContent.isVisible();
		sectionContent.setVisible(newState);
		toggleButton.setIcon(newState? SECTION_RETRACT_ICON: SECTION_EXPAND_ICON);
		toggleButton.setToolTipText(newState? "Retract": "Expand");
		SwingUtilities.invokeLater(sectionContent::revalidate);
	}

	private JButton createButton(String iconPath, String toolTipText) {
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), iconPath);
		JButton button = new JButton(new ImageIcon(icon));
		button.setToolTipText(toolTipText);
		return button;
	}

	private void startEngine() throws LineUnavailableException, IOException {
		try {
			plugin.startTextToSpeech();
		} catch (IOException | LineUnavailableException e) {
			log.error("Error starting TextToSpeech", e);
			throw e;
		}
	}

	private void stopEngine() throws IOException {
		plugin.stopTextToSpeech();
		//		updateStatus(1);
	}

	@Override
	public void onActivate() {
		super.onActivate();

		this.setVisible(true);
	}

	@Override
	public void onDeactivate() {
		super.onDeactivate();

		this.setVisible(false);
	}
}
