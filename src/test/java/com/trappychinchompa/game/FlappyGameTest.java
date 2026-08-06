package com.trappychinchompa.game;

import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlappyGameTest
{
	private static final int W = 242;
	private static final int H = 480;
	private static final int FLOOR = H - FlappyGame.GROUND_HEIGHT;

	private FlappyGame game;

	@BeforeEach
	void setUp()
	{
		game = new FlappyGame(new Random(42));
		game.setViewport(W, H);
		game.setDifficulty(110, 1.9);
	}

	private void tick(int n)
	{
		for (int i = 0; i < n; i++)
		{
			game.tick();
		}
	}

	private void runUntilDead()
	{
		while (game.getState() == FlappyGame.State.RUNNING)
		{
			game.tick();
		}
	}

	@Test
	@DisplayName("starts in READY with the chin bobbing gently around mid-air")
	void startsReadyBobbing()
	{
		double baseline = FLOOR / 2.0;
		for (int i = 0; i < 200; i++)
		{
			game.tick();
			assertEquals(FlappyGame.State.READY, game.getState());
			assertTrue(Math.abs(game.getChinY() - baseline) <= 5.5,
				"bobbing stays within the idle amplitude");
		}
		assertEquals(0, game.getScore());
		assertNull(game.getDeathCause());
	}

	@Test
	@DisplayName("the first flap starts the run and pushes the chin upward")
	void firstFlapStartsRun()
	{
		game.flap();
		assertEquals(FlappyGame.State.RUNNING, game.getState());
		assertEquals(FlappyGame.FLAP_VELOCITY, game.getChinVy());
		double before = game.getChinY();
		tick(3);
		assertTrue(game.getChinY() < before, "chin rises right after a flap");
	}

	@Test
	@DisplayName("gravity accelerates the fall but never past terminal velocity")
	void gravityCapsAtTerminalVelocity()
	{
		game.flap();
		for (int i = 0; i < 120 && game.getState() == FlappyGame.State.RUNNING; i++)
		{
			game.tick();
			assertTrue(game.getChinVy() <= FlappyGame.MAX_FALL_SPEED,
				"fall speed clamped at MAX_FALL_SPEED");
		}
	}

	@Test
	@DisplayName("the canopy bumps the chin back down instead of killing it")
	void ceilingClampsWithoutDeath()
	{
		// 60 ticks of flap-spam pins the chin to the canopy well before the
		// first tower (spawned at the right edge) can reach it.
		game.flap();
		for (int i = 0; i < 60; i++)
		{
			game.flap();
			game.tick();
			assertTrue(game.getChinY() >= FlappyGame.CHIN_HALF_H, "never above the canopy");
		}
		assertEquals(FlappyGame.State.RUNNING, game.getState(),
			"the canopy itself never kills");
	}

	@Test
	@DisplayName("never flapping again drops the chin into the marsh and ends the run")
	void groundEndsRun()
	{
		game.flap();
		runUntilDead();
		assertEquals(FlappyGame.DeathCause.GROUND, game.getDeathCause());
		assertEquals(FLOOR - FlappyGame.CHIN_HALF_H, game.getChinY(), 1e-9,
			"chin rests on the marsh");
		assertTrue(game.isSettled(), "a marsh death settles immediately");
	}

	@Test
	@DisplayName("trap towers spawn ahead and march left at the configured speed")
	void trapsMarchLeft()
	{
		game.flap();
		game.tick();
		assertEquals(1, game.getTraps().size(), "first tower spawns immediately");
		assertEquals(W, game.getTraps().get(0).getX(), 1e-9, "spawns at the right edge");
		game.tick();
		assertEquals(W - game.getSpeed(), game.getTraps().get(0).getX(), 1e-9);
	}

	@Test
	@DisplayName("towers that scroll fully off-screen are removed")
	void offscreenTowersDespawn()
	{
		game.flap();
		game.injectTrap(-FlappyGame.TRAP_WIDTH - 5, FLOOR / 2);
		game.tick();
		for (BoxTrap t : game.getTraps())
		{
			assertTrue(t.getX() + FlappyGame.TRAP_WIDTH >= 0, "off-screen tower was removed");
		}
	}

	@Test
	@DisplayName("every rolled gap fits between the canopy and the marsh margins")
	void gapsAlwaysFit()
	{
		int sampled = 0;
		game.flap();
		for (int i = 0; i < 5000; i++)
		{
			if (game.getState() == FlappyGame.State.DEAD)
			{
				// Restart through the public API and keep sampling.
				tick(FlappyGame.RESTART_COOLDOWN_TICKS);
				game.flap();
				game.flap();
			}
			game.tick();
			for (BoxTrap t : game.getTraps())
			{
				sampled++;
				int gapTop = t.getGapCenter() - game.getGapSize() / 2;
				int gapBottom = t.getGapCenter() + game.getGapSize() / 2;
				assertTrue(gapTop >= FlappyGame.GAP_MARGIN, "gap clears the canopy margin");
				assertTrue(gapBottom <= FLOOR - FlappyGame.GAP_MARGIN,
					"gap clears the marsh margin");
			}
		}
		assertTrue(sampled > 100, "sampled a meaningful number of towers");
	}

	@Test
	@DisplayName("dodging a tower scores exactly once, even as it keeps scrolling")
	void scoresOncePerTrap()
	{
		game.flap();
		int lastScore = 0;
		int scoredEvents = 0;
		for (int i = 0; i < 3000; i++)
		{
			if (game.getState() == FlappyGame.State.DEAD)
			{
				tick(FlappyGame.RESTART_COOLDOWN_TICKS);
				game.flap();
				game.flap();
				lastScore = 0;
			}
			hoverTick();
			if (game.getScore() != lastScore)
			{
				assertEquals(lastScore + 1, game.getScore(), "score only steps by one");
				lastScore = game.getScore();
				scoredEvents++;
			}
		}
		assertTrue(scoredEvents >= 2, "at least two towers dodged across the runs");
	}

	@Test
	@DisplayName("clipping a trap tower snaps shut on the run")
	void trapCollisionKills()
	{
		game.flap();
		// A tower dead ahead whose gap sits far above the chin's flight line.
		game.injectTrap(FlappyGame.CHIN_X - FlappyGame.CHIN_HALF_W + 1, FlappyGame.GAP_MARGIN + 55);
		game.tick();
		assertEquals(FlappyGame.State.DEAD, game.getState());
		assertEquals(FlappyGame.DeathCause.TRAP, game.getDeathCause());
		assertFalse(game.isSettled(), "a mid-air trap death still has to tumble down");
		tick(200);
		assertTrue(game.isSettled(), "the tumble ends on the marsh");
	}

	@Test
	@DisplayName("trap invincibility sails through towers but the marsh still kills")
	void trapInvincibility()
	{
		game.setTrapInvincible(true);
		game.flap();
		game.injectTrap(FlappyGame.CHIN_X - FlappyGame.CHIN_HALF_W + 1, FlappyGame.GAP_MARGIN + 55);
		game.tick();
		assertEquals(FlappyGame.State.RUNNING, game.getState(), "the trap cannot snap");

		runUntilDead();
		assertEquals(FlappyGame.DeathCause.GROUND, game.getDeathCause(),
			"only the marsh ends an invincible run");
	}

	@Test
	@DisplayName("flying clean through a gap does not trigger the trap")
	void gapIsSafe()
	{
		game.flap();
		// A tower at the chin with its gap centred on the current height.
		game.injectTrap(FlappyGame.CHIN_X, (int) game.getChinY());
		game.tick();
		assertEquals(FlappyGame.State.RUNNING, game.getState());
	}

	@Test
	@DisplayName("a flap right after death is swallowed by the misclick cooldown")
	void deathCooldownSwallowsFlaps()
	{
		game.flap();
		runUntilDead();

		game.flap();
		assertEquals(FlappyGame.State.DEAD, game.getState(), "immediate flap ignored");
		tick(FlappyGame.RESTART_COOLDOWN_TICKS - 1);
		assertFalse(game.canRestart());
		game.flap();
		assertEquals(FlappyGame.State.DEAD, game.getState(), "still inside cooldown");

		game.tick();
		assertTrue(game.canRestart());
		game.flap();
		assertEquals(FlappyGame.State.READY, game.getState(), "post-cooldown flap resets");
	}

	@Test
	@DisplayName("restarting clears the towers and the score for the next run")
	void restartClearsWorld()
	{
		game.flap();
		runUntilDead();
		int finalScore = game.getScore();
		tick(FlappyGame.RESTART_COOLDOWN_TICKS);
		game.flap();
		assertEquals(FlappyGame.State.READY, game.getState());
		assertTrue(game.getTraps().isEmpty(), "respawn presents a clean field");
		assertEquals(finalScore, game.getScore(), "final score still visible on READY");

		game.flap();
		assertEquals(0, game.getScore(), "new run starts from zero");
		assertTrue(game.getTraps().isEmpty(), "fresh run has no leftover towers");
	}

	@Test
	@DisplayName("a stretched-tall viewport keeps gaps inside the fixed centre band")
	void tallViewportKeepsGapBand()
	{
		final int tallH = 1000;
		game.setViewport(W, tallH);
		final int mid = (tallH - FlappyGame.GROUND_HEIGHT) / 2;
		final int bandTop = mid - FlappyGame.GAP_BAND_HEIGHT / 2;
		final int bandBottom = mid + FlappyGame.GAP_BAND_HEIGHT / 2;
		int sampled = 0;
		game.flap();
		for (int i = 0; i < 5000; i++)
		{
			if (game.getState() == FlappyGame.State.DEAD)
			{
				tick(FlappyGame.RESTART_COOLDOWN_TICKS);
				game.flap();
				game.flap();
			}
			game.tick();
			for (BoxTrap t : game.getTraps())
			{
				sampled++;
				assertTrue(t.getGapCenter() - game.getGapSize() / 2 >= bandTop,
					"gap stays below the band top");
				assertTrue(t.getGapCenter() + game.getGapSize() / 2 <= bandBottom,
					"gap stays above the band bottom");
			}
		}
		assertTrue(sampled > 100, "sampled a meaningful number of towers");
	}

	@Test
	@DisplayName("a viewport too small for the margins still yields a centred, playable gap")
	void tinyViewportStaysPlayable()
	{
		game.setViewport(120, 140);
		game.flap();
		game.tick();
		int centre = game.getTraps().get(0).getGapCenter();
		assertEquals((140 - FlappyGame.GROUND_HEIGHT) / 2, centre);
	}

	@Test
	@DisplayName("pole xp starts at +5 and climbs +10 per pole: 5 + 10x(n-1)")
	void poleXpEscalates()
	{
		assertEquals(0, FlappyGame.xpTenthsForPole(0));
		assertEquals(50, FlappyGame.xpTenthsForPole(1), "+5 for the first");
		assertEquals(150, FlappyGame.xpTenthsForPole(2), "+15 for the second");
		assertEquals(250, FlappyGame.xpTenthsForPole(3), "+25 for the third");
	}

	/**
	 * Tick once, flapping whenever the chin sinks below the midline - a
	 * crude altitude-hold. It makes no promise of dodging towers; callers
	 * that need long streams restart on death and keep going.
	 */
	private void hoverTick()
	{
		if (game.getState() == FlappyGame.State.RUNNING
			&& game.getChinVy() > 0 && game.getChinY() > FLOOR / 2.0)
		{
			game.flap();
		}
		game.tick();
	}
}
