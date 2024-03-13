package dev.phyce.naturalspeech.tts;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.VOICE_CONFIG_FILE;
import dev.phyce.naturalspeech.configs.VoiceConfig;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;


@Slf4j
@Singleton
public class VoiceManager {

	private final VoiceConfig voiceConfig;
	private final TextToSpeech textToSpeech;
	private final ConfigManager configManager;
	private final GenderedVoiceMap genderedVoiceMap;

	private final Multimap<ModelRepository.ModelLocal, VoiceID> activeVoiceMap = HashMultimap.create();

	@Inject
	public VoiceManager(NaturalSpeechPlugin plugin, ConfigManager configManager) {
		this.textToSpeech = plugin.getTextToSpeech();
		this.configManager = configManager;
		this.genderedVoiceMap = new GenderedVoiceMap();

		textToSpeech.addTextToSpeechListener(
			new TextToSpeech.TextToSpeechListener() {
				@Override
				public void onPiperStart(Piper piper) {
					ModelRepository.ModelLocal modelLocal = piper.getModelLocal();
					genderedVoiceMap.addModel(modelLocal);
					for (ModelRepository.VoiceMetadata voiceMetadata : modelLocal.getVoiceMetadata()) {
						activeVoiceMap.put(modelLocal, voiceMetadata.toVoiceID());
					}
				}

				@Override
				public void onPiperExit(Piper piper) {
					genderedVoiceMap.removeModel(piper.getModelLocal());
					activeVoiceMap.removeAll(piper.getModelLocal());
				}
			}
		);

		voiceConfig = new VoiceConfig();
		loadVoiceConfig();
	}

	public VoiceID getVoiceIDFromChatMessage(ChatMessage message) {
		List<VoiceID> results;
		if (message.getName().equals(PluginHelper.getLocalPlayerUsername())) {
			results = voiceConfig.getWithUsername(message.getName());
		}
		else {
			switch (message.getType()) {
				//			case DIALOG:
				case WELCOME:
				case GAMEMESSAGE:
				case CONSOLE:
					results = voiceConfig.getWithNpcName(message.getName());
					break;
				default:
					results = voiceConfig.getWithUsername(message.getName());
					break;
			}
		}
		if (results == null) {
			if (message.getType() == ChatMessageType.PUBLICCHAT) {
				Player user = PluginHelper.getFromUsername(message.getName());
				if (user != null) {
					ModelRepository.Gender gender =
						ModelRepository.Gender.parseInt(user.getPlayerComposition().getGender());

					results = List.of(randomGenderedVoice(message.getName(), gender));
				}
			}
			results = List.of(randomVoiceFromActiveModels(message.getName()));
		}
		return results.get(0);
	}

	public VoiceID getVoiceIDFromNPCId(int npcId, String npcName) {
		npcName = npcName.toLowerCase();
		List<VoiceID> results;
		results = voiceConfig.getWithNpcId(npcId);

		if (results == null) results = voiceConfig.getWithNpcName(npcName);

		if (results == null) {
			results = List.of(randomVoiceFromActiveModels(npcName));
		}

		// FIXME(Louis) Fix getVoiceIDFromNPCId to consider all options
		return results.get(0);
	}

	public List<VoiceID> getVoiceIDFromNPC(@NonNull NPC npc) {
		List<VoiceID> results;
		//noinspection DataFlowIssue Lombok already nullchecks, intellij still warns
		results = voiceConfig.getWithNpcName(npc.getName());

		if (results == null) {
			results = List.of(randomVoiceFromActiveModels(npc.getName()));
		}

		return results;
	}

	public VoiceID getVoiceIdForLocalPlayer() {
		return getVoiceIDFromUsername(PluginHelper.getLocalPlayerUsername());
	}

	public VoiceID getVoiceIDFromUsername(String username) {
		List<VoiceID> results = voiceConfig.getWithUsername(username);

		if (results == null) {
			results = List.of(randomVoiceFromActiveModels(username));
		}

		assert results != null;
		return results.get(0);
	}

	public void loadVoiceConfig() {
		// try to load from existing json in configManager
		String json = configManager.getConfiguration(CONFIG_GROUP, VOICE_CONFIG_FILE);
		if (json != null) {
			try {
				voiceConfig.loadJSON(json);
				log.info("Loaded {} voice config entries from existing VoiceConfig JSON from ConfigManager.",
					voiceConfig.countAll());
				return;
			} catch (JsonSyntaxException ignored) {
				// fallback to default json
				log.error("Invalid voiceConfig stored in ConfigManager, falling back to default: {}", json);
			}
		}
		else {
			log.error("No existing voiceConfig stored in ConfigManager, falling back to default");
		}

		// if configManager fails, load default from resources
		try {
			URL resourceUrl = Objects.requireNonNull(NaturalSpeechPlugin.class.getResource(VOICE_CONFIG_FILE));
			//noinspection UnstableApiUsage
			json = Resources.toString(resourceUrl, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Default voice config file failed to load. " +
				"Either JSON is incorrect, file path is incorrect, or the file doesn't exist.");
		}

		try {
			voiceConfig.loadJSON(json);
			log.info("Loaded default JSON from ResourceFile " + VOICE_CONFIG_FILE);
		} catch (JsonSyntaxException e) {
			throw new RuntimeException("Failed to parse the default voice config JSON: " + json, e);
		}
	}

	public void saveVoiceConfig() {
		configManager.setConfiguration(CONFIG_GROUP, VOICE_CONFIG_FILE, voiceConfig.toJson());
	}

	public void setActorVoiceID(@NonNull Actor actor, VoiceID voiceId) {
		if (actor instanceof NPC) {
			NPC npc = ((NPC) actor);
			voiceConfig.setDefaultNpcIdVoice(npc.getId(), voiceId);
		}
		else if (actor instanceof Player) {
			voiceConfig.setDefaultPlayerVoice(Objects.requireNonNull(actor.getName()), voiceId);
		}
		else {
			log.error("Tried setting a voice for neither NPC or player. Possibly for an object.");
		}
	}


	public VoiceID randomVoiceFromActiveModels(String username) {
		int hashCode = username.hashCode();

		long count = activeVoiceMap.values().size();
		Optional<VoiceID> first = activeVoiceMap.values().stream().skip(Math.abs(hashCode) % count).findFirst();

		return first.orElse(null);
	}

	public VoiceID randomGenderedVoice(String username,
									   ModelRepository.Gender gender) {
		List<VoiceID> voiceIDs = genderedVoiceMap.find(gender);
		if (voiceIDs == null || voiceIDs.isEmpty()) {
			throw new IllegalArgumentException("No voices available for the specified gender");
		}
		int hashCode = username.hashCode();
		int voice = Math.abs(hashCode) % voiceIDs.size();

		return voiceIDs.get(voice);
	}
}
