package com.puzzlehunt;

import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import com.puzzlehunt.model.PuzzleStep;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the currently-running puzzle hunt: timer accumulation, completion
 * tracking, persistence and end-of-hunt detection.
 *
 * <p>The timer is split into a persisted {@link HuntProgress#getElapsedMillis()
 * elapsedMillis} accumulator plus a transient {@link
 * HuntProgress#getSessionStartMillis() sessionStartMillis} marker. Calling
 * {@link #pauseTimer()} folds the live session into the accumulator, so
 * shutting RuneLite down at any point loses at most the time since the last
 * state-write tick.
 */
@Slf4j
@Singleton
public class ActiveHuntService
{
	/** Listener for any state change that the UI should re-render on. */
	public interface Listener
	{
		void onActiveHuntChanged();
	}

	private final HuntManager huntManager;
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	@Getter
	private PuzzleHunt activeHunt;

	@Getter
	private HuntProgress activeProgress;

	@Inject
	ActiveHuntService(HuntManager huntManager)
	{
		this.huntManager = huntManager;
	}

	public void addListener(Listener l)
	{
		listeners.add(l);
	}

	public void removeListener(Listener l)
	{
		listeners.remove(l);
	}

	private void fireChanged()
	{
		for (Listener l : listeners)
		{
			try
			{
				l.onActiveHuntChanged();
			}
			catch (RuntimeException e)
			{
				log.debug("Listener threw", e);
			}
		}
	}

	/** Begins a hunt immediately. Loads (or initialises) progress for it. */
	public synchronized void start(PuzzleHunt hunt)
	{
		if (hunt == null)
		{
			return;
		}
		// Persist any in-flight hunt before swapping.
		if (activeHunt != null && activeProgress != null)
		{
			pauseTimer();
			persistQuietly();
		}
		this.activeHunt = hunt;
		this.activeProgress = huntManager.loadProgress(hunt.getId());
		if (activeProgress.isCompleted())
		{
			// Re-running a completed hunt — leave the splits intact for display
			// but don't restart the timer until the user resets.
			fireChanged();
			return;
		}
		startTimer();
		fireChanged();
	}

	/** Stops tracking the current hunt without clearing it from disk. */
	public synchronized void stop()
	{
		if (activeHunt == null)
		{
			return;
		}
		pauseTimer();
		persistQuietly();
		this.activeHunt = null;
		this.activeProgress = null;
		fireChanged();
	}

	/** Resumes the timer for the active hunt. No-op if there is no active hunt. */
	public synchronized void startTimer()
	{
		if (activeProgress == null || activeProgress.isCompleted())
		{
			return;
		}
		if (activeProgress.getSessionStartMillis() == 0L)
		{
			activeProgress.setSessionStartMillis(System.currentTimeMillis());
			persistQuietly();
			fireChanged();
		}
	}

	/** Folds the running session into the persisted accumulator. */
	public synchronized void pauseTimer()
	{
		if (activeProgress == null)
		{
			return;
		}
		long start = activeProgress.getSessionStartMillis();
		if (start != 0L)
		{
			long delta = Math.max(0L, System.currentTimeMillis() - start);
			activeProgress.setElapsedMillis(activeProgress.getElapsedMillis() + delta);
			activeProgress.setSessionStartMillis(0L);
			persistQuietly();
			fireChanged();
		}
	}

	/** True iff there is an active hunt with the timer currently running. */
	public synchronized boolean isTimerRunning()
	{
		return activeProgress != null && activeProgress.getSessionStartMillis() != 0L;
	}

	/** Total elapsed milliseconds for the active hunt, including any live session. */
	public synchronized long getElapsedMillis()
	{
		if (activeProgress == null)
		{
			return 0L;
		}
		long base = activeProgress.getElapsedMillis();
		long start = activeProgress.getSessionStartMillis();
		if (start != 0L)
		{
			base += Math.max(0L, System.currentTimeMillis() - start);
		}
		return base;
	}

	/**
	 * In treasure-trail mode, the index of the step currently being attempted.
	 * In diary mode this is meaningless; callers should use
	 * {@link #getCompletedStepIds()} instead.
	 */
	public synchronized int getCurrentStepIndex()
	{
		return activeProgress == null ? 0 : activeProgress.getCurrentStepIndex();
	}

	/** Completed step ids. */
	public synchronized Set<String> getCompletedStepIds()
	{
		if (activeProgress == null)
		{
			return Collections.emptySet();
		}
		return new java.util.LinkedHashSet<>(activeProgress.getStepSplits().keySet());
	}

	/**
	 * Marks the given step complete. In treasure-trail mode only the current
	 * step can be completed; out-of-order completions are ignored. In diary
	 * mode any uncompleted step can be completed.
	 *
	 * @return true if the call advanced state (the step was newly completed)
	 */
	public synchronized boolean completeStep(String stepId)
	{
		if (activeHunt == null || activeProgress == null || activeProgress.isCompleted())
		{
			return false;
		}
		List<PuzzleStep> steps = activeHunt.getSteps();
		if (steps == null || steps.isEmpty())
		{
			return false;
		}
		if (activeProgress.getStepSplits().containsKey(stepId))
		{
			return false;
		}

		if (activeHunt.getMode() == HuntMode.TREASURE_TRAIL)
		{
			int idx = activeProgress.getCurrentStepIndex();
			if (idx < 0 || idx >= steps.size())
			{
				return false;
			}
			PuzzleStep current = steps.get(idx);
			if (!current.getId().equals(stepId))
			{
				return false;
			}
			recordSplit(stepId);
			activeProgress.setCurrentStepIndex(idx + 1);
		}
		else
		{
			// DIARY: just check the id exists in the hunt.
			boolean found = false;
			for (PuzzleStep s : steps)
			{
				if (s.getId().equals(stepId))
				{
					found = true;
					break;
				}
			}
			if (!found)
			{
				return false;
			}
			recordSplit(stepId);
		}

		// End of hunt?
		if (activeProgress.getStepSplits().size() >= steps.size())
		{
			activeProgress.setCompleted(true);
			pauseTimer();
		}

		persistQuietly();
		fireChanged();
		return true;
	}

	/**
	 * Returns the {@link PuzzleStep} the player should currently focus on
	 * (treasure-trail mode), or {@code null} for diary mode / no active hunt.
	 */
	public synchronized PuzzleStep getCurrentStep()
	{
		if (activeHunt == null || activeProgress == null)
		{
			return null;
		}
		if (activeHunt.getMode() != HuntMode.TREASURE_TRAIL)
		{
			return null;
		}
		List<PuzzleStep> steps = activeHunt.getSteps();
		int idx = activeProgress.getCurrentStepIndex();
		if (idx < 0 || idx >= steps.size())
		{
			return null;
		}
		return steps.get(idx);
	}

	/**
	 * Steps the detector should currently watch for completion of. In
	 * treasure-trail mode that's only the current step; in diary mode it's
	 * every uncompleted step.
	 */
	public synchronized List<PuzzleStep> getActiveStepsForDetection()
	{
		if (activeHunt == null || activeProgress == null || activeProgress.isCompleted())
		{
			return Collections.emptyList();
		}
		List<PuzzleStep> steps = activeHunt.getSteps();
		if (activeHunt.getMode() == HuntMode.TREASURE_TRAIL)
		{
			PuzzleStep current = getCurrentStep();
			return current == null ? Collections.emptyList() : Collections.singletonList(current);
		}
		List<PuzzleStep> out = new ArrayList<>();
		for (PuzzleStep s : steps)
		{
			if (!activeProgress.getStepSplits().containsKey(s.getId()))
			{
				out.add(s);
			}
		}
		return out;
	}

	/** Wipes progress so the active (or named) hunt can be replayed from scratch. */
	public synchronized void reset(String huntId)
	{
		if (huntId == null)
		{
			return;
		}
		HuntProgress fresh = new HuntProgress();
		fresh.setHuntId(huntId);
		try
		{
			huntManager.saveProgress(fresh);
		}
		catch (IOException e)
		{
			log.debug("Failed to reset progress for {}", huntId, e);
		}
		if (activeHunt != null && huntId.equals(activeHunt.getId()))
		{
			this.activeProgress = fresh;
			startTimer();
			fireChanged();
		}
	}

	private void recordSplit(String stepId)
	{
		long now = getElapsedMillis();
		activeProgress.getStepSplits().put(stepId, now);
	}

	private void persistQuietly()
	{
		try
		{
			huntManager.saveProgress(activeProgress);
		}
		catch (IOException e)
		{
			log.debug("Failed to persist progress for hunt {}", activeProgress.getHuntId(), e);
		}
	}
}
