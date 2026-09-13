package com.trappychinchompa.duel;

import java.awt.Point;

/**
 * Where the fixed 242x480 duel world sits inside the real panel:
 * letterboxed, centred, scaled. Whole and half scales are preferred once
 * the panel is at least native size so sprites stay crisp.
 */
public final class DuelViewport
{
	private final double scale;
	private final int offsetX;
	private final int offsetY;

	private DuelViewport(double scale, int offsetX, int offsetY)
	{
		this.scale = scale;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
	}

	public static DuelViewport fit(int panelWidth, int panelHeight)
	{
		double s = Math.min(panelWidth / (double) DuelRun.WIDTH, panelHeight / (double) DuelRun.HEIGHT);
		if (s >= 1.0)
		{
			s = Math.floor(s * 4) / 4.0;
		}
		int ox = (int) Math.round((panelWidth - DuelRun.WIDTH * s) / 2);
		int oy = (int) Math.round((panelHeight - DuelRun.HEIGHT * s) / 2);
		return new DuelViewport(s, Math.max(0, ox), Math.max(0, oy));
	}

	public double getScale()
	{
		return scale;
	}

	public int getOffsetX()
	{
		return offsetX;
	}

	public int getOffsetY()
	{
		return offsetY;
	}

	/** A panel point in duel-world coordinates. */
	public Point toDuel(Point panelPoint)
	{
		return new Point(
			(int) Math.floor((panelPoint.x - offsetX) / scale),
			(int) Math.floor((panelPoint.y - offsetY) / scale));
	}
}
