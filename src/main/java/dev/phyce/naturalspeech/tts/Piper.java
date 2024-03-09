package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// Renamed from TTSModel
@Slf4j
public class Piper {
	@Getter
	private final Map<Long, PiperProcess> processMap = new HashMap<>();
	@Getter
	private final ConcurrentHashMap<String, AudioQueue> namedAudioQueueMap = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<PiperTask> piperTaskQueue = new ConcurrentLinkedQueue<>();
	private final AudioPlayer audioPlayer;

	@Getter
	private final ModelRepository.ModelLocal modelLocal;
	@Getter
	private final Path piperPath;
	private final Thread processPiperTaskThread;
	private final Thread processAudioQueueThread;

	private final List<PiperProcessListener> piperProcessListeners = new ArrayList<>();

	/**
	 * Create a piper and immediately start
	 *
	 * @throws IOException if piper fails to start an IOException will be thrown. (because stdin cannot be opened).
	 */
	public static Piper start(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount)
		throws IOException {
		return new Piper(modelLocal, piperPath, instanceCount);
	}

	private Piper(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount) throws IOException {
		this.modelLocal = modelLocal;
		this.piperPath = piperPath;

		audioPlayer = new AudioPlayer();

		startMore(instanceCount);

		processPiperTaskThread = new Thread(this::processPiperTask);
		processPiperTaskThread.start();

		processAudioQueueThread = new Thread(this::processAudioQueue);
		processAudioQueueThread.start();
	}

	public void startMore(int instanceCount) throws IOException {
		//Instance count should not be more than 2
		for(int index = 0; index < instanceCount; index++) {
			PiperProcess process;
			try {
				process = PiperProcess.start(piperPath, modelLocal.getOnnx().toPath());
				triggerOnPiperProcessStart(process);
			} catch(IOException e) {
				// clean-up stray instances before throwing
				processMap.forEach((pid, piperProcess) -> piperProcess.stop());
				processMap.clear();
				throw e;
			}
			process.onExit().thenAccept((PiperProcess p) -> {
				log.info("{} exited.", p);
				triggerOnPiperProcessExit(p);
			});
			processMap.put(process.getPid(), process);
		}
	}

	//Process message queue
	public void processPiperTask() {
		while(!processPiperTaskThread.isInterrupted()) {
			if(piperTaskQueue.isEmpty()) {
				synchronized(piperTaskQueue) {
					try {
						piperTaskQueue.wait();
					} catch(InterruptedException e) {
						return; // just exit on interrupt
					}
				}
				continue; // double check emptiness after notify.
			}

			PiperTask task = piperTaskQueue.poll();

			// using iterator to loop, so if an invalid PiperProcess is found we can remove.
			Iterator<Long> iter = processMap.keySet().iterator();
			while(iter.hasNext()) {
				long pid = iter.next();
				PiperProcess instance = processMap.get(pid);

				if(!instance.getPiperLocked().get()) {
					byte[] audioClip;
					try {
						audioClip = instance.generateAudio(task.getText(), task.getVoiceID().getPiperVoiceID());
					} catch(IOException | InterruptedException e) {
						// PiperProcess exited unexpectedly, remove the instance
						log.error("{} had an unexpected exited, either crashed or terminated by user.", instance);
						instance.stop();
						iter.remove();
						continue;
					}
					if(audioClip != null && audioClip.length > 0) {
						AudioQueue audioQueue =
							namedAudioQueueMap.computeIfAbsent(task.audioQueueName, audioQueueName -> new AudioQueue());
						audioQueue.queue.add(new AudioQueue.AudioTask(audioClip, task.getVolume()));

						synchronized(namedAudioQueueMap) {namedAudioQueueMap.notify();}

						break; // will only break for the nearest scoped loop, aka the for, not while
					}
				}
			}
		}
	}

	public void processAudioQueue() {
		while(!processAudioQueueThread.isInterrupted()) {

			synchronized(namedAudioQueueMap) {
				try {
					namedAudioQueueMap.wait();
				} catch(InterruptedException e) {
					return;
				}
			}

			namedAudioQueueMap.forEach((queueName, audioQueue) -> {

				if(!audioQueue.isPlaying() && !audioQueue.queue.isEmpty()) {
					audioQueue.setPlaying(true);

					// start a thread for each named audio queue
					new Thread(() -> {
						try {
							AudioQueue.AudioTask task;
							while((task = audioQueue.queue.poll()) != null) {
								audioPlayer.playClip(task.getAudioClip(), task.getVolume());
							}
						} finally {
							audioQueue.setPlaying(false);
						}
					}).start();
				}

			});
		}
	}

	// Refactored to decouple from dependencies
	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName) throws IOException {
		if(countAlive() == 0) {
			throw new IOException("No active PiperProcess instances running for " + voiceID.getModelName());
		}

		if(piperTaskQueue.size() > 10) {
			log.info("Cleared queue because queue size is too large. (more then 10)");
			clearQueue();
		}

		piperTaskQueue.add(new PiperTask(text, voiceID, volume, audioQueueName));
		synchronized(piperTaskQueue) {piperTaskQueue.notify();}
	}

	public void clearQueue() {
		piperTaskQueue.clear();
		namedAudioQueueMap.values().forEach(audioQueue -> {
			audioQueue.queue.clear();
		});
	}

	public int countAlive() {
		int result = 0;
		for(PiperProcess process : processMap.values()) {
			if(process.isAlive()) result++;
		}
		return result;
	}

	public void stopAll() {
		audioPlayer.stop();

		for(PiperProcess instance : processMap.values()) {
			instance.stop();
		}
		processMap.clear();

		processAudioQueueThread.interrupt();
		processPiperTaskThread.interrupt();
	}

	public void addPiperListener(PiperProcessListener listener) {
		piperProcessListeners.add(listener);
	}

	public void removePiperListener(PiperProcessListener listener) {
		piperProcessListeners.remove(listener);
	}


	private void triggerOnPiperProcessStart(PiperProcess process) {
		for(PiperProcessListener listener : piperProcessListeners) {
			listener.onPiperProcessStart(process);
		}
	}

	private void triggerOnPiperProcessExit(PiperProcess process) {
		for(PiperProcessListener listener : piperProcessListeners) {
			listener.onPiperProcessExit(process);
		}
	}

	// Renamed from TTSItem, decoupled from dependencies
	@Value
	@AllArgsConstructor
	private static class PiperTask {
		String text;
		VoiceID voiceID;
		float volume;
		String audioQueueName;
	}

	public interface PiperProcessListener {
		default void onPiperProcessStart(PiperProcess process) {};

		default void onPiperProcessExit(PiperProcess process) {};
	}


}
