package com.trappychinchompa.duel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The Java replay, kept for the golden vectors the relay's simulation port is checked against. */
public final class TrappyAdapter
{
	public int replay(long seed, DuelRules rules, List<Integer> flapTicks, int totalTicks)
	{
		List<Integer> flaps = new ArrayList<>(flapTicks);
		Collections.sort(flaps);
		DuelRun run = new DuelRun(seed, rules);
		int total = Math.min(totalTicks, DuelRun.MAX_TICKS);
		int next = 0;
		for (int t = 0; t < total && !run.ended(); t++)
		{
			while (next < flaps.size() && flaps.get(next) <= t)
			{
				if (flaps.get(next) == t)
				{
					run.flap();
				}
				next++;
			}
			run.tick();
		}
		return run.score();
	}
}
