package com.puzzlehunt;

import java.util.function.Consumer;
import javax.inject.Singleton;
import lombok.Getter;

/**
 * Co-ordinates "eyedropper" selection flows used by the creation UI.
 *
 * <p>The UI calls {@link #request(Mode, Consumer)} to enter a selection mode;
 * the menu-entry adapter (in {@link CompletionDetector}) checks {@link
 * #getMode()} to decide which extra entries to surface and calls {@link
 * #provide(Object)} when the user clicks one.
 */
@Singleton
public class EyedropperService
{
	public enum Mode
	{
		NONE,
		ITEM_FROM_INVENTORY,
		NPC,
		MONSTER
	}

	@Getter
	private volatile Mode mode = Mode.NONE;

	private volatile Consumer<Object> callback;

	public synchronized void request(Mode mode, Consumer<Object> callback)
	{
		this.mode = mode == null ? Mode.NONE : mode;
		this.callback = callback;
	}

	public synchronized void cancel()
	{
		this.mode = Mode.NONE;
		this.callback = null;
	}

	/** Called by the detector when the user picks a target. */
	public synchronized void provide(Object target)
	{
		Consumer<Object> cb = this.callback;
		this.mode = Mode.NONE;
		this.callback = null;
		if (cb != null && target != null)
		{
			try
			{
				cb.accept(target);
			}
			catch (RuntimeException ignored)
			{
				// swallow: the UI should not be able to take down the event handler
			}
		}
	}
}
