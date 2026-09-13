package com.trappychinchompa.duel;

import com.trappychinchompa.Difficulty;
import com.trappychinchompa.game.FlappyGame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One duel game: a FlappyGame on a fixed 242x480 virtual viewport, seeded,
 * recording every flap as the tick index it fired on. The same trace
 * replayed against the same seed reproduces the same score anywhere.
 */
public final class DuelRun
{
	public static final int WIDTH = 242;
	public static final int HEIGHT = 480;
	public static final int FPS = 60;
	/** Ten minutes of ticks; hitting it ends the run where it stands. */
	public static final int MAX_TICKS = 600 * FPS;

	private final FlappyGame game;
	private final List<Integer> flapTicks = new ArrayList<>();
	private int ticks;
	private boolean capped;

	public DuelRun(long seed, DuelRules rules)
	{
		game = new FlappyGame(new Random(seed));
		game.setViewport(WIDTH, HEIGHT);
		Difficulty d = rules.getDifficulty();
		game.setDifficulty(d.getGapSize(), d.getSpeed(), d.getMaxGapStep());
	}

	public FlappyGame game()
	{
		return game;
	}

	/** A flap at the current tick; the first one launches the run. */
	public void flap()
	{
		if (ended())
		{
			return;
		}
		flapTicks.add(ticks);
		game.flap();
	}

	/** Advance one frame. After the run ends the game still animates but ticks no longer count. */
	public void tick()
	{
		if (ended())
		{
			game.tick();
			return;
		}
		game.tick();
		ticks++;
		if (ticks >= MAX_TICKS)
		{
			capped = true;
		}
	}

	public boolean ended()
	{
		return capped || game.getState() == FlappyGame.State.DEAD;
	}

	public int score()
	{
		return game.getScore();
	}

	public List<Integer> flapTicks()
	{
		return Collections.unmodifiableList(flapTicks);
	}

	public int totalTicks()
	{
		return ticks;
	}
}
