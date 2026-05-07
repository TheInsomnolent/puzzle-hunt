package com.puzzlehunt.model;

import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single step within a {@link PuzzleHunt}.
 *
 * <p>This base class only carries the fields common to every clue type. The
 * concrete completion criteria (which item, which NPC, which tiles, …) will be
 * added in follow-up work as additional optional fields keyed off
 * {@link #type}. Storing them as plain fields keeps the JSON serialisation
 * shape stable and avoids the need for polymorphic Gson adapters.
 */
@Data
@NoArgsConstructor
public class PuzzleStep
{
	/** Stable identifier so progress can be tracked across edits to the hunt. */
	private String id = UUID.randomUUID().toString();

	/** Short human readable title shown in the sidebar. */
	private String title = "";

	/**
	 * Optional free-text clue. When non-blank this is shown to the player
	 * instead of the underlying completion criteria, so creators can write
	 * riddles or text puzzles.
	 */
	private String clueText = "";

	private ClueType type = ClueType.GET_ITEM;
}
