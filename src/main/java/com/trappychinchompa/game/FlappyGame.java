package com.trappychinchompa.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The whole game, headless: a fixed-timestep state machine with no Swing or
 * RuneLite imports so every rule is unit-testable. The canvas calls
 * {@link #tick()} once per frame and renders whatever the getters expose.
 *
 * States: READY (chinchompa bobbing, waiting for the first flap) -> RUNNING
 * (physics + trap towers + scoring) -> DEAD (world frozen while the poor
 * chin detonates; a flap after a short cooldown returns to READY).
 */
public class FlappyGame
{
	public enum State
	{
		READY, RUNNING, DEAD
	}

	public enum DeathCause
	{
		TRAP, GROUND
	}

	/** Chinchompa centre is pinned at this x; the world scrolls past it. */
	public static final int CHIN_X = 60;
	/** Collision half-extents - deliberately smaller than the drawn sprite. */
	public static final int CHIN_HALF_W = 13;
	public static final int CHIN_HALF_H = 10;
	public static final double GRAVITY = 0.35;
	public static final double FLAP_VELOCITY = -6.2;
	public static final double MAX_FALL_SPEED = 9.0;
	/** Height of the marsh strip at the bottom; landing on it ends the run. */
	public static final int GROUND_HEIGHT = 48;
	public static final int TRAP_WIDTH = 44;
	/** Horizontal distance between successive trap-tower left edges. */
	public static final int TRAP_SPACING = 150;
	/** Minimum distance from a gap edge to the ceiling / marsh. */
	public static final int GAP_MARGIN = 28;
	/**
	 * Gaps roll inside a band of this height centred in the play area, so a
	 * taller panel widens the scenery, not the vertical spread between gaps.
	 * 376 reproduces the spread of the ~480px design-height panel exactly
	 * (432 play height minus both margins); shorter panels are unaffected.
	 */
	public static final int GAP_BAND_HEIGHT = 376;
	/** Ticks after death before a flap may reset to READY (misclick guard). */
	public static final int RESTART_COOLDOWN_TICKS = 24;

	private final Random rng;
	private final List<BoxTrap> traps = new ArrayList<>();

	private State state = State.READY;
	private DeathCause deathCause;
	private int width = 242;
	private int height = 480;
	private int gapSize = 110;
	private double speed = 1.9;
	/** Max vertical travel of the gap centre between consecutive traps. */
	private int maxGapStep = GAP_BAND_HEIGHT;
	/** Previous trap's gap centre, or -1 when the next roll is free. */
	private int lastGapCenter = -1;

	private int readyTicks;
	private double chinY = playFloor() / 2.0;
	private double chinVy;
	private int score;
	private int deadTicks;
	/** Dev sandbox: traps stop killing; only the marsh ends the run. */
	private boolean trapInvincible;

	public FlappyGame(Random rng)
	{
		this.rng = rng;
	}

	public void setViewport(int width, int height)
	{
		if (width <= 0 || height <= 0)
		{
			return;
		}
		this.width = width;
		this.height = height;
		if (state == State.READY)
		{
			chinY = playFloor() / 2.0;
		}
	}

	public void setDifficulty(int gapSize, double speed, int maxGapStep)
	{
		this.gapSize = gapSize;
		this.speed = speed;
		this.maxGapStep = maxGapStep;
	}

	public void setTrapInvincible(boolean trapInvincible)
	{
		this.trapInvincible = trapInvincible;
	}

	public boolean isTrapInvincible()
	{
		return trapInvincible;
	}

	/** Flap - and drive the READY -> RUNNING -> DEAD -> READY cycle. */
	public void flap()
	{
		switch (state)
		{
			case READY:
				startRun();
				break;
			case RUNNING:
				chinVy = FLAP_VELOCITY;
				break;
			case DEAD:
				if (canRestart())
				{
					// Respawn to a clean field: no towers, chin bobbing
					// mid-air, exactly like the first launch.
					state = State.READY;
					readyTicks = 0;
					chinY = playFloor() / 2.0;
					chinVy = 0;
					traps.clear();
				}
				break;
		}
	}

	/** Advance exactly one frame. */
	public void tick()
	{
		switch (state)
		{
			case READY:
				readyTicks++;
				// StrictMath: the bob height seeds the launch height, and duels replay it on other machines.
				chinY = playFloor() / 2.0 + StrictMath.sin(readyTicks * 0.09) * 5.0;
				break;
			case RUNNING:
				stepPhysics();
				stepTraps();
				checkCollisions();
				break;
			case DEAD:
				deadTicks++;
				// What's left of the chin drifts down onto the marsh.
				if (chinY + CHIN_HALF_H < playFloor())
				{
					chinVy = Math.min(chinVy + GRAVITY, MAX_FALL_SPEED);
					chinY = Math.min(chinY + chinVy, playFloor() - CHIN_HALF_H);
				}
				break;
		}
	}

	private void startRun()
	{
		state = State.RUNNING;
		deathCause = null;
		traps.clear();
		lastGapCenter = -1;
		score = 0;
		deadTicks = 0;
		chinVy = FLAP_VELOCITY;
	}

	private void stepPhysics()
	{
		chinVy = Math.min(chinVy + GRAVITY, MAX_FALL_SPEED);
		chinY += chinVy;
		// The canopy bumps, it doesn't kill.
		if (chinY < CHIN_HALF_H)
		{
			chinY = CHIN_HALF_H;
			chinVy = 0;
		}
	}

	private void stepTraps()
	{
		for (BoxTrap trap : traps)
		{
			trap.advance(speed);
		}
		traps.removeIf(t -> t.getX() + TRAP_WIDTH < 0);

		BoxTrap last = traps.isEmpty() ? null : traps.get(traps.size() - 1);
		if (last == null || last.getX() <= width - TRAP_SPACING)
		{
			traps.add(new BoxTrap(width, rollGapCenter()));
		}

		for (BoxTrap trap : traps)
		{
			if (!trap.isScored() && trap.getX() + TRAP_WIDTH < CHIN_X)
			{
				trap.markScored();
				score++;
			}
		}
	}

	private int rollGapCenter()
	{
		final int half = gapSize / 2;
		final int mid = playFloor() / 2;
		int lo = GAP_MARGIN + half;
		int hi = playFloor() - GAP_MARGIN - half;
		// Clamp to the fixed-height band around the middle: consistent gap
		// spacing no matter how tall the panel is stretched.
		lo = Math.max(lo, mid - GAP_BAND_HEIGHT / 2 + half);
		hi = Math.min(hi, mid + GAP_BAND_HEIGHT / 2 - half);
		if (hi <= lo)
		{
			// Viewport too small for margins - centre the gap and carry on.
			return mid;
		}
		if (lastGapCenter >= 0)
		{
			// Each gap stays reachable: the centre may travel at most
			// maxGapStep from the previous trap's.
			final int anchor = Math.max(lo, Math.min(hi, lastGapCenter));
			lo = Math.max(lo, anchor - maxGapStep);
			hi = Math.min(hi, anchor + maxGapStep);
		}
		lastGapCenter = lo + rng.nextInt(hi - lo + 1);
		return lastGapCenter;
	}

	private void checkCollisions()
	{
		if (chinY + CHIN_HALF_H >= playFloor())
		{
			chinY = playFloor() - CHIN_HALF_H;
			die(DeathCause.GROUND);
			return;
		}
		if (trapInvincible)
		{
			return;
		}
		for (BoxTrap trap : traps)
		{
			boolean xOverlap = trap.getX() < CHIN_X + CHIN_HALF_W
				&& trap.getX() + TRAP_WIDTH > CHIN_X - CHIN_HALF_W;
			if (!xOverlap)
			{
				continue;
			}
			int gapTop = trap.getGapCenter() - gapSize / 2;
			int gapBottom = trap.getGapCenter() + gapSize / 2;
			if (chinY - CHIN_HALF_H < gapTop || chinY + CHIN_HALF_H > gapBottom)
			{
				die(DeathCause.TRAP);
				return;
			}
		}
	}

	private void die(DeathCause cause)
	{
		state = State.DEAD;
		deathCause = cause;
		deadTicks = 0;
		chinVy = 0;
	}

	private int playFloor()
	{
		return height - GROUND_HEIGHT;
	}

	public State getState()
	{
		return state;
	}

	/** Why the last run ended; null until the first death. */
	public DeathCause getDeathCause()
	{
		return deathCause;
	}

	public int getScore()
	{
		return score;
	}

	public double getChinY()
	{
		return chinY;
	}

	public double getChinVy()
	{
		return chinVy;
	}

	public int getGapSize()
	{
		return gapSize;
	}

	public double getSpeed()
	{
		return speed;
	}

	public int getTicksSinceDeath()
	{
		return deadTicks;
	}

	public boolean canRestart()
	{
		return state == State.DEAD && deadTicks >= RESTART_COOLDOWN_TICKS;
	}

	/** Whether the dead chin has finished tumbling onto the marsh. */
	public boolean isSettled()
	{
		return state == State.DEAD && chinY + CHIN_HALF_H >= playFloor();
	}

	public List<BoxTrap> getTraps()
	{
		return Collections.unmodifiableList(traps);
	}

	/** Test seam: place a trap tower exactly where a scenario needs it. */
	void injectTrap(double x, int gapCenter)
	{
		traps.add(new BoxTrap(x, gapCenter));
	}

	/**
	 * Anti-hunter xp for the nth pole of a run (1-based), in tenths:
	 * +5 for the first, then +10 more each - +5, +15, +25, ...
	 * Difficulty scales and totals these per run.
	 */
	public static long xpTenthsForPole(int pole)
	{
		if (pole < 1)
		{
			return 0;
		}
		return 100L * pole - 50L;
	}
}
