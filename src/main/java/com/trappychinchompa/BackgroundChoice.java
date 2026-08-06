package com.trappychinchompa;

import com.trappychinchompa.game.BackgroundTheme;
import net.runelite.client.util.Text;

/**
 * The start-screen zone picker's row model (picks persist under the hidden
 * "background" config key). Same lock-aware labelling as
 * {@link CritterChoice}: a locked zone wears its level requirement, earning
 * it drops the tag, and achievement-gated zones stay hidden until earned.
 */
public enum BackgroundChoice
{
	FELDIP_MARSH(BackgroundTheme.FELDIP_MARSH),
	LUMBRIDGE(BackgroundTheme.LUMBRIDGE),
	VARROCK(BackgroundTheme.VARROCK),
	AL_KHARID(BackgroundTheme.AL_KHARID),
	MORYTANIA(BackgroundTheme.MORYTANIA),
	ARDOUGNE(BackgroundTheme.ARDOUGNE),
	KOUREND(BackgroundTheme.KOUREND),
	FREMENNIK(BackgroundTheme.FREMENNIK),
	VARLAMORE(BackgroundTheme.VARLAMORE),
	WILDERNESS(BackgroundTheme.WILDERNESS),
	PRIFDDINAS(BackgroundTheme.PRIFDDINAS),
	PANDEMONIUM(BackgroundTheme.PANDEMONIUM),
	INFERNO(BackgroundTheme.INFERNO);

	private final BackgroundTheme theme;
	private final String base;

	BackgroundChoice(BackgroundTheme theme)
	{
		this.theme = theme;
		this.base = Text.titleCase(theme);
	}

	public BackgroundTheme toTheme()
	{
		return theme;
	}

	/** Mystery rewards hide from the picker entirely until earned. */
	public boolean isMystery()
	{
		return theme.isAchievementGated();
	}

	public boolean isUnlocked()
	{
		return UnlockState.isDevUnlockAll()
			|| theme.isUnlocked(UnlockState.getAntiHunterLevel(),
				UnlockState.getEarnedAchievements());
	}

	@Override
	public String toString()
	{
		if (isUnlocked())
		{
			return base;
		}
		return isMystery() ? Achievement.MYSTERY_MASK : base + " (" + theme.getUnlockLevel() + ")";
	}
}
