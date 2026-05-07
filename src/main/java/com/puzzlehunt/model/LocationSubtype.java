package com.puzzlehunt.model;

/** Subtype for a {@link ClueType#LOCATION_PUZZLE} step. */
public enum LocationSubtype
{
	/** Completed by triggering the "Complete clue step" menu entry on a named NPC. */
	NPC("NPC"),
	/** Completed by standing on one of the painted tiles. */
	TILES("Tiles");

	private final String displayName;

	LocationSubtype(String displayName)
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
