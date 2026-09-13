package com.trappychinchompa.duel;

import com.trappychinchompa.game.BoxTrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DuelRunTest
{
	private static final DuelRules HARD = DuelRules.parse("h-bs3");

	/** Gap centres in spawn order: the newest tower is always last. */
	private static List<Integer> spawnOrder(DuelRun run, int wanted)
	{
		List<Integer> centres = new ArrayList<>();
		run.game().setTrapInvincible(true);
		run.flap();
		int spawned = 0;
		for (int t = 0; t < 100_000 && centres.size() < wanted && !run.ended(); t++)
		{
			if (run.game().getChinY() > DuelRun.HEIGHT / 2.0)
			{
				run.flap();
			}
			run.tick();
			List<BoxTrap> traps = run.game().getTraps();
			int total = traps.size() + run.game().getScore();
			if (total > spawned)
			{
				centres.add(traps.get(traps.size() - 1).getGapCenter());
				spawned = total;
			}
		}
		return centres;
	}

	@Test
	@DisplayName("two runs on the same seed and rules spawn the same first twenty gaps")
	void sameSeedSameTraps()
	{
		List<Integer> a = spawnOrder(new DuelRun(4242L, HARD), 20);
		List<Integer> b = spawnOrder(new DuelRun(4242L, HARD), 20);
		assertEquals(20, a.size());
		assertEquals(a, b);
		assertNotEquals(a, spawnOrder(new DuelRun(4243L, HARD), 20));
	}

	@Test
	@DisplayName("a duel run plays on the fixed 242x480 world with the rules' difficulty")
	void fixedWorld()
	{
		DuelRun run = new DuelRun(1L, HARD);
		assertEquals(HARD.getDifficulty().getGapSize(), run.game().getGapSize());
		assertEquals(HARD.getDifficulty().getSpeed(), run.game().getSpeed(), 1e-9);
	}
}
