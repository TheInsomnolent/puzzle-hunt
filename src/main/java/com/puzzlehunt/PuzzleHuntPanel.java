package com.puzzlehunt;

import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar entry-point for the plugin.
 *
 * <p>Currently exposes the three top-level actions described in the issue —
 * <em>Create</em>, <em>Import</em>, and <em>Start</em> — plus a list of saved
 * hunts with their completion progress. Clicking individual hunts and the
 * full creation / active-hunt flows will be wired up in follow-up PRs; this
 * panel only provides the navigation shell.
 */
@Slf4j
@Singleton
public class PuzzleHuntPanel extends PluginPanel
{
	private final HuntManager huntManager;

	private final JPanel huntListPanel = new JPanel();

	@Inject
	PuzzleHuntPanel(HuntManager huntManager)
	{
		super();
		this.huntManager = huntManager;

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildHeader(), BorderLayout.NORTH);
		add(buildActionButtons(), BorderLayout.CENTER);

		huntListPanel.setLayout(new BoxLayout(huntListPanel, BoxLayout.Y_AXIS));
		huntListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(huntListPanel, BorderLayout.SOUTH);
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel title = new JLabel("Puzzle Hunt", SwingConstants.LEFT);
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.NORTH);
		JLabel subtitle = new JLabel("Build, run and share custom hunts");
		subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(subtitle, BorderLayout.SOUTH);
		return header;
	}

	private JPanel buildActionButtons()
	{
		JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton create = new JButton("Create new hunt");
		create.addActionListener(e -> onCreate());
		buttons.add(create);

		JButton importBtn = new JButton("Import from JSON…");
		importBtn.addActionListener(e -> onImport());
		buttons.add(importBtn);

		JButton start = new JButton("Start a hunt");
		start.addActionListener(e -> onStart());
		buttons.add(start);

		return buttons;
	}

	/** Refreshes the list of saved hunts from disk. Safe to call from any thread. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			huntListPanel.removeAll();
			Map<PuzzleHunt, HuntProgress> all = huntManager.loadAllWithProgress();
			if (all.isEmpty())
			{
				JLabel empty = new JLabel("No hunts saved yet.");
				empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				empty.setAlignmentX(LEFT_ALIGNMENT);
				huntListPanel.add(Box.createVerticalStrut(8));
				huntListPanel.add(empty);
			}
			else
			{
				huntListPanel.add(Box.createVerticalStrut(8));
				for (Map.Entry<PuzzleHunt, HuntProgress> entry : all.entrySet())
				{
					huntListPanel.add(buildHuntRow(entry.getKey(), entry.getValue()));
					huntListPanel.add(Box.createVerticalStrut(4));
				}
			}
			huntListPanel.revalidate();
			huntListPanel.repaint();
		});
	}

	private JPanel buildHuntRow(PuzzleHunt hunt, HuntProgress progress)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

		JLabel name = new JLabel(hunt.getName());
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.NORTH);

		int total = hunt.getSteps() == null ? 0 : hunt.getSteps().size();
		int done = progress.getStepSplits() == null ? 0 : progress.getStepSplits().size();
		JLabel meta = new JLabel(hunt.getMode() + " — " + done + "/" + total + " steps");
		meta.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(meta, BorderLayout.SOUTH);
		return row;
	}

	private void onCreate()
	{
		// The full creation UX is deferred to a follow-up PR. For the base
		// initialisation we save an empty hunt so users can verify the round
		// trip to disk works end-to-end.
		PuzzleHunt hunt = new PuzzleHunt();
		hunt.setName("New hunt");
		try
		{
			huntManager.saveHunt(hunt);
			refresh();
		}
		catch (IOException ex)
		{
			log.debug("Failed to save new hunt", ex);
			showError("Could not save the new hunt: " + ex.getMessage());
		}
	}

	private void onImport()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Import puzzle hunt");
		chooser.setFileFilter(new FileNameExtensionFilter("Puzzle hunt JSON (*.json)", "json"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		try
		{
			String json = new String(Files.readAllBytes(chooser.getSelectedFile().toPath()), StandardCharsets.UTF_8);
			huntManager.importHunt(json);
			refresh();
		}
		catch (IOException ex)
		{
			log.debug("Failed to import hunt", ex);
			showError("Could not import hunt: " + ex.getMessage());
		}
	}

	private void onStart()
	{
		// Selecting and launching a hunt (including the sync-start countdown)
		// is implemented in a follow-up PR. Surface a helpful message for now
		// instead of silently doing nothing.
		showInfo("Hunt selection and start flow is not implemented yet.");
	}

	private void showError(String msg)
	{
		JOptionPane.showMessageDialog(this, msg, "Puzzle hunt", JOptionPane.ERROR_MESSAGE);
	}

	private void showInfo(String msg)
	{
		JOptionPane.showMessageDialog(this, msg, "Puzzle hunt", JOptionPane.INFORMATION_MESSAGE);
	}
}
