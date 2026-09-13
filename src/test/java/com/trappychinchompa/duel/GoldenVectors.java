package com.trappychinchompa.duel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trappychinchompa.game.BoxTrap;
import com.trappychinchompa.game.FlappyGame;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes replay vectors for the relay's JavaScript port of the simulation:
 * seeds, rules, autopilot flap traces, and the scores and gap centres the
 * Java game produces. `./gradlew golden` regenerates build/golden.json.
 */
public final class GoldenVectors
{
	private GoldenVectors()
	{
	}

	public static void main(String[] args) throws IOException
	{
		List<Map<String, Object>> vectors = new ArrayList<>();
		long[] seeds = {1L, 4242L, -77L, 9007199254740993L, Long.MIN_VALUE + 5, 123456789012345L};
		String[] rules = {"e-bs1", "n-bs3", "h-bs5", "n-av1"};
		int k = 0;
		for (long seed : seeds)
		{
			for (String code : rules)
			{
				DuelRules r = DuelRules.parse(code);
				// Three pilots per seed: cautious, greedy, and one that never launches.
				for (int pilot = 0; pilot < 3; pilot++)
				{
					vectors.add(vector(seed, r, pilot, k++));
				}
			}
		}
		Gson gson = new GsonBuilder().create();
		try (FileWriter w = new FileWriter(args.length > 0 ? args[0] : "build/golden.json"))
		{
			w.write(gson.toJson(vectors));
		}
		System.out.println("golden vectors: " + vectors.size());
	}

	private static Map<String, Object> vector(long seed, DuelRules rules, int pilot, int index)
	{
		DuelRun run = new DuelRun(seed, rules);
		List<Integer> flaps = new ArrayList<>();
		List<Integer> gaps = new ArrayList<>();
		int spawned = 0;
		int t = 0;
		int offset = pilot == 0 ? 15 : 4;
		int limit = pilot == 2 ? 300 : 20_000;
		if (pilot != 2)
		{
			run.flap();
			flaps.add(0);
		}
		while (t < limit && !run.ended())
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
			if (pilot != 2 && game.getChinY() > target + offset && game.getChinVy() >= 0 && (index + t) % 3 != 0)
			{
				run.flap();
				flaps.add(t);
			}
			run.tick();
			t++;
			int total = game.getTraps().size() + game.getScore();
			if (total > spawned)
			{
				gaps.add(game.getTraps().get(game.getTraps().size() - 1).getGapCenter());
				spawned = total;
			}
		}
		Map<String, Object> v = new HashMap<>();
		v.put("seed", Long.toString(seed));
		v.put("rules", rules.canonical());
		v.put("flaps", flaps);
		v.put("ticks", run.totalTicks());
		v.put("score", run.score());
		v.put("ended", run.ended());
		v.put("gaps", gaps.subList(0, Math.min(gaps.size(), 12)));
		v.put("replay", new TrappyAdapter().replay(seed, rules, flaps, run.totalTicks()));
		return v;
	}
}
