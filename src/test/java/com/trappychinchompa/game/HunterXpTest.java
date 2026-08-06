package com.trappychinchompa.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HunterXpTest
{
	@Test
	@DisplayName("the wrapper hits the canonical OSRS checkpoints (83, half-of-99 at 92, 13,034,431)")
	void canonicalCheckpoints()
	{
		assertEquals(0, HunterXp.xpForLevel(1));
		assertEquals(83, HunterXp.xpForLevel(2));
		assertEquals(6_517_253, HunterXp.xpForLevel(92), "level 92 is half of 99");
		assertEquals(13_034_431, HunterXp.xpForLevel(99));
	}

	@Test
	@DisplayName("anti-hunter xp converts to a level, clamped to the real-level cap")
	void levelForXp()
	{
		assertEquals(1, HunterXp.levelForXpTenths(0));
		assertEquals(1, HunterXp.levelForXpTenths(82 * 10L));
		assertEquals(2, HunterXp.levelForXpTenths(83 * 10L));
		assertEquals(99, HunterXp.levelForXpTenths(13_034_431 * 10L));
		assertEquals(99, HunterXp.levelForXpTenths(Long.MAX_VALUE / 2), "capped at 99, not virtual");
		assertEquals(1, HunterXp.levelForXpTenths(-5), "garbage clamps to level 1");
	}
}
