package com.puzzlehunt.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A collection of {@link PuzzleStep}s the player attempts to complete in a
 * single play-through.
 */
@Data
@NoArgsConstructor
public class PuzzleHunt
{
	/** Stable identifier so the same hunt can be referenced across imports / saves. */
	private String id = UUID.randomUUID().toString();

	private String name = "Untitled hunt";

	private String author = "";

	private HuntMode mode = HuntMode.TREASURE_TRAIL;

	private List<PuzzleStep> steps = new ArrayList<>();
}
