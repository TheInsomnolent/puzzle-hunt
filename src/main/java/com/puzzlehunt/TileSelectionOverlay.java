package com.puzzlehunt;

import com.puzzlehunt.model.PuzzleStep;
import com.puzzlehunt.model.SerializedTile;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Renders, while a step is being edited:
 * <ul>
 *   <li>Each tile already saved on the step at low opacity ("valid
 *       trigger" tiles).</li>
 *   <li>Each tile sampled by the in-progress walk-loop, in a different
 *       hue, so the user gets live feedback.</li>
 * </ul>
 *
 * <p>To stay within the "no per-frame scene scans" guideline, the
 * overlay only iterates the configured tile lists (typically &lt; 200
 * tiles) and relies on RuneLite's culling at render time.
 */
@Singleton
public class TileSelectionOverlay extends Overlay
{
	private static final Color VALID_FILL = new Color(0, 200, 255, 40);
	private static final Color VALID_BORDER = new Color(0, 200, 255, 120);
	private static final Color SAMPLE_FILL = new Color(255, 200, 0, 80);
	private static final Color SAMPLE_BORDER = new Color(255, 200, 0, 220);
	private static final Color START_FILL = new Color(60, 220, 90, 80);
	private static final Color START_BORDER = new Color(60, 220, 90, 220);
	private static final Stroke STROKE = new BasicStroke(1.5f);

	private final Client client;
	private final CompletionDetector detector;

	/** Step being edited; null disables rendering. */
	@Setter
	private volatile PuzzleStep editStep;

	/** Starting tile for the hunt currently shown on the detail page; null hides it. */
	@Setter
	private volatile SerializedTile startingTile;

	@Inject
	TileSelectionOverlay(Client client, CompletionDetector detector)
	{
		this.client = client;
		this.detector = detector;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_MED);
	}

	@Override
	public java.awt.Dimension render(Graphics2D g)
	{
		PuzzleStep step = editStep;
		SerializedTile start = startingTile;
		if (step == null && start == null)
		{
			return null;
		}
		Player local = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		Scene scene = wv == null ? null : wv.getScene();
		if (local == null || scene == null)
		{
			return null;
		}
		WorldPoint here = local.getWorldLocation();
		if (here == null)
		{
			return null;
		}
		int plane = wv.getPlane();
		g.setStroke(STROKE);

		if (start != null)
		{
			drawTiles(g, here, plane, java.util.Collections.singletonList(start), START_FILL, START_BORDER);
		}

		if (step != null)
		{
			// Saved valid-trigger tiles.
			List<SerializedTile> tiles = step.getTiles();
			if (tiles != null)
			{
				drawTiles(g, here, plane, tiles, VALID_FILL, VALID_BORDER);
			}

			// Live walk-loop samples (only for the step currently being captured).
			if (detector.getSampleStep() == step)
			{
				List<SerializedTile> samples = detector.getSampleBuffer();
				drawTiles(g, here, plane, samples, SAMPLE_FILL, SAMPLE_BORDER);
			}
		}
		return null;
	}

	private void drawTiles(Graphics2D g, WorldPoint here, int plane,
		List<SerializedTile> tiles, Color fill, Color border)
	{
		for (SerializedTile t : tiles)
		{
			if (t.getPlane() != plane)
			{
				continue;
			}
			WorldPoint wp = new WorldPoint(t.getX(), t.getY(), t.getPlane());
			if (wp.distanceTo(here) > Perspective.SCENE_SIZE)
			{
				continue;
			}
			LocalPoint lp = LocalPoint.fromWorld(client, wp);
			if (lp == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly == null)
			{
				continue;
			}
			g.setColor(fill);
			g.fillPolygon(poly);
			g.setColor(border);
			g.drawPolygon(poly);
		}
	}
}
