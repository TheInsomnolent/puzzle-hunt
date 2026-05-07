package com.puzzlehunt;

import com.google.gson.Gson;
import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.PuzzleHunt;
import com.puzzlehunt.model.PuzzleStep;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Behaviour tests for {@link ActiveHuntService}. */
public class ActiveHuntServiceTest
{
	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private HuntManager manager;
	private ActiveHuntService service;

	@Before
	public void setUp()
	{
		manager = new HuntManager(new Gson(), tmp.getRoot().toPath());
		service = new ActiveHuntService(manager);
	}

	private static PuzzleHunt huntWithSteps(HuntMode mode, int n)
	{
		PuzzleHunt h = new PuzzleHunt();
		h.setName("Test");
		h.setMode(mode);
		for (int i = 0; i < n; i++)
		{
			PuzzleStep s = new PuzzleStep();
			s.setTitle("Step " + i);
			h.getSteps().add(s);
		}
		return h;
	}

	@Test
	public void startBeginsTimerAndPauseStopsIt()
	{
		PuzzleHunt h = huntWithSteps(HuntMode.DIARY, 1);
		service.start(h);
		assertTrue(service.isTimerRunning());
		long elapsedWhileRunning = service.getElapsedMillis();
		assertTrue("Elapsed should be non-negative while running", elapsedWhileRunning >= 0L);

		service.pauseTimer();
		assertFalse(service.isTimerRunning());
		long firstPauseValue = service.getElapsedMillis();

		// While paused getElapsedMillis() must return the persisted accumulator
		// only — no live session contribution. Reading it twice in succession
		// must yield the same value.
		assertEquals(firstPauseValue, service.getElapsedMillis());
		assertEquals(0L, service.getActiveProgress().getSessionStartMillis());
	}

	@Test
	public void treasureTrailRequiresOrderedCompletion()
	{
		PuzzleHunt h = huntWithSteps(HuntMode.TREASURE_TRAIL, 3);
		service.start(h);

		String firstId = h.getSteps().get(0).getId();
		String secondId = h.getSteps().get(1).getId();
		String thirdId = h.getSteps().get(2).getId();

		// Completing the second step out of order is rejected.
		assertFalse(service.completeStep(secondId));
		assertEquals(0, service.getCompletedStepIds().size());

		assertTrue(service.completeStep(firstId));
		assertTrue(service.completeStep(secondId));
		assertTrue(service.completeStep(thirdId));

		assertEquals(3, service.getCompletedStepIds().size());
		assertTrue(service.getActiveProgress().isCompleted());
		assertFalse(service.isTimerRunning());
	}

	@Test
	public void diaryAllowsCompletionInAnyOrder()
	{
		PuzzleHunt h = huntWithSteps(HuntMode.DIARY, 3);
		service.start(h);

		String thirdId = h.getSteps().get(2).getId();
		String firstId = h.getSteps().get(0).getId();

		assertTrue(service.completeStep(thirdId));
		assertTrue(service.completeStep(firstId));
		assertEquals(2, service.getCompletedStepIds().size());
		// Re-completing is a no-op.
		assertFalse(service.completeStep(firstId));
	}

	@Test
	public void completedHuntDoesNotResumeTimerOnReload()
	{
		PuzzleHunt h = huntWithSteps(HuntMode.DIARY, 1);
		service.start(h);
		service.completeStep(h.getSteps().get(0).getId());
		assertTrue(service.getActiveProgress().isCompleted());

		// Simulate restarting the plugin by stopping and re-loading the same hunt.
		service.stop();
		service.start(h);
		assertNotNull(service.getActiveProgress());
		assertTrue(service.getActiveProgress().isCompleted());
		assertFalse(service.isTimerRunning());
	}

	@Test
	public void resetClearsProgressAndResumesTimer()
	{
		PuzzleHunt h = huntWithSteps(HuntMode.TREASURE_TRAIL, 2);
		service.start(h);
		service.completeStep(h.getSteps().get(0).getId());
		assertEquals(1, service.getCompletedStepIds().size());

		service.reset(h.getId());
		assertEquals(0, service.getCompletedStepIds().size());
		assertEquals(0, service.getCurrentStepIndex());
		assertTrue(service.isTimerRunning());
	}

	@Test
	public void getActiveStepsForDetectionTreasureTrailReturnsOnlyCurrent()
	{
		PuzzleHunt h = huntWithSteps(HuntMode.TREASURE_TRAIL, 3);
		service.start(h);
		assertEquals(1, service.getActiveStepsForDetection().size());
		assertEquals(h.getSteps().get(0).getId(), service.getActiveStepsForDetection().get(0).getId());
		service.completeStep(h.getSteps().get(0).getId());
		assertEquals(h.getSteps().get(1).getId(), service.getActiveStepsForDetection().get(0).getId());
	}

	@Test
	public void getActiveStepsForDetectionDiaryReturnsAllUncompleted()
	{
		PuzzleHunt h = huntWithSteps(HuntMode.DIARY, 3);
		service.start(h);
		assertEquals(3, service.getActiveStepsForDetection().size());
		service.completeStep(h.getSteps().get(1).getId());
		assertEquals(2, service.getActiveStepsForDetection().size());
	}
}
