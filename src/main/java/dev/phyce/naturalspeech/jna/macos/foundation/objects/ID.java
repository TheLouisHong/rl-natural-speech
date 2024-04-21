package dev.phyce.naturalspeech.jna.macos.foundation.objects;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import lombok.Getter;

@SuppressWarnings("unused")
public class ID extends NativeLong {

	/**
	 * Check NIL with {@link #equals(Object)} or {@link #isNil()}, not `==`
	 */
	@Getter
	private static final ID NIL = new ID(0L);

	public ID() {
		super();
	}

	public ID(long peer) {
		super(peer);
	}

	public ID(Pointer peer) {
		super(Pointer.nativeValue(peer));
	}

	public Pointer toPointer() {
		return new Pointer(longValue());
	}

	public boolean booleanValue() {
		return intValue() != 0;
	}

	public boolean isNil() {
		return equals(NIL);
	}
}