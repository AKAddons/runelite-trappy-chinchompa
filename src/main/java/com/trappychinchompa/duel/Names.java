package com.trappychinchompa.duel;

import java.util.Locale;

/** Player names as the game shows them, made comparable. */
public final class Names
{
	private Names()
	{
	}

	public static String normalise(String name)
	{
		return name == null ? "" : name.replace('\u00A0', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
	}

	public static boolean same(String a, String b)
	{
		return !normalise(a).isEmpty() && normalise(a).equals(normalise(b));
	}
}
