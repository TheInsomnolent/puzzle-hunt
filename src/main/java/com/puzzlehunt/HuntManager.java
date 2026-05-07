package com.puzzlehunt;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Loads and saves {@link PuzzleHunt}s and their {@link HuntProgress} from the
 * <code>.runelite/puzzle-hunt</code> directory.
 *
 * <p>Each hunt is stored as a single JSON file named <code>{id}.json</code> in
 * the {@code hunts/} subdirectory; per-hunt progress is stored alongside it in
 * {@code progress/{id}.json}. Splitting the two files keeps progress writes
 * (which happen frequently while playing) cheap and isolated from the hunt
 * definition itself, which players will sometimes want to share verbatim.
 *
 * <p>All filesystem operations live on whatever thread invokes them; callers
 * that are on the client thread should dispatch through an executor.
 */
@Slf4j
@Singleton
public class HuntManager
{
	static final String PLUGIN_DIR_NAME = "puzzle-hunt";
	private static final String HUNTS_DIR_NAME = "hunts";
	private static final String PROGRESS_DIR_NAME = "progress";
	private static final String JSON_EXT = ".json";

	private final Gson gson;
	private final Path pluginDir;
	private final Path huntsDir;
	private final Path progressDir;

	@Inject
	HuntManager(Gson gson)
	{
		this(gson, RuneLite.RUNELITE_DIR.toPath().resolve(PLUGIN_DIR_NAME));
	}

	/** Visible for tests. */
	HuntManager(Gson gson, Path pluginDir)
	{
		this.gson = gson;
		this.pluginDir = pluginDir;
		this.huntsDir = pluginDir.resolve(HUNTS_DIR_NAME);
		this.progressDir = pluginDir.resolve(PROGRESS_DIR_NAME);
	}

	/** Loads every saved hunt. Corrupt files are skipped with a debug log. */
	public List<PuzzleHunt> loadAllHunts()
	{
		if (!Files.isDirectory(huntsDir))
		{
			return new ArrayList<>();
		}
		List<PuzzleHunt> hunts = new ArrayList<>();
		try (Stream<Path> files = Files.list(huntsDir))
		{
			files
				.filter(p -> p.getFileName().toString().endsWith(JSON_EXT))
				.sorted()
				.forEach(p ->
				{
					try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8))
					{
						PuzzleHunt hunt = gson.fromJson(r, PuzzleHunt.class);
						if (hunt != null && hunt.getId() != null)
						{
							hunts.add(hunt);
						}
					}
					catch (IOException | JsonSyntaxException e)
					{
						log.debug("Skipping unreadable hunt file {}", p, e);
					}
				});
		}
		catch (IOException e)
		{
			log.debug("Failed to list hunts directory", e);
		}
		return hunts;
	}

	/** Persists the given hunt to disk. */
	public void saveHunt(PuzzleHunt hunt) throws IOException
	{
		if (hunt == null || hunt.getId() == null || hunt.getId().isEmpty())
		{
			throw new IllegalArgumentException("Hunt must have a non-empty id");
		}
		Files.createDirectories(huntsDir);
		Path target = huntsDir.resolve(safeFileName(hunt.getId()) + JSON_EXT);
		writeJson(target, hunt);
	}

	/** Removes the hunt and any associated progress. */
	public void deleteHunt(String huntId) throws IOException
	{
		Files.deleteIfExists(huntsDir.resolve(safeFileName(huntId) + JSON_EXT));
		Files.deleteIfExists(progressDir.resolve(safeFileName(huntId) + JSON_EXT));
	}

	/** Imports a hunt from arbitrary JSON content (e.g. one a friend shared). */
	public PuzzleHunt importHunt(String json) throws IOException
	{
		PuzzleHunt hunt;
		try
		{
			hunt = gson.fromJson(json, PuzzleHunt.class);
		}
		catch (JsonSyntaxException e)
		{
			throw new IOException("Imported file is not valid puzzle hunt JSON", e);
		}
		if (hunt == null)
		{
			throw new IOException("Imported file was empty");
		}
		if (hunt.getId() == null || hunt.getId().isEmpty())
		{
			// Generate a fresh hunt by round-tripping through the no-arg ctor.
			PuzzleHunt fresh = new PuzzleHunt();
			hunt.setId(fresh.getId());
		}
		saveHunt(hunt);
		return hunt;
	}

	/** Loads progress for the given hunt id, returning a fresh record if none exists. */
	public HuntProgress loadProgress(String huntId)
	{
		Path target = progressDir.resolve(safeFileName(huntId) + JSON_EXT);
		if (!Files.isRegularFile(target))
		{
			HuntProgress empty = new HuntProgress();
			empty.setHuntId(huntId);
			return empty;
		}
		try (BufferedReader r = Files.newBufferedReader(target, StandardCharsets.UTF_8))
		{
			HuntProgress progress = gson.fromJson(r, HuntProgress.class);
			if (progress == null)
			{
				progress = new HuntProgress();
			}
			progress.setHuntId(huntId);
			if (progress.getStepSplits() == null)
			{
				progress.setStepSplits(new LinkedHashMap<>());
			}
			return progress;
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.debug("Failed to read progress for hunt {}; resetting", huntId, e);
			HuntProgress empty = new HuntProgress();
			empty.setHuntId(huntId);
			return empty;
		}
	}

	public void saveProgress(HuntProgress progress) throws IOException
	{
		if (progress == null || progress.getHuntId() == null || progress.getHuntId().isEmpty())
		{
			throw new IllegalArgumentException("Progress must reference a hunt id");
		}
		Files.createDirectories(progressDir);
		Path target = progressDir.resolve(safeFileName(progress.getHuntId()) + JSON_EXT);
		writeJson(target, progress);
	}

	/** Returns the on-disk hunts directory. Visible for tests / diagnostics. */
	Path getPluginDir()
	{
		return pluginDir;
	}

	/** Convenience: hunts paired with their current progress, in load order. */
	public Map<PuzzleHunt, HuntProgress> loadAllWithProgress()
	{
		List<PuzzleHunt> hunts = loadAllHunts();
		Map<PuzzleHunt, HuntProgress> out = new LinkedHashMap<>();
		for (PuzzleHunt hunt : hunts)
		{
			out.put(hunt, loadProgress(hunt.getId()));
		}
		return Collections.unmodifiableMap(out);
	}

	private void writeJson(Path target, Object value) throws IOException
	{
		try (Writer w = new BufferedWriter(Files.newBufferedWriter(target, StandardCharsets.UTF_8)))
		{
			gson.toJson(value, w);
		}
	}

	/**
	 * Strips characters that are problematic in filenames. Hunt ids are
	 * generated from {@link java.util.UUID#randomUUID()} so this is a defence
	 * in depth measure for hand-edited or imported hunts.
	 */
	private static String safeFileName(String raw)
	{
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++)
		{
			char c = raw.charAt(i);
			if (Character.isLetterOrDigit(c) || c == '-' || c == '_')
			{
				sb.append(c);
			}
			else
			{
				sb.append('_');
			}
		}
		return sb.length() == 0 ? "hunt" : sb.toString();
	}
}
