/*

	Copyright 2026 Sergio, Nuclr (https://nuclr.dev)

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

*/
package dev.nuclr.plugin.core.quick.viewer.csv;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.UIManager;

import dev.nuclr.platform.NuclrThemeScheme;

/**
 * Colours, fonts and number formatting shared by the viewer's parts.
 *
 * <p>Colours come from the active look-and-feel, with the theme scheme's
 * explicit overrides on top: the host installs the selected theme as the L&amp;F
 * and a built-in theme carries no overrides at all, so reading from the scheme
 * alone would leave the grid painted in the previous theme's colours.
 */
final class ViewerUi {

	/** Amber that stays legible on both light and dark backgrounds; marks search hits and truncation. */
	static final Color ACCENT = new Color(0xE0, 0xA8, 0x3C);

	/** How far a selected row must stand out from an unselected one to read as selected at a glance. */
	private static final double MIN_SELECTION_CONTRAST = 3.0;

	/** How readable text must be on whatever it is painted over. */
	private static final double MIN_TEXT_CONTRAST = 4.5;

	private ViewerUi() {
	}

	/** The colours the viewer paints with, resolved once per theme change. */
	record Palette(
			Color background,
			Color foreground,
			Color muted,
			Color grid,
			Color stripe,
			Color header,
			Color headerForeground,
			Color selection,
			Color selectionForeground,
			Color accent) {
	}

	static Palette palette(NuclrThemeScheme theme) {

		Color background = color(theme, "Table.background", Color.WHITE);
		Color foreground = color(theme, "Table.foreground", Color.BLACK);
		Color header = color(theme, "TableHeader.background", background);

		// The theme's own selection colour, nudged only if it is so close to the row
		// background that a selected row would not look selected. Themes are free to
		// be subtle; a viewer whose whole export flow is "tick these rows" is not.
		Color selection = ensureSelectionContrast(background,
				color(theme, "Table.selectionBackground", new Color(0x30, 0x60, 0xA0)));

		return new Palette(
				background,
				foreground,
				blend(foreground, background, 0.45f),
				color(theme, "Table.gridColor", blend(background, foreground, 0.15f)),
				stripe(theme, background, foreground),
				header,
				color(theme, "TableHeader.foreground", foreground),
				selection,
				readable(selection, color(theme, "Table.selectionForeground", Color.WHITE), foreground),
				ACCENT);
	}

	/**
	 * A colour from the theme scheme, falling back to the look-and-feel and then
	 * to the caller's default.
	 */
	static Color color(NuclrThemeScheme theme, String key, Color fallback) {

		Color color = colorOrNull(theme, key);
		return color != null ? color : opaque(fallback);
	}

	/**
	 * A colour the theme or the look-and-feel actually defines, or {@code null} -
	 * which is how the palette tells "the theme chose this" apart from "nobody
	 * said", for keys like the alternating row colour that are often unset.
	 */
	static Color colorOrNull(NuclrThemeScheme theme, String key) {

		Color fromScheme = theme != null ? theme.color(key, null) : null;
		if (fromScheme != null) {
			return opaque(fromScheme);
		}

		Color fromLaf = UIManager.getColor(key);
		return fromLaf != null ? opaque(fromLaf) : null;
	}

	/** The banding colour: the theme's own where it has one, otherwise a hint of the foreground. */
	private static Color stripe(NuclrThemeScheme theme, Color background, Color foreground) {

		Color alternate = colorOrNull(theme, "Table.alternateRowColor");
		return alternate != null ? alternate : blend(background, foreground, isDark(background) ? 0.05f : 0.035f);
	}

	/**
	 * Keeps the theme's selection colour, adjusting it only when it is too close
	 * to the row background to be seen. The adjustment walks the colour towards
	 * black or white - whichever the background is further from - so it stays the
	 * theme's colour rather than becoming some other theme's blue.
	 */
	static Color ensureSelectionContrast(Color background, Color selection) {

		if (contrastRatio(background, selection) >= MIN_SELECTION_CONTRAST) {
			return selection;
		}

		Color target = contrastRatio(background, Color.BLACK) >= contrastRatio(background, Color.WHITE)
				? Color.BLACK
				: Color.WHITE;

		float low = 0f;
		float high = 1f;
		for (int i = 0; i < 12; i++) {
			float weight = (low + high) / 2f;
			if (contrastRatio(background, blend(selection, target, weight)) >= MIN_SELECTION_CONTRAST) {
				high = weight;
			} else {
				low = weight;
			}
		}

		return blend(selection, target, high);
	}

	/** Strips a UIResource wrapper and any alpha: those are swapped out under us on a L&F change. */
	private static Color opaque(Color color) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue());
	}

	static Font defaultFont() {

		Font font = UIManager.getFont("defaultFont");
		return font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	}

	/** A slightly smaller font for the chrome, so the toolbar and status line do not crowd the grid. */
	static Font smallFont() {

		Font font = defaultFont();
		return font.deriveFont(Font.PLAIN, Math.max(10f, font.getSize2D() - 1f));
	}

	/** Shrinks a control and stops it demanding width it does not need. */
	static <T extends JComponent> T compact(T component) {

		component.setFont(smallFont());
		component.setMinimumSize(new Dimension(0, component.getMinimumSize().height));
		return component;
	}

	static boolean isDark(Color color) {
		return luminance(color) < 0.5;
	}

	static Color blend(Color base, Color overlay, float overlayWeight) {

		float weight = Math.max(0f, Math.min(1f, overlayWeight));
		float baseWeight = 1f - weight;

		return new Color(
				Math.round(base.getRed() * baseWeight + overlay.getRed() * weight),
				Math.round(base.getGreen() * baseWeight + overlay.getGreen() * weight),
				Math.round(base.getBlue() * baseWeight + overlay.getBlue() * weight));
	}

	/** The first of two foregrounds that is legible on {@code background}, or black/white when neither is. */
	static Color readable(Color background, Color preferred, Color fallback) {

		if (contrastRatio(background, preferred) >= MIN_TEXT_CONTRAST) {
			return preferred;
		}
		if (contrastRatio(background, fallback) >= MIN_TEXT_CONTRAST) {
			return fallback;
		}

		return contrastRatio(background, Color.BLACK) >= contrastRatio(background, Color.WHITE)
				? Color.BLACK
				: Color.WHITE;
	}

	static double contrastRatio(Color first, Color second) {

		double lighter = Math.max(luminance(first), luminance(second));
		double darker = Math.min(luminance(first), luminance(second));

		return (lighter + 0.05) / (darker + 0.05);
	}

	/** Thousands-separated, because a row count is read, not calculated. */
	static String count(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	/** Formats a byte count the way a file manager would: 1.4 KB, 42.3 MB. */
	static String humanSize(long bytes) {

		if (bytes < 1024) {
			return bytes + " B";
		}

		String[] units = { "KB", "MB", "GB", "TB" };
		double value = bytes;
		int unit = -1;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}

		return String.format(Locale.ROOT, value < 10 ? "%.1f %s" : "%.0f %s", value, units[unit]);
	}

	static String ellipsizeEnd(String text, int max) {

		if (text == null) {
			return "";
		}

		return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
	}

	private static double luminance(Color color) {
		return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
	}

	private static double channel(int value) {

		double normalized = value / 255.0;
		return normalized <= 0.04045 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
	}
}
