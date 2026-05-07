package com.puzzlehunt;

import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import com.puzzlehunt.model.PuzzleStep;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;

/**
 * Live view for an in-progress hunt. Steps are rendered with the visibility
 * rules from the issue:
 *
 * <ul>
 *   <li>Treasure trail — completed: collapsed, green tick. Current: expanded
 *       with clue. Future: title replaced with "??????".</li>
 *   <li>Diary — completed: collapsed, greyed. Uncompleted: expanded.</li>
 * </ul>
 */
class ActiveHuntView extends JPanel
{
	private static final Color DONE_GREEN = new Color(120, 200, 120);
	private static final Color FUTURE_GREY = new Color(120, 120, 120);

	private final PuzzleHuntPanel host;
	private final JLabel title = new JLabel();
	private final JLabel timer = new JLabel("00:00.0");
	private final JButton pauseBtn = new JButton("Pause");
	private final JButton stopBtn = new JButton("Stop");
	private final JButton backBtn = new JButton("← Hide");
	private final JPanel stepsPanel = new JPanel();

	ActiveHuntView(PuzzleHuntPanel host)
	{
		this.host = host;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		backBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		backBtn.addActionListener(e -> host.showHome());
		header.add(backBtn);
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);
		timer.setForeground(Color.WHITE);
		timer.setFont(timer.getFont().deriveFont(20f));
		timer.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(timer);

		JPanel controls = new JPanel(new GridLayout(1, 2, 4, 0));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		pauseBtn.addActionListener(e ->
		{
			if (host.getActive().isTimerRunning())
			{
				host.getActive().pauseTimer();
			}
			else
			{
				host.getActive().startTimer();
			}
		});
		controls.add(pauseBtn);
		stopBtn.addActionListener(e ->
		{
			host.getActive().stop();
			host.showHome();
		});
		controls.add(stopBtn);
		controls.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(controls);

		add(header, BorderLayout.NORTH);

		stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.Y_AXIS));
		stepsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JScrollPane sp = new JScrollPane(stepsPanel);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(sp, BorderLayout.CENTER);
	}

	void refresh()
	{
		PuzzleHunt hunt = host.getActive().getActiveHunt();
		HuntProgress progress = host.getActive().getActiveProgress();
		if (hunt == null || progress == null)
		{
			title.setText("No active hunt");
			timer.setText("00:00.0");
			stepsPanel.removeAll();
			stepsPanel.revalidate();
			stepsPanel.repaint();
			return;
		}
		title.setText(hunt.getName() + " — " + (hunt.getMode() == HuntMode.TREASURE_TRAIL ? "Treasure Trail" : "Diary"));
		pauseBtn.setText(host.getActive().isTimerRunning() ? "Pause" : "Resume");
		rebuildSteps(hunt, progress);
		tick();
	}

	void tick()
	{
		timer.setText(formatTime(host.getActive().getElapsedMillis()));
	}

	private void rebuildSteps(PuzzleHunt hunt, HuntProgress progress)
	{
		stepsPanel.removeAll();
		Set<String> done = host.getActive().getCompletedStepIds();
		int currentIdx = host.getActive().getCurrentStepIndex();
		boolean treasure = hunt.getMode() == HuntMode.TREASURE_TRAIL;
		for (int i = 0; i < hunt.getSteps().size(); i++)
		{
			PuzzleStep step = hunt.getSteps().get(i);
			boolean isDone = done.contains(step.getId());
			boolean isCurrent = treasure ? (i == currentIdx) : !isDone;
			boolean isFuture = treasure && !isDone && i > currentIdx;
			stepsPanel.add(buildStepBox(step, i, isDone, isCurrent, isFuture, progress));
			stepsPanel.add(Box.createVerticalStrut(4));
		}
		stepsPanel.revalidate();
		stepsPanel.repaint();
	}

	private JPanel buildStepBox(PuzzleStep step, int index, boolean done, boolean current, boolean future, HuntProgress progress)
	{
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		box.setAlignmentX(Component.LEFT_ALIGNMENT);

		String displayTitle;
		if (done)
		{
			displayTitle = "✓ " + (index + 1) + ". " + step.getTitle();
		}
		else if (future)
		{
			displayTitle = (index + 1) + ". ??????";
		}
		else
		{
			displayTitle = (index + 1) + ". " + step.getTitle();
		}
		JLabel titleLabel = new JLabel(displayTitle);
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (done)
		{
			titleLabel.setForeground(DONE_GREEN);
		}
		else if (future)
		{
			titleLabel.setForeground(FUTURE_GREY);
		}
		else
		{
			titleLabel.setForeground(Color.WHITE);
		}
		box.add(titleLabel);

		// Body — only shown when expanded.
		boolean expanded = (current && !done) || (!future && !done);
		if (expanded && !done && !future)
		{
			JTextArea body = new JTextArea(describe(step));
			body.setEditable(false);
			body.setLineWrap(true);
			body.setWrapStyleWord(true);
			body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			body.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			body.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
			body.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.add(body);
		}
		if (done)
		{
			Long split = progress.getStepSplits().get(step.getId());
			if (split != null)
			{
				JLabel sl = new JLabel("Split: " + formatTime(split));
				sl.setForeground(DONE_GREEN);
				sl.setAlignmentX(Component.LEFT_ALIGNMENT);
				box.add(sl);
			}
		}
		return box;
	}

	private static String describe(PuzzleStep step)
	{
		if (step.getClueText() != null && !step.getClueText().trim().isEmpty())
		{
			return step.getClueText();
		}
		switch (step.getType())
		{
			case GET_ITEM:
				StringBuilder sb = new StringBuilder("Get item: ");
				sb.append(step.getItemName().isEmpty() ? ("id " + step.getItemId()) : step.getItemName());
				switch (step.getItemSource())
				{
					case ANY: sb.append(" (any source)"); break;
					case MONSTER_DROP:
						sb.append(" — drop from ");
						sb.append(step.getMonsterNames().isEmpty() ? "configured monsters" : String.join(", ", step.getMonsterNames()));
						break;
					case GROUND_SPAWN: sb.append(" — pick up from a ground spawn"); break;
				}
				return sb.toString();
			case LOCATION_PUZZLE:
				switch (step.getLocationSubtype())
				{
					case NPC: return "Talk to / right-click \"" + step.getNpcName() + "\" → Complete clue step";
					case TILES: return "Find the right tile (" + step.getTiles().size() + " options)";
				}
		}
		return "";
	}

	private static String formatTime(long ms)
	{
		long totalSec = ms / 1000L;
		long mins = totalSec / 60L;
		long secs = totalSec % 60L;
		long tenths = (ms % 1000L) / 100L;
		return String.format("%02d:%02d.%d", mins, secs, tenths);
	}
}
