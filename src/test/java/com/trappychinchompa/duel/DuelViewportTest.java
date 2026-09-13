package com.trappychinchompa.duel;

import java.awt.Point;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DuelViewportTest
{
	@Test
	@DisplayName("a 300x600 panel shows the 242x480 duel at scale 1 with 29px side bars")
	void nearNativeSizeSnapsToOne()
	{
		DuelViewport vp = DuelViewport.fit(300, 600);
		assertEquals(1.0, vp.getScale(), 1e-9);
		assertEquals(29, vp.getOffsetX());
		assertEquals(60, vp.getOffsetY());
	}

	@Test
	@DisplayName("a 484x960 panel doubles the duel exactly")
	void doubleSize()
	{
		DuelViewport vp = DuelViewport.fit(484, 960);
		assertEquals(2.0, vp.getScale(), 1e-9);
		assertEquals(0, vp.getOffsetX());
		assertEquals(0, vp.getOffsetY());
	}

	@Test
	@DisplayName("a panel narrower than the duel scales down smoothly instead of clipping")
	void smallPanelsScaleDown()
	{
		DuelViewport vp = DuelViewport.fit(200, 600);
		assertEquals(200 / 242.0, vp.getScale(), 1e-9);
		assertEquals(0, vp.getOffsetX());
	}

	@Test
	@DisplayName("clicks map back into duel coordinates exactly")
	void hitTestingMapsBack()
	{
		DuelViewport vp = DuelViewport.fit(484, 960);
		Point p = vp.toDuel(new Point(100, 250));
		assertEquals(50, p.x);
		assertEquals(125, p.y);
		DuelViewport bars = DuelViewport.fit(300, 600);
		Point q = bars.toDuel(new Point(29, 60));
		assertEquals(0, q.x);
		assertEquals(0, q.y);
	}
}
