package com.trappychinchompa.ui;

import com.trappychinchompa.TrappyChinchompaConfig;
import com.trappychinchompa.TrappyChinchompaPlugin;
import java.awt.CardLayout;
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
	private final GameCanvas canvas;
	private final AchievementsPanel achievements;
	private boolean active;
	private boolean showingAchievements;

	public TrappyChinchompaPanel(TrappyChinchompaPlugin plugin, ItemManager itemManager,
		SpriteManager spriteManager, TrappyChinchompaConfig config)
	{
		super(false);
		setLayout(cards);
		canvas = new GameCanvas(plugin, itemManager, spriteManager, config);
		achievements = new AchievementsPanel(plugin, this::showGame);
		add(canvas, CARD_GAME);
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

	public void dispose()
	{
		canvas.stop();
	}
}
