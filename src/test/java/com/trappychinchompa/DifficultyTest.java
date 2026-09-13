package com.trappychinchompa;

import com.trappychinchompa.game.FlappyGame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyTest
{
	@Test
	@DisplayName("pole awards are whole xp with a constant step: easy 1,2,3 / normal 5,15,25 / hard 13,38,63")
	void wholeNumberAwards()
	{
		assertEquals(10, Difficulty.EASY.xpTenthsForPole(1), "+1");
		assertEquals(20, Difficulty.EASY.xpTenthsForPole(2), "+2");
		assertEquals(30, Difficulty.EASY.xpTenthsForPole(3), "+3");

		assertEquals(50, Difficulty.NORMAL.xpTenthsForPole(1), "+5");
		assertEquals(150, Difficulty.NORMAL.xpTenthsForPole(2), "+15");
		assertEquals(250, Difficulty.NORMAL.xpTenthsForPole(3), "+25");

		assertEquals(130, Difficulty.HARD.xpTenthsForPole(1), "+13");
		assertEquals(380, Difficulty.HARD.xpTenthsForPole(2), "+38");
		assertEquals(630, Difficulty.HARD.xpTenthsForPole(3), "+63");

		for (Difficulty d : Difficulty.values())
		{
			final long step = d.xpTenthsForPole(2) - d.xpTenthsForPole(1);
			for (int pole = 1; pole <= 50; pole++)
			{
				assertEquals(0, d.xpTenthsForPole(pole) % 10,
					d + " pole " + pole + " is whole xp");
				if (pole > 1)
				{
					assertEquals(step, d.xpTenthsForPole(pole) - d.xpTenthsForPole(pole - 1),
						d + " keeps a constant interval");
				}
			}
		}
	}

	@Test
	@DisplayName("a run's total is exactly the sum of its pole drops at every difficulty")
	void runTotalMatchesDropSum()
	{
		for (Difficulty d : Difficulty.values())
		{
			long sum = 0;
			for (int pole = 1; pole <= 40; pole++)
			{
				sum += d.xpTenthsForPole(pole);
				assertEquals(sum, d.xpTenthsForRun(pole),
					d + " total matches drop-by-drop sum at pole " + pole);
			}
		}
		assertEquals(11_250, Difficulty.NORMAL.xpTenthsForRun(15), "normal unchanged: 1,125 xp");
		assertEquals(1_200, Difficulty.EASY.xpTenthsForRun(15), "easy 15 poles: 120 xp");
		assertEquals(28_200, Difficulty.HARD.xpTenthsForRun(15), "hard 15 poles: 2,820 xp");
	}

	@Test
	@DisplayName("only easy runs are barred from setting the high score")
	void recordEligibility()
	{
		assertFalse(Difficulty.EASY.countsForRecords(), "easy is the practice pond");
		assertTrue(Difficulty.NORMAL.countsForRecords());
		assertTrue(Difficulty.HARD.countsForRecords());
	}

	@Test
	@DisplayName("gap travel tightens as speed rises - hard wanders the least by far")
	void maxGapStepOrdering()
	{
		assertTrue(Difficulty.EASY.getMaxGapStep() > Difficulty.NORMAL.getMaxGapStep());
		assertTrue(Difficulty.NORMAL.getMaxGapStep() > Difficulty.HARD.getMaxGapStep());
		assertTrue(Difficulty.HARD.getMaxGapStep() <= 100, "hard stays close to followable");
		for (Difficulty d : Difficulty.values())
		{
			assertTrue(d.getMaxGapStep() < FlappyGame.GAP_BAND_HEIGHT,
				d + " constrains the roll below the free band");
		}
	}
}
