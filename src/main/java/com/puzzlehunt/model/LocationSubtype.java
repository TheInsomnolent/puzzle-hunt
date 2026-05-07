package com.puzzlehunt.model;

/** Subtype for a {@link ClueType#LOCATION_PUZZLE} step. */
public enum LocationSubtype
{
	/** Completed by triggering the "Complete clue step" menu entry on a named NPC. */
	NPC,
	/** Completed by standing on one of the painted tiles. */
	TILES
}
