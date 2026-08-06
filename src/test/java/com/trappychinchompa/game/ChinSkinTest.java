package com.trappychinchompa.game;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChinSkinTest
{
	@Test
	@DisplayName("the score ladder ascends pixel 0 / grey 10 / red 20 / black 40 / egg 60 / chompy 80 / chicken 100")
	void ladderShape()
	{
		int previous = -1;
		int ranked = 0;
		for (ChinSkin skin : ChinSkin.values())
		{
			if (skin.getUnlockAchievementId() != null)
			{
				continue;
			}
			assertTrue(skin.getUnlockHighScore() > previous,
				skin + " unlocks after the rank before it");
			previous = skin.getUnlockHighScore();
			ranked++;
		}
		assertEquals(7, ranked, "seven score-ranked critters");
		assertEquals(0, ChinSkin.PIXEL_CHIN.getUnlockHighScore(), "the mascot is free");
		assertEquals(100, ChinSkin.RUBBER_CHICKEN.getUnlockHighScore(), "the chicken is the summit");
	}

	@Test
	@DisplayName("highestUnlocked walks the wardrobe with the high score and earned trophies")
	void highestUnlocked()
	{
		final Set<String> none = Collections.emptySet();
		assertEquals(ChinSkin.PIXEL_CHIN, ChinSkin.highestUnlocked(0, none));
		assertEquals(ChinSkin.GREY_CHINCHOMPA, ChinSkin.highestUnlocked(10, none));
		assertEquals(ChinSkin.RED_CHINCHOMPA, ChinSkin.highestUnlocked(20, none));
		assertEquals(ChinSkin.BLACK_CHINCHOMPA, ChinSkin.highestUnlocked(45, none));
		assertEquals(ChinSkin.RUBBER_CHICKEN, ChinSkin.highestUnlocked(240, none));
		assertEquals(ChinSkin.CABBAGE, ChinSkin.highestUnlocked(0,
			Collections.singleton("score_EASY_100")), "trophies count as newest");
	}

	@Test
	@DisplayName("achievement-gated skins answer only to their achievement")
	void achievementGatedSkins()
	{
		final Set<String> none = Collections.emptySet();
		assertFalse(ChinSkin.TZTOK_JAD.isUnlocked(9_999, none),
			"no score buys the jad");
		assertTrue(ChinSkin.TZTOK_JAD.isUnlocked(0, Collections.singleton("score_HARD_100")));
		assertTrue(ChinSkin.CABBAGE.isUnlocked(0, Collections.singleton("score_EASY_100")));
	}

	@Test
	@DisplayName("a new best reports the newest critter it unlocked, or nothing")
	void newlyUnlocked()
	{
		assertNull(ChinSkin.newlyUnlocked(45, 46), "no threshold crossed");
		assertEquals(ChinSkin.RED_CHINCHOMPA, ChinSkin.newlyUnlocked(19, 20));
		assertEquals(ChinSkin.EGG, ChinSkin.newlyUnlocked(45, 60));
		assertEquals(ChinSkin.RUBBER_CHICKEN, ChinSkin.newlyUnlocked(85, 120),
			"the newest of several crossed unlocks wins");
	}

	@Test
	@DisplayName("every skin has a sprite source: a distinct item id or a bundled resource")
	void skinSanity()
	{
		Set<Integer> ids = new HashSet<>();
		for (ChinSkin skin : ChinSkin.values())
		{
			if (skin.getResourceName() != null)
			{
				assertTrue(skin.getItemId() <= 0, skin + " is resource-backed, not an item");
				continue;
			}
			assertTrue(ids.add(skin.getItemId()), skin + " has a unique item id");
			assertTrue(skin.getItemId() > 0, skin + " item id is positive");
		}
		assertEquals("chin_sprite.png", ChinSkin.PIXEL_CHIN.getResourceName());
	}
}
