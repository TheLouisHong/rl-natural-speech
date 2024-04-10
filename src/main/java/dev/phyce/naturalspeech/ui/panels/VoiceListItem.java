package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

@Slf4j
public class VoiceListItem extends JPanel {

	private final VoiceExplorerPanel voiceExplorerPanel;
	private final TextToSpeech textToSpeech;
	@Getter
	private final VoiceMetadata voiceMetadata;

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
		VoiceMetadata voiceMetadata) {
		this.voiceExplorerPanel = voiceExplorerPanel;
		this.textToSpeech = textToSpeech;
		this.voiceMetadata = voiceMetadata;

		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setToolTipText(String.format("%s %s (%s)", voiceMetadata.voiceId.id, voiceMetadata.getName(),
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

		JLabel piperIdLabel = new JLabel(voiceMetadata.voiceId.id);

		speakerLayout.setHorizontalGroup(speakerLayout
			.createSequentialGroup()
			.addGap(5)
			.addComponent(piperIdLabel, 25, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
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


//		boolean isModelActive = textToSpeech.isModelActive(voiceMetadata.voiceId);
//		JButton playButton = new JButton(isModelActive ? PLAY_BUTTON : PLAY_BUTTON_DISABLED);
//		playButton.setEnabled(isModelActive);
//		SwingUtil.removeButtonDecorations(playButton);
//		playButton.setPreferredSize(
//			new Dimension(PLAY_BUTTON_DISABLED.getIconWidth(), PLAY_BUTTON_DISABLED.getIconHeight()));
//		playButton.addActionListener(
//			event -> {
//				if (textToSpeech != null) {
//					try {
//						if (textToSpeech.isModelActive(voiceMetadata.voiceId)) {
//							textToSpeech.speak(
//								voiceMetadata.voiceId,
//								textToSpeech.expandAbbreviations(voiceExplorerPanel.getSpeechText().getText()),
//								() -> 0f,
//								AudioLineNames.VOICE_EXPLORER);
//						}
//						else {
//							log.error("Model {} is currently not running.", voiceMetadata.voiceId.modelName);
//						}
//					} catch (ModelLocalUnavailableException e) {
//						throw new RuntimeException(e);
//					}
//				}
//			});


		BorderLayout rootLayout = new BorderLayout();
		this.setLayout(rootLayout);
		this.add(speakerPanel, BorderLayout.CENTER);
//		this.add(playButton, BorderLayout.EAST);

		revalidate();

//		textToSpeech.addTextToSpeechListener(
//			new TextToSpeech.TextToSpeechListener() {
//				@Override
//				public void onPiperStart(PiperModel piper) {
//					if (piper.getModelLocal().getModelName().equals(voiceMetadata.voiceId.modelName)) {
//						playButton.setIcon(PLAY_BUTTON);
//						playButton.setEnabled(true);
//					}
//				}
//
//				@Override
//				public void onPiperExit(PiperModel piper) {
//					if (piper.getModelLocal().getModelName().equals(voiceMetadata.voiceId.modelName)) {
//						playButton.setIcon(PLAY_BUTTON_DISABLED);
//						playButton.setEnabled(false);
//					}
//				}
//
//			}
//		);
	}
}
