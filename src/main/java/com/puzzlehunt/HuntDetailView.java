package com.puzzlehunt;

import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.HuntProgress;
import com.puzzlehunt.model.PuzzleHunt;
import com.puzzlehunt.model.SerializedTile;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Detail view for a single saved hunt — Start now, Sync start (with countdown
 * to the next whole minute, ≥60s), Edit and Delete.
 */
@Slf4j
class HuntDetailView extends JPanel
{
	/** Sound played for each of the 3, 2, 1 ticks (iron_door_open). */
	private static final int SOUND_TICK = 71;
	/** Sound played at GO! (quick_teleport). */
	private static final int SOUND_GO = 198;
	private final PuzzleHuntPanel host;
	private final JLabel title = new JLabel();
	private final JLabel meta = new JLabel();
	private final JLabel progressLabel = new JLabel();
	private final JLabel countdown = new JLabel(" ");
	private final JPanel briefingPanel = new JPanel();

	/** Snapshot of inventory item counts populated on the client thread. UI reads this. */
	private volatile Map<Integer, Integer> inventorySnapshot = java.util.Collections.emptyMap();
	private final JButton startNow = PanelComponents.styleButton(new JButton("Start now"));
	private final JButton syncStart = PanelComponents.styleButton(new JButton("Sync start"));
	private final JButton cancelSync = PanelComponents.styleButton(new JButton("Cancel countdown"));
	private final JButton edit = PanelComponents.styleButton(new JButton("Edit"));
	private final JButton resetBtn = PanelComponents.styleButton(new JButton("Reset progress"));
	private final JButton exportBtn = PanelComponents.styleButton(new JButton("Export (copy code)"));
	private final JButton delete = PanelComponents.styleButton(new JButton("Delete"));
	private final JButton back = PanelComponents.styleButton(new JButton("← Back"));

