package com.trappychinchompa;

import com.trappychinchompa.game.ChinSkin;

/**
 * The start-screen critter picker's row model (picks persist under the
 * hidden "skin" config key). The three chinchompas advertise their score
 * requirements; every other critter is a sealed mystery - hidden from the
 * picker and masked until its reward is earned.
 */
public enum CritterChoice
{
	PIXEL_CHIN(ChinSkin.PIXEL_CHIN, "Pixel Chin"),
	GREY_CHINCHOMPA(ChinSkin.GREY_CHINCHOMPA, "Grey"),
	RED_CHINCHOMPA(ChinSkin.RED_CHINCHOMPA, "Red"),
	BLACK_CHINCHOMPA(ChinSkin.BLACK_CHINCHOMPA, "Black"),
	EGG(ChinSkin.EGG, "Egg"),
	CHOMPY_BIRD(ChinSkin.CHOMPY_BIRD, "Chompy"),
	RUBBER_CHICKEN(ChinSkin.RUBBER_CHICKEN, "Chicken"),
	CABBAGE(ChinSkin.CABBAGE, "Cabbage"),
	VORKI(ChinSkin.VORKI, "Vorki"),
	LAB_MASCOT(ChinSkin.LAB_MASCOT, "Loadie"),
	TZTOK_JAD(ChinSkin.TZTOK_JAD, "Jad");

	private final ChinSkin skin;
	private final String base;
	private final String lockedLabel;

	CritterChoice(ChinSkin skin, String base)
	{
		this.skin = skin;
		this.base = base;
		this.lockedLabel = skin.isMysteryReward()
			? Achievement.MYSTERY_MASK : base + " (" + skin.getUnlockHighScore() + ")";
	}

	public ChinSkin toSkin()
	{
		return skin;
	}

	/** Mystery rewards hide from the picker entirely until earned. */
	public boolean isMystery()
	{
		return skin.isMysteryReward();
	}

	public boolean isUnlocked()
	{
		return UnlockState.isDevUnlockAll()
			|| skin.isUnlocked(UnlockState.getBestScore(), UnlockState.getEarnedAchievements());
	}

	@Override
	public String toString()
	{
		return isUnlocked() ? base : lockedLabel;
	}
}
