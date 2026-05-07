package com.puzzlehunt;

import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import com.puzzlehunt.model.PuzzleStep;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import net.runelite.client.ui.ColorScheme;

class SummaryView extends JPanel
{
	private final PuzzleHuntPanel host;
	private final JLabel title = new JLabel();
	private final JLabel total = new JLabel();
	private final JPanel splitsPanel = new JPanel();

	SummaryView(PuzzleHuntPanel host)
	{
		this.host = host;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);
		total.setForeground(Color.WHITE);
		total.setFont(total.getFont().deriveFont(20f));
		total.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(total);
		add(header, BorderLayout.NORTH);

		splitsPanel.setLayout(new BoxLayout(splitsPanel, BoxLayout.Y_AXIS));
		splitsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JScrollPane sp = new JScrollPane(splitsPanel);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(sp, BorderLayout.CENTER);

		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton reset = new JButton("Reset progress");
		reset.setAlignmentX(Component.LEFT_ALIGNMENT);
		reset.addActionListener(e ->
		{
			PuzzleHunt h = host.getActive().getActiveHunt();
			if (h != null)
			{
				host.getActive().reset(h.getId());
				host.showActive();
			}
		});
		controls.add(reset);
		JButton home = new JButton("← Back to hunts");
		home.setAlignmentX(Component.LEFT_ALIGNMENT);
		home.addActionListener(e -> host.showHome());
		controls.add(home);
		add(controls, BorderLayout.SOUTH);
	}

	void refresh()
	{
		PuzzleHunt hunt = host.getActive().getActiveHunt();
		HuntProgress progress = host.getActive().getActiveProgress();
		if (hunt == null || progress == null)
		{
			title.setText("No active hunt");
			total.setText("");
			splitsPanel.removeAll();
			splitsPanel.revalidate();
			splitsPanel.repaint();
			return;
		}
		title.setText(hunt.getName() + (progress.isCompleted() ? " — Complete!" : " — In progress"));
		total.setText(formatTime(progress.getElapsedMillis()));
		splitsPanel.removeAll();
		long previous = 0L;
		for (PuzzleStep step : hunt.getSteps())
		{
			Long split = progress.getStepSplits().get(step.getId());
			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel left = new JLabel(step.getTitle());
			left.setForeground(Color.WHITE);
			row.add(left, BorderLayout.WEST);
			if (split == null)
			{
				JLabel right = new JLabel("—");
				right.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				row.add(right, BorderLayout.EAST);
			}
			else
			{
				long delta = split - previous;
				previous = split;
				JLabel right = new JLabel(formatTime(split) + "  (+" + formatTime(delta) + ")");
				right.setForeground(Color.WHITE);
				row.add(right, BorderLayout.EAST);
			}
			splitsPanel.add(row);
			splitsPanel.add(Box.createVerticalStrut(2));
		}
		splitsPanel.revalidate();
		splitsPanel.repaint();
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
