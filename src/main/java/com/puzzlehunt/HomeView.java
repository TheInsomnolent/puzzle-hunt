package com.puzzlehunt;

import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

@Slf4j
class HomeView extends JPanel
{
	private final PuzzleHuntPanel host;
	private final JPanel huntList = new JPanel();

	HomeView(PuzzleHuntPanel host)
	{
		this.host = host;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildHeader(), BorderLayout.NORTH);

		JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton create = PanelComponents.button("Create new hunt");
		create.addActionListener(e -> host.showCreate(null));
		buttons.add(create);
		JButton importBtn = PanelComponents.button("Import from clipboard");
		importBtn.addActionListener(e -> onImport());
		buttons.add(importBtn);

		JPanel center = new JPanel(new BorderLayout(0, 8));
		center.setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.add(buttons, BorderLayout.NORTH);

		huntList.setLayout(new BoxLayout(huntList, BoxLayout.Y_AXIS));
		huntList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.add(huntList, BorderLayout.CENTER);

		add(center, BorderLayout.CENTER);
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

	void refresh()
	{
		huntList.removeAll();
		Map<PuzzleHunt, HuntProgress> all = host.getHuntManager().loadAllWithProgress();
		if (all.isEmpty())
		{
			JLabel empty = new JLabel("No hunts saved yet.");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setAlignmentX(LEFT_ALIGNMENT);
			huntList.add(Box.createVerticalStrut(8));
			huntList.add(empty);
		}
		else
		{
			huntList.add(Box.createVerticalStrut(8));
			for (Map.Entry<PuzzleHunt, HuntProgress> entry : all.entrySet())
			{
				JPanel row = buildHuntRow(entry.getKey(), entry.getValue());
				huntList.add(row);
				huntList.add(Box.createVerticalStrut(4));
			}
		}
		huntList.revalidate();
		huntList.repaint();
	}

	private JPanel buildHuntRow(PuzzleHunt hunt, HuntProgress progress)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = new JLabel(hunt.getName());
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.NORTH);

		int total = hunt.getSteps() == null ? 0 : hunt.getSteps().size();
		int done = progress.getStepSplits() == null ? 0 : progress.getStepSplits().size();
		String modeLabel = hunt.getMode() == HuntMode.TREASURE_TRAIL ? "Treasure Trail" : "Diary";
		JLabel meta = new JLabel(modeLabel + " — " + done + "/" + total + " steps"
			+ (progress.isCompleted() ? " ✓" : ""));
		meta.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(meta, BorderLayout.SOUTH);

		row.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent e) { host.showDetail(hunt); }
		});
		return row;
	}

	private void onImport()
	{
		String code;
		try
		{
			Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
			if (cb == null || !cb.isDataFlavorAvailable(DataFlavor.stringFlavor))
			{
				JOptionPane.showMessageDialog(this, "Clipboard does not contain text.",
					"Puzzle hunt", JOptionPane.WARNING_MESSAGE);
				return;
			}
			code = (String) cb.getData(DataFlavor.stringFlavor);
		}
		catch (IllegalStateException | UnsupportedFlavorException | IOException ex)
		{
			log.debug("Failed to read clipboard", ex);
			JOptionPane.showMessageDialog(this, "Could not read clipboard: " + ex.getMessage(),
				"Puzzle hunt", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (code == null || code.trim().isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Clipboard is empty.",
				"Puzzle hunt", JOptionPane.WARNING_MESSAGE);
			return;
		}
		try
		{
			host.getHuntManager().importHunt(code.trim());
			refresh();
		}
		catch (IOException | IllegalArgumentException ex)
		{
			log.debug("Failed to import hunt", ex);
			JOptionPane.showMessageDialog(this, "Could not import hunt: " + ex.getMessage(),
				"Puzzle hunt", JOptionPane.ERROR_MESSAGE);
		}
	}
}
