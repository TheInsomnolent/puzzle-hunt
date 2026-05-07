package com.puzzlehunt;

import com.puzzlehunt.model.SerializedTile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Geometry helpers for the "walk loop" tile capture flow:
 *
 * <ol>
 *   <li>The player presses <em>Walk loop</em> and walks along the boundary
 *   of the area they want to mark.</li>
 *   <li>Each game tick samples the player's tile.</li>
 *   <li>On <em>Finish loop</em> the samples are reduced to a convex hull
 *   and every integer tile inside (or on) the hull is added to the
 *   step's tile list.</li>
 * </ol>
 *
 * <p>Convex hull is computed with Andrew's monotone chain in O(n log n).
 * Interior tiles are enumerated by ray-casting each integer tile centre
 * against the hull polygon. All work is done on plain ints so it stays
 * out of the client thread.
 */
final class TileHull
{
	private TileHull() {}

	/**
	 * Returns the set of tiles forming the filled convex hull of the
	 * supplied samples (all on the same plane). Returns an empty list if
	 * fewer than one sample is supplied.
	 */
	static List<SerializedTile> hullFill(List<SerializedTile> samples)
	{
		if (samples == null || samples.isEmpty())
		{
			return new ArrayList<>();
		}
		int plane = samples.get(0).getPlane();
		// Dedupe samples on this plane.
		Set<Long> seen = new HashSet<>();
		List<int[]> pts = new ArrayList<>(samples.size());
		for (SerializedTile t : samples)
		{
			if (t.getPlane() != plane)
			{
				continue;
			}
			long k = (((long) t.getX()) << 32) | (t.getY() & 0xffffffffL);
			if (seen.add(k))
			{
				pts.add(new int[]{t.getX(), t.getY()});
			}
		}
		if (pts.isEmpty())
		{
			return new ArrayList<>();
		}
		if (pts.size() == 1)
		{
			List<SerializedTile> single = new ArrayList<>(1);
			single.add(new SerializedTile(pts.get(0)[0], pts.get(0)[1], plane));
			return single;
		}

		List<int[]> hull = convexHull(pts);
		// Bounding box.
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		for (int[] p : hull)
		{
			if (p[0] < minX) minX = p[0];
			if (p[0] > maxX) maxX = p[0];
			if (p[1] < minY) minY = p[1];
			if (p[1] > maxY) maxY = p[1];
		}
		List<SerializedTile> out = new ArrayList<>();
		for (int x = minX; x <= maxX; x++)
		{
			for (int y = minY; y <= maxY; y++)
			{
				if (insideOrOnPolygon(hull, x, y))
				{
					out.add(new SerializedTile(x, y, plane));
				}
			}
		}
		return out;
	}

	private static List<int[]> convexHull(List<int[]> input)
	{
		List<int[]> pts = new ArrayList<>(input);
		pts.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
		int n = pts.size();
		int[][] hull = new int[2 * n][];
		int k = 0;
		// Lower hull
		for (int[] p : pts)
		{
			while (k >= 2 && cross(hull[k - 2], hull[k - 1], p) <= 0) k--;
			hull[k++] = p;
		}
		// Upper hull
		int lower = k + 1;
		for (int i = n - 2; i >= 0; i--)
		{
			int[] p = pts.get(i);
			while (k >= lower && cross(hull[k - 2], hull[k - 1], p) <= 0) k--;
			hull[k++] = p;
		}
		List<int[]> out = new ArrayList<>(k - 1);
		for (int i = 0; i < k - 1; i++)
		{
			out.add(hull[i]);
		}
		return out;
	}

	private static long cross(int[] o, int[] a, int[] b)
	{
		return (long) (a[0] - o[0]) * (b[1] - o[1]) - (long) (a[1] - o[1]) * (b[0] - o[0]);
	}

	/**
	 * Test whether the integer tile (x,y) lies inside or on the convex
	 * polygon. Uses the half-plane test which is exact for convex
	 * polygons and avoids the ambiguity of ray casting on edges.
	 */
	private static boolean insideOrOnPolygon(List<int[]> hull, int x, int y)
	{
		int n = hull.size();
		if (n == 0) return false;
		if (n == 1) return hull.get(0)[0] == x && hull.get(0)[1] == y;
		// All cross products should have the same sign (or zero) for
		// the point to be inside a CCW polygon.
		boolean hasPos = false, hasNeg = false;
		for (int i = 0; i < n; i++)
		{
			int[] a = hull.get(i);
			int[] b = hull.get((i + 1) % n);
			long c = (long) (b[0] - a[0]) * (y - a[1]) - (long) (b[1] - a[1]) * (x - a[0]);
			if (c > 0) hasPos = true;
			else if (c < 0) hasNeg = true;
			if (hasPos && hasNeg) return false;
		}
		return true;
	}
}
