package com.trappychinchompa.duel;

import java.util.Collections;
import java.util.List;

/** How a duel ended, as seen from one player's side. */
public final class DuelOutcome
{
	public enum Reason
	{
		WIN, FORFEIT, CHEAT, DISCONNECT, NO_CONTEST, DISPUTED, DRAW
	}

	public static final class GameLine
	{
		private final int gameIndex;
		private final int mine;
		private final int theirs;
		private final boolean suddenDeath;

		public GameLine(int gameIndex, int mine, int theirs, boolean suddenDeath)
		{
			this.gameIndex = gameIndex;
			this.mine = mine;
			this.theirs = theirs;
			this.suddenDeath = suddenDeath;
		}

		public int getGameIndex()
		{
			return gameIndex;
		}

		public int getMine()
		{
			return mine;
		}

		public int getTheirs()
		{
			return theirs;
		}

		public boolean isSuddenDeath()
		{
			return suddenDeath;
		}
	}

	private final String winner;
	private final Reason reason;
	private final List<GameLine> games;
	private final String scoreLine;

	public DuelOutcome(String winner, Reason reason, List<GameLine> games, String scoreLine)
	{
		this.winner = winner;
		this.reason = reason;
		this.games = Collections.unmodifiableList(games);
		this.scoreLine = scoreLine;
	}

	/** The winner's name, or null when nobody won (no contest, disputed). */
	public String getWinner()
	{
		return winner;
	}

	public Reason getReason()
	{
		return reason;
	}

	public List<GameLine> getGames()
	{
		return games;
	}

	public String getScoreLine()
	{
		return scoreLine;
	}
}
