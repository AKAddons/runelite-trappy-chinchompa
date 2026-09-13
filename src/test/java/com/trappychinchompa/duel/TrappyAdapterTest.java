package com.trappychinchompa.duel;

import com.trappychinchompa.game.BoxTrap;
import com.trappychinchompa.game.FlappyGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrappyAdapterTest
{
	private static final DuelRules HARD = DuelRules.parse("h-bs3");
	private static final DuelRules NORMAL = DuelRules.parse("n-bs3");
	private static final long SEED = 4242L;

	/** Flap whenever the chin sinks below the next gap's centre. Deterministic, mediocre. */
	private static void autopilot(DuelRun run, int maxTicks)
	{
		run.flap();
		for (int t = 0; t < maxTicks && !run.ended(); t++)
		{
			FlappyGame game = run.game();
			int target = DuelRun.HEIGHT / 2;
			for (BoxTrap trap : game.getTraps())
			{
				if (trap.getX() + FlappyGame.TRAP_WIDTH > FlappyGame.CHIN_X - FlappyGame.CHIN_HALF_W)
				{
					target = trap.getGapCenter();
					break;
				}
			}
			if (game.getChinY() > target + 15 && game.getChinVy() >= 0)
			{
				run.flap();
			}
			run.tick();
		}
	}

	@Test
	@DisplayName("two runs on the same seed and rules spawn the same first twenty gaps")
	void sameSeedSameTraps()
	{
		List<Integer> a = spawnOrder(new DuelRun(SEED, HARD), 20);
		List<Integer> b = spawnOrder(new DuelRun(SEED, HARD), 20);
		assertEquals(20, a.size());
		assertEquals(a, b);
		assertNotEquals(a, spawnOrder(new DuelRun(SEED + 1, HARD), 20));
	}

	/** Gap centres in spawn order: the newest tower is always last in the list. */
	private static List<Integer> spawnOrder(DuelRun run, int wanted)
	{
		List<Integer> centres = new ArrayList<>();
		run.game().setTrapInvincible(true);
		run.flap();
		int spawned = 0;
		while (centres.size() < wanted && !run.ended())
		{
			if (run.game().getChinY() > DuelRun.HEIGHT / 2.0)
			{
				run.flap();
			}
			run.tick();
			List<BoxTrap> traps = run.game().getTraps();
			// Spawn count = towers on screen + towers already passed (scored).
			int total = traps.size() + spawnedBefore(run);
			if (total > spawned)
			{
				centres.add(traps.get(traps.size() - 1).getGapCenter());
				spawned = total;
			}
		}
		return centres;
	}

	private static int spawnedBefore(DuelRun run)
	{
		return run.game().getScore();
	}

	@Test
	@DisplayName("a recorded run replays headlessly to exactly its own score")
	void replayReproducesTheRecordedScore()
	{
		DuelRun run = new DuelRun(SEED, NORMAL);
		autopilot(run, 20_000);
		assertTrue(run.ended());
		assertTrue(run.score() > 0, "autopilot should clear at least one trap, scored " + run.score());
		int replayed = new TrappyAdapter().replay(SEED, NORMAL, run.flapTicks(), run.totalTicks());
		assertEquals(run.score(), replayed);
	}

	@Test
	@DisplayName("a trace with one flap removed replays to a different score")
	void tamperedTraceReplaysDifferently()
	{
		DuelRun run = new DuelRun(SEED, NORMAL);
		autopilot(run, 20_000);
		List<Integer> flaps = new ArrayList<>(run.flapTicks());
		flaps.remove(flaps.size() / 2);
		int replayed = new TrappyAdapter().replay(SEED, NORMAL, flaps, run.totalTicks());
		assertNotEquals(run.score(), replayed);
	}

	@Test
	@DisplayName("a run that never launches ends at the ten-minute cap with no score, and replay honours the cap")
	void cappedAtTenMinutes()
	{
		DuelRun run = new DuelRun(SEED, HARD);
		for (int i = 0; i < DuelRun.MAX_TICKS + 500; i++)
		{
			run.tick();
		}
		assertTrue(run.ended());
		assertEquals(DuelRun.MAX_TICKS, run.totalTicks());
		assertEquals(0, run.score());
		assertEquals(0, new TrappyAdapter().replay(SEED, HARD, List.of(), DuelRun.MAX_TICKS + 500));
	}

	@Test
	@DisplayName("flaps after the run has ended are not recorded")
	void flapsAfterTheEndAreIgnored()
	{
		DuelRun run = new DuelRun(SEED, HARD);
		run.flap();
		while (!run.ended())
		{
			run.tick();
		}
		int recorded = run.flapTicks().size();
		run.flap();
		assertEquals(recorded, run.flapTicks().size());
		assertFalse(run.flapTicks().isEmpty());
	}

}
