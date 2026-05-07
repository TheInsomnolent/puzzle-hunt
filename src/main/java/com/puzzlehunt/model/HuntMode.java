package com.puzzlehunt.model;

/**
 * The mode a {@link PuzzleHunt} is played in.
 *
 * <ul>
 *     <li>{@link #TREASURE_TRAIL} — steps must be completed in order; future
 *     steps are hidden from the player.</li>
 *     <li>{@link #DIARY} — all steps are visible from the start and can be
 *     completed in any order.</li>
 * </ul>
 */
public enum HuntMode
{
	TREASURE_TRAIL("Treasure Trail"),
	DIARY("Diary");

	private final String displayName;

	HuntMode(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
