package com.puzzlehunt;

import com.puzzlehunt.model.ClueType;
import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import com.puzzlehunt.model.PuzzleStep;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
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
	private final JButton pauseBtn = PanelComponents.styleButton(new JButton("Pause"));
	private final JButton stopBtn = PanelComponents.styleButton(new JButton("Stop"));
	private final JButton resetBtn = PanelComponents.styleButton(new JButton("Reset"));
	private final JButton backBtn = PanelComponents.styleButton(new JButton("← Hide"));
	private final JPanel stepsPanel = new JPanel();
	private final java.util.Map<String, JProgressBar> stepBars = new java.util.HashMap<>();

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

		JPanel controls = new JPanel(new GridLayout(1, 3, 4, 0));
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
		resetBtn.addActionListener(e ->
		{
			PuzzleHunt h = host.getActive().getActiveHunt();
			if (h == null)
			{
				return;
			}
			int choice = javax.swing.JOptionPane.showConfirmDialog(this,
				"Reset all progress and the timer for \"" + h.getName() + "\"?",
				"Reset hunt", javax.swing.JOptionPane.OK_CANCEL_OPTION,
				javax.swing.JOptionPane.WARNING_MESSAGE);
			if (choice == javax.swing.JOptionPane.OK_OPTION)
			{
				host.getActive().reset(h.getId());
				host.getDetector().resetForNewHunt();
				refresh();
			}
		});
		controls.add(resetBtn);
		controls.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(controls);

		add(header, BorderLayout.NORTH);

		stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.Y_AXIS));
		stepsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(stepsPanel, BorderLayout.CENTER);
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
		timer.setText(TimeFormat.formatTime(host.getActive().getElapsedMillis()));
		for (java.util.Map.Entry<String, JProgressBar> e : stepBars.entrySet())
		{
			updateProgressBar(e.getKey(), e.getValue());
		}
	}

	private void rebuildSteps(PuzzleHunt hunt, HuntProgress progress)
	{
		stepsPanel.removeAll();
		stepBars.clear();
		Set<String> done = host.getActive().getCompletedStepIds();
		ActiveHuntService svc = host.getActive();
		java.util.Set<String> activeIds = new java.util.HashSet<>();
		for (PuzzleStep s : svc.getActiveStepsForDetection()) activeIds.add(s.getId());
		boolean treasure = hunt.getMode() == HuntMode.TREASURE_TRAIL;
		// Detect whether the hunt actually uses chapters so we only show
		// dividers when meaningful.
		int maxChapter = 0;
		for (PuzzleStep s : hunt.getSteps()) if (s.getChapter() > maxChapter) maxChapter = s.getChapter();
		boolean hasChapters = treasure && maxChapter > 0;
		int lastChapter = -1;
		for (int i = 0; i < hunt.getSteps().size(); i++)
		{
			PuzzleStep step = hunt.getSteps().get(i);
			if (hasChapters && step.getChapter() != lastChapter)
			{
				stepsPanel.add(buildChapterDivider(hunt, step.getChapter(), svc));
				stepsPanel.add(Box.createVerticalStrut(2));
				lastChapter = step.getChapter();
			}
			boolean isDone = done.contains(step.getId());
			boolean isCurrent = treasure ? activeIds.contains(step.getId()) : !isDone;
			boolean isFuture = svc.isStepHidden(step);
			stepsPanel.add(buildStepBox(step, i, isDone, isCurrent, isFuture, progress));
			stepsPanel.add(Box.createVerticalStrut(4));
		}
		stepsPanel.revalidate();
		stepsPanel.repaint();
	}

	private JPanel buildChapterDivider(PuzzleHunt hunt, int chapterIdx, ActiveHuntService svc)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		int active = svc.getActiveChapter();
		String state;
		if (active < 0 || chapterIdx < active) state = "done";
		else if (chapterIdx == active) state = "active";
		else state = "locked";
		HuntMode m = hunt.getChapterMode(chapterIdx);
		String modeLabel = m == HuntMode.DIARY ? "any order" : "in order";
		JLabel left = new JLabel("Chapter " + (chapterIdx + 1) + " — " + modeLabel);
		left.setForeground(state.equals("locked") ? FUTURE_GREY : Color.WHITE);
		row.add(left, BorderLayout.WEST);
		JLabel right = new JLabel(state);
		right.setForeground(state.equals("active") ? new Color(120, 220, 120)
			: state.equals("done") ? DONE_GREEN : FUTURE_GREY);
		row.add(right, BorderLayout.EAST);
		return row;
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

			JLabel imgLabel = renderImage(step);
			if (imgLabel != null)
			{
				imgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				box.add(Box.createVerticalStrut(4));
				box.add(imgLabel);
			}

			JProgressBar bar = buildProgressBar(step);
			if (bar != null)
			{
				bar.setAlignmentX(Component.LEFT_ALIGNMENT);
				bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
				box.add(Box.createVerticalStrut(4));
				box.add(bar);
				stepBars.put(step.getId(), bar);
			}

			if (step.getType() == ClueType.PASSWORD)
			{
				JPanel pwRow = new JPanel(new BorderLayout(4, 0));
				pwRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				pwRow.setAlignmentX(Component.LEFT_ALIGNMENT);
				JTextField input = new JTextField();
				JButton submit = PanelComponents.styleButton(new JButton("Submit"));
				Runnable trySubmit = () ->
				{
					boolean ok = host.getDetector().submitPassword(step.getId(), input.getText());
					if (!ok)
					{
						input.setBackground(new Color(80, 30, 30));
					}
					else
					{
						refresh();
					}
				};
				input.addActionListener(e -> trySubmit.run());
				submit.addActionListener(e -> trySubmit.run());
				pwRow.add(input, BorderLayout.CENTER);
				pwRow.add(submit, BorderLayout.EAST);
				box.add(Box.createVerticalStrut(4));
				box.add(pwRow);
			}
		}
		if (done)
		{
			Long split = progress.getStepSplits().get(step.getId());
			if (split != null)
			{
				JLabel sl = new JLabel("Split: " + TimeFormat.formatTime(split));
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
			{
				StringBuilder sb = new StringBuilder("Get item: ");
				sb.append(step.getItemName().isEmpty() ? ("id " + step.getItemId()) : step.getItemName());
				if (step.getAdditionalItemNames() != null && !step.getAdditionalItemNames().isEmpty())
				{
					sb.append(" / ").append(String.join(" / ", step.getAdditionalItemNames()));
				}
				if (step.getRequiredCount() > 1)
				{
					sb.append(" × ").append(step.getRequiredCount());
				}
				switch (step.getItemSource())
				{
					case ANY: sb.append(" (any source)"); break;
					case MONSTER_DROP:
						sb.append(" — drop from ");
						sb.append(step.getMonsterNames().isEmpty() ? "any monster" : String.join(", ", step.getMonsterNames()));
						break;
					case SKILLING_RESOURCE: sb.append(" — gather via skilling (no banks/shops)"); break;
					case GROUND_SPAWN: sb.append(" — pick up from a ground spawn"); break;
				}
				return sb.toString();
			}
			case LOCATION_PUZZLE:
				switch (step.getLocationSubtype())
				{
					case NPC: return "Stand next to \"" + step.getNpcName() + "\"";
					case TILES: return "Find the right tile (" + step.getTiles().size() + " options)";
				}
				return "";
			case KILL_MONSTER:
			{
				StringBuilder sb = new StringBuilder("Kill ");
				sb.append(Math.max(1, step.getKillCount())).append(" × ");
				sb.append(step.getKillMonsterNames() == null || step.getKillMonsterNames().isEmpty()
					? "any monster"
					: String.join(" / ", step.getKillMonsterNames()));
				return sb.toString();
			}
			case DIE: return "Die.";
			case GAIN_GP: return "Gain " + step.getGpAmount() + " gp";
			case GAIN_XP:
			{
				String skills = step.getXpSkills() == null || step.getXpSkills().isEmpty()
					? "any skill"
					: String.join(" / ", step.getXpSkills());
				return "Gain " + step.getXpAmount() + " xp in " + skills;
			}
			case PASSWORD: return "Enter the answer.";
		}
		return "";
	}

	private static JLabel renderImage(PuzzleStep step)
	{
		String b64 = step.getImageBase64();
		if (b64 == null || b64.isEmpty()) return null;
		try
		{
			byte[] data = Base64.getDecoder().decode(b64);
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
			if (img == null) return null;
			Image scaled = img.getWidth() > 200
				? img.getScaledInstance(200, -1, Image.SCALE_SMOOTH)
				: img;
			JLabel label = new JLabel(new ImageIcon(scaled));
			label.setToolTipText("Click to enlarge");
			label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			final BufferedImage full = img;
			label.addMouseListener(new MouseAdapter()
			{
				@Override public void mouseClicked(MouseEvent e) { showFullImage(label, full); }
			});
			return label;
		}
		catch (IOException | IllegalArgumentException ex)
		{
			return null;
		}
	}

	private static void showFullImage(Component anchor, BufferedImage img)
	{
		java.awt.Window owner = SwingUtilities.getWindowAncestor(anchor);
		JDialog dlg = owner instanceof java.awt.Frame
			? new JDialog((java.awt.Frame) owner, "Clue image", false)
			: new JDialog((java.awt.Dialog) null, "Clue image", false);
		JLabel full = new JLabel(new ImageIcon(img));
		JScrollPane sp = new JScrollPane(full);
		java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		int w = Math.min(img.getWidth() + 32, (int) (screen.width * 0.9));
		int h = Math.min(img.getHeight() + 32, (int) (screen.height * 0.9));
		sp.setPreferredSize(new Dimension(w, h));
		dlg.setContentPane(sp);
		dlg.pack();
		dlg.setLocationRelativeTo(anchor);
		dlg.setVisible(true);
	}

	private JProgressBar buildProgressBar(PuzzleStep step)
	{
		ProgressSpec spec = progressSpec(step);
		if (spec == null) return null;
		int current = Math.min(spec.required, host.getDetector().getStepProgress(step.getId()));
		JProgressBar bar = new JProgressBar(0, spec.required);
		bar.setValue(current);
		bar.setStringPainted(true);
		bar.setString(current + " / " + spec.required + spec.suffix);
		return bar;
	}

	private void updateProgressBar(String stepId, JProgressBar bar)
	{
		PuzzleHunt hunt = host.getActive().getActiveHunt();
		if (hunt == null) return;
		PuzzleStep step = null;
		for (PuzzleStep s : hunt.getSteps())
		{
			if (stepId.equals(s.getId())) { step = s; break; }
		}
		if (step == null) return;
		ProgressSpec spec = progressSpec(step);
		if (spec == null) return;
		int current = Math.min(spec.required, host.getDetector().getStepProgress(stepId));
		if (bar.getMaximum() != spec.required) bar.setMaximum(spec.required);
		bar.setValue(current);
		bar.setString(current + " / " + spec.required + spec.suffix);
	}

	private static ProgressSpec progressSpec(PuzzleStep step)
	{
		switch (step.getType())
		{
			case GET_ITEM:
				if (step.getRequiredCount() <= 1) return null;
				return new ProgressSpec(step.getRequiredCount(), " items");
			case KILL_MONSTER:
				int kc = Math.max(1, step.getKillCount());
				if (kc <= 1) return null;
				return new ProgressSpec(kc, " kills");
			case GAIN_GP:
				if (step.getGpAmount() <= 0) return null;
				return new ProgressSpec(step.getGpAmount(), " gp");
			case GAIN_XP:
				if (step.getXpAmount() <= 0) return null;
				return new ProgressSpec(step.getXpAmount(), " xp");
			default:
				return null;
		}
	}

	private static final class ProgressSpec
	{
		final int required;
		final String suffix;
		ProgressSpec(int required, String suffix) { this.required = required; this.suffix = suffix; }
	}
}
