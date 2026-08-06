package com.trappychinchompa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundChoiceTest
{
	@BeforeEach
	@AfterEach
	void resetUnlocks()
	{
		UnlockState.update(0, 1);
	}

	@Test
	@DisplayName("locked picker entries wear their requirement; unlocking drops the tag")
	void lockAwareLabels()
	{
		assertEquals("Feldip Marsh", BackgroundChoice.FELDIP_MARSH.toString(),
			"always-open zone never wears a tag");
		assertEquals("Pixel Chin", CritterChoice.PIXEL_CHIN.toString(),
			"the free critter never wears a tag");
		assertEquals("Morytania (40)", BackgroundChoice.MORYTANIA.toString());
		assertEquals("Grey (10)", CritterChoice.GREY_CHINCHOMPA.toString(),
			"chinchompas advertise their requirement");
		assertEquals("Red (20)", CritterChoice.RED_CHINCHOMPA.toString());

		// Andrew mid-grind: best 45, level 50.
		UnlockState.update(45, 50);
		assertEquals("Morytania", BackgroundChoice.MORYTANIA.toString(), "earned: tag gone");
		assertEquals("Ardougne", BackgroundChoice.ARDOUGNE.toString());
		assertEquals("Kourend (60)", BackgroundChoice.KOUREND.toString(), "still locked");
		assertEquals("Black", CritterChoice.BLACK_CHINCHOMPA.toString());
		assertEquals("Prifddinas (99)", BackgroundChoice.PRIFDDINAS.toString());
	}

	@Test
	@DisplayName("every non-chinchompa critter is a hidden mystery until earned")
	void mysteryCritters()
	{
		for (CritterChoice c : new CritterChoice[]{CritterChoice.EGG, CritterChoice.CHOMPY_BIRD,
			CritterChoice.RUBBER_CHICKEN, CritterChoice.CABBAGE, CritterChoice.VORKI,
			CritterChoice.TZTOK_JAD})
		{
			assertTrue(c.isMystery(), c + " is a mystery");
			assertEquals("??????", c.toString(), c + " stays sealed while locked");
		}
		assertFalse(CritterChoice.BLACK_CHINCHOMPA.isMystery(), "chins are advertised");

		UnlockState.update(60, 1);
		assertEquals("Egg", CritterChoice.EGG.toString(), "earned: revealed");
		UnlockState.update(0, 1, java.util.Collections.singleton("score_HARD_100"));
		assertEquals("Jad", CritterChoice.TZTOK_JAD.toString(), "trophy earned: revealed");
	}

	@Test
	@DisplayName("isUnlocked follows the published state; the starters are always allowed")
	void unlockChecks()
	{
		assertTrue(BackgroundChoice.FELDIP_MARSH.isUnlocked());
		assertTrue(CritterChoice.PIXEL_CHIN.isUnlocked());
		assertFalse(CritterChoice.GREY_CHINCHOMPA.isUnlocked(), "grey now takes a best of 10");
		assertFalse(BackgroundChoice.WILDERNESS.isUnlocked());
		assertFalse(CritterChoice.EGG.isUnlocked());
		UnlockState.update(60, 90);
		assertTrue(CritterChoice.GREY_CHINCHOMPA.isUnlocked());
		assertTrue(BackgroundChoice.WILDERNESS.isUnlocked());
		assertTrue(CritterChoice.EGG.isUnlocked());
	}
}
