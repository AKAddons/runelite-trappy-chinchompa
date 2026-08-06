package com.trappychinchompa.game;

import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Playable critters. Most unlock by HIGH SCORE - your best run is your
 * wardrobe; the last two are achievement-gated trophies (ids matched
 * against the achievement catalog by a drift-guard test, since this
 * package cannot depend on it). Each is a real in-game item whose sprite
 * is loaded at runtime from the player's own client via ItemManager.
 *
 * Enum order is ladder order for the score-based ranks.
 */
public enum ChinSkin
{
	/** The plugin's own mascot, free from the start - original art bundled
	 * as a resource, the one critter that isn't a game item. */
	PIXEL_CHIN(-1, "chin_sprite.png", 0, null, null, false),
	GREY_CHINCHOMPA(ItemID.CHINCHOMPA_CAPTURED, null, 10, null, null, false),
	RED_CHINCHOMPA(ItemID.CHINCHOMPA_BIG_CAPTURED, null, 20, null, null, false),
	BLACK_CHINCHOMPA(ItemID.CHINCHOMPA_BLACK, null, 40, null, null, false),
	EGG(ItemID.EGG, null, 60, null, null, true),
	CHOMPY_BIRD(ItemID.CHOMPY_BIRD_OBJ, null, 80, null, null, true),
	RUBBER_CHICKEN(ItemID.RUBBER_CHICKEN, null, 100, null, null, true),
	/** The monument to wasted effort: clear 100 traps on Easy. */
	CABBAGE(ItemID.CABBAGE, null, Integer.MAX_VALUE, "score_EASY_100", null, true),
	/** Mystery reward: clear 250 traps on Normal. */
	VORKI(ItemID.VORKATHPET, null, Integer.MAX_VALUE, "score_NORMAL_250", null, true),
	/** Cross-plugin easter egg: Loadie, Loadout Lab's bottle mascot,
	 * unlocked by having Loadout Lab installed. Its art, bundled by its
	 * author. */
	LAB_MASCOT(-1, "lab_mascot.png", Integer.MAX_VALUE, "loadout_lab", "Loadie", true),
	/** The prestige mount and final flex: clear 100 traps on Hard. (The
	 * sprite is the pet item, but it flies under the boss's famous name.) */
	TZTOK_JAD(ItemID.JAD_PET, null, Integer.MAX_VALUE, "score_HARD_100", "TzTok-Jad", true);

	private final int itemId;
	/** Bundled-resource sprite name, or null for item-sprite critters. */
	private final String resourceName;
	private final int unlockHighScore;
	private final String unlockAchievementId;
	/** Exact display styling where titleCase of the name won't do. */
	private final String displayName;
	/** Everything past the chinchompas is a sealed ?????? until earned. */
	private final boolean mysteryReward;

	ChinSkin(int itemId, String resourceName, int unlockHighScore, String unlockAchievementId,
		String displayName, boolean mysteryReward)
	{
		this.itemId = itemId;
		this.resourceName = resourceName;
		this.unlockHighScore = unlockHighScore;
		this.unlockAchievementId = unlockAchievementId;
		this.displayName = displayName;
		this.mysteryReward = mysteryReward;
	}

	public String getResourceName()
	{
		return resourceName;
	}

	public boolean isMysteryReward()
	{
		return mysteryReward;
	}

	/** The styled display name, or null to fall back to titleCase. */
	public String getDisplayName()
	{
		return displayName;
	}

	public int getItemId()
	{
		return itemId;
	}

	public int getUnlockHighScore()
	{
		return unlockHighScore;
	}

	/** The achievement gating this skin, or null for score-based ranks. */
	public String getUnlockAchievementId()
	{
		return unlockAchievementId;
	}

	/** The full check: score threshold or earned achievement. */
	public boolean isUnlocked(int highScore, Set<String> earnedAchievements)
	{
		if (unlockAchievementId != null)
		{
			return earnedAchievements.contains(unlockAchievementId);
		}
		return highScore >= unlockHighScore;
	}

	/** The newest unlocked critter; the pixel chin is always open. */
	public static ChinSkin highestUnlocked(int highScore, Set<String> earnedAchievements)
	{
		ChinSkin best = PIXEL_CHIN;
		for (ChinSkin skin : values())
		{
			if (skin.isUnlocked(highScore, earnedAchievements))
			{
				best = skin;
			}
		}
		return best;
	}

	/**
	 * The newest score-ranked critter unlocked by pushing the high score
	 * from before to after, or null. Achievement-gated skins announce
	 * through their achievements instead.
	 */
	public static ChinSkin newlyUnlocked(int highBefore, int highAfter)
	{
		ChinSkin newest = null;
		for (ChinSkin skin : values())
		{
			if (skin.unlockAchievementId == null
				&& skin.unlockHighScore > highBefore && skin.unlockHighScore <= highAfter)
			{
				newest = skin;
			}
		}
		return newest;
	}
}
