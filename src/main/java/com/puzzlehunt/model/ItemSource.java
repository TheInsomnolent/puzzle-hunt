package com.puzzlehunt.model;

/** Where a {@link ClueType#GET_ITEM} step accepts the item from. */
public enum ItemSource
{
	/** Any source counts — picked up, dropped, looted, traded, anything. */
	ANY,
	/** Must come from a kill of one of the configured monsters. */
	MONSTER_DROP,
	/** Must be picked up off the ground from a (naturally occurring) ground spawn. */
	GROUND_SPAWN
}
