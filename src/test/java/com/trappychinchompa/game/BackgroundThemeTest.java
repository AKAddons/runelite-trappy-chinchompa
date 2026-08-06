package com.trappychinchompa.game;

import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundThemeTest
{
	private static final Set<String> NONE = Collections.emptySet();

	@Test
	@DisplayName("the level ladder is strictly ascending, opens at level 1, and caps at 99")
	void ladderShape()
	{
		int previous = 0;
		int levelZones = 0;
		for (BackgroundTheme theme : BackgroundTheme.values())
		{
			if (theme.isAchievementGated())
			{
				continue;
			}
			assertTrue(theme.getUnlockLevel() > previous,
				theme + " unlocks after the zone before it");
			assertTrue(theme.getUnlockLevel() <= HunterXp.MAX_LEVEL, theme + " is reachable");
			previous = theme.getUnlockLevel();
			levelZones++;
		}
		assertEquals(11, levelZones, "eleven level-ladder zones");
		assertEquals(99, BackgroundTheme.PRIFDDINAS.getUnlockLevel(), "the crystal city caps the ladder");
	}

	@Test
	@DisplayName("highestUnlocked walks the ladder with level and earned trophies")
	void highestUnlocked()
	{
		assertEquals(BackgroundTheme.FELDIP_MARSH, BackgroundTheme.highestUnlocked(1, NONE));
		assertEquals(BackgroundTheme.LUMBRIDGE, BackgroundTheme.highestUnlocked(10, NONE));
		assertEquals(BackgroundTheme.ARDOUGNE, BackgroundTheme.highestUnlocked(50, NONE));
		assertEquals(BackgroundTheme.PRIFDDINAS, BackgroundTheme.highestUnlocked(99, NONE));
		assertEquals(BackgroundTheme.PANDEMONIUM, BackgroundTheme.highestUnlocked(1,
			Collections.singleton("score_EASY_250")), "trophies count as newest");
	}

	@Test
	@DisplayName("achievement-gated zones answer only to their achievements, ANY of them")
	void gatedZones()
	{
		assertFalse(BackgroundTheme.PANDEMONIUM.isUnlocked(99, NONE), "no level opens it");
		assertTrue(BackgroundTheme.PANDEMONIUM.isUnlocked(1,
			Collections.singleton("score_EASY_250")));
		assertTrue(BackgroundTheme.INFERNO.isUnlocked(1,
			Collections.singleton("streak_NORMAL_50x20")), "the Normal streak opens it");
		assertTrue(BackgroundTheme.INFERNO.isUnlocked(1,
			Collections.singleton("streak_HARD_50x20")), "so does the Hard streak");
		assertFalse(BackgroundTheme.INFERNO.isUnlocked(99, NONE));
	}

	@Test
	@DisplayName("a level jump reports the newest zone it unlocked, or nothing")
	void newlyUnlocked()
	{
		assertNull(BackgroundTheme.newlyUnlocked(3, 9), "no unlock crossed");
		assertEquals(BackgroundTheme.LUMBRIDGE, BackgroundTheme.newlyUnlocked(9, 10));
		assertEquals(BackgroundTheme.AL_KHARID, BackgroundTheme.newlyUnlocked(10, 39),
			"the newest of several crossed unlocks wins");
		assertEquals(BackgroundTheme.PRIFDDINAS, BackgroundTheme.newlyUnlocked(90, 99),
			"gated zones never appear here");
		assertNull(BackgroundTheme.newlyUnlocked(99, 99), "nothing left to unlock");
	}
}
