package com.puzzlehunt;

import com.puzzlehunt.model.ClueType;
import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.ItemSource;
import com.puzzlehunt.model.LocationSubtype;
import com.puzzlehunt.model.PuzzleHunt;
import com.puzzlehunt.model.PuzzleStep;
import com.puzzlehunt.model.SerializedTile;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
class CreateHuntView extends JPanel
{
	private final PuzzleHuntPanel host;

	private final JTextField nameField = new JTextField();
	private final JComboBox<HuntMode> modeBox = new JComboBox<>(HuntMode.values());

	private final JPanel stepsPanel = new JPanel();
	private final JPanel briefingPanel = new JPanel();
	private final JButton addStepBtn = new JButton("+ Add step");

	/** Per-instance UI state — chapter indices currently collapsed. */
	private final java.util.Set<Integer> collapsedChapters = new java.util.HashSet<>();

	private PuzzleHunt hunt;

	CreateHuntView(PuzzleHuntPanel host)
	{
		this.host = host;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton back = PanelComponents.button("← Back");
		back.addActionListener(e ->
		{
			host.getDetector().cancelSampling();
			host.getTileOverlay().setEditStep(null);
			save();
			host.showHome();
		});
		add(back, BorderLayout.NORTH);

		JPanel form = new JPanel(new GridBagLayout());
		form.setBackground(ColorScheme.DARK_GRAY_COLOR);
		GridBagConstraints g = new GridBagConstraints();
		g.fill = GridBagConstraints.HORIZONTAL;
		g.insets = new Insets(2, 2, 2, 2);
		g.gridx = 0; g.gridy = 0; g.weightx = 0;
		form.add(label("Name"), g);
		g.gridx = 1; g.weightx = 1;
		form.add(nameField, g);
		g.gridx = 0; g.gridy = 1; g.weightx = 0;
		form.add(label("Mode"), g);
		g.gridx = 1; g.weightx = 1;
		form.add(modeBox, g);

		nameField.getDocument().addDocumentListener(onChange(this::syncFields));
		modeBox.addActionListener(e ->
		{
			if (hunt != null)
			{
				hunt.setMode((HuntMode) modeBox.getSelectedItem());
				rebuildSteps();
				save();
			}
		});

		stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.Y_AXIS));
		stepsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		briefingPanel.setLayout(new BoxLayout(briefingPanel, BoxLayout.Y_AXIS));
		briefingPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		briefingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel scrollBody = new JPanel();
		scrollBody.setLayout(new BoxLayout(scrollBody, BoxLayout.Y_AXIS));
		scrollBody.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollBody.add(briefingPanel);
		scrollBody.add(Box.createVerticalStrut(8));
		scrollBody.add(stepsPanel);

		JPanel center = new JPanel(new BorderLayout(0, 8));
		center.setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.add(form, BorderLayout.NORTH);
		// No inner JScrollPane — the surrounding sidebar scroll handles
		// vertical overflow, and dropping the nested scroll prevents the
		// horizontal scrollbar from appearing on long step content.
		center.add(scrollBody, BorderLayout.CENTER);
		PanelComponents.styleButton(addStepBtn);
		addStepBtn.addActionListener(e -> onAddStep());
		center.add(addStepBtn, BorderLayout.SOUTH);
		add(center, BorderLayout.CENTER);
	}

	private static JLabel label(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(Color.WHITE);
		return l;
	}

	void setHunt(PuzzleHunt hunt)
	{
		if (hunt == null)
		{
			hunt = new PuzzleHunt();
			hunt.setName("New hunt");
		}
		// Always stamp the current player's RSN as the author when available,
		// so authorship reflects whoever last edited the hunt on this client.
		String rsn = currentPlayerName();
		if (rsn != null && !rsn.isEmpty())
		{
			hunt.setAuthor(rsn);
		}
		this.hunt = hunt;
		collapsedChapters.clear();
		nameField.setText(hunt.getName());
		modeBox.setSelectedItem(hunt.getMode());
		rebuildBriefing();
		rebuildSteps();
	}

	private String currentPlayerName()
	{
		try
		{
			Player p = host.getClient().getLocalPlayer();
			return p != null ? p.getName() : null;
		}
		catch (Exception ignored)
		{
			// Client may not be on a logged-in state; fall back to blank.
			return null;
		}
	}

	private void loadItemIcon(JLabel target, int itemId)
	{
		target.setIcon(null);
		if (itemId <= 0)
		{
			return;
		}
		// getImage / item sprites must be primed on the client thread.
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

	private void openItemSearch(PuzzleStep step)
	{
		java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
		ItemSearchDialog dlg = new ItemSearchDialog(
			owner,
			host.getItemManager(),
			host.getClientThread(),
			host.getExecutor(),
			picked ->
			{
				step.setItemId(picked.id);
				step.setItemName(picked.name);
				SwingUtilities.invokeLater(this::rebuildSteps);
				save();
			});
		dlg.setVisible(true);
	}

	private void syncFields()
	{
		if (hunt == null)
		{
			return;
		}
		hunt.setName(nameField.getText());
		save();
	}

	private void onAddStep()
	{
		if (hunt == null)
		{
			return;
		}
		PuzzleStep step = new PuzzleStep();
		step.setTitle("Step " + (hunt.getSteps().size() + 1));
		// Default new steps into the hunt's highest existing chapter so creators
		// can keep adding to the chapter they're currently working on without
		// having to bump the spinner every time.
		int maxChapter = 0;
		for (PuzzleStep s : hunt.getSteps())
		{
			if (s.getChapter() > maxChapter) maxChapter = s.getChapter();
		}
		step.setChapter(maxChapter);
		// Newly added steps go into a chapter the user is currently working in,
		// so make sure that chapter isn't collapsed when we rebuild.
		collapsedChapters.remove(maxChapter);
		hunt.getSteps().add(step);
		rebuildSteps();
		save();
	}

	// ------------------------------------------------------------------
	// Pre-hunt briefing editor (instructions, starting tile, starting items)
	// ------------------------------------------------------------------

	private void rebuildBriefing()
	{
		briefingPanel.removeAll();
		if (hunt == null)
		{
			briefingPanel.revalidate();
			briefingPanel.repaint();
			return;
		}

		JLabel header = new JLabel("Pre-hunt briefing");
		header.setForeground(Color.WHITE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		briefingPanel.add(header);

		// Instructions text area
		JLabel instrLabel = label("Instructions (shown before Start)");
		instrLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		briefingPanel.add(instrLabel);
		JTextArea instrArea = new JTextArea(hunt.getStartingInstructions(), 3, 1);
		instrArea.setLineWrap(true);
		instrArea.setWrapStyleWord(true);
		instrArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
		instrArea.setForeground(Color.WHITE);
		instrArea.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		instrArea.setAlignmentX(Component.LEFT_ALIGNMENT);
		instrArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		instrArea.getDocument().addDocumentListener(onChange(() ->
		{
			hunt.setStartingInstructions(instrArea.getText());
			save();
		}));
		briefingPanel.add(instrArea);

		// Starting tile row
		SerializedTile tile = hunt.getStartingTile();
		JPanel tileRow = new JPanel(new BorderLayout(4, 0));
		tileRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		String tileText = tile == null
			? "Start tile: (none)"
			: ("Start tile: " + tile.getX() + ", " + tile.getY() + " (plane " + tile.getPlane() + ")");
		JLabel tileLabel = new JLabel(tileText);
		tileLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tileRow.add(tileLabel, BorderLayout.CENTER);
		JPanel tileBtns = new JPanel();
		tileBtns.setLayout(new BoxLayout(tileBtns, BoxLayout.X_AXIS));
		tileBtns.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton setHere = PanelComponents.button("Use my tile");
		setHere.addActionListener(e ->
		{
			// WorldPoint / LocalPlayer reads must happen on the client thread.
			host.getClientThread().invoke(() ->
			{
				SerializedTile here = currentPlayerTile();
				SwingUtilities.invokeLater(() ->
				{
					if (here == null)
					{
						JOptionPane.showMessageDialog(this,
							"Could not read your current tile. Are you logged in?",
							"Start tile", JOptionPane.WARNING_MESSAGE);
						return;
					}
					hunt.setStartingTile(here);
					save();
					rebuildBriefing();
				});
			});
		});
		tileBtns.add(setHere);
		if (tile != null)
		{
			JButton clearTile = PanelComponents.button("Clear");
			clearTile.addActionListener(e ->
			{
				hunt.setStartingTile(null);
				save();
				rebuildBriefing();
			});
			tileBtns.add(clearTile);
		}
		tileRow.add(tileBtns, BorderLayout.EAST);
		briefingPanel.add(tileRow);

		// Starting items list (item search; remove buttons)
		JLabel itemsHdr = label("Starting items");
		itemsHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
		briefingPanel.add(itemsHdr);
		List<Integer> ids = hunt.getStartingItemIds();
		List<String> names = hunt.getStartingItemNames();
		if (ids != null)
		{
			for (int i = 0; i < ids.size(); i++)
			{
				final int idx = i;
				JPanel row = new JPanel(new BorderLayout(4, 0));
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				JLabel icon = new JLabel();
				icon.setPreferredSize(new Dimension(36, 32));
				loadItemIcon(icon, ids.get(idx));
				row.add(icon, BorderLayout.WEST);
				String display = names != null && idx < names.size() && names.get(idx) != null
					? names.get(idx) : ("#" + ids.get(idx));
				JLabel name = new JLabel(display);
				name.setForeground(Color.WHITE);
				row.add(name, BorderLayout.CENTER);
				JButton rm = PanelComponents.button("✕");
				rm.addActionListener(e ->
				{
					ids.remove(idx);
					if (names != null && idx < names.size()) names.remove(idx);
					save();
					rebuildBriefing();
				});
				row.add(rm, BorderLayout.EAST);
				briefingPanel.add(row);
			}
		}
		JButton addItem = PanelComponents.button("+ Add starting item");
		addItem.setAlignmentX(Component.LEFT_ALIGNMENT);
		addItem.addActionListener(e ->
		{
			java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
			new ItemSearchDialog(owner, host.getItemManager(), host.getClientThread(), host.getExecutor(),
				picked ->
				{
					hunt.getStartingItemIds().add(picked.id);
					hunt.getStartingItemNames().add(picked.name);
					save();
					SwingUtilities.invokeLater(this::rebuildBriefing);
				}).setVisible(true);
		});
		briefingPanel.add(addItem);

		briefingPanel.revalidate();
		briefingPanel.repaint();
	}

	private SerializedTile currentPlayerTile()
	{
		try
		{
			Player p = host.getClient().getLocalPlayer();
			if (p == null) return null;
			net.runelite.api.coords.WorldPoint wp = p.getWorldLocation();
			if (wp == null) return null;
			return new SerializedTile(wp.getX(), wp.getY(), wp.getPlane());
		}
		catch (Exception ignored)
		{
			return null;
		}
	}

	private void rebuildSteps()
	{
		stepsPanel.removeAll();
		if (hunt != null)
		{
			normalizeChapters();
			int lastChapter = -1;
			boolean treasure = hunt.getMode() == HuntMode.TREASURE_TRAIL;
			for (int i = 0; i < hunt.getSteps().size(); i++)
			{
				PuzzleStep step = hunt.getSteps().get(i);
				if (treasure && step.getChapter() != lastChapter)
				{
					stepsPanel.add(buildChapterDivider(step.getChapter()));
					stepsPanel.add(Box.createVerticalStrut(4));
					lastChapter = step.getChapter();
				}
				if (treasure && collapsedChapters.contains(step.getChapter()))
				{
					continue;
				}
				stepsPanel.add(buildStepEditor(i));
				stepsPanel.add(Box.createVerticalStrut(6));
			}
			// Glue absorbs any remaining vertical space so cards don’t stretch.
			stepsPanel.add(Box.createVerticalGlue());
		}
		stepsPanel.revalidate();
		stepsPanel.repaint();
	}

	/**
	 * Stable-sorts steps by chapter so chapters stay contiguous, and trims
	 * {@code chapterModes} so its size matches the highest chapter index used.
	 */
	private void normalizeChapters()
	{
		List<PuzzleStep> steps = hunt.getSteps();
		steps.sort((a, b) -> Integer.compare(a.getChapter(), b.getChapter()));
		int max = 0;
		for (PuzzleStep s : steps) if (s.getChapter() > max) max = s.getChapter();
		List<HuntMode> modes = hunt.getChapterModes();
		while (modes.size() <= max) modes.add(hunt.getMode());
		while (modes.size() > max + 1) modes.remove(modes.size() - 1);
	}

	private JPanel buildChapterDivider(int chapterIdx)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean collapsed = collapsedChapters.contains(chapterIdx);
		int count = 0;
		for (PuzzleStep s : hunt.getSteps()) if (s.getChapter() == chapterIdx) count++;
		JButton toggle = PanelComponents.button((collapsed ? "▸ " : "▾ ")
			+ "Chapter " + (chapterIdx + 1)
			+ "  (" + count + " step" + (count == 1 ? "" : "s") + ")");
		toggle.setHorizontalAlignment(SwingConstants.LEFT);
		toggle.addActionListener(e ->
		{
			if (collapsedChapters.contains(chapterIdx)) collapsedChapters.remove(chapterIdx);
			else collapsedChapters.add(chapterIdx);
			rebuildSteps();
		});
		row.add(toggle, BorderLayout.CENTER);

		JComboBox<HuntMode> picker = new JComboBox<>(HuntMode.values());
		picker.setSelectedItem(hunt.getChapterMode(chapterIdx));
		picker.addActionListener(e ->
		{
			while (hunt.getChapterModes().size() <= chapterIdx)
			{
				hunt.getChapterModes().add(hunt.getMode());
			}
			hunt.getChapterModes().set(chapterIdx, (HuntMode) picker.getSelectedItem());
			save();
		});
		row.add(picker, BorderLayout.EAST);
		return row;
	}

	private JPanel buildStepEditor(int index)
	{
		final PuzzleStep step = hunt.getSteps().get(index);
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		box.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Header: index + reorder/remove buttons
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel num = new JLabel("Step " + (index + 1));
		num.setForeground(Color.WHITE);
		header.add(num, BorderLayout.WEST);

		JPanel hb = new JPanel();
		hb.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		hb.setLayout(new BoxLayout(hb, BoxLayout.X_AXIS));
		if (hunt.getMode() == HuntMode.TREASURE_TRAIL)
		{
			JButton up = PanelComponents.button("↑");
			up.setEnabled(index > 0);
			up.addActionListener(e -> moveStep(index, -1));
			hb.add(up);
			JButton down = PanelComponents.button("↓");
			down.setEnabled(index < hunt.getSteps().size() - 1);
			down.addActionListener(e -> moveStep(index, +1));
			hb.add(down);
		}
		JButton duplicate = PanelComponents.button("⎘");
		duplicate.setToolTipText("Duplicate this step");
		duplicate.addActionListener(e ->
		{
			PuzzleStep copy = host.getHuntManager().cloneStep(step);
			if (copy == null) return;
			copy.setTitle(step.getTitle() + " (copy)");
			hunt.getSteps().add(index + 1, copy);
			rebuildSteps();
			save();
		});
		hb.add(duplicate);
		JButton remove = PanelComponents.button("✕");
		remove.addActionListener(e ->
		{
			hunt.getSteps().remove(index);
			rebuildSteps();
			save();
		});
		hb.add(remove);
		header.add(hb, BorderLayout.EAST);
		box.add(header);

		if (hunt.getMode() == HuntMode.TREASURE_TRAIL)
		{
			JPanel chapterRow = new JPanel(new BorderLayout(6, 0));
			chapterRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			chapterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel cl = new JLabel("Chapter");
			cl.setForeground(Color.WHITE);
			chapterRow.add(cl, BorderLayout.WEST);
			JSpinner chapterSpinner = new JSpinner(new SpinnerNumberModel(
				Math.max(0, step.getChapter()) + 1, 1, 99, 1));
			chapterSpinner.addChangeListener(e ->
			{
				int v = ((Integer) chapterSpinner.getValue()) - 1;
				if (v != step.getChapter())
				{
					step.setChapter(v);
					rebuildSteps();
					save();
				}
			});
			chapterRow.add(chapterSpinner, BorderLayout.EAST);
			box.add(chapterRow);
		}

		// Title
		box.add(field("Title", step.getTitle(), v -> { step.setTitle(v); save(); }));

		// Free-text clue (optional, multiline)
		JLabel clueLabel = label("Clue text (optional — hides criteria)");
		clueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.add(clueLabel);
		JTextArea clueArea = new JTextArea(step.getClueText(), 3, 1);
		clueArea.setLineWrap(true);
		clueArea.setWrapStyleWord(true);
		clueArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
		clueArea.setForeground(Color.WHITE);
		clueArea.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		clueArea.setAlignmentX(Component.LEFT_ALIGNMENT);
		// Constrain the text area’s preferred width so BoxLayout wraps it
		// instead of allowing it to push the parent wider.
		clueArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		clueArea.getDocument().addDocumentListener(onChange(() -> { step.setClueText(clueArea.getText()); save(); }));
		box.add(clueArea);

		// Type selector
		JPanel typeRow = new JPanel(new BorderLayout());
		typeRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		typeRow.add(label("Type"), BorderLayout.WEST);
		JComboBox<ClueType> typeBox = new JComboBox<>(ClueType.values());
		typeBox.setSelectedItem(step.getType());
		typeBox.addActionListener(e ->
		{
			step.setType((ClueType) typeBox.getSelectedItem());
			rebuildSteps();
			save();
		});
		typeRow.add(typeBox, BorderLayout.CENTER);
		typeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.add(typeRow);

		if (step.getType() == ClueType.GET_ITEM)
		{
			buildGetItemEditor(box, step);
		}
		else if (step.getType() == ClueType.LOCATION_PUZZLE)
		{
			buildLocationEditor(box, step);
		}
		else if (step.getType() == ClueType.KILL_MONSTER)
		{
			buildKillMonsterEditor(box, step);
		}
		else if (step.getType() == ClueType.DIE)
		{
			JLabel info = label("No extra config — completes when you die.");
			info.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.add(info);
		}
		else if (step.getType() == ClueType.GAIN_GP)
		{
			buildGpEditor(box, step);
		}
		else if (step.getType() == ClueType.GAIN_XP)
		{
			buildXpEditor(box, step);
		}
		else if (step.getType() == ClueType.PASSWORD)
		{
			buildPasswordEditor(box, step);
		}

		buildImageEditor(box, step);

		// Clamp width to the sidebar so a wide child (e.g. an unwrapped
		// JTextArea) can’t push the BoxLayout column past the visible
		// area, and clamp height to preferred so a single card doesn’t
		// stretch to fill the viewport.
		int maxW = stepsPanel.getWidth() > 0 ? stepsPanel.getWidth() : PluginPanel.PANEL_WIDTH - 16;
		Dimension pref = box.getPreferredSize();
		box.setMaximumSize(new Dimension(maxW, pref.height));
		return box;
	}

	private void buildGetItemEditor(JPanel parent, PuzzleStep step)
	{
		// Primary item icon + search button.
		JPanel itemRow = new JPanel(new BorderLayout(4, 0));
		itemRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(36, 32));
		loadItemIcon(iconLabel, step.getItemId());
		itemRow.add(iconLabel, BorderLayout.WEST);

		JButton search = PanelComponents.button("Search item");
		search.addActionListener(e -> openItemSearch(step));
		itemRow.add(search, BorderLayout.EAST);
		itemRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		parent.add(itemRow);

		// Additional items (any of these counts).
		parent.add(buildAdditionalItemsRow(step));

		// Required count
		parent.add(buildCountRow("Items needed (0 = any)", step.getRequiredCount(),
			v -> { step.setRequiredCount(v); save(); }));

		// Source selector
		JPanel srcRow = new JPanel(new BorderLayout());
		srcRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		srcRow.add(label("Source"), BorderLayout.WEST);
		JComboBox<ItemSource> srcBox = new JComboBox<>(ItemSource.values());
		srcBox.setSelectedItem(step.getItemSource());
		srcBox.addActionListener(e ->
		{
			step.setItemSource((ItemSource) srcBox.getSelectedItem());
			rebuildSteps();
			save();
		});
		srcRow.add(srcBox, BorderLayout.CENTER);
		srcRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		parent.add(srcRow);

		if (step.getItemSource() == ItemSource.MONSTER_DROP)
		{
			parent.add(buildMonsterNamesField("Monster names (comma-separated; leave blank for any)",
				step.getMonsterNames(), step::setMonsterNames));
		}
	}

	private JPanel buildAdditionalItemsRow(PuzzleStep step)
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel hdr = label("Also accepts:");
		hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(hdr);

		java.util.List<Integer> ids = step.getAdditionalItemIds();
		java.util.List<String> names = step.getAdditionalItemNames();
		if (ids != null)
		{
			for (int i = 0; i < ids.size(); i++)
			{
				final int idx = i;
				JPanel row = new JPanel(new BorderLayout(4, 0));
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				String display = names != null && idx < names.size() && names.get(idx) != null
					? names.get(idx) : ("#" + ids.get(idx));
				JLabel name = new JLabel(display);
				name.setForeground(Color.WHITE);
				row.add(name, BorderLayout.CENTER);
				JButton rm = PanelComponents.button("✕");
				rm.addActionListener(e ->
				{
					step.getAdditionalItemIds().remove(idx);
					if (step.getAdditionalItemNames() != null
						&& idx < step.getAdditionalItemNames().size())
					{
						step.getAdditionalItemNames().remove(idx);
					}
					rebuildSteps();
					save();
				});
				row.add(rm, BorderLayout.EAST);
				wrap.add(row);
			}
		}
		JButton add = PanelComponents.button("+ Add another item");
		add.setAlignmentX(Component.LEFT_ALIGNMENT);
		add.addActionListener(e ->
		{
			java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
			new ItemSearchDialog(owner, host.getItemManager(), host.getClientThread(), host.getExecutor(),
				picked ->
				{
					if (step.getAdditionalItemIds() == null)
					{
						step.setAdditionalItemIds(new ArrayList<>());
					}
					if (step.getAdditionalItemNames() == null)
					{
						step.setAdditionalItemNames(new ArrayList<>());
					}
					step.getAdditionalItemIds().add(picked.id);
					step.getAdditionalItemNames().add(picked.name);
					SwingUtilities.invokeLater(this::rebuildSteps);
					save();
				}).setVisible(true);
		});
		wrap.add(add);
		return wrap;
	}

	private JPanel buildCountRow(String labelText, int initial, java.util.function.IntConsumer onChange)
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel lbl = label(labelText);
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(lbl);
		JSpinner sp = new JSpinner(new SpinnerNumberModel(Math.max(0, initial), 0, Integer.MAX_VALUE, 1));
		sp.setAlignmentX(Component.LEFT_ALIGNMENT);
		sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, sp.getPreferredSize().height));
		sp.addChangeListener(e -> onChange.accept((Integer) sp.getValue()));
		wrap.add(sp);
		return wrap;
	}

	private JPanel buildMonsterNamesField(String labelText, java.util.List<String> initial,
		java.util.function.Consumer<java.util.List<String>> onChange)
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel lbl = label(labelText);
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(lbl);
		JTextField tf = new JTextField(initial == null ? "" : String.join(", ", initial));
		tf.setAlignmentX(Component.LEFT_ALIGNMENT);
		tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, tf.getPreferredSize().height));
		tf.getDocument().addDocumentListener(onChange(() ->
		{
			java.util.List<String> names = new ArrayList<>();
			for (String part : tf.getText().split(","))
			{
				String t = part.trim();
				if (!t.isEmpty()) names.add(t);
			}
			onChange.accept(names);
			save();
		}));
		wrap.add(tf);
		return wrap;
	}

	private void buildKillMonsterEditor(JPanel parent, PuzzleStep step)
	{
		parent.add(buildMonsterNamesField("Monster names (comma-separated; blank = any)",
			step.getKillMonsterNames(), step::setKillMonsterNames));
		parent.add(buildCountRow("Kills required", Math.max(1, step.getKillCount()),
			v -> { step.setKillCount(Math.max(1, v)); save(); }));
	}

	private void buildGpEditor(JPanel parent, PuzzleStep step)
	{
		parent.add(buildCountRow("GP to gain", step.getGpAmount(),
			v -> { step.setGpAmount(v); save(); }));
	}

	private void buildXpEditor(JPanel parent, PuzzleStep step)
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel lbl = label("Skills (comma-separated; blank = any)");
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(lbl);
		JTextField tf = new JTextField(step.getXpSkills() == null
			? "" : String.join(", ", step.getXpSkills()));
		tf.setAlignmentX(Component.LEFT_ALIGNMENT);
		tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, tf.getPreferredSize().height));
		tf.getDocument().addDocumentListener(onChange(() ->
		{
			java.util.List<String> names = new ArrayList<>();
			for (String part : tf.getText().split(","))
			{
				String t = part.trim();
				if (!t.isEmpty()) names.add(t.toUpperCase());
			}
			step.setXpSkills(names);
			save();
		}));
		wrap.add(tf);
		parent.add(wrap);
		parent.add(buildCountRow("XP to gain", step.getXpAmount(),
			v -> { step.setXpAmount(v); save(); }));
	}

	private void buildPasswordEditor(JPanel parent, PuzzleStep step)
	{
		parent.add(field("Answer", step.getPasswordAnswer(),
			v -> { step.setPasswordAnswer(v); save(); }));
	}

	private void buildImageEditor(JPanel parent, PuzzleStep step)
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel hdr = label("Image (optional)");
		hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(hdr);

		JLabel preview = new JLabel();
		preview.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (step.getImageBase64() != null && !step.getImageBase64().isEmpty())
		{
			try
			{
				byte[] data = Base64.getDecoder().decode(step.getImageBase64());
				BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
				if (img != null)
				{
					Image scaled = img.getWidth() > 200
						? img.getScaledInstance(200, -1, Image.SCALE_SMOOTH)
						: img;
					preview.setIcon(new ImageIcon(scaled));
					preview.setToolTipText("Click to enlarge");
					preview.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
					final BufferedImage full = img;
					preview.addMouseListener(new java.awt.event.MouseAdapter()
					{
						@Override public void mouseClicked(java.awt.event.MouseEvent e)
						{
							showFullImage(preview, full);
						}
					});
				}
			}
			catch (IOException | IllegalArgumentException ex)
			{
				log.debug("Bad step image", ex);
			}
		}
		wrap.add(preview);

		JPanel btns = new JPanel();
		btns.setLayout(new BoxLayout(btns, BoxLayout.X_AXIS));
		btns.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		btns.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton upload = PanelComponents.button("Upload");
		upload.addActionListener(e -> onUploadImage(step));
		btns.add(upload);
		if (step.getImageBase64() != null && !step.getImageBase64().isEmpty())
		{
			JButton clear = PanelComponents.button("Clear");
			clear.addActionListener(e ->
			{
				step.setImageBase64(null);
				rebuildSteps();
				save();
			});
			btns.add(clear);
		}
		wrap.add(btns);
		parent.add(wrap);
	}

	private static void showFullImage(Component anchor, BufferedImage img)
	{
		java.awt.Window owner = SwingUtilities.getWindowAncestor(anchor);
		javax.swing.JDialog dlg = owner instanceof java.awt.Frame
			? new javax.swing.JDialog((java.awt.Frame) owner, "Clue image", false)
			: new javax.swing.JDialog((java.awt.Dialog) null, "Clue image", false);
		JLabel full = new JLabel(new ImageIcon(img));
		javax.swing.JScrollPane sp = new javax.swing.JScrollPane(full);
		Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		int w = Math.min(img.getWidth() + 32, (int) (screen.width * 0.9));
		int h = Math.min(img.getHeight() + 32, (int) (screen.height * 0.9));
		sp.setPreferredSize(new Dimension(w, h));
		dlg.setContentPane(sp);
		dlg.pack();
		dlg.setLocationRelativeTo(anchor);
		dlg.setVisible(true);
	}

	private void onUploadImage(PuzzleStep step)
	{
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif"));
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File f = fc.getSelectedFile();
		try
		{
			BufferedImage img = ImageIO.read(f);
			if (img == null)
			{
				warn("Could not read image.");
				return;
			}
			// Re-encode to PNG so the stored bytes are normalized.
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(img, "png", baos);
			String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
			if (b64.length() > 1_500_000)
			{
				warn("Image is too large after encoding (>1MB). Pick a smaller one.");
				return;
			}
			step.setImageBase64(b64);
			rebuildSteps();
			save();
		}
		catch (IOException ex)
		{
			log.debug("Failed to read image", ex);
			warn("Failed to read image: " + ex.getMessage());
		}
	}

	private void buildLocationEditor(JPanel parent, PuzzleStep step)
	{
		JPanel subRow = new JPanel(new BorderLayout());
		subRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		subRow.add(label("Subtype"), BorderLayout.WEST);
		JComboBox<LocationSubtype> subBox = new JComboBox<>(LocationSubtype.values());
		subBox.setSelectedItem(step.getLocationSubtype());
		subBox.addActionListener(e ->
		{
			step.setLocationSubtype((LocationSubtype) subBox.getSelectedItem());
			rebuildSteps();
			save();
		});
		subRow.add(subBox, BorderLayout.CENTER);
		subRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		parent.add(subRow);

		if (step.getLocationSubtype() == LocationSubtype.NPC)
		{
			parent.add(field("NPC name", step.getNpcName(), v -> { step.setNpcName(v); save(); }));
		}
		else // TILES
		{
			JLabel tileCount = new JLabel("Tiles: " + step.getTiles().size());
			tileCount.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			tileCount.setAlignmentX(Component.LEFT_ALIGNMENT);
			parent.add(tileCount);

			JPanel tb = new JPanel();
			tb.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			tb.setLayout(new BoxLayout(tb, BoxLayout.X_AXIS));
			boolean isCapturing = host.getDetector().getSampleStep() == step;

			JButton walk = PanelComponents.button("Walk loop");
			walk.setEnabled(!isCapturing);
			walk.addActionListener(e ->
			{
				host.getTileOverlay().setEditStep(step);
				host.getDetector().startSampling(step, () ->
					javax.swing.SwingUtilities.invokeLater(this::rebuildSteps));
				rebuildSteps();
			});
			tb.add(walk);

			JButton finish = PanelComponents.button("Finish loop");
			finish.setEnabled(isCapturing);
			finish.addActionListener(e ->
			{
				host.getDetector().finishSampling();
				host.getTileOverlay().setEditStep(step);
				rebuildSteps();
				save();
			});
			tb.add(finish);

			JButton clear = PanelComponents.button("Clear tiles");
			clear.addActionListener(e ->
			{
				step.getTiles().clear();
				rebuildSteps();
				save();
			});
			tb.add(clear);

			tb.setAlignmentX(Component.LEFT_ALIGNMENT);
			parent.add(tb);

			// Make sure the overlay shows the step being edited.
			host.getTileOverlay().setEditStep(step);
		}
	}

	private JPanel field(String name, String initial, java.util.function.Consumer<String> onChange)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.add(label(name), BorderLayout.WEST);
		JTextField tf = new JTextField(initial == null ? "" : initial);
		tf.getDocument().addDocumentListener(onChange(() -> onChange.accept(tf.getText())));
		row.add(tf, BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private void moveStep(int idx, int delta)
	{
		int target = idx + delta;
		if (target < 0 || target >= hunt.getSteps().size())
		{
			return;
		}
		PuzzleStep s = hunt.getSteps().remove(idx);
		hunt.getSteps().add(target, s);
		rebuildSteps();
		save();
	}

	private void save()
	{
		if (hunt == null)
		{
			return;
		}
		try
		{
			host.getHuntManager().saveHunt(hunt);
		}
		catch (IOException ex)
		{
			log.debug("Failed to save hunt", ex);
		}
	}

	private DocumentListener onChange(Runnable r)
	{
		return new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { r.run(); }
			@Override public void removeUpdate(DocumentEvent e) { r.run(); }
			@Override public void changedUpdate(DocumentEvent e) { r.run(); }
		};
	}

	private void warn(String msg)
	{
		JLabel l = new JLabel(msg, SwingConstants.CENTER);
		l.setForeground(Color.ORANGE);
		JOptionPane.showMessageDialog(this, l, "Puzzle hunt", JOptionPane.WARNING_MESSAGE);
	}
}
