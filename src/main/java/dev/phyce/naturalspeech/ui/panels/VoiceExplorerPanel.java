package dev.phyce.naturalspeech.ui.panels;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.ui.components.IconTextField;
import dev.phyce.naturalspeech.ui.layouts.OnlyVisibleGridLayout;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import org.apache.commons.lang3.StringUtils;

public class VoiceExplorerPanel extends EditorPanel {

	public static final ImageIcon SECTION_EXPAND_ICON;
	private static final ImageIcon SECTION_RETRACT_ICON;
	private static final ImmutableList<String> SEARCH_HINTS = ImmutableList.of("Male", "Female");

	static {
		BufferedImage sectionRetractIcon =
			ImageUtil.loadImageResource(MainSettingsPanel.class, "section_icons/arrow_right.png");
		sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
		SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
		final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
		SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);
	}

	final ImageIcon speechTextIcon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "speechText.png"));
	private final PiperRepository piperRepository;
	private final TextToSpeech textToSpeech;
	@Getter
	private final IconTextField speechText;
	@Getter
	private final IconTextField searchBar;
	@Getter
	private final FixedWidthPanel sectionListPanel;
	@Getter
	private final JScrollPane speakerScrollPane;
	private final List<VoiceListItem> voiceListItems = new ArrayList<>();
	private final PiperRepository.ModelRepositoryListener modelRepositoryListener;

	@Inject
	public VoiceExplorerPanel(PiperRepository piperRepository, TextToSpeech textToSpeech) {
		this.piperRepository = piperRepository;
		this.textToSpeech = textToSpeech;

		this.setLayout(new BorderLayout());
		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Search Bar
		searchBar = new IconTextField();
		searchBar.setPlaceholderText("Enter name or gender");
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		SEARCH_HINTS.forEach(searchBar.getSuggestionListModel()::addElement);
		searchBar.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				searchFilter(searchBar.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				searchFilter(searchBar.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				searchFilter(searchBar.getText());
			}
		});

		// Speech Text Bar
		speechText = new IconTextField();
		speechText.setIcon(speechTextIcon);
		speechText.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		speechText.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		speechText.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		speechText.setText("Hello, Natural Speech");
		speechText.setToolTipText("Sentence to be spoken.");
		speechText.setPlaceholderText("Enter a sentence");

		// Float Top/North Wrapper Panel, for search and speech text bar.
		JPanel topPanel = new JPanel();
		topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		topPanel.setLayout(new GridLayout(0, 1, 0, PluginPanel.BORDER_OFFSET));
		topPanel.add(searchBar);
		topPanel.add(speechText);
		this.add(topPanel, BorderLayout.NORTH);

		// Speakers panel containing individual speaker item panels
		sectionListPanel = new FixedWidthPanel();
		sectionListPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
		sectionListPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		sectionListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// North panel wraps and fixes the speakerList north
		JPanel speakerListNorthWrapper = new FixedWidthPanel();
		speakerListNorthWrapper.setLayout(new BorderLayout());
		speakerListNorthWrapper.add(sectionListPanel, BorderLayout.NORTH);

		// A parent scroll view pane for speakerListPanel
		speakerScrollPane = new JScrollPane(speakerListNorthWrapper);
		speakerScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		this.add(speakerScrollPane);

		modelRepositoryListener = new PiperRepository.ModelRepositoryListener() {
			@Override
			public void onRepositoryChanged(String modelName) {
				SwingUtilities.invokeLater(() -> buildSpeakerList());
			}

			@Override
			public void onRefresh() {
				SwingUtilities.invokeLater(() -> buildSpeakerList());
			}
		};
		this.piperRepository.addRepositoryChangedListener(modelRepositoryListener);

		buildSpeakerList();
	}

	void buildSpeakerList() {
		sectionListPanel.removeAll();
		for (PiperRepository.ModelURL modelURL : piperRepository.getModelURLS()) {
			try {
				if (piperRepository.hasModelLocal(modelURL.getModelName())) {
					buildSpeakerSegmentForVoice(modelURL.getModelName());
				}
			} catch (IOException ignore) {
			}
		}
	}

	private void toggleSpeakerSection(JButton toggleButton, JPanel sectionContent) {
		boolean newState = !sectionContent.isVisible();
		sectionContent.setVisible(newState);
		toggleButton.setIcon(newState? SECTION_RETRACT_ICON: SECTION_EXPAND_ICON);
		toggleButton.setToolTipText(newState? "Retract": "Expand");
		SwingUtilities.invokeLater(sectionContent::revalidate);
	}

	private void buildSpeakerSegmentForVoice(String voice_name) {

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

		final String name = voice_name;
		final String description = voice_name;
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

		// Add listeners to each part of the header so that it's easier to toggle them
		final MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleSpeakerSection(sectionToggle, sectionContent);
			}
		};
		sectionToggle.addActionListener(actionEvent -> toggleSpeakerSection(sectionToggle, sectionContent));
		sectionName.addMouseListener(adapter);
		sectionHeader.addMouseListener(adapter);

		try {
			PiperRepository.ModelLocal modelLocal = piperRepository.loadModelLocal(voice_name);

			Arrays.stream(modelLocal.getVoiceMetadata())
				.sorted(Comparator.comparing(a -> a.getName().toLowerCase()))
				.forEach((voiceMetadata) -> {
					VoiceListItem speakerItem = new VoiceListItem(this, textToSpeech, voiceMetadata, modelLocal);
					voiceListItems.add(speakerItem);
					sectionContent.add(speakerItem);
				});

		} catch (IOException e) {throw new RuntimeException(e);}

		sectionListPanel.add(section);
	}

	void searchFilter(String searchInput) {
		if (searchInput.isEmpty()) {
			for (VoiceListItem speakerItems : voiceListItems) {speakerItems.setVisible(true);}
			return;
		}

		// split search by space and comma
		Set<String> searchTerms = Arrays.stream(searchInput.toLowerCase().split("[,\\s]+"))
			.filter(s -> !s.isEmpty())
			.map(String::trim)
			.map(String::toLowerCase).collect(Collectors.toSet());

		Gender genderSearch = null;
		Iterator<String> iterator = searchTerms.iterator();
		while (iterator.hasNext()) {
			String searchTerm = iterator.next();

			if (List.of("m", "male", "guy").contains(searchTerm)) {
				genderSearch = Gender.MALE;
				iterator.remove();
			}
			else if (List.of("f", "female", "girl").contains(searchTerm)) {
				genderSearch = Gender.FEMALE;
				iterator.remove();
			}
		}

		searchInput = StringUtils.join(searchTerms, " ");

		for (VoiceListItem speakerItem : voiceListItems) {
			PiperRepository.VoiceMetadata voiceMetadata = speakerItem.getVoiceMetadata();

			boolean visible = genderSearch == null || genderSearch.equals(voiceMetadata.getGender());

			// name search
			if (!searchInput.isEmpty()) {
				boolean term_matched = false;
				if (!searchTerms.isEmpty() && voiceMetadata.getName().toLowerCase().contains(searchInput)) {
					term_matched = true;
				}

				if (!term_matched) visible = false;
			}
			speakerItem.setVisible(visible);
		}

		sectionListPanel.revalidate();
	}

	public void shutdown() {
		piperRepository.removeRepositoryChangedListener(modelRepositoryListener);
		this.removeAll();
	}

	@Override
	public void onActivate() {
		super.onActivate();

		SwingUtilities.invokeLater(() -> setVisible(true));
	}

	@Override
	public void onDeactivate() {
		super.onDeactivate();

		SwingUtilities.invokeLater(() -> setVisible(false));
	}
}
