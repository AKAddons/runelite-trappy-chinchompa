package com.trappychinchompa.game;

/**
 * One box-trap tower pair: stacked traps from the ceiling and the marsh with
 * a gap the chinchompa must thread. Pure data; geometry (gap size, tower
 * width) lives on {@link FlappyGame}.
 */
public class BoxTrap
{
	private double x;
	private final int gapCenter;
	private boolean scored;

	BoxTrap(double x, int gapCenter)
	{
		this.x = x;
		this.gapCenter = gapCenter;
	}

	public double getX()
	{
		return x;
	}

	public int getGapCenter()
	{
		return gapCenter;
	}

	public boolean isScored()
	{
		return scored;
	}

	void advance(double dx)
	{
		x -= dx;
	}

	void markScored()
	{
		scored = true;
	}
}
