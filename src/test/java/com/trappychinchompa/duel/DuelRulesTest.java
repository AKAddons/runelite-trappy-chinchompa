package com.trappychinchompa.duel;

import com.trappychinchompa.Difficulty;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DuelRulesTest
{
	@Test
	public void everyRuleSetRoundTripsThroughItsCanonicalCode()
	{
		for (Difficulty d : Difficulty.values())
		{
			for (DuelRules.Metric m : DuelRules.Metric.values())
			{
				for (int series : new int[]{1, 3, 5})
				{
					DuelRules rules = new DuelRules(d, m, series);
					DuelRules back = DuelRules.parse(rules.canonical());
					assertEquals(rules, back);
				}
			}
		}
	}

	@Test
	public void canonicalCodesAreHumanGuessable()
	{
		assertEquals("h-bs3", new DuelRules(Difficulty.HARD, DuelRules.Metric.BEST_ROUND, 3).canonical());
		assertEquals("n-av5", new DuelRules(Difficulty.NORMAL, DuelRules.Metric.AVERAGE, 5).canonical());
		assertEquals("e-bs1", new DuelRules(Difficulty.EASY, DuelRules.Metric.BEST_ROUND, 1).canonical());
	}

	@Test
	public void describesItselfForPlayers()
	{
		assertEquals("Hard, best round of 3", DuelRules.parse("h-bs3").describe());
		assertEquals("Normal, average of 5", DuelRules.parse("n-av5").describe());
		assertEquals("Easy, best round of 1", DuelRules.parse("e-bs1").describe());
	}

	@Test
	public void rejectsAnythingOutsideTheGrammar()
	{
		assertNull(DuelRules.parse("h-bs7"));
		assertNull(DuelRules.parse("x-bs3"));
		assertNull(DuelRules.parse("h-av2"));
		assertNull(DuelRules.parse("h-xx3"));
		assertNull(DuelRules.parse(""));
		assertNull(DuelRules.parse(null));
	}
}
