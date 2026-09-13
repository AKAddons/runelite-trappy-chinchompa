package com.trappychinchompa.duel;

import com.trappychinchompa.Difficulty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesTest
{
	private static Series series(String code)
	{
		return new Series(DuelRules.parse(code));
	}

	@Test
	@DisplayName("best round of 3: the single highest score wins, even with a worse total")
	void bestRoundTakesTheHighestSingleScore()
	{
		Series s = series("h-bs3");
		s.record(1, 5, 8);
		s.record(2, 9, 8);
		s.record(3, 2, 8);
		assertTrue(s.complete());
		assertTrue(s.compare() > 0);
	}

	@Test
	@DisplayName("average of 3: the higher total wins, compared as integer sums")
	void averageTakesTheHigherSum()
	{
		Series s = series("h-av3");
		s.record(1, 5, 8);
		s.record(2, 9, 8);
		s.record(3, 2, 8);
		assertTrue(s.complete());
		assertTrue(s.compare() < 0);
	}

	@Test
	@DisplayName("a tied single game goes to sudden death and the first difference decides")
	void tiedBestOfOneGoesToSuddenDeath()
	{
		Series s = series("n-bs1");
		s.record(1, 4, 4);
		assertFalse(s.complete());
		assertEquals(2, s.nextGameIndex());
		assertTrue(s.lineFor(2).isSuddenDeath());
		s.record(2, 3, 3);
		assertFalse(s.complete());
		s.record(3, 2, 1);
		assertTrue(s.complete(), "a sudden-death game decides on its own score");
		assertTrue(s.compare() > 0);
		assertEquals(3, s.lines().size());
		assertEquals("best 4 to 4, sudden death 2 to 1", s.scoreLine());
	}

	@Test
	@DisplayName("average of 5 with equal sums keeps playing until they differ")
	void tiedAverageSumsKeepPlaying()
	{
		Series s = series("e-av5");
		int[][] scores = {{1, 5}, {5, 1}, {3, 3}, {2, 4}, {4, 2}};
		for (int i = 0; i < 5; i++)
		{
			s.record(i + 1, scores[i][0], scores[i][1]);
		}
		assertFalse(s.complete());
		assertEquals(6, s.nextGameIndex());
		s.record(6, 1, 0);
		assertTrue(s.complete());
		assertTrue(s.compare() > 0);
	}

	@Test
	@DisplayName("all games are played: a 2-0 lead in best round of 3 is not yet complete")
	void everyGameIsPlayed()
	{
		Series s = series("h-bs3");
		s.record(1, 9, 1);
		s.record(2, 9, 1);
		assertFalse(s.complete());
		assertEquals(3, s.nextGameIndex());
		assertFalse(s.lineFor(3).isSuddenDeath());
	}

	@Test
	@DisplayName("the score line reads games won for best round and totals for average")
	void scoreLine()
	{
		Series bs = series("h-bs3");
		bs.record(1, 5, 8);
		bs.record(2, 9, 8);
		bs.record(3, 2, 8);
		assertEquals("best 9 to 8", bs.scoreLine());
		Series av = series("h-av3");
		av.record(1, 5, 8);
		av.record(2, 9, 8);
		av.record(3, 2, 8);
		assertEquals("total 16 to 24", av.scoreLine());
		assertEquals(Difficulty.HARD, av.getRules().getDifficulty());
	}
}
