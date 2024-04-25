package dev.phyce.naturalspeech;

import com.sun.jna.Native;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation.AVSpeechSynthesisVoice;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation.AVSpeechSynthesizer;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation.AVSpeechUtterance;
import dev.phyce.naturalspeech.jna.macos.foundation.util.AutoRelease;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.NSObject;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.NSString;
import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundation;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.Test;

public class MacTest {

	@Test
	public void testJNA() {
		ID hello = NSString.alloc("Hello, ");

		// ARC testing
		NSObject.retain(hello);
		System.out.println("hello ref count: " + NSObject.getRetainCount(hello));
		NSObject.release(hello);
		System.out.println("hello ref count: " + NSObject.getRetainCount(hello));
		// if we release again, it will be deallocated, and SEGFAULT will occur when we try to access it
		// NSObject.release(hello);
		// NSObject.getRetainCount(hello); <- SEGFAULT

		// Modify the NSString
		ID world = NSString.alloc("World!");
		ID helloworld = NSString.allocStringByAppendingString(hello, world);

		// Read the string
		System.out.println("String: " + NSString.getJavaString(helloworld));

		// Release the NSString
		NSObject.release(hello);
		NSObject.release(world);
		NSObject.release(helloworld);
	}

	@Test
	public void testAVSpeechSynthesisVoice() {
		ID[] voices = AVSpeechSynthesisVoice.getSpeechVoices();
		Arrays.sort(voices, Comparator.comparing(AVSpeechSynthesisVoice::getName));
		for (ID voice : voices) {
			String name = AVSpeechSynthesisVoice.getName(voice);
			Gender gender = AVSpeechSynthesisVoice.getGender(voice);
			String identifier = AVSpeechSynthesisVoice.getIdentifier(voice);
			String language = AVSpeechSynthesisVoice.getLanguage(voice);
			System.out.printf("%-10s %-10s %-10s %50s\n", name, language, gender, identifier);
		}
	}

	@Test
	public void testAVSpeechUtterance() {
		ID utterance = AVSpeechUtterance.allocSpeechUtteranceWithString("Hello, Natural Speech!");
		AutoRelease.register(utterance);
		ID voice = AVSpeechUtterance.getVoice(utterance);
		ID speechString = AVSpeechUtterance.getSpeechString(utterance);
		String voiceName = AVSpeechSynthesisVoice.getName(voice);

		System.out.println("Voice: " + voiceName);
		System.out.println("Speech: " + NSString.getJavaString(speechString));

		ID synthesizer = AVSpeechSynthesizer.alloc();
		AutoRelease.register(synthesizer);

		AVSpeechSynthesizer.speakUtterance(synthesizer, utterance);
		try {
			Thread.sleep(2000L);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testAutoReleaseSEGFAULT() {
		WeakReference<Pair> helloWeak = testAutoReleaseSUGFAULT_aux();

		//noinspection DataFlowIssue
		ID helloID = helloWeak.get().id;

		while (helloWeak.get() != null) {
			System.gc();
		}

		System.out.printf("The java object has been garbage collected, ID(%s) should be released\n", helloID);

		// This will cause a SEGFAULT
		System.out.println("ASSERTING SEGFAULT\n--------------------");
		System.out.println("YOU SHOULD NEVER SEE THIS PRINT -> " + NSObject.getRetainCount(helloID));
	}

	public static class Pair {
		public final Object obj;
		public final ID id;

		public Pair(Object obj, ID id) {
			this.obj = obj;
			this.id = id;
		}
	}

	@SuppressWarnings("StringOperationCanBeSimplified")
	public WeakReference<Pair> testAutoReleaseSUGFAULT_aux() {

		/*
		 Important for this test to succeed:

		 // Do not test with:
		 String hello = "Hello, ";

		 because literal strings are interned into a string pool,
		 they will not be garbage collected until the JVM exits

		 see String#intern() for more information
		 */

		String hello = new String("Hello, ");
		String world = new String("World!");

		ID helloID = AutoRelease.register(hello, NSString.alloc(hello)).getId();
		ID worldID = AutoRelease.register(world, NSString.alloc(world)).getId();
		ID helloworldID = NSString.allocStringByAppendingString(helloID, worldID);
		AutoRelease.register(NSString.getJavaString(helloworldID), helloworldID);

		// This should not cause a SEGFAULT
		System.out.println("CHECKING RETAIN COUNT, ASSERT 1 -> " + NSObject.getRetainCount(helloID));

		return new WeakReference<>(new Pair(hello, helloID));

	}

	@Test
	public void testAutoReleaseSEGFAULT2() {
		WeakReference<Pair> weakRefs = testAutoReleaseSEGFAULT2_AUX();

		//noinspection DataFlowIssue
		long idValue = weakRefs.get().id.longValue();

		while (weakRefs.get() != null) {
			System.gc();
		}

		System.out.printf("The ID object has been garbage collected, ID(%s) should be released\n",idValue);

		// This will cause a SEGFAULT
		System.out.println("ASSERTING SEGFAULT\n--------------------");
		System.out.println("YOU SHOULD NEVER SEE THIS PRINT -> " + NSObject.getRetainCount(new ID(idValue)));
	}

	@SuppressWarnings("StringOperationCanBeSimplified")
	private WeakReference<Pair> testAutoReleaseSEGFAULT2_AUX() {
		String hello = new String("Hello, ");
		String world = new String("World!");

		ID helloID = AutoRelease.register(NSString.alloc(hello));
		ID worldID = AutoRelease.register(NSString.alloc(world));
		ID helloworldID = AutoRelease.register(NSString.allocStringByAppendingString(helloID, worldID));

		String helloworld = NSString.getJavaString(helloworldID);

		// This should not cause a SEGFAULT
		System.out.println("ASSERT 1 -> " + NSObject.getRetainCount(helloworldID));

		return new WeakReference<>(new Pair(helloworld, helloworldID));
	}

}
