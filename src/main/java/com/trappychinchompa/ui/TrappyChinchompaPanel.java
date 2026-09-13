package com.trappychinchompa.ui;

import com.trappychinchompa.TrappyChinchompaConfig;
import com.trappychinchompa.TrappyChinchompaPlugin;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Host with two cards: the game canvas and the scrollable achievements
 * list. The game loop only runs while this panel is the active sidebar tab
 * AND the game card is showing, so an idle plugin costs nothing.
 */
public class TrappyChinchompaPanel extends PluginPanel
{
	private static final String CARD_GAME = "game";
	private static final String CARD_ACHIEVEMENTS = "achievements";

	private final CardLayout cards = new CardLayout();
	private final TrappyChinchompaPlugin plugin;
	private final GameCanvas canvas;
	private final DuelDock dock;
	private final AchievementsPanel achievements;
	private boolean active;
	private boolean showingAchievements;

	public TrappyChinchompaPanel(TrappyChinchompaPlugin plugin, ItemManager itemManager,
		SpriteManager spriteManager, TrappyChinchompaConfig config)
	{
		super(false);
		this.plugin = plugin;
		setLayout(cards);
		canvas = new GameCanvas(plugin, itemManager, spriteManager, config);
		dock = new DuelDock(plugin::getDuels, config::difficulty, plugin::challengeDuel, plugin::competeDuel, this::playDuel, this::focusGame);
		achievements = new AchievementsPanel(plugin, this::showGame);
		final JPanel game = new JPanel(new BorderLayout());
		game.add(canvas, BorderLayout.CENTER);
		game.add(dock, BorderLayout.SOUTH);
		add(game, CARD_GAME);
		add(achievements, CARD_ACHIEVEMENTS);
	}

	@Override
	public void onActivate()
	{
		active = true;
		if (!showingAchievements)
		{
			canvas.start();
		}
	}

	@Override
	public void onDeactivate()
	{
		active = false;
		canvas.stop();
	}

	public void showAchievements()
	{
		showingAchievements = true;
		canvas.stop();
		achievements.refresh();
		cards.show(this, CARD_ACHIEVEMENTS);
	}

	public void showGame()
	{
		showingAchievements = false;
		cards.show(this, CARD_GAME);
		if (active)
		{
			canvas.start();
		}
	}

	public void refreshFromConfig()
	{
		canvas.refreshFromConfig();
	}

	/** An achievement earned outside the canvas still gets its banner. */
	public void announceAchievement(com.trappychinchompa.Achievement achievement)
	{
		canvas.queueBanner(achievement);
	}

	/** After a progress wipe: reload every earned number from config. */
	public void reloadProgress()
	{
		canvas.reloadProgress();
		if (showingAchievements)
		{
			achievements.refresh();
		}
	}

	/** The dock's Play: the duel's next game on the canvas, once the relay has revealed its seed. */
	public void playDuel(String id)
	{
		final com.trappychinchompa.duel.DuelRecord r = plugin.getDuels() == null ? null : plugin.getDuels().play(id);
		if (r != null)
		{
			playNow(r);
		}
	}

	public void playNow(com.trappychinchompa.duel.DuelRecord r)
	{
		showGame();
		canvas.startDuelGame(r);
	}

	/** A short floating word on the canvas. */
	public void duelNotice(String text)
	{
		canvas.notice(text);
	}

	public void repaintGame()
	{
		dock.refresh();
		canvas.repaint();
	}

	/** Keys go to the game after any dock button. */
	public void focusGame()
	{
		canvas.requestFocusInWindow();
	}

	/** Trap invincibility on: the integrity gate for duels. */
	public boolean isSandbox()
	{
		return canvas.isSandbox();
	}

	public void dispose()
	{
		canvas.stop();
	}
}
