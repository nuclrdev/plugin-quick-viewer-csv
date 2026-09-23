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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.nuclr.platform.plugin.NuclrResource;

/**
 * Draws a still thumbnail of delimited text: the top rows as a small
 * spreadsheet, the header row shaded when the file has one and numbers aligned
 * right as the grid aligns them.
 *
 * <p>Stateless and safe to call from any thread. At sizes too small to read,
 * cells hold grey bars as long as their text instead of glyphs.
 */
final class CsvThumbnail {

	/** Read from the head of the file: plenty for the rows a thumbnail has room for. */
	static final int SAMPLE_BYTES = 32 * 1024;

	private static final int MAX_ROWS = 60;
	private static final int MAX_COLUMNS = 16;
	/** Widest a column is sized for, in characters; longer cells are cut. */
	private static final int MAX_COLUMN_CHARS = 18;
	private static final int MIN_COLUMN_CHARS = 3;
	/** Characters across the sheet: sets the font size relative to the thumbnail. */
	private static final int COLUMNS_OF_TEXT = 48;
	/** Width over height of the sheet. */
	private static final double SHEET_ASPECT = 4.0 / 3.0;
	private static final float MIN_READABLE_FONT = 5f;

	private static final Color SHEET = Color.WHITE;
	private static final Color GRID = new Color(0xD5D9DF);
	private static final Color HEADER = new Color(0xE6ECF3);
	private static final Color STRIPE = new Color(0xF6F7F9);
	private static final Color INK = new Color(0x30343A);
	private static final Color HEADER_INK = new Color(0x1C3D63);
	private static final Color BAR = new Color(0x9AA1AB);

	private CsvThumbnail() {
	}

	/**
	 * @return the sheet, or {@code null} for a file without rows, binary content,
	 *         or when {@code cancelled} was set
	 */
	static BufferedImage render(NuclrResource resource, int maxWidth, int maxHeight, AtomicBoolean cancelled)
			throws Exception {

		String sample = CsvLoader.head(resource, SAMPLE_BYTES);
		if (cancelled != null && cancelled.get()) {
			return null;
		}

		CsvDialect dialect = CsvDialect.sniff(sample, CsvFileSupport.name(resource));
		List<List<String>> rows = new ArrayList<>();
		try (CsvParser parser = new CsvParser(new StringReader(sample), dialect.delimiter(), dialect.quote())) {
			List<String> record;
			while (rows.size() < MAX_ROWS && (record = parser.next()) != null) {
				if (record.size() == 1 && record.getFirst().isBlank()) {
					continue;
				}
				rows.add(record.size() > MAX_COLUMNS ? record.subList(0, MAX_COLUMNS) : record);
			}
		}
		if (rows.isEmpty()) {
			return null;
		}
		return draw(rows, CsvDialect.looksLikeHeader(rows.getFirst()), maxWidth, maxHeight, cancelled);
	}

	static BufferedImage draw(List<List<String>> rows, boolean header, int maxWidth, int maxHeight,
			AtomicBoolean cancelled) {

		if (rows.isEmpty() || maxWidth <= 0 || maxHeight <= 0) {
			return null;
		}

		int width = maxWidth;
		int height = (int) Math.round(width / SHEET_ASPECT);
		if (height > maxHeight) {
			height = maxHeight;
			width = Math.max(1, (int) Math.round(height * SHEET_ASPECT));
		}

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

			g.setColor(SHEET);
			g.fillRect(0, 0, width, height);

			float fontSize = width / (COLUMNS_OF_TEXT * 0.6f);
			boolean readable = fontSize >= MIN_READABLE_FONT;
			Font plain = new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(fontSize);
			Font bold = plain.deriveFont(Font.BOLD);
			FontMetrics fm = g.getFontMetrics(plain);
			float charWidth = Math.max(1f, fm.charWidth('0'));
			float rowHeight = fontSize * 1.6f;
			float padding = charWidth * 0.6f;

			float[] columnX = columnEdges(rows, charWidth, padding, width);
			int columns = columnX.length - 1;

			float y = 0;
			for (int r = 0; r < rows.size() && y < height; r++) {
				if (cancelled != null && cancelled.get()) {
					return null;
				}
				boolean isHeader = header && r == 0;
				if (isHeader || r % 2 == 0) {
					g.setColor(isHeader ? HEADER : STRIPE);
					g.fill(new Rectangle2D.Float(0, y, width, rowHeight));
				}
				List<String> row = rows.get(r);
				g.setFont(isHeader ? bold : plain);
				FontMetrics rowMetrics = g.getFontMetrics();
				for (int c = 0; c < columns && c < row.size(); c++) {
					String value = oneLine(row.get(c));
					if (value.isEmpty()) {
						continue;
					}
					float cellWidth = columnX[c + 1] - columnX[c] - 2 * padding;
					boolean numeric = !isHeader && !Double.isNaN(Numbers.parse(value));
					drawCell(g, rowMetrics, value, columnX[c] + padding, y, cellWidth, rowHeight, numeric, readable,
							isHeader ? HEADER_INK : INK);
				}
				y += rowHeight;
			}

			// Grid over the fills: row rules, then column rules down to the last row drawn.
			g.setColor(GRID);
			for (float line = rowHeight; line < Math.min(y, height); line += rowHeight) {
				g.draw(new Line2D.Float(0, line, width, line));
			}
			for (int c = 1; c < columns; c++) {
				g.draw(new Line2D.Float(columnX[c], 0, columnX[c], Math.min(y, height)));
			}
			g.draw(new Rectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f));
			return image;
		} finally {
			g.dispose();
		}
	}

	/** Column edges, sized to each column's longest cell within bounds and stopping at the sheet's edge. */
	private static float[] columnEdges(List<List<String>> rows, float charWidth, float padding, int width) {

		int columns = 0;
		for (List<String> row : rows) {
			columns = Math.max(columns, row.size());
		}

		List<Float> edges = new ArrayList<>();
		edges.add(0f);
		float x = 0;
		for (int c = 0; c < columns && x < width; c++) {
			int chars = MIN_COLUMN_CHARS;
			for (List<String> row : rows) {
				if (c < row.size()) {
					chars = Math.max(chars, Math.min(MAX_COLUMN_CHARS, oneLine(row.get(c)).length()));
				}
			}
			x += chars * charWidth + 2 * padding;
			edges.add(Math.min(x, width));
		}

		float[] result = new float[edges.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = edges.get(i);
		}
		return result;
	}

	private static void drawCell(Graphics2D g, FontMetrics fm, String value, float x, float y, float cellWidth,
			float rowHeight, boolean numeric, boolean readable, Color ink) {

		if (cellWidth <= 0) {
			return;
		}
		String text = fit(value, fm, cellWidth);
		float textWidth = fm.stringWidth(text);
		float left = numeric ? x + cellWidth - textWidth : x;

		if (readable) {
			g.setColor(ink);
			g.drawString(text, left, y + (rowHeight - fm.getHeight()) / 2 + fm.getAscent());
			return;
		}
		float barHeight = Math.max(1f, rowHeight * 0.3f);
		g.setColor(BAR);
		g.fill(new Rectangle2D.Float(left, y + (rowHeight - barHeight) / 2, textWidth, barHeight));
	}

	private static String fit(String value, FontMetrics fm, float width) {
		int end = Math.min(value.length(), MAX_COLUMN_CHARS * 2);
		while (end > 0 && fm.stringWidth(value.substring(0, end)) > width) {
			end--;
		}
		return value.substring(0, end);
	}

	private static String oneLine(String value) {
		return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').strip();
	}
}
