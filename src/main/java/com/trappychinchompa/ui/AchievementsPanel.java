package com.trappychinchompa.ui;

import com.trappychinchompa.Achievement;
import com.trappychinchompa.TrappyChinchompaPlugin;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The full achievement list: a real scrollable Swing view (83 rows is far
 * past what the drawn canvas cards can carry). Earned rows glow, locked
 * rows grey out, reward attributions show on the rows that carry them.
 * Rebuilt on every open so earned state is always fresh.
 */
class AchievementsPanel extends JPanel
{
	// Earned/reward/debug colours come from GameCanvas so the brand language
	// cannot drift between the game and this list.
	private static final Color EARNED = GameCanvas.TEXT_YELLOW;
	private static final Color REWARD = GameCanvas.TEXT_ORANGE;
	private static final Color DEBUG_RED = GameCanvas.DEBUG_RED;
	private static final Color LOCKED = new Color(0x8a, 0x8a, 0x8a);
	private static final Color DESC = new Color(0xb0, 0xb0, 0xb0);
	private static final Color DESC_LOCKED = new Color(0x6a, 0x6a, 0x6a);

	private final TrappyChinchompaPlugin plugin;
	private final JPanel list = new JPanel();
	private final JLabel counter = new JLabel();

	AchievementsPanel(TrappyChinchompaPlugin plugin, Runnable onBack)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		final JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		final JButton back = new JButton("Back");
		back.setFocusable(false);
		back.addActionListener(e -> onBack.run());
		counter.setFont(FontManager.getRunescapeBoldFont());
		counter.setForeground(Color.WHITE);
		header.add(back, BorderLayout.WEST);
		header.add(counter, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		final JPanel north = new JPanel(new BorderLayout());
		north.setBackground(ColorScheme.DARK_GRAY_COLOR);
		north.add(list, BorderLayout.NORTH);
		final JScrollPane scroll = new JScrollPane(north);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(24);
		scroll.setBorder(null);
		add(scroll, BorderLayout.CENTER);
	}

	/** Rebuild all rows against the current earned set. */
	void refresh()
	{
		final Set<String> earned = plugin.loadEarnedAchievements();
		final Set<String> debug = plugin.loadDebugAchievements();
		counter.setText(earned.size() + " / " + Achievement.ALL.size());
		list.removeAll();
		String category = null;
		for (Achievement a : Achievement.ALL)
		{
			if (!a.getCategory().equals(category))
			{
				category = a.getCategory();
				list.add(categoryHeader(category));
			}
			list.add(row(a, earned.contains(a.getId()), debug.contains(a.getId())));
		}
		list.revalidate();
		list.repaint();
	}

	private Component categoryHeader(String category)
	{
		final JLabel label = new JLabel(category);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createEmptyBorder(10, 8, 2, 8));
		final JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.add(label, BorderLayout.WEST);
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		return wrap;
	}

	private Component row(Achievement a, boolean earned, boolean debugEarned)
	{
		final JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(earned ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 8));
		row.setAlignmentX(LEFT_ALIGNMENT);

		final Box nameLine = Box.createHorizontalBox();
		final JLabel name = new JLabel(a.getNameDisplay(earned));
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(earned ? EARNED : LOCKED);
		nameLine.add(name);
		if (earned && debugEarned)
		{
			// The anti-cheat brand: earned with dev unlocks on, marked forever.
			final JLabel brand = new JLabel("  DEBUG");
			brand.setFont(FontManager.getRunescapeSmallFont());
			brand.setForeground(DEBUG_RED);
			nameLine.add(brand);
		}
		nameLine.setAlignmentX(LEFT_ALIGNMENT);
		row.add(nameLine);

		final JLabel desc = new JLabel(a.getDescriptionDisplay(earned));
		desc.setFont(FontManager.getRunescapeSmallFont());
		desc.setForeground(earned ? DESC : DESC_LOCKED);
		row.add(desc);

		if (a.getRewardDisplay(earned) != null)
		{
			final JLabel reward = new JLabel(a.getRewardDisplay(earned));
			reward.setFont(FontManager.getRunescapeSmallFont());
			reward.setForeground(earned ? REWARD : DESC_LOCKED);
			row.add(reward);
		}
		row.add(Box.createVerticalStrut(2));
		return row;
	}
}
