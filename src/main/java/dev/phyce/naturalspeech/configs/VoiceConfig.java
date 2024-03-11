package dev.phyce.naturalspeech.configs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.VoiceConfigDatum;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.NonNull;
import dev.phyce.naturalspeech.helpers.PluginHelper;


import java.util.HashMap;

public class VoiceConfig {
	public HashMap<String, PlayerNameVoiceConfigDatum> player;
	public HashMap<Integer, NPCIDVoiceConfigDatum> npcID;
	public HashMap<String, NPCNameVoiceConfigDatum> npcName;
	public Gson gson;

	public VoiceConfig(@NonNull VoiceConfigDatum data) {

		player = new HashMap<>();
		npcID = new HashMap<>();
		npcName = new HashMap<>();
		gson = new Gson();

		loadDatum(data);
	}

	public VoiceConfig(@NonNull String path) throws JsonSyntaxException {

		player = new HashMap<>();
		npcID = new HashMap<>();
		npcName = new HashMap<>();
		gson = new Gson();
		String jsonContent = readFileToString(path);
		loadJSON(jsonContent);
	}

	public void loadJSON(String jsonString) {
		try {
			VoiceConfigDatum data = gson.fromJson(jsonString, VoiceConfigDatum.class);
			loadDatum(data);
		} catch (JsonSyntaxException e) {
			System.err.println("JSON syntax error: " + e.getMessage());
		} catch (IllegalStateException e) {
			System.err.println("Illegal state encountered during JSON parsing: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("An unexpected error occurred during JSON parsing: " + e.getMessage());
		}
	}

	public String readFileToString(String resourceName) {
		String resourcePath = "dev/phyce/naturalspeech/" + resourceName;
		try (InputStream is = Objects.requireNonNull(
			Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath),
			"Resource not found: " + resourcePath);
			 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

			return reader.lines().collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException | NullPointerException e) {
			System.err.println("Error reading resource: " + e.getMessage());
			return "";
		}
	}

	public void GetCurrentWorkingDirectory() {
		String currentWorkingDirectory = System.getProperty("user.dir");
	}

	public String toJson() {
		return gson.toJson(exportDatum());
	}

	public void loadDatum(VoiceConfigDatum data) {
		player.clear();
		npcID.clear();
		npcName.clear();

		for (PlayerNameVoiceConfigDatum datum : data.getPlayerNameVoiceConfigData()) {
			player.put(datum.getPlayerName(), datum);
		}

		for (NPCIDVoiceConfigDatum datum : data.getNpcIDVoiceConfigData()) {
			npcID.put(datum.getNpcId(), datum);
		}

		for (NPCNameVoiceConfigDatum datum : data.getNpcNameVoiceConfigData()) {
			npcName.put(datum.getNpcName(), datum);
		}
	}

	public VoiceConfigDatum exportDatum() {
		VoiceConfigDatum voiceConfigDatum = new VoiceConfigDatum();
		voiceConfigDatum.getPlayerNameVoiceConfigData().addAll(player.values());
		voiceConfigDatum.getNpcIDVoiceConfigData().addAll(npcID.values());
		voiceConfigDatum.getNpcNameVoiceConfigData().addAll(npcName.values());
		return voiceConfigDatum;
	}

	public VoiceID[] getPlayerVoiceIDs(String playerUserName) {
		if (PluginHelper.getClientUsername().equals(playerUserName)) {
			Supplier<VoiceID> voiceIDSupplier = () -> {
				String personalVoiceID = PluginHelper.getConfig().personalVoiceID();
				try {
					String[] parts = personalVoiceID.split(":");
					if (parts.length == 2) {
						String modelName = parts[0];
						int piperVoiceID = Integer.parseInt(parts[1]);
						return new VoiceID(modelName, piperVoiceID);
					}
				} catch (Exception e) {}
				return null;
			};
			VoiceID voice = voiceIDSupplier.get();
			if(voice != null) return new VoiceID[]{voice};
//			return new VoiceID[] {voice};
		}
		PlayerNameVoiceConfigDatum playerNameVoiceConfigDatum = player.get(playerUserName.toLowerCase());

		if (playerNameVoiceConfigDatum == null) return null;

		return playerNameVoiceConfigDatum.getVoiceIDs();
	}

	public VoiceID[] findVoiceIDsWithNPCID(int npcID) {
		NPCIDVoiceConfigDatum npcIDVoiceConfigDatum = this.npcID.get(npcID);

		if (npcIDVoiceConfigDatum == null) return null;

		return npcIDVoiceConfigDatum.getVoiceIDs();
	}

	public VoiceID[] getNpcVoiceIDs(String npcName) {
		NPCNameVoiceConfigDatum npcNameVoiceConfigDatum = this.npcName.get(npcName.toLowerCase());

		if (npcNameVoiceConfigDatum == null) return null;

		return npcNameVoiceConfigDatum.getVoiceIDs();
	}
}
