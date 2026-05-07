package com.puzzlehunt.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A serialisable world tile coordinate. Mirrors {@code WorldPoint} so the
 * model package has no dependency on the RuneLite API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SerializedTile
{
	private int x;
	private int y;
	private int plane;
}
