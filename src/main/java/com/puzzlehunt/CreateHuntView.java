package com.puzzlehunt;

import com.puzzlehunt.model.ClueType;
import com.puzzlehunt.model.HuntMode;
import com.puzzlehunt.model.ItemSource;
import com.puzzlehunt.model.LocationSubtype;
import com.puzzlehunt.model.PuzzleHunt;
import com.puzzlehunt.model.PuzzleStep;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

@Slf4j
class CreateHuntView extends JPanel
{
	private final PuzzleHuntPanel host;

	private final JTextField nameField = new JTextField();
	private final JTextField authorField = new JTextField();
	private final JComboBox<HuntMode> modeBox = new JComboBox<>(HuntMode.values());

	private final JPanel stepsPanel = new JPanel();
	private final JButton addStepBtn = new JButton("+ Add step");

	private PuzzleHunt hunt;

	CreateHuntView(PuzzleHuntPanel host)
	{
		this.host = host;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton back = new JButton("← Back");
		back.addActionListener(e ->
		{
			host.getDetector().setPaintStep(null, null);
			host.getTileOverlay().setPaintStep(null);
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
		form.add(label("Author"), g);
		g.gridx = 1; g.weightx = 1;
		form.add(authorField, g);
		g.gridx = 0; g.gridy = 2; g.weightx = 0;
		form.add(label("Mode"), g);
		g.gridx = 1; g.weightx = 1;
		form.add(modeBox, g);

		nameField.getDocument().addDocumentListener(onChange(this::syncFields));
		authorField.getDocument().addDocumentListener(onChange(this::syncFields));
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

		JPanel center = new JPanel(new BorderLayout(0, 8));
		center.setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.add(form, BorderLayout.NORTH);
		JScrollPane sp = new JScrollPane(stepsPanel);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.add(sp, BorderLayout.CENTER);
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
		this.hunt = hunt;
		nameField.setText(hunt.getName());
		authorField.setText(hunt.getAuthor());
		modeBox.setSelectedItem(hunt.getMode());
		rebuildSteps();
	}

	private void syncFields()
	{
		if (hunt == null)
		{
			return;
		}
		hunt.setName(nameField.getText());
		hunt.setAuthor(authorField.getText());
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
		hunt.getSteps().add(step);
		rebuildSteps();
		save();
	}

	private void rebuildSteps()
	{
		stepsPanel.removeAll();
		if (hunt != null)
		{
			for (int i = 0; i < hunt.getSteps().size(); i++)
			{
				stepsPanel.add(buildStepEditor(i));
				stepsPanel.add(Box.createVerticalStrut(6));
			}
		}
		stepsPanel.revalidate();
		stepsPanel.repaint();
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
		JLabel num = new JLabel("Step " + (index + 1));
		num.setForeground(Color.WHITE);
		header.add(num, BorderLayout.WEST);

		JPanel hb = new JPanel();
		hb.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		hb.setLayout(new BoxLayout(hb, BoxLayout.X_AXIS));
		if (hunt.getMode() == HuntMode.TREASURE_TRAIL)
		{
			JButton up = new JButton("↑");
			up.setEnabled(index > 0);
			up.addActionListener(e -> moveStep(index, -1));
			hb.add(up);
			JButton down = new JButton("↓");
			down.setEnabled(index < hunt.getSteps().size() - 1);
			down.addActionListener(e -> moveStep(index, +1));
			hb.add(down);
		}
		JButton remove = new JButton("✕");
		remove.addActionListener(e ->
		{
			hunt.getSteps().remove(index);
			rebuildSteps();
			save();
		});
		hb.add(remove);
		header.add(hb, BorderLayout.EAST);
		box.add(header);

		// Title
		box.add(field("Title", step.getTitle(), v -> { step.setTitle(v); save(); }));

		// Free-text clue (optional, multiline)
		JLabel clueLabel = label("Clue text (optional — hides criteria)");
		clueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.add(clueLabel);
		JTextArea clueArea = new JTextArea(step.getClueText(), 3, 20);
		clueArea.setLineWrap(true);
		clueArea.setWrapStyleWord(true);
		clueArea.getDocument().addDocumentListener(onChange(() -> { step.setClueText(clueArea.getText()); save(); }));
		JScrollPane clueScroll = new JScrollPane(clueArea);
		clueScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.add(clueScroll);

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
		else
		{
			buildLocationEditor(box, step);
		}

		return box;
	}

	private void buildGetItemEditor(JPanel parent, PuzzleStep step)
	{
		// Item id + name + eyedropper
		JPanel itemRow = new JPanel(new BorderLayout(4, 0));
		itemRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JTextField idField = new JTextField(String.valueOf(step.getItemId()), 6);
		idField.getDocument().addDocumentListener(onChange(() ->
		{
			try { step.setItemId(Integer.parseInt(idField.getText().trim())); save(); }
			catch (NumberFormatException ignored) {}
		}));
		itemRow.add(idField, BorderLayout.WEST);
		JLabel nameLabel = new JLabel(step.getItemName().isEmpty() ? "(no item)" : step.getItemName());
		nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		itemRow.add(nameLabel, BorderLayout.CENTER);
		JButton eye = new JButton("Eyedropper");
		eye.addActionListener(e -> host.getEyedropper().request(EyedropperService.Mode.ITEM_FROM_INVENTORY, picked ->
		{
			if (picked instanceof CompletionDetector.SelectedItem)
			{
				CompletionDetector.SelectedItem si = (CompletionDetector.SelectedItem) picked;
				step.setItemId(si.id);
				step.setItemName(si.name);
				javax.swing.SwingUtilities.invokeLater(this::rebuildSteps);
				save();
			}
		}));
		itemRow.add(eye, BorderLayout.EAST);
		itemRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		parent.add(itemRow);

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
			JLabel monsters = new JLabel("Monsters: "
				+ (step.getMonsterNames().isEmpty() ? "(none — eyedropper to add)" : String.join(", ", step.getMonsterNames())));
			monsters.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			monsters.setAlignmentX(Component.LEFT_ALIGNMENT);
			parent.add(monsters);

			JPanel mb = new JPanel();
			mb.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			mb.setLayout(new BoxLayout(mb, BoxLayout.X_AXIS));
			JButton addM = new JButton("+ Eyedropper monster");
			addM.addActionListener(e -> host.getEyedropper().request(EyedropperService.Mode.MONSTER, picked ->
			{
				if (picked instanceof CompletionDetector.SelectedNpc)
				{
					CompletionDetector.SelectedNpc n = (CompletionDetector.SelectedNpc) picked;
					if (!step.getMonsterIds().contains(n.id))
					{
						step.getMonsterIds().add(n.id);
						step.getMonsterNames().add(n.name);
					}
					javax.swing.SwingUtilities.invokeLater(this::rebuildSteps);
					save();
				}
			}));
			mb.add(addM);
			JButton clearM = new JButton("Clear");
			clearM.addActionListener(e ->
			{
				step.setMonsterIds(new ArrayList<>());
				step.setMonsterNames(new ArrayList<>());
				rebuildSteps();
				save();
			});
			mb.add(clearM);
			mb.setAlignmentX(Component.LEFT_ALIGNMENT);
			parent.add(mb);
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
			JButton eye = new JButton("Eyedropper NPC");
			eye.setAlignmentX(Component.LEFT_ALIGNMENT);
			eye.addActionListener(e -> host.getEyedropper().request(EyedropperService.Mode.NPC, picked ->
			{
				if (picked instanceof CompletionDetector.SelectedNpc)
				{
					step.setNpcName(((CompletionDetector.SelectedNpc) picked).name);
					javax.swing.SwingUtilities.invokeLater(this::rebuildSteps);
					save();
				}
			}));
			parent.add(eye);
		}
		else // TILES
		{
			JLabel tileCount = new JLabel("Painted tiles: " + step.getTiles().size());
			tileCount.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			tileCount.setAlignmentX(Component.LEFT_ALIGNMENT);
			parent.add(tileCount);

			JPanel tb = new JPanel();
			tb.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			tb.setLayout(new BoxLayout(tb, BoxLayout.X_AXIS));
			boolean isPainting = host.getDetector().getPaintStep() == step;
			JButton paint = new JButton(isPainting ? "Stop painting" : "Start painting");
			paint.addActionListener(e ->
			{
				if (host.getDetector().getPaintStep() == step)
				{
					host.getDetector().setPaintStep(null, null);
					host.getTileOverlay().setPaintStep(null);
				}
				else
				{
					host.getDetector().setPaintStep(step, () ->
					{
						save();
						javax.swing.SwingUtilities.invokeLater(this::rebuildSteps);
					});
					host.getTileOverlay().setPaintStep(step);
				}
				rebuildSteps();
			});
			tb.add(paint);
			JButton clear = new JButton("Clear tiles");
			clear.addActionListener(e ->
			{
				step.getTiles().clear();
				rebuildSteps();
				save();
			});
			tb.add(clear);
			tb.setAlignmentX(Component.LEFT_ALIGNMENT);
			parent.add(tb);
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

	@SuppressWarnings("unused") // kept for potential validation messaging
	private void warn(String msg)
	{
		JLabel l = new JLabel(msg, SwingConstants.CENTER);
		l.setForeground(Color.ORANGE);
		JOptionPane.showMessageDialog(this, l, "Puzzle hunt", JOptionPane.WARNING_MESSAGE);
	}
}
