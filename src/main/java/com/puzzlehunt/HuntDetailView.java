package com.puzzlehunt;

import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * Detail view for a single saved hunt — Start now, Sync start (with countdown
 * to the next whole minute, ≥60s), Edit and Delete.
 */
@Slf4j
class HuntDetailView extends JPanel
{
	private final PuzzleHuntPanel host;
	private final JLabel title = new JLabel();
	private final JLabel meta = new JLabel();
	private final JLabel progressLabel = new JLabel();
	private final JLabel countdown = new JLabel(" ");
	private final JButton startNow = new JButton("Start now");
	private final JButton syncStart = new JButton("Sync start");
	private final JButton cancelSync = new JButton("Cancel countdown");
	private final JButton edit = new JButton("Edit");
	private final JButton delete = new JButton("Delete");
	private final JButton back = new JButton("← Back");

	private PuzzleHunt hunt;
	private long countdownTargetMs;

	HuntDetailView(PuzzleHuntPanel host)
	{
		this.host = host;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(e ->
		{
			cancelCountdownIfRunning();
			host.showHome();
		});
		header.add(back);
		header.add(Box.createVerticalStrut(6));
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);
		meta.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		meta.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(meta);
		progressLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(progressLabel);
		add(header, BorderLayout.NORTH);

		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		startNow.addActionListener(e -> onStartNow());
		buttons.add(startNow);
		syncStart.addActionListener(e -> onSyncStart());
		buttons.add(syncStart);
		countdown.setForeground(Color.WHITE);
		buttons.add(countdown);
		cancelSync.addActionListener(e -> cancelCountdownIfRunning());
		cancelSync.setVisible(false);
		buttons.add(cancelSync);
		edit.addActionListener(e -> host.showCreate(hunt));
		buttons.add(edit);
		delete.addActionListener(e -> onDelete());
		buttons.add(delete);
		add(buttons, BorderLayout.CENTER);
	}

	void setHunt(PuzzleHunt hunt)
	{
		this.hunt = hunt;
		cancelCountdownIfRunning();
		refresh();
	}

	private void refresh()
	{
		if (hunt == null)
		{
			title.setText("(no hunt)");
			meta.setText("");
			progressLabel.setText("");
			return;
		}
		title.setText(hunt.getName());
		String modeLabel = hunt.getMode() == HuntMode.TREASURE_TRAIL ? "Treasure Trail" : "Diary";
		int total = hunt.getSteps() == null ? 0 : hunt.getSteps().size();
		meta.setText(modeLabel + " — " + total + " step" + (total == 1 ? "" : "s"));
		HuntProgress p = host.getHuntManager().loadProgress(hunt.getId());
		int done = p.getStepSplits() == null ? 0 : p.getStepSplits().size();
		progressLabel.setText("Progress: " + done + "/" + total + (p.isCompleted() ? " — completed" : ""));
	}

	void tick()
	{
		if (countdownTargetMs == 0L)
		{
			return;
		}
		long remaining = countdownTargetMs - System.currentTimeMillis();
		if (remaining <= 0L)
		{
			countdown.setText("Started!");
			countdownTargetMs = 0L;
			cancelSync.setVisible(false);
			syncStart.setEnabled(true);
			startNow.setEnabled(true);
			host.getActive().start(hunt);
			host.getDetector().resetInventoryBaseline();
			host.showActive();
		}
		else
		{
			long sec = (remaining + 999L) / 1000L;
			countdown.setText("Sync starting in " + sec + "s…");
		}
	}

	private void onStartNow()
	{
		if (hunt == null)
		{
			return;
		}
		host.getActive().start(hunt);
		host.getDetector().resetInventoryBaseline();
		host.showActive();
	}

	private void onSyncStart()
	{
		if (hunt == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		// Round up to the next whole minute, but ensure at least 60s of countdown.
		long minMs = now + 60_000L;
		long target = ((minMs / 60_000L) + (minMs % 60_000L == 0 ? 0 : 1)) * 60_000L;
		countdownTargetMs = target;
		cancelSync.setVisible(true);
		syncStart.setEnabled(false);
		startNow.setEnabled(false);
		tick();
	}

	private void cancelCountdownIfRunning()
	{
		if (countdownTargetMs != 0L)
		{
			countdownTargetMs = 0L;
			countdown.setText(" ");
			cancelSync.setVisible(false);
			syncStart.setEnabled(true);
			startNow.setEnabled(true);
		}
	}

	private void onDelete()
	{
		if (hunt == null)
		{
			return;
		}
		int choice = JOptionPane.showConfirmDialog(this,
			"Delete \"" + hunt.getName() + "\" and its progress?",
			"Puzzle hunt", JOptionPane.YES_NO_OPTION);
		if (choice != JOptionPane.YES_OPTION)
		{
			return;
		}
		try
		{
			host.getHuntManager().deleteHunt(hunt.getId());
			if (host.getActive().getActiveHunt() != null && hunt.getId().equals(host.getActive().getActiveHunt().getId()))
			{
				host.getActive().stop();
			}
			host.showHome();
		}
		catch (IOException ex)
		{
			log.debug("Failed to delete hunt", ex);
			JOptionPane.showMessageDialog(this, "Could not delete: " + ex.getMessage(),
				"Puzzle hunt", JOptionPane.ERROR_MESSAGE);
		}
	}
}