	private PuzzleHunt hunt;
	private long countdownTargetMs;
	/** Used so we only play each countdown sound once per second. */
	private int lastCountdownDigit = -1;

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

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);

		briefingPanel.setLayout(new BoxLayout(briefingPanel, BoxLayout.Y_AXIS));
		briefingPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		briefingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttons.add(briefingPanel);
		buttons.add(Box.createVerticalStrut(8));

		startNow.addActionListener(e -> onStartNow());
		addStacked(buttons, startNow);
		syncStart.addActionListener(e -> onSyncStart());
		addStacked(buttons, syncStart);
		countdown.setForeground(Color.WHITE);
		countdown.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttons.add(countdown);
		cancelSync.addActionListener(e -> cancelCountdownIfRunning());
		cancelSync.setVisible(false);
		addStacked(buttons, cancelSync);
		edit.addActionListener(e -> host.showCreate(hunt));
		addStacked(buttons, edit);
		resetBtn.addActionListener(e -> onReset());
		addStacked(buttons, resetBtn);
		exportBtn.addActionListener(e -> onExport());
		addStacked(buttons, exportBtn);
		delete.addActionListener(e -> onDelete());
		addStacked(buttons, delete);
		add(buttons, BorderLayout.CENTER);
	}

	/**
	 * Add a button to a {@link BoxLayout} column without letting it
	 * stretch to fill leftover vertical space (the GridLayout default
	 * made every button render as a tall square).
	 */
	private static void addStacked(JPanel parent, JButton b)
	{
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension pref = b.getPreferredSize();
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
		parent.add(b);
		parent.add(Box.createVerticalStrut(4));
	}

	void setHunt(PuzzleHunt hunt)
	{
		this.hunt = hunt;
		cancelCountdownIfRunning();
		refresh();
		refreshInventorySnapshot();
	}

	private void refresh()
	{
		if (hunt == null)
		{
			title.setText("(no hunt)");
			meta.setText("");
			progressLabel.setText("");
			briefingPanel.removeAll();
			briefingPanel.revalidate();
			briefingPanel.repaint();
			return;
		}
		title.setText(hunt.getName());
		String modeLabel = hunt.getMode() == HuntMode.TREASURE_TRAIL ? "Treasure Trail" : "Diary";
		int total = hunt.getSteps() == null ? 0 : hunt.getSteps().size();
		meta.setText(modeLabel + " — " + total + " step" + (total == 1 ? "" : "s"));
		HuntProgress p = host.getHuntManager().loadProgress(hunt.getId());
		int done = p.getStepSplits() == null ? 0 : p.getStepSplits().size();
		progressLabel.setText("Progress: " + done + "/" + total + (p.isCompleted() ? " — completed" : ""));
		resetBtn.setVisible(done > 0 || p.isCompleted());
		rebuildBriefing();
	}

	private void rebuildBriefing()
	{
		briefingPanel.removeAll();
		if (hunt == null)
		{
			briefingPanel.revalidate();
			briefingPanel.repaint();
			return;
		}
		String instructions = hunt.getStartingInstructions();
		SerializedTile tile = hunt.getStartingTile();
		List<Integer> ids = hunt.getStartingItemIds();
		boolean hasInstructions = instructions != null && !instructions.trim().isEmpty();
		boolean hasTile = tile != null;
		boolean hasItems = ids != null && !ids.isEmpty();
		if (!hasInstructions && !hasTile && !hasItems)
		{
			briefingPanel.revalidate();
			briefingPanel.repaint();
			return;
		}

		JLabel header = new JLabel("Briefing");
		header.setForeground(Color.WHITE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		briefingPanel.add(header);

		if (hasInstructions)
		{
			JTextArea ta = new JTextArea(instructions);
			ta.setLineWrap(true);
			ta.setWrapStyleWord(true);
			ta.setEditable(false);
			ta.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			ta.setForeground(Color.WHITE);
			ta.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
			ta.setAlignmentX(Component.LEFT_ALIGNMENT);
			ta.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
			briefingPanel.add(ta);
		}

		if (hasTile)
		{
			JLabel tl = new JLabel("Start at: " + tile.getX() + ", " + tile.getY()
				+ " (plane " + tile.getPlane() + ")");
			tl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			tl.setAlignmentX(Component.LEFT_ALIGNMENT);
			briefingPanel.add(tl);
		}

		if (hasItems)
		{
			JLabel itemsHdr = new JLabel("Required items:");
			itemsHdr.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			itemsHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
			briefingPanel.add(itemsHdr);
			List<String> names = hunt.getStartingItemNames();
			Map<Integer, Integer> inv = inventorySnapshot;
			for (int i = 0; i < ids.size(); i++)
			{
				int id = ids.get(i);
				String display = names != null && i < names.size() && names.get(i) != null
					? names.get(i) : ("#" + id);
				boolean present = inv.getOrDefault(id, 0) > 0;
				JPanel row = new JPanel(new BorderLayout(4, 0));
				row.setBackground(present ? PRESENT_BG : MISSING_BG);
				row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
				JLabel icon = new JLabel();
				icon.setPreferredSize(new Dimension(32, 24));
				loadItemIcon(icon, id);
				row.add(icon, BorderLayout.WEST);
				JLabel nm = new JLabel(display);
				nm.setForeground(Color.WHITE);
				row.add(nm, BorderLayout.CENTER);
				JLabel status = new JLabel(present ? "✓" : "✗");
				status.setForeground(Color.WHITE);
				row.add(status, BorderLayout.EAST);
				briefingPanel.add(row);
				briefingPanel.add(Box.createVerticalStrut(2));
			}
		}

		briefingPanel.revalidate();
		briefingPanel.repaint();
	}

	/** Quest-helper-style highlight colours for the required-items list. */
	private static final Color PRESENT_BG = new Color(40, 90, 40);
	private static final Color MISSING_BG = new Color(110, 40, 40);

	private Map<Integer, Integer> readInventoryCounts()
	{
		// Must be called on the client thread — ItemContainer access asserts that.
		Map<Integer, Integer> out = new HashMap<>();
		try
		{
			ItemContainer inv = host.getClient().getItemContainer(InventoryID.INV);
			if (inv == null) return out;
			for (Item item : inv.getItems())
			{
				if (item == null || item.getId() <= 0) continue;
				out.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		catch (Exception ignored)
		{
			// Pre-login the container is null; just show everything as missing.
		}
		return out;
	}

	/** Refreshes {@link #inventorySnapshot} on the client thread, then re-renders the briefing on the EDT. */
	private void refreshInventorySnapshot()
	{
		host.getClientThread().invoke(() ->
		{
			Map<Integer, Integer> snap = readInventoryCounts();
			if (snap.equals(inventorySnapshot))
			{
				return;
			}
			inventorySnapshot = snap;
			SwingUtilities.invokeLater(this::rebuildBriefing);
		});
	}

	private void loadItemIcon(JLabel target, int itemId)
	{
		target.setIcon(null);
		if (itemId <= 0) return;
		host.getClientThread().invoke(() ->
		{
			AsyncBufferedImage img = host.getItemManager().getImage(itemId);
			if (img != null)
			{
				img.addTo(target);
				SwingUtilities.invokeLater(() -> target.setIcon(new ImageIcon(img)));
			}
		});
	}

	void tick()
	{
		// Inventory contents may change while the user lingers on this view, so
		// refresh the briefing's red/green item highlights once per UI tick.
		refreshInventorySnapshot();
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
			host.getCountdownOverlay().setText("GO!", new Color(120, 220, 120));
			playSound(SOUND_GO);
			host.getExecutor().schedule(
				() -> host.getCountdownOverlay().clear(),
				1200, java.util.concurrent.TimeUnit.MILLISECONDS);
			host.getActive().start(hunt);
			host.getDetector().resetForNewHunt();
			host.showActive();
			lastCountdownDigit = -1;
		}
		else
		{
			long sec = (remaining + 999L) / 1000L;
			countdown.setText("Sync starting in " + sec + "s…");
			if (sec >= 1 && sec <= 3 && sec != lastCountdownDigit)
			{
				lastCountdownDigit = (int) sec;
				host.getCountdownOverlay().setText(Long.toString(sec), Color.WHITE);
				playSound(SOUND_TICK);
			}
		}
	}

	private void playSound(int soundId)
	{
		// playSoundEffect respects the user's in-game sound effect volume.
		host.getClientThread().invoke(() -> host.getClient().playSoundEffect(soundId));
	}

	private void onStartNow()
	{
		if (hunt == null)
		{
			return;
		}
		// 3 ticks + GO. Use 3500ms so seconds 3, 2, 1 each fire exactly once before GO.
		countdownTargetMs = System.currentTimeMillis() + 3500L;
		cancelSync.setVisible(true);
		syncStart.setEnabled(false);
		startNow.setEnabled(false);
		tick();
	}

	private void onSyncStart()
	{
		if (hunt == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		// Round up to the next 15-second boundary, ensuring at least 15s of countdown.
		long minMs = now + 15_000L;
		long target = ((minMs / 15_000L) + (minMs % 15_000L == 0 ? 0 : 1)) * 15_000L;
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
			host.getCountdownOverlay().clear();
			lastCountdownDigit = -1;
		}
	}

	private void onReset()
	{
		if (hunt == null)
		{
			return;
		}
		int choice = JOptionPane.showConfirmDialog(this,
			"Reset all progress and the timer for \"" + hunt.getName() + "\"?",
			"Reset hunt", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.OK_OPTION)
		{
			host.getActive().reset(hunt.getId());
			host.getDetector().resetForNewHunt();
			refresh();
		}
	}

	private void onExport()
	{
		if (hunt == null)
		{
			return;
		}
		String code = host.getHuntManager().exportHunt(hunt);
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
		}
		catch (RuntimeException ignored)
		{
			// Headless or restricted clipboard — fall through to the dialog.
		}
		JTextArea area = new JTextArea(code, 8, 40);
		area.setLineWrap(true);
		area.setWrapStyleWord(false);
		area.setEditable(false);
		area.selectAll();
		JScrollPane sp = new JScrollPane(area);
		sp.setPreferredSize(new Dimension(360, 200));
		JOptionPane.showMessageDialog(this, sp,
			"Hunt code (copied to clipboard)", JOptionPane.INFORMATION_MESSAGE);
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

