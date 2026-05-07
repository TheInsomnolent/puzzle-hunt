package com.puzzlehunt.model;

/**
 * Identifies the kind of completion check a {@link PuzzleStep} uses.
 *
 * <p>Subtype-specific configuration lives on the step itself; this enum only
 * decides which group of fields are relevant. Future clue types should be
 * appended to the end of this enum so existing serialised hunts keep working.
 */
public enum ClueType
{
	/** Acquire a specific item. May be restricted to a monster drop or a ground spawn. */
	GET_ITEM("Get item"),
	/** A free-text clue completed by interacting with a named NPC or stepping on a marked tile. */
	LOCATION_PUZZLE("Location puzzle"),
	/** Kill one or more monsters by name a configurable number of times. */
	KILL_MONSTER("Kill monster"),
	/** Die. Useful for speed-death challenges. */
	DIE("Die"),
	/** Accumulate a target amount of GP (positive coin-stack deltas in inventory). */
	GAIN_GP("Gain GP"),
	/** Accumulate XP in one or more skills. */
	GAIN_XP("Gain XP"),
	/** Player must enter a secret password into the panel. */
	PASSWORD("Secret password");

	private final String displayName;

	ClueType(String displayName)
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
