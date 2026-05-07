package com.puzzlehunt;

import com.google.gson.Gson;
import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import com.puzzlehunt.model.PuzzleStep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HuntManagerTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private HuntManager manager;

	@Before
	public void setUp()
	{
		manager = new HuntManager(new Gson(), tmp.getRoot().toPath());
	}

	@Test
	public void loadAllHuntsReturnsEmptyWhenDirectoryMissing()
	{
		assertTrue(manager.loadAllHunts().isEmpty());
	}

	@Test
	public void saveAndLoadHuntRoundTrips() throws IOException
	{
		PuzzleHunt hunt = new PuzzleHunt();
		hunt.setName("My hunt");
		hunt.setMode(HuntMode.DIARY);
		PuzzleStep step = new PuzzleStep();
		step.setTitle("First step");
		hunt.getSteps().add(step);

		manager.saveHunt(hunt);

		List<PuzzleHunt> loaded = manager.loadAllHunts();
		assertEquals(1, loaded.size());
		PuzzleHunt back = loaded.get(0);
		assertEquals(hunt.getId(), back.getId());
		assertEquals("My hunt", back.getName());
		assertEquals(HuntMode.DIARY, back.getMode());
		assertEquals(1, back.getSteps().size());
		assertEquals("First step", back.getSteps().get(0).getTitle());
	}

	@Test
	public void loadProgressReturnsEmptyRecordWhenMissing()
	{
		HuntProgress progress = manager.loadProgress("missing-hunt");
		assertNotNull(progress);
		assertEquals("missing-hunt", progress.getHuntId());
		assertEquals(0L, progress.getElapsedMillis());
		assertTrue(progress.getStepSplits().isEmpty());
	}

	@Test
	public void saveAndLoadProgressRoundTrips() throws IOException
	{
		HuntProgress progress = new HuntProgress();
		progress.setHuntId("abc");
		progress.setElapsedMillis(12_345L);
		progress.getStepSplits().put("step-1", 6_000L);

		manager.saveProgress(progress);

		HuntProgress back = manager.loadProgress("abc");
		assertEquals(12_345L, back.getElapsedMillis());
		assertEquals(Long.valueOf(6_000L), back.getStepSplits().get("step-1"));
	}

	@Test
	public void importHuntPersistsAndAssignsIdWhenMissing() throws IOException
	{
		String json = "{\"name\":\"Shared\",\"mode\":\"TREASURE_TRAIL\",\"steps\":[]}";
		PuzzleHunt imported = manager.importHunt(json);
		assertNotNull(imported.getId());
		assertTrue(!imported.getId().isEmpty());
		assertEquals("Shared", imported.getName());
		assertEquals(1, manager.loadAllHunts().size());
	}

	@Test
	public void importHuntRejectsInvalidJson()
	{
		try
		{
			manager.importHunt("not json");
			fail("Expected IOException");
		}
		catch (IOException expected)
		{
			// pass
		}
	}

	@Test
	public void deleteHuntRemovesHuntAndProgress() throws IOException
	{
		PuzzleHunt hunt = new PuzzleHunt();
		hunt.setName("Throwaway");
		manager.saveHunt(hunt);

		HuntProgress progress = new HuntProgress();
		progress.setHuntId(hunt.getId());
		progress.setElapsedMillis(1L);
		manager.saveProgress(progress);

		manager.deleteHunt(hunt.getId());

		assertTrue(manager.loadAllHunts().isEmpty());
		// Loading progress after delete returns a fresh empty record.
		HuntProgress reloaded = manager.loadProgress(hunt.getId());
		assertNotSame(progress, reloaded);
		assertEquals(0L, reloaded.getElapsedMillis());
	}

	@Test
	public void saveHuntCreatesPluginSubdirectoryUnderProvidedRoot() throws IOException
	{
		PuzzleHunt hunt = new PuzzleHunt();
		manager.saveHunt(hunt);
		Path huntsDir = tmp.getRoot().toPath().resolve("hunts");
		assertTrue(Files.isDirectory(huntsDir));
	}
}
