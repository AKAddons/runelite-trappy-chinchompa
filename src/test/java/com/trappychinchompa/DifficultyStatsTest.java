package com.trappychinchompa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DifficultyStatsTest
{
	@Test
	@DisplayName("average traps per run shows one rounded decimal")
	void averageRounding()
	{
		assertEquals("4.3", new DifficultyStats(3, 7, 0, 13).averageText());
		assertEquals("5.0", new DifficultyStats(2, 5, 0, 10).averageText());
		assertEquals("0.7", new DifficultyStats(3, 2, 0, 2).averageText());
	}

	@Test
	@DisplayName("no runs yet reads 0.0 instead of dividing by zero")
	void zeroRuns()
	{
		assertEquals("0.0", new DifficultyStats(0, 0, 0, 0).averageText());
	}

	@Test
	@DisplayName("zero-trap runs still drag the average down")
	void zeroScoreRunsCount()
	{
		assertEquals("2.5", new DifficultyStats(4, 10, 0, 10).averageText());
	}
}
