package com.trappychinchompa.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

/** Rounded surfaces and buttons for the dock, after the Goal Planner dock's language. */
final class Rounded
{
	static final int RADIUS = 8;

	private Rounded()
	{
	}

	static void fill(Graphics2D g2, int x, int y, int w, int h, int radius, Color color)
	{
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(color);
		g2.fillRoundRect(x, y, w, h, radius * 2, radius * 2);
	}

	/** A flat rounded button with a hover shade; colours set by the caller. */
	static final class Button extends JButton
	{
		private Color rest;
		private Color hover;

		Button(String text, Color background, Color hover, Color foreground)
		{
			super(text);
			this.rest = background;
			this.hover = hover;
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setBackground(background);
			setForeground(foreground);
			setBorder(BorderFactory.createEmptyBorder(4, 9, 4, 9));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					setBackground(Button.this.hover);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					setBackground(rest);
				}
			});
		}

		void recolour(Color background, Color hover, Color foreground)
		{
			this.rest = background;
			this.hover = hover;
			setBackground(background);
			setForeground(foreground);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				fill(g2, 0, 0, getWidth(), getHeight(), RADIUS, getBackground());
			}
			finally
			{
				g2.dispose();
			}
			super.paintComponent(g);
		}
	}

	/** A panel that paints a rounded background from its background colour. */
	static final class Panel extends JPanel
	{
		Panel(java.awt.LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				fill(g2, 0, 0, getWidth(), getHeight(), RADIUS, getBackground());
			}
			finally
			{
				g2.dispose();
			}
			super.paintComponent(g);
		}
	}
}
