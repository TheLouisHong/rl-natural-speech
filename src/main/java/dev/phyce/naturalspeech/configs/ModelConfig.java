package dev.phyce.naturalspeech.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.ModelConfigDatum;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.PiperConfigDatum;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ModelConfig {

	private final List<PiperConfig> piperConfigs;

	private static final Gson gson = new GsonBuilder().create();

	public static ModelConfig fromDatum(ModelConfigDatum datum) {
		return new ModelConfig(datum);
	}

	public ModelConfigDatum toDatum() {
		ModelConfigDatum configDatum = new ModelConfigDatum();
		configDatum.getPiperConfigData()
			.addAll(this.piperConfigs.stream().map(PiperConfig::toDatum).collect(Collectors.toList()));
		return configDatum;
	}

	public static ModelConfig fromJson(String json) throws JsonSyntaxException {
		ModelConfigDatum datum = gson.fromJson(json, ModelConfigDatum.class);
		return new ModelConfig(datum);
	}

	public String toJson() {
		return gson.toJson(toDatum());
	}

	private ModelConfig(ModelConfigDatum datum) {
		piperConfigs = datum.getPiperConfigData().stream().map(PiperConfig::fromDatum).collect(Collectors.toList());
	}

	public int getModelProcessCount(String modelName) {
		return piperConfigs.stream()
			.filter(p -> p.getModelName().equals(modelName))
			.findFirst()
			.map(PiperConfig::getProcessCount)
			.orElse(1);
	}

	public void setModelProcessCount(String modelName, int processCount) {
		// look for existing config
		for (PiperConfig p : piperConfigs) {
			if (p.getModelName().equals(modelName)) {
				p.setProcessCount(processCount);
				return;
			}
		}

		initializePiperConfig(modelName);
		setModelProcessCount(modelName, processCount);
	}

	public boolean isModelEnabled(String modelName) {
		return piperConfigs.stream().anyMatch(p -> p.getModelName().equals(modelName) && p.isEnabled());
	}

	public void setModelEnabled(String modelName, boolean enabled) {
		// look for existing config
		for (PiperConfig p : piperConfigs) {
			if (p.getModelName().equals(modelName)) {
				p.setEnabled(enabled);
				return;
			}
		}

		initializePiperConfig(modelName);
		setModelEnabled(modelName, enabled);
	}

	private void initializePiperConfig(String modelName) {
		// existing config wasn't found, make new
		PiperConfigDatum piperDatum = new PiperConfigDatum(modelName, false, 1);
		piperConfigs.add(PiperConfig.fromDatum(piperDatum));
	}

	public void resetPiperConfig(String modelName) {
		// look for existing config
		Iterator<PiperConfig> iter = piperConfigs.iterator();
		while (iter.hasNext()) {
			PiperConfig piperConfig = iter.next();
			if (piperConfig.getModelName().equals(modelName)) {
				iter.remove();
				break;
			}
		}

		// existing config wasn't found, make new
		PiperConfigDatum piperDatum = new PiperConfigDatum(modelName, false, 1);
		piperConfigs.add(PiperConfig.fromDatum(piperDatum));
	}

}
