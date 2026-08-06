package com.trappychinchompa.ui;

import com.trappychinchompa.game.BackgroundTheme;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dev tool: renders every zone backdrop to one contact sheet so the art can
 * be reviewed without levelling to 99 in-game. No client needed.
 *
 *   ./gradlew backdrops        -> build/backdrops.png
 */
public class BackdropGallery
{
	private static final int TILE_W = 225;
	private static final int TILE_H = 420;
	private static final int LABEL_H = 26;
	private static final int COLS = 4;

	public static void main(String[] args) throws Exception
	{
		final BackgroundTheme[] themes = BackgroundTheme.values();
		final int rows = (themes.length + COLS - 1) / COLS;
		final BufferedImage sheet = new BufferedImage(
			COLS * TILE_W, rows * (TILE_H + LABEL_H), BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = sheet.createGraphics();
		g.setColor(new Color(0x28, 0x28, 0x28));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

		for (int i = 0; i < themes.length; i++)
		{
			final int ox = (i % COLS) * TILE_W;
			final int oy = (i / COLS) * (TILE_H + LABEL_H);
			final Graphics2D tile = (Graphics2D) g.create(ox, oy, TILE_W, TILE_H);
			GameCanvas.renderBackdrop(tile, TILE_W, TILE_H, themes[i]);
			tile.dispose();

			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
			g.setColor(Color.WHITE);
			final String tag = themes[i].isAchievementGated()
				? "(achievement)" : "(lvl " + themes[i].getUnlockLevel() + ")";
			g.drawString(themes[i].name() + "  " + tag, ox + 8, oy + TILE_H + 18);
		}
		g.dispose();

		final File out = new File(args.length > 0 ? args[0] : "build/backdrops.png");
		out.getParentFile().mkdirs();
		ImageIO.write(sheet, "png", out);
		System.out.println("wrote " + out.getAbsolutePath());
	}
}
