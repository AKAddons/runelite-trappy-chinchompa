package com.trappychinchompa.duel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every duel the relay says I am in, plus my record. Settled duels
 * become news once; the relay does all the verifying.
 */
public final class DuelLedger
{
	private final String me;
	private final Map<String, DuelRecord> records = new LinkedHashMap<>();
	private final Set<String> announced = new HashSet<>();
	private final List<DuelRecord> fresh = new ArrayList<>();
	private Map<String, Object> history = new LinkedHashMap<>();

	public DuelLedger(String me)
	{
		this.me = me;
	}

	public String getMe()
	{
		return me;
	}

	/** The relay pushed a record (new or changed). */
	public void update(Map<String, Object> raw)
	{
		DuelRecord r = DuelRecord.from(raw, me);
		records.put(r.getId(), r);
		if (r.isDone() && announced.add(r.getId()))
		{
			fresh.add(r);
		}
	}

	/** Hello answered: the active duels and my record. Settled ones already shown stay until dismissed. */
	public void sync(List<Map<String, Object>> active, Map<String, Object> historyMap)
	{
		Map<String, DuelRecord> keep = new LinkedHashMap<>();
		for (DuelRecord r : records.values())
		{
			if (r.isDone())
			{
				keep.put(r.getId(), r);
			}
		}
		records.clear();
		records.putAll(keep);
		if (active != null)
		{
			for (Map<String, Object> raw : active)
			{
				update(raw);
			}
		}
		if (historyMap != null)
		{
			history = historyMap;
		}
	}

	public void setHistory(Map<String, Object> historyMap)
	{
		if (historyMap != null)
		{
			history = historyMap;
		}
	}

	public List<DuelRecord> duels()
	{
		return new ArrayList<>(records.values());
	}

	public DuelRecord get(String id)
	{
		return records.get(id);
	}

	public void dismiss(String id)
	{
		records.remove(id);
	}

	/** Settled since last asked, once each. */
	public List<DuelRecord> freshlySettled()
	{
		List<DuelRecord> out = new ArrayList<>(fresh);
		fresh.clear();
		return out;
	}

	// ---- my record ----

	public Map<String, Object> history()
	{
		return history;
	}

	public int getWins()
	{
		return count(history, "wins");
	}

	public int getLosses()
	{
		return count(history, "losses");
	}

	public static int count(Map<String, Object> m, String key)
	{
		Object v = m == null ? null : m.get(key);
		return v instanceof Number ? ((Number) v).intValue() : 0;
	}
}
