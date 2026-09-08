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
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;

import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;

/**
 * Paints one cell: numbers to the right, alternating row tints, and the
 * amber wash that shows where the find bar's matches are.
 */
final class CsvCellRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	/** How far a matching cell is tinted towards the accent, and a bit more for the current one. */
	private static final float MATCH_TINT = 0.28f;
	private static final float CURRENT_MATCH_TINT = 0.62f;

	/** Values longer than this get a tooltip, since the column will not show them all. */
	private static final int TOOLTIP_THRESHOLD = 24;

	private final CsvTableModel model;
	private final Border padding = BorderFactory.createEmptyBorder(0, 6, 0, 6);

	private Palette palette = ViewerUi.palette(null);
	private CsvSearch.Hits hits = CsvSearch.Hits.NONE;
	private int currentViewRow = -1;
	private int currentColumn = -1;

	CsvCellRenderer(CsvTableModel model) {
		this.model = model;
		setOpaque(true);
	}

	void applyTheme(Palette palette) {
		this.palette = palette;
	}

	/**
	 * Tells the renderer where the search matches are.
	 *
	 * @param hits           every match in the current view
	 * @param currentViewRow the row of the match the user is on, or {@code -1}
	 * @param currentColumn  the column of that match, or {@code -1}
	 */
	void setHits(CsvSearch.Hits hits, int currentViewRow, int currentColumn) {
		this.hits = hits != null ? hits : CsvSearch.Hits.NONE;
		this.currentViewRow = currentViewRow;
		this.currentColumn = currentColumn;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {

		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

		int modelColumn = table.convertColumnIndexToModel(column);
		int dataColumn = CsvTableModel.dataColumn(modelColumn);
		boolean gutter = dataColumn < 0;
		int viewRow = model.viewRow(row);

		setBorder(padding);
		setHorizontalAlignment(gutter || isNumeric(dataColumn) ? RIGHT : LEFT);
		setFont(ViewerUi.defaultFont());

		// Selected rows keep their full colour whether or not the grid has focus:
		// the selection is the set of rows the user is about to export, not a
		// cursor, so dimming it the way a list would hides what they picked.
		Color background = isSelected ? palette.selection() : rowBackground(row, gutter);
		Color foreground = isSelected ? palette.selectionForeground() : (gutter ? palette.muted() : palette.foreground());

		if (!gutter && hits.contains(viewRow, dataColumn)) {
			boolean current = viewRow == currentViewRow && dataColumn == currentColumn;
			background = ViewerUi.blend(background, palette.accent(), current ? CURRENT_MATCH_TINT : MATCH_TINT);
			foreground = ViewerUi.readable(background, foreground, palette.foreground());
		}

		setBackground(background);
		setForeground(foreground);

		String text = value == null ? "" : value.toString();
		// A tooltip is the only way to read a value the column is too narrow for,
		// and a quick-view pane is always too narrow for something.
		setToolTipText(text.length() > TOOLTIP_THRESHOLD ? text : null);

		return this;
	}

	private boolean isNumeric(int dataColumn) {
		return model.data() != null && model.data().isNumericColumn(dataColumn);
	}

	private Color rowBackground(int row, boolean gutter) {

		if (gutter) {
			return palette.header();
		}

		return row % 2 == 0 ? palette.background() : palette.stripe();
	}
}
