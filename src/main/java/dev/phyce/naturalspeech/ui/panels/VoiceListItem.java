package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.tts.piper.Piper;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class VoiceListItem extends JPanel {

	private final VoiceExplorerPanel voiceExplorerPanel;
	private final TextToSpeech textToSpeech;
	@Getter
	private final ModelRepository.VoiceMetadata voiceMetadata;
	private final ModelRepository.ModelLocal modelLocal;

	private static final ImageIcon PLAY_BUTTON;
	private static final ImageIcon PLAY_BUTTON_DISABLED;

	static {
		BufferedImage image = ImageUtil.loadImageResource(VoiceListItem.class, "start.png");
		PLAY_BUTTON = new ImageIcon(image.getScaledInstance(25, 25, Image.SCALE_SMOOTH));
		PLAY_BUTTON_DISABLED = new ImageIcon(
			ImageUtil.luminanceScale(ImageUtil.grayscaleImage(image), 0.61f)
				.getScaledInstance(25, 25, Image.SCALE_SMOOTH));

	}


	public VoiceListItem(
		VoiceExplorerPanel voiceExplorerPanel,
		TextToSpeech textToSpeech,
		ModelRepository.VoiceMetadata voiceMetadata,
		ModelRepository.ModelLocal modelLocal) {
		this.voiceExplorerPanel = voiceExplorerPanel;
		this.textToSpeech = textToSpeech;
		this.voiceMetadata = voiceMetadata;
		this.modelLocal = modelLocal;

		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setToolTipText(String.format("%s %s (%s)", voiceMetadata.getPiperVoiceID(), voiceMetadata.getName(),
			voiceMetadata.getGender()));

		JPanel speakerPanel = new JPanel();
		speakerPanel.setOpaque(false);

		GroupLayout speakerLayout = new GroupLayout(speakerPanel);
		speakerPanel.setLayout(speakerLayout);


		JLabel nameLabel = new JLabel(voiceMetadata.getName());
		nameLabel.setForeground(Color.white);

		String genderString;
		if (voiceMetadata.getGender() == Gender.MALE) {
			genderString = "(M)";
		}
		else if (voiceMetadata.getGender() == Gender.FEMALE) {
			genderString = "(F)";
		}
		else {
			genderString = "(?)";
		}

		JLabel genderLabel = new JLabel(genderString);
		genderLabel.setForeground(Color.white);

		JLabel piperIdLabel = new JLabel(String.format("ID%d", voiceMetadata.getPiperVoiceID()));

		speakerLayout.setHorizontalGroup(speakerLayout
			.createSequentialGroup()
			.addGap(5)
			.addComponent(piperIdLabel, 35, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
			.addGap(5)
			.addComponent(nameLabel)
			.addGap(5).addComponent(genderLabel));

		int lineHeight = (int) (nameLabel.getFontMetrics(nameLabel.getFont()).getHeight() * 1.5);

		speakerLayout.setVerticalGroup(speakerLayout.createParallelGroup()
			.addGap(5)
			.addComponent(piperIdLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(nameLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(genderLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addGap(5));


		JButton playButton = new JButton(PLAY_BUTTON_DISABLED);
		SwingUtil.removeButtonDecorations(playButton);
		playButton.setPreferredSize(
			new Dimension(PLAY_BUTTON_DISABLED.getIconWidth(), PLAY_BUTTON_DISABLED.getIconHeight()));
		playButton.addActionListener(

			event -> {
				if (textToSpeech != null && textToSpeech.activePiperProcessCount() > 0) {
					try {
						if (textToSpeech.isModelActive(modelLocal)) {
							textToSpeech.speak(
								voiceMetadata.toVoiceID(),
								textToSpeech.expandShortenedPhrases(voiceExplorerPanel.getSpeechText().getText()),
								0,
								"&VoiceExplorer");
						}
						else {
							log.info("Model {} is currently not running.", modelLocal.getModelName());
						}
					} catch (ModelLocalUnavailableException e) {
						throw new RuntimeException(e);
					}
				}
			});

		playButton.setEnabled(false);

		BorderLayout rootLayout = new BorderLayout();
		this.setLayout(rootLayout);
		this.add(speakerPanel, BorderLayout.CENTER);
		this.add(playButton, BorderLayout.EAST);

		revalidate();

		textToSpeech.addTextToSpeechListener(
			new TextToSpeech.TextToSpeechListener() {
				@Override
				public void onPiperStart(Piper piper) {
					if (piper.getModelLocal().getModelName().equals(modelLocal.getModelName())) {
						playButton.setIcon(PLAY_BUTTON);
						playButton.setEnabled(true);
					}
				}

				@Override
				public void onPiperExit(Piper piper) {
					if (piper.getModelLocal().getModelName().equals(modelLocal.getModelName())) {
						playButton.setIcon(PLAY_BUTTON_DISABLED);
						playButton.setEnabled(false);
					}
				}
			}
		);
	}
}
