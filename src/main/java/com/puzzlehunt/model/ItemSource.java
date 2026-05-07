package com.puzzlehunt.model;

/** Where a {@link ClueType#GET_ITEM} step accepts the item from. */
public enum ItemSource
{
	/** Any source counts — picked up, dropped, looted, traded, anything. */
	ANY("Any source"),
	/** Must come from a kill of one of the configured monsters. */
	MONSTER_DROP("Monster drop"),
	/** Must come from a skilling action (mining, fishing, woodcutting, crafting, etc.) — not from a bank or shop. */
	SKILLING_RESOURCE("Skilling resource"),
	/** Must be picked up off the ground from a (naturally occurring) ground spawn. */
	GROUND_SPAWN("Ground spawn");

	private final String displayName;

	ItemSource(String displayName)
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
