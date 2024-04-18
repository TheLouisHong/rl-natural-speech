package dev.phyce.naturalspeech.events;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class TextToSpeechFailedStart {
	public enum Reason {
		ALL_DISABLED,
		ALL_FAILED
	}

	Reason reason;
}